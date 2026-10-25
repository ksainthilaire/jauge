/** Marks of inclusive writing and soft separators, removed so that "chargé·e" reads as "chargee". */
const IGNORED_MARKS = /[·‧⋅•­]/g;

/** Letters that NFD does not decompose. */
const SPECIAL_LETTERS: Readonly<Record<string, string>> = {
  "œ": "oe",
  "æ": "ae",
  "ß": "ss",
  "ø": "o",
  "đ": "d",
  "ł": "l",
  "ı": "i",
  "ħ": "h",
  "ŧ": "t",
};

/**
 * Folds a text for comparison, with the same rules as the app (`core/common/.../text/SearchText.kt`): lower case,
 * accents removed, ligatures spelled out, middle dots of inclusive writing removed, blanks collapsed.
 *
 * @param text text to fold.
 */
export function normalizeSearchText(text: string): string {
  return text
    .toLowerCase()
    .replace(IGNORED_MARKS, "")
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[œæßøđłıħŧ]/g, (letter) => SPECIAL_LETTERS[letter] ?? letter)
    .replace(/\s+/g, " ")
    .trim();
}

/** Words of a query, folded. A blank query has no word (and matches everything). */
export function searchWords(query: string | null | undefined): string[] {
  if (!query) return [];
  return normalizeSearchText(query).split(" ").filter((word) => word.length > 0).slice(0, 10);
}

/**
 * True when every word of the query appears in one of the fields, in any order.
 *
 * @param words folded words of the query.
 * @param fields searchable texts (job title, company, city), null fields skipped.
 */
export function matchesSearch(words: readonly string[], ...fields: (string | null | undefined)[]): boolean {
  if (words.length === 0) return true;
  const haystack = fields.filter((field): field is string => !!field).map(normalizeSearchText).join(" ");
  return words.every((word) => haystack.includes(word));
}

/** Escapes `%`, `_` and `\` for a SQL `like` pattern. */
export function escapeLikePattern(word: string): string {
  return word.replace(/[\\%_]/g, (character) => `\\${character}`);
}
