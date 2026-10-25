import { assert, assertEquals } from "@std/assert";
import { matchesSearch, normalizeSearchText, searchWords } from "./search_text.ts";

Deno.test("search folds accents, ligatures and inclusive writing like the app", () => {
  assertEquals(normalizeSearchText("  Chargé·e   de Communication "), "chargee de communication");
  assertEquals(normalizeSearchText("Écully"), "ecully");
  assertEquals(normalizeSearchText("Cœur de métier"), "coeur de metier");
  assert(matchesSearch(searchWords("chargee lyon"), "Chargé·e de marketing", "Atelier Sorel", "Lyon 3e"));
  assert(!matchesSearch(searchWords("chargee paris"), "Chargé·e de marketing", null, "Lyon 3e"));
  assert(matchesSearch(searchWords("   "), "anything"));
});
