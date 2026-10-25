import { normalizeSearchText } from "../text/search_text.ts";
import { cityKey } from "../geography/cities.ts";

/** Kind of normalized key: link of an offer, manual entry, or offer read from a text or an image. */
export type OfferKeyKind = "link" | "manual" | "text";

/** A normalized offer key (`link:<sha256>`), the key of the 7 day cache. */
export interface OfferKey {
  /** Key stored in `offers.normalized_key`. */
  readonly value: string;
  /** Kind of key. */
  readonly kind: OfferKeyKind;
}

/** Query parameters that never identify an offer (tracking, sessions, referrers). */
const TRACKING_PARAMETERS = new Set([
  "utm_source",
  "utm_medium",
  "utm_campaign",
  "utm_term",
  "utm_content",
  "utm_id",
  "gclid",
  "fbclid",
  "msclkid",
  "trk",
  "trackingid",
  "refid",
  "ref",
  "referer",
  "referrer",
  "src",
  "source",
  "from",
  "origin",
  "position",
  "pagenum",
  "searchid",
  "sid",
  "session",
  "cmp",
  "campaign",
  "xtor",
  "at_medium",
  "at_campaign",
  "mc_cid",
  "mc_eid",
  "ebp",
  "lipi",
  "currentjobid",
  "tk",
  "advn",
  "adid",
  "vjk",
  "from_search",
  "page",
  "q",
  "l",
  "rank",
]);

/** Known job sites and how to keep only the identifier of the offer in their links. */
const SITE_RULES: readonly { site: string; host: RegExp; identify: (url: URL) => string | null }[] = [
  {
    site: "linkedin",
    host: /(^|\.)linkedin\.com$/,
    identify: (url) => {
      const fromPath = /\/jobs\/view\/(?:[^/]*?-)?(\d{6,})/.exec(url.pathname)?.[1];
      const fromQuery = url.searchParams.get("currentJobId");
      const identifier = fromPath ?? (fromQuery && /^\d{6,}$/.test(fromQuery) ? fromQuery : null);
      return identifier ? `https://www.linkedin.com/jobs/view/${identifier}` : null;
    },
  },
  {
    site: "indeed",
    host: /(^|\.)indeed\.(com|fr)$/,
    identify: (url) => {
      const identifier = url.searchParams.get("jk") ?? url.searchParams.get("vjk");
      return identifier && /^[0-9a-f]{16}$/i.test(identifier)
        ? `https://fr.indeed.com/viewjob?jk=${identifier.toLowerCase()}`
        : null;
    },
  },
  {
    site: "welcome_to_the_jungle",
    host: /(^|\.)welcometothejungle\.com$/,
    identify: (url) => {
      const match = /\/companies\/([^/]+)\/jobs\/([^/?#]+)/.exec(url.pathname);
      return match ? `https://www.welcometothejungle.com/fr/companies/${match[1]}/jobs/${match[2]}` : null;
    },
  },
  {
    site: "hellowork",
    host: /(^|\.)hellowork\.com$/,
    identify: (url) => {
      const identifier = /\/emplois\/(\d+)\.html/.exec(url.pathname)?.[1];
      return identifier ? `https://www.hellowork.com/fr-fr/emplois/${identifier}.html` : null;
    },
  },
  {
    site: "glassdoor",
    host: /(^|\.)glassdoor\.(com|fr)$/,
    identify: (url) => {
      const identifier = url.searchParams.get("jobListingId") ?? /_JV_.*?KO\d+,\d+_.*?(\d{8,})/.exec(url.pathname)?.[1];
      return identifier && /^\d{6,}$/.test(identifier)
        ? `https://www.glassdoor.fr/job-listing/?jl=${identifier}`
        : null;
    },
  },
  {
    site: "france_travail",
    host: /(^|\.)(francetravail\.fr|pole-emploi\.fr)$/,
    identify: (url) => {
      const identifier = /\/offres\/recherche\/detail\/([0-9A-Z]{6,8})/i.exec(url.pathname)?.[1];
      return identifier
        ? `https://candidat.francetravail.fr/offres/recherche/detail/${identifier.toUpperCase()}`
        : null;
    },
  },
  {
    site: "apec",
    host: /(^|\.)apec\.fr$/,
    identify: (url) => {
      const identifier = /\/detail-offre\/([0-9A-Z]+)/i.exec(url.pathname)?.[1];
      return identifier ? `https://www.apec.fr/candidat/recherche-emploi.html/emploi/detail-offre/${identifier}` : null;
    },
  },
];

/** A link as understood by the API. */
export interface CanonicalLink {
  /** Canonical URL: lower case host, no tracking parameter, no fragment, identifier only for known sites. */
  readonly url: string;
  /** Known job site, or null for another site (career page...). */
  readonly site: string | null;
}

/**
 * Parses and canonicalizes the link of an offer. Returns null when the text is not an http(s) link (`invalid_link`).
 * Local and private addresses are refused too: the server never fetches them.
 *
 * @param text link pasted by the user.
 */
export function canonicalizeLink(text: string): CanonicalLink | null {
  let url: URL;
  try {
    url = new URL(text.trim());
  } catch {
    return null;
  }
  if (url.protocol !== "https:" && url.protocol !== "http:") return null;
  if (url.username || url.password) return null;
  const host = url.hostname.toLowerCase().replace(/\.$/, "");
  if (!host.includes(".") || isPrivateHost(host)) return null;
  url.hostname = host;
  url.protocol = "https:";
  url.hash = "";
  url.port = "";
  for (const rule of SITE_RULES) {
    if (rule.host.test(host)) {
      const identified = rule.identify(url);
      if (identified) return { url: identified, site: rule.site };
      return { url: stripTracking(url), site: rule.site };
    }
  }
  return { url: stripTracking(url), site: null };
}

function stripTracking(url: URL): string {
  const kept = [...url.searchParams.entries()]
    .filter(([name]) => !TRACKING_PARAMETERS.has(name.toLowerCase()) && !name.toLowerCase().startsWith("utm_"))
    .sort(([first], [second]) => first.localeCompare(second));
  const copy = new URL(url.toString());
  copy.search = "";
  for (const [name, value] of kept) copy.searchParams.append(name, value);
  const path = copy.pathname.length > 1 ? copy.pathname.replace(/\/+$/, "") : copy.pathname;
  return `${copy.protocol}//${copy.hostname}${path}${copy.search}`;
}

function isPrivateHost(host: string): boolean {
  if (host === "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) {
    return true;
  }
  if (/^\[?[0-9a-f:]+\]?$/i.test(host) && host.includes(":")) return true;
  const ipv4 = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(host);
  if (!ipv4) return false;
  const [first, second] = [Number(ipv4[1]), Number(ipv4[2])];
  return first === 10 || first === 127 || first === 0 || (first === 169 && second === 254) ||
    (first === 172 && second >= 16 && second <= 31) || (first === 192 && second === 168) || first >= 224;
}

/** Words that do not change the job: gender marks, contracts, seniority, filler words. */
const IGNORED_TITLE_WORDS = new Set([
  "h",
  "f",
  "hf",
  "fh",
  "x",
  "e",
  "cdi",
  "cdd",
  "stage",
  "stagiaire",
  "alternance",
  "alternant",
  "alternante",
  "interim",
  "freelance",
  "junior",
  "senior",
  "confirme",
  "confirmee",
  "experimente",
  "experimentee",
  "debutant",
  "debutante",
  "temps",
  "plein",
  "partiel",
  "de",
  "du",
  "des",
  "la",
  "le",
  "les",
  "l",
  "d",
  "en",
  "et",
  "a",
  "au",
  "aux",
  "un",
  "une",
  "pour",
  "sur",
  "the",
  "and",
  "of",
  "in",
  "with",
  "mois",
  "an",
  "ans",
]);

/**
 * Job family of a title: the comparison key with the other offers ("Chargé·e de marketing digital (H/F) - CDI" and
 * "Chargé de marketing digital" both give "charge marketing digital"). Feminine endings are folded to one spelling.
 *
 * @param title job title.
 */
export function jobFamily(title: string): string {
  const words = normalizeSearchText(title)
    .replace(/\(.*?\)/g, " ")
    .replace(/[^a-z0-9+#]+/g, " ")
    .split(" ")
    .filter((word) => word.length > 0 && !IGNORED_TITLE_WORDS.has(word) && !/^\d+$/.test(word))
    .map(foldFeminine);
  const family = words.slice(0, 5).join(" ");
  return family.length > 0 ? family.slice(0, 120) : "autre";
}

function foldFeminine(word: string): string {
  if (word.length <= 4) return word;
  if (word.endsWith("trice")) return `${word.slice(0, -5)}teur`;
  if (word.endsWith("euse")) return `${word.slice(0, -4)}eur`;
  if (word.endsWith("ienne")) return word.slice(0, -2);
  if (word.endsWith("ere")) return `${word.slice(0, -3)}er`;
  if (word.endsWith("ee")) return word.slice(0, -1);
  return word;
}

/** SHA-256 of a text, in hexadecimal. */
export async function sha256Hex(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

/** Key of an offer known by its canonical link. */
export async function linkOfferKey(link: CanonicalLink): Promise<OfferKey> {
  return { kind: "link", value: `link:${await sha256Hex(link.url)}` };
}

/** Key of a manual entry: job family and city. */
export async function manualOfferKey(jobTitle: string, city: string): Promise<OfferKey> {
  return { kind: "manual", value: `manual:${await sha256Hex(`${jobFamily(jobTitle)}|${cityKey(city)}`)}` };
}

/** Key of an offer read from a text or an image: job family, company and city. */
export async function textOfferKey(jobTitle: string, company: string | null, city: string): Promise<OfferKey> {
  const companyKey = company ? normalizeSearchText(company) : "";
  return { kind: "text", value: `text:${await sha256Hex(`${jobFamily(jobTitle)}|${companyKey}|${cityKey(city)}`)}` };
}
