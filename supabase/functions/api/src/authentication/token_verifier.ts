import { createRemoteJWKSet, decodeProtectedHeader, type JWTPayload, jwtVerify } from "jose";

/** The caller of a request, read from a verified Supabase access token. */
export interface AuthenticatedUser {
  /** Supabase user identifier (`sub`). */
  readonly userId: string;
  /** True for an anonymous Supabase session. */
  readonly isAnonymous: boolean;
}

/** Checks a Supabase access token. */
export interface TokenVerifier {
  /**
   * Returns the user of a valid token, or null for a missing, expired, badly signed or foreign token.
   *
   * @param token bearer token.
   */
  verify(token: string): Promise<AuthenticatedUser | null>;
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

/**
 * Verifies Supabase access tokens locally, without a network call per request:
 * - asymmetric keys (ES256, RS256, the default of new projects) through the JWKS of the project, cached by jose;
 * - the legacy HS256 secret when `SUPABASE_JWT_SECRET` is set.
 * Checks the issuer (`<project>/auth/v1`), the audience `authenticated`, the expiry and the `sub` claim.
 */
export class SupabaseTokenVerifier implements TokenVerifier {
  private readonly issuer: string;
  private readonly keySet: ReturnType<typeof createRemoteJWKSet>;
  private readonly legacySecret: Uint8Array | null;

  /**
   * @param supabaseUrl URL of the project.
   * @param legacyJwtSecret HS256 secret of the project, null when the project only uses asymmetric keys.
   */
  constructor(supabaseUrl: string, legacyJwtSecret: string | null) {
    this.issuer = `${supabaseUrl.replace(/\/$/, "")}/auth/v1`;
    this.keySet = createRemoteJWKSet(new URL(`${this.issuer}/.well-known/jwks.json`), {
      cacheMaxAge: 10 * 60 * 1000,
      cooldownDuration: 30 * 1000,
    });
    this.legacySecret = legacyJwtSecret ? new TextEncoder().encode(legacyJwtSecret) : null;
  }

  /** @inheritdoc */
  async verify(token: string): Promise<AuthenticatedUser | null> {
    try {
      const header = decodeProtectedHeader(token);
      const options = { issuer: this.issuer, audience: "authenticated", clockTolerance: 30 };
      let payload: JWTPayload;
      if (header.alg === "HS256") {
        if (!this.legacySecret) return null;
        payload = (await jwtVerify(token, this.legacySecret, { ...options, algorithms: ["HS256"] })).payload;
      } else {
        payload =
          (await jwtVerify(token, this.keySet, { ...options, algorithms: ["ES256", "RS256", "EdDSA"] })).payload;
      }
      return userFromPayload(payload);
    } catch {
      return null;
    }
  }
}

/** Builds the user from verified claims, or null when the claims are not those of a user session. */
export function userFromPayload(payload: JWTPayload): AuthenticatedUser | null {
  if (typeof payload.sub !== "string" || !UUID_PATTERN.test(payload.sub)) return null;
  if (payload.role !== undefined && payload.role !== "authenticated") return null;
  return { userId: payload.sub, isAnonymous: payload.is_anonymous === true };
}

/** Verifier of the tests: tokens are `test-token.<user id>`. */
export class FakeTokenVerifier implements TokenVerifier {
  /** @inheritdoc */
  verify(token: string): Promise<AuthenticatedUser | null> {
    const match = /^test-token\.([0-9a-f-]{36})$/.exec(token);
    return Promise.resolve(match ? { userId: match[1], isAnonymous: false } : null);
  }
}
