import type { ReliabilityReason } from "./estimation.ts";

/** Languages of the texts written by the API. */
export type Language = "fr" | "en";

/**
 * Language of a request from `Accept-Language` (French by default, English when English comes first).
 *
 * @param header value of the header.
 */
export function languageFromHeader(header: string | null): Language {
  if (!header) return "fr";
  const preferred = header
    .split(",")
    .map((part) => {
      const [tag, ...parameters] = part.trim().split(";");
      const quality = parameters.map((parameter) => /^q=([0-9.]+)$/.exec(parameter.trim())?.[1]).find(Boolean);
      return { tag: tag.toLowerCase(), quality: quality ? Number(quality) : 1 };
    })
    .filter((entry) => entry.tag.length > 0)
    .sort((first, second) => second.quality - first.quality);
  for (const entry of preferred) {
    if (entry.tag.startsWith("fr")) return "fr";
    if (entry.tag.startsWith("en")) return "en";
  }
  return "fr";
}

/**
 * Text of a reliability reason, as shown under "Pourquoi « moyenne » ici".
 *
 * @param reason reason code.
 * @param language language of the user.
 * @param daysOnline days since publication, when known.
 */
export function reliabilityReasonText(
  reason: ReliabilityReason,
  language: Language,
  daysOnline: number | null,
): string {
  const days = daysOnline ?? 0;
  if (language === "en") {
    switch (reason) {
      case "applicants_shown_by_site":
        return "the site of the offer shows its number of applicants";
      case "many_comparable_offers":
        return "many comparable offers with a known number of applicants";
      case "few_comparable_offers":
        return "few comparable offers with a known number of applicants";
      case "recent_offer":
        return days <= 1 ? "offer online for 1 day only" : `offer online for ${days} days only`;
      case "unknown_publication_date":
        return "publication date unknown";
      case "displayed_salary":
        return "salary shown in the offer";
      case "no_displayed_salary":
        return "no salary shown";
      case "partial_sources":
        return "some sources could not be read today";
    }
  }
  switch (reason) {
    case "applicants_shown_by_site":
      return "le site de l'offre affiche son nombre de candidats";
    case "many_comparable_offers":
      return "beaucoup d'offres comparables dont on connaît le nombre de candidats";
    case "few_comparable_offers":
      return "peu d'offres comparables dont on connaît le nombre de candidats";
    case "recent_offer":
      return days <= 1 ? "offre en ligne depuis 1 jour seulement" : `offre en ligne depuis ${days} jours seulement`;
    case "unknown_publication_date":
      return "date de mise en ligne inconnue";
    case "displayed_salary":
      return "salaire affiché dans l'offre";
    case "no_displayed_salary":
      return "sans salaire affiché";
    case "partial_sources":
      return "certaines sources n'ont pas pu être lues aujourd'hui";
  }
}

/** Reasons that lower the reliability (the screen lists those first). */
export const LOWERING_REASONS: readonly ReliabilityReason[] = [
  "recent_offer",
  "no_displayed_salary",
  "few_comparable_offers",
  "unknown_publication_date",
  "partial_sources",
];
