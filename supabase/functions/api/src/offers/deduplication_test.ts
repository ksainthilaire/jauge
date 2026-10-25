import { assertEquals } from "@std/assert";
import type { MarketOffer } from "../scoring/estimation.ts";
import { companyKey, duplicateKey, mergeDuplicateOffers } from "./deduplication.ts";

function offer(values: Partial<MarketOffer> & { id: string }): MarketOffer {
  return {
    jobTitle: "Chargé de marketing digital",
    company: "Atelier Sorel",
    city: "Lyon",
    departmentCode: "69",
    latitude: 45.76,
    longitude: 4.84,
    publishedOn: "2026-10-21",
    salary: null,
    applicantCount: null,
    applicantCountObservedAt: null,
    contractType: null,
    workMode: null,
    skills: [],
    url: null,
    ...values,
  };
}

Deno.test("the same offer on LinkedIn, Indeed and HelloWork counts once, with the best of each source", () => {
  const merged = mergeDuplicateOffers([
    offer({
      id: "indeed-1",
      jobTitle: "Chargé de marketing digital H/F",
      company: "ATELIER SOREL SAS",
      city: "Lyon 3e",
      publishedOn: "2026-10-19",
      salary: { minimum: 36, maximum: 40 },
      contractType: "permanent",
      skills: ["SEO"],
    }),
    offer({
      id: "linkedin-1",
      jobTitle: "Chargée de marketing digital (H/F)",
      applicantCount: 87,
      applicantCountObservedAt: "2026-10-25T09:00:00Z",
      skills: ["Google Ads", "SEO"],
    }),
    offer({ id: "hellowork-1", company: "Atelier Sorel", city: "69003 Lyon", workMode: "Hybride" }),
    offer({ id: "other-company", company: "Lumio" }),
  ]);
  assertEquals(merged.length, 2);
  assertEquals(merged[0], {
    ...offer({ id: "linkedin-1", jobTitle: "Chargée de marketing digital (H/F)" }),
    applicantCount: 87,
    applicantCountObservedAt: "2026-10-25T09:00:00Z",
    publishedOn: "2026-10-19",
    salary: { minimum: 36, maximum: 40 },
    contractType: "permanent",
    workMode: "Hybride",
    skills: ["SEO", "Google Ads"],
  });
  assertEquals(merged[1].id, "other-company");
});

Deno.test("offers without company, or published months apart, are never merged", () => {
  const anonymous = mergeDuplicateOffers([offer({ id: "a", company: null }), offer({ id: "b", company: null })]);
  assertEquals(anonymous.map((entry) => entry.id), ["a", "b"]);
  const reposted = mergeDuplicateOffers([
    offer({ id: "march", publishedOn: "2026-03-02" }),
    offer({ id: "october", publishedOn: "2026-10-20" }),
    offer({ id: "october-again", publishedOn: "2026-10-22" }),
  ]);
  assertEquals(reposted.map((entry) => entry.id), ["march", "october"]);
  assertEquals(companyKey("Atelier Sorel SAS"), companyKey("ATELIER SOREL"));
  assertEquals(duplicateKey({ jobTitle: "Chef de projet", company: "SAS", city: "Lyon" }), null);
});
