import { assert, assertEquals, assertExists } from "@std/assert";
import { createTestApplication } from "./testing/test_application.ts";

const LINK = "https://www.linkedin.com/jobs/view/3912345678/?trk=public";
const AREA = { city: "Lyon", departmentCode: "69", radiusKilometers: 30 };

Deno.test("a link is analyzed end to end and every insight answers in the shape of the contract", async () => {
  const api = await createTestApplication();
  try {
    const user = await api.test.createUser({ email: "alice@example.org", provider: "apple" });
    const created = await api.request("POST", "/v1/analyses", {
      userId: user,
      body: { type: "link", url: LINK, area: AREA },
    });
    assertEquals(created.status, 202);
    assertEquals(created.body.status, "processing");
    assertEquals((created.body.steps as unknown[]).length, 4);
    const id = String(created.body.id);

    await api.runWorker();
    const analysis = await api.request("GET", `/v1/analyses/${id}`, { userId: user });
    assertEquals(analysis.status, 200);
    assertEquals(analysis.body.status, "completed", JSON.stringify(analysis.body));
    assertEquals(analysis.body.progressPercent, 100);
    const result = analysis.body.result as Record<string, unknown>;
    const score = Number(result.score);
    assert(score >= 0 && score <= 100);
    assertEquals(result.regionName, "Rhône");
    assert(["high", "medium", "low"].includes(String(result.reliability)));
    const applicants = result.applicants as { estimate: number; low: number; high: number };
    assert(applicants.low <= applicants.estimate && applicants.estimate <= applicants.high);
    assertEquals(result.twinRadiusKilometers, 30);
    assertEquals(result.internal, undefined, "internal fields never leave the server");
    const offer = analysis.body.offer as Record<string, unknown>;
    assertEquals(offer.city, "Lyon 3e");
    assertEquals(offer.sourceUrl, "https://www.linkedin.com/jobs/view/3912345678");
    for (const step of analysis.body.steps as { status: string }[]) assertEquals(step.status, "done");

    const salary = await api.request("GET", `/v1/analyses/${id}/salary?city=Lyon&department=69`, { userId: user });
    assertEquals(salary.status, 200, JSON.stringify(salary.body));
    assert(Number(salary.body.scaleMinimum) < Number(salary.body.scaleMaximum));
    assertExists(salary.body.middleHalf);
    const skills = await api.request("GET", `/v1/analyses/${id}/skills`, { userId: user });
    assert((skills.body.skills as unknown[]).length > 0);
    const trend = await api.request("GET", `/v1/analyses/${id}/trend`, { userId: user });
    for (const point of trend.body.points as { month: string }[]) assert(/^\d{4}-\d{2}$/.test(point.month));
    const twins = await api.request(
      "GET",
      `/v1/analyses/${id}/twins?sort=best_paid&city=Lyon&department=69&radius=50`,
      { userId: user },
    );
    assertEquals(twins.status, 200);
    assertEquals(twins.body.radiusKilometers, 50);
    for (const twin of twins.body.items as { score: number; distanceKilometers: number }[]) {
      assert(twin.score < score);
      assert(twin.distanceKilometers <= 50);
    }
    const methodology = await api.request("GET", `/v1/methodology?analysisId=${id}`, {
      userId: user,
      headers: { "Accept-Language": "en-US,en;q=0.9" },
    });
    assertEquals(methodology.status, 200);
    assertEquals(methodology.body.regionName, "Rhône");
    assert(Array.isArray(methodology.body.reliabilityReasons));

    const list = await api.request("GET", "/v1/analyses?limit=10", { userId: user });
    assertEquals((list.body.items as { id: string }[]).map((item) => item.id), [id]);
    assertEquals(list.body.nextCursor, null);
    const found = await api.request("GET", `/v1/analyses?query=${encodeURIComponent("chargee lyon")}`, {
      userId: user,
    });
    const missing = await api.request("GET", "/v1/analyses?query=boulanger", { userId: user });
    assertEquals((missing.body.items as unknown[]).length, 0);
    assert((found.body.items as unknown[]).length <= 1);

    const subscription = await api.request("GET", "/v1/subscription", { userId: user });
    assertEquals(subscription.body, { status: "free", trialEndsOn: null, renewsOn: null, freeAnalysesLeftThisWeek: 0 });
    const account = await api.request("GET", "/v1/account", { userId: user });
    assertEquals(account.body.signInMethod, "apple");
    assertEquals(account.body.analysisCount, 1);
  } finally {
    await api.close();
  }
});

Deno.test("the free analysis of the week is used once, and given back when the offer cannot be read", async () => {
  const api = await createTestApplication();
  try {
    const user = await api.test.createUser();
    const unreadable = await api.request("POST", "/v1/analyses", {
      userId: user,
      body: { type: "link", url: "https://example.com/unreadable-offer" },
    });
    assertEquals(unreadable.status, 202);
    await api.runWorker();
    const failed = await api.request("GET", `/v1/analyses/${unreadable.body.id}`, { userId: user });
    assertEquals(failed.body.status, "failed");
    assertEquals(failed.body.failure, { code: "unreadable_offer", recognizedCompany: "Atelier Sorel" });
    assertEquals((await api.request("GET", "/v1/subscription", { userId: user })).body.freeAnalysesLeftThisWeek, 1);

    const unsupported = await api.request("POST", "/v1/analyses", {
      userId: user,
      body: { type: "link", url: "https://example.com/unsupported" },
    });
    await api.runWorker();
    assertEquals((await api.request("GET", `/v1/analyses/${unsupported.body.id}`, { userId: user })).body.failure, {
      code: "unsupported_link",
      recognizedCompany: null,
    });

    const first = await api.request("POST", "/v1/analyses", {
      userId: user,
      body: { type: "manual", jobTitle: "Comptable", city: "Lyon" },
    });
    assertEquals(first.status, 202);
    const second = await api.request("POST", "/v1/analyses", {
      userId: user,
      body: { type: "manual", jobTitle: "Infirmier", city: "Lyon" },
    });
    assertEquals(second.status, 402);
    assertEquals(second.body, { error: { code: "quota_reached", message: "Weekly free analysis already used" } });

    await api.makePlus(user);
    const plus = await api.request("POST", "/v1/analyses", {
      userId: user,
      body: { type: "manual", jobTitle: "Infirmier", city: "Lyon" },
    });
    assertEquals(plus.status, 202);
  } finally {
    await api.close();
  }
});

Deno.test("the 7 day cache and the market slices avoid paying twice for the same offer or market", async () => {
  const api = await createTestApplication();
  try {
    const alice = await api.test.createUser();
    const bob = await api.test.createUser();
    const collections = async () => (await api.test.database.query(`select * from public.source_collections`)).length;

    await api.request("POST", "/v1/analyses", { userId: alice, body: { type: "link", url: LINK } });
    await api.runWorker();
    const afterFirst = await collections();
    assert(afterFirst > 0);

    const cached = await api.request("POST", "/v1/analyses", {
      userId: bob,
      body: { type: "link", url: `${LINK}&utm_source=x` },
    });
    await api.runWorker();
    const cachedAnalysis = await api.request("GET", `/v1/analyses/${cached.body.id}`, { userId: bob });
    assertEquals(cachedAnalysis.body.status, "completed");
    assertEquals(await collections(), afterFirst, "a cached offer is not collected again");
    const fromCache = await api.test.database.query<{ from_cache: boolean }>(
      `select from_cache from public.analyses where id = $1::uuid`,
      [cached.body.id],
    );
    assertEquals(fromCache[0].from_cache, true);

    // Expired cache: the offer is computed again, but the market slice (7 days) is reused.
    await api.test.database.query(`update public.offer_snapshots set expires_at = now() - interval '1 minute'`);
    const carol = await api.test.createUser();
    const again = await api.request("POST", "/v1/analyses", {
      userId: carol,
      body: { type: "link", url: "https://www.linkedin.com/jobs/view/3912345678" },
    });
    await api.runWorker();
    const recomputed = await api.request("GET", `/v1/analyses/${again.body.id}`, { userId: carol });
    assertEquals(recomputed.body.status, "completed");
    assertEquals(await collections(), afterFirst, "the market slice of the same job and department is reused");
    assertEquals(
      (await api.test.database.query(`select * from public.offer_snapshots where status = 'ready'`)).length,
      2,
    );
  } finally {
    await api.close();
  }
});

Deno.test("idempotency keys and double taps give back the same analysis without using the quota twice", async () => {
  const api = await createTestApplication();
  try {
    const user = await api.test.createUser();
    const headers = { "Idempotency-Key": "c1b2a3d4-key" };
    const first = await api.request("POST", "/v1/analyses", {
      userId: user,
      body: { type: "link", url: LINK },
      headers,
    });
    const repeated = await api.request("POST", "/v1/analyses", {
      userId: user,
      body: { type: "link", url: LINK },
      headers,
    });
    const doubleTap = await api.request("POST", "/v1/analyses", {
      userId: user,
      body: { type: "link", url: `${LINK}&refId=9` },
    });
    assertEquals(repeated.body.id, first.body.id);
    assertEquals(doubleTap.body.id, first.body.id);
    assertEquals((await api.test.database.query(`select * from public.jobs where kind = 'analyze_offer'`)).length, 1);
  } finally {
    await api.close();
  }
});

Deno.test("text read on the phone and images are analyzed, and never kept", async () => {
  const api = await createTestApplication();
  try {
    const user = await api.test.createUser();
    await api.makePlus(user);
    const text =
      "Chargé de communication\nChez Maison Brunet, Lyon\nCDI, 38k€ - 42k€, publiée il y a 2 jours.\nSEO, Canva, Emailing";
    const fromText = await api.request("POST", "/v1/analyses", {
      userId: user,
      body: { type: "text", text, origin: "screenshot", area: AREA },
    });
    assertEquals(fromText.status, 202);
    const short = await api.request("POST", "/v1/analyses", {
      userId: user,
      body: { type: "text", text: "trop court" },
    });
    assertEquals(short.status, 422);
    assertEquals((short.body.error as { code: string }).code, "unreadable_offer");

    const jpeg = new Uint8Array(4096);
    jpeg.set([0xff, 0xd8, 0xff, 0xe0]);
    const form = new FormData();
    form.append("image", new File([jpeg], "capture.jpg", { type: "image/jpeg" }));
    form.append("origin", "screenshot");
    form.append("areaCity", "Lyon");
    form.append("areaDepartment", "69");
    form.append("areaRadius", "30");
    const fromImage = await api.request("POST", "/v1/analyses", { userId: user, rawBody: form });
    assertEquals(fromImage.status, 202, JSON.stringify(fromImage.body));
    assertEquals((await api.test.database.query(`select * from public.analysis_inputs`)).length, 2);

    await api.runWorker();
    assertEquals(
      (await api.test.database.query(`select * from public.analysis_inputs`)).length,
      0,
      "inputs are deleted once read",
    );
    const textAnalysis = await api.request("GET", `/v1/analyses/${fromText.body.id}`, { userId: user });
    assertEquals(textAnalysis.body.status, "completed", JSON.stringify(textAnalysis.body));
    assertEquals((textAnalysis.body.offer as { displayedSalary: unknown }).displayedSalary, {
      minimum: 38,
      maximum: 42,
    });
    assertEquals(textAnalysis.body.publishedDaysAgo, 2);
    assertEquals(
      (await api.request("GET", `/v1/analyses/${fromImage.body.id}`, { userId: user })).body.status,
      "completed",
    );

    const notAnImage = new FormData();
    notAnImage.append("image", new File([new TextEncoder().encode("hello")], "x.jpg", { type: "image/jpeg" }));
    notAnImage.append("origin", "photo");
    const refused = await api.request("POST", "/v1/analyses", { userId: user, rawBody: notAnImage });
    assertEquals((refused.body.error as { code: string }).code, "unreadable_offer");

    const huge = new FormData();
    huge.append("image", new File([new Uint8Array(10 * 1024 * 1024 + 10)], "big.jpg", { type: "image/jpeg" }));
    huge.append("origin", "photo");
    const tooLarge = await api.request("POST", "/v1/analyses", { userId: user, rawBody: huge });
    assertEquals(tooLarge.status, 413);
    assertEquals((tooLarge.body.error as { code: string }).code, "image_too_large");
  } finally {
    await api.close();
  }
});
