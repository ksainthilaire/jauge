import { findCity } from "../../geography/cities.ts";
import { DEPARTMENT_NAMES } from "../../geography/reference_cities.ts";
import { normalizeSearchText } from "../../text/search_text.ts";
import type { ContractType, SalaryRange } from "../provider_types.ts";

/**
 * Small parsers shared by the readers and the sources: the job sites write salaries, dates, contracts and applicant
 * counts as text ("35 000 - 40 000 € / an", "il y a 3 jours", "Over 200 applicants"). Every parser returns null when
 * it is not sure: an empty field is better than a wrong one.
 */

/** Folded text for the patterns: no case, no accent, apostrophes, slashes and hyphens as spaces. */
export function fold(text: string): string {
  return normalizeSearchText(text).replace(/['\u2019`/_-]+/g, " ").replace(/\s+/g, " ").trim();
}

/** Hours of work in a French year (35 hours a week), to turn an hourly salary into a yearly one. */
const HOURS_PER_YEAR = 1607;
/** Days billed in a French year, to turn a daily rate into a yearly one. */
const DAYS_PER_YEAR = 218;

/** Period of a salary amount. */
export type SalaryPeriod = "year" | "month" | "week" | "day" | "hour";

/**
 * Turns amounts in euros into a yearly gross range in thousands of euros (the unit of the API), or null when the
 * result is not a plausible salary (1 to 1 000 thousand euros).
 *
 * @param minimum smallest amount, in euros.
 * @param maximum largest amount, in euros (the same as the minimum for a single amount).
 * @param period period of the amounts, guessed from their size when null.
 */
export function yearlySalaryRange(
  minimum: number,
  maximum: number,
  period: SalaryPeriod | null,
): SalaryRange | null {
  if (!Number.isFinite(minimum) || !Number.isFinite(maximum) || minimum <= 0 || maximum <= 0) return null;
  const low = Math.min(minimum, maximum);
  const high = Math.max(minimum, maximum);
  const effectivePeriod = period ?? guessPeriod(high);
  const factor = effectivePeriod === "year"
    ? 1
    : effectivePeriod === "month"
    ? 12
    : effectivePeriod === "week"
    ? 52
    : effectivePeriod === "day"
    ? DAYS_PER_YEAR
    : HOURS_PER_YEAR;
  const range = { minimum: Math.round((low * factor) / 1000), maximum: Math.round((high * factor) / 1000) };
  if (range.minimum < 1 || range.maximum > 1000 || range.minimum > range.maximum) return null;
  return range;
}

function guessPeriod(amount: number): SalaryPeriod {
  if (amount >= 12_000) return "year";
  if (amount >= 1_000) return "month";
  if (amount >= 150) return "day";
  return "hour";
}

/** Reads a period word ("/ an", "par mois", "a year", "per hour", "YEAR", "MONTHLY"). */
export function salaryPeriodOf(text: string | null | undefined): SalaryPeriod | null {
  if (!text) return null;
  const folded = fold(text);
  if (/\b(an|ans|annuel|annuelle|annuels|year|yearly|annual|annually|annum|pa)\b/.test(folded)) return "year";
  if (/\b(mois|mensuel|mensuelle|month|monthly)\b/.test(folded)) return "month";
  if (/\b(semaine|hebdomadaire|week|weekly)\b/.test(folded)) return "week";
  if (/\b(jour|journalier|journee|tjm|day|daily)\b/.test(folded)) return "day";
  if (/\b(heure|horaire|hour|hourly|h)\b/.test(folded)) return "hour";
  return null;
}

/** Reads a number written the French or the English way ("35 000", "35.000", "35,000.00", "12,35", "45k"). */
export function parseAmount(text: string): number | null {
  const cleaned = text.replace(/[\s  ]/g, "").toLowerCase();
  const match = /^(\d+(?:[.,]\d+)*)(k)?$/.exec(cleaned);
  if (!match) return null;
  let digits = match[1];
  const lastSeparator = Math.max(digits.lastIndexOf(","), digits.lastIndexOf("."));
  if (lastSeparator >= 0) {
    const decimals = digits.length - lastSeparator - 1;
    if (decimals === 3) {
      // Thousands separator ("35.000", "35,000").
      digits = digits.replace(/[.,]/g, "");
    } else {
      const integerPart = digits.slice(0, lastSeparator).replace(/[.,]/g, "");
      digits = `${integerPart}.${digits.slice(lastSeparator + 1)}`;
    }
  }
  const value = Number(digits);
  if (!Number.isFinite(value)) return null;
  return match[2] ? value * 1000 : value;
}

/**
 * Parses a salary written as text by a job site: "35 000 - 40 000 € / an", "De 2 000 € à 2 400 € par mois",
 * "11,91 - 12,35 € / heure", "45k€", "€35,000.00". Amounts in another currency (dollars, pounds) give null.
 *
 * @param text salary text.
 */
export function parseSalaryText(text: string | null | undefined): SalaryRange | null {
  if (!text) return null;
  if (/[$£]|\b(usd|gbp|chf|cad)\b/i.test(text) && !/€|\beur\b|euro/i.test(text)) return null;
  const amounts = [...text.matchAll(/\d[\d\s  .,]*\s?k?(?![a-z])/gi)]
    .map((match) => parseAmount(match[0].trim()))
    .filter((value): value is number => value !== null && value > 0);
  if (amounts.length === 0) return null;
  const period = salaryPeriodOf(text.replace(/\d/g, " "));
  const low = amounts[0];
  // The second number is the top of the range only when it looks like one ("35 000 € brut, 13e mois" is not).
  const second = amounts[1];
  const high = second !== undefined && second >= low && second <= low * 3 ? second : low;
  return yearlySalaryRange(low, high, period);
}

/** Adds days to an ISO date. */
export function addDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

/**
 * Reads a publication date: an ISO date or timestamp, a Unix timestamp in seconds or milliseconds, or a relative age
 * ("il y a 3 jours", "hier", "Today", "3 days ago", "30+ days ago", "Publiée il y a 2 semaines").
 *
 * @param value date or age as given by the site.
 * @param today date of today (ISO).
 */
export function parsePublicationDate(value: unknown, today: string): string | null {
  if (typeof value === "number" && Number.isFinite(value) && value > 0) {
    const milliseconds = value > 10_000_000_000 ? value : value * 1000;
    const date = new Date(milliseconds);
    return Number.isNaN(date.getTime()) ? null : boundedDate(date.toISOString().slice(0, 10), today);
  }
  if (typeof value !== "string") return null;
  const text = value.trim();
  const iso = /^(\d{4}-\d{2}-\d{2})/.exec(text)?.[1];
  if (iso) return boundedDate(iso, today);
  const french = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(text);
  if (french) return boundedDate(`${french[3]}-${french[2]}-${french[1]}`, today);
  const folded = fold(text);
  if (/\b(aujourd hui|today|just posted|a l instant|nouveau|new|heures?|hours?|minutes?)\b/.test(folded)) {
    return today;
  }
  if (/\b(hier|yesterday)\b/.test(folded)) return addDays(today, -1);
  const relative = /(\d+)\s*\+?\s*(jours?|j|days?|d|semaines?|weeks?|mois|months?|ans?|years?)\b/.exec(folded);
  if (!relative) return null;
  const count = Number(relative[1]);
  const unit = relative[2];
  const days = /^(jour|j|day|d)/.test(unit)
    ? count
    : /^(semaine|week)/.test(unit)
    ? count * 7
    : /^(mois|month)/.test(unit)
    ? count * 30
    : count * 365;
  return addDays(today, -days);
}

function boundedDate(isoDate: string, today: string): string | null {
  const date = new Date(`${isoDate}T00:00:00Z`);
  if (Number.isNaN(date.getTime())) return null;
  // A date in the future is a mistake of the site (or a time zone): today at most.
  return isoDate > today ? today : isoDate;
}

/**
 * Reads an applicant count shown by a site: "200", "Over 200 applicants", "Plus de 200 candidats", "47 candidatures".
 * "Be among the first 25 applicants" only says "fewer than 25": null, the estimate does better than a guess.
 *
 * @param value count or caption.
 */
export function parseApplicantCount(value: unknown): number | null {
  if (typeof value === "number") return Number.isInteger(value) && value >= 0 && value < 100_000 ? value : null;
  if (typeof value !== "string") return null;
  const folded = fold(value);
  if (/\b(first|premiers?|premieres?)\b/.test(folded)) return null;
  const match = /(\d[\d\s.,]*)/.exec(folded);
  if (!match) return null;
  const count = Number(match[1].replace(/[\s.,]/g, ""));
  return Number.isInteger(count) && count >= 0 && count < 100_000 ? count : null;
}

/**
 * Contract type of the API from what a site writes ("CDI", "CDD", "Alternance", "Stage", "Intérim", "Freelance",
 * "INTERN", "CONTRACTOR", "full_time"...). "Full-time" alone says nothing about the contract: null.
 *
 * @param values contract words (one or several).
 */
export function contractTypeOf(...values: readonly (string | null | undefined)[]): ContractType | null {
  const folded = fold(values.filter(Boolean).join(" "));
  if (!folded) return null;
  if (/\b(cdi|permanent|indetermine|indeterminee)\b/.test(folded)) return "permanent";
  if (/\b(alternance|apprentissage|apprenti|apprentie|apprenticeship|professionnalisation|work study)\b/.test(folded)) {
    return "apprenticeship";
  }
  if (/\b(stage|stagiaire|intern|internship|interne)\b/.test(folded)) return "internship";
  if (/\b(cdd|determine|determinee|fixed term)\b/.test(folded)) return "fixed_term";
  if (/\b(interim|interimaire|temporary|temporaire|mission interim|travail temporaire)\b/.test(folded)) {
    return "temporary";
  }
  if (/\b(freelance|independant|independante|contractor|portage|auto entrepreneur|consultant externe)\b/.test(folded)) {
    return "freelance";
  }
  if (/\b(full time|temps plein|part time|temps partiel)\b/.test(folded)) return null;
  if (/\b(volunteer|benevole|benevolat|other|autre)\b/.test(folded)) return "other";
  return null;
}

/**
 * Work mode as shown in the app ("Télétravail complet", "Hybride", "Sur site"), or null.
 *
 * @param values remote words of the site ("Télétravail partiel", "Hybrid", "fulltime", "TELECOMMUTE"...).
 */
export function workModeOf(...values: readonly (string | null | undefined)[]): string | null {
  const folded = fold(values.filter(Boolean).join(" ").replace(/_/g, " "));
  if (!folded) return null;
  if (/\b(hybride|hybrid|partiel|partial|occasionnel|punctual|ponctuel)\b/.test(folded)) return "Hybride";
  // "Télétravail : 2 jours par semaine" is hybrid work.
  if (/\b(\d|un|deux|trois|quatre)\s*(jours?|j)\b.*\b(semaine|week)\b/.test(folded)) return "Hybride";
  if (
    /\b(full remote|fulltime|teletravail complet|teletravail total|100 teletravail|remote|telecommute|teletravail)\b/
      .test(folded)
  ) {
    return /\b(non|no|pas de|aucun)\b/.test(folded) ? "Sur site" : "Télétravail complet";
  }
  if (/\b(on site|onsite|sur site|presentiel|no)\b/.test(folded)) return "Sur site";
  return null;
}

/**
 * Department of a place written by a site: a postcode ("69003 Lyon"), a code in brackets ("Lyon (69)"), a code
 * after a dash ("Lyon - 69"), a department name ("Rhône"), or a known city.
 *
 * @param texts place texts, the first that gives a department wins.
 */
export function departmentOf(...texts: readonly (string | null | undefined)[]): string | null {
  for (const text of texts) {
    if (!text) continue;
    const postcode = /\b(\d{5})\b/.exec(text)?.[1];
    if (postcode) {
      const fromPostcode = departmentOfPostcode(postcode);
      if (fromPostcode) return fromPostcode;
    }
    const code = /(?:\(|-\s*|,\s*)(\d{2}|2[AB]|9[78]\d)\)?\s*$/i.exec(text.trim())?.[1]?.toUpperCase();
    if (code && DEPARTMENT_NAMES[code]) return code;
    const folded = fold(text);
    for (const [departmentCode, name] of Object.entries(DEPARTMENT_NAMES)) {
      if (fold(name) === folded) return departmentCode;
    }
    const city = findCity(cityOf(text));
    if (city) return city.departmentCode;
  }
  return null;
}

/** Department of a French postcode ("69003" gives "69", "20100" gives "2A", "97411" gives "974"). */
export function departmentOfPostcode(postcode: string): string | null {
  if (!/^\d{5}$/.test(postcode)) return null;
  if (postcode.startsWith("97") || postcode.startsWith("98")) return postcode.slice(0, 3);
  if (postcode.startsWith("20")) return Number(postcode) < 20200 ? "2A" : "2B";
  const code = postcode.slice(0, 2);
  return DEPARTMENT_NAMES[code] ? code : null;
}

/**
 * City part of a place text: "Lyon, Auvergne-Rhône-Alpes, France" gives "Lyon", "69003 Lyon" gives "Lyon",
 * "Lyon - 69" and "Lyon (69)" give "Lyon", "Télétravail à Lyon (69)" gives "Lyon".
 *
 * @param text place text.
 */
export function cityOf(text: string | null | undefined): string | null {
  if (!text) return null;
  let city = text.split(/[,;|•]/)[0]
    .replace(/\(.*?\)/g, " ")
    .replace(/\b\d{5}\b/g, " ")
    .replace(/\s-\s*(\d{2}|2[AB]|9[78]\d)\s*$/i, " ")
    .replace(/^(t[ée]l[ée]travail|remote|hybride|hybrid)\s+(à|a|in)\s+/i, "")
    .replace(/\s+/g, " ")
    .trim();
  if (/^(france|remote|t[ée]l[ée]travail)$/i.test(city)) city = "";
  return city.length > 0 ? city.slice(0, 120) : null;
}

/** Removes HTML tags and entities, keeps line breaks between blocks. */
export function htmlToText(html: string): string {
  return decodeEntities(
    html
      .replace(/<(script|style|noscript|svg|template)[\s\S]*?<\/\1>/gi, " ")
      .replace(/<!--[\s\S]*?-->/g, " ")
      .replace(/<(br|\/p|\/div|\/li|\/h[1-6]|\/tr|\/section|\/article)\b[^>]*>/gi, "\n")
      .replace(/<[^>]+>/g, " "),
  )
    .replace(/[ \t ]+/g, " ")
    .replace(/\s*\n\s*/g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

const NAMED_ENTITIES: Readonly<Record<string, string>> = {
  amp: "&",
  lt: "<",
  gt: ">",
  quot: '"',
  apos: "'",
  nbsp: " ",
  eacute: "é",
  egrave: "è",
  ecirc: "ê",
  agrave: "à",
  acirc: "â",
  ccedil: "ç",
  ocirc: "ô",
  ucirc: "û",
  ugrave: "ù",
  icirc: "î",
  iuml: "ï",
  euml: "ë",
  rsquo: "'",
  lsquo: "'",
  ldquo: '"',
  rdquo: '"',
  hellip: "...",
  euro: "€",
  middot: "·",
  bull: "•",
  laquo: '"',
  raquo: '"',
  mdash: "-",
  ndash: "-",
};

/** Decodes HTML entities (named, decimal and hexadecimal). */
export function decodeEntities(text: string): string {
  return text.replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (whole, entity: string) => {
    if (entity.startsWith("#x") || entity.startsWith("#X")) return safeCharacter(parseInt(entity.slice(2), 16), whole);
    if (entity.startsWith("#")) return safeCharacter(parseInt(entity.slice(1), 10), whole);
    const named = NAMED_ENTITIES[entity.toLowerCase()];
    // Dashes are written as plain hyphens in Jauge.
    return named ?? whole;
  }).replace(/[\u2013\u2014]/g, "-");
}

function safeCharacter(code: number, fallback: string): string {
  if (!Number.isFinite(code) || code <= 0 || code > 0x10ffff) return fallback;
  return String.fromCodePoint(code);
}

/** Skills that the job sites ask for, with their usual spellings (folded) and the label shown in the app. */
const SKILL_VOCABULARY: readonly { readonly label: string; readonly patterns: readonly string[] }[] = [
  { label: "SEO", patterns: ["seo", "referencement naturel"] },
  { label: "SEA", patterns: ["sea", "referencement payant"] },
  { label: "Google Ads", patterns: ["google ads", "adwords"] },
  { label: "Meta Ads", patterns: ["meta ads", "facebook ads"] },
  { label: "Google Analytics", patterns: ["google analytics", "ga4"] },
  { label: "CRM", patterns: ["crm"] },
  { label: "Salesforce", patterns: ["salesforce"] },
  { label: "HubSpot", patterns: ["hubspot"] },
  { label: "Emailing", patterns: ["emailing", "e mailing"] },
  { label: "Canva", patterns: ["canva"] },
  { label: "Figma", patterns: ["figma"] },
  { label: "Adobe Photoshop", patterns: ["photoshop"] },
  { label: "Adobe Illustrator", patterns: ["illustrator"] },
  { label: "Suite Adobe", patterns: ["suite adobe", "adobe creative"] },
  { label: "WordPress", patterns: ["wordpress"] },
  { label: "Rédaction web", patterns: ["redaction web", "copywriting"] },
  { label: "Réseaux sociaux", patterns: ["reseaux sociaux", "social media"] },
  { label: "Gestion de projet", patterns: ["gestion de projet", "project management", "conduite de projet"] },
  { label: "Agile", patterns: ["agile", "scrum", "kanban"] },
  { label: "Excel", patterns: ["excel"] },
  { label: "Power BI", patterns: ["power bi", "powerbi"] },
  { label: "Tableau", patterns: ["tableau software"] },
  { label: "SQL", patterns: ["sql", "postgresql", "mysql"] },
  { label: "Python", patterns: ["python"] },
  { label: "Java", patterns: ["java"] },
  { label: "JavaScript", patterns: ["javascript"] },
  { label: "TypeScript", patterns: ["typescript"] },
  { label: "React", patterns: ["react", "reactjs", "react js"] },
  { label: "Angular", patterns: ["angular"] },
  { label: "Vue.js", patterns: ["vue js", "vuejs"] },
  { label: "Node.js", patterns: ["node js", "nodejs"] },
  { label: "PHP", patterns: ["php", "symfony", "laravel"] },
  { label: "C#", patterns: ["c#", ".net", "dotnet"] },
  { label: "Kotlin", patterns: ["kotlin"] },
  { label: "Swift", patterns: ["swift"] },
  { label: "Docker", patterns: ["docker"] },
  { label: "Kubernetes", patterns: ["kubernetes", "k8s"] },
  { label: "AWS", patterns: ["aws", "amazon web services"] },
  { label: "Azure", patterns: ["azure"] },
  { label: "Google Cloud", patterns: ["gcp", "google cloud"] },
  { label: "Linux", patterns: ["linux"] },
  { label: "Git", patterns: ["git", "github", "gitlab"] },
  { label: "DevOps", patterns: ["devops", "ci cd"] },
  { label: "Machine learning", patterns: ["machine learning", "apprentissage automatique"] },
  { label: "Intelligence artificielle", patterns: ["intelligence artificielle", "ia generative", "llm"] },
  { label: "SAP", patterns: ["sap"] },
  { label: "Comptabilité", patterns: ["comptabilite", "comptable"] },
  { label: "Paie", patterns: ["paie"] },
  { label: "Contrôle de gestion", patterns: ["controle de gestion"] },
  { label: "Fiscalité", patterns: ["fiscalite"] },
  { label: "Droit social", patterns: ["droit social", "droit du travail"] },
  { label: "Recrutement", patterns: ["recrutement", "sourcing"] },
  { label: "Prospection", patterns: ["prospection", "developpement commercial"] },
  { label: "Négociation", patterns: ["negociation"] },
  { label: "Relation client", patterns: ["relation client", "service client"] },
  { label: "Management", patterns: ["management d equipe", "encadrement", "management"] },
  { label: "Logistique", patterns: ["logistique", "supply chain"] },
  { label: "Achats", patterns: ["achats", "procurement"] },
  { label: "Qualité", patterns: ["qualite", "iso 9001"] },
  { label: "Lean", patterns: ["lean", "amelioration continue"] },
  { label: "AutoCAD", patterns: ["autocad"] },
  { label: "SolidWorks", patterns: ["solidworks"] },
  { label: "CACES", patterns: ["caces"] },
  { label: "Permis B", patterns: ["permis b"] },
  {
    label: "Anglais courant",
    patterns: ["anglais courant", "anglais professionnel", "fluent english", "bilingue anglais"],
  },
  { label: "Anglais", patterns: ["anglais", "english"] },
  { label: "Allemand", patterns: ["allemand", "german"] },
  { label: "Espagnol", patterns: ["espagnol", "spanish"] },
  { label: "Soins infirmiers", patterns: ["soins infirmiers", "infirmier", "infirmiere"] },
  { label: "HACCP", patterns: ["haccp"] },
  { label: "Pack Office", patterns: ["pack office", "microsoft office", "suite office"] },
];

const COMPILED_VOCABULARY = SKILL_VOCABULARY.map((entry) => ({
  label: entry.label,
  expressions: entry.patterns.map((pattern) =>
    new RegExp(`(^|[^a-z0-9#+])${pattern.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}($|[^a-z0-9#+])`)
  ),
}));

/**
 * Finds known skills in a text (description of an offer), in the order of the vocabulary, at most `limit`. The text
 * itself is never kept.
 *
 * @param text description (plain text or HTML).
 * @param limit largest number of skills.
 */
export function skillsInText(text: string | null | undefined, limit = 12): string[] {
  if (!text) return [];
  const folded = fold(text.includes("<") ? htmlToText(text) : text);
  const found: string[] = [];
  for (const entry of COMPILED_VOCABULARY) {
    if (found.length >= limit) break;
    if (entry.label === "Anglais" && found.includes("Anglais courant")) continue;
    if (entry.expressions.some((expression) => expression.test(folded))) found.push(entry.label);
  }
  return found;
}

/** Cleans a list of skills given by a site: trimmed, unique (case folded), 80 characters, at most `limit`. */
export function cleanSkills(values: unknown, limit = 20): string[] {
  if (!Array.isArray(values)) return [];
  const seen = new Set<string>();
  const skills: string[] = [];
  for (const value of values) {
    const label = typeof value === "string"
      ? value
      : value && typeof value === "object" && "name" in value && typeof value.name === "string"
      ? value.name
      : null;
    const trimmed = label?.replace(/\s+/g, " ").trim().slice(0, 80);
    if (!trimmed) continue;
    const key = fold(trimmed);
    if (seen.has(key)) continue;
    seen.add(key);
    skills.push(trimmed);
    if (skills.length >= limit) break;
  }
  return skills;
}

/** A trimmed text of at most `maximum` characters, or null when empty or not a text. */
export function cleanText(value: unknown, maximum = 200): string | null {
  if (typeof value !== "string" && typeof value !== "number") return null;
  const text = decodeEntities(String(value)).replace(/\s+/g, " ").trim();
  return text.length > 0 ? text.slice(0, maximum) : null;
}
