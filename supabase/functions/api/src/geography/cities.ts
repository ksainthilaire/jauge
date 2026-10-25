import { normalizeSearchText } from "../text/search_text.ts";
import { DEPARTMENT_NAMES, REFERENCE_CITIES, type ReferenceCity } from "./reference_cities.ts";

export type { ReferenceCity } from "./reference_cities.ts";

const EARTH_RADIUS_KILOMETERS = 6371;

const CITIES_BY_KEY = new Map<string, ReferenceCity>(REFERENCE_CITIES.map((city) => [cityKey(city.name), city]));

/** Folded name of a city without district, postcode or "Cedex" ("Lyon 3e" and "69003 Lyon" give "lyon"). */
export function cityKey(name: string): string {
  return normalizeSearchText(name)
    .replace(/\b\d{5}\b/g, " ")
    .replace(/\bcedex\b.*$/, " ")
    .replace(/\b\d{1,2}\s*(e|er|eme|ème)\b/g, " ")
    .replace(/\(.*?\)/g, " ")
    .replace(/[-']/g, " ")
    .replace(/\bsaint\b/g, "st")
    .replace(/\bsainte\b/g, "ste")
    .replace(/\s+/g, " ")
    .trim();
}

/**
 * Finds a reference city by name, optionally checking its department. Unknown cities give null (the analysis
 * then works at the department level only).
 *
 * @param name city as written by the user or in an offer.
 * @param departmentCode department, when known.
 */
export function findCity(name: string | null | undefined, departmentCode?: string | null): ReferenceCity | null {
  if (!name) return null;
  const key = cityKey(name);
  const city = CITIES_BY_KEY.get(key) ?? CITIES_BY_KEY.get(key.split(" ")[0] ?? "");
  if (!city) return null;
  if (departmentCode && city.departmentCode !== departmentCode) return null;
  return city;
}

/** Name of a department ("69" gives "Rhône"), or null. */
export function departmentName(code: string | null | undefined): string | null {
  return code ? DEPARTMENT_NAMES[code] ?? null : null;
}

/** Great circle distance between two points, in kilometers. */
export function distanceKilometers(
  from: { latitude: number; longitude: number },
  to: { latitude: number; longitude: number },
): number {
  const radians = (degrees: number) => (degrees * Math.PI) / 180;
  const latitudeDelta = radians(to.latitude - from.latitude);
  const longitudeDelta = radians(to.longitude - from.longitude);
  const haversine = Math.sin(latitudeDelta / 2) ** 2 +
    Math.cos(radians(from.latitude)) * Math.cos(radians(to.latitude)) * Math.sin(longitudeDelta / 2) ** 2;
  return 2 * EARTH_RADIUS_KILOMETERS * Math.asin(Math.min(1, Math.sqrt(haversine)));
}
