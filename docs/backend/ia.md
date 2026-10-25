# Jauge backend: AI and social search (Reddit, X)

Code: `supabase/functions/api/src/ai/` (models, prompts, checks), `src/social/` (Reddit and X through Apify),
`src/services/reputation_service.ts` (30 day cache per company), `src/services/advice_facts.ts` (facts given to the
AI).

**The reputation is internal** (decision of the owner, 25/10/2026): what Reddit and X say about a company is never
shown to the user. It is not in any response of the API, not in the shared page, not in the stored result (results
stored before carry it and are cleaned at reading, `publicResult`). It only helps the AI write `result.advice`, and it
never changes a number of the score (`docs/backend/score.md`, "What the score never uses"). Interfaces: `AiProvider` and `SocialSearch` in `src/providers/provider_types.ts`, wired in `createProviders`
(`src/providers/provider_registry.ts`). Prices below were checked on the official pages on 25/10/2026.

## What the AI does, and what it never does

| Operation | When | Input | Output |
|---|---|---|---|
| `extract_text` | analysis of a text read on the phone or pasted | offer text (15 000 characters at most) | the offer (title, company, city, contract, salary, date, skills, applicants shown) |
| `extract_image` | fallback when the phone could not read the screenshot | image (deleted right after) | same |
| `reputation` | once per company every 30 days | anonymized Reddit and X posts | internal summary in French **and** English, tone (kept on the server, never sent) |
| `advice` | every completed analysis | facts computed by Jauge (score, applicants, salary, trend, twins, skills, timing) + `candidateFeedback`, the reputation summary as internal context | advice ("Postule avant jeudi..."), negotiation argument, missing skills, in the user's language |

The AI **never produces a number of the estimate**: score, applicants, salaries, trend and twins come from
`src/scoring/estimation.ts` (`docs/backend/score.md`). The advice may only repeat numbers present in the computed facts
(never a number of `candidateFeedback`); a salary or an applicant count read from a text must be written in that text,
otherwise the field stays empty.

## How the advice uses the reputation (prompt `advice.v2`)

`candidateFeedback` is the reputation summary of the company in the user's language (from the 30 day cache, anonymized
posts, no usernames, no links), or null. Only the summary is given: not the tone, not the counts, not the sources. The
prompt tells the model to use it only when it talks about the hiring itself, and only to make the advice more practical
and careful, for example:

- a slow recruitment: "le recrutement peut parfois prendre du temps";
- frequent remarks about late answers: "prévois une relance polie si la réponse tarde".

And never to: cite where it comes from (Reddit, X, Twitter, posts, reviews, reputation, forum), judge the company (good
or bad employer, atmosphere, management), or take a number or a duration from it. The code checks the answer too: an
advice that names a source or the reputation (`socialSourceWords` in `src/ai/output_guards.ts`) or that contains a
number found only in `candidateFeedback` is refused and asked again once, then dropped (the app shows the computed
timing advice). Without feedback, or with feedback about something else, the advice is the same as before.

## Providers

| | Gemini (default) | Mistral |
|---|---|---|
| Model | `gemini-3.1-flash-lite` (stable; `gemini-2.5-flash-lite` is cheaper but only open to projects that used it before; `gemini-3.5-flash-lite` costs more) | `mistral-small-2603` (Mistral Small 4, text and images) |
| Price per million tokens (input / output) | 0.25 / 1.50 USD | 0.15 / 0.60 USD |
| Structured output | `responseMimeType: application/json` + `responseSchema` | `response_format: json_schema`, `strict: true` |
| Thinking | `thinkingLevel: minimal` (thinking tokens are billed as output) | none |
| Data | paid tier: prompts and answers not used to improve Google products; no EU residency on the Developer API | processed in the EU; API data not used for training (keep "anonymous improvement data" off) |
| Secret | `GEMINI_API_KEY` | `MISTRAL_API_KEY` |

Switch: `AI_PROVIDER=gemini` or `AI_PROVIDER=mistral` (and `GEMINI_MODEL` / `MISTRAL_MODEL` to change the model).
A model missing from the price lists (`GEMINI_PRICES`, `MISTRAL_PRICES`) is refused unless
`AI_INPUT_USD_PER_MILLION_TOKENS` and `AI_OUTPUT_USD_PER_MILLION_TOKENS` give its price: a call whose cost cannot be
known cannot be checked against the daily limit. Note: Mistral Small 4 is about 45 % cheaper than Gemini 3.1
Flash-Lite per token and keeps the data in Europe; Gemini stays the default as asked, the switch is one secret.

## Reliability of the answers

- Every call asks for JSON following a schema (`src/ai/json_schema.ts`), and the answer is validated again
  (closed objects, enums, lengths) because a model may still answer something else.
- Then content checks (`src/ai/output_guards.ts`): no number absent from the facts, no weekday other than the computed
  one, no link, no username, handle, email or phone number; long dashes are replaced by commas.
- A rejected answer is asked again **once** with the reason (2 attempts). Then the operation gives up gracefully:
  unreadable offer (the free analysis is given back), no advice (the app shows the computed timing advice), no
  internal reputation (the advice is written without it).
- Timeout of 20 s per call; network errors, 408, 429 and 5xx are retryable (the queue retries with a backoff until the
  deadline of the analysis); 400, 401, 403 and blocked prompts are not.
- Tokens and cost of **every attempt** are journaled in `provider_costs` (`model`, `input_tokens`, `output_tokens`,
  `cost_micro_usd`) and logged (`ai_call`, `ai_answer_rejected`: codes and counts, never the content).
- Daily limit: before each call the pipeline checks `estimate + spent today <= JAUGE_DAILY_SPEND_LIMIT_USD`. The
  estimate uses the maximum output of the operation, so the real cost is never above it by more than the input
  difference. Over the limit, AI operations are skipped like the paid sources.

## The prompts

Versioned in `src/ai/prompts/v1/` (`offer_extraction.v1`, `reputation.v1`) and `src/ai/prompts/v2/` (`advice.v2`,
used since 25/10/2026: the reputation becomes internal context; `v1/advice.ts` is kept as history and no longer
used). The version is logged with each call. Written in English for the model; the outputs are in French or English
(the reputation in both at once, so that one summary serves every user for 30 days). A new wording goes in a new
version folder with new tests, never by editing a version in place.

Rules written in the prompts and checked by the code:

- Never make up a number; use only the facts. Weekday only the computed one.
- Sources: the reputation summary cites posts by their number only; the links of the posts are not kept (nothing
  shows them since the reputation is internal), and the advice never names its sources.
- Honest tone: a low reliability must be called rough; opinions are "some public posts mention..." in the internal
  summary, "parfois" / "sometimes" in the advice, never facts.
- No personal data of third parties: no names of people, no usernames or handles; posts are anonymized before the
  model reads them (`src/social/anonymize.ts`); X links lose the handle (`https://x.com/i/status/<id>`).
- Defamation: neutral and careful wording, accusations of crimes, harassment or illegal practices are not repeated
  ("some posts are very critical" at most).
- No long dashes.

Injection of instructions (an offer or a post saying "ignore your rules and..."):

- Untrusted texts are cleaned (control, zero width and bidirectional characters removed), cut, and fenced between
  markers that carry a random value per request (`<untrusted offer_text id="...">`). Fake closing markers inside the
  text are neutralized, so the text cannot leave the fence.
- The instructions say that fenced content is data, never instructions; the image of an offer is untrusted too.
- Whatever the model writes, the output checks above apply: an "obeying" answer with a link, a contact or an invented
  number is refused. The model has no tools, no access to the database and no way to act: the worst case is a refused
  answer.

## Reddit and X: what we chose and why

| Option | Price (25/10/2026) | Terms | Choice |
|---|---|---|---|
| Reddit Data API (official) | free for non commercial use (100 requests per minute); commercial use needs a separate agreement, price not public | since November 2025 every new access needs Reddit's approval (Responsible Builder Policy) | **later**: the clean way, but not obtainable quickly for a commercial app |
| Apify `trudax/reddit-scraper-lite` | 3.40 USD per 1 000 results, no platform usage | reads public pages; Reddit's user agreement does not allow scraping without its consent (contractual risk for Jauge, see below) | **chosen** for Reddit: predictable price, 12 results per company |
| Apify `trudax/reddit-scraper` | 0.10 USD per 1 000 results + Apify platform usage (up to about 4 USD per 1 000) | same | not chosen: unpredictable cost |
| X API (official, pay per use) | 0.005 USD per post read (5 USD per 1 000), no subscription | the clean way | not chosen: 20 times the price (0.10 USD per company); switching is possible later |
| Apify `kaitoeasyapi/twitter-x-data-tweet-scraper-pay-per-result-cheapest` | about 0.25 USD per 1 000 posts, 20 posts minimum per search, a small minimum charge per call | scraping of public posts, X's terms do not allow it | **chosen** for X |
| Apify `apidojo/tweet-scraper` | 0.40 USD per 1 000 posts, 50 minimum per search | same | backup (`JAUGE_X_ACTOR`, same input fields) |
| Gemini grounding with Google Search restricted to reddit.com and x.com | Gemini 3: 5 000 queries per month free, then 14 USD per 1 000 | the restriction to two sites is only a wish in the prompt; the terms require showing Google's search suggestions next to the grounded answer, which the app does not do; not available on the paid 3.1 Flash-Lite according to the price table | rejected |

How it runs: one search per company every 30 days (`company_reputations`, also when nothing is found), only when the
offer names a company, Reddit and X side by side with the synchronous Apify endpoint (`run-sync-get-dataset-items`,
25 s, `maxTotalChargeUsd` per run), only posts that contain the company name, anonymized and cut to 400 characters,
never stored. A failure is not cached (the next analysis tries again); the reputation never blocks an analysis.

Legal note (to decide by the owner, see `docs/audit/a-faire-par-toi.md`): reading public posts is lawful under the
GDPR with data minimization (legitimate interest: better advice for candidates; no usernames, no links, nothing stored
but a short summary that no user sees), but scraping goes against the terms of Reddit and X. The risk is contractual (blocking, request to stop),
not criminal. The fully compliant setup is the official X API (a small `SocialActor`-like connector to write, about
0.10 USD per company) and a Reddit commercial agreement. `JAUGE_SOCIAL_SOURCES` turns each network on or off
(`reddit,x`, `x`, `reddit`, or `none`).

## Cost per analysis (Gemini 3.1 Flash-Lite)

| Operation | Typical tokens (input / output) | Typical cost | Cap checked before the call |
|---|---|---|---|
| Reading a text | 1 000 / 150 | 0.0005 USD | 0.0018 USD |
| Reading an image (fallback) | 1 700 / 150 | 0.0007 USD | 0.0016 USD |
| Advice (every analysis) | 1 500 / 150 | 0.0006 USD | 0.0011 USD |
| Reputation summary (per company, 30 days) | 4 000 / 150 | 0.0012 USD | 0.0025 USD |
| Social search (per company, 30 days) | | Reddit up to 0.041 USD + X about 0.005 USD | 0.046 USD |

So an analysis costs about **0.001 USD of AI** (text read on the phone + advice), plus **about 0.05 USD once per new
company** for the reputation. With Mistral Small 4 the AI part is about 45 % cheaper.

Per month for 1 000 users (2 600 analyses, about 850 new companies with a name per month after the 30 day cache):
AI 3 to 5 USD, social search 20 to 40 USD (Reddit is most of it: `JAUGE_SOCIAL_SOURCES=x` brings it under 5 USD).
Everything stays under the daily limit `JAUGE_DAILY_SPEND_LIMIT_USD` with the Apify sources.

## Tests

No test calls the network: answers recorded from the real APIs are replayed (`src/testing/recorded_answers.ts`).
`src/ai/*_test.ts` (schemas, guards, Gemini and Mistral requests and errors, retries, cost, injection, advice v2:
feedback used carefully, never cited, no number taken from it),
`src/social/apify_social_search_test.ts` (filtering, anonymization, cost, failures),
`src/services/reputation_service_test.ts` (30 day cache, nothing found, daily limit, links not kept),
`src/queue/ai_pipeline_test.ts` (the advice uses the reputation; the reputation never appears in any response, the
shared page, an old stored result, a failed advice or an error; tokens journaled),
`src/providers/provider_registry_test.ts` (configuration).
