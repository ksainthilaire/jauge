/** Error codes of the contract (docs/openapi.yaml, schema Error). */
export type ApiErrorCode =
  | "quota_reached"
  | "unreadable_offer"
  | "invalid_link"
  | "unsupported_link"
  | "image_too_large"
  | "validation_failed"
  | "unauthorized"
  | "not_found"
  | "rate_limited"
  | "maintenance"
  | "internal";

/** HTTP status of each error code when nothing more precise is given. */
const DEFAULT_STATUS: Readonly<Record<ApiErrorCode, number>> = {
  quota_reached: 402,
  unreadable_offer: 422,
  invalid_link: 422,
  unsupported_link: 422,
  image_too_large: 413,
  validation_failed: 400,
  unauthorized: 401,
  not_found: 404,
  rate_limited: 429,
  maintenance: 503,
  internal: 500,
};

/**
 * Typed error of the API. The router turns it into `{ "error": { "code", "message" } }` with its HTTP status.
 * `message` is for developers only: it never contains personal data.
 */
export class ApiError extends Error {
  /** Machine readable code of the contract. */
  readonly code: ApiErrorCode;
  /** HTTP status of the answer. */
  readonly status: number;
  /** Seconds to wait, sent as `Retry-After` (rate limits, maintenance). */
  readonly retryAfterSeconds: number | null;

  /**
   * @param code error code of the contract.
   * @param message message for developers.
   * @param options HTTP status (default of the code) and seconds to wait.
   */
  constructor(code: ApiErrorCode, message: string, options: { status?: number; retryAfterSeconds?: number } = {}) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.status = options.status ?? DEFAULT_STATUS[code];
    this.retryAfterSeconds = options.retryAfterSeconds ?? null;
  }
}

/** Shortcut for a 404 `not_found`. */
export function notFound(what: string): ApiError {
  return new ApiError("not_found", `${what} not found`);
}

/** Shortcut for a `validation_failed` (400 by default, 422 when the body is well formed but wrong). */
export function validationFailed(message: string, status = 400): ApiError {
  return new ApiError("validation_failed", message, { status });
}
