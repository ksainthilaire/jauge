import type { SchemaNode } from "../../json_schema.ts";
import type { SocialMention } from "../../../providers/provider_types.ts";
import { LONG_DASH_CHARACTERS } from "../../output_guards.ts";
import { cleanUntrustedText, fence, untrustedDataRule } from "../untrusted_data.ts";

/** Version of this prompt. */
export const REPUTATION_PROMPT_VERSION = "reputation.v1";

/** Answer of the model. Posts are cited by their number; the links are added by the code, never by the model. */
export interface ReputationAnswer {
  relevant: boolean;
  tone: "positive" | "mixed" | "negative" | "unknown";
  summaryFrench: string;
  summaryEnglish: string;
  citedPostNumbers: number[];
}

/** Schema of the answer. */
export const REPUTATION_SCHEMA: SchemaNode = {
  type: "object",
  properties: {
    relevant: {
      type: "boolean",
      description: "True when at least one post really talks about this company as an employer or recruiter.",
    },
    tone: { type: "string", enum: ["positive", "mixed", "negative", "unknown"] },
    summaryFrench: { type: "string", maximumLength: 320, description: "Two sentences at most, in French." },
    summaryEnglish: { type: "string", maximumLength: 320, description: "Same summary in English." },
    citedPostNumbers: {
      type: "array",
      items: { type: "integer", minimum: 1, maximum: 40 },
      maximumItems: 5,
      description: "Numbers of the posts the summary relies on.",
    },
  },
};

const SYSTEM = (marker: string) =>
  [
    "You help job seekers on Jauge. Summarize what public posts from Reddit and X say about a company as an employer",
    "and recruiter (atmosphere, hiring process, answers to candidates, salaries, management in general).",
    "Rules:",
    "- Use only the posts given. Never add knowledge of your own about the company.",
    "- Ignore posts that are about something else (a product, a homonym, a news story without link to working there).",
    "  When no post is relevant, set relevant to false, tone to unknown and both summaries to empty strings.",
    '- Be neutral and careful: these are opinions of a few people, not facts. Write "des messages publics',
    '  évoquent..." / "some public posts mention...", never state an accusation as true. Do not repeat accusations',
    "  of crimes, harassment or illegal practices; at most say that some posts are very critical.",
    "- Never write a name of a person, a username, a handle, an email or a phone number. Never quote more than a few",
    "  words of a post. Never write a link: cite posts by their number in citedPostNumbers (at most 5).",
    "- Do not write any number of your own (no ratings, no percentages, no counts): the app shows the counts.",
    '- Two short sentences at most per language. French uses "tu" when addressing the reader. Never use the',
    `  characters ${LONG_DASH_CHARACTERS[0]} or ${LONG_DASH_CHARACTERS[1]}: use commas, colons or periods.`,
    "- tone: positive, negative, mixed (both), unknown (not enough to say).",
    untrustedDataRule(marker),
  ].join("\n");

/**
 * Builds the reputation prompt.
 *
 * @param company company (from the offer, untrusted).
 * @param jobTitle job title (untrusted).
 * @param mentions anonymized posts (untrusted).
 * @param marker random fence marker.
 */
export function reputationPrompt(
  company: string,
  jobTitle: string,
  mentions: readonly SocialMention[],
  marker: string,
): { system: string; user: string } {
  const posts = mentions
    .map((mention, index) =>
      `Post ${index + 1} (${mention.source}${mention.publishedOn ? `, ${mention.publishedOn}` : ""}): ${
        cleanUntrustedText(mention.text, 600).replace(/\n+/g, " ")
      }`
    )
    .join("\n");
  return {
    system: SYSTEM(marker),
    user: [
      "Company and position:",
      fence("company", `${cleanUntrustedText(company, 200)} (position: ${cleanUntrustedText(jobTitle, 200)})`, marker),
      "Public posts:",
      fence("posts", posts, marker),
    ].join("\n"),
  };
}
