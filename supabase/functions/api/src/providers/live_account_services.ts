import { importPKCS8, SignJWT } from "jose";
import type { AppleTokenRevoker, AuthAdministration, OperationsAlerter, SubscriptionStore } from "./provider_types.ts";
import { ProviderError } from "./provider_types.ts";

/** Fetch function (injected so that tests can answer without network). */
export type FetchFunction = (input: string, init?: RequestInit) => Promise<Response>;

const TIMEOUT_MILLISECONDS = 10_000;

async function call(fetcher: FetchFunction, provider: string, url: string, init: RequestInit): Promise<Response> {
  let response: Response;
  try {
    response = await fetcher(url, { ...init, signal: AbortSignal.timeout(TIMEOUT_MILLISECONDS) });
  } catch {
    throw new ProviderError(provider, "network", true);
  }
  return response;
}

/** RevenueCat REST API v1 with the secret key (`sk_...`), never shipped in the app. */
export class RevenueCatSubscriptionStore implements SubscriptionStore {
  /**
   * @param secretApiKey secret key of the RevenueCat project.
   * @param fetcher fetch function.
   */
  constructor(private readonly secretApiKey: string, private readonly fetcher: FetchFunction = fetch) {}

  /** @inheritdoc */
  async deleteCustomer(appUserId: string): Promise<void> {
    const response = await call(
      this.fetcher,
      "revenuecat",
      `https://api.revenuecat.com/v1/subscribers/${encodeURIComponent(appUserId)}`,
      { method: "DELETE", headers: { Authorization: `Bearer ${this.secretApiKey}` } },
    );
    await response.body?.cancel();
    // 404: the customer never bought anything, nothing to delete.
    if (response.ok || response.status === 404) return;
    throw new ProviderError("revenuecat", `http_${response.status}`, response.status >= 500 || response.status === 429);
  }
}

/** Sign in with Apple: exchanges the authorization code for a refresh token, then revokes it. */
export class AppleSignInRevoker implements AppleTokenRevoker {
  /**
   * @param settings team identifier, client identifier (bundle identifier of the app), key identifier and .p8 key.
   * @param fetcher fetch function.
   */
  constructor(
    private readonly settings: { teamId: string; clientId: string; keyId: string; privateKey: string },
    private readonly fetcher: FetchFunction = fetch,
  ) {}

  /** @inheritdoc */
  async revokeWithAuthorizationCode(authorizationCode: string): Promise<void> {
    const clientSecret = await this.clientSecret();
    const tokenResponse = await call(this.fetcher, "apple", "https://appleid.apple.com/auth/token", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        client_id: this.settings.clientId,
        client_secret: clientSecret,
        code: authorizationCode,
        grant_type: "authorization_code",
      }),
    });
    if (!tokenResponse.ok) {
      await tokenResponse.body?.cancel();
      // An expired or used code cannot become valid again: do not retry.
      throw new ProviderError("apple", `token_http_${tokenResponse.status}`, tokenResponse.status >= 500);
    }
    const tokens = await tokenResponse.json() as { refresh_token?: string; access_token?: string };
    const token = tokens.refresh_token ?? tokens.access_token;
    if (!token) throw new ProviderError("apple", "no_token", false);
    const revokeResponse = await call(this.fetcher, "apple", "https://appleid.apple.com/auth/revoke", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        client_id: this.settings.clientId,
        client_secret: clientSecret,
        token,
        token_type_hint: tokens.refresh_token ? "refresh_token" : "access_token",
      }),
    });
    await revokeResponse.body?.cancel();
    if (!revokeResponse.ok) {
      throw new ProviderError("apple", `revoke_http_${revokeResponse.status}`, revokeResponse.status >= 500);
    }
  }

  private async clientSecret(): Promise<string> {
    const key = await importPKCS8(this.settings.privateKey.replace(/\\n/g, "\n"), "ES256");
    return await new SignJWT({})
      .setProtectedHeader({ alg: "ES256", kid: this.settings.keyId })
      .setIssuer(this.settings.teamId)
      .setSubject(this.settings.clientId)
      .setAudience("https://appleid.apple.com")
      .setIssuedAt()
      .setExpirationTime("5m")
      .sign(key);
  }
}

/** Supabase Auth admin endpoint with the service role key. */
export class SupabaseAuthAdministration implements AuthAdministration {
  /**
   * @param supabaseUrl URL of the project.
   * @param serviceRoleKey service role key (provided by Supabase to the functions).
   * @param fetcher fetch function.
   */
  constructor(
    private readonly supabaseUrl: string,
    private readonly serviceRoleKey: string,
    private readonly fetcher: FetchFunction = fetch,
  ) {}

  /** @inheritdoc */
  async deleteUser(userId: string): Promise<void> {
    const response = await call(
      this.fetcher,
      "supabase_auth",
      `${this.supabaseUrl.replace(/\/$/, "")}/auth/v1/admin/users/${encodeURIComponent(userId)}`,
      { method: "DELETE", headers: { Authorization: `Bearer ${this.serviceRoleKey}`, apikey: this.serviceRoleKey } },
    );
    await response.body?.cancel();
    if (response.ok || response.status === 404) return;
    throw new ProviderError(
      "supabase_auth",
      `http_${response.status}`,
      response.status >= 500 || response.status === 429,
    );
  }
}

/** Sends operations alerts as a JSON POST (works with ntfy, Slack and Discord incoming webhooks). */
export class WebhookOperationsAlerter implements OperationsAlerter {
  /**
   * @param url URL of the webhook, null to only log.
   * @param fetcher fetch function.
   */
  constructor(private readonly url: string | null, private readonly fetcher: FetchFunction = fetch) {}

  /** @inheritdoc */
  async send(title: string, message: string): Promise<void> {
    if (!this.url) return;
    const response = await call(this.fetcher, "alert_webhook", this.url, {
      method: "POST",
      headers: { "Content-Type": "application/json", Title: title },
      body: JSON.stringify({ title, message, text: `${title}: ${message}`, content: `${title}: ${message}` }),
    });
    await response.body?.cancel();
  }
}
