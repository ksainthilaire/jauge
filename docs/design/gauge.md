# The gauge

The gauge is the heart of the Jauge brand (the logo is a gauge). This page records the references, the three
directions drawn in Compose and the choice.

## What was wrong (October 2026)

Seen on a phone in dark theme, verdict 04 at score 78:

- The top of the arc was cut by the edge of the card. Cause: the gauge asked for `aspectRatio(2f)` while the screen
  forced `height(150.dp)`; with both, the canvas kept its 2:1 size and overflowed its box by about 20 dp at the top
  and at the bottom.
- The same overflow pushed the needle pivot onto the score: the dot covered the "78".
- In dark theme the card used `inverseSurface` and turned cream, a bright block on an ink screen.

## References

| Link | What we keep |
|---|---|
| [Score meter, Kuba Zelichowski (Dribbble)](https://dribbble.com/shots/20962123-Score-meter) | A thick half circle with a continuous color scale and the score large in the middle: the number is the hero, the arc frames it. |
| [Rotating Gauge / Meter, Yonatan Doron (Dribbble)](https://dribbble.com/shots/7105754-Rotating-Gauge-Meter) | A small round marker that rides the scale instead of a needle crossing the face: nothing passes over the value. |
| [Fintech case study, designing a credit scoring system (Behance)](https://www.behance.net/gallery/134175381/Fintech-Case-Study-Designing-A-Credit-Scoring-System) | Credit score screens: one dial at the top, the explanation below; the color of the zone and its word go together. |
| [Monzo credit insights, design notes (community thread)](https://community.monzo.com/t/the-latest-news-on-our-credit-insights-tool/167920?page=3) | Monzo chose a scale where the color changes are easy to tell apart rather than evenly spread: soft transitions placed on the thresholds. |
| [Recovery score radial dial, WHOOP pattern (VP0)](https://vp0.com/blogs/whoop-clone-recovery-score-radial-dial-ui) | Health score dials: an arc that fills from 0 to the value in banded colors, the score in the center and a short label under it; the fill animates on entry. |

Common lessons: the value is read first (big number, free of any mark), the arc only gives the context; the color of
the zone always comes with its word (our rule from `BRANDING.md`: "Calme", "Disputé", "Bondé"); the entry animation is
a single sweep that settles, never a bounce.

## Three directions

Captures in `docs/design/gauge-pistes/` (light and dark), drawn by `GaugeDirectionsScreenshotTest`.

1. **Cursor** (`1-cursor-*.png`): a continuous mint, amber, coral scale, faint for the whole range and full color up
   to the score, a round cursor (ring in the color of the score, ink center) that rides the arc, discreet graduation
   dots every 10 points inside the arc, the score in the middle.
2. **Segments and tick** (`2-segments_tick-*.png`): the three segments of the logo, the active one lit, a short tick
   that crosses the arc at the value.
3. **Graduations** (`3-graduations-*.png`): 41 radial bars, colored up to the score, a longer bar at the value.

## Choice: direction 1, the cursor

- The score is never covered: the cursor only lives on the arc, the number has the whole middle.
- Calm, disputed and crowded read at once from the color under the cursor, and the coming levels stay visible in the
  faint part of the scale. The color transitions are centered on the level thresholds (33/34 and 66/67), so the
  cursor color always agrees with the word of the pill.
- It stays readable small: at 52 dp in list rows, the arc, the cursor and the score inside remain clear. Direction 3
  turns into noise below 80 dp; direction 2 reads well but its tick is hard to see on the dim segments and it repeats
  the old look the user found clumsy.
- It keeps the brand: the half circle and the three colors of the logo, in a smoother and more modern form.

## Rules of the component (`JaugeGauge`, `core:designsystem`)

- The whole arc, its round ends and the cursor always fit with a margin (`GaugeGeometry.fit`), whatever size the
  layout gives. A squeezed area gets a thinner arc (`thicknessScale`), never a cut one.
- The score is written in a free area inside the arc (`GaugeGeometry.scoreArea`) and shrinks to fit (auto size): no
  overlap at 200 % text size, from 0 to 100.
- Entry: the arc sweeps from 0 to the score in 1.1 s with a soft deceleration while the number counts up. No motion
  when the system asks to reduce animations, or in lists and screenshots (`animate = false`).
- Colors come from the theme: `contentColor` (text of the card) and `surfaceColor` (the card, inside the cursor). The
  verdict card uses `emphasisSurface`: ink on a light screen, a raised dark card on a dark screen.
- Sizes: `HERO` (welcome), `LARGE` (verdict), `MEDIUM` (share card, account brand card), `MINI` (list rows, 52 dp,
  score inside).
- The welcome hero uses `JaugeGauge` (HERO size); illustrations that draw a gauge themselves call `drawJaugeArc` (email illustration). The logo and the
  tab icon keep their needle: they are the brand mark, not a reading.
