/**
 * Checks of the texts written by the AI before they reach a user. The schema checks the shape; these check the
 * content: no number that Jauge did not compute, no link, no username or contact detail, no long dashes.
 */

import { normalizeSearchText } from "../text/search_text.ts";

/** The two long dashes (em dash, en dash) that the texts of Jauge never use, written by their code so that no source file of Jauge contains them. */
export const LONG_DASH_CHARACTERS = String.fromCharCode(0x2014) + String.fromCharCode(0x2013);

const LONG_DASHES = new RegExp(`\\s*[${LONG_DASH_CHARACTERS}]\\s*`, "g");

/**
 * Replaces the long dashes by a comma (the models like them, the brand of Jauge does not).
 *
 * @param text text of the AI.
 */
export function removeLongDashes(text: string): string {
  return text.replace(LONG_DASHES, ", ").replace(/\s+,/g, ",").replace(/,\s*,/g, ",").trim();
}

/**
 * Numbers written in a text, as plain digits ("1 200" and "1.200" give "1200", "2,5" gives "2.5").
 *
 * @param text any text.
 */
export function numbersIn(text: string): string[] {
  const joined = text.replace(/(\d)[\s  .](?=\d{3}\b)/g, "$1");
  return [...joined.matchAll(/\d+(?:[.,]\d+)?/g)].map((match) => String(Number(match[0].replace(",", "."))));
}

/**
 * Numbers of a text that are not among the allowed ones (the facts computed by Jauge).
 *
 * @param text text of the AI.
 * @param allowed allowed numbers (as given by `numbersIn` on the facts).
 */
export function unknownNumbers(text: string, allowed: ReadonlySet<string>): string[] {
  return numbersIn(text).filter((value) => !allowed.has(value));
}

/** True when the text contains a link or a domain name. */
export function containsLink(text: string): boolean {
  return /https?:\/\/|www\.|\b[a-z0-9-]+\.(?:com|fr|io|net|org|ly|gg|co|me|xyz|info)\b/i.test(text);
}

/** True when the text contains a username, a handle, an email address or a phone number. */
export function containsPersonalContact(text: string): boolean {
  return /(^|[^\w])@[A-Za-z0-9_]{2,}/.test(text) ||
    /\bu\/[A-Za-z0-9_-]{2,}/i.test(text) ||
    /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/.test(text) ||
    /(?:\+33|\b0)[1-9](?:[\s.-]?\d{2}){4}\b/.test(text);
}

const WEEKDAYS: Readonly<Record<string, readonly string[]>> = {
  MONDAY: ["lundi", "monday"],
  TUESDAY: ["mardi", "tuesday"],
  WEDNESDAY: ["mercredi", "wednesday"],
  THURSDAY: ["jeudi", "thursday"],
  FRIDAY: ["vendredi", "friday"],
  SATURDAY: ["samedi", "saturday"],
  SUNDAY: ["dimanche", "sunday"],
};

/**
 * Weekdays named in a text other than the allowed one (the advice may only name the day computed by Jauge).
 *
 * @param text text of the AI.
 * @param allowedDay `THURSDAY`... or null when no day may be named.
 */
export function unexpectedWeekdays(text: string, allowedDay: string | null): string[] {
  const words = new Set(normalizeSearchText(text).split(/[^a-z]+/));
  return Object.entries(WEEKDAYS)
    .filter(([day, names]) => day !== allowedDay && names.some((name) => words.has(name)))
    .map(([day]) => day);
}

/** Words that name the public posts behind the internal context of the advice, which a user must never read. */
const SOCIAL_SOURCE_WORDS = new Set([
  "reddit",
  "subreddit",
  "twitter",
  "tweet",
  "tweets",
  "reputation",
  "avis",
  "posts",
  "forum",
  "forums",
]);

/**
 * Names of the social sources or of the reputation in a text (the advice uses them as internal context only and never
 * cites them: no "Reddit", no "X", no "public posts", no "reputation").
 *
 * @param text text of the AI.
 */
export function socialSourceWords(text: string): string[] {
  const folded = normalizeSearchText(text);
  const words = folded.split(/[^a-z0-9]+/).filter((word) => word.length > 0);
  const found = new Set(words.filter((word) => SOCIAL_SOURCE_WORDS.has(word)));
  if (/(^|[^a-z0-9])(sur|on) x([^a-z0-9]|$)/.test(folded)) found.add("x");
  if (/reseaux? sociaux|social (media|network)/.test(folded)) found.add("social media");
  return [...found];
}

/**
 * First problem of a text written for a user, or null when it may be shown.
 *
 * @param text text (long dashes already removed).
 * @param allowedNumbers numbers the text may contain.
 */
export function textProblem(text: string, allowedNumbers: ReadonlySet<string>): string | null {
  if (containsLink(text)) return "contains a link";
  if (containsPersonalContact(text)) return "contains a username or contact detail";
  const unknown = unknownNumbers(text, allowedNumbers);
  if (unknown.length > 0) return `contains numbers that are not in the facts: ${unknown.slice(0, 3).join(", ")}`;
  return null;
}
