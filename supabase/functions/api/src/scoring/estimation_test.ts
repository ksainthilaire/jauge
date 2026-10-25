import { assert, assertAlmostEquals, assertEquals } from "@std/assert";
import {
  arrivalShare,
  competitionScore,
  estimateCompetition,
  type MarketOffer,
  marketTrend,
  negotiationTarget,
  percentileOf,
  probableSalary,
  roundCount,
  salaryScale,
  skillDemand,
  timingAdvice,
} from "./estimation.ts";

const TODAY = "2026-10-25";

function offer(index: number, values: Partial<MarketOffer>): MarketOffer {
  return {
    id: `offer-${index}`,
    jobTitle: "Chargé de marketing digital",
    company: null,
    city: "Lyon",
    departmentCode: "69",
    latitude: 45.76,
    longitude: 4.84,
    publishedOn: "2026-10-01",
    salary: null,
    applicantCount: null,
    applicantCountObservedAt: null,
    contractType: "permanent",
    workMode: null,
    skills: [],
    url: null,
    ...values,
  };
}

/** 40 anchors: offers read 14 days after publication with 100 to 295 applicants. */
const ANCHORS = Array.from({ length: 40 }, (_, index) =>
  offer(index, {
    publishedOn: "2026-10-01",
    applicantCount: 100 + index * 5,
    applicantCountObservedAt: "2026-10-15T10:00:00Z",
    salary: index % 2 === 0 ? { minimum: 32 + (index % 8), maximum: 36 + (index % 8) } : null,
    skills: index % 3 === 0 ? ["SEO", "Canva"] : ["SEO"],
  }));

Deno.test("arrival of applications: about 70 % in 5 days", () => {
  assertAlmostEquals(arrivalShare(5), 0.696, 0.01);
  assertEquals(arrivalShare(0), 0);
  assertEquals(roundCount(212), 210);
  assertEquals(roundCount(37), 35);
  assertEquals(roundCount(8), 8);
});

Deno.test("the estimate follows the anchors, the age of the offer and its features", () => {
  const target = {
    publishedOn: "2026-10-23",
    displayedSalary: null,
    contractType: "permanent",
    workMode: null,
    displayedApplicantCount: null,
  };
  const young = estimateCompetition({ target, comparables: ANCHORS, history: [], today: TODAY, partialSources: false });
  const older = estimateCompetition({
    target: { ...target, publishedOn: "2026-10-15" },
    comparables: ANCHORS,
    history: [],
    today: TODAY,
    partialSources: false,
  });
  assert(
    young.estimate < older.estimate,
    "an offer online for 2 days has fewer applicants than one online for 10 days",
  );
  assertEquals(young.anchorCount, 40);
  assertEquals(young.reliability, "medium", "many anchors, but recent and without salary, like the mockup");
  assert(young.reasons.includes("recent_offer") && young.reasons.includes("no_displayed_salary"));
  assertEquals(young.low, roundCount(young.estimate * 0.75));
  assertEquals(young.high, roundCount(young.estimate * 1.25));
  const withSalary = estimateCompetition({
    target: {
      ...target,
      publishedOn: "2026-10-15",
      displayedSalary: { minimum: 36, maximum: 41 },
      workMode: "Télétravail partiel",
    },
    comparables: ANCHORS,
    history: [],
    today: TODAY,
    partialSources: false,
  });
  assertEquals(withSalary.reliability, "high");
  assert(withSalary.finalApplicants > older.finalApplicants, "salary shown and remote work attract more applicants");
  assert(withSalary.score >= older.score);
});

Deno.test("without anchors the estimate uses the history, then the prior, with a low reliability", () => {
  const target = {
    publishedOn: null,
    displayedSalary: null,
    contractType: null,
    workMode: null,
    displayedApplicantCount: null,
  };
  const fromHistory = estimateCompetition({
    target,
    comparables: [],
    history: [{
      month: "2026-09-01",
      applicantsPerOffer: 300,
      salaryMedian: null,
      salaryFirstQuartile: null,
      salaryThirdQuartile: null,
      salaryOfferCount: 0,
    }],
    today: TODAY,
    partialSources: true,
  });
  assertEquals(fromHistory.marketLevel, 300);
  assertEquals(fromHistory.reliability, "low");
  assert(fromHistory.reasons.includes("partial_sources") && fromHistory.reasons.includes("unknown_publication_date"));
  const prior = estimateCompetition({ target, comparables: [], history: [], today: TODAY, partialSources: false });
  assertEquals(prior.marketLevel, 80);
  assertEquals(prior.percentileInRegion, 50);
});

Deno.test("a count shown by the site wins over the model", () => {
  const estimate = estimateCompetition({
    target: {
      publishedOn: "2026-10-20",
      displayedSalary: null,
      contractType: null,
      workMode: null,
      displayedApplicantCount: 212,
    },
    comparables: ANCHORS,
    history: [],
    today: TODAY,
    partialSources: false,
  });
  assertEquals(estimate.estimate, 210);
  assertEquals(estimate.reliability, "high");
});

Deno.test("score: percentile and absolute level, bounded to 0..100", () => {
  assertEquals(percentileOf(5, [1, 2, 3, 10]), 75);
  assertEquals(competitionScore(0, 0), 0);
  assertEquals(competitionScore(10_000, 100), 100);
  assert(competitionScore(200, 84) > 66, "the mockup offer (about 210 applicants, 84th percentile) is crowded");
  assert(competitionScore(20, 10) <= 33, "a calm offer");
});

Deno.test("salary scale, probable salary and negotiation target", () => {
  const scale = salaryScale(ANCHORS, [])!;
  assert(scale.scaleMinimum < scale.middleHalf.minimum && scale.middleHalf.maximum <= scale.scaleMaximum);
  assert(scale.middleHalf.minimum <= scale.median && scale.median <= scale.middleHalf.maximum);
  assertEquals(scale.offerCount, 20);
  const probable = probableSalary(null, ANCHORS)!;
  assert(probable.minimum < probable.maximum);
  assertEquals(probableSalary({ minimum: 36, maximum: 41 }, ANCHORS), { minimum: 36, maximum: 41 });
  assertEquals(probableSalary(null, []), null);
  assertEquals(
    negotiationTarget({ ...scale, middleHalf: { minimum: 35, maximum: 40 } }, { minimum: 36, maximum: 41 }),
    40,
  );
  assertEquals(
    negotiationTarget({ ...scale, middleHalf: { minimum: 35, maximum: 45 } }, { minimum: 36, maximum: 41 }),
    41,
  );
  assertEquals(salaryScale([], []), null);
});

Deno.test("trend, skills and timing advice", () => {
  const months = ["2026-07-01", "2026-08-01", "2026-09-01", "2026-10-01"];
  const comparables = months.flatMap((month, monthIndex) =>
    [0, 1].map((index) =>
      offer(monthIndex * 10 + index, {
        publishedOn: month,
        applicantCount: 100 + monthIndex * 20,
        applicantCountObservedAt: `${month.slice(0, 8)}20T00:00:00Z`,
      })
    )
  );
  const trend = marketTrend(comparables, [], TODAY);
  assertEquals(trend.points.map((point) => point.month), ["2026-07", "2026-08", "2026-09", "2026-10"]);
  assert(trend.changeThreeMonthsPercent > 0);
  const skills = skillDemand(ANCHORS);
  assertEquals(skills[0], { name: "SEO", sharePercent: 100 });
  assertEquals(timingAdvice("2026-10-22", TODAY), { applyBefore: "TUESDAY", sharePercent: 70, withinDays: 5 });
  assertEquals(timingAdvice("2026-10-01", TODAY), null);
  assertEquals(timingAdvice(null, TODAY), null);
});
