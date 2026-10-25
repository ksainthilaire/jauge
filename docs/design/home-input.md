# Home screen input (screen 02, top)

Redesign of "Quelle offre on jauge ?": the link card, the "Jauger cette offre" button and the "Une capture" and
"Photographier" tiles.

## Problems of the previous version

- The card had a thick paper outline (2 dp, `textPrimary`): the loudest element of the screen before anything is typed.
- "Lien collé" was shown while nothing was pasted.
- The field used JetBrains Mono in grey and had no field shape: it read as a caption, not as a place to type.
- The big cream "Jauger cette offre" button was always on, although there was nothing to gauge.
- Link, screenshot and photo looked like three unrelated blocks (one card, two tiles).
- "BONJOUR" in capitals, whatever the hour and even with a known first name.

## References

1. Awwwards, "Central search bar": https://www.awwwards.com/inspiration/central-search-bar
   One prominent field is the hero of the page; the rest of the page steps back. Kept: the field is the first thing
   to read after the headline, with a calm surface instead of a heavy outline.
2. Awwwards, "Input field" (fio.ai "Ask me anything"): https://www.awwwards.com/inspiration/input-field
   The input and its send action live in the same object, and the action only lights up when there is something to
   send. Kept: one action whose look follows the content of the field.
3. Dribbble, "Link Copy Paste Web UI" (Md Afser Uddin): https://dribbble.com/shots/21100820-Link-Copy-Paste-Web-UI
   Paste link inputs put a paste button inside the field and confirm what was understood. Kept: "Coller" inside the
   composer, and a line under the link naming the recognized site.
4. Behance, AI assistant app projects (for example "Grok | AI Assistant App Redesign"):
   https://www.behance.net/search/projects/ai%20assistant%20app
   The mobile AI composers group the text, the attachments (photo, file) and the send button in one surface, with
   attachments as small tiles on the left and the send action on the right. Kept: "Capture" and "Photo" become the
   attachments of the composer, so the three ways to give an offer form one set.
5. Screensdesign, mobile send buttons: https://screensdesign.com/explore/ui-elements/send-buttons/
   The send button changes state (disabled, ready, sending) instead of being a big permanent block. Kept: "Jauger" is
   dimmed while the text is not a link, coral once it is.

Platform rules that shaped the paste button:

- iOS shows "Autoriser le collage ?" when an app reads the pasteboard. `UIPasteboard.hasURLs` does not show it, so the
  app only asks whether a link is there, and reads the content on the tap of "Coller"
  (https://developer.apple.com/documentation/uikit/uipasteboard).
- Android 12 and later shows a toast each time the clipboard content is read. The clip description (and its URL
  confidence score) is metadata and shows nothing
  (https://developer.android.com/develop/ui/views/touch-and-input/copy-paste).

## Three directions

Sketches (empty on top, recognized link below), `docs/design/home-input-pistes/`, drawn by
`HomeInputDirectionsScreenshotTest`:

| Direction | Light | Dark |
|---|---|---|
| A. Hero field, "Coller" inside, coral button only when a link is recognized, text links "Une capture · Une photo" | `a-champ-heros-clair.png` | `a-champ-heros-sombre.png` |
| B. Segmented control "Lien · Capture · Photo" above one content area | `b-segment-clair.png` | `b-segment-sombre.png` |
| C. Composer: field on top, "Capture" and "Photo" in its toolbar, one action on the right ("Coller" then "Jauger") | `c-composer-clair.png` | `c-composer-sombre.png` |

## Choice: C, the composer

- **One set.** The three ways to give an offer sit in one surface, in the order people use them (type or paste a
  link, else attach a capture or a photo). A pushes capture and photo into small text links that look secondary and
  are hard to tap; B makes capture and photo cost two taps (pick the segment, then the button) and keeps a dead
  "Jauger" block on screen.
- **One action at a time.** The right of the toolbar is "Coller" (or "Coller le lien copié" when the system says the
  clipboard holds a link) while the field is empty, "Jauger" dimmed while the text is not a link, and coral "Jauger"
  once it is. The coral never shows before there is something to gauge.
- **Quiet before, clear after.** Thin outline (darker only with the focus), Instrument Sans at 16 sp in the field,
  and a mint line "Offre Welcome to the Jungle reconnue" or "Lien vers emplois-rhone.fr" once the link is understood.
- **Familiar.** It is the shape of the AI composers people already know, which fits "on jauge pour toi".

## What was built

- Design system (`core/designsystem`): `JaugeComposer`, `ComposerToolButton`, `ComposerActionButton`,
  `ComposerStatus`, the `Paste` icon and `rememberReducedMotion()` (Android animator scale, iOS "Réduire les
  animations"). Transitions (outline color, action cross fade, height of the status line) are short (120 to 200 ms)
  and skipped when motion is reduced.
- Screen 02 (`feature/analysis`): `OfferInput`, `OfferLinkRecognizer` (known job sites: Welcome to the Jungle,
  Indeed, LinkedIn, France Travail, HelloWork, Apec, Cadremploi, Glassdoor, Monster, JobTeaser, Meteojob,
  leboncoin, Malt; any other site shows its address), `ClipboardReader` (Android, iOS), greeting "Bonjour" from 5 h
  and "Bonsoir" from 18 h, with the first name when signed in.
- Paste keeps the address of a copied offer that comes with its title ("Chargée de marketing https://...").
- Accessibility: every control is at least 48 dp, the field is labelled "Lien de l'offre", the cross "Effacer le
  lien"; the toolbar wraps with large text (`02-saisie-texte-130*.png`).
- Screenshots of every state, light and dark: `docs/screenshots/02-saisie-*.png`.
