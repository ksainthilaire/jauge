#!/usr/bin/env node
/**
 * Captures d'écran et contrôles du site avec le Chromium de Playwright, contre un serveur local.
 *
 *   python3 tools/site/serveur_local.py &        # port 8812, réécritures de web/_redirects
 *   node tools/site/captures.js [http://127.0.0.1:8812]
 *
 * Écrit web/.captures/<page>-<largeur>-<theme>.png (dossier ignoré par git) et affiche, pour chaque page :
 * les erreurs de console, les requêtes en échec, un défilement horizontal éventuel, les cibles tactiles < 44 px,
 * le poids transféré de la page d'accueil. Sort avec le code 1 si un contrôle échoue.
 */
const fs = require("fs");
const os = require("os");
const path = require("path");

const ROOT = path.resolve(__dirname, "..", "..");
const OUT = path.join(ROOT, "web", ".captures");
const BASE = process.argv[2] || "http://127.0.0.1:8812";
const core = process.env.PLAYWRIGHT_CORE || "/opt/homebrew/lib/node_modules/openclaw/node_modules/playwright-core";
const chromiumPath = process.env.PLAYWRIGHT_CHROMIUM
  || path.join(os.homedir(), "Library/Caches/ms-playwright/chromium-1243/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing");
const { chromium } = require(core);

const PAGES = [
  ["accueil", "/"],
  ["telecharger", "/telecharger/"],
  ["partage", "/a/a-41872"], // réécrit vers /a/index.html par le serveur local (tools/site/serveur_local.py) comme par Cloudflare Pages
  ["confidentialite", "/confidentialite/"],
  ["conditions", "/conditions/"],
  ["supprimer-mon-compte", "/supprimer-mon-compte/"],
  ["mentions-legales", "/mentions-legales/"],
  ["cookies", "/cookies/"],
  ["support", "/support/"],
  ["accessibilite", "/accessibilite/"],
  ["404", "/404.html"],
];
const WIDTHS = [[390, 844], [1400, 900]];
const THEMES = ["light", "dark"];

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ executablePath: chromiumPath, headless: true });
  let failures = 0;
  for (const [name, route] of PAGES) {
    for (const [width, height] of WIDTHS) {
      for (const theme of THEMES) {
        const context = await browser.newContext({ viewport: { width, height }, colorScheme: theme, deviceScaleFactor: 1, locale: "fr-FR" });
        const page = await context.newPage();
        const problems = [];
        let bytes = 0;
        page.on("console", (message) => { if (message.type() === "error" || message.type() === "warning") problems.push(`console ${message.type()}: ${message.text()}`); });
        // L'API n'est pas joignable en local : la page de partage reçoit une analyse d'exemple (même forme que docs/openapi.yaml, PublicAnalysis).
        await page.route("https://api.jauge.io/public/analyses/*", (route) => route.fulfill({ status: 200, contentType: "application/json", headers: { "Access-Control-Allow-Origin": "*" }, body: JSON.stringify({ analysisId: "a-41872", jobTitle: "Chargée de marketing digital", company: "Atelier Sorel", place: "Lyon 3e", score: 78, applicants: { estimate: 210, low: 160, high: 260 }, reliability: "medium", regionName: "Rhône", calmTwin: { jobTitle: "Chargé·e marketing digital", place: "Écully", estimatedApplicants: 35, score: 16 }, createdAt: "2026-10-23T08:00:00Z" }) }));
        page.on("pageerror", (error) => problems.push(`erreur JS: ${error.message}`));
        page.on("requestfailed", (request) => {
          // L'API api.jauge.io n'est pas joignable en local : la page de partage affiche son message d'erreur, c'est attendu.
          if (!request.url().startsWith("https://api.jauge.io/")) problems.push(`requête en échec: ${request.url()} (${request.failure() && request.failure().errorText})`);
        });
        page.on("response", async (response) => {
          const url = response.url();
          if (url.startsWith(BASE) && response.status() >= 400 && !route.startsWith("/404")) problems.push(`HTTP ${response.status()}: ${url}`);
          try { const body = await response.body(); bytes += body.length; } catch (error) { /* corps indisponible (redirection, etc.) */ }
        });
        await page.goto(BASE + route, { waitUntil: "networkidle" });
        // Défile toute la page pour déclencher les apparitions (.reveal) avant la capture.
        await page.evaluate(async () => { document.documentElement.style.scrollBehavior = "auto"; for (let y = 0; y < document.body.scrollHeight; y += 300) { window.scrollTo({ top: y, behavior: "instant" }); await new Promise((r) => setTimeout(r, 60)); } window.scrollTo({ top: 0, behavior: "instant" }); });
        await page.waitForTimeout(900);
        const checks = await page.evaluate(() => {
          const scroll = document.documentElement.scrollWidth > document.documentElement.clientWidth + 1;
          const small = [];
          document.querySelectorAll("a[href], button").forEach((element) => {
            const rect = element.getBoundingClientRect();
            if (rect.width === 0 && rect.height === 0) return;
            if (element.closest("[hidden]") || getComputedStyle(element).display === "none") return;
            // Les liens dans le texte courant (paragraphes, listes de contenu) sont exemptés (WCAG 2.5.5 et 2.5.8).
            if (element.tagName === "A" && element.closest("p, td, .doc-body li, .band li")) return;
            if (rect.height < 44 || rect.width < 44) small.push(`${element.tagName.toLowerCase()} « ${(element.textContent || element.getAttribute("aria-label") || "").trim().slice(0, 30)} » ${Math.round(rect.width)}×${Math.round(rect.height)}`);
          });
          const h1 = document.querySelectorAll("h1").length;
          return { scroll, small, h1, title: document.title };
        });
        if (checks.scroll) problems.push("défilement horizontal");
        if (checks.h1 !== 1) problems.push(`${checks.h1} titres h1`);
        checks.small.forEach((item) => problems.push(`cible < 44 px: ${item}`));
        const file = path.join(OUT, `${name}-${width}-${theme}.png`);
        await page.screenshot({ path: file, fullPage: true });
        const label = `${name} ${width}px ${theme}`;
        if (name === "accueil" && width === 390 && theme === "light") console.log(`poids transféré de l'accueil (mobile, clair) : ${(bytes / 1024).toFixed(0)} Ko`);
        if (problems.length) { failures += 1; console.log(`✗ ${label}\n   ${problems.join("\n   ")}`); }
        else console.log(`✓ ${label} (${checks.title})`);
        await context.close();
      }
    }
  }
  await browser.close();
  console.log(failures ? `${failures} page(s) avec des problèmes` : "toutes les pages passent");
  process.exit(failures ? 1 : 0);
})().catch((error) => { console.error(error); process.exit(1); });
