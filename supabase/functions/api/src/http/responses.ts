import { ApiError } from "./api_error.ts";

/** Headers sent with every answer of the API. */
const COMMON_HEADERS: Readonly<Record<string, string>> = {
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "no-referrer",
};

/**
 * JSON answer.
 *
 * @param body value serialized as JSON.
 * @param status HTTP status.
 * @param headers extra headers.
 */
export function jsonResponse(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), { status, headers: { ...COMMON_HEADERS, ...headers } });
}

/** Empty answer (`204` by default). */
export function emptyResponse(status = 204): Response {
  const headers: Record<string, string> = { ...COMMON_HEADERS };
  delete headers["Content-Type"];
  return new Response(null, { status, headers });
}

/** Error answer in the shape of the contract. */
export function errorResponse(error: ApiError): Response {
  const headers: Record<string, string> = {};
  if (error.retryAfterSeconds !== null) headers["Retry-After"] = String(error.retryAfterSeconds);
  return jsonResponse({ error: { code: error.code, message: error.message } }, error.status, headers);
}
