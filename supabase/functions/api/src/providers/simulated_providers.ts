import { REFERENCE_CITIES } from "../geography/reference_cities.ts";
import { distanceKilometers, findCity } from "../geography/cities.ts";
import { normalizeSearchText } from "../text/search_text.ts";
import type {
  Advice,
  AdviceFacts,
  AiProvider,
  AppleTokenRevoker,
  AuthAdministration,
  CollectedOfferInput,
  CollectionPoll,
  CollectionRequest,
  CollectionStart,
  ContractType,
  ExtractedOffer,
  ExtractionOutcome,
  OfferReader,
  OfferSource,
  OperationsAlerter,
  Providers,
  ReputationSummary,
  SocialMention,
  SocialSearch,
  SubscriptionStore,
} from "./provider_types.ts";

/**
 * Deterministic fake providers: the same input always gives the same output, with realistic values (the offer of the
 * mockups: "Chargé·e de marketing digital", Atelier Sorel, Lyon). Used by the tests and by
 * `JAUGE_PROVIDER_MODE=simulated` until the live connectors are plugged in. Simulated costs are journaled with
 * `simulated = true` and never count against the daily limit.
 */

/** 32 bit FNV-1a hash of a text. */
export function hashText(text: string): number {
  let hash = 0x811c9dc5;
  for (let index = 0; index < text.length; index++) {
    hash ^= text.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return hash >>> 0;
}

/** Seeded pseudo random generator (mulberry32): numbers in [0, 1). */
export function seededRandom(seed: string): () => number {
  let state = hashText(seed);
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let value = state;
    value = Math.imul(value ^ (value >>> 15), value | 1);
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  };
}

const JOB_TITLES = [
  "Chargé·e de marketing digital",
  "Responsable acquisition",
  "Chef·fe de projet digital",
  "Community manager",
  "Chargé·e de communication",
  "Content manager",
];
const COMPANIES = [
  "Atelier Sorel",
  "Maison Brunet",
  "Lumio",
  "Coopérative Vercors",
  "Studio Pivot",
  "Nexa Santé",
  "Rivage",
  "Okapi",
];
const SKILLS = [
  "SEO",
  "Google Ads",
  "Meta Ads",
  "Google Analytics",
  "CRM",
  "Emailing",
  "Canva",
  "Rédaction web",
  "Figma",
  "Gestion de projet",
  "Anglais courant",
  "HubSpot",
  "Excel",
  "WordPress",
];

function addDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

function pick<Value>(values: readonly Value[], random: () => number): Value {
  return values[Math.floor(random() * values.length)];
}

function pickSkills(random: () => number, count: number): string[] {
  const chosen = new Set<string>();
  while (chosen.size < count) chosen.add(pick(SKILLS, random));
  return [...chosen];
}

/** The offer of the mockups, used for links and images in simulated mode. */
function simulatedOffer(seed: string, today: string): ExtractedOffer {
  const random = seededRandom(seed);
  const mockupOffer = random() < 0.5;
  return {
    jobTitle: mockupOffer ? JOB_TITLES[0] : pick(JOB_TITLES, random),
    company: mockupOffer ? COMPANIES[0] : pick(COMPANIES, random),
    city: "Lyon 3e",
    departmentCode: "69",
    contractType: "permanent",
    workMode: random() < 0.5 ? "Hybride, 2 jours" : null,
    publishedOn: addDays(today, -(1 + Math.floor(random() * 6))),
    displayedSalary: random() < 0.4 ? { minimum: 36, maximum: 41 } : null,
    skills: pickSkills(random, 4),
    displayedApplicantCount: null,
  };
}

/** Simulated reading of links. Links containing "unreadable" or "unsupported" simulate those failures. */
export class SimulatedOfferReader implements OfferReader {
  readonly name = "simulated_reader";

  estimateCostMicroUsd(): number {
    return 0;
  }

  readLink(url: string, _site: string | null, today: string): Promise<ExtractionOutcome> {
    if (url.includes("unsupported")) return Promise.resolve({ kind: "unsupported", costMicroUsd: 0 });
    if (url.includes("unreadable")) {
      return Promise.resolve({ kind: "unreadable", recognizedCompany: "Atelier Sorel", costMicroUsd: 0 });
    }
    return Promise.resolve({ kind: "extracted", offer: simulatedOffer(url, today), costMicroUsd: 0 });
  }
}

/**
 * Simulated market source. An asynchronous source answers "running" on the first poll and finishes on the next
 * one, like an Apify run. Offers are spread over the year, around the city of the request, with salaries and
 * (for sources that show them) applicant counts.
 */
export class SimulatedOfferSource implements OfferSource {
  private readonly runs = new Map<string, { request: CollectionRequest; polls: number }>();

  /**
   * @param name name of the source (`linkedin`, `indeed`...).
   * @param options paid source, asynchronous runs, shows applicant counts, cost per offer in micro dollars.
   */
  constructor(
    readonly name: string,
    private readonly options: {
      paid: boolean;
      asynchronous: boolean;
      showsApplicants: boolean;
      costPerOfferMicroUsd: number;
      failing?: boolean;
    },
  ) {}

  get paid(): boolean {
    return this.options.paid;
  }

  estimateCostMicroUsd(request: CollectionRequest): number {
    return request.maximumOffers * this.options.costPerOfferMicroUsd;
  }

  start(request: CollectionRequest): Promise<CollectionStart> {
    if (!this.options.asynchronous) {
      const offers = this.generate(request);
      return Promise.resolve({
        kind: "finished",
        offers,
        costMicroUsd: offers.length * this.options.costPerOfferMicroUsd,
      });
    }
    const runId = `simulated-${this.name}-${
      hashText(`${request.jobFamily}|${request.departmentCode}|${this.runs.size}`)
    }`;
    this.runs.set(runId, { request, polls: 0 });
    return Promise.resolve({ kind: "started", runId });
  }

  poll(runId: string): Promise<CollectionPoll> {
    const run = this.runs.get(runId);
    if (!run) return Promise.resolve({ kind: "failed", code: "unknown_run", costMicroUsd: 0 });
    run.polls += 1;
    if (this.options.failing) return Promise.resolve({ kind: "failed", code: "actor_failed", costMicroUsd: 1000 });
    if (run.polls < 2) return Promise.resolve({ kind: "running" });
    const offers = this.generate(run.request);
    return Promise.resolve({
      kind: "finished",
      offers,
      costMicroUsd: offers.length * this.options.costPerOfferMicroUsd,
    });
  }

  private generate(request: CollectionRequest): CollectedOfferInput[] {
    const random = seededRandom(`${this.name}|${request.jobFamily}|${request.departmentCode}`);
    const center = findCity(request.city) ??
      REFERENCE_CITIES.find((city) => city.departmentCode === request.departmentCode) ?? REFERENCE_CITIES[0];
    const nearby = REFERENCE_CITIES.filter((city) => distanceKilometers(center, city) <= 60);
    const familyRandom = seededRandom(request.jobFamily);
    const baseSalary = 30 + Math.round(familyRandom() * 14);
    const baseApplicants = 60 + Math.round(familyRandom() * 120);
    const count = Math.min(request.maximumOffers, 40 + Math.floor(random() * 30));
    const offers: CollectedOfferInput[] = [];
    for (let index = 0; index < count; index++) {
      const city = random() < 0.6 ? center : pick(nearby, random);
      const age = Math.floor(random() ** 1.6 * 365);
      const salaryShown = random() < 0.45;
      const low = baseSalary - 6 + Math.round(random() * 12);
      const applicantFactor = 0.35 + random() * 1.6;
      const daysOnline = Math.min(age + 1, 30);
      const shown = this.options.showsApplicants && random() < 0.7;
      offers.push({
        source: this.name,
        externalId: `${this.name}-${hashText(`${request.jobFamily}|${request.departmentCode}|${index}`)}`,
        jobTitle: index % 3 === 0
          ? request.jobTitle
          : `${request.jobTitle} ${pick(["junior", "confirmé·e", "H/F"], random)}`,
        company: pick(COMPANIES, random),
        city: city.name,
        departmentCode: city.departmentCode,
        publishedOn: addDays(request.today, -age),
        salary: salaryShown ? { minimum: low, maximum: low + 3 + Math.round(random() * 6) } : null,
        applicantCount: shown
          ? Math.max(1, Math.round(baseApplicants * applicantFactor * (1 - Math.exp(-daysOnline / 4.2))))
          : null,
        contractType: random() < 0.8 ? "permanent" : "fixed_term",
        workMode: random() < 0.4 ? "Hybride" : null,
        skills: pickSkills(random, 3 + Math.floor(random() * 3)),
        url: `https://example.org/${this.name}/${index}`,
      });
    }
    return offers;
  }
}

/** Simulated social search: a few posts per company. */
export class SimulatedSocialSearch implements SocialSearch {
  readonly name = "simulated_social";
  readonly paid = false;

  estimateCostMicroUsd(): number {
    return 0;
  }

  search(query: { company: string | null; jobTitle: string; city: string | null }): Promise<{
    mentions: readonly SocialMention[];
    costMicroUsd: number;
  }> {
    if (!query.company) return Promise.resolve({ mentions: [], costMicroUsd: 0 });
    const random = seededRandom(`social|${query.company}`);
    const count = Math.floor(random() * 6);
    const mentions: SocialMention[] = [];
    for (let index = 0; index < count; index++) {
      mentions.push({
        source: index % 2 === 0 ? "reddit" : "x",
        url: `https://example.org/post/${hashText(`${query.company}|${index}`)}`,
        publishedOn: null,
        text: random() < 0.6
          ? "Bonne ambiance, process de recrutement rapide."
          : "Beaucoup de candidats, réponse lente.",
      });
    }
    return Promise.resolve({ mentions, costMicroUsd: 0 });
  }
}

const CONTRACT_WORDS: readonly [RegExp, ContractType][] = [
  [/\bcdi\b/, "permanent"],
  [/\bcdd\b/, "fixed_term"],
  [/\balternance\b|\bapprentissage\b/, "apprenticeship"],
  [/\bstage\b/, "internship"],
  [/\bfreelance\b|\bindependant\b/, "freelance"],
  [/\binterim\b/, "temporary"],
];

/** Simulated AI: reads offers with simple rules and writes a reputation summary from a template. */
export class SimulatedAiProvider implements AiProvider {
  readonly name = "simulated_ai";

  estimateCostMicroUsd(): number {
    return 0;
  }

  extractOfferFromText(text: string, today: string): Promise<ExtractionOutcome> {
    const lines = text.split(/\r?\n/).map((line) => line.trim()).filter((line) => line.length > 0);
    const folded = normalizeSearchText(text);
    const city = REFERENCE_CITIES.find((candidate) => folded.includes(normalizeSearchText(candidate.name)));
    const companyMatch = /(?:chez|entreprise|société|company)\s*:?\s*([A-ZÀ-Ý][\w&'À-ÿ -]{1,60})/u.exec(text);
    const company = companyMatch ? companyMatch[1].trim() : null;
    if (text.trim().length < 40 || lines.length === 0 || lines[0].length > 120 || !city) {
      return Promise.resolve({ kind: "unreadable", recognizedCompany: company, costMicroUsd: 0 });
    }
    const salary = /(\d{2})\s*(?:k|000)\s*(?:€|euros?)?\s*(?:-|à|a)\s*(\d{2})\s*(?:k|000)/i.exec(text);
    const daysAgo = /il y a (\d{1,2}) jours?/i.exec(text);
    const contract = CONTRACT_WORDS.find(([pattern]) => pattern.test(folded))?.[1] ?? null;
    return Promise.resolve({
      kind: "extracted",
      costMicroUsd: 0,
      offer: {
        jobTitle: lines[0],
        company,
        city: city.name,
        departmentCode: city.departmentCode,
        contractType: contract,
        workMode: /teletravail|hybride/.test(folded) ? "Hybride" : null,
        publishedOn: daysAgo ? addDays(today, -Number(daysAgo[1])) : null,
        displayedSalary: salary ? { minimum: Number(salary[1]), maximum: Number(salary[2]) } : null,
        skills: SKILLS.filter((skill) => folded.includes(normalizeSearchText(skill))),
        displayedApplicantCount: null,
      },
    });
  }

  extractOfferFromImage(image: Uint8Array, _mimeType: string, today: string): Promise<ExtractionOutcome> {
    if (image.byteLength < 2048) {
      return Promise.resolve({ kind: "unreadable", recognizedCompany: null, costMicroUsd: 0 });
    }
    return Promise.resolve({
      kind: "extracted",
      offer: simulatedOffer(String(hashText(String(image.byteLength))), today),
      costMicroUsd: 0,
    });
  }

  summarizeReputation(input: {
    company: string | null;
    jobTitle: string;
    mentions: readonly SocialMention[];
  }): Promise<{ summary: ReputationSummary | null; costMicroUsd: number }> {
    if (input.mentions.length === 0) return Promise.resolve({ summary: null, costMicroUsd: 0 });
    const positive = input.mentions.filter((mention) => mention.text.startsWith("Bonne")).length;
    const tone = positive * 2 > input.mentions.length ? "positive" : positive === 0 ? "negative" : "mixed";
    const sources = [...new Set(input.mentions.map((mention) => mention.source))];
    return Promise.resolve({
      summary: {
        summaryFrench: "Des avis publics évoquent une ambiance plutôt appréciée et des réponses parfois lentes.",
        summaryEnglish: "Some public posts mention a well liked atmosphere and answers that sometimes take long.",
        tone,
        mentionCount: input.mentions.length,
        sources,
        links: input.mentions.slice(0, 3).map((mention) => ({ source: mention.source, url: mention.url })),
      },
      costMicroUsd: 0,
    });
  }

  writeAdvice(facts: AdviceFacts): Promise<{ advice: Advice | null; costMicroUsd: number }> {
    const french = facts.language === "fr";
    const day = facts.timingAdvice ? WEEKDAY_NAMES[facts.timingAdvice.applyBefore]?.[french ? 0 : 1] : null;
    const timing = facts.timingAdvice && day
      ? french
        ? `Postule avant ${day} : environ ${facts.timingAdvice.sharePercent} % des candidatures arrivent en ${facts.timingAdvice.withinDays} jours.`
        : `Apply before ${day}: about ${facts.timingAdvice.sharePercent} % of applications arrive within ${facts.timingAdvice.withinDays} days.`
      : french
      ? `Environ ${facts.applicants.estimate} candidats estimés sur cette offre.`
      : `About ${facts.applicants.estimate} applicants estimated on this offer.`;
    const negotiation = facts.negotiationTarget === null
      ? null
      : french
      ? `Vise ${facts.negotiationTarget} k€ : c'est le haut de la fourchette des offres comparables.`
      : `Aim for €${facts.negotiationTarget}k: the upper part of the range of comparable offers.`;
    return Promise.resolve({
      advice: {
        text: timing,
        negotiationArgument: negotiation,
        missingSkills: facts.missingSkillCandidates.slice(0, 3),
      },
      costMicroUsd: 0,
    });
  }
}

const WEEKDAY_NAMES: Readonly<Record<string, readonly [string, string]>> = {
  MONDAY: ["lundi", "Monday"],
  TUESDAY: ["mardi", "Tuesday"],
  WEDNESDAY: ["mercredi", "Wednesday"],
  THURSDAY: ["jeudi", "Thursday"],
  FRIDAY: ["vendredi", "Friday"],
  SATURDAY: ["samedi", "Saturday"],
  SUNDAY: ["dimanche", "Sunday"],
};

/** Records the account calls instead of making them (RevenueCat, Apple, Supabase Auth, alerts). */
export class RecordingAccountServices
  implements SubscriptionStore, AppleTokenRevoker, AuthAdministration, OperationsAlerter {
  /** Calls made, in order (`revenuecat:<id>`, `apple`, `auth:<id>`, `alert:<title>`). */
  readonly calls: string[] = [];
  /** Number of the next calls that fail (to test retries). */
  failuresToSimulate = 0;

  private maybeFail(): void {
    if (this.failuresToSimulate > 0) {
      this.failuresToSimulate -= 1;
      throw new Error("simulated failure");
    }
  }

  deleteCustomer(appUserId: string): Promise<void> {
    this.maybeFail();
    this.calls.push(`revenuecat:${appUserId}`);
    return Promise.resolve();
  }

  revokeWithAuthorizationCode(_authorizationCode: string): Promise<void> {
    this.maybeFail();
    this.calls.push("apple");
    return Promise.resolve();
  }

  deleteUser(userId: string): Promise<void> {
    this.maybeFail();
    this.calls.push(`auth:${userId}`);
    return Promise.resolve();
  }

  send(title: string, _message: string): Promise<void> {
    this.calls.push(`alert:${title}`);
    return Promise.resolve();
  }
}

/**
 * Builds the simulated providers. LinkedIn (asynchronous, shows applicants) and Hellowork are on, Indeed is a paid
 * complement, Welcome to the Jungle stays off (rental too expensive, see ARCHITECTURE.md).
 */
export function createSimulatedProviders(accountServices = new RecordingAccountServices()): Providers {
  return {
    mode: "simulated",
    offerReader: new SimulatedOfferReader(),
    offerSources: [
      new SimulatedOfferSource("linkedin", {
        paid: true,
        asynchronous: true,
        showsApplicants: true,
        costPerOfferMicroUsd: 1000,
      }),
      new SimulatedOfferSource("hellowork", {
        paid: true,
        asynchronous: false,
        showsApplicants: false,
        costPerOfferMicroUsd: 1000,
      }),
    ],
    socialSearch: new SimulatedSocialSearch(),
    ai: new SimulatedAiProvider(),
    subscriptionStore: accountServices,
    appleTokenRevoker: accountServices,
    authAdministration: accountServices,
    operationsAlerter: accountServices,
  };
}
