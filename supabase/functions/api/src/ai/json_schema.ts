/**
 * Small JSON schema used for the structured outputs of the AI. One description serves three purposes: the schema
 * sent to Gemini (OpenAPI subset, `nullable`), the schema sent to Mistral (JSON Schema, `strict`), and the
 * validation of every answer before it is used (a model can still answer something else).
 */

/** A schema node. Every object is closed: unknown properties are refused. */
export type SchemaNode =
  | {
    readonly type: "string";
    readonly nullable?: boolean;
    readonly maximumLength?: number;
    readonly enum?: readonly string[];
    readonly description?: string;
  }
  | {
    readonly type: "integer";
    readonly nullable?: boolean;
    readonly minimum?: number;
    readonly maximum?: number;
    readonly description?: string;
  }
  | { readonly type: "boolean"; readonly nullable?: boolean; readonly description?: string }
  | {
    readonly type: "array";
    readonly nullable?: boolean;
    readonly items: SchemaNode;
    readonly maximumItems?: number;
    readonly description?: string;
  }
  | {
    readonly type: "object";
    readonly nullable?: boolean;
    readonly properties: Readonly<Record<string, SchemaNode>>;
    readonly description?: string;
  };

/** Result of a validation: the value, or the first problem found (path and reason, for the logs and the retry). */
export type ValidationResult<T> = { readonly valid: true; readonly value: T } | {
  readonly valid: false;
  readonly problem: string;
};

/**
 * Checks a parsed JSON value against a schema. Every property of an object is required (it may be null when the
 * schema says so), extra properties are refused, strings longer than their maximum are refused.
 *
 * @param schema expected shape.
 * @param value parsed JSON.
 */
export function validateAgainstSchema<T>(schema: SchemaNode, value: unknown): ValidationResult<T> {
  const problem = findProblem(schema, value, "$");
  return problem === null ? { valid: true, value: value as T } : { valid: false, problem };
}

function findProblem(schema: SchemaNode, value: unknown, path: string): string | null {
  if (value === null) return schema.nullable ? null : `${path}: null is not allowed`;
  switch (schema.type) {
    case "string":
      if (typeof value !== "string") return `${path}: string expected`;
      if (schema.maximumLength !== undefined && value.length > schema.maximumLength) return `${path}: too long`;
      if (schema.enum && !schema.enum.includes(value)) return `${path}: unexpected value`;
      return null;
    case "integer":
      if (typeof value !== "number" || !Number.isInteger(value)) return `${path}: integer expected`;
      if (schema.minimum !== undefined && value < schema.minimum) return `${path}: below ${schema.minimum}`;
      if (schema.maximum !== undefined && value > schema.maximum) return `${path}: above ${schema.maximum}`;
      return null;
    case "boolean":
      return typeof value === "boolean" ? null : `${path}: boolean expected`;
    case "array": {
      if (!Array.isArray(value)) return `${path}: array expected`;
      if (schema.maximumItems !== undefined && value.length > schema.maximumItems) return `${path}: too many items`;
      for (let index = 0; index < value.length; index++) {
        const problem = findProblem(schema.items, value[index], `${path}[${index}]`);
        if (problem) return problem;
      }
      return null;
    }
    case "object": {
      if (typeof value !== "object" || Array.isArray(value)) return `${path}: object expected`;
      const record = value as Record<string, unknown>;
      for (const key of Object.keys(record)) {
        if (!(key in schema.properties)) return `${path}.${key}: unknown property`;
      }
      for (const [key, child] of Object.entries(schema.properties)) {
        if (!(key in record)) return `${path}.${key}: missing`;
        const problem = findProblem(child, record[key], `${path}.${key}`);
        if (problem) return problem;
      }
      return null;
    }
  }
}

/**
 * Schema in the OpenAPI subset of Gemini (`responseSchema`): `nullable`, `enum`, `maxItems`, `propertyOrdering`.
 *
 * @param schema schema.
 */
export function toGeminiSchema(schema: SchemaNode): Record<string, unknown> {
  const base: Record<string, unknown> = { type: schema.type.toUpperCase() };
  if (schema.nullable) base.nullable = true;
  if (schema.description) base.description = schema.description;
  switch (schema.type) {
    case "string":
      if (schema.enum) {
        base.format = "enum";
        base.enum = [...schema.enum];
      }
      return base;
    case "integer":
      if (schema.minimum !== undefined) base.minimum = schema.minimum;
      if (schema.maximum !== undefined) base.maximum = schema.maximum;
      return base;
    case "boolean":
      return base;
    case "array":
      base.items = toGeminiSchema(schema.items);
      if (schema.maximumItems !== undefined) base.maxItems = schema.maximumItems;
      return base;
    case "object": {
      const keys = Object.keys(schema.properties);
      base.properties = Object.fromEntries(keys.map((key) => [key, toGeminiSchema(schema.properties[key])]));
      base.required = keys;
      base.propertyOrdering = keys;
      return base;
    }
  }
}

/**
 * Standard JSON Schema (Mistral `response_format.json_schema` with `strict: true`): nullable values are written as
 * a type list, every object is closed and lists all its properties as required. Bounds (lengths, minimum, maximum,
 * number of items) are left to the validation of the answer: strict modes accept only a subset of the keywords.
 *
 * @param schema schema.
 */
export function toJsonSchema(schema: SchemaNode): Record<string, unknown> {
  const type = schema.nullable ? [schema.type, "null"] : schema.type;
  const base: Record<string, unknown> = { type };
  if (schema.description) base.description = schema.description;
  switch (schema.type) {
    case "string":
      if (schema.enum) base.enum = schema.nullable ? [...schema.enum, null] : [...schema.enum];
      return base;
    case "integer":
    case "boolean":
      return base;
    case "array":
      base.items = toJsonSchema(schema.items);
      return base;
    case "object": {
      const keys = Object.keys(schema.properties);
      base.properties = Object.fromEntries(keys.map((key) => [key, toJsonSchema(schema.properties[key])]));
      base.required = keys;
      base.additionalProperties = false;
      return base;
    }
  }
}
