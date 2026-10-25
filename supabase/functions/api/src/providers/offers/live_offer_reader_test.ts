import { assert, assertEquals, assertRejects } from "@std/assert";
import { FakeApify, readFixture, readJsonFixture } from "../../testing/fake_apify.ts";
import { INDEED_OFFER_ACTOR } from "../apify/actor_catalog.ts";
import { ApifyClient } from "../apify/apify_client.ts";
import { ApifyOfferActorReader } from "../apify/apify_offer_source.ts";
import type { AiProvider, ExtractionOutcome } from "../provider_types.ts";
import { ProviderError } from "../provider_types.ts";
import { SimulatedAiProvider } from "../simulated_providers.ts";
import { enrichFromText, LiveOfferReader } from "./live_offer_reader.ts";

const TODAY = "2026-10-25";
const LINKEDIN = "https://www.linkedin.com/jobs/view/4012345678";
const HELLOWORK = "https://www.hellowork.com/fr-fr/emplois/62810499.html";
const CAREER = "https://carrieres.studio-pivot.fr/offres/chef-de-projet-digital";
const INDEED = "https://fr.indeed.com/viewjob?jk=a1b2c3d4e5f60718";
const noWait = () => Promise.resolve();

/** An AI that records the texts it reads and answers a fixed offer. */
class RecordingAi extends SimulatedAiProvider {
  readonly texts: string[] = [];
  override extractOfferFromText(text: string, _today: string): Promise<ExtractionOutcome> {
    this.texts.push(text);
    return Promise.resolve({
      kind: "extracted",
      costMicroUsd: 300,
      offer: {
        jobTitle: "Chef de projet digital",
        company: "Studio Pivot",
        city: "Lyon",
        departmentCode: "69",
        contractType: null,
        workMode: null,
        publishedOn: null,
        displayedSalary: null,
        skills: ["Figma"],
        displayedApplicantCount: null,
      },
    });
  }
}

/** An AI that is not connected (live mode before the AI phase). */
const notConnectedAi = {
  name: "ai",
  estimateCostMicroUsd: () => 0,
  extractOfferFromText: () => Promise.reject(new ProviderError("ai", "not_configured", false)),
  extractOfferFromImage: () => Promise.reject(new ProviderError("ai", "not_configured", false)),
  summarizeReputation: () => Promise.reject(new ProviderError("ai", "not_configured", false)),
  writeAdvice: () => Promise.reject(new ProviderError("ai", "not_configured", false)),
} satisfies AiProvider;

async function setup(ai: AiProvider = new RecordingAi()) {
  const items = await readJsonFixture<unknown[]>("apify/fixtures/indeed_items.json");
  const apify = new FakeApify({ "misceres~indeed-scraper": { items, runningPolls: 0, resultUsd: 0.005 } });
  apify.pages.set(LINKEDIN, await readFixture("offers/fixtures/linkedin_job.html"));
  apify.pages.set(HELLOWORK, await readFixture("offers/fixtures/hellowork_job.html"));
  apify.pages.set(CAREER, await readFixture("offers/fixtures/career_page.html"));
  apify.pages.set(INDEED, 403);
  const client = new ApifyClient("token", apify.fetcher, {}, noWait);
  const reader = new LiveOfferReader(
    ai,
    { indeed: new ApifyOfferActorReader(INDEED_OFFER_ACTOR, client, "BRONZE") },
    apify.fetcher,
  );
  return { apify, reader };
}

Deno.test("LinkedIn page: JSON-LD JobPosting and the applicant count of the page, for free", async () => {
  const { apify, reader } = await setup();
  const outcome = await reader.readLink(LINKEDIN, "linkedin", TODAY);
  assertEquals(outcome, {
    kind: "extracted",
    costMicroUsd: 0,
    offer: {
      jobTitle: "Chargé de marketing digital (H/F)",
      company: "Atelier Sorel",
      city: "Lyon",
      departmentCode: "69",
      contractType: "permanent",
      workMode: "Hybride",
      publishedOn: "2026-10-21",
      displayedSalary: null,
      skills: ["SEO", "Google Ads", "HubSpot", "Emailing"],
      displayedApplicantCount: 87,
    },
  });
  assertEquals(apify.apifyCalls.length, 0, "no Apify run when the page is readable");
  assertEquals(apify.calls[0].headers.get("user-agent")?.includes("JaugeLinkPreview"), true);
});

Deno.test("HelloWork page: JobPosting inside @graph, monthly salary made yearly, listed skills", async () => {
  const { reader } = await setup();
  const outcome = await reader.readLink(HELLOWORK, "hellowork", TODAY);
  if (outcome.kind !== "extracted") throw new Error(outcome.kind);
  assertEquals(outcome.offer.displayedSalary, { minimum: 35, maximum: 39 });
  assertEquals(outcome.offer.skills, ["SEO", "SEA", "CRM"]);
  assertEquals(outcome.offer.city, "Lyon 3e");
  assertEquals(outcome.offer.departmentCode, "69");
  assertEquals(outcome.offer.contractType, "permanent");
});

Deno.test("career page without structured data: the AI reads the visible text, plain facts fill the gaps", async () => {
  const ai = new RecordingAi();
  const { reader } = await setup(ai);
  const outcome = await reader.readLink(CAREER, null, TODAY);
  if (outcome.kind !== "extracted") throw new Error(outcome.kind);
  assertEquals(outcome.costMicroUsd, 300);
  assertEquals(outcome.offer.contractType, "permanent");
  assertEquals(outcome.offer.displayedSalary, { minimum: 40, maximum: 45 });
  assertEquals(outcome.offer.publishedOn, "2026-10-19");
  assertEquals(outcome.offer.workMode, "Hybride");
  assert(!ai.texts[0].includes("Mentions légales"), "header, menu and footer are not sent to the AI");
  assert(ai.texts[0].includes("Chef de projet digital"));
});

Deno.test("Indeed refuses direct reading: its Apify actor reads the offer when the daily limit allows it", async () => {
  const { apify, reader } = await setup();
  const outcome = await reader.readLink(INDEED, "indeed", TODAY);
  if (outcome.kind !== "extracted") throw new Error(outcome.kind);
  assertEquals(outcome.offer.company, "ATELIER SOREL SAS");
  assertEquals(outcome.costMicroUsd, 5_050);
  assertEquals(apify.startsOf("misceres~indeed-scraper").length, 1);

  const asked: number[] = [];
  const refused = await reader.readLink(INDEED, "indeed", TODAY, (estimate) => {
    asked.push(estimate);
    return Promise.resolve(false);
  });
  assertEquals(refused, { kind: "unsupported", costMicroUsd: 0 }, "over the limit: the user is offered a screenshot");
  assertEquals(asked, [5_000]);
  assertEquals(apify.startsOf("misceres~indeed-scraper").length, 1);
});

Deno.test("removed offers, blocked sites, challenges, private redirects and down sites", async () => {
  const { apify, reader } = await setup(notConnectedAi);
  assertEquals(await reader.readLink("https://www.hellowork.com/fr-fr/emplois/1.html", "hellowork", TODAY), {
    kind: "unreadable",
    recognizedCompany: null,
    costMicroUsd: 0,
  });
  apify.pages.set("https://www.glassdoor.fr/job-listing/?jl=1009740741115", 403);
  assertEquals(
    await reader.readLink("https://www.glassdoor.fr/job-listing/?jl=1009740741115", "glassdoor", TODAY),
    { kind: "unsupported", costMicroUsd: 0 },
  );
  apify.pages.set("https://jobs.example.fr/1", await readFixture("offers/fixtures/challenge.html"));
  assertEquals(await reader.readLink("https://jobs.example.fr/1", null, TODAY), {
    kind: "unsupported",
    costMicroUsd: 0,
  });
  // Without AI, a page without structured data is unreadable (the user can type the offer or send a screenshot).
  const career = await reader.readLink(CAREER, null, TODAY);
  assertEquals(career.kind, "unreadable");

  const redirecting = new LiveOfferReader(notConnectedAi, {}, (url) =>
    Promise.resolve(
      url === "https://short.example.fr/x"
        ? new Response(null, { status: 302, headers: { location: "http://169.254.169.254/latest/meta-data" } })
        : new Response("", { status: 500 }),
    ));
  assertEquals(await redirecting.readLink("https://short.example.fr/x", null, TODAY), {
    kind: "unsupported",
    costMicroUsd: 0,
  });
  const error = await assertRejects(
    () => redirecting.readLink("https://down.example.fr/x", null, TODAY),
    ProviderError,
  );
  assertEquals([error.code, error.retryable], ["page_http_500", true]);
});

Deno.test("text read on the phone: the AI structures it, the plain facts it missed are added", async () => {
  const { reader } = await setup();
  const text = "Chef de projet digital\nStudio Pivot - Lyon\nCDI\n47 candidats\nPubliée il y a 2 jours\n" +
    "Salaire : 38 000 € - 42 000 € par an";
  const outcome = await reader.readText(text, TODAY);
  if (outcome.kind !== "extracted") throw new Error(outcome.kind);
  assertEquals(outcome.offer.displayedApplicantCount, 47);
  assertEquals(outcome.offer.publishedOn, "2026-10-23");
  assertEquals(outcome.offer.displayedSalary, { minimum: 38, maximum: 42 });
  assertEquals(outcome.offer.contractType, "permanent");
  const kept = enrichFromText({ ...outcome.offer, displayedApplicantCount: 12 }, text, TODAY);
  assertEquals(kept.displayedApplicantCount, 12, "the AI is never overruled");
});
