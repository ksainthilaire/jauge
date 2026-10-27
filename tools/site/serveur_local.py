#!/usr/bin/env python3
"""Serveur local d'essai du site (port 8812) qui imite Cloudflare Pages : dossier web/, réécriture /a/* vers
web/a/_index.html, page 404.html, barre finale ajoutée aux dossiers, apple-app-site-association en application/json.

    python3 tools/site/serveur_local.py [port]
"""
import http.server
import pathlib
import re
import sys

WEB = pathlib.Path(__file__).resolve().parents[2] / "web"
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8812


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(WEB), **kwargs)

    def do_GET(self):
        path = self.path.split("?")[0]
        if re.fullmatch(r"/a/[^/]+/?", path):
            self.path = "/a/_index.html"
        elif path != "/" and not path.endswith("/") and (WEB / path.lstrip("/")).is_dir():
            self.send_response(301)
            self.send_header("Location", path + "/")
            self.end_headers()
            return
        elif not (WEB / path.lstrip("/")).exists() and not (WEB / path.lstrip("/") / "index.html").exists():
            self.send_response(404)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write((WEB / "404.html").read_bytes())
            return
        super().do_GET()

    def guess_type(self, path):
        if path.endswith("apple-app-site-association"):
            return "application/json"
        if path.endswith(".webmanifest"):
            return "application/manifest+json"
        return super().guess_type(path)

    def log_message(self, format, *args):
        pass


if __name__ == "__main__":
    print(f"http://127.0.0.1:{PORT}/ (dossier {WEB})")
    http.server.ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
