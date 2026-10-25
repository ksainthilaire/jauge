import { assert, assertEquals, assertNotEquals } from "@std/assert";
import { canonicalizeLink, jobFamily, linkOfferKey, manualOfferKey } from "./offer_key.ts";
import { cityKey, findCity } from "../geography/cities.ts";

Deno.test("links of the same offer give the same key", async () => {
  const first = canonicalizeLink(
    "https://www.linkedin.com/jobs/view/chargee-de-marketing-at-sorel-3912345678/?trk=abc&refId=x",
  );
  const second = canonicalizeLink("http://LinkedIn.com/jobs/view/3912345678#top");
  assertEquals(first?.url, "https://www.linkedin.com/jobs/view/3912345678");
  assertEquals(first?.site, "linkedin");
  assertEquals((await linkOfferKey(first!)).value, (await linkOfferKey(second!)).value);
  assertEquals(
    canonicalizeLink("https://fr.indeed.com/viewjob?jk=ABCDEF0123456789&from=serp&vjs=3")?.url,
    "https://fr.indeed.com/viewjob?jk=abcdef0123456789",
  );
  assertEquals(
    canonicalizeLink("https://example.com/careers/42/?utm_source=x&b=2&a=1")?.url,
    "https://example.com/careers/42?a=1&b=2",
  );
});

Deno.test("things that are not public links are refused", () => {
  for (
    const text of [
      "bonjour",
      "ftp://example.com/x",
      "https://localhost/x",
      "http://192.168.1.2/job",
      "https://user:pw@example.com",
    ]
  ) {
    assertEquals(canonicalizeLink(text), null, text);
  }
});

Deno.test("job family ignores gender marks, contracts and inclusive writing", async () => {
  assertEquals(jobFamily("Chargé·e de marketing digital (H/F) - CDI"), "charge marketing digital");
  assertEquals(jobFamily("Chargée de Marketing Digital"), "charge marketing digital");
  assertEquals(jobFamily("Vendeuse confirmée"), "vendeur");
  assertEquals(
    (await manualOfferKey("Chargé de marketing digital", "Lyon 3e")).value,
    (await manualOfferKey("Chargée de marketing digital H/F", "lyon")).value,
  );
  assertNotEquals(
    (await manualOfferKey("Chargé de marketing digital", "Lyon")).value,
    (await manualOfferKey("Chargé de marketing digital", "Paris")).value,
  );
});

Deno.test("cities are found with districts, postcodes and accents", () => {
  assertEquals(cityKey("69003 Lyon 3e"), "lyon");
  assertEquals(findCity("Lyon 3e")?.departmentCode, "69");
  assertEquals(findCity("saint-etienne")?.departmentCode, "42");
  assert(findCity("Atlantis") === null);
});
