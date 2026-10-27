#!/usr/bin/env node
/**
 * Rend les images du site (favicon PNG, icônes, favicon.ico, image Open Graph) avec le Chromium de Playwright.
 *
 *   node tools/site/rendre_images.js
 *
 * Il faut `playwright-core` (PLAYWRIGHT_CORE, par défaut celui d'OpenClaw sur le Mac) et un Chromium
 * (PLAYWRIGHT_CHROMIUM, par défaut celui du cache Playwright). Les sources : web/assets/icons/favicon.svg et
 * tools/site/og.html. Aucune image n'est générée au déploiement : les PNG sont commités.
 */
const fs = require("fs");
const os = require("os");
const path = require("path");

const ROOT = path.resolve(__dirname, "..", "..");
const WEB = path.join(ROOT, "web");
const core = process.env.PLAYWRIGHT_CORE || "/opt/homebrew/lib/node_modules/openclaw/node_modules/playwright-core";
const chromium = process.env.PLAYWRIGHT_CHROMIUM
  || path.join(os.homedir(), "Library/Caches/ms-playwright/chromium-1243/chrome-mac-arm64/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing");
const playwright = require(core);

function ico(png) {
  // Conteneur ICO d'une seule image PNG 32 × 32 (accepté par tous les navigateurs actuels).
  const header = Buffer.alloc(6 + 16);
  header.writeUInt16LE(0, 0); header.writeUInt16LE(1, 2); header.writeUInt16LE(1, 4);
  header.writeUInt8(32, 6); header.writeUInt8(32, 7); header.writeUInt8(0, 8); header.writeUInt8(0, 9);
  header.writeUInt16LE(1, 10); header.writeUInt16LE(32, 12); header.writeUInt32LE(png.length, 14); header.writeUInt32LE(22, 18);
  return Buffer.concat([header, png]);
}

(async () => {
  const browser = await playwright.chromium.launch({ executablePath: chromium, headless: true });
  const svg = fs.readFileSync(path.join(WEB, "assets/icons/favicon.svg"), "utf8");

  async function renderIcon(size, file) {
    const page = await browser.newPage({ viewport: { width: size, height: size }, deviceScaleFactor: 1 });
    await page.setContent(`<html><body style="margin:0;background:transparent">${svg.replace("<svg ", `<svg width="${size}" height="${size}" `)}</body></html>`);
    const buffer = await page.screenshot({ omitBackground: true, type: "png" });
    await page.close();
    if (file) fs.writeFileSync(path.join(WEB, file), buffer);
    return buffer;
  }

  await renderIcon(180, "assets/icons/apple-touch-icon.png");
  await renderIcon(192, "assets/icons/icon-192.png");
  await renderIcon(512, "assets/icons/icon-512.png");
  fs.writeFileSync(path.join(WEB, "favicon.ico"), ico(await renderIcon(32, null)));

  const page = await browser.newPage({ viewport: { width: 1200, height: 630 }, deviceScaleFactor: 1 });
  await page.goto("file://" + path.join(__dirname, "og.html"));
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(300);
  await page.screenshot({ path: path.join(WEB, "assets/img/og.png"), type: "png" });
  await page.close();
  await browser.close();
  console.log("images écrites dans web/assets/icons, web/favicon.ico et web/assets/img/og.png");
})().catch((error) => { console.error(error); process.exit(1); });
