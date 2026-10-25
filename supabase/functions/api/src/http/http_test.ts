import { assertEquals } from "@std/assert";
import { Router, stripFunctionPrefix } from "./router.ts";
import { languageFromHeader } from "../scoring/methodology_texts.ts";
import { detectImageType } from "../routes/analysis_requests.ts";
import { secretsMatch } from "../operations/rate_limiter.ts";
import { parisWeekStart } from "../operations/clock.ts";

Deno.test("router matches templates and reports other methods", () => {
  const router = new Router<string>().add("GET", "/v1/analyses/:id", "one").add("DELETE", "/v1/analyses/:id", "delete");
  const found = router.lookup("GET", "/v1/analyses/abc");
  assertEquals(found.kind === "found" ? [found.route.handler, found.match.parameters.id] : null, ["one", "abc"]);
  assertEquals(router.lookup("POST", "/v1/analyses/abc").kind, "method_not_allowed");
  assertEquals(router.lookup("GET", "/v1/analyses/abc/def").kind, "not_found");
  assertEquals(stripFunctionPrefix("/functions/v1/api/v1/analyses/"), "/v1/analyses");
  assertEquals(stripFunctionPrefix("/api/health"), "/health");
  assertEquals(stripFunctionPrefix("/v1/account"), "/v1/account");
});

Deno.test("language, image types, secrets and quota weeks", () => {
  assertEquals(languageFromHeader("en-US,en;q=0.9,fr;q=0.8"), "en");
  assertEquals(languageFromHeader("de-DE, fr;q=0.5"), "fr");
  assertEquals(languageFromHeader(null), "fr");
  assertEquals(detectImageType(new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0, 0, 0, 0])), "image/png");
  assertEquals(detectImageType(new TextEncoder().encode("\0\0\0\x18ftypheic....")), "image/heic");
  assertEquals(detectImageType(new TextEncoder().encode("<svg></svg>")), null);
  assertEquals(secretsMatch("abc", "abc"), true);
  assertEquals(secretsMatch("abc", "abd"), false);
  assertEquals(secretsMatch(null, "abc"), false);
  assertEquals(secretsMatch("abc", null), false);
  // Sunday 25 October 2026 at 23:30 UTC is already Monday 26 in Paris.
  assertEquals(parisWeekStart(new Date("2026-10-25T21:00:00Z")), "2026-10-19");
  assertEquals(parisWeekStart(new Date("2026-10-25T23:30:00Z")), "2026-10-26");
});
