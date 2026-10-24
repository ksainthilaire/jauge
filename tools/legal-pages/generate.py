#!/usr/bin/env python3
"""Builds the public legal pages of jauge.io (docs/web) from the texts of the app.

The app is the single source: feature/account/src/commonMain/composeResources/values/legal.xml (sections) and
legal_identity.xml (publisher identity). Run after any change of these files:

    python3 tools/legal-pages/generate.py

Pages written (French, the store listings link to them):
    docs/web/confidentialite/index.html     privacy policy (Google Play and App Store "Privacy policy URL")
    docs/web/conditions/index.html          terms of use (App Store EULA link in the description)
    docs/web/mentions-legales/index.html    legal notice
    docs/web/supprimer-mon-compte/index.html  account deletion without the app (Google Play "Delete account URL")
"""
import html
import pathlib
import re
import xml.etree.ElementTree as ElementTree

ROOT = pathlib.Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "feature/account/src/commonMain/composeResources/values"
WEB = ROOT / "docs/web"


def read_strings(path: pathlib.Path) -> dict:
    """Reads a Compose resources strings file into a dictionary, unescaping \\n and \\'."""
    strings = {}
    for element in ElementTree.parse(path).getroot().iter("string"):
        text = element.text or ""
        strings[element.get("name")] = text.replace("\\n", "\n").replace("\\'", "'").replace('\\"', '"')
    return strings


def fill(template: str, *values: str) -> str:
    """Replaces the %1$s placeholders of an Android string."""
    return re.sub(r"%(\d+)\$s", lambda match: values[int(match.group(1)) - 1], template)


def paragraphs(text: str) -> str:
    """Turns a legal body (paragraphs and "• " bullet lines) into HTML."""
    blocks = []
    for block in text.split("\n\n"):
        lines = [line for line in block.split("\n") if line.strip()]
        if lines and all(line.startswith("• ") for line in lines):
            items = "".join(f"<li>{html.escape(line[2:])}</li>" for line in lines)
            blocks.append(f"<ul>{items}</ul>")
        else:
            blocks.append("".join(f"<p>{html.escape(line)}</p>" for line in lines))
    return "\n".join(blocks)


STYLE = """
:root { color-scheme: light dark; --paper: #F5F0E6; --ink: #13201C; --muted: #5B6863; --action: #FF5A3C; --line: #E3DACB; }
@media (prefers-color-scheme: dark) { :root { --paper: #13201C; --ink: #F5F0E6; --muted: #A9B3AE; --line: #2A3833; } }
* { box-sizing: border-box; }
body { margin: 0; background: var(--paper); color: var(--ink); font: 17px/1.6 -apple-system, "Segoe UI", Roboto, sans-serif; }
main { max-width: 720px; margin: 0 auto; padding: 32px 16px 64px; }
a { color: var(--action); }
h1 { font-size: 2rem; line-height: 1.2; margin: 0 0 8px; }
h2 { font-size: 1.2rem; margin: 32px 0 8px; }
.updated, footer { color: var(--muted); font-size: 0.9rem; }
.brand { font-weight: 700; text-decoration: none; color: var(--ink); display: inline-block; margin-bottom: 24px; }
ol.steps li { margin-bottom: 8px; }
.box { border: 1px solid var(--line); border-radius: 16px; padding: 16px 20px; margin: 16px 0; }
footer { margin-top: 48px; border-top: 1px solid var(--line); padding-top: 16px; }
"""


def page(title: str, body: str) -> str:
    """Wraps a page body in the shared layout."""
    return f"""<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)} - Jauge</title>
<style>{STYLE}</style>
</head>
<body>
<main>
<a class="brand" href="/">Jauge</a>
{body}
<footer>
<a href="/confidentialite/">Confidentialité</a> · <a href="/conditions/">Conditions d'utilisation</a> ·
<a href="/mentions-legales/">Mentions légales</a> · <a href="/supprimer-mon-compte/">Supprimer mon compte</a>
</footer>
</main>
</body>
</html>
"""


def document(title: str, introduction: str | None, sections: list, updated: str) -> str:
    """A legal document: title, update date, introduction and numbered sections."""
    parts = [f"<h1>{html.escape(title)}</h1>", f'<p class="updated">{html.escape(updated)}</p>']
    if introduction:
        parts.append(paragraphs(introduction))
    for section_title, section_body in sections:
        parts.append(f"<h2>{html.escape(section_title)}</h2>\n{paragraphs(section_body)}")
    return page(title, "\n".join(parts))


def write(relative: str, content: str) -> None:
    """Writes a page under docs/web."""
    target = WEB / relative / "index.html"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")
    print("wrote", target.relative_to(ROOT))


def main() -> None:
    legal = read_strings(RESOURCES / "legal.xml")
    identity = read_strings(RESOURCES / "legal_identity.xml")
    app = read_strings(RESOURCES / "strings.xml")
    updated = legal["legal_updated"]

    privacy = [(legal[f"privacy_{index}_title"], legal[f"privacy_{index}_body"]) for index in range(1, 13)]
    write("confidentialite", document(app.get("privacy_policy_title", "Politique de confidentialité"), legal["privacy_intro"], privacy, updated))

    terms = [(legal[f"terms_{index}_title"], legal[f"terms_{index}_body"]) for index in range(1, 14)]
    terms[-1] = (terms[-1][0], fill(terms[-1][1], identity["legal_consumer_mediator"]))
    write("conditions", document(app.get("terms_of_use_title", "Conditions d'utilisation"), legal["terms_intro"], terms, updated))

    notice = [
        (legal["notice_1_title"], fill(
            legal["notice_1_body"],
            identity["legal_publisher_name"], identity["legal_publisher_legal_form"],
            identity["legal_publisher_share_capital"], identity["legal_publisher_registration"],
            identity["legal_publisher_address"], identity["legal_publisher_vat_number"],
        )),
        (legal["notice_2_title"], fill(legal["notice_2_body"], identity["legal_publication_director"])),
        (legal["notice_3_title"], fill(legal["notice_3_body"], identity["legal_host_address"])),
        (legal["notice_4_title"], legal["notice_4_body"]),
        (legal["notice_5_title"], legal["notice_5_body"]),
    ]
    write("mentions-legales", document(app.get("legal_notice_title", "Mentions légales"), None, notice, updated))

    write("supprimer-mon-compte", page("Supprimer mon compte Jauge", DELETION_BODY))


DELETION_BODY = """
<h1>Supprimer mon compte Jauge</h1>
<p>Tu peux supprimer ton compte Jauge et toutes les données qui y sont liées, à tout moment et sans justification.</p>

<h2>Depuis l'application (le plus rapide)</h2>
<ol class="steps">
<li>Ouvre Jauge et touche l'onglet <strong>Profil</strong>.</li>
<li>Dans « Compte et données », touche <strong>Supprimer mon compte</strong>.</li>
<li>Coche « Je comprends que la suppression est définitive », puis touche <strong>Supprimer définitivement</strong>.</li>
</ol>

<h2>Sans l'application</h2>
<p>Si tu n'as plus l'application, écris-nous depuis l'adresse e-mail de ton compte (ou indique le service utilisé pour te connecter : Apple, Google ou e-mail) :</p>
<p class="box"><a href="mailto:privacy@jauge.io?subject=Suppression%20de%20mon%20compte%20Jauge&amp;body=Bonjour%2C%0A%0AJe%20demande%20la%20suppression%20de%20mon%20compte%20Jauge%20et%20de%20toutes%20mes%20donn%C3%A9es.%0A%0AAdresse%20e-mail%20du%20compte%20%3A%20%0AConnexion%20utilis%C3%A9e%20(Apple%2C%20Google%20ou%20e-mail)%20%3A%20%0A">privacy@jauge.io</a>, objet « Suppression de mon compte Jauge ».</p>
<p>Nous vérifions que la demande vient bien du titulaire du compte, puis nous supprimons le compte sous 30 jours au plus, et nous te confirmons la suppression par e-mail.</p>

<h2>Ce qui est supprimé</h2>
<ul>
<li>Ton compte, ton adresse e-mail et tes accords (conseils par e-mail).</li>
<li>Tes offres jaugées, leurs résultats et les captures encore conservées.</li>
<li>Tes alertes « calme », tes notifications et les jetons de notification de tes appareils.</li>
<li>Tes liens de partage jauge.io/a/ : ils cessent de fonctionner.</li>
</ul>
<p>L'effacement est immédiat dans l'application et complet sur nos serveurs sous 30 jours, sauvegardes comprises.</p>

<h2>Ce qui est conservé</h2>
<ul>
<li>Les données de facturation tenues par l'App Store, Google Play et RevenueCat, selon leurs obligations légales (jusqu'à 10 ans).</li>
<li>Les journaux techniques, sans contenu personnel, 12 mois au plus.</li>
</ul>

<h2>Ton abonnement Jauge Plus</h2>
<p>Supprimer ton compte n'arrête pas un abonnement en cours : il est géré par l'App Store ou Google Play. Annule-le d'abord dans
Réglages, ton nom, Abonnements (iPhone) ou dans Google Play, Paiements et abonnements, Abonnements (Android).</p>
"""

if __name__ == "__main__":
    main()
