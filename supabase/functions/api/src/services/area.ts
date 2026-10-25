import { validationFailed } from "../http/api_error.ts";
import { DEPARTMENT_CODE_PATTERN, isObject, optionalText, rejectUnknownFields } from "../http/validation.ts";
import { findCity, type ReferenceCity } from "../geography/cities.ts";

/** Search area of the user: a city, its department and a radius. Never a position. */
export interface SearchArea {
  readonly city: string;
  readonly departmentCode: string | null;
  readonly radiusKilometers: number;
}

/** Radius used when none is given. */
export const DEFAULT_RADIUS_KILOMETERS = 30;

const CITY_RULES = { maximumLength: 120, pattern: /^[\p{L}\p{M}0-9 '’.()-]+$/u };

function parseDepartment(value: unknown): string | null {
  const department = optionalText(value, "departmentCode", { maximumLength: 3 });
  if (department === null) return null;
  const upper = department.toUpperCase();
  if (!DEPARTMENT_CODE_PATTERN.test(upper)) throw validationFailed("departmentCode is badly formed", 422);
  return upper;
}

function parseRadius(value: unknown): number {
  if (value === undefined || value === null || value === "") return DEFAULT_RADIUS_KILOMETERS;
  const radius = typeof value === "string" && /^\d{1,3}$/.test(value) ? Number(value) : value;
  if (typeof radius !== "number" || !Number.isInteger(radius) || radius < 1 || radius > 200) {
    throw validationFailed("radius must be an integer from 1 to 200", 422);
  }
  return radius;
}

/** Reads the `area` object of a JSON body (null when absent). */
export function parseAreaObject(value: unknown): SearchArea | null {
  if (value === undefined || value === null) return null;
  if (!isObject(value)) throw validationFailed("area must be an object", 422);
  rejectUnknownFields(value, ["city", "departmentCode", "radiusKilometers"], "area");
  const city = optionalText(value.city, "area.city", CITY_RULES);
  if (city === null) throw validationFailed("area.city is required", 422);
  return {
    city,
    departmentCode: parseDepartment(value.departmentCode),
    radiusKilometers: parseRadius(value.radiusKilometers),
  };
}

/**
 * Reads an area from separate values (query parameters `city`, `department`, `radius`, or multipart parts
 * `areaCity`, `areaDepartment`, `areaRadius`). Without a city there is no area.
 */
export function parseAreaParts(city: unknown, department: unknown, radius: unknown): SearchArea | null {
  const cityName = optionalText(city, "city", CITY_RULES);
  if (cityName === null) {
    if (radius !== undefined && radius !== null && radius !== "") parseRadius(radius);
    return null;
  }
  return { city: cityName, departmentCode: parseDepartment(department), radiusKilometers: parseRadius(radius) };
}

/** Reference city at the center of an area, or null when the city is unknown. */
export function areaCenter(area: { city: string | null; departmentCode: string | null } | null): ReferenceCity | null {
  if (!area?.city) return null;
  return findCity(area.city, area.departmentCode) ?? findCity(area.city);
}
