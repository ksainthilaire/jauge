import { assertEquals } from "@std/assert";
import {
  cityOf,
  contractTypeOf,
  departmentOf,
  htmlToText,
  parseApplicantCount,
  parsePublicationDate,
  parseSalaryText,
  skillsInText,
  workModeOf,
} from "./offer_parsing.ts";

const TODAY = "2026-10-25";

Deno.test("salaries written by the sites become a yearly range in thousands of euros", () => {
  assertEquals(parseSalaryText("35 000 - 40 000 € / an"), { minimum: 35, maximum: 40 });
  assertEquals(parseSalaryText("De 2 000 € à 2 400 € par mois"), { minimum: 24, maximum: 29 });
  assertEquals(parseSalaryText("11,91 - 12,35 € / heure"), { minimum: 19, maximum: 20 });
  assertEquals(parseSalaryText("€36,000.00/yr - €41,000.00/yr"), { minimum: 36, maximum: 41 });
  assertEquals(parseSalaryText("45k€"), { minimum: 45, maximum: 45 });
  assertEquals(parseSalaryText("450 € par jour"), { minimum: 98, maximum: 98 });
  assertEquals(parseSalaryText("35 000 € brut annuel, 13e mois"), { minimum: 35, maximum: 35 });
  assertEquals(parseSalaryText("$17.00 - $19.00"), null, "another currency is not mixed with euros");
  assertEquals(parseSalaryText("Selon profil"), null);
  assertEquals(parseSalaryText("3 € / an"), null, "an implausible salary is left empty");
});

Deno.test("publication dates: ISO, timestamps and relative ages in French and English", () => {
  assertEquals(parsePublicationDate("2026-10-19T08:00:00.000Z", TODAY), "2026-10-19");
  assertEquals(parsePublicationDate("il y a 3 jours", TODAY), "2026-10-22");
  assertEquals(parsePublicationDate("Publiée il y a 30+ jours", TODAY), "2026-09-25");
  assertEquals(parsePublicationDate("il y a 2 semaines", TODAY), "2026-10-11");
  assertEquals(parsePublicationDate("il y a 1 heure", TODAY), TODAY);
  assertEquals(parsePublicationDate("Aujourd'hui", TODAY), TODAY);
  assertEquals(parsePublicationDate("hier", TODAY), "2026-10-24");
  assertEquals(parsePublicationDate("3 days ago", TODAY), "2026-10-22");
  assertEquals(parsePublicationDate(1792396800, TODAY), "2026-10-19");
  assertEquals(parsePublicationDate("2027-01-01", TODAY), TODAY, "a date in the future is today at most");
  assertEquals(parsePublicationDate("bientôt", TODAY), null);
});

Deno.test("applicant counts: numbers and captions, 'among the first 25' stays unknown", () => {
  assertEquals(parseApplicantCount("87"), 87);
  assertEquals(parseApplicantCount(87), 87);
  assertEquals(parseApplicantCount("Over 200 applicants"), 200);
  assertEquals(parseApplicantCount("Plus de 200 candidats"), 200);
  assertEquals(parseApplicantCount("1 204 candidatures"), 1204);
  assertEquals(parseApplicantCount("Be among the first 25 applicants"), null);
  assertEquals(parseApplicantCount("Soyez parmi les 25 premiers candidats"), null);
  assertEquals(parseApplicantCount(null), null);
});

Deno.test("contracts, work modes, places and skills", () => {
  assertEquals(contractTypeOf("CDI"), "permanent");
  assertEquals(contractTypeOf("Contrat à durée indéterminée"), "permanent");
  assertEquals(contractTypeOf("CDD"), "fixed_term");
  assertEquals(contractTypeOf("Alternance"), "apprenticeship");
  assertEquals(contractTypeOf("INTERN"), "internship");
  assertEquals(contractTypeOf("Mission d'intérim"), "temporary");
  assertEquals(contractTypeOf("CONTRACTOR"), "freelance");
  assertEquals(contractTypeOf("Full-time"), null, "full time says nothing about the contract");
  assertEquals(workModeOf("Télétravail partiel"), "Hybride");
  assertEquals(workModeOf("Pas de télétravail"), "Sur site");
  assertEquals(workModeOf("Remote"), "Télétravail complet");
  assertEquals(workModeOf("fulltime"), "Télétravail complet");
  assertEquals(workModeOf("no"), "Sur site");
  assertEquals(departmentOf("69003 Lyon"), "69");
  assertEquals(departmentOf("Vénissieux (69)"), "69");
  assertEquals(departmentOf("Lyon - 69"), "69");
  assertEquals(departmentOf("Ajaccio 20000"), "2A");
  assertEquals(departmentOf("Rhône"), "69");
  assertEquals(departmentOf("Lyon, Auvergne-Rhône-Alpes, France"), "69");
  assertEquals(cityOf("Lyon, Auvergne-Rhône-Alpes, France"), "Lyon");
  assertEquals(cityOf("69003 Lyon"), "Lyon");
  assertEquals(cityOf("Télétravail à Lyon (69)"), "Lyon");
  assertEquals(cityOf("Lyon - 69"), "Lyon");
  assertEquals(
    skillsInText("SEO, campagnes Google Ads, emailing avec HubSpot. Anglais courant."),
    ["SEO", "Google Ads", "HubSpot", "Emailing", "Anglais courant"],
  );
  assertEquals(skillsInText("<p>Maîtrise de <b>Canva</b></p>"), ["Canva"]);
  assertEquals(htmlToText("<p>Un&nbsp;&amp;&#233;l&eacute;ment</p><p>suite</p>"), "Un &élément\nsuite");
});
