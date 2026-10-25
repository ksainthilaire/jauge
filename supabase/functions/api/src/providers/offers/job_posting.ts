import type { ExtractedOffer, SalaryRange } from "../provider_types.ts";
import {
  cityOf,
  cleanSkills,
  cleanText,
  contractTypeOf,
  decodeEntities,
  departmentOf,
  departmentOfPostcode,
  htmlToText,
  parseApplicantCount,
  parsePublicationDate,
  parseSalaryText,
  salaryPeriodOf,
  skillsInText,
  workModeOf,
  yearlySalaryRange,
} from "./offer_parsing.ts";

/**
 * Reading of the structured data that most job sites put in their pages for search engines: the schema.org
 * `JobPosting` in a `<script type="application/ld+json">` block (LinkedIn public pages, Hellowork, France Travail,
 * Welcome to the Jungle, Apec, most career sites). Reading it is free and needs no AI.
 */

type JsonObject = Record<string, unknown>;

function isObject(value: unknown): value is JsonObject {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function asArray(value: unknown): unknown[] {
  if (value === undefined || value === null) return [];
  return Array.isArray(value) ? value : [value];
}

function hasType(node: JsonObject, type: string): boolean {
  return asArray(node["@type"]).some((value) => typeof value === "string" && value.toLowerCase() === type);
}

/** Every JSON-LD block of a page, parsed (blocks that are not valid JSON are skipped). */
export function jsonLdBlocks(html: string): unknown[] {
  const blocks: unknown[] = [];
  const expression = /<script\b[^>]*type\s*=\s*["']?application\/ld\+json["']?[^>]*>([\s\S]*?)<\/script>/gi;
  for (const match of html.matchAll(expression)) {
    const content = match[1].trim().replace(/^<!\[CDATA\[|\]\]>$/g, "").replace(/^<!--|-->$/g, "");
    try {
      blocks.push(JSON.parse(content));
    } catch {
      // Some sites write invalid JSON (raw line breaks in strings): try once with them escaped.
      try {
        blocks.push(JSON.parse(content.replace(/[\r\n\t]+/g, " ")));
      } catch {
        continue;
      }
    }
  }
  return blocks;
}

/** The first `JobPosting` of a page (top level, in an array or in `@graph`), or null. */
export function findJobPosting(html: string): JsonObject | null {
  const queue: unknown[] = jsonLdBlocks(html);
  let visited = 0;
  while (queue.length > 0 && visited < 200) {
    visited += 1;
    const node = queue.shift();
    if (Array.isArray(node)) {
      queue.push(...node);
      continue;
    }
    if (!isObject(node)) continue;
    if (hasType(node, "jobposting")) return node;
    if (node["@graph"]) queue.push(node["@graph"]);
    if (isObject(node.mainEntity)) queue.push(node.mainEntity);
  }
  return null;
}

function text(value: unknown, maximum = 200): string | null {
  if (isObject(value)) return text(value.name ?? value["@value"] ?? null, maximum);
  if (Array.isArray(value)) return text(value[0], maximum);
  return cleanText(value, maximum);
}

function numberOf(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string") {
    const parsed = Number(value.replace(/[\s  ]/g, "").replace(",", "."));
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function salaryOf(posting: JsonObject): SalaryRange | null {
  for (const candidate of [posting.baseSalary, posting.estimatedSalary]) {
    for (const salary of asArray(candidate)) {
      if (typeof salary === "string") {
        const parsed = parseSalaryText(salary);
        if (parsed) return parsed;
        continue;
      }
      if (!isObject(salary)) continue;
      const currency = text(salary.currency)?.toUpperCase() ?? "EUR";
      if (currency !== "EUR") continue;
      const value = isObject(salary.value) ? salary.value : salary;
      const minimum = numberOf(value.minValue) ?? numberOf(value.value);
      const maximum = numberOf(value.maxValue) ?? numberOf(value.value) ?? minimum;
      if (minimum === null || maximum === null) continue;
      const unit = text(value.unitText) ?? text(salary.unitText);
      const range = yearlySalaryRange(minimum, maximum, salaryPeriodOf(unit));
      if (range) return range;
    }
  }
  return null;
}

interface Place {
  readonly city: string | null;
  readonly departmentCode: string | null;
}

function placeOf(posting: JsonObject): Place {
  for (const location of asArray(posting.jobLocation)) {
    if (!isObject(location)) continue;
    const address = isObject(location.address) ? location.address : location;
    const country = text(address.addressCountry)?.toLowerCase() ?? "fr";
    if (!["fr", "fra", "france"].includes(country)) continue;
    const locality = text(address.addressLocality, 120);
    const postcode = text(address.postalCode, 10);
    const region = text(address.addressRegion, 120);
    const departmentCode = (postcode ? departmentOfPostcode(postcode.replace(/\s/g, "")) : null) ??
      departmentOf(locality, region);
    return { city: cityOf(locality) ?? cityOf(region), departmentCode };
  }
  return { city: null, departmentCode: null };
}

/**
 * Turns a schema.org `JobPosting` into an offer, or null when it has no title. Skills come from `skills` when the
 * site gives them, otherwise from the vocabulary found in the description (the description itself is not kept).
 *
 * @param posting JSON-LD node.
 * @param today date of today (ISO).
 */
export function offerFromJobPosting(posting: JsonObject, today: string): ExtractedOffer | null {
  const jobTitle = text(posting.title ?? posting.name);
  if (!jobTitle) return null;
  // Some sites escape the HTML of the description ("&lt;p&gt;"): decoded first, then the tags removed.
  const description = decodeEntities(typeof posting.description === "string" ? posting.description : "");
  const descriptionText = description.includes("<") ? htmlToText(description) : description;
  const place = placeOf(posting);
  const employmentTypes = asArray(posting.employmentType).map((value) => text(value)).filter(Boolean) as string[];
  const remote = asArray(posting.jobLocationType).some((value) => /telecommute/i.test(String(value)))
    ? "Télétravail complet"
    : null;
  const listedSkills = typeof posting.skills === "string"
    ? cleanSkills(posting.skills.split(/[,;\n•]/))
    : cleanSkills(asArray(posting.skills));
  return {
    jobTitle,
    company: text(posting.hiringOrganization),
    city: place.city,
    departmentCode: place.departmentCode,
    contractType: contractTypeOf(...employmentTypes) ?? contractTypeOf(contractHint(jobTitle)) ??
      contractTypeOf(contractHint(descriptionText)),
    workMode: remote ?? workModeOf(contractWindow(descriptionText, /t[ée]l[ée]travail|remote|hybride/i)),
    publishedOn: parsePublicationDate(posting.datePosted, today),
    displayedSalary: salaryOf(posting),
    skills: listedSkills.length > 0 ? listedSkills : skillsInText(descriptionText),
    displayedApplicantCount: null,
  };
}

/** "CDI", "CDD", "Alternance"... when written in a text, as a hint for the contract (null otherwise). */
function contractHint(value: string): string | null {
  return /\b(CDI|CDD|alternance|apprentissage|stage|int[ée]rim|freelance)\b/i.exec(value)?.[0] ?? null;
}

/** The words around the first match of a pattern (to read "Télétravail : 2 jours par semaine"). */
function contractWindow(value: string, pattern: RegExp): string | null {
  const match = pattern.exec(value);
  if (!match || match.index === undefined) return null;
  return value.slice(Math.max(0, match.index - 20), match.index + 40);
}

/**
 * Applicant count shown in a LinkedIn public page ("Over 200 applicants", "47 candidats"), or null. LinkedIn writes it
 * in an element with the class `num-applicants__caption`.
 *
 * @param html page.
 */
export function linkedInApplicantCount(html: string): number | null {
  const caption = /class="[^"]*num-applicants__caption[^"]*"[^>]*>([\s\S]*?)<\//i.exec(html)?.[1];
  return caption ? parseApplicantCount(htmlToText(caption)) : null;
}

/** Title of a page (`og:title` or `<title>`), used to recognize the company of an unreadable offer. */
export function pageTitle(html: string): string | null {
  const openGraph = /<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)["']/i.exec(html)?.[1];
  const title = openGraph ?? /<title[^>]*>([\s\S]*?)<\/title>/i.exec(html)?.[1];
  return title ? cleanText(title, 200) : null;
}

/** The text a person sees in the page, for the AI when the page has no structured data (at most 12 000 chars). */
export function visibleText(html: string): string {
  const body = /<main\b[\s\S]*?<\/main>/i.exec(html)?.[0] ?? /<body\b[\s\S]*?<\/body>/i.exec(html)?.[0] ?? html;
  return htmlToText(body.replace(/<(header|footer|nav|aside|form)\b[\s\S]*?<\/\1>/gi, " ")).slice(0, 12_000);
}
