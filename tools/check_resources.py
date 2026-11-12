#!/usr/bin/env python3
"""Offline sanity check for the Android project (no SDK needed).

- Every XML file parses.
- Every @type/name reference in XML and every R.type.name reference in Java resolves to a
  resource defined in main/ (or the debug/release source set that uses it).
- Every theme defines the same color roles.
Usage: python3 tools/check_resources.py
"""
import os, re, sys, glob
import xml.etree.ElementTree as ET

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src")
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
errors = []

def defined(sourceset):
    d = {k: set() for k in ["string","plurals","color","dimen","style","attr","drawable","layout","menu","mipmap","xml","id"]}
    for ss in ("main", sourceset):
        res = os.path.join(ROOT, ss, "res")
        if not os.path.isdir(res): continue
        for f in glob.glob(res + "/**/*.xml", recursive=True):
            folder = os.path.basename(os.path.dirname(f)).split("-")[0]
            name = os.path.splitext(os.path.basename(f))[0]
            if folder in ("drawable","layout","menu","mipmap","xml"):
                d[folder].add(name)
            try:
                tree = ET.parse(f)
            except ET.ParseError as e:
                errors.append(f"XML parse error {f}: {e}"); continue
            for el in tree.iter():
                if folder == "values" and el.tag in d and el.get("name"):
                    d[el.tag].add(el.get("name"))
                for v in el.attrib.values():
                    for m in re.finditer(r"@\+id/(\w+)", v):
                        d["id"].add(m.group(1))
    return d

def check_xml_refs(sourceset, d):
    for ss in ("main", sourceset):
        for f in glob.glob(os.path.join(ROOT, ss, "res") + "/**/*.xml", recursive=True):
            text = open(f, encoding="utf-8").read()
            for t, n in re.findall(r"@(string|plurals|color|dimen|style|drawable|layout|menu|mipmap|xml|id)/([\w.]+)", text):
                if t == "style" and not re.match(r"(Base\.)?(Theme|Widget|TextAppearance|ShapeAppearance)\.DaySpark", n):
                    continue  # library style (Material / AppCompat)
                if n not in d[t]:
                    errors.append(f"{os.path.relpath(f, ROOT)}: missing @{t}/{n}")
            for n in re.findall(r"\?attr/(ds\w+)", text):
                if n not in d["attr"]:
                    errors.append(f"{os.path.relpath(f, ROOT)}: missing ?attr/{n}")
            for parent in re.findall(r'parent="(?:@style/)?(Base\.Theme\.DaySpark|Theme\.DaySpark[\w.]*|Widget\.DaySpark[\w.]*|TextAppearance\.DaySpark[\w.]*)"', text):
                if parent not in d["style"]:
                    errors.append(f"{os.path.relpath(f, ROOT)}: missing parent style {parent}")

def check_java_refs(sourceset, d):
    for ss in ("main", sourceset) + (("androidTest",) if sourceset == "debug" else ()):
        for f in glob.glob(os.path.join(ROOT, ss, "java") + "/**/*.java", recursive=True):
            text = open(f, encoding="utf-8").read()
            for t, n in re.findall(r"(?<![\w.])R\.(string|plurals|color|dimen|style|drawable|layout|menu|id|xml|mipmap)\.(\w+)", text):
                key = n.replace("_", ".") if t == "style" else n
                pool = d[t] if t != "style" else {s for s in d["style"]}
                if t == "style":
                    if key not in pool and n not in pool:
                        errors.append(f"{os.path.relpath(f, ROOT)}: missing R.style.{n}")
                elif n not in pool:
                    errors.append(f"{os.path.relpath(f, ROOT)}: missing R.{t}.{n}")

def check_theme_roles():
    colors = open(os.path.join(ROOT, "main/res/values/colors.xml")).read()
    names = re.findall(r'name="(\w+)"', colors)
    themes = ["dayspark","forest","ocean","lavender","sunset"]
    roles = {t: {n[len(t)+1:] for n in names if n.startswith(t + "_")} for t in themes}
    base = roles["dayspark"]
    for t in themes[1:]:
        if roles[t] != base:
            errors.append(f"theme {t} roles differ: {sorted(roles[t] ^ base)}")

for ss in ("debug", "release"):
    d = defined(ss)
    check_xml_refs(ss, d)
    check_java_refs(ss, d)
check_theme_roles()

errors = sorted(set(errors))
if errors:
    print("\n".join(errors)); print(f"\n{len(errors)} problem(s)"); sys.exit(1)
print("Resources OK: all XML parses, all references resolve, all themes define the same roles.")
