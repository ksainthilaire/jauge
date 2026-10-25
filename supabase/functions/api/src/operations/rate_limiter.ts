import { ApiError } from "../http/api_error.ts";
import type { Database } from "../database/database.ts";

/** One rate limit: at most `maximumHits` per window of `windowSeconds`. */
export interface RateLimit {
  readonly name: string;
  readonly windowSeconds: number;
  readonly maximumHits: number;
}

/** Limits of the API (per user and per IP address). */
export const RATE_LIMITS = {
  /** Every request of an IP address (hashed). */
  ipPerMinute: { name: "ip", windowSeconds: 60, maximumHits: 300 },
  /** Every request of a user (the app polls about once per second while an analysis runs). */
  userPerMinute: { name: "user", windowSeconds: 60, maximumHits: 240 },
  /** Analyses started by a user per minute. */
  analysesPerMinute: { name: "analyses_minute", windowSeconds: 60, maximumHits: 6 },
  /** Public pages of shared analyses per IP address. */
  publicPerMinute: { name: "public", windowSeconds: 60, maximumHits: 60 },
} as const satisfies Record<string, RateLimit>;

/** Fixed window rate limiter stored in Postgres (shared by every isolate of the function). */
export class RateLimiter {
  /** @param database database. */
  constructor(private readonly database: Database) {}

  /**
   * Counts one hit and throws `rate_limited` (429, with `Retry-After`) over the limit.
   *
   * @param limit the limit.
   * @param subject user identifier or hashed IP address.
   */
  async hit(limit: RateLimit, subject: string): Promise<void> {
    const rows = await this.database.query<{ allowed: boolean; retry_after_seconds: number }>(
      `select allowed, retry_after_seconds from public.api_hit_rate_limit($1, $2, $3)`,
      [`${limit.name}:${subject}`.slice(0, 120), limit.windowSeconds, limit.maximumHits],
    );
    const row = rows[0];
    if (row && !row.allowed) {
      throw new ApiError("rate_limited", `Rate limit ${limit.name} reached`, {
        retryAfterSeconds: row.retry_after_seconds,
      });
    }
  }
}

/**
 * Hash of an IP address for the rate limits: the address itself is never stored nor logged.
 *
 * @param address IP address.
 * @param salt secret salt (`JAUGE_IP_HASH_SALT`).
 */
export async function hashAddress(address: string, salt: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`${salt}|${address}`));
  return [...new Uint8Array(digest).slice(0, 12)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

/**
 * Constant time comparison of two secrets.
 *
 * @param received value received.
 * @param expected configured value.
 */
export function secretsMatch(received: string | null, expected: string | null): boolean {
  if (!received || !expected) return false;
  const first = new TextEncoder().encode(received);
  const second = new TextEncoder().encode(expected);
  let difference = first.length ^ second.length;
  for (let index = 0; index < Math.max(first.length, second.length); index++) {
    difference |= (first[index] ?? 0) ^ (second[index] ?? 0);
  }
  return difference === 0;
}
