import { assert, assertEquals, assertNotEquals } from "@std/assert";
import {
  createTestApplication,
  TEST_REVENUECAT_AUTHORIZATION,
  TEST_WEBHOOK_SECRET,
  type TestApplication,
} from "./testing/test_application.ts";

const LINK = "https://www.linkedin.com/jobs/view/3912345678";

async function completedAnalysis(api: TestApplication, userId: string, url = LINK): Promise<string> {
  const created = await api.request("POST", "/v1/analyses", {
    userId,
    body: { type: "link", url, area: { city: "Lyon", departmentCode: "69", radiusKilometers: 30 } },
  });
  await api.runWorker();
  return String(created.body.id);
}

function errorCode(body: Record<string, unknown>): string {
  return (body.error as { code: string }).code;
}

Deno.test("requests are validated strictly and answer the typed errors of the contract", async () => {
  const api = await createTestApplication();
  try {
    const user = await api.test.createUser();
    const noToken = await api.request("GET", "/v1/analyses");
    assertEquals([noToken.status, errorCode(noToken.body)], [401, "unauthorized"]);
    const badToken = await api.request("GET", "/v1/analyses", { headers: { Authorization: "Bearer nope" } });
    assertEquals(badToken.status, 401);
    const notALink = await api.request("POST", "/v1/analyses", {
      userId: user,
      body: { type: "link", url: "bonjour" },
    });
    assertEquals([notALink.status, errorCode(notALink.body)], [422, "invalid_link"]);
    const unknownField = await api.request("POST", "/v1/analyses", {
      userId: user,
      body: { type: "manual", jobTitle: "Comptable", city: "Lyon", admin: true },
    });
    assertEquals([unknownField.status, errorCode(unknownField.body)], [422, "validation_failed"]);
    const badArea = await api.request("POST", "/v1/analyses", {
      userId: user,
      body: { type: "manual", jobTitle: "Comptable", city: "Lyon", area: { city: "Lyon", radiusKilometers: 9000 } },
    });
    assertEquals(badArea.status, 422);
    const notJson = await api.request("POST", "/v1/analyses", {
      userId: user,
      rawBody: "{",
      headers: { "Content-Type": "application/json" },
    });
    assertEquals([notJson.status, errorCode(notJson.body)], [400, "validation_failed"]);
    assertEquals((await api.request("GET", "/v1/analyses?limit=0", { userId: user })).status, 400);
    assertEquals((await api.request("GET", "/v1/analyses?cursor=garbage", { userId: user })).status, 400);
    assertEquals((await api.request("GET", `/v1/analyses?query=${"a".repeat(101)}`, { userId: user })).status, 400);
    const unknownAnalysis = await api.request("GET", "/v1/analyses/not-a-uuid", { userId: user });
    assertEquals([unknownAnalysis.status, errorCode(unknownAnalysis.body)], [404, "not_found"]);
    assertEquals((await api.request("GET", "/v1/nothing", { userId: user })).status, 404);
    assertEquals((await api.request("PATCH", "/v1/analyses", { userId: user })).status, 405);
    const twinsSort = await api.request("GET", `/v1/analyses/${crypto.randomUUID()}/twins?sort=random`, {
      userId: user,
    });
    assertEquals(twinsSort.status, 422);
  } finally {
    await api.close();
  }
});

Deno.test("users never see the analyses of other users", async () => {
  const api = await createTestApplication();
  try {
    const alice = await api.test.createUser();
    const mallory = await api.test.createUser();
    const id = await completedAnalysis(api, alice);
    for (
      const path of [
        `/v1/analyses/${id}`,
        `/v1/analyses/${id}/salary`,
        `/v1/analyses/${id}/twins`,
        `/v1/methodology?analysisId=${id}`,
      ]
    ) {
      assertEquals((await api.request("GET", path, { userId: mallory })).status, 404, path);
    }
    assertEquals((await api.request("DELETE", `/v1/analyses/${id}`, { userId: mallory })).status, 404);
    assertEquals((await api.request("PUT", `/v1/analyses/${id}/favorite`, { userId: mallory })).status, 404);
    assertEquals(
      (await api.request("POST", "/v1/shares", { userId: mallory, body: { analysisId: id, format: "link" } })).status,
      404,
    );
  } finally {
    await api.close();
  }
});

Deno.test("favorites, feedback, pagination and deletion of analyses", async () => {
  const api = await createTestApplication();
  try {
    const user = await api.test.createUser();
    await api.makePlus(user);
    const ids = [];
    for (const job of ["Comptable", "Infirmier", "Boulanger"]) {
      await api.request("POST", "/v1/analyses", {
        userId: user,
        body: { type: "manual", jobTitle: job, city: "Lyon" },
      });
    }
    await api.runWorker();
    const firstPage = await api.request("GET", "/v1/analyses?limit=2", { userId: user });
    ids.push(...(firstPage.body.items as { id: string }[]).map((item) => item.id));
    assertEquals(ids.length, 2);
    assertNotEquals(firstPage.body.nextCursor, null);
    const secondPage = await api.request("GET", `/v1/analyses?limit=2&cursor=${firstPage.body.nextCursor}`, {
      userId: user,
    });
    assertEquals((secondPage.body.items as unknown[]).length, 1);
    assertEquals(secondPage.body.nextCursor, null);
    const search = await api.request("GET", "/v1/analyses?query=BOULANGER", { userId: user });
    assertEquals((search.body.items as { jobTitle: string }[]).map((item) => item.jobTitle), ["Boulanger"]);

    assertEquals((await api.request("PUT", `/v1/analyses/${ids[0]}/favorite`, { userId: user })).status, 204);
    assertEquals((await api.request("PUT", `/v1/analyses/${ids[0]}/favorite`, { userId: user })).status, 204);
    assertEquals((await api.request("GET", `/v1/analyses/${ids[0]}`, { userId: user })).body.isFavorite, true);
    assertEquals((await api.request("DELETE", `/v1/analyses/${ids[0]}/favorite`, { userId: user })).status, 204);
    assertEquals((await api.request("GET", `/v1/analyses/${ids[0]}`, { userId: user })).body.isFavorite, false);

    assertEquals(
      (await api.request("POST", `/v1/analyses/${ids[0]}/feedback`, { userId: user, body: { actualApplicants: 0 } }))
        .status,
      422,
    );
    assertEquals(
      (await api.request("POST", `/v1/analyses/${ids[0]}/feedback`, { userId: user, body: { actualApplicants: 150 } }))
        .status,
      204,
    );
    assertEquals(
      (await api.request("POST", `/v1/analyses/${ids[0]}/feedback`, { userId: user, body: { actualApplicants: 160 } }))
        .status,
      204,
    );
    assertEquals(await api.test.database.query(`select actual_applicants from public.applicant_feedback`), [{
      actual_applicants: 160,
    }]);

    assertEquals((await api.request("DELETE", `/v1/analyses/${ids[0]}`, { userId: user })).status, 204);
    assertEquals((await api.request("GET", `/v1/analyses/${ids[0]}`, { userId: user })).status, 404);
  } finally {
    await api.close();
  }
});

Deno.test("shares give a public link that stops working when the share is deleted", async () => {
  const api = await createTestApplication();
  try {
    const user = await api.test.createUser();
    const id = await completedAnalysis(api, user);
    assertEquals((await api.request("GET", `/public/analyses/${id}`)).status, 404, "not public before a share");
    const share = await api.request("POST", "/v1/shares", {
      userId: user,
      body: { analysisId: id, format: "story", hideCompany: true },
    });
    assertEquals(share.status, 201);
    assertEquals(share.body.url, `https://jauge.io/a/${id}`);
    assert(/^[A-Za-z0-9_-]{22}$/.test(String(share.body.id)));
    assertEquals(share.body.showCalmTwin, true);
    const view = await api.request("GET", `/public/analyses/${id}`);
    assertEquals(view.status, 200);
    assertEquals(view.body.company, null, "the company is hidden as asked");
    assertEquals(view.headers.get("access-control-allow-origin"), "https://jauge.io");
    assertEquals(((await api.request("GET", "/v1/shares", { userId: user })).body.items as unknown[]).length, 1);
    assertEquals((await api.request("DELETE", `/v1/shares/${share.body.id}`, { userId: user })).status, 204);
    assertEquals((await api.request("GET", `/public/analyses/${id}`)).status, 404);
    assertEquals(
      (await api.request("POST", "/v1/shares", { userId: user, body: { analysisId: id, format: "poster" } })).status,
      422,
    );
  } finally {
    await api.close();
  }
});

Deno.test("calm alerts need Jauge Plus and notify a new calm twin once", async () => {
  const api = await createTestApplication();
  try {
    const user = await api.test.createUser();
    const id = await completedAnalysis(api, user);
    const refused = await api.request("POST", "/v1/alerts", { userId: user, body: { analysisId: id, threshold: 30 } });
    assertEquals([refused.status, errorCode(refused.body)], [402, "quota_reached"]);
    await api.makePlus(user);
    const created = await api.request("POST", "/v1/alerts", { userId: user, body: { analysisId: id, threshold: 99 } });
    assertEquals(created.status, 201);
    assertEquals(created.body.threshold, 99);
    assertEquals(((await api.request("GET", "/v1/alerts", { userId: user })).body.items as unknown[]).length, 1);

    // A new offer appears near Lyon with very few applicants: the maintenance notifies it once.
    await api.test.database.query(
      `insert into public.collected_offers (source, external_id, job_title, job_family, company, city, department_code, latitude,
         longitude, published_on, applicant_count, applicant_count_observed_at)
       select 'linkedin', 'new-calm-offer', 'Chargée de marketing digital', job_family, 'Okapi', 'Villeurbanne', '69', 45.77, 4.88,
              current_date - 3, 1, now()
       from public.offers limit 1`,
    );
    const maintenance = await api.request("POST", "/internal/maintenance", {
      headers: { "x-jauge-webhook-secret": TEST_WEBHOOK_SECRET },
    });
    assertEquals(maintenance.status, 200);
    const notifications = await api.test.database.query<{ kind: string; analysis_id: string }>(
      `select kind, analysis_id from public.notifications`,
    );
    assertEquals(notifications.length, 1);
    assertEquals(notifications[0], { kind: "calm_alert", analysis_id: id });
    await api.request("POST", "/internal/maintenance", { headers: { "x-jauge-webhook-secret": TEST_WEBHOOK_SECRET } });
    assert((await api.test.database.query(`select * from public.alert_matches`)).length >= 1);

    assertEquals((await api.request("DELETE", `/v1/alerts/${created.body.id}`, { userId: user })).status, 204);
    assertEquals((await api.request("DELETE", `/v1/alerts/${created.body.id}`, { userId: user })).status, 404);
  } finally {
    await api.close();
  }
});

Deno.test("the RevenueCat webhook checks its authorization, applies each event once and in order", async () => {
  const api = await createTestApplication();
  try {
    const user = await api.test.createUser();
    const event = (id: string, type: string, timestamp: number, extra: Record<string, unknown> = {}) => ({
      api_version: "1.0",
      event: {
        id,
        type,
        app_user_id: user,
        aliases: ["$RCAnonymousID:abc", user],
        entitlement_ids: ["plus"],
        product_id: "jauge_plus_monthly",
        period_type: "TRIAL",
        event_timestamp_ms: timestamp,
        expiration_at_ms: Date.now() + 3 * 86_400_000,
        environment: "PRODUCTION",
        store: "APP_STORE",
        ...extra,
      },
    });
    const send = (body: unknown, authorization = TEST_REVENUECAT_AUTHORIZATION) =>
      api.request("POST", "/webhooks/revenuecat", { body, headers: { Authorization: authorization } });

    assertEquals((await send(event("e1", "INITIAL_PURCHASE", 1000), "Bearer wrong")).status, 401);
    assertEquals((await send(event("e1", "INITIAL_PURCHASE", 1000))).body.status, "applied");
    assertEquals((await send(event("e1", "INITIAL_PURCHASE", 1000))).body.status, "duplicate");
    const trial = await api.request("GET", "/v1/subscription", { userId: user });
    assertEquals(trial.body.status, "trial");
    assert(typeof trial.body.trialEndsOn === "string");

    assertEquals(
      (await send(
        event("e2", "RENEWAL", 3000, { period_type: "NORMAL", expiration_at_ms: Date.now() + 30 * 86_400_000 }),
      )).body.status,
      "applied",
    );
    assertEquals((await api.request("GET", "/v1/subscription", { userId: user })).body.status, "active");
    assertEquals((await send(event("e3", "EXPIRATION", 2000))).body.status, "ignored", "older than the renewal");
    assertEquals(
      (await send(event("e4", "EXPIRATION", 4000, { expiration_at_ms: Date.now() - 1000 }))).body.status,
      "applied",
    );
    assertEquals((await api.request("GET", "/v1/subscription", { userId: user })).body.status, "expired");
    assertEquals(
      (await send(event("e5", "INITIAL_PURCHASE", 5000, { app_user_id: "$RCAnonymousID:zzz", aliases: [] }))).body
        .status,
      "ignored",
    );
    assertEquals((await send({ event: { type: "RENEWAL" } })).status, 400);
  } finally {
    await api.close();
  }
});

Deno.test("deleting the account erases the data at once, then RevenueCat, Apple and the auth user", async () => {
  const api = await createTestApplication();
  try {
    const user = await api.test.createUser();
    const other = await api.test.createUser();
    await completedAnalysis(api, user);
    await completedAnalysis(api, other);
    api.accountServices.failuresToSimulate = 1;
    const deleted = await api.request("DELETE", "/v1/account", {
      userId: user,
      body: { appleAuthorizationCode: "c0de.apple-authorization" },
    });
    assertEquals(deleted.status, 202);
    assertEquals(
      (await api.test.database.query(`select * from public.analyses where user_id = $1::uuid`, [user])).length,
      0,
    );
    assertEquals(
      (await api.test.database.query(`select * from public.analyses where user_id = $1::uuid`, [other])).length,
      1,
    );
    const refused = await api.request("POST", "/v1/analyses", {
      userId: user,
      body: { type: "manual", jobTitle: "Comptable", city: "Lyon" },
    });
    assertEquals(refused.status, 401);

    await api.test.database.query(`update public.jobs set run_after = now() where kind = 'delete_account'`);
    await api.runWorker();
    await api.test.database.query(`update public.jobs set run_after = now() where kind = 'delete_account'`);
    await api.runWorker();
    assertEquals(api.accountServices.calls.filter((call) => !call.startsWith("alert")), [
      `revenuecat:${user}`,
      "apple",
      `auth:${user}`,
    ]);
    const job = await api.test.database.query<{ status: string; payload: unknown }>(
      `select status, payload from public.jobs where kind = 'delete_account'`,
    );
    assertEquals(job[0], { status: "succeeded", payload: {} }, "the Apple code is wiped");
  } finally {
    await api.close();
  }
});

Deno.test("rate limits answer 429 with Retry-After", async () => {
  const api = await createTestApplication();
  try {
    const user = await api.test.createUser();
    await api.makePlus(user);
    const statuses = [];
    for (let index = 0; index < 7; index++) {
      const response = await api.request("POST", "/v1/analyses", {
        userId: user,
        body: { type: "manual", jobTitle: `Métier ${index}`, city: "Lyon" },
      });
      statuses.push(response.status);
      if (response.status === 429) {
        assertEquals(errorCode(response.body), "rate_limited");
        assert(Number(response.headers.get("retry-after")) >= 1);
      }
    }
    assertEquals(statuses, [202, 202, 202, 202, 202, 202, 429]);
  } finally {
    await api.close();
  }
});

Deno.test("maintenance mode, health check and internal routes", async () => {
  const api = await createTestApplication({ environment: { JAUGE_MAINTENANCE: "true" } });
  try {
    const user = await api.test.createUser();
    const paused = await api.request("GET", "/v1/analyses", { userId: user });
    assertEquals([paused.status, errorCode(paused.body)], [503, "maintenance"]);
    const health = await api.request("GET", "/health");
    assertEquals(health.status, 200);
    assertEquals(health.body.status, "ok");
    assertEquals(health.body.queue, undefined, "details need the secret");
    const detailed = await api.request("GET", "/functions/v1/api/health", {
      headers: { "x-jauge-webhook-secret": TEST_WEBHOOK_SECRET },
    });
    assertEquals((detailed.body.queue as { due: number }).due, 0);
    assertEquals((await api.request("POST", "/internal/worker")).status, 401);
    assertEquals(
      (await api.request("POST", "/internal/worker", { headers: { "x-jauge-webhook-secret": "wrong" } })).status,
      401,
    );
    assertEquals(
      (await api.request("POST", "/api/internal/worker", {
        headers: { "x-jauge-webhook-secret": TEST_WEBHOOK_SECRET },
      })).status,
      202,
    );
    const logged = JSON.stringify(api.logger.lines);
    assert(!logged.includes(user), "logs never contain user identifiers");
  } finally {
    await api.close();
  }
});
