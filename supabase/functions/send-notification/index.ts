// Jauge: sends one notification of the `notifications` table to every device of its user.
//
// Called by the database trigger `notifications_push_after_insert` (pg_net) with the header
// `x-jauge-webhook-secret`, or by a trusted server with the service role key as bearer token. Body:
//   { "notification_id": "<uuid>" }
// Android devices receive it through Firebase Cloud Messaging (HTTP v1), iPhones through APNs (token based auth).
// Calm alerts are limited to 3 pushes per user and per 7 days (promise of screen 12); the others stay in the app.
//
// Secrets (supabase secrets set ..., see docs/supabase.md), never in the repository:
//   JAUGE_WEBHOOK_SECRET        shared with the Vault secret jauge_webhook_secret
//   FCM_SERVICE_ACCOUNT_JSON    Firebase service account key (JSON, one line)
//   APNS_KEY_P8                 APNs authentication key (.p8 content)
//   APNS_KEY_ID, APNS_TEAM_ID, APNS_BUNDLE_ID
//   APNS_USE_SANDBOX            "true" for development builds
// SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY are provided by Supabase to every function.

import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2.49.4";

const CALM_ALERT_WEEKLY_LIMIT = 3;
const WEEK_MILLISECONDS = 7 * 24 * 60 * 60 * 1000;
const ANDROID_CHANNEL = "calm_alerts";

interface NotificationRow {
  id: string;
  user_id: string;
  kind: "calm_alert" | "trial_reminder" | "other";
  title: string;
  body: string;
  analysis_id: string | null;
  alert_id: string | null;
  push_sent_at: string | null;
}

interface DeviceRow {
  token: string;
  platform: "android" | "ios";
}

type DeliveryResult = "sent" | "invalid_token" | "failed" | "not_configured";

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  if (!isAuthorized(request)) return json({ error: "unauthorized" }, 401);

  let notificationId: string | undefined;
  try {
    const payload = await request.json();
    notificationId = payload?.notification_id ?? payload?.record?.id;
  } catch {
    return json({ error: "invalid_body" }, 400);
  }
  if (!notificationId || !/^[0-9a-f-]{36}$/i.test(notificationId)) return json({ error: "invalid_notification_id" }, 400);

  const database = createClient(requiredEnvironment("SUPABASE_URL"), requiredEnvironment("SUPABASE_SERVICE_ROLE_KEY"), {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: notification, error } = await database
    .from("notifications")
    .select("id, user_id, kind, title, body, analysis_id, alert_id, push_sent_at")
    .eq("id", notificationId)
    .maybeSingle<NotificationRow>();
  if (error) return json({ error: "database_error" }, 500);
  if (!notification) return json({ error: "not_found" }, 404);
  if (notification.push_sent_at) return json({ status: "already_sent" });

  if (notification.kind === "calm_alert" && (await weeklyLimitReached(database, notification.user_id))) {
    await markSkipped(database, notification.id, "weekly_limit");
    return json({ status: "skipped", reason: "weekly_limit" });
  }

  const { data: devices } = await database.from("devices").select("token, platform").eq("user_id", notification.user_id);
  if (!devices || devices.length === 0) {
    await markSkipped(database, notification.id, "no_device");
    return json({ status: "skipped", reason: "no_device" });
  }

  const results: Record<string, number> = { sent: 0, invalid_token: 0, failed: 0, not_configured: 0 };
  for (const device of devices as DeviceRow[]) {
    const result = device.platform === "android"
      ? await sendWithFirebase(device.token, notification)
      : await sendWithApns(device.token, notification);
    results[result]++;
    if (result === "invalid_token") {
      await database.from("devices").delete().eq("token", device.token);
    }
  }

  if (results.sent > 0) {
    await database.from("notifications").update({ push_sent_at: new Date().toISOString() }).eq("id", notification.id);
  } else {
    await markSkipped(database, notification.id, results.not_configured > 0 ? "push_not_configured" : "delivery_failed");
  }
  return json({ status: "done", results });
});

function isAuthorized(request: Request): boolean {
  const webhookSecret = Deno.env.get("JAUGE_WEBHOOK_SECRET");
  const header = request.headers.get("x-jauge-webhook-secret");
  if (webhookSecret && header && timingSafeEqual(header, webhookSecret)) return true;
  const serviceRole = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const bearer = request.headers.get("authorization")?.replace(/^Bearer\s+/i, "");
  return Boolean(serviceRole && bearer && timingSafeEqual(bearer, serviceRole));
}

function timingSafeEqual(left: string, right: string): boolean {
  const encoder = new TextEncoder();
  const a = encoder.encode(left);
  const b = encoder.encode(right);
  let difference = a.length ^ b.length;
  for (let index = 0; index < Math.max(a.length, b.length); index++) difference |= (a[index] ?? 0) ^ (b[index] ?? 0);
  return difference === 0;
}

async function weeklyLimitReached(database: SupabaseClient, userId: string): Promise<boolean> {
  const since = new Date(Date.now() - WEEK_MILLISECONDS).toISOString();
  const { count } = await database
    .from("notifications")
    .select("id", { count: "exact", head: true })
    .eq("user_id", userId)
    .eq("kind", "calm_alert")
    .gte("push_sent_at", since);
  return (count ?? 0) >= CALM_ALERT_WEEKLY_LIMIT;
}

async function markSkipped(database: SupabaseClient, id: string, reason: string) {
  await database.from("notifications").update({ push_skipped_reason: reason }).eq("id", id);
}

function customData(notification: NotificationRow): Record<string, string> {
  const data: Record<string, string> = { notification_id: notification.id, kind: notification.kind };
  if (notification.analysis_id) data.analysis_id = notification.analysis_id;
  if (notification.alert_id) data.alert_id = notification.alert_id;
  return data;
}

// Firebase Cloud Messaging (HTTP v1) ----------------------------------------------------------------------------------

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

let firebaseAccessToken: { value: string; expiresAt: number } | undefined;

async function sendWithFirebase(token: string, notification: NotificationRow): Promise<DeliveryResult> {
  const raw = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON");
  if (!raw) return "not_configured";
  const account = JSON.parse(raw) as ServiceAccount;
  const accessToken = await firebaseToken(account);
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`, {
    method: "POST",
    headers: { authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
    body: JSON.stringify({
      message: {
        token,
        notification: { title: notification.title, body: notification.body },
        data: customData(notification),
        android: { priority: "HIGH", notification: { channel_id: ANDROID_CHANNEL } },
      },
    }),
  });
  if (response.ok) return "sent";
  const text = await response.text();
  if (response.status === 404 || text.includes("UNREGISTERED") || text.includes("registration-token-not-registered")) {
    return "invalid_token";
  }
  if (response.status === 400 && text.includes("INVALID_ARGUMENT") && text.includes("token")) return "invalid_token";
  console.error("FCM error", response.status, text.slice(0, 300));
  return "failed";
}

async function firebaseToken(account: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (firebaseAccessToken && firebaseAccessToken.expiresAt - 60 > now) return firebaseAccessToken.value;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToBytes(account.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const assertion = await signJwt(
    { alg: "RS256", typ: "JWT" },
    {
      iss: account.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    },
    (data) => crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, data),
  );
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
  });
  if (!response.ok) throw new Error(`Google OAuth error ${response.status}`);
  const body = await response.json();
  firebaseAccessToken = { value: body.access_token, expiresAt: now + (body.expires_in ?? 3600) };
  return firebaseAccessToken.value;
}

// Apple Push Notification service (token based) -----------------------------------------------------------------------

let apnsJwt: { value: string; issuedAt: number } | undefined;

async function sendWithApns(token: string, notification: NotificationRow): Promise<DeliveryResult> {
  const keyP8 = Deno.env.get("APNS_KEY_P8");
  const keyId = Deno.env.get("APNS_KEY_ID");
  const teamId = Deno.env.get("APNS_TEAM_ID");
  const bundleId = Deno.env.get("APNS_BUNDLE_ID");
  if (!keyP8 || !keyId || !teamId || !bundleId) return "not_configured";
  const host = Deno.env.get("APNS_USE_SANDBOX") === "true" ? "api.sandbox.push.apple.com" : "api.push.apple.com";
  const response = await fetch(`https://${host}/3/device/${token}`, {
    method: "POST",
    headers: {
      authorization: `bearer ${await apnsToken(keyP8, keyId, teamId)}`,
      "apns-topic": bundleId,
      "apns-push-type": "alert",
      "apns-priority": "10",
      "content-type": "application/json",
    },
    body: JSON.stringify({
      aps: { alert: { title: notification.title, body: notification.body }, sound: "default" },
      ...customData(notification),
    }),
  });
  if (response.ok) return "sent";
  const text = await response.text();
  if (response.status === 410 || text.includes("BadDeviceToken") || text.includes("Unregistered")) return "invalid_token";
  console.error("APNs error", response.status, text.slice(0, 300));
  return "failed";
}

async function apnsToken(keyP8: string, keyId: string, teamId: string): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  // Apple accepts a provider token for one hour and refuses refreshing it more than once every 20 minutes.
  if (apnsJwt && now - apnsJwt.issuedAt < 40 * 60) return apnsJwt.value;
  const key = await crypto.subtle.importKey("pkcs8", pemToBytes(keyP8), { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"]);
  const value = await signJwt(
    { alg: "ES256", kid: keyId },
    { iss: teamId, iat: now },
    (data) => crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, data),
  );
  apnsJwt = { value, issuedAt: now };
  return value;
}

// Helpers ------------------------------------------------------------------------------------------------------------

async function signJwt(
  header: Record<string, unknown>,
  claims: Record<string, unknown>,
  sign: (data: Uint8Array) => Promise<ArrayBuffer>,
): Promise<string> {
  const encoder = new TextEncoder();
  const unsigned = `${base64Url(encoder.encode(JSON.stringify(header)))}.${base64Url(encoder.encode(JSON.stringify(claims)))}`;
  const signature = new Uint8Array(await sign(encoder.encode(unsigned)));
  return `${unsigned}.${base64Url(signature)}`;
}

function pemToBytes(pem: string): Uint8Array {
  const base64 = pem.replace(/\\n/g, "\n").replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  return Uint8Array.from(atob(base64), (character) => character.charCodeAt(0));
}

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function requiredEnvironment(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new Error(`Missing environment variable ${name}`);
  return value;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });
}
