# How Jauge estimates (score, applicants, salary, trend, twins)

Code: `supabase/functions/api/src/scoring/estimation.ts` (pure functions, unit tests next to it). The screen
"Comment on estime" (mockup 09) says the same thing to the user in three steps: we read the offer, we compare it with
the offers of the department over the year starting from those whose site shows the number of applicants, and we
check ourselves every month against real numbers.

Jauge never sees the applications received by the employer: everything below is an estimate with an honest range.

## Inputs

- **The offer**: job title, city and department, contract, remote work, publication date, salary shown, and the number
  of applicants when the site shows it (LinkedIn does).
- **Comparable offers**: same job family (title folded: gender marks, contracts, seniority, inclusive writing and
  feminine endings removed, so "Chargé·e de marketing digital (H/F) CDI" and "Chargé de marketing digital" match), same
  department, published in the last 12 months (`collected_offers`, collected per market slice).
- **Anchors**: comparable offers whose site showed a number of applicants, with the day it was read.
- **History**: `market_statistics` (applicants per offer and salary quartiles per month), kept when old offers are
  purged.

## 1. Arrival of applications

Applications arrive fast, then slow down: share arrived after `t` days online = `1 - exp(-t / 4.2)`. About 70 % arrive
in the first 5 days, which is the timing advice of the verdict ("Postule avant jeudi : environ 70 % des candidatures
arrivent en 5 jours").

## 2. Level of the market

Each anchor gives its **final applicants** = shown count / share arrived when it was read (anchors read on the day of
publication are skipped: too noisy), divided by its own offer factor (step 3) so that anchors are comparable. The
level of the market is the **weighted median** of these values (weight 1 for anchors of the last 90 days, 0.5
before). With fewer than 3 anchors: the latest month of history, else a prior of 80 applicants.

## 3. Offer factor and market balance

Multipliers of the final applicants of an offer:

| Feature | Factor | Why |
|---|---|---|
| Salary shown | x 1.15 | offers with a salary attract more applications |
| Remote or hybrid work | x 1.15 | wider pool of candidates |
| Internship, apprenticeship | x 1.3 | many candidates per position |
| Fixed term | x 0.9 | |
| Freelance, temporary | x 0.8 | |

Market balance: when the department has more offers this month than its monthly average, each offer gets fewer
applicants: `(monthly average / offers of the last 30 days) ^ 0.3`, bounded to 0.75 .. 1.3 (only with 12 offers or
more).

## 4. Applicants today

`final = level x balance x offer factor`, `estimate = final x arrival share (days online)` (7 days when the date is
unknown). When the site shows the count, it is used as is. Rounded like people say it: to 10 above 100, to 5 above 20.

## 5. Reliability and range

Points: shown by the site +3; anchors 30 or more +2, 8 or more +1; online 3 days or more +1, date unknown -1; salary
shown +1; some sources missing today -1. **High** from 3 points (range +/- 15 %), **medium** at 2 (+/- 25 %), **low**
below (+/- 40 %). The reasons (`offre en ligne depuis 2 jours seulement`, `sans salaire affiché`...) are sent by
`GET /v1/methodology` in the language of the user, the ones that lower the level first.

Example of the mockup: many anchors (+2), online for 2 days (0), no salary (0): **medium**, 210 shown as
"160 ≈ 210 260".

## 6. Percentile and score

Every comparable offer gets its own final applicants (its anchor when it has one, the model otherwise). The
**percentile in the region** is the share of comparable offers with fewer final applicants ("plus que 84 % des offres
du Rhône"). The **score** mixes this rank with the absolute level, so that a crowded market stays crowded:

`score = round(0.6 x percentile + 0.4 x 100 x ln(1 + final) / ln(1 + 400))`, bounded to 0 .. 100.

0 to 33 calm, 34 to 66 disputed, 67 to 100 crowded.

## 7. Twin offers

Comparable offers of the same job family published in the last 45 days, within the radius of the search area (great
circle distance between city centers), with a lower score than the offer. Each one gets its own estimate (its shown
count, or the model at its own age). Sorted by score (`least_disputed`), salary (`best_paid`) or date
(`most_recent`). The twins at analysis time are kept (`snapshot_twins`): the calm alert only notifies twins that
appear later and go under the threshold.

## 8. Salary

- **Scale of the area**: middle of the range of each comparable offer that shows a salary; 10th and 90th
  percentiles for the ends of the scale, quartiles for "la moitié des offres paie dans cette zone", median. Area
  of the user when its department has data, department of the offer otherwise, history when fewer than 3 salaries.
- **Probable salary of the offer**: the one shown, otherwise the 40th to 65th percentiles of the comparable salaries
  (offers that hide their salary pay a bit under the middle). Nothing when fewer than 3 salaries: an empty field
  rather than a wrong number.
- **Negotiation target**: the third quartile, kept inside the range of the offer.

## 9. Trend

Median final applicants of the anchors published each month (2 anchors at least), history for the other months;
months without data are left out. Change over 3 months = last month compared with 3 months before.

## 10. Skills

Share of the comparable offers (with skills) that ask for each skill (spelling folded, most common spelling shown),
the 8 most asked above 5 %.

## What the score never uses

The reputation of the company (what public Reddit and X posts say about it, `docs/backend/ia.md`) never changes a
number: score, applicants, range, reliability, percentile, salary, trend and twins are computed only from the market
data above, with the same functions for every company. A few anonymous posts are opinions, not measures: they are not
comparable from one company to another, most companies have none, and they would make the score unfair and impossible
to check against real numbers. The reputation is only internal context for the wording of the advice
(`result.advice`), and it is never shown to the user.

## Checking ourselves

`POST /v1/analyses/{id}/feedback` stores the real number of applicants known by users next to the estimate
(`applicant_feedback`). Phase 2 or 3: a monthly job computes the usual error by reliability level and adjusts the time
constant (4.2 days), the factors and the ranges (+/- 15, 25, 40 %) so that the real number falls in the range most of
the time, as the screen promises.
