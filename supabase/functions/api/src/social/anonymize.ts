/**
 * Anonymization of public posts before the AI reads them: no usernames, handles, emails, phone numbers or links,
 * short extracts only. Posts are never stored; only the summary of the AI and the links to the posts are kept.
 */

/** Longest extract of a post given to the AI. */
export const MAXIMUM_EXTRACT_LENGTH = 400;

/**
 * Removes what identifies a person from the text of a post and cuts it.
 *
 * @param text text of the post.
 */
export function anonymizePost(text: string): string {
  const cleaned = text
    .replace(/https?:\/\/\S+|www\.\S+/gi, "[lien]")
    .replace(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g, "[email]")
    .replace(/(^|[^\w])@[A-Za-z0-9_]{2,}/g, "$1[pseudo]")
    .replace(/\b\/?u\/[A-Za-z0-9_-]{2,}/gi, "[pseudo]")
    .replace(/(?:\+\d{2}\s?|\b0)[1-9](?:[\s.-]?\d{2}){4}\b/g, "[téléphone]")
    .replace(/\s+/g, " ")
    .trim();
  if (cleaned.length <= MAXIMUM_EXTRACT_LENGTH) return cleaned;
  const cut = cleaned.slice(0, MAXIMUM_EXTRACT_LENGTH);
  const lastSpace = cut.lastIndexOf(" ");
  return `${cut.slice(0, lastSpace > 300 ? lastSpace : MAXIMUM_EXTRACT_LENGTH)}...`;
}

/**
 * Link of a post without the name of its author. X links carry the handle (`x.com/<handle>/status/<id>`): they
 * become `https://x.com/i/status/<id>`, which opens the same post. Reddit links carry no username.
 *
 * @param source source of the post.
 * @param url link given by the source.
 */
export function anonymousPostLink(source: "reddit" | "x", url: string): string | null {
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return null;
  }
  if (parsed.protocol !== "https:") return null;
  if (source === "x") {
    const status = /\/status(?:es)?\/(\d{5,25})/.exec(parsed.pathname);
    return status ? `https://x.com/i/status/${status[1]}` : null;
  }
  if (!/(^|\.)reddit\.com$/.test(parsed.hostname)) return null;
  if (/^\/(user|u)\//.test(parsed.pathname)) return null;
  return `https://www.reddit.com${parsed.pathname}`;
}
