import { normalizeSearchText } from "../../text/search_text.ts";
import type { CollectedOfferInput, CollectionRequest, ContractType, ExtractedOffer } from "../provider_types.ts";
import {
  cityOf,
  cleanSkills,
  cleanText,
  contractTypeOf,
  departmentOf,
  parseApplicantCount,
  parsePublicationDate,
  parseSalaryText,
  skillsInText,
  workModeOf,
  yearlySalaryRange,
} from "../offers/offer_parsing.ts";
import type { ActorPrices } from "./apify_pricing.ts";

/**
 * The Apify actors of Jauge: their identifier, their prices (from their Apify pages, October 2026), the input Jauge
 * gives them and the mapping of their results to the offers of the API. Inputs and outputs follow the documentation
 * of each actor; a test replays recorded results of each one (`fixtures/`).
 */

/** Names of the market sources (`collected_offers.source`). */
export type SourceName = "linkedin" | "indeed" | "hellowork" | "glassdoor" | "welcome_to_the_jungle";

/** Every market source, in the order they are started. */
export const SOURCE_NAMES: readonly SourceName[] = [
  "linkedin",
  "hellowork",
  "indeed",
  "glassdoor",
  "welcome_to_the_jungle",
];

/** An item of a dataset. */
export type DatasetItem = Record<string, unknown>;

/** What a mapping needs besides the item. */
export interface MappingContext {
  /** Date of today (ISO), to turn "il y a 3 jours" into a date. */
  readonly today: string;
}

/** An actor that collects the offers of a market (one job in one area). */
export interface MarketActor {
  readonly source: SourceName;
  /** Actor identifier, `owner~name`. */
  readonly actorId: string;
  readonly prices: ActorPrices;
  readonly memoryMegabytes: number;
  /** Seconds after which Apify stops the run (results found so far are kept and paid). */
  readonly timeoutSeconds: number;
  /** Results asked at most, whatever the request says (the price is per result). */
  readonly maximumOffers: number;
  /** Fields read from the dataset (descriptions are only read to find skills, never stored). */
  readonly fields: readonly string[];
  /** Input of the actor for a request, `limit` results at most. */
  buildInput(request: CollectionRequest, limit: number): Record<string, unknown>;
  /** One result as an offer of the market, or null when it is unusable (no title, abroad, error item). */
  mapItem(item: DatasetItem, context: MappingContext): CollectedOfferInput | null;
}

/** An actor that reads one offer from its link, for the sites whose pages cannot be read directly. */
export interface OfferActor {
  readonly site: string;
  readonly actorId: string;
  readonly prices: ActorPrices;
  readonly memoryMegabytes: number;
  readonly timeoutSeconds: number;
  readonly fields: readonly string[];
  buildInput(url: string): Record<string, unknown>;
  mapItem(item: DatasetItem, context: MappingContext): ExtractedOffer | null;
}

// Helpers --------------------------------------------------------------------------------------------------------

const ABROAD =
  /\b(switzerland|suisse|schweiz|belgique|belgium|belgie|luxembourg|germany|deutschland|allemagne|spain|espana|espagne|italy|italia|italie|united kingdom|royaume uni|england|ireland|netherlands|pays bas|portugal|monaco|canada|quebec|united states|usa|maroc|morocco|tunisie|algerie)\b/;

/** True when a place text names another country than France. */
export function isAbroad(...texts: readonly (string | null | undefined)[]): boolean {
  return texts.some((text) => text !== null && text !== undefined && ABROAD.test(normalizeSearchText(text)));
}

/**
 * Words of a job title for the search boxes of the sites: no gender marks, no contract, no bracket
 * ("Chargé·e de marketing digital (H/F) - CDI" gives "Chargé·e de marketing digital").
 */
export function searchWords(jobTitle: string): string {
  return jobTitle
    .replace(/\(.*?\)|\[.*?\]/g, " ")
    .replace(/\b(H\s*\/\s*F|F\s*\/\s*H|H\s*\/\s*F\s*\/\s*X|M\s*\/\s*F|F\s*\/\s*M)\b/gi, " ")
    .replace(/\s[-|/]\s*(CDI|CDD|alternance|stage|int[ée]rim|freelance)\b.*$/i, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, 100);
}

function text(value: unknown, maximum = 200): string | null {
  return cleanText(value, maximum);
}

function strings(value: unknown): string[] {
  if (Array.isArray(value)) return value.filter((entry): entry is string => typeof entry === "string");
  return typeof value === "string" ? [value] : [];
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function nearest(value: number, choices: readonly number[]): number {
  return choices.reduce((best, choice) => Math.abs(choice - value) < Math.abs(best - value) ? choice : best);
}

/** An offer of the market as an offer read from its page (same fields). */
export function toExtractedOffer(offer: CollectedOfferInput): ExtractedOffer {
  return {
    jobTitle: offer.jobTitle,
    company: offer.company,
    city: offer.city,
    departmentCode: offer.departmentCode,
    contractType: (offer.contractType as ContractType | null) ?? null,
    workMode: offer.workMode,
    publishedOn: offer.publishedOn,
    displayedSalary: offer.salary,
    skills: offer.skills,
    displayedApplicantCount: offer.applicantCount,
  };
}

// LinkedIn Jobs Scraper (curious_coder/linkedin-jobs-scraper) ------------------------------------------------------

/**
 * LinkedIn: the only source that shows the number of applicants (`applicantsCount`), the anchors of the estimate.
 * 0.001 USD per result on the paid plans (0.002 on the free plan), plus 0.00005 USD per start.
 */
export const LINKEDIN_ACTOR: MarketActor = {
  source: "linkedin",
  actorId: "curious_coder~linkedin-jobs-scraper",
  prices: {
    resultEvent: "apify-default-dataset-item",
    resultUsd: { FREE: 0.002, BRONZE: 0.001 },
    startUsdPerGigabyte: 0.00005,
  },
  memoryMegabytes: 1024,
  timeoutSeconds: 240,
  maximumOffers: 100,
  fields: [
    "id",
    "link",
    "title",
    "companyName",
    "location",
    "salaryInfo",
    "postedAt",
    "postedAtTimestamp",
    "applicantsCount",
    "employmentType",
    "workplaceTypes",
    "workRemoteAllowed",
    "descriptionText",
  ],
  buildInput(request, limit) {
    return {
      keywords: searchWords(request.jobTitle),
      location: `${request.city}, France`,
      // Miles; LinkedIn offers 5, 10, 25, 50 and 100.
      distance: nearest(request.radiusKilometers / 1.609, [5, 10, 25, 50, 100]),
      datePosted: "pastMonth",
      scrapeCompany: false,
      splitByLocation: false,
      autoConvertToAiSearch: true,
      limitPerSource: limit,
    };
  },
  mapItem(item, { today }) {
    const identifier = text(item.id) ?? /(\d{6,})/.exec(String(item.link ?? ""))?.[1] ?? null;
    const jobTitle = text(item.title);
    const location = text(item.location, 200);
    if (!identifier || !/^\d{6,}$/.test(identifier) || !jobTitle || isAbroad(location)) return null;
    const salaryText = strings(item.salaryInfo).join(" - ");
    return {
      source: "linkedin",
      externalId: identifier,
      jobTitle,
      company: text(item.companyName),
      city: cityOf(location),
      departmentCode: departmentOf(location),
      publishedOn: parsePublicationDate(item.postedAt ?? item.postedAtTimestamp, today),
      salary: parseSalaryText(salaryText),
      applicantCount: parseApplicantCount(item.applicantsCount),
      contractType: contractTypeOf(text(item.employmentType)),
      workMode: workModeOf(...strings(item.workplaceTypes), item.workRemoteAllowed === true ? "remote" : null),
      skills: skillsInText(typeof item.descriptionText === "string" ? item.descriptionText : null),
      url: `https://www.linkedin.com/jobs/view/${identifier}`,
    };
  },
};

// Indeed Scraper (misceres/indeed-scraper) ---------------------------------------------------------------------------

function mapIndeed(item: DatasetItem, { today }: MappingContext): CollectedOfferInput | null {
  // "No result" is written as an item with an `error` field.
  if (typeof item.error === "string") return null;
  const identifier = text(item.id);
  const jobTitle = text(item.positionName);
  const location = text(item.location, 200);
  if (!identifier || !jobTitle || isAbroad(location)) return null;
  return {
    source: "indeed",
    externalId: identifier,
    jobTitle,
    company: text(item.company),
    city: cityOf(location),
    departmentCode: departmentOf(location),
    publishedOn: parsePublicationDate(item.postingDateParsed ?? item.postedAt, today),
    salary: parseSalaryText(text(item.salary)),
    applicantCount: null,
    contractType: contractTypeOf(...strings(item.jobType)),
    workMode: workModeOf(...strings(item.jobSetting)),
    skills: skillsInText(typeof item.description === "string" ? item.description : null),
    url: /^[0-9a-f]{16}$/i.test(identifier)
      ? `https://fr.indeed.com/viewjob?jk=${identifier.toLowerCase()}`
      : text(item.url, 2048),
  };
}

const INDEED_FIELDS = [
  "id",
  "positionName",
  "company",
  "location",
  "salary",
  "jobType",
  "jobSetting",
  "postingDateParsed",
  "postedAt",
  "url",
  "description",
  "error",
];

const INDEED_PRICES: ActorPrices = {
  resultEvent: "result",
  resultUsd: { FREE: 0.006, BRONZE: 0.005, SILVER: 0.004, GOLD: 0.003, PLATINUM: 0.002, DIAMOND: 0.001 },
  startUsdPerGigabyte: 0,
};

/**
 * Indeed: many offers, salaries often shown, no applicant count. 0.005 USD per result on Starter (3 USD per 1 000 from
 * the Gold tier): the most expensive per result, so it is a complementary source (thin markets only, 30 results) by
 * default.
 */
export const INDEED_ACTOR: MarketActor = {
  source: "indeed",
  actorId: "misceres~indeed-scraper",
  prices: INDEED_PRICES,
  memoryMegabytes: 1024,
  timeoutSeconds: 240,
  // 30 results at most: 0.15 USD on Starter, only for thin markets.
  maximumOffers: 30,
  fields: INDEED_FIELDS,
  buildInput(request, limit) {
    return {
      position: searchWords(request.jobTitle),
      country: "FR",
      location: request.city,
      maxItemsPerSearch: limit,
      parseCompanyDetails: false,
      saveOnlyUniqueItems: true,
    };
  },
  mapItem: mapIndeed,
};

/** Indeed pages refuse direct reading: the same actor reads one offer from its link (about 0.005 USD). */
export const INDEED_OFFER_ACTOR: OfferActor = {
  site: "indeed",
  actorId: "misceres~indeed-scraper",
  prices: INDEED_PRICES,
  memoryMegabytes: 1024,
  timeoutSeconds: 60,
  fields: INDEED_FIELDS,
  buildInput(url) {
    return { startUrls: [{ url }], country: "FR", maxItemsPerSearch: 1, parseCompanyDetails: false };
  },
  mapItem(item, context) {
    const offer = mapIndeed(item, context);
    return offer ? toExtractedOffer(offer) : null;
  },
};

// HelloWork Jobs Scraper (sbzh/hellowork-jobs-scraper) ------------------------------------------------------------

/**
 * HelloWork: French offers, the department is given, salaries often shown. 0.0008 USD per result plus 0.00005 USD
 * per start. The actor returns results by batches and may bring more than asked: `maxItems` caps what is charged.
 */
export const HELLOWORK_ACTOR: MarketActor = {
  source: "hellowork",
  actorId: "sbzh~hellowork-jobs-scraper",
  prices: {
    resultEvent: "apify-default-dataset-item",
    resultUsd: { FREE: 0.0008 },
    startUsdPerGigabyte: 0.00005,
  },
  memoryMegabytes: 1024,
  timeoutSeconds: 240,
  maximumOffers: 100,
  fields: [
    "job_id",
    "job_title",
    "company",
    "location",
    "city",
    "department",
    "country",
    "contract",
    "salary",
    "time",
    "publication_date",
    "link",
    "remote",
    "description",
  ],
  buildInput(request, limit) {
    return {
      keywords: searchWords(request.jobTitle),
      location: request.city,
      // Kilometers; HelloWork offers 5, 10, 20 and 50.
      distance: String(nearest(request.radiusKilometers, [5, 10, 20, 50])),
      period: "30d",
      sort_by: "date",
      limit,
    };
  },
  mapItem(item, { today }) {
    const identifier = text(item.job_id);
    const jobTitle = text(item.job_title);
    const location = text(item.location, 200);
    const country = text(item.country);
    if (!identifier || !jobTitle || (country && !/^france$/i.test(country)) || isAbroad(location)) return null;
    const department = text(item.department, 3)?.toUpperCase() ?? null;
    return {
      source: "hellowork",
      externalId: identifier,
      jobTitle,
      company: text(item.company),
      city: cityOf(text(item.city) ?? location),
      departmentCode: department && /^(\d{2}|2[AB]|9[78]\d)$/.test(department) ? department : departmentOf(location),
      publishedOn: parsePublicationDate(item.publication_date ?? item.time, today),
      salary: parseSalaryText(text(item.salary)),
      applicantCount: null,
      contractType: contractTypeOf(text(item.contract)),
      workMode: workModeOf(text(item.remote)),
      skills: skillsInText(typeof item.description === "string" ? item.description : null),
      url: text(item.link, 2048),
    };
  },
};

// Glassdoor Jobs Scraper (getdataforme/glassdoor-jobs-scraper) ------------------------------------------------------

const GLASSDOOR_PERIODS: Readonly<Record<string, "year" | "month" | "hour" | "day" | "week">> = {
  ANNUAL: "year",
  MONTHLY: "month",
  WEEKLY: "week",
  DAILY: "day",
  HOURLY: "hour",
};

/**
 * Glassdoor: salary ranges (given by the employer or estimated by Glassdoor, 10th to 90th percentile). Expensive:
 * 0.05 USD per start plus 0.009 USD per result, and no input limit (the run cap does the limiting): off by default.
 */
export const GLASSDOOR_ACTOR: MarketActor = {
  source: "glassdoor",
  actorId: "getdataforme~glassdoor-jobs-scraper",
  prices: {
    resultEvent: "apify-default-dataset-item",
    resultUsd: { FREE: 0.009 },
    startUsdPerGigabyte: 0.05,
  },
  memoryMegabytes: 1024,
  timeoutSeconds: 240,
  maximumOffers: 20,
  fields: [
    "listing_id",
    "job_title",
    "employer_name",
    "employerNameFromSearch",
    "location",
    "pay_period",
    "pay_periodAdjustedPay",
    "pay_currency",
    "salary_source",
    "job_url",
  ],
  buildInput(request) {
    return {
      job_name: searchWords(request.jobTitle),
      location: `${request.city}, France`,
      proxyConfiguration: { useApifyProxy: true, apifyProxyGroups: ["RESIDENTIAL"] },
    };
  },
  mapItem(item) {
    const identifier = text(item.listing_id);
    const jobTitle = text(item.job_title);
    const location = text(item.location, 200);
    if (!identifier || !jobTitle || isAbroad(location) || /^remote$/i.test(location ?? "")) return null;
    const pay = isRecord(item.pay_periodAdjustedPay) ? item.pay_periodAdjustedPay : null;
    const period = GLASSDOOR_PERIODS[String(item.pay_period ?? "").toUpperCase()] ?? null;
    const salary = pay && text(item.pay_currency) === "EUR" && typeof pay.p10 === "number" &&
        typeof pay.p90 === "number"
      ? yearlySalaryRange(pay.p10, pay.p90, period)
      : null;
    return {
      source: "glassdoor",
      externalId: identifier,
      jobTitle,
      company: text(item.employerNameFromSearch) ?? text(item.employer_name),
      city: cityOf(location),
      departmentCode: departmentOf(location),
      publishedOn: null,
      salary,
      applicantCount: null,
      contractType: null,
      workMode: null,
      skills: [],
      url: text(item.job_url, 2048),
    };
  },
};

// Welcome to the Jungle Scraper (saswave/welcome-to-the-jungle-scraper) -------------------------------------------

const JUNGLE_CONTRACTS: Readonly<Record<string, ContractType>> = {
  full_time: "permanent",
  temporary: "fixed_term",
  internship: "internship",
  apprenticeship: "apprenticeship",
  freelance: "freelance",
  vie: "fixed_term",
  graduate_program: "permanent",
  other: "other",
};

const JUNGLE_PERIODS: Readonly<Record<string, "year" | "month" | "day" | "hour" | "week">> = {
  yearly: "year",
  monthly: "month",
  weekly: "week",
  daily: "day",
  hourly: "hour",
};

function jungleOffer(item: DatasetItem, today: string): CollectedOfferInput | null {
  const identifier = text(item.reference) ?? text(item.objectID);
  const jobTitle = text(item.name);
  if (!identifier || !jobTitle) return null;
  const offices = (Array.isArray(item.offices) ? item.offices : isRecord(item.office) ? [item.office] : [])
    .filter(isRecord);
  const office = offices.find((candidate) => candidate.country_code === "FR") ?? null;
  if (offices.length > 0 && !office) return null;
  const organization = isRecord(item.organization) ? item.organization : {};
  const currency = text(item.salary_currency);
  const period = JUNGLE_PERIODS[String(item.salary_period ?? "")] ?? null;
  const minimum = typeof item.salary_minimum === "number" ? item.salary_minimum : null;
  const maximum = typeof item.salary_maximum === "number" ? item.salary_maximum : minimum;
  const salary = currency === "EUR" && minimum !== null && maximum !== null
    ? yearlySalaryRange(minimum, maximum, period)
    : null;
  const listedSkills = cleanSkills(item.skills);
  const city = office ? text(office.city, 120) ?? cityOf(text(office.address, 200)) : null;
  const organizationSlug = text(organization.slug, 120);
  const slug = text(item.slug, 200);
  return {
    source: "welcome_to_the_jungle",
    externalId: identifier,
    jobTitle,
    company: text(organization.name),
    city,
    departmentCode: office ? departmentOf(text(office.zip_code, 10), text(office.district), city) : null,
    publishedOn: parsePublicationDate(item.published_at_date ?? item.published_at, today),
    salary,
    applicantCount: null,
    contractType: JUNGLE_CONTRACTS[String(item.contract_type ?? "")] ?? null,
    workMode: workModeOf(text(item.remote)),
    skills: listedSkills.length > 0 ? listedSkills : skillsInText(
      [item.summary, item.profile, item.description, ...strings(item.key_missions)]
        .filter((value) => typeof value === "string").join("\n"),
    ),
    url: text(item.job_url, 2048) ??
      (organizationSlug && slug
        ? `https://www.welcometothejungle.com/fr/companies/${organizationSlug}/jobs/${slug}`
        : null),
  };
}

const JUNGLE_PRICES: ActorPrices = {
  resultEvent: "apify-default-dataset-item",
  resultUsd: { FREE: 0.001, BRONZE: 0.0009, SILVER: 0.0008, GOLD: 0.0007 },
  startUsdPerGigabyte: 0.00005,
};

const JUNGLE_FIELDS = [
  "reference",
  "objectID",
  "name",
  "slug",
  "organization",
  "offices",
  "office",
  "salary_minimum",
  "salary_maximum",
  "salary_currency",
  "salary_period",
  "published_at",
  "published_at_date",
  "contract_type",
  "remote",
  "skills",
  "summary",
  "profile",
  "key_missions",
  "job_url",
];

/**
 * Welcome to the Jungle: start-ups and tech, salaries sometimes, no applicant count. The actor was rented 70 USD per
 * month when the owner chose the sources; it is pay per event since August 2026 (0.0009 USD per result on Starter).
 * Off by default, as the owner asked: add it to `JAUGE_APIFY_SOURCES` to use it.
 */
export const WELCOME_TO_THE_JUNGLE_ACTOR: MarketActor = {
  source: "welcome_to_the_jungle",
  actorId: "saswave~welcome-to-the-jungle-scraper",
  prices: JUNGLE_PRICES,
  memoryMegabytes: 1024,
  timeoutSeconds: 240,
  maximumOffers: 60,
  fields: JUNGLE_FIELDS,
  buildInput(request) {
    const query = new URLSearchParams({ query: searchWords(request.jobTitle), aroundQuery: `${request.city}, France` });
    query.append("refinementList[offices.country_code][]", "FR");
    // One page of search results (about 30 offers).
    return { search_job: [`https://www.welcometothejungle.com/fr/jobs?${query}`], max_page: 1 };
  },
  mapItem(item, { today }) {
    return jungleOffer(item, today);
  },
};

/** Welcome to the Jungle reads one offer from its link (about 0.001 USD), when its page cannot be read directly. */
export const WELCOME_TO_THE_JUNGLE_OFFER_ACTOR: OfferActor = {
  site: "welcome_to_the_jungle",
  actorId: "saswave~welcome-to-the-jungle-scraper",
  prices: JUNGLE_PRICES,
  memoryMegabytes: 1024,
  timeoutSeconds: 60,
  fields: JUNGLE_FIELDS,
  buildInput(url) {
    return { jobs: [url], max_page: 1 };
  },
  mapItem(item, { today }) {
    const offer = jungleOffer(item, today);
    return offer ? toExtractedOffer(offer) : null;
  },
};

/** Market actors by source name. */
export const MARKET_ACTORS: Readonly<Record<SourceName, MarketActor>> = {
  linkedin: LINKEDIN_ACTOR,
  indeed: INDEED_ACTOR,
  hellowork: HELLOWORK_ACTOR,
  glassdoor: GLASSDOOR_ACTOR,
  welcome_to_the_jungle: WELCOME_TO_THE_JUNGLE_ACTOR,
};

/** Actors that read one offer, by site (`canonicalizeLink` site names). */
export const OFFER_ACTORS: Readonly<Record<string, OfferActor>> = {
  indeed: INDEED_OFFER_ACTOR,
  welcome_to_the_jungle: WELCOME_TO_THE_JUNGLE_OFFER_ACTOR,
};
