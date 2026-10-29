#!/usr/bin/env python3
"""Converts the launcher icon (adaptive-icon foreground + gradient background) to an SVG.

Used to regenerate docs/images/logo.svg after changing the icon, and to preview the icon
without a device:

    python3 scripts/icon_to_svg.py > docs/images/logo.svg
    python3 scripts/icon_to_svg.py --mask circle > /tmp/icon_round.svg
"""
import argparse
import os
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
RES = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "drawable")


def color(value):
    """#AARRGGBB -> (#RRGGBB, alpha)."""
    v = value.lstrip("#")
    if len(v) == 6:
        return "#" + v, 1.0
    return "#" + v[2:], round(int(v[:2], 16) / 255, 3)


def attr(el, name, default=None):
    return el.get(ANDROID + name, default)


def convert(el):
    if el.tag == "group":
        px, py = float(attr(el, "pivotX", 0)), float(attr(el, "pivotY", 0))
        sx, sy = float(attr(el, "scaleX", 1)), float(attr(el, "scaleY", 1))
        rot = float(attr(el, "rotation", 0))
        tx, ty = float(attr(el, "translateX", 0)), float(attr(el, "translateY", 0))
        # Android: scale and rotate about the pivot, then translate.
        transform = f"translate({tx} {ty}) translate({px} {py}) rotate({rot}) scale({sx} {sy}) translate({-px} {-py})"
        return f'<g transform="{transform}">' + "".join(convert(c) for c in el) + "</g>"
    if el.tag == "path":
        out = f'<path d="{attr(el, "pathData")}"'
        fill = attr(el, "fillColor")
        if fill:
            c, a = color(fill)
            out += f' fill="{c}"' + (f' fill-opacity="{a}"' if a < 1 else "")
        else:
            out += ' fill="none"'
        stroke = attr(el, "strokeColor")
        if stroke:
            c, a = color(stroke)
            out += f' stroke="{c}"' + (f' stroke-opacity="{a}"' if a < 1 else "")
            out += f' stroke-width="{attr(el, "strokeWidth", 1)}"'
            out += f' stroke-linecap="{attr(el, "strokeLineCap", "butt")}" stroke-linejoin="{attr(el, "strokeLineJoin", "miter")}"'
        return out + "/>"
    return ""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mask", choices=["squircle", "circle"], default="squircle")
    parser.add_argument("--size", type=int, default=256)
    args = parser.parse_args()

    fg = ET.parse(os.path.join(RES, "ic_launcher_foreground.xml")).getroot()
    body = "".join(convert(c) for c in fg)
    shape = (
        '<circle cx="54" cy="54" r="36"/>' if args.mask == "circle"
        else '<rect x="18" y="18" width="72" height="72" rx="17"/>'
    )
    print(
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{args.size}" height="{args.size}" viewBox="18 18 72 72">'
        '<defs><linearGradient id="g" gradientUnits="userSpaceOnUse" x1="20" y1="10" x2="88" y2="98">'
        '<stop offset="0" stop-color="#5B6CFF"/><stop offset="1" stop-color="#3A2FB8"/></linearGradient>'
        f'<clipPath id="m">{shape}</clipPath></defs>'
        f'<g clip-path="url(#m)"><rect x="0" y="0" width="108" height="108" fill="url(#g)"/>{body}</g></svg>'
    )


if __name__ == "__main__":
    main()
