#!/usr/bin/env python3
"""Generate the pre-race screen-coverage artifact from reference/screen-coverage.json.

Single source of truth -> self-contained HTML (CSP-safe: fonts inlined as data-URI, no external
requests). Output is written to the path given as argv[1] (default: scratchpad/coverage-map.html),
then published via the Artifact tool by the caller.
"""
import base64
import html
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "reference", "screen-coverage.json")
FONT = os.path.join(ROOT, "android", "app", "src", "main", "assets", "fonts", "UniversLTStd.otf")
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "scratchpad", "coverage-map.html")

doc = json.load(open(DATA))
screens = doc["screens"]
font_b64 = base64.b64encode(open(FONT, "rb").read()).decode()

# graph display order
ORDER = ["Root", "Garage", "OpenPlay", "Race", "Campaign", "Profile",
         "Account", "Store", "Tracks", "Singletons", "Settings"]
graphs = {}
for s in screens:
    graphs.setdefault(s["graph"], []).append(s)

n = len(screens)
counts = {k: sum(1 for s in screens if s["status"] == k) for k in ("matched", "partial", "wireframe")}
pct = {k: (counts[k] * 100.0 / n) for k in counts}

STATUS = {
    "matched":   ("MATCHED",   "var(--ok)"),
    "partial":   ("PARTIAL",   "var(--gold)"),
    "wireframe": ("WIREFRAME", "var(--bad)"),
}

def esc(x):  # safe text
    return html.escape(str(x or ""))

def row(s):
    label, color = STATUS[s["status"]]
    anchors = '<span class="ab on">ANCHORS</span>' if s.get("anchorsVerified") else '<span class="ab">anchors&nbsp;tbd</span>'
    note = esc(s["notes"])
    return f"""
    <div class="row" data-status="{s['status']}" style="--st:{color}">
      <div class="stripe"></div>
      <div class="rmain">
        <div class="rtop">
          <span class="rname">{esc(s['title'])}</span>
          <span class="pill" style="--p:{color}">{label}</span>
          {anchors}
        </div>
        <div class="rmeta">
          <span class="kv"><span class="k">layout</span><span class="v">{esc(s['layoutSource'])}</span></span>
          <span class="kv"><span class="k">assets</span><span class="v">{esc(s['assetSource'])}</span></span>
        </div>
        {f'<div class="note">{note}</div>' if note else ''}
      </div>
    </div>"""

groups_html = ""
present = [g for g in ORDER if g in graphs] + [g for g in graphs if g not in ORDER]
for g in present:
    rows = graphs[g]
    gc = {k: sum(1 for s in rows if s["status"] == k) for k in ("matched", "partial", "wireframe")}
    chips = "".join(
        f'<span class="mini" style="--p:{STATUS[k][1]}">{gc[k]}</span>'
        for k in ("matched", "partial", "wireframe") if gc[k]
    )
    open_attr = "open" if any(s["status"] != "matched" for s in rows) else ""
    groups_html += f"""
    <details class="grp" {open_attr}>
      <summary>
        <span class="caret">▸</span>
        <span class="gtitle">{esc(g)}</span>
        <span class="gcount">{len(rows)} screens</span>
        <span class="minis">{chips}</span>
      </summary>
      <div class="rows">{''.join(row(s) for s in rows)}</div>
    </details>"""

HTML = f"""<title>Pre-Race Screen Coverage — Overdrive</title>
<meta name="description" content="Asset + layout match status for every screen reachable before a race: matched / partial / wireframe, with 2.6 / 3.4 / 4.0.4 sourcing.">
<style>
@font-face{{font-family:'Univers';src:url('data:font/otf;base64,{font_b64}') format('opentype');font-display:block;}}
:root{{
  --void:#140A22; --card-1:#241A3A; --card-2:#180F2B; --line:#FFFFFF;
  --holo:#4FB0FF; --holo-cyan:#8FE0FF; --rose:#ECA9CE;
  --ink:#F4ECFA; --lav:#AE95C8; --lavdim:#7C6592;
  --ok:#49C56A; --gold:#E6B800; --bad:#E5484D;
}}
*{{box-sizing:border-box}}
.page{{font-family:ui-sans-serif,system-ui,-apple-system,Segoe UI,Roboto,sans-serif;
  color:var(--ink);background:var(--void);min-height:100vh;
  background-image:radial-gradient(120% 80% at 28% 18%,#5B2A8259,transparent 60%),
    radial-gradient(90% 70% at 80% 12%,#9B3FB833,transparent 55%),
    radial-gradient(140% 90% at 55% 100%,#2A124066,transparent 60%);}}
.wrap{{max-width:1020px;margin:0 auto;padding:34px 22px 80px}}
.disp{{font-family:'Univers','Arial Narrow',system-ui,sans-serif;font-style:italic;font-weight:700;
  text-transform:uppercase;letter-spacing:.01em;line-height:.95}}
.mono{{font-family:ui-monospace,'SF Mono',Menlo,monospace}}

/* hero / summary */
.tag{{font-family:ui-monospace,monospace;letter-spacing:.32em;text-transform:uppercase;font-size:11px;
  color:var(--holo-cyan);display:inline-flex;align-items:center;gap:9px;margin-bottom:14px}}
.dot{{width:7px;height:7px;border-radius:50%;background:var(--holo);
  box-shadow:0 0 10px var(--holo),0 0 20px var(--holo);animation:pulse 2.4s ease-in-out infinite}}
@keyframes pulse{{0%,100%{{opacity:.5;transform:scale(.85)}}50%{{opacity:1;transform:scale(1.15)}}}}
h1.disp{{font-size:clamp(30px,5.4vw,52px);margin:0 0 4px;color:var(--rose);
  text-shadow:0 0 34px #ec7fc04d;text-wrap:balance}}
.sub{{color:var(--lav);max-width:64ch;font-size:14.5px;line-height:1.55;margin:8px 0 26px}}
.sub b{{color:var(--ink);font-weight:600}}

.summary{{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-bottom:14px}}
.stat{{border:1px solid #ffffff14;border-radius:13px;padding:16px 18px;
  background:linear-gradient(180deg,#241a3acc,#180f2bcc);position:relative;overflow:hidden}}
.stat .num{{font-size:38px;font-variant-numeric:tabular-nums;line-height:1}}
.stat .lbl{{font-family:ui-monospace,monospace;letter-spacing:.18em;text-transform:uppercase;
  font-size:10.5px;color:var(--lav);margin-top:7px}}
.stat .pc{{position:absolute;right:14px;top:14px;font-variant-numeric:tabular-nums;font-size:12px;color:var(--lavdim)}}
.stat::after{{content:'';position:absolute;left:0;top:0;bottom:0;width:4px;background:var(--c)}}
.bar{{height:12px;border-radius:7px;overflow:hidden;display:flex;border:1px solid #ffffff14;margin-bottom:30px}}
.bar i{{display:block;height:100%}}

/* filters */
.filters{{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:20px}}
.filters button{{font-family:ui-monospace,monospace;font-size:11px;letter-spacing:.14em;text-transform:uppercase;
  color:var(--lav);background:#ffffff08;border:1px solid #ffffff1a;border-radius:20px;padding:7px 14px;cursor:pointer;
  transition:all .18s}}
.filters button:hover{{color:var(--ink);border-color:var(--holo)}}
.filters button[aria-pressed=true]{{color:var(--void);background:var(--holo-cyan);border-color:var(--holo-cyan);font-weight:700}}
.filters button:focus-visible{{outline:2px solid var(--holo-cyan);outline-offset:3px}}

/* groups */
.grp{{border:1px solid #ffffff14;border-radius:13px;background:#160d28cc;margin-bottom:12px;overflow:hidden}}
.grp>summary{{list-style:none;cursor:pointer;display:flex;align-items:center;gap:13px;padding:14px 18px}}
.grp>summary::-webkit-details-marker{{display:none}}
.grp>summary:hover{{background:#ffffff07}}
.caret{{color:var(--lav);transition:transform .2s;font-size:12px}}
.grp[open] .caret{{transform:rotate(90deg)}}
.gtitle{{font-family:'Univers','Arial Narrow',sans-serif;font-style:italic;font-weight:700;text-transform:uppercase;
  letter-spacing:.05em;font-size:18px}}
.gcount{{font-family:ui-monospace,monospace;font-size:11px;color:var(--lavdim);letter-spacing:.1em}}
.minis{{margin-left:auto;display:flex;gap:6px}}
.mini{{font-variant-numeric:tabular-nums;font-size:11px;font-weight:700;color:var(--void);background:var(--p);
  min-width:20px;text-align:center;border-radius:6px;padding:2px 6px}}
.rows{{border-top:1px solid #ffffff0d}}

/* row */
.row{{display:flex;gap:0;border-bottom:1px solid #ffffff0a;position:relative}}
.row:last-child{{border-bottom:0}}
.stripe{{width:4px;background:var(--st);flex:0 0 4px}}
.rmain{{padding:13px 18px;flex:1;min-width:0}}
.rtop{{display:flex;align-items:center;gap:10px;flex-wrap:wrap}}
.rname{{font-family:'Univers','Arial Narrow',sans-serif;font-style:italic;font-weight:700;text-transform:uppercase;
  letter-spacing:.02em;font-size:16px}}
.pill{{font-family:ui-monospace,monospace;font-size:9.5px;letter-spacing:.13em;font-weight:700;color:var(--void);
  background:var(--p);border-radius:5px;padding:3px 7px}}
.ab{{font-family:ui-monospace,monospace;font-size:9.5px;letter-spacing:.1em;color:var(--lavdim);
  border:1px solid #ffffff14;border-radius:5px;padding:2px 7px}}
.ab.on{{color:var(--holo-cyan);border-color:#4fb0ff55}}
.rmeta{{display:flex;flex-wrap:wrap;gap:8px 22px;margin-top:7px}}
.kv{{display:flex;gap:8px;align-items:baseline;min-width:0}}
.kv .k{{font-family:ui-monospace,monospace;font-size:9.5px;letter-spacing:.15em;text-transform:uppercase;
  color:var(--lavdim);flex:0 0 auto}}
.kv .v{{font-family:ui-monospace,monospace;font-size:11.5px;color:var(--lav);word-break:break-word}}
.note{{font-size:12px;color:var(--lavdim);margin-top:7px;line-height:1.45;max-width:74ch}}
.row.hide{{display:none}}

@media (prefers-reduced-motion:reduce){{*{{animation:none!important;transition:none!important}}}}
@media (max-width:560px){{.summary{{grid-template-columns:1fr}}.rmeta{{gap:6px}}}}
</style>

<div class="page"><div class="wrap">
  <div class="tag"><span class="dot"></span>Anki Overdrive · Rebuild · Pre-Race Logic Tree</div>
  <h1 class="disp">Screen Coverage</h1>
  <p class="sub">Every screen reachable <b>before a race</b>, and how faithfully each is matched to the
  original game. Assets are sourced <b>2.6 → 3.4 F&amp;F → 4.0.4</b>; layout structure is read from the
  fully-decompiled <b>3.4</b> C# controllers (the readable same-era proxy for 2.6) and rendered in the
  kept <b>4.0.4 nebula</b> skin. <b>{counts['wireframe']}</b> screens remain on wireframe.</p>

  <div class="summary">
    <div class="stat" style="--c:var(--ok)"><div class="num" style="color:var(--ok)">{counts['matched']}</div><div class="lbl">Matched</div><div class="pc">{pct['matched']:.0f}%</div></div>
    <div class="stat" style="--c:var(--gold)"><div class="num" style="color:var(--gold)">{counts['partial']}</div><div class="lbl">Partial</div><div class="pc">{pct['partial']:.0f}%</div></div>
    <div class="stat" style="--c:var(--bad)"><div class="num" style="color:var(--bad)">{counts['wireframe']}</div><div class="lbl">Wireframe</div><div class="pc">{pct['wireframe']:.0f}%</div></div>
  </div>
  <div class="bar">
    <i style="width:{pct['matched']}%;background:var(--ok)"></i>
    <i style="width:{pct['partial']}%;background:var(--gold)"></i>
    <i style="width:{pct['wireframe']}%;background:var(--bad)"></i>
  </div>

  <div class="filters" role="group" aria-label="Filter by status">
    <button data-f="all" aria-pressed="true">All · {n}</button>
    <button data-f="matched" aria-pressed="false">Matched · {counts['matched']}</button>
    <button data-f="partial" aria-pressed="false">Partial · {counts['partial']}</button>
    <button data-f="wireframe" aria-pressed="false">Wireframe · {counts['wireframe']}</button>
  </div>

  {groups_html}
</div></div>

<script>
(function(){{
  var btns=[].slice.call(document.querySelectorAll('.filters button'));
  var rows=[].slice.call(document.querySelectorAll('.row'));
  var grps=[].slice.call(document.querySelectorAll('.grp'));
  function apply(f){{
    btns.forEach(function(b){{b.setAttribute('aria-pressed', b.dataset.f===f);}});
    rows.forEach(function(r){{r.classList.toggle('hide', f!=='all' && r.dataset.status!==f);}});
    grps.forEach(function(g){{
      var any=g.querySelectorAll('.row:not(.hide)').length;
      g.style.display=any?'':'none';
      if(f!=='all'&&any) g.setAttribute('open','');
    }});
  }}
  btns.forEach(function(b){{b.addEventListener('click',function(){{apply(b.dataset.f);}});}});
}})();
</script>
"""

os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, "w").write(HTML)
print(f"wrote {OUT} ({len(HTML)} bytes) — {n} screens: "
      f"{counts['matched']} matched / {counts['partial']} partial / {counts['wireframe']} wireframe")
