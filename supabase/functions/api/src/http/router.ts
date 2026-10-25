/** Values extracted from a request before its handler runs. */
export interface RouteMatch {
  /** Path parameters (`:id`). */
  readonly parameters: Readonly<Record<string, string>>;
  /** Template of the route (`/v1/analyses/:id`), used in the logs instead of the real path. */
  readonly template: string;
}

/** A route of the router. */
export interface Route<Handler> {
  /** HTTP method. */
  readonly method: string;
  /** Template with `:name` segments. */
  readonly template: string;
  /** Handler of the route. */
  readonly handler: Handler;
}

/** Result of a lookup: a route, a known path with another method, or nothing. */
export type RouteLookup<Handler> =
  | { readonly kind: "found"; readonly route: Route<Handler>; readonly match: RouteMatch }
  | { readonly kind: "method_not_allowed"; readonly allowed: readonly string[] }
  | { readonly kind: "not_found" };

/**
 * Small router with path templates. Segments are compared exactly; `:name` segments capture one segment.
 */
export class Router<Handler> {
  private readonly routes: Route<Handler>[] = [];

  /**
   * Adds a route.
   *
   * @param method HTTP method.
   * @param template path template.
   * @param handler handler of the route.
   */
  add(method: string, template: string, handler: Handler): this {
    this.routes.push({ method: method.toUpperCase(), template, handler });
    return this;
  }

  /**
   * Finds the route of a request.
   *
   * @param method HTTP method.
   * @param path path without the function prefix.
   */
  lookup(method: string, path: string): RouteLookup<Handler> {
    const allowed: string[] = [];
    for (const route of this.routes) {
      const parameters = matchTemplate(route.template, path);
      if (!parameters) continue;
      if (route.method === method.toUpperCase()) {
        return { kind: "found", route, match: { parameters, template: route.template } };
      }
      allowed.push(route.method);
    }
    return allowed.length > 0 ? { kind: "method_not_allowed", allowed } : { kind: "not_found" };
  }
}

function matchTemplate(template: string, path: string): Record<string, string> | null {
  const templateSegments = template.split("/").filter((segment) => segment !== "");
  const pathSegments = path.split("/").filter((segment) => segment !== "");
  if (templateSegments.length !== pathSegments.length) return null;
  const parameters: Record<string, string> = {};
  for (let index = 0; index < templateSegments.length; index++) {
    const expected = templateSegments[index];
    const actual = pathSegments[index];
    if (expected.startsWith(":")) {
      let decoded: string;
      try {
        decoded = decodeURIComponent(actual);
      } catch {
        return null;
      }
      parameters[expected.slice(1)] = decoded;
    } else if (expected !== actual) {
      return null;
    }
  }
  return parameters;
}

/**
 * Removes the prefix added by Supabase in front of the path of a function (`/functions/v1/api`, `/api`), so that
 * the same routes work behind `https://api.jauge.io/` (Cloudflare Worker) and on the Supabase URL.
 *
 * @param pathname path of the request.
 */
export function stripFunctionPrefix(pathname: string): string {
  let path = pathname;
  if (path.startsWith("/functions/v1/")) path = path.slice("/functions/v1".length);
  if (path === "/api" || path.startsWith("/api/")) path = path.slice("/api".length);
  if (path === "") path = "/";
  if (path.length > 1 && path.endsWith("/")) path = path.slice(0, -1);
  return path;
}
