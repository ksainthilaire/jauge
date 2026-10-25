import { ApiError, validationFailed } from "./api_error.ts";

/** Largest JSON body accepted (the longest one is a pasted offer text of 20 000 characters). */
export const MAXIMUM_JSON_BODY_BYTES = 96 * 1024;

/** A JSON object received from a client. */
export type JsonObject = Record<string, unknown>;

/**
 * Reads the JSON body of a request: size limited, must be an object.
 *
 * @param request incoming request.
 * @param maximumBytes largest accepted body.
 */
export async function readJsonObject(request: Request, maximumBytes = MAXIMUM_JSON_BODY_BYTES): Promise<JsonObject> {
  const contentType = request.headers.get("content-type") ?? "";
  if (!contentType.toLowerCase().startsWith("application/json")) {
    throw validationFailed("Content-Type must be application/json");
  }
  const text = await readLimitedText(request, maximumBytes);
  let value: unknown;
  try {
    value = JSON.parse(text);
  } catch {
    throw validationFailed("Body is not valid JSON");
  }
  if (!isObject(value)) throw validationFailed("Body must be a JSON object");
  return value;
}

/**
 * Reads a request body as text, refusing bodies over the limit (declared or real).
 *
 * @param request incoming request.
 * @param maximumBytes largest accepted body.
 */
export async function readLimitedText(request: Request, maximumBytes: number): Promise<string> {
  const bytes = await readLimitedBytes(request, maximumBytes, () => validationFailed("Body too large", 413));
  return new TextDecoder("utf-8", { fatal: false }).decode(bytes);
}

/**
 * Reads a request body as bytes, refusing bodies over the limit.
 *
 * @param request incoming request.
 * @param maximumBytes largest accepted body.
 * @param tooLarge error thrown when the body is over the limit.
 */
export async function readLimitedBytes(
  request: Request,
  maximumBytes: number,
  tooLarge: () => ApiError,
): Promise<Uint8Array<ArrayBuffer>> {
  const declared = Number(request.headers.get("content-length") ?? "0");
  if (Number.isFinite(declared) && declared > maximumBytes) throw tooLarge();
  if (!request.body) return new Uint8Array(new ArrayBuffer(0));
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > maximumBytes) {
      await reader.cancel();
      throw tooLarge();
    }
    chunks.push(value);
  }
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return bytes;
}

/** True for a plain JSON object (not null, not an array). */
export function isObject(value: unknown): value is JsonObject {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/**
 * Refuses fields that the contract does not define (strict validation).
 *
 * @param body received object.
 * @param allowed names of the fields of the contract.
 * @param where name of the object, for the developer message.
 */
export function rejectUnknownFields(body: JsonObject, allowed: readonly string[], where = "body"): void {
  for (const key of Object.keys(body)) {
    if (!allowed.includes(key)) throw validationFailed(`Unknown field ${where}.${key}`, 422);
  }
}

/** Options of a text field. */
export interface TextRules {
  /** Smallest length after trimming. */
  readonly minimumLength?: number;
  /** Largest length after trimming. */
  readonly maximumLength: number;
  /** Pattern the trimmed value must match. */
  readonly pattern?: RegExp;
}

/**
 * Reads a required text field, trimmed. Control characters are refused.
 *
 * @param value received value.
 * @param name name of the field.
 * @param rules length and pattern.
 */
export function requiredText(value: unknown, name: string, rules: TextRules): string {
  if (typeof value !== "string") throw validationFailed(`${name} must be a string`, 422);
  const trimmed = value.trim();
  if (trimmed.length < (rules.minimumLength ?? 1)) throw validationFailed(`${name} is too short`, 422);
  if (trimmed.length > rules.maximumLength) throw validationFailed(`${name} is too long`, 422);
  // deno-lint-ignore no-control-regex
  if (/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/.test(trimmed)) {
    throw validationFailed(`${name} contains control characters`, 422);
  }
  if (rules.pattern && !rules.pattern.test(trimmed)) throw validationFailed(`${name} is badly formed`, 422);
  return trimmed;
}

/** Reads an optional text field: absent, null or blank gives null. */
export function optionalText(value: unknown, name: string, rules: TextRules): string | null {
  if (value === undefined || value === null) return null;
  if (typeof value === "string" && value.trim() === "") return null;
  return requiredText(value, name, rules);
}

/**
 * Reads a required integer within bounds.
 *
 * @param value received value.
 * @param name name of the field.
 * @param minimum smallest value.
 * @param maximum largest value.
 */
export function requiredInteger(value: unknown, name: string, minimum: number, maximum: number): number {
  if (typeof value !== "number" || !Number.isInteger(value)) throw validationFailed(`${name} must be an integer`, 422);
  if (value < minimum || value > maximum) throw validationFailed(`${name} is out of range`, 422);
  return value;
}

/** Reads an optional boolean, with its default value. */
export function optionalBoolean(value: unknown, name: string, defaultValue: boolean): boolean {
  if (value === undefined || value === null) return defaultValue;
  if (typeof value !== "boolean") throw validationFailed(`${name} must be a boolean`, 422);
  return value;
}

/** Reads a required value among a closed list. */
export function requiredChoice<Choice extends string>(
  value: unknown,
  name: string,
  choices: readonly Choice[],
): Choice {
  if (typeof value !== "string" || !(choices as readonly string[]).includes(value)) {
    throw validationFailed(`${name} must be one of ${choices.join(", ")}`, 422);
  }
  return value as Choice;
}

/** Pattern of a French department code (`69`, `2A`, `974`). */
export const DEPARTMENT_CODE_PATTERN = /^([0-9]{2}|2[AB]|9[78][0-9])$/;

/** Pattern of a canonical UUID (identifiers of analyses and alerts). */
export const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

/**
 * Reads an identifier from the path or the query. An identifier that cannot exist answers `not_found` (never a
 * database error), so that unknown and badly formed identifiers look the same.
 *
 * @param value raw identifier.
 * @param what name of the resource, for the developer message.
 */
export function resourceIdentifier(value: string | null | undefined, what: string): string {
  const lowered = (value ?? "").toLowerCase();
  if (!UUID_PATTERN.test(lowered)) throw new ApiError("not_found", `${what} not found`);
  return lowered;
}
