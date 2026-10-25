import { assertEquals, assertRejects } from "@std/assert";
import { exportPKCS8, generateKeyPair, SignJWT } from "jose";
import {
  AppleSignInRevoker,
  RevenueCatSubscriptionStore,
  SupabaseAuthAdministration,
} from "./live_account_services.ts";
import { ProviderError } from "./provider_types.ts";
import { SupabaseTokenVerifier } from "../authentication/token_verifier.ts";

function recordingFetch(answers: Response[]) {
  const calls: { url: string; method: string; body: string }[] = [];
  const fetcher = (input: string, init?: RequestInit) => {
    calls.push({ url: input, method: init?.method ?? "GET", body: init?.body ? String(init.body) : "" });
    return Promise.resolve(answers.shift() ?? new Response(null, { status: 500 }));
  };
  return { calls, fetcher };
}

Deno.test("RevenueCat customers are deleted with the secret key, 404 is fine, 5xx is retryable", async () => {
  const { calls, fetcher } = recordingFetch([
    new Response(null, { status: 200 }),
    new Response(null, { status: 404 }),
    new Response(null, { status: 503 }),
  ]);
  const store = new RevenueCatSubscriptionStore("sk_test", fetcher);
  await store.deleteCustomer("user 1");
  await store.deleteCustomer("user-2");
  const error = await assertRejects(() => store.deleteCustomer("user-3"), ProviderError);
  assertEquals(error.retryable, true);
  assertEquals(calls[0], { url: "https://api.revenuecat.com/v1/subscribers/user%201", method: "DELETE", body: "" });
});

Deno.test("Supabase auth users are deleted through the admin endpoint", async () => {
  const { calls, fetcher } = recordingFetch([new Response(null, { status: 200 })]);
  await new SupabaseAuthAdministration("https://project.supabase.co/", "service-role", fetcher).deleteUser("abc");
  assertEquals(calls[0].url, "https://project.supabase.co/auth/v1/admin/users/abc");
});

Deno.test("Sign in with Apple: the code is exchanged then the refresh token revoked", async () => {
  const { privateKey } = await generateKeyPair("ES256", { extractable: true });
  const { calls, fetcher } = recordingFetch([
    Response.json({ refresh_token: "refresh-1", access_token: "access-1" }),
    new Response(null, { status: 200 }),
  ]);
  const revoker = new AppleSignInRevoker(
    { teamId: "TEAM123456", clientId: "io.jauge.app", keyId: "KEY1234567", privateKey: await exportPKCS8(privateKey) },
    fetcher,
  );
  await revoker.revokeWithAuthorizationCode("code-123");
  assertEquals(calls.map((call) => call.url), [
    "https://appleid.apple.com/auth/token",
    "https://appleid.apple.com/auth/revoke",
  ]);
  const revokeBody = new URLSearchParams(calls[1].body);
  assertEquals([revokeBody.get("token"), revokeBody.get("token_type_hint")], ["refresh-1", "refresh_token"]);
});

Deno.test("Supabase tokens: legacy HS256 tokens are checked for issuer, audience and expiry", async () => {
  const secret = "legacy-secret-legacy-secret-legacy-secret";
  const verifier = new SupabaseTokenVerifier("https://project.supabase.co", secret);
  const key = new TextEncoder().encode(secret);
  const sign = (claims: Record<string, unknown>, issuer = "https://project.supabase.co/auth/v1", expires = "1h") =>
    new SignJWT(claims).setProtectedHeader({ alg: "HS256" }).setIssuer(issuer).setAudience("authenticated")
      .setSubject("6f1c2d3e-4b5a-4c6d-8e7f-9a0b1c2d3e4f").setIssuedAt().setExpirationTime(expires).sign(key);
  assertEquals(await verifier.verify(await sign({ role: "authenticated" })), {
    userId: "6f1c2d3e-4b5a-4c6d-8e7f-9a0b1c2d3e4f",
    isAnonymous: false,
  });
  assertEquals(await verifier.verify(await sign({ role: "authenticated" }, "https://other.supabase.co/auth/v1")), null);
  assertEquals(await verifier.verify(await sign({ role: "service_role" })), null);
  assertEquals(await verifier.verify(await sign({ role: "authenticated" }, undefined, "-1h")), null);
  assertEquals(await verifier.verify("not.a.token"), null);
  assertEquals(await new SupabaseTokenVerifier("https://project.supabase.co", null).verify(await sign({})), null);
});
