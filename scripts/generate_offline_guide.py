#!/usr/bin/env python3
"""
Generates the offline bundled HTML user guide and optimized WebP images
for Telemetry Camera's in-app Help & Documentation viewer.
"""

import os
import re
import subprocess
from pathlib import Path
from PIL import Image

REPO_ROOT = Path(__file__).resolve().parent.parent
DOCS_SCREENSHOTS = REPO_ROOT / "docs" / "screenshots"
USERGUIDE_MD = REPO_ROOT / "userguide.md"
ASSETS_DIR = REPO_ROOT / "app" / "src" / "main" / "assets"
ASSETS_IMAGES = ASSETS_DIR / "guide" / "images"
OUTPUT_HTML = ASSETS_DIR / "userguide.html"

CSS_STYLES = """
:root {
    --bg-color: #121212;
    --card-bg: #1c2327;
    --border-color: #263843;
    --text-primary: #e0e8ec;
    --text-secondary: #90a4ae;
    --accent-cyan: #00e5ff;
    --accent-amber: #ffb300;
    --code-bg: #161b1e;
    --quote-bg: #10242e;
    --font-sans: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
    --font-mono: "SF Mono", "Fira Code", "Roboto Mono", monospace;
}

* {
    box-sizing: border-box;
}

body {
    background-color: var(--bg-color);
    color: var(--text-primary);
    font-family: var(--font-sans);
    line-height: 1.6;
    margin: 0;
    padding: 16px 20px 80px 20px;
    font-size: 15px;
    -webkit-text-size-adjust: 100%;
}

h1, h2, h3, h4, h5, h6 {
    color: #ffffff;
    font-weight: 600;
    line-height: 1.3;
    margin-top: 1.6em;
    margin-bottom: 0.5em;
    scroll-margin-top: 20px;
}

h1 {
    font-size: 1.7em;
    color: var(--accent-cyan);
    border-bottom: 2px solid var(--border-color);
    padding-bottom: 8px;
    margin-top: 0.2em;
}

h2 {
    font-size: 1.35em;
    color: var(--accent-cyan);
    border-bottom: 1px solid var(--border-color);
    padding-bottom: 6px;
}

h3 {
    font-size: 1.15em;
    color: #b2ebf2;
}

h4 {
    font-size: 1.02em;
    color: var(--accent-amber);
    text-transform: uppercase;
    letter-spacing: 0.5px;
}

h5 {
    font-size: 0.95em;
    color: #81d4fa;
}

p, ul, ol {
    margin: 0.7em 0;
}

ul, ol {
    padding-left: 22px;
}

li {
    margin: 0.35em 0;
}

a {
    color: var(--accent-cyan);
    text-decoration: none;
}

a:hover, a:active {
    text-decoration: underline;
}

hr {
    border: none;
    border-top: 1px solid var(--border-color);
    margin: 28px 0;
}

blockquote {
    margin: 1em 0;
    padding: 12px 16px;
    background-color: var(--quote-bg);
    border-left: 4px solid var(--accent-cyan);
    border-radius: 0 8px 8px 0;
    color: #eceff1;
}

blockquote p {
    margin: 0.3em 0;
}

code {
    background-color: var(--code-bg);
    color: #4dd0e1;
    font-family: var(--font-mono);
    font-size: 0.88em;
    padding: 2px 6px;
    border-radius: 4px;
    border: 1px solid #22323a;
}

pre {
    background-color: var(--code-bg);
    border: 1px solid var(--border-color);
    border-radius: 8px;
    padding: 14px;
    overflow-x: auto;
    margin: 1em 0;
}

pre code {
    background: none;
    color: #cfd8dc;
    border: none;
    padding: 0;
    font-size: 0.85em;
}

img {
    max-width: 100%;
    height: auto;
    border-radius: 8px;
    border: 1px solid var(--border-color);
    display: block;
    margin: 14px auto;
    background-color: #000000;
}

p align="center", div[align="center"] {
    text-align: center;
}

table {
    border-collapse: collapse;
    width: 100%;
    margin: 1em 0;
    font-size: 0.9em;
}

th, td {
    border: 1px solid var(--border-color);
    padding: 8px 12px;
    text-align: left;
}

th {
    background-color: #172b36;
    color: var(--accent-cyan);
}

tr:nth-child(even) {
    background-color: #151e23;
}

/* Floating Table of Contents Shortcut Button */
#toc-fab {
    position: fixed;
    bottom: 20px;
    right: 20px;
    background-color: #003740;
    color: var(--accent-cyan);
    border: 1px solid var(--accent-cyan);
    border-radius: 24px;
    padding: 8px 16px;
    font-size: 13px;
    font-weight: 600;
    letter-spacing: 0.5px;
    text-decoration: none;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.6);
    display: flex;
    align-items: center;
    gap: 6px;
    z-index: 1000;
    opacity: 0.92;
    transition: opacity 0.2s, transform 0.1s;
}

#toc-fab:active {
    opacity: 1;
    transform: scale(0.96);
}
"""

def compress_images():
    ASSETS_IMAGES.mkdir(parents=True, exist_ok=True)
    if not DOCS_SCREENSHOTS.exists():
        print(f"Screenshots directory not found: {DOCS_SCREENSHOTS}")
        return

    print("Compressing documentation screenshots to WebP...")
    for img_path in sorted(DOCS_SCREENSHOTS.glob("*.png")):
        out_path = ASSETS_IMAGES / f"{img_path.stem}.webp"
        with Image.open(img_path) as im:
            w, h = im.size
            if h > 1600:
                new_w = int(w * 1600 / h)
                im = im.resize((new_w, 1600), Image.Resampling.LANCZOS)
            im.save(out_path, "WEBP", quality=80, method=6)
        old_kb = img_path.stat().st_size / 1024
        new_kb = out_path.stat().st_size / 1024
        print(f"  {img_path.name} ({old_kb:.0f} KB) -> {out_path.name} ({new_kb:.0f} KB)")

def convert_markdown_to_html():
    print("Reading userguide.md...")
    with open(USERGUIDE_MD, "r", encoding="utf-8") as f:
        md_text = f.read()

    # Rewrite image paths to local assets webp
    md_text = re.sub(
        r'docs/screenshots/([a-zA-Z0-9_-]+)\.png',
        r'guide/images/\1.webp',
        md_text
    )

    # Use GitHub API if available for identical GFM rendering
    html_body = None
    try:
        res = subprocess.run(
            ["gh", "api", "/markdown", "-F", f"text={md_text}"],
            capture_output=True,
            text=True,
            check=True
        )
        html_body = res.stdout
        # Normalize GitHub's user-content- IDs so #slug anchors jump cleanly
        html_body = re.sub(r'id="user-content-([^"]+)"', r'id="\1"', html_body)
        print("Rendered with GitHub Flavored Markdown API.")
    except Exception as e:
        print(f"gh api unavailable ({e}), falling back to python-markdown...")
        import markdown
        html_body = markdown.markdown(
            md_text,
            extensions=["toc", "tables", "fenced_code", "nl2br"]
        )

    # Full standalone HTML document
    full_html = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
    <title>Telemetry Camera Field User Guide</title>
    <style>
{CSS_STYLES}
    </style>
</head>
<body>
<a href="#table-of-contents" id="toc-fab">📋 Table of Contents</a>
{html_body}
</body>
</html>
"""

    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    with open(OUTPUT_HTML, "w", encoding="utf-8") as f:
        f.write(full_html)

    size_kb = OUTPUT_HTML.stat().st_size / 1024
    print(f"Generated offline guide: {OUTPUT_HTML} ({size_kb:.1f} KB)")

if __name__ == "__main__":
    compress_images()
    convert_markdown_to_html()
    print("Done!")
