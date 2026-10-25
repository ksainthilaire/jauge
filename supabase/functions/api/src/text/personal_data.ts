/**
 * Removes the personal data an offer text may hold before the AI reads it: e-mail addresses, phone numbers (French and
 * international formats) and the names of people when they are found without doubt (after "Contact :", "Recruteur :",
 * a civility such as "Mme", or in a signature such as "Marie Dupont, Talent Acquisition"). The app already does it on
 * the phone (`core/domain/.../PersonalData.kt`, same rules): this is the defense of the server.
 *
 * What the analysis needs is always kept: job title, company, city, salary, skills, dates, offer references and links.
 */
import { BRANDS, FIRST_NAMES, STOP_WORDS } from "./name_words.ts";

/** Replaces an e-mail address. */
export const EMAIL_MARKER = "[e-mail retiré]";
/** Replaces a phone number. */
export const PHONE_MARKER = "[téléphone retiré]";
/** Replaces the name of a person. */
export const NAME_MARKER = "[nom retiré]";

const FRENCH_SPEAKING_COUNTRY_CODES = new Set(["33", "32", "41", "352", "377", "262", "590", "594", "596"]);
const PARTICLES = new Set(["de", "du", "des", "van", "von", "der", "den", "di", "da", "le", "la"]);

const SEPARATOR = "[ .\\-\\u00A0\\u202F]";
const WORD = "(?:\\p{Lu}\\.|\\p{Lu}[\\p{Ll}\\p{Lu}'’]*(?:-\\p{Lu}[\\p{Ll}\\p{Lu}'’]*)*)";
const PARTICLE = "(?:[dD]e|[dD]u|[dD]es|[vV]an|[vV]on|[dD]er|[dD]en|[dD]i|[dD]a|[lL]e|[lL]a)";
const CIVILITY = "(?:M\\.|Mr\\.?|Mme\\.?|Mlle\\.?|MME|MLLE|Madame|Monsieur|Mademoiselle|Mrs\\.?|Ms\\.?)";
const NAME = `(?:(${CIVILITY})[ \\t]+)?${WORD}(?:[ \\t]+(?:${PARTICLE}[ \\t]+){0,2}${WORD}|[ \\t]+d['’]${WORD}){0,3}`;

const EMAIL = /[A-Za-z0-9._%+'\-]+@[A-Za-z0-9\-]+(?:\.[A-Za-z0-9\-]+)*\.[A-Za-z]{2,}/g;
const LINK = /(?:https?:\/\/|www\.)\S+|[A-Za-z0-9\-]+(?:\.[A-Za-z0-9\-]+)*\.[A-Za-z]{2,}\/\S*/gi;
/** A French number: 10 digits starting with 0, in pairs with one same separator (or none). */
const FRENCH_PHONE = new RegExp(
  `(?<![\\p{L}\\p{N}+/_\\-])0[1-9](${SEPARATOR}?)\\d{2}(?:\\1\\d{2}){3}(?![\\p{L}\\p{N}/_\\-]|[.,]\\d)`,
  "gu",
);
/** An international number: `+` or `00` and a country code, then 6 to 12 digits with separators. */
const INTERNATIONAL_PHONE = new RegExp(
  `(?<![\\p{L}\\p{N}+])(?:\\+[1-9]\\d{0,2}|00[1-9]\\d{0,2})${SEPARATOR}?(?:\\(\\d{1,4}\\)${SEPARATOR}?)?\\d(?:${SEPARATOR}?\\d){5,12}(?!\\p{N})`,
  "gu",
);
const AMOUNT_AFTER = /^[   ]*(?:€|\$|%|£|k\b|k€|euros?\b|eur\b|usd\b|chf\b)/i;
const REFERENCE_BEFORE =
  /(?<!\p{L})(?:r[ée]f[ée]?r?e?n?c?e?|n°|no\.|num[ée]ro|siret|siren|id|code|offre)[^\n]{0,12}$/iu;
const PHONE_BEFORE = /(?:t[ée]l|phone|portable|mobile|appel|joindre|fixe)[^\n]{0,20}$/i;

const NAME_AT = new RegExp(NAME, "uy");
const WORD_IN_NAME = new RegExp(WORD, "gu");
const NAME_BEFORE_ROLE = new RegExp(
  `${WORD}(?:[ \\t]+(?:${PARTICLE}[ \\t]+){0,2}${WORD}|[ \\t]+d['’]${WORD}){0,4}[ \\t]*[,|·\\-\\u2013\\u2014]`,
  "gu",
);
const CIVILITY_ANYWHERE = new RegExp(`(?<![\\p{L}\\p{N}])${CIVILITY}[ \\t]+(?=\\p{Lu})`, "gu");
const LABEL_SOURCE = "(?<!\\p{L})(?:contact(?:ez|er)?|votre contact|personne [àa] contacter|interlocut(?:eur|rice)|" +
  "r[ée]f[ée]rente?|recruteu(?:r|se)|recruiter|charg[ée]e? (?:de|du) recrutement|responsable (?:du |des )?recrutement|" +
  "responsable rh|talent acquisition(?: manager| partner| specialist)?|hiring manager|posted by|publi[ée]e? par|" +
  "auteur de l'offre|job poster|aupr[èe]s de|s'adresser [àa]|[ée]crire [àa]|" +
  "envoy(?:er|ez) (?:votre candidature|votre cv|vos candidatures) [àa])[ \\t]*(?:[:\\-\\u2013\\u2014][ \\t]*)?";
const LABEL = new RegExp(LABEL_SOURCE, "giu");
const LABEL_AT_END = new RegExp(`(?:${LABEL_SOURCE}|[ée]quipe de recrutement|hiring team)[ \\t]*:?[ \\t]*$`, "iu");
const ROLE = new RegExp(
  "^[ \\t·•|,\\-\\u2013\\u2014]*(?:talent acquisition|recruteu(?:r|se)|recruiter|recruitment|recrutement|" +
    "charg[ée]e? (?:de|du) recrutement|responsable (?:du |des )?recrutement|responsable rh|" +
    "responsable des ressources humaines|directeur des ressources humaines|directrice des ressources humaines|" +
    "ressources humaines|human resources|(?:rh|hr|drh)(?!\\p{L})|people|hiring|job poster|auteur de l'offre|" +
    "ceo(?!\\p{L})|co-?fondat(?:eur|rice)|fondat(?:eur|rice)|founder|co-?founder|" +
    "head of (?:people|talent|recruitment|hr)|g[ée]rante?|assistante? rh|consultante? (?:en )?recrutement|" +
    "hr business partner|business partner rh)",
  "iu",
);
const STANDALONE_SUFFIX = /^[ \t]*(?:[,|·•\-\u2013\u2014][ \t]*(.*))?$/u;
const CONNECTION_DEGREE = /^(?:1er|2e|3e|1st|2nd|3rd|\d+e?)$/i;

type Range = { start: number; end: number };

/**
 * Returns `text` without its personal data.
 *
 * @param text text of an offer (read on a capture, pasted or typed).
 * @returns the same text with every e-mail address, phone number and safely found person name replaced by a marker.
 */
export function scrubPersonalData(text: string): string {
  if (text.trim() === "") return text;
  const withoutEmails = text.replace(EMAIL, EMAIL_MARKER);
  return removeNames(removePhones(withoutEmails));
}

/** Lower case without accents, for comparisons with the word lists. */
export function foldName(text: string): string {
  return text.toLowerCase().normalize("NFD").replace(/\p{M}/gu, "").replace(/’/g, "'").replace(/\s+/g, " ");
}

function linkRanges(text: string): Range[] {
  return [...text.matchAll(LINK)].map((match) => ({ start: match.index, end: match.index + match[0].length }));
}

function overlaps(ranges: Range[], start: number, end: number): boolean {
  return ranges.some((range) => range.start < end && start < range.end);
}

function replaceOutsideLinks(
  text: string,
  regex: RegExp,
  replacement: (match: RegExpExecArray) => string | null,
): string {
  const links = linkRanges(text);
  return text.replace(regex, (...parameters) => {
    const value = parameters[0] as string;
    const groupsEnd = parameters.findIndex((parameter, index) => index > 0 && typeof parameter === "number");
    const offset = parameters[groupsEnd] as number;
    if (overlaps(links, offset, offset + value.length)) return value;
    const match = Object.assign([value, ...parameters.slice(1, groupsEnd)], { index: offset, input: text });
    return replacement(match as unknown as RegExpExecArray) ?? value;
  });
}

function removePhones(text: string): string {
  const international = replaceOutsideLinks(
    text,
    INTERNATIONAL_PHONE,
    (match) => looksLikeInternationalPhone(text, match) ? PHONE_MARKER : null,
  );
  return replaceOutsideLinks(international, FRENCH_PHONE, (match) => {
    const separated = (match[1] ?? "") !== "";
    const before = international.slice(Math.max(0, match.index - 40), match.index);
    if (!separated && REFERENCE_BEFORE.test(before) && !PHONE_BEFORE.test(before)) return null;
    return PHONE_MARKER;
  });
}

function looksLikeInternationalPhone(text: string, match: RegExpExecArray): boolean {
  const groups = match[0].match(/\d+/g) ?? [];
  const digits = groups.reduce((total, group) => total + group.length, 0);
  if (digits < 8 || digits > 15) return false;
  const afterCountryCode = groups.slice(1);
  const frenchSpeakingNumber = FRENCH_SPEAKING_COUNTRY_CODES.has((groups[0] ?? "").replace(/^0+/, "")) &&
    afterCountryCode.reduce((total, group) => total + group.length, 0) === 9;
  // "+25 000 000" or "00 500 000": thousands of an amount, not a phone number.
  if (!frenchSpeakingNumber && afterCountryCode.length >= 2 && afterCountryCode.every((group) => group.length === 3)) {
    return false;
  }
  return !AMOUNT_AFTER.test(text.slice(match.index + match[0].length));
}

function removeNames(text: string): string {
  const lines = text.split("\n");
  return lines.map((_, index) => removeNamesInLine(lines, index)).join("\n");
}

function removeNamesInLine(lines: string[], index: number): string {
  const line = lines[index];
  if (line.trim() === "") return line;
  const ranges: Range[] = [];
  const add = (range: Range | null) => {
    if (range) ranges.push(range);
  };
  add(standaloneName(lines, index));
  for (const found of line.matchAll(LABEL)) add(nameAt(line, found.index + found[0].length));
  for (const found of line.matchAll(CIVILITY_ANYWHERE)) add(nameAt(line, found.index));
  for (const found of line.matchAll(NAME_BEFORE_ROLE)) add(signatureIn(line, found));
  if (ranges.length === 0) return line;
  const links = linkRanges(line);
  const kept = ranges.filter((range) => !overlaps(links, range.start, range.end)).sort((a, b) => a.start - b.start);
  const merged: Range[] = [];
  for (const range of kept) {
    const last = merged[merged.length - 1];
    if (last && range.start <= last.end) last.end = Math.max(last.end, range.end);
    else merged.push({ ...range });
  }
  let result = line;
  for (const range of merged.reverse()) result = result.slice(0, range.start) + NAME_MARKER + result.slice(range.end);
  return result;
}

/**
 * A line that is only a name, with a recruiting role on the same line ("Marie Dupont, Talent Acquisition") or on the
 * next lines, or under a "Contact" or "hiring team" title.
 */
function standaloneName(lines: string[], index: number): Range | null {
  const line = lines[index];
  const start = line.search(/\S/);
  const name = nameAt(line, start);
  if (!name) return null;
  const suffix = STANDALONE_SUFFIX.exec(line.slice(name.end));
  if (!suffix) return null;
  const rest = (suffix[1] ?? "").trim();
  const roleOnLine = rest !== "" && ROLE.test(rest);
  if (rest !== "" && !roleOnLine && !CONNECTION_DEGREE.test(rest)) return null;
  const following = lines.slice(index + 1).filter((other) => other.trim() !== "")
    .filter((other) => !CONNECTION_DEGREE.test(other.trim())).slice(0, 2);
  const previous = lines.slice(0, index).filter((other) => other.trim() !== "").pop();
  const inContext = roleOnLine || following.some((other) => ROLE.test(other)) ||
    (previous !== undefined && LABEL_AT_END.test(previous));
  return inContext ? name : null;
}

/** "avec Marie Dupont, Talent Acquisition": a name followed by a separator and a recruiting role, inside a line. */
function signatureIn(line: string, found: RegExpExecArray | RegExpMatchArray): Range | null {
  const separator = (found.index ?? 0) + found[0].length - 1;
  if (!ROLE.test(line.slice(separator + 1))) return null;
  const words = new RegExp(WORD, "gu");
  words.lastIndex = found.index ?? 0;
  const head = line.slice(0, separator);
  for (let word = words.exec(head); word; word = words.exec(head)) {
    const name = nameAt(line, word.index);
    if (name && line.slice(name.end, separator).trim() === "") return name;
  }
  return null;
}

/**
 * The name of a person starting at `start` in `line`, or null. Without a civility, the first word must be a known
 * first name followed by a last name, and the whole must not be a brand named after a person ("Yves Rocher").
 */
function nameAt(line: string, start: number): Range | null {
  if (start < 0 || start >= line.length) return null;
  NAME_AT.lastIndex = start;
  const match = NAME_AT.exec(line);
  if (!match) return null;
  const civility = match[1];
  const wordsStart = civility ? start + match[0].indexOf(civility) + civility.length : start;
  WORD_IN_NAME.lastIndex = wordsStart;
  const area = line.slice(0, start + match[0].length);
  const words: RegExpExecArray[] = [];
  for (let word = WORD_IN_NAME.exec(area); word; word = WORD_IN_NAME.exec(area)) words.push(word);
  const limit = civility ? 3 : 2;
  const kept: RegExpExecArray[] = [];
  for (const word of words) {
    const folded = foldName(word[0].replace(/\.+$/, ""));
    if (STOP_WORDS.has(folded) || kept.length === limit) break;
    // A capitalized particle ("de La Fontaine", "Le Gall") belongs to the next word.
    if (PARTICLES.has(folded) && word !== words[words.length - 1]) continue;
    kept.push(word);
  }
  if (kept.length === 0) return null;
  if (!civility) {
    if (kept.length < 2) return null;
    if (!FIRST_NAMES.has(foldName(kept[0][0].split("-")[0]))) return null;
  }
  const end = kept[kept.length - 1].index + kept[kept.length - 1][0].length;
  if (BRANDS.has(foldName(line.slice(wordsStart, end)))) return null;
  return { start, end };
}
