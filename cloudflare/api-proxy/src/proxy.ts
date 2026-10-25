/**
 * Cloudflare Worker in front of the Jauge API: `https://api.jauge.io/*` is forwarded to the Edge Function "api" of
 * Supabase (`https://<project>.supabase.co/functions/v1/api/*`). It keeps the public URL stable (the app never knows
 * the Supabase URL), exposes only the public routes, and tells the API the address of the client through a header
 * signed by a shared secret (the API hashes it for its rate limits and never stores it).
 */

/** Settings of the Worker (wrangler.toml variables and secrets). */
export interface ProxyEnvironment {
  /** `https://<project-ref>.supabase.co/functions/v1/api`, without a final slash. */
  readonly SUPABASE_FUNCTION_URL: string;
  /** Secret shared with the API (`JAUGE_PROXY_SECRET` on both sides). */
  readonly JAUGE_PROXY_SECRET: string;
}

/** Paths served through the proxy. Internal routes (worker, maintenance) are only reachable from the database. */
const ALLOWED_PATHS = [/^\/v1(\/|$)/, /^\/health$/, /^\/public\/analyses\/[^/]+$/, /^\/webhooks\/revenuecat$/];

/** Headers forwarded to the API. Everything else (cookies, internal secrets, spoofed addresses) is dropped. */
const FORWARDED_HEADERS = [
  "accept",
  "accept-language",
  "authorization",
  "content-type",
  "content-length",
  "idempotency-key",
  "user-agent",
  "origin",
];

/** Largest body forwarded (images are 10 MB at most). */
const MAXIMUM_BODY_BYTES = 11 * 1024 * 1024;

function notFound(): Response {
  return new Response(JSON.stringify({ error: { code: "not_found", message: "Route not found" } }), {
    status: 404,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}

/**
 * Forwards one request.
 *
 * @param request request received by the Worker.
 * @param environment settings.
 * @param fetcher fetch function (the global one in the Worker, a fake in tests).
 */
export async function forward(
  request: Request,
  environment: ProxyEnvironment,
  fetcher: (request: Request) => Promise<Response> = fetch,
): Promise<Response> {
  const url = new URL(request.url);
  if (!ALLOWED_PATHS.some((pattern) => pattern.test(url.pathname))) return notFound();
  const declared = Number(request.headers.get("content-length") ?? "0");
  if (declared > MAXIMUM_BODY_BYTES) {
    return new Response(JSON.stringify({ error: { code: "image_too_large", message: "Body too large" } }), {
      status: 413,
      headers: { "Content-Type": "application/json; charset=utf-8" },
    });
  }
  const headers = new Headers();
  for (const name of FORWARDED_HEADERS) {
    const value = request.headers.get(name);
    if (value !== null) headers.set(name, value);
  }
  headers.set("x-jauge-proxy-secret", environment.JAUGE_PROXY_SECRET);
  const clientAddress = request.headers.get("cf-connecting-ip");
  if (clientAddress) headers.set("x-jauge-client-ip", clientAddress);
  const target = `${environment.SUPABASE_FUNCTION_URL.replace(/\/$/, "")}${url.pathname}${url.search}`;
  const hasBody = request.method !== "GET" && request.method !== "HEAD";
  const upstream = await fetcher(
    new Request(target, {
      method: request.method,
      headers,
      body: hasBody ? request.body : undefined,
      redirect: "manual",
    }),
  );
  const response = new Response(upstream.body, upstream);
  response.headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
  return response;
}

export default {
  fetch(request: Request, environment: ProxyEnvironment): Promise<Response> {
    return forward(request, environment);
  },
};
