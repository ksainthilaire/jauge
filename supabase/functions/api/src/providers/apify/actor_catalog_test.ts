import { assert, assertEquals } from "@std/assert";
import { readJsonFixture } from "../../testing/fake_apify.ts";
import type { CollectionRequest } from "../provider_types.ts";
import {
  GLASSDOOR_ACTOR,
  HELLOWORK_ACTOR,
  INDEED_ACTOR,
  INDEED_OFFER_ACTOR,
  LINKEDIN_ACTOR,
  searchWords,
  WELCOME_TO_THE_JUNGLE_ACTOR,
} from "./actor_catalog.ts";
import { mapItems } from "./apify_offer_source.ts";

const TODAY = "2026-10-25";
const REQUEST: CollectionRequest = {
  jobTitle: "Chargé·e de marketing digital (H/F) - CDI",
  jobFamily: "charge marketing digital",
  city: "Lyon",
  departmentCode: "69",
  radiusKilometers: 30,
  maximumOffers: 60,
  today: TODAY,
};

Deno.test("inputs of the actors follow their documentation and limit the results", () => {
  assertEquals(searchWords(REQUEST.jobTitle), "Chargé·e de marketing digital");
  assertEquals(LINKEDIN_ACTOR.buildInput(REQUEST, 60), {
    keywords: "Chargé·e de marketing digital",
    location: "Lyon, France",
    distance: 25,
    datePosted: "pastMonth",
    scrapeCompany: false,
    splitByLocation: false,
    autoConvertToAiSearch: true,
    limitPerSource: 60,
  });
  assertEquals(INDEED_ACTOR.buildInput(REQUEST, 40), {
    position: "Chargé·e de marketing digital",
    country: "FR",
    location: "Lyon",
    maxItemsPerSearch: 40,
    parseCompanyDetails: false,
    saveOnlyUniqueItems: true,
  });
  assertEquals(HELLOWORK_ACTOR.buildInput(REQUEST, 60), {
    keywords: "Chargé·e de marketing digital",
    location: "Lyon",
    distance: "20",
    period: "30d",
    sort_by: "date",
    limit: 60,
  });
  assertEquals(GLASSDOOR_ACTOR.buildInput(REQUEST, 20).job_name, "Chargé·e de marketing digital");
  const jungle = WELCOME_TO_THE_JUNGLE_ACTOR.buildInput(REQUEST, 30) as { search_job: string[]; max_page: number };
  assertEquals(jungle.max_page, 1);
  const searchUrl = new URL(jungle.search_job[0]);
  assertEquals(searchUrl.searchParams.get("query"), "Chargé·e de marketing digital");
  assertEquals(searchUrl.searchParams.get("refinementList[offices.country_code][]"), "FR");
  assertEquals(INDEED_OFFER_ACTOR.buildInput("https://fr.indeed.com/viewjob?jk=a1b2c3d4e5f60718"), {
    startUrls: [{ url: "https://fr.indeed.com/viewjob?jk=a1b2c3d4e5f60718" }],
    country: "FR",
    maxItemsPerSearch: 1,
    parseCompanyDetails: false,
  });
});

Deno.test("LinkedIn: applicant counts, salaries in euros only, offers abroad and repeats left out", async () => {
  const offers = mapItems(LINKEDIN_ACTOR, await readJsonFixture("apify/fixtures/linkedin_items.json"), TODAY);
  assertEquals(offers.map((offer) => offer.externalId), ["4012345678", "4012345679", "4012345680"]);
  assertEquals(offers[0], {
    source: "linkedin",
    externalId: "4012345678",
    jobTitle: "Chargé de marketing digital (H/F)",
    company: "Atelier Sorel",
    city: "Lyon",
    departmentCode: "69",
    publishedOn: "2026-10-21",
    salary: { minimum: 36, maximum: 41 },
    applicantCount: 87,
    contractType: null,
    workMode: "Hybride",
    skills: ["SEO", "Google Ads", "Google Analytics", "HubSpot", "Emailing", "Anglais courant"],
    url: "https://www.linkedin.com/jobs/view/4012345678",
  });
  assertEquals(offers[1].applicantCount, 200);
  assertEquals(offers[1].city, "Villeurbanne");
  assertEquals(offers[1].departmentCode, null, "a city outside the reference list: the searched department is used");
  assertEquals(offers[2].salary, null, "dollars are not euros");
  assertEquals(offers[2].applicantCount, null, "'among the first 25' is not a count");
  assertEquals(offers[2].contractType, "internship");
  assertEquals(offers[2].workMode, "Télétravail complet");
});

Deno.test("Indeed: French salaries and dates, the 'no result' item is ignored", async () => {
  const offers = mapItems(INDEED_ACTOR, await readJsonFixture("apify/fixtures/indeed_items.json"), TODAY);
  assertEquals(offers.length, 3);
  assertEquals(offers[0].salary, { minimum: 36, maximum: 40 });
  assertEquals(offers[0].contractType, "permanent");
  assertEquals(offers[0].workMode, "Hybride");
  assertEquals(offers[0].publishedOn, "2026-10-19");
  assertEquals(offers[0].url, "https://fr.indeed.com/viewjob?jk=a1b2c3d4e5f60718");
  assertEquals(offers[0].skills, ["SEO", "Google Ads", "WordPress"]);
  assertEquals(offers[1].salary, { minimum: 13, maximum: 17 });
  assertEquals(offers[1].contractType, "apprenticeship");
  assertEquals(offers[1].departmentCode, "69");
  assertEquals(offers[1].publishedOn, "2026-09-25");
  assertEquals(offers[2].city, "Lyon");
  assertEquals(offers[2].publishedOn, TODAY);
  assertEquals(offers[2].salary, { minimum: 24, maximum: 24 });
  assertEquals(mapItems(INDEED_ACTOR, await readJsonFixture("apify/fixtures/indeed_no_results.json"), TODAY), []);
});

Deno.test("HelloWork: department given by the site, offers abroad left out", async () => {
  const offers = mapItems(HELLOWORK_ACTOR, await readJsonFixture("apify/fixtures/hellowork_items.json"), TODAY);
  assertEquals(offers.map((offer) => offer.externalId), ["62810499", "62810500"]);
  assertEquals(offers[0].city, "Lyon");
  assertEquals(offers[0].departmentCode, "69");
  assertEquals(offers[0].salary, { minimum: 35, maximum: 39 });
  assertEquals(offers[0].publishedOn, "2026-10-22");
  assertEquals(offers[0].workMode, "Hybride");
  assertEquals(offers[0].skills, ["SEO", "SEA", "CRM", "Emailing", "Anglais courant"]);
  assertEquals(offers[1].contractType, "fixed_term");
  assertEquals(offers[1].workMode, "Sur site");
  assertEquals(offers[1].publishedOn, "2026-10-25");
});

Deno.test("Glassdoor: percentile salaries in euros, other currencies and remote offers left out", async () => {
  const offers = mapItems(GLASSDOOR_ACTOR, await readJsonFixture("apify/fixtures/glassdoor_items.json"), TODAY);
  assertEquals(offers.map((offer) => offer.externalId), ["1009740741115", "1009740487949"]);
  assertEquals(offers[0].salary, { minimum: 34, maximum: 43 });
  assertEquals(offers[0].company, "Atelier Sorel");
  assertEquals(offers[1].salary, { minimum: 34, maximum: 41 }, "a monthly range becomes yearly");
});

Deno.test("Welcome to the Jungle: French office, yearly salary, listed skills, offers abroad left out", async () => {
  const offers = mapItems(
    WELCOME_TO_THE_JUNGLE_ACTOR,
    await readJsonFixture("apify/fixtures/welcome_to_the_jungle_items.json"),
    TODAY,
  );
  assertEquals(offers.length, 2);
  assertEquals(offers[0].city, "Lyon");
  assertEquals(offers[0].departmentCode, "69");
  assertEquals(offers[0].salary, { minimum: 42, maximum: 48 });
  assertEquals(offers[0].contractType, "permanent");
  assertEquals(offers[0].workMode, "Hybride");
  assertEquals(offers[0].url, "https://www.welcometothejungle.com/fr/companies/lumio/jobs/growth-marketer_lyon");
  assert(offers[0].skills.includes("Google Ads"));
  assertEquals(offers[1].departmentCode, "75");
  assertEquals(offers[1].skills, ["Prospection", "Négociation", "HubSpot"]);
  assertEquals(offers[1].workMode, "Sur site");
  assertEquals(offers[1].publishedOn, "2026-10-25");
});
