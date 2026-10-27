#!/usr/bin/env bash
# Vérifie le site statique web/ sans serveur : liens internes et fichiers référencés, un seul h1 par page,
# balises SEO présentes, JSON-LD valide, sitemap.xml valide et cohérent avec les pages, _redirects et _headers.
# Usage : bash web/verifier.sh   (code de sortie 1 en cas d'erreur)
set -u
cd "$(dirname "$0")" || exit 1

python3 - <<'EOF'
import json, pathlib, re, sys
import xml.etree.ElementTree as ET

WEB = pathlib.Path(".")
errors = []
pages = sorted(p for p in WEB.rglob("*.html") if ".captures" not in p.parts)

def exists(target: str) -> bool:
    target = target.split("#")[0].split("?")[0]
    if not target or target.startswith(("http://", "https://", "mailto:", "tel:")):
        return True
    path = WEB / target.lstrip("/")
    if target.endswith("/"):
        return (path / "index.html").is_file()
    if target == "/favicon.ico" or "." in path.name:
        return path.is_file()
    return path.is_dir() and (path / "index.html").is_file() or path.is_file()

for page in pages:
    text = page.read_text(encoding="utf-8")
    where = str(page)
    if text.count("<h1") != 1:
        errors.append(f"{where}: {text.count('<h1')} h1")
    if '<html lang="fr"' not in text:
        errors.append(f"{where}: lang=\"fr\" absent")
    for required in ("<title>", 'name="description"', 'name="viewport"'):
        if required not in text:
            errors.append(f"{where}: {required} absent")
    if page.name != "404.html" and 'rel="canonical"' not in text and "noindex" not in text:
        errors.append(f"{where}: canonical absent")
    for match in re.finditer(r'(?:href|src|srcset)="([^"]+)"', text):
        for value in match.group(1).split(","):
            target = value.strip().split(" ")[0]
            if target.startswith("/") and not exists(target):
                errors.append(f"{where}: lien ou fichier introuvable {target}")
            if "REPLACE_WITH" in target:
                print(f"  à remplacer avant publication : {where} -> {target}")
    for anchor in re.findall(r'href="#([^"]+)"', text):
        if f'id="{anchor}"' not in text:
            errors.append(f"{where}: ancre #{anchor} introuvable")
    for match in re.finditer(r'<img\b[^>]*>', text):
        tag = match.group(0)
        if ' alt="' not in tag:
            errors.append(f"{where}: image sans alt : {tag[:60]}")
        if ' width="' not in tag or ' height="' not in tag:
            errors.append(f"{where}: image sans dimensions : {tag[:60]}")
    for block in re.findall(r'<script type="application/ld\+json">(.*?)</script>', text, re.S):
        try:
            data = json.loads(block)
            graph = data.get("@graph", [data])
            if any("aggregateRating" in node for node in graph):
                errors.append(f"{where}: aggregateRating présent sans avis réels")
        except json.JSONDecodeError as error:
            errors.append(f"{where}: JSON-LD invalide ({error})")
    for span in re.findall(r"\[à compléter[^\]]*\]", text):
        print(f"  à compléter avant publication : {where} -> {span}")

# sitemap.xml
try:
    root = ET.parse("sitemap.xml").getroot()
    namespace = "{http://www.sitemaps.org/schemas/sitemap/0.9}"
    locations = [element.text for element in root.iter(f"{namespace}loc")]
    for location in locations:
        path = location.replace("https://jauge.io", "")
        if not exists(path):
            errors.append(f"sitemap.xml: page introuvable {path}")
    for page in pages:
        route = "/" + page.relative_to(WEB).as_posix().replace("index.html", "")
        if page.name == "index.html" and route != "/a/" and f"https://jauge.io{route}" not in locations:
            errors.append(f"sitemap.xml: {route} absent")
except ET.ParseError as error:
    errors.append(f"sitemap.xml invalide ({error})")

# Fichiers Cloudflare, manifeste, .well-known
for name in ("_headers", "_redirects", "robots.txt", "llms.txt", "llms-full.txt", "site.webmanifest", "favicon.ico",
             ".well-known/assetlinks.json", ".well-known/apple-app-site-association", "email/logo-jauge-96.png", "404.html"):
    if not (WEB / name).is_file():
        errors.append(f"fichier manquant : {name}")
for name in ("site.webmanifest", ".well-known/assetlinks.json", ".well-known/apple-app-site-association"):
    try:
        json.loads((WEB / name).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        errors.append(f"{name}: JSON invalide ({error})")
for line in (WEB / "_redirects").read_text(encoding="utf-8").splitlines():
    parts = line.split()
    if parts and not line.startswith("#") and parts[1].startswith("/") and "*" not in parts[1] and not exists(parts[1]):
        errors.append(f"_redirects: cible introuvable {parts[1]}")
if "REPLACE_WITH" in (WEB / ".well-known/assetlinks.json").read_text() + (WEB / ".well-known/apple-app-site-association").read_text():
    print("  à remplacer avant publication : .well-known (empreinte Play, Team ID Apple)")

# Poids de la page d'accueil, images comprises
home = WEB / "index.html"
weight = home.stat().st_size + (WEB / "assets/site.css").stat().st_size + (WEB / "assets/site.js").stat().st_size
for image in re.findall(r'(?:src|srcset)="(/assets/[^"]+)"', home.read_text(encoding="utf-8")):
    weight += (WEB / image.lstrip("/")).stat().st_size
print(f"  poids de la page d'accueil (HTML, CSS, JS, images) : {weight / 1024:.0f} Ko")
if weight > 1024 * 1024:
    errors.append("page d'accueil > 1 Mo")

print(f"  {len(pages)} pages vérifiées")
if errors:
    print("\n".join("ERREUR " + error for error in errors))
    sys.exit(1)
print("  tout est bon")
EOF
