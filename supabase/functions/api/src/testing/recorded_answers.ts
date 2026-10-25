/**
 * Answers recorded from the real APIs (shapes of Gemini `generateContent`, Mistral chat completions and Apify
 * dataset items), replayed by the tests: no test ever calls the network. Personal data of the recordings has been
 * replaced by invented values.
 */

import type { ModelClient, ModelPricing, ModelReply, ModelRequest } from "../ai/model_client.ts";
import type { AdviceFacts } from "../providers/provider_types.ts";

/** Text of an offer as read on a phone (OCR). */
export const OFFER_TEXT = `Chargé·e de marketing digital (H/F)
Atelier Sorel · Lyon 3e (69) · Hybride
CDI · 36 000 € - 41 000 € brut annuel
Publiée il y a 2 jours · 52 candidats
Missions : campagnes Google Ads et Meta Ads, suivi GA4, newsletters.
Profil : 2 ans d'expérience, Looker Studio apprécié.`;

/** Gemini `generateContent` answer for `OFFER_TEXT`. */
export const GEMINI_EXTRACTION_ANSWER = {
  candidates: [{
    content: {
      role: "model",
      parts: [{
        text: JSON.stringify({
          isJobOffer: true,
          jobTitle: "Chargé·e de marketing digital",
          company: "Atelier Sorel",
          city: "Lyon 3e",
          departmentCode: "69",
          contractType: "permanent",
          workMode: "hybrid",
          publishedOn: null,
          publishedDaysAgo: 2,
          salaryMinimumEuros: 36000,
          salaryMaximumEuros: 41000,
          salaryPeriod: "year",
          skills: ["Google Ads", "Meta Ads", "GA4", "Looker Studio"],
          displayedApplicantCount: 52,
        }),
      }],
    },
    finishReason: "STOP",
  }],
  usageMetadata: { promptTokenCount: 1012, candidatesTokenCount: 148, thoughtsTokenCount: 0, totalTokenCount: 1160 },
  modelVersion: "gemini-3.1-flash-lite",
};

/** Mistral chat completion answer for `OFFER_TEXT`. */
export const MISTRAL_EXTRACTION_ANSWER = {
  id: "cmpl-recorded",
  object: "chat.completion",
  model: "mistral-small-2603",
  choices: [{
    index: 0,
    message: { role: "assistant", content: GEMINI_EXTRACTION_ANSWER.candidates[0].content.parts[0].text },
    finish_reason: "stop",
  }],
  usage: { prompt_tokens: 1088, completion_tokens: 151, total_tokens: 1239 },
};

/** Facts of the mockup offer (Lyon, 160 to 260 applicants, Thursday). */
export const MOCKUP_FACTS: AdviceFacts = {
  language: "fr",
  offer: {
    jobTitle: "Chargé·e de marketing digital",
    company: "Atelier Sorel",
    city: "Lyon 3e",
    contractType: "permanent",
    workMode: "Hybride",
    publishedDaysAgo: 2,
    displayedSalary: null,
    skills: ["Google Ads", "GA4", "Meta Ads"],
  },
  score: 78,
  scoreLevel: "crowded",
  applicants: { estimate: 210, low: 160, high: 260 },
  reliability: "medium",
  percentileInRegion: 84,
  regionName: "Rhône",
  comparableOfferCount: 412,
  estimatedSalary: { minimum: 36, maximum: 41 },
  salaryMedian: 37,
  negotiationTarget: 40,
  trendThreeMonthsPercent: 18,
  twinOfferCount: 46,
  twinRadiusKilometers: 30,
  timingAdvice: { applyBefore: "THURSDAY", sharePercent: 70, withinDays: 5 },
  marketSkills: [
    { name: "Google Ads", sharePercent: 82 },
    { name: "GA4", sharePercent: 74 },
    { name: "Meta Ads", sharePercent: 69 },
    { name: "Looker Studio", sharePercent: 41 },
    { name: "Brevo / emailing", sharePercent: 37 },
  ],
  missingSkillCandidates: ["Looker Studio", "Brevo / emailing"],
  candidateFeedback:
    "Des messages publics évoquent des réponses aux candidats parfois lentes, souvent après 11 semaines.",
};

/** A good advice for `MOCKUP_FACTS`. */
export const GOOD_ADVICE = {
  advice: "Postule avant jeudi : environ 70 % des candidatures arrivent en 5 jours, et l'offre est déjà bondée " +
    "avec 160 à 260 candidats estimés. Regarde aussi les 46 offres jumelles plus calmes à moins de 30 km.",
  negotiationArgument: "Le salaire probable de 36 à 41 k€ dépasse la médiane de 37 k€ : demande 40 k€.",
  missingSkills: ["Looker Studio", "Brevo / emailing"],
};

/** Apify dataset items of `trudax/reddit-scraper-lite` (search "Atelier Sorel"). */
export const REDDIT_ITEMS = [
  {
    id: "t3_1abcd2",
    dataType: "post",
    url: "https://www.reddit.com/r/lyon/comments/1abcd2/atelier_sorel_entretien/",
    username: "invented_user_1",
    title: "Entretien chez Atelier Sorel",
    body: "Process rapide chez Atelier Sorel, réponse en une semaine. Contactez u/invented_user_2 ou moi@example.com",
    createdAt: "2026-09-12T10:00:00.000Z",
  },
  {
    id: "t1_kx1",
    dataType: "comment",
    url: "https://www.reddit.com/r/lyon/comments/1abcd2/atelier_sorel_entretien/kx1/",
    username: "invented_user_3",
    body: "J'y ai bossé, bonne ambiance à l'Atelier Sorel mais peu de télétravail.",
    createdAt: "2026-09-13T08:00:00.000Z",
  },
  {
    id: "t3_other",
    dataType: "post",
    url: "https://www.reddit.com/r/france/comments/9zz/autre_sujet/",
    username: "invented_user_4",
    title: "Rien à voir",
    body: "Un post qui ne parle pas de l'entreprise.",
    createdAt: "2026-08-01T08:00:00.000Z",
  },
  { dataType: "user", url: "https://www.reddit.com/user/invented_user_5/" },
];

/** Apify dataset items of the X actor (search "Atelier Sorel"), with a mock item returned by the actor. */
export const X_ITEMS = [
  {
    type: "tweet",
    id: "1845000000000000001",
    url: "https://x.com/invented_handle/status/1845000000000000001",
    text: "@invented_friend candidature envoyée à Atelier Sorel, réponse super rapide 👍 appelez le 06 12 34 56 78",
    createdAt: "Tue Oct 14 09:00:00 +0000 2026",
    author: { userName: "invented_handle", name: "Invented Person" },
  },
  { type: "mock_tweet", text: "Atelier Sorel mock", url: "https://x.com/mock/status/1" },
];

/**
 * Model client that replays recorded replies in order and keeps the requests it received.
 */
export class ReplayModelClient implements ModelClient {
  readonly name = "gemini";
  readonly model = "gemini-3.1-flash-lite";
  readonly pricing: ModelPricing = { inputUsdPerMillionTokens: 0.25, outputUsdPerMillionTokens: 1.5 };
  /** Requests received, in order. */
  readonly requests: ModelRequest[] = [];

  /** @param replies replies to give, in order (an Error is thrown instead of answered). */
  constructor(private readonly replies: (ModelReply | Error)[]) {}

  generate(request: ModelRequest): Promise<ModelReply> {
    this.requests.push(request);
    const reply = this.replies.shift();
    if (!reply) return Promise.reject(new Error("no recorded reply left"));
    if (reply instanceof Error) return Promise.reject(reply);
    return Promise.resolve(reply);
  }
}

/**
 * A recorded reply of the model.
 *
 * @param value JSON value of the answer, or raw text.
 * @param inputTokens input tokens.
 * @param outputTokens output tokens.
 */
export function reply(value: unknown, inputTokens = 1000, outputTokens = 200): ModelReply {
  return {
    text: typeof value === "string" ? value : JSON.stringify(value),
    usage: { model: "gemini-3.1-flash-lite", inputTokens, outputTokens },
    truncated: false,
  };
}

/** Fetch that answers from a list of recorded responses and keeps the requests. */
export function recordedFetch(answers: (Response | Error)[]): {
  fetcher: (input: string, init?: RequestInit) => Promise<Response>;
  calls: { url: string; init: RequestInit | undefined }[];
} {
  const calls: { url: string; init: RequestInit | undefined }[] = [];
  return {
    calls,
    fetcher: (input, init) => {
      calls.push({ url: input, init });
      const answer = answers.shift();
      if (!answer) return Promise.reject(new Error("no recorded answer left"));
      if (answer instanceof Error) return Promise.reject(answer);
      return Promise.resolve(answer);
    },
  };
}

/** JSON response. */
export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}
