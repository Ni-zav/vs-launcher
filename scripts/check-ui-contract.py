#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SURFACE = ROOT / "app/src/main/java/com/vslauncher/LauncherSurface.java"
MAIN = ROOT / "app/src/main/java/com/vslauncher/MainActivity.java"
TOKENS = ROOT / "app/src/main/java/com/vslauncher/DesignTokens.java"
APP_GRADLE = ROOT / "app/build.gradle"
MAIN_SRC = ROOT / "app/src/main/java"

errors = []


def method_body(source: str, name: str) -> str:
    match = re.search(
        rf"(?:@Override\s+)?(?:public|private|protected)?\s*(?:static\s+)?[\w<>\[\]]+\s+{re.escape(name)}\s*\([^)]*\)\s*\{{",
        source,
    )
    if not match:
        errors.append(f"Could not find method {name}")
        return ""
    start = match.end() - 1
    depth = 0
    for i in range(start, len(source)):
        ch = source[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return source[start : i + 1]
    errors.append(f"Could not parse method body for {name}")
    return ""


surface = SURFACE.read_text(encoding="utf-8")
main = MAIN.read_text(encoding="utf-8")
tokens = TOKENS.read_text(encoding="utf-8")
gradle = APP_GRADLE.read_text(encoding="utf-8")

hot_methods = [
    "onDraw",
    "drawPage",
    "drawHome",
    "drawHomeRows",
    "drawBattery",
    "drawApps",
    "drawAppRows",
    "drawBrowseRows",
    "drawAlphabetRail",
    "drawSettings",
    "drawSettingsRow",
]

for method in hot_methods:
    body = method_body(surface, method)
    for forbidden in ("new ", ".measureText(", "dp(", "sp(", "getResources("):
        if forbidden in body:
            errors.append(f"{method} contains draw-time forbidden token: {forbidden}")

for forbidden in ("SharedPreferences", "PackageManager", "LauncherApps", "launcherPreferences"):
    if forbidden in surface:
        errors.append(f"LauncherSurface must not depend on {forbidden}")

text_changed = method_body(main, "onTextChanged")
if "launcherPreferences.aliases()" in text_changed or "getAll()" in text_changed:
    errors.append("Search TextWatcher must not read SharedPreferences aliases")

manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
if "android.permission.QUERY_ALL_PACKAGES" in manifest:
    errors.append("Launcher must not request QUERY_ALL_PACKAGES")
if "android.permission.ACCESS_HIDDEN_PROFILES" not in manifest:
    errors.append("Private Space support requires ACCESS_HIDDEN_PROFILES")

color_calls = set(re.findall(r"Color\.([A-Za-z0-9_]+)", tokens))
unexpected_colors = color_calls - {"BLACK", "WHITE"}
if unexpected_colors:
    errors.append(f"DesignTokens uses non-binary Color APIs/constants: {sorted(unexpected_colors)}")

if "#000000" not in tokens or "#FFFFFF" not in tokens:
    errors.append("DesignTokens strict black/white contract comment is missing")

production_text = gradle
for path in MAIN_SRC.rglob("*.java"):
    production_text += "\n" + path.read_text(encoding="utf-8")

for forbidden in (
    "androidx.compose",
    "RecyclerView",
    "androidx.recyclerview",
):
    if forbidden in production_text:
        errors.append(f"Production UI must remain framework-light; found {forbidden}")

for required in ('"HOME"', '"STATUS"', '"GESTURES"', '"APPS"', '"DATA"'):
    if required not in surface:
        errors.append(f"Missing Settings section label {required}")

if errors:
    print("UI/performance contract FAILED:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    sys.exit(1)

print("UI/performance contract passed.")
print(f"Checked draw hot paths: {', '.join(hot_methods)}")
print("Palette: strict black/white")
print("Production UI: no Compose/RecyclerView")
