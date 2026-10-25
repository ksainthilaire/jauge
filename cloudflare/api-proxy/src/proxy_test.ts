import { assertEquals } from "@std/assert";
import { forward } from "./proxy.ts";

const ENVIRONMENT = {
  SUPABASE_FUNCTION_URL: "https://project.supabase.co/functions/v1/api/",
  JAUGE_PROXY_SECRET: "proxy-secret",
};

function capture() {
  const seen: Request[] = [];
  return {
    seen,
    fetcher: (request: Request) => (seen.push(request), Promise.resolve(new Response("{}", { status: 200 }))),
  };
}

Deno.test("public routes are forwarded with the proxy secret and the client address, nothing else", async () => {
  const { seen, fetcher } = capture();
  const request = new Request("https://api.jauge.io/v1/analyses?limit=5", {
    headers: {
      Authorization: "Bearer token",
      "Accept-Language": "fr-FR",
      "CF-Connecting-IP": "203.0.113.7",
      "x-jauge-proxy-secret": "forged",
      "x-jauge-webhook-secret": "forged",
      "x-jauge-client-ip": "198.51.100.1",
      Cookie: "a=b",
    },
  });
  const response = await forward(request, ENVIRONMENT, fetcher);
  assertEquals(response.status, 200);
  assertEquals(seen[0].url, "https://project.supabase.co/functions/v1/api/v1/analyses?limit=5");
  assertEquals(seen[0].headers.get("x-jauge-proxy-secret"), "proxy-secret");
  assertEquals(seen[0].headers.get("x-jauge-client-ip"), "203.0.113.7");
  assertEquals(seen[0].headers.get("x-jauge-webhook-secret"), null);
  assertEquals(seen[0].headers.get("cookie"), null);
  assertEquals(seen[0].headers.get("authorization"), "Bearer token");
});

Deno.test("internal routes and unknown paths are not reachable through the proxy", async () => {
  const { seen, fetcher } = capture();
  for (const path of ["/internal/worker", "/internal/maintenance", "/", "/functions/v1/api/v1/analyses"]) {
    const response = await forward(
      new Request(`https://api.jauge.io${path}`, { method: "POST" }),
      ENVIRONMENT,
      fetcher,
    );
    assertEquals(response.status, 404, path);
  }
  assertEquals(seen.length, 0);
});
