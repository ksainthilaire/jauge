import { assert, assertEquals } from "@std/assert";
import {
  containsLink,
  containsPersonalContact,
  LONG_DASH_CHARACTERS,
  numbersIn,
  removeLongDashes,
  textProblem,
  unexpectedWeekdays,
} from "./output_guards.ts";

Deno.test("numbers are read the way people write them", () => {
  assertEquals(numbersIn("36 000 € ou 36.000, 2,5 jours, 70 %"), ["36000", "36000", "2.5", "70"]);
});

Deno.test("a text with a number that is not in the facts is refused", () => {
  const allowed = new Set(["70", "5", "40"]);
  assertEquals(textProblem("Postule avant jeudi : 70 % arrivent en 5 jours.", allowed), null);
  assert(textProblem("Demande 45 k€.", allowed)?.includes("45"));
});

Deno.test("links, usernames, emails and phone numbers are refused", () => {
  assert(containsLink("Voir https://evil.example pour postuler"));
  assert(containsLink("va sur jobs-faciles.com"));
  assert(!containsLink("Google Ads et GA4"));
  assert(containsPersonalContact("selon @invented_handle"));
  assert(containsPersonalContact("dixit u/invented_user"));
  assert(containsPersonalContact("écris à rh@example.com"));
  assert(containsPersonalContact("appelle le 06 12 34 56 78"));
  assert(!containsPersonalContact("Atelier Sorel, Lyon 3e"));
});

Deno.test("only the computed weekday may be named", () => {
  assertEquals(unexpectedWeekdays("Postule avant jeudi.", "THURSDAY"), []);
  assertEquals(unexpectedWeekdays("Postule avant lundi.", "THURSDAY"), ["MONDAY"]);
  assertEquals(unexpectedWeekdays("Apply before Friday.", null), ["FRIDAY"]);
});

Deno.test("long dashes are replaced by commas", () => {
  const text = `Postule vite ${LONG_DASH_CHARACTERS[0]} l'offre est bondée ${LONG_DASH_CHARACTERS[1]} vraiment.`;
  assertEquals(removeLongDashes(text), "Postule vite, l'offre est bondée, vraiment.");
});
