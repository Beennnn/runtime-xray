"""Shared drawing helpers — the logo-libres grammar, applied by hand.

Shapes, fills, strokes and the accent come from formes.json and ADR 0007;
the six layer colours from scripts/couches.json. Nothing here invents a value.
"""
import re, html, os

import pathlib
XRAY = str(pathlib.Path(__file__).resolve().parents[2])
LL   = os.environ.get('LOGO_LIBRES', str(pathlib.Path(XRAY).parent / 'logo-libres'))

FONT = "'IBM Plex Sans','Helvetica Neue',Arial,sans-serif"
MONO = "'IBM Plex Mono','SFMono-Regular',Menlo,Consolas,monospace"

INK      = '#16181A'
MUTED    = '#5B6873'
FAINT    = '#8896A2'
STROKE   = '#3E444A'
ACCENT   = '#A3196F'

FORMES = {
    'service':     dict(rx=0,  fill='#FFFFFF', stroke=STROKE, w=1.6),
    'application': dict(rx=10, fill='#FFFFFF', stroke=STROKE, w=1.6),
    'stockage':    dict(cyl=True, fill='#FFFFFF', stroke=STROKE, w=1.6),
    'flux':        dict(rx='half', fill='#FFFFFF', stroke=STROKE, w=1.6),
    'acteur':      dict(rx=10, fill='#E8ECEF', stroke=STROKE, w=1.6),
    'externe':     dict(rx=8,  fill='#F3F5F6', stroke=FAINT, w=1.6, dash='5 4'),
    'zone':        dict(rx=14, fill='#F2F5F6', stroke=FAINT, w=1.4, dash='8 6'),
    'zone2':       dict(rx=14, fill='#E7EBEE', stroke=FAINT, w=1.4, dash='8 6'),
    'noeud':       dict(rx=2,  fill='#DFE5E9', stroke=STROKE, w=2.4),
}

COUCHES = {
    'api':       ('API and code',   '#7038D8'),
    'fichiers':  ('Files',          '#0B7A6E'),
    'infra':     ('Infrastructure', '#4C5A66'),
    'messaging': ('Messaging',      '#B36208'),
    'web':       ('Web',            '#1B5FD9'),
    'acces':     ('Access',         '#C0392F'),
}

def esc(t):
    return html.escape(str(t), quote=False)

def symbol(path, x, y, size):
    """Nest a self-contained badge SVG at (x, y), scaled to `size`."""
    raw = open(path, encoding='utf-8').read()
    vb = re.search(r'viewBox="([^"]+)"', raw).group(1)
    inner = raw[raw.index('>', raw.index('<svg')) + 1: raw.rindex('</svg>')]
    inner = re.sub(r'<title>.*?</title>', '', inner, flags=re.S)
    return (f'<svg x="{x}" y="{y}" width="{size}" height="{size}" '
            f'viewBox="{vb}" overflow="visible">{inner}</svg>')

def box(x, y, w, h, forme, accent=False):
    f = FORMES[forme]
    stroke = ACCENT if accent else f['stroke']
    width  = 3.2 if accent else f['w']
    dash   = f' stroke-dasharray="{f["dash"]}"' if 'dash' in f else ''
    if f.get('cyl'):
        ry = min(12, h / 5)
        return (f'<g><path d="M{x} {y+ry} v{h-2*ry} a{w/2} {ry} 0 0 0 {w} 0 v{-(h-2*ry)}" '
                f'fill="{f["fill"]}" stroke="{stroke}" stroke-width="{width}"/>'
                f'<ellipse cx="{x+w/2}" cy="{y+ry}" rx="{w/2}" ry="{ry}" '
                f'fill="{f["fill"]}" stroke="{stroke}" stroke-width="{width}"/></g>')
    rx = h / 2 if f['rx'] == 'half' else f['rx']
    return (f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" '
            f'fill="{f["fill"]}" stroke="{stroke}" stroke-width="{width}"{dash}/>')

def text(x, y, s, size=13, fill=INK, weight=400, anchor='start', mono=False, op=1, halo=False):
    fam = f' font-family="{MONO}"' if mono else ''
    o = f' opacity="{op}"' if op != 1 else ''
    # A white halo lets a label cross a zone border without the dashes running through it.
    h = ' stroke="#FFFFFF" stroke-width="3.5" paint-order="stroke" stroke-linejoin="round"' if halo else ''
    return (f'<text x="{x}" y="{y}" font-size="{size}" fill="{fill}" '
            f'font-weight="{weight}" text-anchor="{anchor}"{fam}{o}{h}>{esc(s)}</text>')

def arrow(d, label=None, sub=None, lx=None, ly=None, dashed=False, anchor='middle'):
    dash = ' stroke-dasharray="6 5"' if dashed else ''
    out = (f'<path d="{d}" fill="none" stroke="{FAINT}" stroke-width="1.5" '
           f'marker-end="url(#fl)"{dash}/>')
    if label:
        out += text(lx, ly, label, 11, MUTED, anchor=anchor)
    if sub:
        out += text(lx, ly + 13, sub, 10, FAINT, anchor=anchor, mono=True)
    return out

def head(w, h, title, subtitle, aria):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" '
            f'viewBox="0 0 {w} {h}" font-family="{FONT}" role="img" '
            f'aria-label="{esc(aria)}">\n'
            f'<title>{esc(title)}</title>\n'
            f'<defs><marker id="fl" viewBox="0 0 10 10" refX="9" refY="5" '
            f'markerWidth="7" markerHeight="7" orient="auto-start-reverse">'
            f'<path d="M0,1 L9,5 L0,9" fill="none" stroke="{FAINT}" stroke-width="1.6" '
            f'stroke-linecap="round" stroke-linejoin="round"/></marker></defs>\n'
            f'<rect width="{w}" height="{h}" fill="#FFFFFF"/>\n'
            + text(40, 46, title, 19, INK, 700)
            + text(40, 68, subtitle, 12.5, MUTED))

def legend_formes(x, y, items):
    out = [text(x, y + 12, 'SHAPES', 10, FAINT, 600, mono=True)]
    cx = x + 64
    for forme, label in items:
        out.append(box(cx, y, 26, 16, forme))
        out.append(text(cx + 32, y + 12, label, 10.5, MUTED))
        cx += 32 + len(label) * 6.2 + 26
    return ''.join(out), cx

def legend_couleurs(x, y, keys):
    out = [text(x, y + 10, 'COLOURS', 10, FAINT, 600, mono=True)]
    cx = x + 64
    for k in keys:
        label, colour = COUCHES[k]
        out.append(f'<rect x="{cx}" y="{y}" width="12" height="12" rx="3" fill="{colour}"/>')
        out.append(text(cx + 18, y + 10, label, 10.5, MUTED))
        cx += 18 + len(label) * 6.2 + 24
    return ''.join(out), cx
