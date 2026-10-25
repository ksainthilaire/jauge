import { assert } from "@std/assert";
import { parse } from "@std/yaml";
import { createTestApplication } from "./testing/test_application.ts";

/** Every operation of docs/openapi.yaml is served by the router (no 404 route, no 405). */
Deno.test("the router implements every operation of docs/openapi.yaml", async () => {
  const contract = parse(await Deno.readTextFile(new URL("../../../../docs/openapi.yaml", import.meta.url))) as {
    paths: Record<string, Record<string, unknown>>;
  };
  const api = await createTestApplication();
  try {
    let operations = 0;
    for (const [path, item] of Object.entries(contract.paths)) {
      for (const method of ["get", "post", "put", "patch", "delete"]) {
        if (!(method in item)) continue;
        operations += 1;
        const concrete = path.replace(/\{[^}]+\}/g, crypto.randomUUID());
        const response = await api.request(method.toUpperCase(), concrete);
        const message = (response.body.error as { message?: string } | undefined)?.message ?? "";
        assert(
          response.status !== 405 && message !== "Route not found",
          `${method.toUpperCase()} ${path} is not routed`,
        );
      }
    }
    assert(operations >= 25, `only ${operations} operations in the contract`);
  } finally {
    await api.close();
  }
});
