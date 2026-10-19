#!/usr/bin/env python3
"""Generator glyph ikon SP Launcher: menghasilkan VectorDrawable (res/drawable/sym_*.xml) + sheet pratinjau SVG/HTML.
Semua glyph digambar pada kanvas 24x24, putih; diwarnai ulang lewat Drawable.setTint()."""
import math, sys, os

def f(n): 
    s = ("%.2f" % n).rstrip("0").rstrip(".")
    return s if s not in ("-0", "") else "0"

def circle(cx, cy, r):
    return "M%s,%s a%s,%s 0 1,0 %s,0 a%s,%s 0 1,0 %s,0 Z" % (f(cx - r), f(cy), f(r), f(r), f(2 * r), f(r), f(r), f(-2 * r))

def rrect(x, y, w, h, r):
    r = min(r, w / 2, h / 2)
    return ("M%s,%s H%s A%s,%s 0 0 1 %s,%s V%s A%s,%s 0 0 1 %s,%s H%s A%s,%s 0 0 1 %s,%s V%s A%s,%s 0 0 1 %s,%s Z" % (
        f(x + r), f(y), f(x + w - r), f(r), f(r), f(x + w), f(y + r), f(y + h - r), f(r), f(r), f(x + w - r), f(y + h),
        f(x + r), f(r), f(r), f(x), f(y + h - r), f(y + r), f(r), f(r), f(x + r), f(y)))

def polar(cx, cy, r, deg):
    a = math.radians(deg)
    return cx + r * math.cos(a), cy + r * math.sin(a)

def arcpath(cx, cy, r, a0, a1):
    """busur dari sudut a0 ke a1 (derajat, 0 = kanan, searah jarum jam positif karena sumbu y ke bawah)."""
    x0, y0 = polar(cx, cy, r, a0); x1, y1 = polar(cx, cy, r, a1)
    large = 1 if abs(a1 - a0) > 180 else 0
    sweep = 1 if a1 > a0 else 0
    return "M%s,%s A%s,%s 0 %d %d %s,%s" % (f(x0), f(y0), f(r), f(r), large, sweep, f(x1), f(y1))

def gear(cx, cy, teeth, ro, rr, hole, tw_out=8, tw_root=15):
    d = ""
    step = 360.0 / teeth
    for i in range(teeth):
        a = i * step - 90
        p = [polar(cx, cy, rr, a - tw_root), polar(cx, cy, ro, a - tw_out), polar(cx, cy, ro, a + tw_out), polar(cx, cy, rr, a + tw_root)]
        if i == 0: d += "M%s,%s " % (f(p[0][0]), f(p[0][1]))
        else: d += "A%s,%s 0 0 1 %s,%s " % (f(rr), f(rr), f(p[0][0]), f(p[0][1]))
        d += "L%s,%s L%s,%s L%s,%s " % (f(p[1][0]), f(p[1][1]), f(p[2][0]), f(p[2][1]), f(p[3][0]), f(p[3][1]))
    p0 = polar(cx, cy, rr, -90 - tw_root)
    d += "A%s,%s 0 0 1 %s,%s Z " % (f(rr), f(rr), f(p0[0]), f(p0[1]))
    d += circle(cx, cy, hole)
    return d

def rays(cx, cy, r0, r1, n, off=0):
    d = ""
    for i in range(n):
        x0, y0 = polar(cx, cy, r0, off + i * 360.0 / n); x1, y1 = polar(cx, cy, r1, off + i * 360.0 / n)
        d += "M%s,%s L%s,%s " % (f(x0), f(y0), f(x1), f(y1))
    return d.strip()

def arrowhead(cx, cy, r, a, size=3.6, spread=2.8):
    """kepala panah (chevron) di titik busur sudut a, mengarah searah jarum jam."""
    tx, ty = polar(cx, cy, r, a)
    dx, dy = -math.sin(math.radians(a)), math.cos(math.radians(a))
    nx, ny = -dy, dx
    bx, by = tx - dx * size, ty - dy * size
    w1 = (bx + nx * spread, by + ny * spread); w2 = (bx - nx * spread, by - ny * spread)
    return "M%s,%s L%s,%s L%s,%s" % (f(w1[0]), f(w1[1]), f(tx), f(ty), f(w2[0]), f(w2[1]))

def wifi():
    cx, cy = 12, 19.2
    els = [("f", circle(cx, cy - 0.2, 1.5))]
    for r in (5, 9, 13):
        els.append(("s", arcpath(cx, cy, r, -135, -45), 2.3))
    return els

G = {}
G["wifi"] = wifi()
G["bluetooth"] = [("s", "M6.6,7.6 L17.2,16.6 L12,21 L12,3 L17.2,7.4 L6.6,16.4", 2.1)]
G["airplane"] = [("f", "M12,2.4 C12.95,2.4 13.7,3.15 13.7,4.1 V9.1 L21.2,13.6 V15.9 L13.7,13.7 V18.3 L16.2,20.1 V21.7 L12,20.5 L7.8,21.7 V20.1 L10.3,18.3 V13.7 L2.8,15.9 V13.6 L10.3,9.1 V4.1 C10.3,3.15 11.05,2.4 12,2.4 Z")]
G["cellular"] = [("f", rrect(3, 16, 3.2, 5, 1)), ("f", rrect(8.2, 12, 3.2, 9, 1)), ("f", rrect(13.4, 8, 3.2, 13, 1)), ("f", rrect(18.6, 3, 3.2, 18, 1))]
G["flashlight"] = [("fe", "M8,2.8 H16 V6.4 L14.4,9 V19 A2.2,2.2 0 0 1 12.2,21.2 H11.8 A2.2,2.2 0 0 1 9.6,19 V9 L8,6.4 Z " + circle(12, 13.2, 1.3) + " M8,5 H16")]
G["moon"] = [("f", "M20.6,13.4 A9,9 0 1 1 10.6,3.4 A7.2,7.2 0 0 0 20.6,13.4 Z")]
G["rotation_lock"] = [("s", arcpath(12, 12, 8.8, -40, 215), 2.1), ("s", arrowhead(12, 12, 8.8, 215), 2.1),
                      ("f", rrect(8.8, 11.2, 6.4, 5.2, 1.3)), ("s", "M10.2,11.2 V9.9 A1.8,1.8 0 0 1 13.8,9.9 V11.2", 1.7)]
G["brightness"] = [("f", circle(12, 12, 4.1)), ("s", rays(12, 12, 7.2, 9.6, 8, 0), 2.1)]
G["volume"] = [("f", "M3.8,9.6 H7.6 L12.4,5.6 V18.4 L7.6,14.4 H3.8 Z"), ("s", "M15.6,9.2 A4,4 0 0 1 15.6,14.8", 2.0), ("s", "M18.4,6.4 A8,8 0 0 1 18.4,17.6", 2.0)]
G["timer"] = [("s", circle(12, 13.6, 7.8), 2.1), ("s", "M9.6,2.8 H14.4 M12,2.8 V5.8", 2.1), ("s", "M12,13.6 L15.2,10.4", 2.1)]
G["calculator"] = [("s", rrect(4.5, 2.8, 15, 18.4, 3.2), 2.1), ("f", rrect(7.4, 5.8, 9.2, 3.4, 1)),
                   ("f", circle(8.6, 12.9, 1.2)), ("f", circle(12, 12.9, 1.2)), ("f", circle(15.4, 12.9, 1.2)),
                   ("f", circle(8.6, 16.9, 1.2)), ("f", circle(12, 16.9, 1.2)), ("f", circle(15.4, 16.9, 1.2))]
G["camera"] = [("fe", rrect(2.6, 6.6, 18.8, 13.6, 3.6) + " " + circle(12, 13.4, 4.4)), ("f", circle(12, 13.4, 2.5)),
               ("f", "M8.2,6.6 L9.5,3.8 H14.5 L15.8,6.6 Z")]
G["gear"] = [("fe", gear(12, 12, 8, 9.8, 7.6, 3.3))]
G["play"] = [("f", "M8,5.4 L18.6,12 L8,18.6 Z"), ("s", "M8,5.4 L18.6,12 L8,18.6 Z", 1.6)]
G["pause"] = [("f", rrect(6.2, 4.8, 4, 14.4, 1.3)), ("f", rrect(13.8, 4.8, 4, 14.4, 1.3))]
G["prev"] = [("f", rrect(4.6, 5.2, 2.6, 13.6, 1.1)), ("f", "M19.4,5.6 L9,12 L19.4,18.4 Z"), ("s", "M19.4,5.6 L9,12 L19.4,18.4 Z", 1.4)]
G["next"] = [("f", rrect(16.8, 5.2, 2.6, 13.6, 1.1)), ("f", "M4.6,5.6 L15,12 L4.6,18.4 Z"), ("s", "M4.6,5.6 L15,12 L4.6,18.4 Z", 1.4)]
G["music"] = [("f", "M9.6,5.9 L19.4,3.8 V8 L9.6,10.1 Z"), ("s", "M9.6,17 V6.2 M19.4,14.9 V4", 2.0),
              ("f", "M7.3,14.8 A3,2.6 0 1 0 7.31,14.8 Z"), ("f", "M17.1,12.7 A3,2.6 0 1 0 17.11,12.7 Z")]
G["mail"] = [("s", rrect(2.9, 5.2, 18.2, 13.6, 3.2), 2.1), ("s", "M5.2,8.4 L12,13.4 L18.8,8.4", 2.1)]
G["calendar"] = [("s", rrect(3.6, 5, 16.8, 16, 3.4), 2.1), ("s", "M3.6,10.2 H20.4 M8,2.8 V6.6 M16,2.8 V6.6", 2.1),
                 ("f", rrect(7, 13, 3, 3, 0.8)), ("f", rrect(14, 13, 3, 3, 0.8)), ("f", rrect(7, 17.2, 3, 2, 0.8))]
G["notes"] = [("s", rrect(4.6, 3, 14.8, 18, 3.2), 2.1), ("s", "M8.5,9 H15.5 M8.5,13 H15.5 M8.5,17 H12.5", 2.0)]
G["folder"] = [("f", "M3,7.6 A2.6,2.6 0 0 1 5.6,5 H9.6 L12.2,7.6 H18.4 A2.6,2.6 0 0 1 21,10.2 V17.4 A2.6,2.6 0 0 1 18.4,20 H5.6 A2.6,2.6 0 0 1 3,17.4 Z")]
G["person"] = [("f", circle(12, 8, 4)), ("f", "M4.6,20.4 C4.6,16.2 8,13.8 12,13.8 S19.4,16.2 19.4,20.4 Z")]
G["compass"] = [("s", circle(12, 12, 9), 2.0), ("f", "M16,8 L13.7,13.7 L8,16 L10.3,10.3 Z")]
G["weather"] = [("f", "M7.4,19.6 A4,4 0 0 1 6.6,11.8 A5.5,5.5 0 0 1 16.9,11.1 A4.2,4.2 0 0 1 17,19.6 Z"), ("f", circle(17.6, 6.6, 2.6)),
                ("s", rays(17.6, 6.6, 4.3, 5.3, 8, 0), 1.4)]
G["recorder"] = [("f", rrect(9, 2.8, 6, 11.4, 3)), ("s", "M5.8,11.2 A6.2,6.2 0 0 0 18.2,11.2", 2.0), ("s", "M12,17.6 V21 M9,21 H15", 2.0)]
G["download"] = [("s", "M12,4 V14.6 M7.4,10.4 L12,15 L16.6,10.4 M5,19.4 H19", 2.2)]
G["globe"] = [("s", circle(12, 12, 9), 2.0), ("s", "M12,3 C14.6,5.4 15.8,8.6 15.8,12 C15.8,15.4 14.6,18.6 12,21 C9.4,18.6 8.2,15.4 8.2,12 C8.2,8.6 9.4,5.4 12,3 Z", 1.8), ("s", "M3,12 H21", 1.8)]
G["pin"] = [("fe", "M12,21.6 C12,21.6 5,15 5,9.8 A7,7 0 0 1 19,9.8 C19,15 12,21.6 12,21.6 Z " + circle(12, 9.8, 2.7))]
G["phone"] = [("s", "M6.8,6.8 Q6.8,17.2 17.2,17.2", 4.4), ("f", rrect(3.2, 3.2, 7.2, 3.8, 1.7)), ("f", rrect(17, 13.6, 3.8, 7.2, 1.7))]
G["messages"] = [("f", "M12,3.6 C6.9,3.6 2.8,7.1 2.8,11.5 C2.8,14.1 4.2,16.3 6.4,17.7 C6.2,18.9 5.5,20.1 4.5,21 C6.6,20.9 8.3,20.2 9.5,19.2 C10.3,19.4 11.1,19.5 12,19.5 C17.1,19.5 21.2,16 21.2,11.5 C21.2,7.1 17.1,3.6 12,3.6 Z")]
G["photo"] = [("s", rrect(3.2, 4.6, 17.6, 14.8, 3.4), 2.0), ("f", circle(8.6, 10, 1.9)), ("s", "M3.8,17.2 L9.2,12.4 L13,16 L15.8,13.4 L20.2,17.6", 2.0)]
G["clock"] = [("s", circle(12, 12, 9), 2.1), ("s", "M12,6.6 V12 L15.6,14.2", 2.1)]
G["plane"] = [("f", "M21.2,3.2 L2.8,10.6 L8.8,13 L17.8,6.8 L10.4,14.4 L10.6,20.8 L13.8,16.8 L18.4,20 Z")]
G["plus"] = [("s", "M12,5 V19 M5,12 H19", 2.4)]
G["xmark"] = [("s", "M6.4,6.4 L17.6,17.6 M17.6,6.4 L6.4,17.6", 2.4)]
G["bell"] = [("f", "M12,3 C8.6,3 7,5.7 7,9 V12 L5,16 H19 L17,12 V9 C17,5.7 15.4,3 12,3 Z"), ("s", "M9.8,18.6 A2.4,2.4 0 0 0 14.2,18.6", 1.9)]
G["check"] = [("s", "M5,12.6 L10,17.4 L19,7", 2.6)]
G["lock"] = [("f", rrect(5.5, 10.4, 13, 10, 2.6)), ("s", "M8.4,10.4 V8 A3.6,3.6 0 0 1 15.6,8 V10.4", 2.1)]
G["gallery"] = G["photo"]

def vector_xml(els, name):
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- generated: tools/glyphs.py (%s) -->' % name,
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">']
    for e in els:
        if e[0] in ("f", "fe"):
            ft = ' android:fillType="evenOdd"' if e[0] == "fe" else ""
            out.append('    <path android:fillColor="#FFFFFFFF"%s android:pathData="%s"/>' % (ft, e[1]))
        else:
            out.append('    <path android:strokeColor="#FFFFFFFF" android:strokeWidth="%s" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="%s"/>' % (f(e[2]), e[1]))
    out.append('</vector>')
    return "\n".join(out) + "\n"

def svg_inner(els, color="#fff"):
    out = []
    for e in els:
        if e[0] in ("f", "fe"):
            out.append('<path fill="%s" %s d="%s"/>' % (color, 'fill-rule="evenodd"' if e[0] == "fe" else "", e[1]))
        else:
            out.append('<path fill="none" stroke="%s" stroke-width="%s" stroke-linecap="round" stroke-linejoin="round" d="%s"/>' % (color, f(e[2]), e[1]))
    return "".join(out)

if __name__ == "__main__":
    mode = sys.argv[1]
    if mode == "xml":
        outdir = sys.argv[2]
        os.makedirs(outdir, exist_ok=True)
        names = [n for n in G if n != "gallery"]
        for n in names:
            open(os.path.join(outdir, "sym_%s.xml" % n), "w").write(vector_xml(G[n], n))
        print("wrote", len(names), "glyphs ->", outdir)
    elif mode == "sheet":
        # sheet 1: glyph putih di atas kotak gelap
        cells = []
        for n, els in G.items():
            if n == "gallery": continue
            cells.append('<div class="c"><svg viewBox="0 0 24 24" width="72" height="72">%s</svg><span>%s</span></div>' % (svg_inner(els), n))
        html = "<html><body style='margin:0;background:#1c1c1e;color:#aaa;font:11px sans-serif'><style>.c{display:inline-block;width:96px;text-align:center;margin:6px}.c svg{background:#2c2c2e;border-radius:14px}</style>" + "".join(cells) + "</body></html>"
        open(sys.argv[2], "w").write(html)
