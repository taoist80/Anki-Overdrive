# Design artifacts (claude.ai) — index

Published visual artifacts that back the rebuild's screens. They're hosted on claude.ai and persist
across conversations: any session can open the URL, `WebFetch` it to re-read the spec, or redeploy to it
(`Artifact` tool with `url=`). They're private to the account; *sharing with teammates* is a separate
claude.ai UI action (these links make them cross-**conversation**, not public).

| Artifact | URL | What it is | Build reflected | Status |
|---|---|---|---|---|
| **Restyle design system** | https://claude.ai/code/artifact/ea0e5fa4-88c1-484a-aff0-10df85042a6e | The master 4.0.4 visual system: palette, type, nav logic-tree, screen mocks, motion. Root spec everything else hangs off. | 4.0.4 | current |
| **In-race HUD spec** | https://claude.ai/code/artifact/c724d253-c360-4132-a0b9-8d33ba464466 | 1:1 in-race HUD reconstructed from 4.0.4 screenshots (holo bays, full-button weapon/support, health/energy). | 4.0.4 | current — built (commit 2db274a) |
| **Profile art gallery** | https://claude.ai/code/artifact/772f16a2-77a6-4443-a7ca-8eee482c4f0f | Authentic 2.6 avatars, medals, and all 27 commander portraits (the generated + extracted set). | 2.6 | current |
| **Store mockup** | https://claude.ai/code/artifact/a7f678b4-a11c-4996-b10e-40f0f3b2274b | 2.6 store: dual currency, categories, price tables. | 2.6 | current — build-out pending |
| **Campaign mockup v2** | https://claude.ai/code/artifact/4455854b-7d08-4d47-a247-770f6bb0217d | Authentic 2.6 campaign: 3×2 chapter grid, vertical road-spline mission rail, real titles/briefings. | 2.6 | current — built |
| **Campaign mockup v1** | https://claude.ai/code/artifact/f28b0362-48fa-4c31-ac86-cccc861ef143 | First campaign pass. | 2.6 | superseded by v2 |
| **Startup / Victory / Results** | https://claude.ai/code/artifact/2ee8eb61-a4a8-4e9b-8397-2c13e7ae02fa | 2.6 victory/defeat + results (loot) end screens and the 4.0.4 startup sequence. | 2.6 + 4.0.4 | current — built |

To rebuild any of these from source: the HTML lives in this session's scratchpad only (per-session), but
each artifact is self-contained — `WebFetch` the URL to recover its full HTML, or redeploy a fresh edit to
the same URL via the `Artifact` tool's `url=` param.

Companion docs (already in-repo, so already cross-conversation): [PLANNER-PLAN.md](PLANNER-PLAN.md),
[DRIVING-PARITY.md](DRIVING-PARITY.md).
