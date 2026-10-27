#!/usr/bin/env python3
"""Builds the document pages of jauge.io (web/) from the texts of the app and from HTML fragments.

The app is the single source of the legal texts: feature/account/src/commonMain/composeResources/values/legal.xml
(sections) and legal_identity.xml (publisher identity). Run after any change of these files, of the fragments in
tools/legal-pages/pages/ or of the layout tools/legal-pages/layout.html:

    python3 tools/legal-pages/generate.py

Pages written (French, the store listings link to them):
    web/confidentialite/index.html       privacy policy (Google Play and App Store "Privacy policy URL")
    web/conditions/index.html            terms of use (App Store EULA link in the description)
    web/mentions-legales/index.html      legal notice
    web/supprimer-mon-compte/index.html  account deletion without the app (Google Play "Delete account URL")
    web/cookies/index.html, web/support/index.html, web/accessibilite/index.html   from tools/legal-pages/pages/

The layout (header, footer, metadata, JSON-LD) is tools/legal-pages/layout.html; the style is web/assets/site.css.
The landing page (web/index.html), web/telecharger/ and web/a/ are written by hand.
"""
import html
import pathlib
import re
import unicodedata
import xml.etree.ElementTree as ElementTree

ROOT = pathlib.Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "feature/account/src/commonMain/composeResources/values"
HERE = pathlib.Path(__file__).resolve().parent
WEB = ROOT / "web"

MONTHS = ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"]

# Site only additions (the app has no website section): appended to the generated legal pages.
PRIVACY_SITE_SECTION = (
    "Le site jauge.io",
    "Le site jauge.io (cette page, la page d'accueil, la page de téléchargement et les pages de partage jauge.io/a/) "
    "n'utilise aucun cookie, aucun traceur publicitaire ni outil de mesure d'audience. Il est servi par Cloudflare, Inc. "
    "(États-Unis, serveurs dans le monde entier, dont l'Union européenne), qui conserve des journaux techniques de "
    "connexion (adresse IP, page demandée, date) pendant une courte durée pour la sécurité et la lutte contre les abus. "
    "Les polices de caractères sont chargées depuis Google Fonts : Google voit alors l'adresse IP de ton navigateur, "
    "sans cookie. Les pages de partage lisent la carte partagée auprès de l'API de Jauge sans identifiant ni cookie.\n\n"
    "Détails : jauge.io/cookies."
)
NOTICE_SITE_PARAGRAPH = (
    "Le site jauge.io est hébergé par Cloudflare, Inc., 101 Townsend Street, San Francisco, CA 94107, États-Unis "
    "(cloudflare.com)."
)


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


def mark_todo(text: str) -> str:
    """Makes the "[à compléter …]" placeholders visible (never invented, filled by the owner before publication)."""
    return re.sub(r"\[à compléter[^\]]*\]", lambda match: f'<span class="todo">{match.group(0)}</span>', text)


def escape(text: str) -> str:
    return mark_todo(html.escape(text))


def paragraphs(text: str) -> str:
    """Turns a legal body (paragraphs and "• " bullet lines) into HTML."""
    blocks = []
    for block in text.split("\n\n"):
        lines = [line for line in block.split("\n") if line.strip()]
        if lines and all(line.startswith("• ") for line in lines):
            items = "".join(f"<li>{escape(line[2:])}</li>" for line in lines)
            blocks.append(f"<ul>{items}</ul>")
        else:
            blocks.append("".join(f"<p>{escape(line)}</p>" for line in lines))
    return "\n".join(blocks)


def slug(title: str) -> str:
    """An anchor for a section title: "3. Tes droits" -> "tes-droits"."""
    text = unicodedata.normalize("NFKD", title).encode("ascii", "ignore").decode()
    text = re.sub(r"^\d+\.\s*", "", text).lower()
    return re.sub(r"[^a-z0-9]+", "-", text).strip("-") or "section"


def iso_date(updated: str) -> str:
    """"Dernière mise à jour : 25 octobre 2026" -> "2026-10-25"."""
    match = re.search(r"(\d{1,2})\s+(\S+)\s+(\d{4})", updated)
    if not match or match.group(2) not in MONTHS:
        return "2026-10-27"
    return f"{match.group(3)}-{MONTHS.index(match.group(2)) + 1:02d}-{int(match.group(1)):02d}"


def toc(body: str) -> str:
    """One entry per <h2 id="…"> of the body."""
    entries = re.findall(r'<h2 id="([^"]+)">(.*?)</h2>', body)
    return "\n".join(f'        <li><a href="#{anchor}">{title}</a></li>' for anchor, title in entries)


def page(path: str, title: str, description: str, body: str, modified: str) -> str:
    """Wraps a page body in the shared layout."""
    layout = (HERE / "layout.html").read_text(encoding="utf-8")
    replacements = {
        "@@TITLE@@": html.escape(title, quote=True),
        "@@DESCRIPTION@@": html.escape(description, quote=True),
        "@@PATH@@": path,
        "@@MODIFIED@@": modified,
        "@@TOC@@": toc(body),
        "@@BODY@@": body,
        "@@CURRENT_SUPPORT@@": ' aria-current="page"' if path == "/support/" else "",
    }
    for token, value in replacements.items():
        layout = layout.replace(token, value)
    return layout


def document(title: str, introduction: str | None, sections: list, updated: str) -> str:
    """A legal document: title, update date, introduction and numbered sections."""
    parts = [f"<h1>{html.escape(title)}</h1>", f'<p class="updated">{html.escape(updated)}</p>']
    if introduction:
        parts.append(paragraphs(introduction))
    for section_title, section_body in sections:
        parts.append(f'<h2 id="{slug(section_title)}">{html.escape(section_title)}</h2>\n{paragraphs(section_body)}')
    return "\n".join(parts)


def write(relative: str, content: str) -> None:
    """Writes a page under web/."""
    target = WEB / relative / "index.html"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")
    print("wrote", target.relative_to(ROOT))


def fragment(name: str) -> tuple:
    """A hand written body (tools/legal-pages/pages/<name>.html) and its update date."""
    body = (HERE / "pages" / f"{name}.html").read_text(encoding="utf-8").strip()
    match = re.search(r'<p class="updated">(.*?)</p>', body)
    return body, iso_date(match.group(1) if match else "")


def main() -> None:
    legal = read_strings(RESOURCES / "legal.xml")
    identity = read_strings(RESOURCES / "legal_identity.xml")
    app = read_strings(RESOURCES / "strings.xml")
    updated = legal["legal_updated"]
    modified = iso_date(updated)

    privacy = [(legal[f"privacy_{index}_title"], legal[f"privacy_{index}_body"]) for index in range(1, 13)]
    privacy.append((f"{len(privacy) + 1}. {PRIVACY_SITE_SECTION[0]}", PRIVACY_SITE_SECTION[1]))
    title = app.get("privacy_policy_title", "Politique de confidentialité")
    write("confidentialite", page(
        "/confidentialite/", title,
        "Quelles données l'application Jauge utilise, pourquoi, combien de temps, qui les reçoit et quels sont tes droits. "
        "Les captures d'écran ne quittent jamais ton téléphone.",
        document(title, legal["privacy_intro"], privacy, updated), modified,
    ))

    terms = [(legal[f"terms_{index}_title"], legal[f"terms_{index}_body"]) for index in range(1, 14)]
    terms[-1] = (terms[-1][0], fill(terms[-1][1], identity["legal_consumer_mediator"]))
    title = app.get("terms_of_use_title", "Conditions d'utilisation")
    write("conditions", page(
        "/conditions/", title,
        "Conditions d'utilisation de l'application Jauge et de l'abonnement Jauge Plus : service, estimations, compte, "
        "offre gratuite, abonnement, essai, résiliation, rétractation, partages et litiges.",
        document(title, legal["terms_intro"], terms, updated), modified,
    ))

    notice = [
        (legal["notice_1_title"], fill(
            legal["notice_1_body"],
            identity["legal_publisher_name"], identity["legal_publisher_legal_form"],
            identity["legal_publisher_siren"], identity["legal_publisher_address"],
        )),
        (legal["notice_2_title"], fill(legal["notice_2_body"], identity["legal_publication_director"])),
        (legal["notice_3_title"], fill(legal["notice_3_body"], identity["legal_host_address"]) + "\n\n" + NOTICE_SITE_PARAGRAPH),
        (legal["notice_4_title"], legal["notice_4_body"]),
        (legal["notice_5_title"], legal["notice_5_body"]),
    ]
    title = app.get("legal_notice_title", "Mentions légales")
    write("mentions-legales", page(
        "/mentions-legales/", title,
        "Éditeur, directeur de la publication, hébergeurs de l'application et du site jauge.io, propriété intellectuelle "
        "et signalement d'un contenu.",
        document(title, None, notice, updated), modified,
    ))

    for name, title, description in (
        ("supprimer-mon-compte", "Supprimer mon compte",
         "Comment supprimer ton compte Jauge et toutes tes données : dans l'application (Profil, Supprimer mon compte) "
         "ou par e-mail à privacy@jauge.io. Ce qui est effacé, ce qui est conservé, et sous quel délai."),
        ("cookies", "Cookies",
         "Le site jauge.io n'utilise aucun cookie ni aucun traceur : pas de mesure d'audience, pas de publicité, "
         "pas de bandeau."),
        ("support", "Support et contact",
         "Une question sur l'application Jauge ? Contact, réponses aux problèmes fréquents (offre illisible, lien non "
         "lisible, connexion, abonnement) et suppression de compte."),
        ("accessibilite", "Accessibilité",
         "Déclaration d'accessibilité du site jauge.io et de l'application Jauge : niveau visé WCAG 2.1 AA, état de "
         "conformité, défauts connus et contact."),
    ):
        body, body_modified = fragment(name)
        write(name, page(f"/{name}/", title, description, body, body_modified))


if __name__ == "__main__":
    main()
