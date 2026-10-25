import { ApiError, validationFailed } from "../http/api_error.ts";
import {
  type JsonObject,
  optionalText,
  readLimitedBytes,
  rejectUnknownFields,
  requiredChoice,
  requiredText,
} from "../http/validation.ts";
import type { AnalysisInput } from "../services/analysis_service.ts";
import { parseAreaObject, parseAreaParts } from "../services/area.ts";

/** Largest image accepted (contract: 10 MB). */
export const MAXIMUM_IMAGE_BYTES = 10 * 1024 * 1024;
/** Largest offer text read on the phone. */
export const MAXIMUM_OFFER_TEXT_LENGTH = 20_000;

const TEXT_RULES = { maximumLength: 200, pattern: /^[^<>{}]+$/ };

/** Detects the type of an image from its first bytes (the declared type is not trusted). */
export function detectImageType(bytes: Uint8Array): string | null {
  if (bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return "image/jpeg";
  if (bytes.length >= 8 && bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47) {
    return "image/png";
  }
  if (bytes.length >= 12) {
    const box = new TextDecoder().decode(bytes.slice(4, 12));
    if (/^ftyp(heic|heix|hevc|heim|heis|mif1|msf1)/.test(box)) return "image/heic";
    if (
      new TextDecoder().decode(bytes.slice(0, 4)) === "RIFF" && new TextDecoder().decode(bytes.slice(8, 12)) === "WEBP"
    ) {
      return "image/webp";
    }
  }
  return null;
}

/** Reads the JSON body of `POST /v1/analyses` (link, manual entry, or text read on the phone). */
export function parseJsonAnalysisRequest(body: JsonObject): AnalysisInput {
  const type = requiredChoice(body.type, "type", ["link", "manual", "text"] as const);
  if (type === "link") {
    rejectUnknownFields(body, ["type", "url", "area"]);
    if (typeof body.url !== "string" || body.url.trim() === "") throw new ApiError("invalid_link", "url is required");
    if (body.url.length > 2048) throw new ApiError("invalid_link", "url is too long");
    return { kind: "link", url: body.url.trim(), area: parseAreaObject(body.area) };
  }
  if (type === "manual") {
    rejectUnknownFields(body, ["type", "jobTitle", "city", "area"]);
    return {
      kind: "manual",
      jobTitle: requiredText(body.jobTitle, "jobTitle", { ...TEXT_RULES, minimumLength: 2 }),
      city: requiredText(body.city, "city", { maximumLength: 120, pattern: /^[\p{L}\p{M}0-9 '’.()-]+$/u }),
      area: parseAreaObject(body.area),
    };
  }
  rejectUnknownFields(body, ["type", "text", "origin", "area"]);
  if (typeof body.text !== "string") throw validationFailed("text must be a string", 422);
  if (body.text.length > MAXIMUM_OFFER_TEXT_LENGTH) throw validationFailed("text is too long", 422);
  const origin = body.origin === undefined || body.origin === null
    ? null
    : requiredChoice(body.origin, "origin", ["screenshot", "photo"] as const);
  // deno-lint-ignore no-control-regex
  const text = body.text.replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, " ");
  return { kind: "text", text, origin, area: parseAreaObject(body.area) };
}

/** Reads the multipart body of `POST /v1/analyses` (image of a screenshot or a photo). */
export async function parseMultipartAnalysisRequest(request: Request): Promise<AnalysisInput> {
  const tooLarge = () => new ApiError("image_too_large", "Image over 10 MB");
  const bytes = await readLimitedBytes(request, MAXIMUM_IMAGE_BYTES + 64 * 1024, tooLarge);
  let form: FormData;
  try {
    form = await new Response(bytes, { headers: { "content-type": request.headers.get("content-type") ?? "" } })
      .formData();
  } catch {
    throw validationFailed("Multipart body is not valid");
  }
  for (const name of form.keys()) {
    if (!["image", "origin", "areaCity", "areaDepartment", "areaRadius"].includes(name)) {
      throw validationFailed(`Unknown part ${name}`, 422);
    }
  }
  const image = form.get("image");
  if (!(image instanceof File)) throw validationFailed("image is required", 422);
  if (image.size > MAXIMUM_IMAGE_BYTES) throw tooLarge();
  const content = new Uint8Array(await image.arrayBuffer());
  const mimeType = detectImageType(content);
  if (!mimeType) throw new ApiError("unreadable_offer", "The file is not a JPEG, PNG, HEIC or WebP image");
  const origin = requiredChoice(form.get("origin"), "origin", ["screenshot", "photo"] as const);
  const textPart = (name: string) => {
    const value = form.get(name);
    return typeof value === "string" ? value : null;
  };
  return {
    kind: "image",
    image: content,
    mimeType,
    origin,
    area: parseAreaParts(textPart("areaCity"), textPart("areaDepartment"), textPart("areaRadius")),
  };
}

/** Reads the `Idempotency-Key` header (8 to 64 letters, digits, `-` or `_`), null when absent. */
export function idempotencyKey(request: Request): string | null {
  const value = optionalText(request.headers.get("idempotency-key"), "Idempotency-Key", { maximumLength: 64 });
  if (value === null) return null;
  if (!/^[A-Za-z0-9_-]{8,64}$/.test(value)) throw validationFailed("Idempotency-Key is badly formed", 400);
  return value;
}
