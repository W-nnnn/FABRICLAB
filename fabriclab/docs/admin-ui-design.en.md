# FABRICLAB Admin — Interface Design Specification

> Keeps the storefront's three-colour palette and square corners, rebuilt around the typographic logic of a **sample book**. Target directory `fabriclab/admin`, stylesheet `src/main/resources/static/css/admin.css`.
> Hi-fi prototype: `docs/admin-ui-prototype.html` (single file, five views, toggle ZH / EN in the top bar).
> Chinese edition: `docs/admin-ui-design.md`.
>
> **V3 completion pass (14 Nov 2026)** — a hands-on audit showed v2 had turned "restraint" into "absence": zero product imagery, zero rail icons, and 91 of 103 clickable elements produced no response at all. V3 restores imagery, iconography, brand presence, operational density and working interaction. See **§6.13**; where it conflicts with earlier clauses, §6.13 governs.

## 1. Design thesis

**The back office is a sample book.** A fashion brand's admin should not read like data software. It should read like an internal sample book: paper underneath, rules and whitespace doing the dividing, type carrying the hierarchy, colour reserved for *what is happening right now*.

Six rules follow from that:

1. **Paper, not white cards.** The content area is one sheet (`#FBFAF7`); modules separate by a 1px rule and whitespace. White is spent only on layers that genuinely float — dialogs, toasts, dropdowns. The previous version wrapped every block in a bordered white card, fracturing the page into a dozen boxes that scrolled like a spreadsheet.
2. **Serif narrates, sans-serif operates, tabular figures count.** Never mixed: headings, block titles, empty-state titles and dialog titles take the serif; body, tables, buttons and tags take the sans; amounts, quantities, SKUs, dates and page numbers take tabular figures with `font-variant-numeric: tabular-nums lining-nums`.
3. **One signal colour, only for the present.** Terracotta `#B4643A` marks the active rail item, attention dots, destructive buttons, focus rings and link hover. **V3 addition:** one large field on the dashboard — the featured-style banner, roughly 3.5% of the first screen — so the brand colour is actually present. Everything else stays ink and grey. The previous version turned the primary button orange on hover and the action column orange outright; when an accent is everywhere it stops being an accent.
4. **Elevation only for overlays.** Table rows, cards, blocks — no shadow. A shadow means "this layer floats above the sheet," which only `<dialog>` and toast earn.
5. **One primary action per screen.** At most one solid ink button in the page header; everything else is outlined or plain text. In-row, the resting state is a single "Edit"; `···` appears on hover or keyboard focus.
6. **Never show a number you cannot query.** Every count, amount and quantity comes from the current SQL statement. Metrics without a real source (conversion, visitors, year-on-year) do not appear — whitespace is preferable to invention.

## 2. Visual language

### 2.1 Colour

| Token | Value | Use | Note |
|---|---|---|---|
| `--ink` | `#1A1A1A` | Primary text, rail ground, primary button | Shared with the storefront |
| `--ink-soft` | `#2E2C28` | Secondary text on dark | — |
| `--paper` | `#FBFAF7` | Content sheet, sticky header ground | **New**: one step warmer than white; kinder over long sessions |
| `--canvas` | `#F0EDE6` | Outermost page canvas | Visible only outside the content area |
| `--white` | `#FFFFFF` | Dialogs, toasts, dropdowns | The only pure white |
| `--cream` | `#F2EFE9` | Text on dark, primary button label | Shared |
| `--rust` | `#B4643A` | Signal colour (rule 3) | Shared |
| `--rust-ink` | `#9E532D` | Small-size emphasis (5.0:1 on paper) | Darkened one step to clear AA |
| `--rule` | `rgba(26,26,26,.11)` | Block dividers, row lines, aside splits | **Replaces** the previous solid `--line` |
| `--rule-strong` | `rgba(26,26,26,.30)` | Header underline, table head rule, input border | — |
| `--wash` | `#F4F1EA` | Row hover, read-only fill | — |
| `--wash-deep` | `#EBE7DD` | Icon-button hover, switch off-state | — |
| `--muted` | `#6E6961` | Secondary text (5.1:1 on paper) | Safe at any size |
| `--faint` | `#8A8478` | Indices, times, placeholders (3.4:1) | **Non-essential information only**; never the sole carrier of an action |
| `--body` | `#3A3833` | Long-form body copy | — |

Rail-specific: ground `--ink`, text `rgba(242,239,233,.70)`, hover `.045` fill with text at `.92`, group labels `.32`, dividers `.10`.

**No green for success.** Success is ink plus a check; in-progress is a solid terracotta dot; terminal states are a 26% grey dot. Four colours suffice — a fifth would let "success" steal attention from "needs action."

### 2.2 Typeface

Chinese and English editions deliberately diverge here. Latin has no square glyph, so at the same size it reads narrower and looser than Han characters; swapping the stack without resetting the scale makes the English interface look under-set and airy.

| Token | Chinese edition | English edition | Use |
|---|---|---|---|
| `--f-song` | `"Songti SC", "Noto Serif SC", "Source Han Serif SC", STSong, SimSun, serif` | `"Iowan Old Style", "Palatino Linotype", Palatino, Georgia, "Times New Roman", serif` | Page titles, block titles, empty-state and dialog titles |
| `--f-body` | `"Inter", "PingFang SC", "Noto Sans SC", "Microsoft YaHei", sans-serif` | `"Inter", "Helvetica Neue", Arial, sans-serif` | Body, tables, forms, buttons, tags |
| `--f-num` | `"Inter", "Helvetica Neue", Arial, sans-serif` | same | Amounts, quantities, SKUs, dates, pagination; always `tabular-nums lining-nums` |

**Why the numerals changed.** The previous version used Bahnschrift/DIN. Fine on Windows, but glyphs shift across macOS and on server-rendered PDFs. Inter with an Arial fallback plus `tabular-nums` guarantees column alignment — alignment is the first requirement for admin numerals, letterform character is second.

**Why the Latin serif is not Georgia alone.** Georgia is the last fallback, not the first choice: its rounded bowls and old-style figures read too friendly for a house that prints its lookbook on uncoated stock. Iowan Old Style and Palatino both hold a sharper, more editorial tone, and both are present on macOS and Windows respectively, so the first available face is already the right one on the two platforms that matter.

The serif never enters body copy or tables. In Chinese, Songti below 13px has strokes too fine to scan; above 21px it immediately reads as print. The same threshold holds for Latin serif: 14px or larger only.

### 2.3 Type scale

| Size | Weight | Line height | Tracking | Use |
|---|---|---|---|---|
| 38 | 400 | 1.0 | `-.02em` | Count-band figures (tabular) |
| 34 (zh) / 37 (en) | 500 | 1.18 / 1.14 | `.015em` / `-.006em` | Page title (serif) |
| 27 | 400 | 1.25 | `.05em` | Document-number title (tabular, order detail) |
| 21 (zh) / 21.5 (en) | 500 | 1.3 | `.02em` / `.004em` | Block title (serif) |
| 20 (zh) / 21 (en) | 500 | — | `.03em` / `0` | Empty-state title (serif) |
| 14 | 400 | 1.65 / 1.68 | 0 | Body, table primary row, names |
| 13.5 | 400 | 1.7 | 0 | Page description, input values |
| 13 | 400 | 1.6 | `.02em` | Rail navigation, secondary body |
| 12.5 | 400 | 1.5 | `.04em` | Table links, small buttons, segmented control |
| 12 | 400 | 1.5 | `.02em` | Category, date, supporting copy |
| 11.5 | 400 | 1.5 | `.04em` | Status tags, captions, units |
| 11 (zh) / 10.5 (en) | 500 | 1.0 | `.1em` / `.08em` | Form labels |
| 10.5 | 500 | 1.0 | `.18em` / `.13em` | Eyebrows, column headers, aside labels |
| 10 (zh) / 9.5 (en) | 500 | 1.0 | `.14em` / `.10em` | Table head |

Two adjustments are English-only and non-negotiable:

- **Tracking contracts by roughly a third.** `.18em` on a two-character Han label is an elegant air gap; the same value on "RUNTIME" tears the word into five islands. Every all-caps label drops to `.10–.14em`.
- **Line height rises to 1.68.** Latin lowercase has a larger x-height relative to its cap height than Han characters have to their em box, so identical leading looks tighter.

The scale sits one step above the previous version (body 13 → 14, title 26 → 34). The earlier build ran 11–13px throughout on a 1440 display: dense but weightless, reading like a business tool from a decade ago.

`--faint` is used only below 11px, for indices and captions, and never as the sole carrier of information.

### 2.4 Grid and rhythm

Eight-step spacing: `4 / 8 / 12 / 16 / 24 / 32 / 48 / 64`.

| Item | Value |
|---|---|
| Max content width | 1440 (previously 1560 — measure too long) |
| Content padding | `0 48 72`; `<1280` `32`; `<1024` `24`; `<720` `20` |
| Page header | `padding 36 0 22`, bottom `1px --rule-strong` |
| Block | top `1px --rule`, `padding-top 32`; index column 44, body indent 44 |
| Main / aside | `minmax(0,1fr) 300`, gap 64; aside `border-left 1px --rule` + `padding-left 40` |
| Between blocks | 32 (rule included) |
| Between fields | 24; two-up gap 24 |

**The index column is new in this version.** Each block carries a two-digit number (`01`, `02`) at 10.5px tabular, `--faint`, set 44px clear of the title. It turns a form from "a pile of inputs" into a document with chapters — and it is the only place `--faint` is allowed to stand alone.

### 2.5 Borders, elevation, motion

- Every border is 1px, and only two weights exist: `--rule` (division) and `--rule-strong` (structure, control edges). No 2px borders.
- Shadows in two places only: dialog `0 32px 80px rgba(26,26,26,.26)`, toast `0 12px 32px` plus a `1px` ring. **Cards and table rows never take a shadow.**
- Motion: hover `.13–.16s ease`; translation `.2s cubic-bezier(.22,.61,.36,1)`; progress bar `1.1s`. Animate only `background / border-color / color / opacity / transform`.
- All of it collapses to zero under `prefers-reduced-motion: reduce` (a reset block at the end of `admin.css`).

## 3. Layout

### 3.1 Shell

```
┌───────────────┬──────────────────────────────────────────────────┐
│               │  Bar 60   Breadcrumb ······ search help │ DEMO   │
│  Rail 240     ├──────────────────────────────────────────────────┤
│  ink          │                                                  │
│               │  ┌ Eyebrow — 02 Products ───────────────────┐    │
│  Brand 72     │  │ h1 Styles          [60 catalogue][New style]│  │
│               │  └───────────────────────────────────────────┘    │
│  Overview     │  ────────────────────────────────────────────────  │
│   Dashboard   │                                                  │
│               │  Toolbar  search / segments / reset              │
│  Products     │  ────────────────────────────────────────────────  │
│   Styles      │  Table (sticky head)                             │
│   Categories  │                                                  │
│   Stock & …   │  Pager                                           │
│               │                                                  │
│  Clients      │  ────────────────────────────────────────────────  │
│   Accounts    │  Colophon                                        │
│               │                                                  │
│  Commerce     │                                                  │
│   Orders      │                                                  │
│               │                                                  │
│  System       │                                                  │
│   Sessions…   │                                                  │
│   Audit Log   │                                                  │
│  ─────────    │                                                  │
│  Account / out│                                                  │
└───────────────┴──────────────────────────────────────────────────┘
```

- Outer `height:100vh; display:flex; overflow:hidden`; rail and content scroll independently. Admin navigation stays put.
- Rail `240px` fixed (English edition `254px`); content `flex:1; min-width:0` — the `min-width:0` is what keeps a wide table from breaking the layout.
- When a form or order page has an action bar, the bar sits **outside** the scroll container (after `.main`) and takes its height from flex. Never `position: sticky`, never overlaying the body.

### 3.2 Rail

| Part | Spec |
|---|---|
| Brand | Height 72; 30×30 paper square holding a serif "未" (zh) / "F" (en); name 11.5px tracking `.2em`; subtitle `MANAGEMENT` 9.5px tracking `.26em` at `.38` |
| Group label | Padding `24 20 8`; 9.5px tracking `.24em` at `.32`; not clickable |
| Nav item | Height 36; padding-left 20; 13px; resting `.70`; hover fill `.045` + text `.92` |
| Active | 2px `--rust` bar at left (inset 8 top and bottom) + `--cream` text |
| Footer | Top `1px rgba(cream,.1)`; 26×26 avatar square on 14% white; name 11.5px; role `ADMIN` 9.5px tracking `.14em`; sign-out icon at right |

**V3 restores icons, but only after unifying their geometry.** v2 removed every rail icon, leaving a column of grey text with no scanning anchors and a visibly unfinished rail. V3 uses one `<symbol>` library: 16-unit grid, 1.4px stroke, round caps, no fills, eight distinct marks built the same way; 42% opacity at rest, 95% when active. Indent, fill and tracking still carry the hierarchy — the icon is an anchor, not the system.

English edition widens the rail by 14px so two-word items ("Sessions & Security", "Stock & Gallery") stay on one line. Navigation labels use **Title Case** consistently; buttons, tags and body copy use sentence case. Chinese has no case distinction, so this rule exists only in the English build.

### 3.3 Top bar

Height 60, `--paper` ground, bottom `1px --rule`, horizontal padding follows the content area.

- Left: breadcrumb `Products / Styles`, 12px, `--faint` separators, last level `--ink` with `aria-current="page"`.
- Right: search and help icons (34×34 hit area, `--wash` hover) → 1px divider → environment tag `DEMO` (10px tracking `.16em`, 1px `--rule` border).
- **The account no longer repeats in the top bar.** The previous version had "Platform admin / ADMIN / Sign out" at the right of the bar and an identical set at the foot of the rail. This version keeps the rail footer only.
- A 1px terracotta progress bar (`#progress`) sits on the bar's bottom edge, sweeping `scaleX` 0→1 across page submissions, fading after 1.1s.

## 4. Information architecture

| Group | Item | Route | Key screens |
|---|---|---|---|
| Overview | Dashboard | `/admin/console` | Counts / needs attention / recently published / runtime |
| Products | Styles | `/admin/products` | List / new / edit |
| Products | Categories | `/admin/categories` | Two-level tree + inline edit |
| Products | Stock & Gallery | `/admin/products/{id}/stock`, `/images` | Restock, gallery |
| Clients | Accounts | `/admin/users` | List / edit / reset / enable |
| Commerce | Orders | `/admin/orders` | List / detail / amend / ship |
| System | Sessions & Security | `/admin/security` | Read-only policy |
| System | Audit Log | `/admin/logs` | Read-only stream (after T16) |

The rail renders only what the role can reach. There are no greyed-out items — an unbuilt module simply does not appear, which avoids implying capability that isn't there.

## 5. Page specifications

### 5.1 Shared page header

```html
<div class="ph">
  <div>
    <div class="ph__eyebrow"><em>02</em>Products</div>
    <h1>Styles</h1>
    <p>Maintain styles, fabric, craft, price and publish state.
       Stock changes only through restock.</p>
  </div>
  <div class="ph__act">
    <span class="ph__count" data-en-html="<b>60</b> in catalogue · <b>54</b> live">
      在册 <b>60</b> 款 · 在售 <b>54</b></span>
    <button class="btn">New style</button>
  </div>
</div>
```

- The eyebrow is a 20px rule, a two-digit chapter number, then the module name, 10.5px at `.2em`.
- `h1` is serif 34/37px. No redundant suffix — `Styles`, not `Product Style Management List`.
- The description states what this page does and what it deliberately does not do. No module background.
- Right side: count index first (`ph__count`, 11.5px, tabular numerals), then the primary button. The count lets the header carry "chapter three, sixty records" editorial information instead of a lone button.

**Word order is not translatable piecewise.** `在册 60 款 · 在售 54` and `60 in catalogue · 54 live` share no common segment order, and the same is true of `共 60 条 · 第 1 / 3 页` versus `60 styles · page 1 of 3`. These three fragments (header count, pager status, bulk counter) are marked with `data-en-html` and swapped as whole nodes rather than as adjacent text runs. See §6.12.

### 5.2 Dashboard `/admin/console`

Four bands: counts → needs attention (01) → recently published (02), with three aside notes at right.

- **Count band**: `grid` 4 columns, `1px --rule` dividers, no outer frame. Each cell `padding 32 0 30`:
  - Metric label 10.5px at `.18em`, followed by a status code (`LIVE`, `DRAFT`, `≤3`, `PAID`) and a hairline rule running to the cell edge — together they read as a table head;
  - Figure at 38px tabular, wrapped in `.stat__num` with `min-width:1.92ch` so the units under `54 / 6 / 3 / 2` land on one vertical line;
  - Caption 11.5px `--faint`.
  - Four measures: published styles, draft styles, styles at stock ≤3, orders awaiting dispatch. All from `COUNT`.
  - **English labels are `Published` and `Unpublished`, not `Live styles` and `Draft styles`.** The status code beside them already reads `LIVE` and `DRAFT`; a translated label that repeats the code produces "Live styles LIVE", which says nothing twice.
- **Needs attention**: `grid 96 | 1fr | 84`, row height 76, `1px --rule` between rows, full-row `--wash` on hover. Left column status tag, middle primary + secondary line, right "Ship →" (arrow shifts 3px on hover). Replaced wholesale by an empty state when nothing is pending.
- **Recently published**: `grid 110 | 1fr | 76`, row height 60. Time (11.5px `--faint`) + SKU (tabular 11.5px) + name (13.5px) + status. `ORDER BY updated_at DESC LIMIT 3`, same predicate as the list page.
- **Aside** (300px, 1px rule at left): runtime (port / prefix / timeout / CSRF / database), this session (signed in / last activity / origin), note. Three `aside-sec` blocks, each closed by a 1px `--rule`.
- No trend charts, no revenue, no "new today". If there is no verifiable source, it is not drawn.

### 5.3 List pages (shared skeleton: categories / styles / accounts / orders)

Structure: page header → toolbar → (bulk bar) → table → pager.

- **Toolbar**: `padding 26 0 0` with a bottom `1px --rule`; that bottom line *is* the segmented control's active indicator.
  - Search field: `280×38`, 1px `--rule-strong`, 12px inner padding, 14px glyph; on focus the border turns ink and a 3px `rgba(26,26,26,.06)` ring appears.
  - Segmented control: text with a 1px underline, 26px apart, `margin-bottom:-1px` so the active indicator collides with the toolbar rule; each item carries a tabular count (`All 60`, `Live 54`).
  - Right: "Reset filters" as 12.5px underlined text.
  - **The previous version used an underline-style search field with `margin-bottom:20`, leaving its baseline 22px off the segmented control's — two lines visibly out of register.** This version makes the field a 38px box so its bottom edge, the segments and the toolbar rule collapse into one line.
- **Bulk bar** `.bulk`: appears above the table head once ≥1 row is checked, `padding 13 0`, "3 selected" at left, "Publish / Unpublish / Clear" at right. Checked rows take `#F1ECDF`. In English the counter reads "3 selected" — numeral first — which is why it is swapped as a whole node.
- **Table**: see 6.1.
- **Pager**: `padding 22 0 0`; left "60 styles · page 1 of 3" (tabular); right page numbers plus rows-per-page (`select`, 10 / 20 / 50, default 20).

Column definitions:

| List | Columns (alignment) | Action column |
|---|---|---|
| Styles | check 40, SKU 110 (tabular), name/fabric auto, category 120 (en 134), price 110 (right), stock 70 (right), updated 100, status 86, actions 92 | Edit + `···` |
| Categories | code (left), name (left, level 2 indented 24 with a guide line), level (tag), rank (right), style count (right) | Edit, delete |
| Accounts | username (left), display name (left), email (left), status, registered (right) | Edit, reset password, enable |
| Orders | order no. (tabular, left), recipient, amount (right), status, placed (right) | Detail, ship |

- **The "Updated" column is new.** The previous name column took `auto` and swallowed the remainder — 460px at 1440 for 190px of content, leaving 270px empty on the right and making every row look cut short. With the timestamp column added, the name column settles at 376px and nine columns distribute naturally.
- The action column keeps one primary action ("Edit", underlined text); everything else collapses into `···` (26×26, **revealed on row hover or keyboard focus**). Four orange links in a column press the row's data into background.
- Key columns (SKU / order no. / username) are themselves links. No separate "View".
- The colour swatch sits inside the name cell as `.pcell` (flex, 14px gap), never as its own column.
- Below 1024px, category / stock / updated are hidden, the table gets `min-width:600` and switches to `table-layout:auto`, and the container scrolls horizontally.
  - **The previous version set `min-width:0` for narrow screens; at 420px the fixed columns exceeded the container, the name column collapsed to zero, and price text overprinted the swatch.** Keeping a 600px floor and letting the container scroll keeps content intact at any width.
- English dates read `13 Nov 2026`, not `2026.11.13`. The column holds both without reflow, but a day-first order is the only form an international operator reads without decoding.

### 5.4 Style editor `/admin/products/{id}`

Two columns: main `minmax(0,1fr)`, aside 300, gap 64.

- **Three numbered blocks in the main column**:
  - `01 Basics`: SKU (read-only), category (`select`, second level only), name, price, rank.
  - `02 Fabric & craft`: fabric, gauge, fit, craft. The craft field carries the error-state example.
  - `03 Copy`: description, 5-row `textarea`.
  - Per field: label 11px at `.1em` (en 10.5px at `.08em`), required asterisk in `--rust` immediately after the label (`margin-right:auto` pushes the remainder away), input 42px, help text 11px `--faint` with a character count at the right.
- **Four aside blocks** (no card; `border-left:1px --rule` + `padding-left:40`, `position:sticky; top:0`):
  - Publish state: `.seg2` split evenly (Live / Draft) with a one-sentence consequence below.
  - Stock: 32px tabular figure + "increases only through restock" + an outlined small button.
  - Gallery: three 1:1 thumbs (`grid` 3) + count + type note + button.
  - Metadata: created / updated / `version`, `grid auto 1fr`, values right-aligned with `word-break:break-all` so long references never escape the column.
- **Action bar**: outside the scroll container, height 64, top `1px --rule-strong`; "Changes apply only after saving" at left, Cancel and Save changes at right, aligned to the 1440 measure.

New and edit share one template. On create the SKU is editable and the gallery and metadata blocks are absent.

### 5.5 Restock `/admin/products/{id}/stock`

Single column, 560. Three read-only facts (current stock 30px tabular / last restock / `version`), one quantity input (`number min=1 step=1`), a live preview "after restock → 13", and "Confirm restock".

Restock is incremental — it never overwrites stock. Concurrency is guarded by a `version` predicate; on failure the message is "This style was updated elsewhere. Reload to continue."

### 5.6 Gallery `/admin/products/{id}/images`

Top: an add row (path, type: flat / on-body / detail, source note, rank). Bottom: existing entries, each a 1:1 thumb plus path, type, rank and "Move up / Move down / Delete". Reordering uses buttons; no drag library.

### 5.7 Account editor `/admin/users/{id}`

Fields: username (read-only), role (read-only tag), display name, email, phone. Three separate actions never mixed into save: `Save`, `Reset password` (dialog), `Disable / Enable` (dialog, stating "the account's current session expires on its next request"). The reset dialog does not show a new-password field.

### 5.8 Order detail `/admin/orders/{orderNo}`

**The only page whose `h1` is a document number**, because an order is a document.

- Header: eyebrow `04 Commerce · Orders` → `h1.code` order number (27px tabular at `.05em`) → description "Placed … · paid … · 2 items". Right: status tag + "Edit delivery" (outlined) + "Mark as shipped" (solid).
- Main: `01 Line snapshot` (SKU + name on two lines, unit price, qty, subtotal, `tfoot` total at 17px) + `02 History` (timeline, 6px squares, unmet nodes hollow).
- Aside: delivery, payment, notes. Long references break-all and right-align.
- A delete entry appears only when the order is cancelled and carries no payment reference: red text button plus a confirmation dialog.

### 5.9 Sessions & Security `/admin/security`

Read-only. Four fact groups: cookie name and port, 30-minute timeout, CSRF scope, password storage (BCrypt, irreversible). Each with one sentence on *why* it is set that way — no class names, no code paths.

## 6. Components

### 6.1 Table `.tbl`

| Item | Spec |
|---|---|
| Layout | `table-layout:fixed` with a `colgroup`; `auto` below 1024 |
| Head | Height 40; 10px at `.14em` (en 9.5px at `.10em`); `--muted`; **no fill**; bottom `1px --rule-strong`; `sticky top:0` |
| Row | Height 64 (holds a 34×44 swatch); 56 on image-free lists |
| Cell | `0 14`; first cell left 0, last right 0, sharing one baseline with the rest |
| Row rule | Horizontal `1px --rule` only; the last row keeps its bottom rule (forming a sandwich with the pager) |
| Hover | Full row `--wash`, `.13s` |
| Selected | Full row `#F1ECDF` |
| Numerals | Tabular with `tabular-nums`, right-aligned; amounts fixed at two decimals with `¥` |
| Thumbnail | **V3**: `40×52` garment silhouette SVG (8 shapes × 3 colourways) with cloth shading, weave, seams, ribbing and a 2px drop shadow; scales 5.5% on row hover |
| Empty | Head stays; the empty state occupies a full row |
| Overflow | Container `overflow-x:auto`, table `min-width:900`; the page itself never scrolls sideways |

**The head lost its fill.** The previous version laid `--fill` under the head and then a border on top, so with the card border the table opened with three stacked horizontal lines. This head now sits directly on paper and separates from data with a single `--rule-strong` — closer to a contents page.

**V3 replaces colour blocks with silhouettes.** A flat rectangle reads as "image failed to load" at any size. A garment outline at least answers *what kind of piece is this, in which colour*. Shading and weave give volume without pretending to be photography, and the featured tile holds up at 150×196.

No zebra striping: the row rules already do the work, and both together read as dirt.

### 6.2 Status tag `.tag`

No outlined boxes — eight bordered chips in one table become a field of pattern.

| Meaning | Square | Text | Used for |
|---|---|---|---|
| In progress | `--rust` solid **7×7** (V3; 5×5 was near-invisible on HiDPI) | `--rust-ink` | Live, paid, awaiting dispatch |
| Complete | `--ink` solid | `--ink` | Shipped, completed |
| Neutral / draft | `--faint` solid | `--muted` | Draft, unpaid |
| Terminal | 26% ink solid | `--faint` | Cancelled, disabled, sold out |
| Needs action | `--rust` solid + `0 0 0 2.5px rgba(180,100,58,.16)` halo | `--rust-ink` | Low stock, zero stock still live |

No padding, no border; 8px between square and text; 11.5px at `.06em`. Siblings sit at least 20px apart.

**Terminal states moved from hollow to 26% grey.** A 5px outline in `--faint` leaves four corner pixels and reads as a rendering glitch in a list.

### 6.3 Buttons

| Size | Height | Padding | Size | Context |
|---|---|---|---|---|
| `lg` | 46 | `0 30` | 13 | Sign-in, dialog primary |
| `md` | 40 | `0 22` | 13 | Page-level primary |
| `sm` | 32 | `0 14` | 12 | In-row, aside, toolbar |

- **Primary**: ink ground, paper label. Hover `#000` plus `inset 0 0 0 1px rgba(255,255,255,.13)`. **No hue shift, no translation.**
  - The previous version shifted ink to terracotta on hover: two brand colours fighting on one element, changing colour before the click even lands. Darkening with an inner border gives feedback while preserving "a button has one identity".
- **Outlined**: transparent, `1px --rule-strong`; border turns ink on hover.
- **Text**: no border, `--wash` hover.
- **Destructive**: `--rust` ground, paper label, confirmation only; disabled becomes 34% terracotta border with 55% terracotta text — visibly risky, visibly unavailable.
- **Disabled**: transparent, `1px --rule`, `--faint` label, `cursor:not-allowed`.
- `focus-visible`: `outline 2px --rust; outline-offset 2px`.
- English labels run 15–40% longer than their Chinese counterparts. The kit page sets every variant at its natural width rather than a fixed width, so wrapping never produces a two-line button; where a row of buttons must fit (action bars below 768px), they split the width evenly instead of shrinking to content.

### 6.4 Forms

- Label 11px at `.1em`, `--muted`, 9px above the input; required asterisk in `--rust`, 4px after the label text.
- Input 42px, `1px --rule-strong`, paper ground, `0 13` padding, 13.5px.
- Hover border at 45% ink; focus border ink plus `0 0 0 3px rgba(26,26,26,.06)` ring (box model unchanged).
- Textarea `min-height:88`, vertical resize only; character count in `.help s`.
- Read-only: `--wash` ground, `--muted` text, `--rule` border, no focus ring.
- Error: border `--rust`, focus ring at 12% terracotta; `.errmsg` below at 12px `--rust-ink` with a 12px glyph, wired with `aria-invalid` / `aria-describedby`.
- Switch `.sw`: 38×22, 1px border; off is `--wash-deep` with a grey knob, on is ink with a paper knob at `translateX(16px)`; `label.sw` uses `gap:11` to separate knob and text.
- Segmented `.seg2`: 1px surround, equal `flex:1` halves, active is ink ground with paper label.

### 6.5 Dialog `<dialog>`

- Width 460 (400 small), pure white, square, padding 32, **`margin:auto` for centring** — a global `margin:0` reset pins `dialog` to the top-left corner, so it must be written back explicitly.
- Backdrop `rgba(26,26,26,.46)` with `backdrop-filter: blur(1.5px)`.
- Title 21/22px serif; body 13px at 1.8; the alternative path goes in `.dlg__meta` (top `1px --rule`).
- Buttons right-aligned, 10px apart, secondary leading.
- Focus lands on Cancel; ESC closes; focus returns to the trigger. Destructive copy states the consequence — never "Are you sure?".

### 6.6 Notice `.alert`

| Type | Ground | Glyph | Text | Role |
|---|---|---|---|---|
| success | `#EEEAE1` | ink check | `--body` | `role="status"` |
| error | `#FBF0EA` | terracotta mark | `#7E3B1A` | `role="alert"` |
| info | `--wash` | grey dot | `--body` | `role="status"` |

Padding `14 16`, no left bar (the ground already separates; three vertical bars would add stray lines to the page), 15px close at right. Never auto-dismisses. One per page.

### 6.7 Toast

Bottom-right, white with shadow and a 2px status bar at left (ink for success, terracotta for failure), max width 340, `padding 14 16`. Dismisses after 4s, pauses on hover, `role="status"`. Only for "this took effect / this failed" — never for content that needs reading.

### 6.8 Empty state `.empty`

Centred, `padding 88 24`, max width 400. A 44×44 bordered square holds an 18px line glyph; below, a serif 20/21px title, 12.5px explanation at 1.85, and a 32px button.

Two distinct copies: **no data** ("No styles yet" + New style) and **no results** ("Nothing matches these filters" + Clear filters). They are not interchangeable.

### 6.9 Loading

- Table skeleton: 68px rows, 34×44 block plus two 10px bars, `--wash` fill, whole block fades in over 1.2s. No per-row shimmer.
- Button: disabled on click, label becomes "Saving", `min-width` locked to prevent collapse.
- Page level: the 1px terracotta progress bar on the top bar, submissions only.

### 6.10 Pagination `.pg`

Items `30×30`, 12px tabular; `--muted` at rest, `--wash` hover; **the current page takes no fill** — weight 500 plus a 1px underline. Disabled at 22% ink. Rows-per-page is an underline `select`, 20px clear of the numbers.

### 6.11 Colophon `.colophon`

One `1px --rule` closes the content area; 10.5px at `.12em` in `--faint` on both sides: left "FABRICLAB Undyed · Management", right "Session expires after 30 min idle · every write is logged". It functions as a book's colophon: it closes a long page and states the system's constraints where an operator will actually see them.

### 6.12 Bilingual mechanism

The prototype carries both languages in one file. Switching is instant and lossless.

**Three node classes.**

| Attribute | Holds | Example |
|---|---|---|
| *(plain text)* | Chinese source, translated by dictionary lookup | `款号维护` → `Styles` |
| `data-en` | A single English string replacing a node's text content | `2026.11.13` → `13 Nov 2026` |
| `data-en-html` | English markup replacing a node's inner HTML | `共 <b>60</b> 条 · 第 <b>1</b> / <b>3</b> 页` → `<b>60</b> styles · page <b>1</b> of <b>3</b>` |

`data-en-html` exists precisely because Chinese and English disagree on segment order. Translating `共`, `条 · 第` and `页` as three separate runs yields "Total 60 of 1 page 3" — grammatically dead. The whole phrase must be swapped. Three nodes need it: the header count, the pager status, the bulk counter.

**Restoration.** Every Chinese text node is recorded with its original string and whitespace before the first switch. Returning to Chinese replays the originals rather than translating back, so no drift accumulates across switches.

**Attributes are covered too.** `placeholder`, `aria-label`, `title`, `alt` and `value` travel through the same dictionary. Values matter: product names, fabric descriptions and craft notes are attribute values on inputs, not child text.

**What is not translated.** SKUs (`FL-HM-01`), order numbers, payment references (`MOCK…4C8A`), routes, ports and the `version` counter are technical identifiers. They stay identical in both editions, which is also why they are set in the tabular face.

**Language switching must re-render, not only re-label.** The breadcrumb is written by script and re-rendered per language from a bilingual `META` table; the bulk counter is re-read from the live checkbox state after each switch, because `data-en-html` replaces the `<b>` node inside it and stale references would print the previous language's count.

**Rules that differ by edition.**

| Item | Chinese | English |
|---|---|---|
| Serif stack | Songti / Noto Serif SC | Iowan Old Style / Palatino / Georgia |
| `h1` | 34px / `.015em` | 37px / `-.006em` |
| All-caps tracking | `.18em` | `.13em` |
| Table head | 10px / `.14em` | 9.5px / `.10em` |
| Line height | 1.65 | 1.68 |
| Rail width | 240 | 254 |
| Category column | 120 | 134 |
| Dates | `2026.11.13` | `13 Nov 2026` |
| Nav labels | — | Title Case |
| Count labels | 在售款号 / 草稿款号 | Published / Unpublished |

The count labels are the subtlest case: a literal translation produces "Live styles LIVE" beside the status code. The English edition renames the metric instead of reprinting the code.

## 7. State matrix

| Control | Default | Hover | Focus | Active / selected | Disabled |
|---|---|---|---|---|---|
| Primary button | ink ground, paper label | `#000` + lightened inner border | 2px terracotta ring | stays `#000`, no shift | transparent + fine border + grey label |
| Outlined button | transparent, 30% border | border turns ink | as above | — | `--rule` border, `--faint` label |
| Text button | 12.5px, 30% underline | label and line turn terracotta | as above | — | — |
| Nav item | 70% paper text | 4.5% fill, 92% text | 2px inset paper ring | 2px terracotta bar + full text | — |
| Table row | paper | `--wash` | first link takes a 2px ring | checked `#F1ECDF` | — |
| Row actions | `···` hidden | revealed on row hover | revealed on focus | — | — |
| Input | 30% border | 45% border | ink border + 3px 6% ring | — | `--wash` ground |
| Switch | grey ground, grey knob | border 45% | 2px terracotta ring | ink ground, knob right | — |
| Segmented | `--muted` text | text turns ink | 2px ring | ink text + ink underline | — |
| Pager item | `--muted` | `--wash` ground | 2px ring | ink text + underline | 22% ink, inert |
| Dialog | backdrop + white card | per-button states | first focus on Cancel | primary disabled while submitting | — |

## 8. Responsive

| Breakpoint | Rail | Content | Table | Other |
|---|---|---|---|---|
| ≥1440 | 240 fixed | max 1440, padding 48 | nine columns | — |
| 1280–1439 | 240 | padding 32 | nine columns | page title drops to 30px |
| 1024–1279 | 240 | padding 24 | nine columns | two-column pages: aside 300, gap 40 |
| 768–1023 | drawer 264 + scrim | padding 24, two columns collapse to one | category / stock / updated hidden; `min-width:600`, scrolls | hamburger appears, breadcrumb keeps last level only, count band becomes 2×2 |
| <768 | drawer | padding 20 | as above, scrolls | header stacks, form single-column, action-bar buttons split evenly, dialog `calc(100% - 24px)` |

While the drawer is open the content does not scroll; tapping the scrim or pressing ESC closes it and focus returns to the hamburger.

## 9. Accessibility

1. The first focusable element on every page is "Skip to main content", targeting `#main`.
2. The active rail item carries `aria-current="page"`; group labels are `<h2 class="side__group">` so the rail forms a real outline.
3. Tables carry `<caption class="sr-only">` stating scope; row actions are named in full ("Edit FL-HM-01") rather than a bare "Edit".
4. Success uses `role="status"` / `aria-live="polite"`; failure uses `role="alert"`.
5. Everything is keyboard-reachable: tab order is rail → bar → content; focus is trapped inside dialogs; ESC closes; the row `···` appears on focus.
6. Contrast: `--ink` 16.4:1 on paper; `--muted` 5.1:1; `--rust-ink` 5.0:1; `--faint` 3.4:1 (non-essential only). `--rust` is used only at ≥16px or on non-text elements.
7. Swatch `alt` names the SKU and product, never "image". Anything hidden at narrow widths must still be reachable on the detail page.
8. `html[lang]` switches between `zh-CN` and `en` with the interface, so screen readers change pronunciation rules along with the visible copy.

## 10. Implementation mapping

### 10.1 Style skeleton (append to `admin.css`)

```css
:root { /* inherits storefront tokens; adds --paper --canvas --white --rule --rule-strong
           --wash --wash-deep --faint --body and --f-song --f-body --f-num */ }
.shell / .side / .side__brand / .side__mark / .side__group / .side__item / .side__foot
.topbar / .crumb / .ibtn / .env / #progress
.main / .page / .ph / .ph__eyebrow / .ph__count / .ph__act
.block / .block__head / .block__no / .block__body
.stats / .stat / .stat__k / .stat__v / .stat__num
.ledger / .recent / .facts / .aside-sec
.toolbar / .search / .seg / .bulk
.tbl / .tblwrap / .tbl--snap / .swatch / .pcell / .acts / .ck / .col-md
.pager / .pgs / .pg / .psize
.form2 / .form2__side / .field / .ctl / .ctl--ro / .errmsg / .help / .frow
.seg2 / .sw / .bignum / .meta / .gal
.btn / .btn--line / .btn--quiet / .btn--danger / .btn--sm / .btn--lg / .txt
.alert / .alert--ok / .alert--err / .tag / .tag--*
.empty / .skelrow / .sk / dialog / .dlg__* / .toast
.actions-bar / .colophon
```

For the English edition, the `html[lang="en"]` block overrides only: the serif stack, `h1` and block-title sizes, every `.1em`-and-up tracking value, body line height, rail width, and the category column width. Nothing in the colour, spacing, border or component layer changes — the two editions must remain one system.

### 10.2 Template skeleton

Add `layout(title, section, active, content, actions)` to `fragments.html`:

- `section` emits the eyebrow and the breadcrumb parent (`Products` / `Commerce`);
- `active` decides the rail's `aria-current`;
- `actions` is the header's button-and-count group (may be empty);
- the body carries content blocks only; rail and bar are maintained in one place.

Blocks use the `th:replace` fragment `frag/block :: block(no, title, desc)`, with the number hard-coded in the template (`01`, `02`, `03`) so every page advances at the same cadence.

Copy comes from `messages.properties` / `messages_en.properties` with keys per screen region (`admin.styles.title`, `admin.styles.desc`, …). Three composite strings — header count, pager status, bulk counter — need whole-message patterns with placeholders (`{0} styles · page {1} of {2}`), not concatenated fragments.

### 10.3 Build order

1. `layout` + rail + top bar + `.ph` + `.block` + `.tbl` + `.pager` + `.tag` + `.alert` + `.btn` (once, then reuse everywhere).
2. T13: category page (tree + inline edit), style list (bulk bar and `···` menu), two-column editor.
3. T14: restock page, gallery maintenance.
4. T15: account list and editor, reset and disable dialogs.
5. T16: order list and detail (document-style header), ship and delete dialogs.
6. Then `.empty` / `.skelrow` / `.toast` / error pages / five breakpoints / `prefers-reduced-motion`.

### 10.4 What changed from the previous version

| Previous | This version | Reason |
|---|---|---|
| Content ground `#EDEAE2`, every module a bordered white card | Content is one sheet `#FBFAF7`; white only for overlays | A dozen white boxes fractured the page into spreadsheet software |
| Body 13px, titles 26px | Body 14px, titles 34/37px | Everything ran small on a 1440 display: dense but weightless |
| Eight inconsistent icons | v2 removed them → **V3 replaces them with one unified 1.4px library** | Removing them left a grey text column and dropped both scannability and finish |
| Primary button hovered to terracotta | Hover darkens with an inner border | Two brand colours fighting on one button |
| Accent spread across seven places | Consolidated to five | An accent everywhere is no accent |
| Name column `auto`, swallowing the remainder | New "Updated" column, fixed widths | 270px of empty space at the right of every row |
| Head filled `--fill` plus a border | Head unfilled, one rule | Card border + head fill + head rule made three lines |
| Thumbnails faked fabric with diagonal gradients | Flat swatch + 6% inner border | At 34×44 the gradient became grey noise |
| Numerals in Bahnschrift/DIN | Inter + `tabular-nums` | Glyphs shifted across platforms; alignment first |
| Order page `h1` "Order detail" plus a status strip | `h1` is the order number | An order is a document; the number is the subject |
| Search baseline 22px off the segmented control | One 38px box, three lines collapse | Two lines out of register is the clearest sign of haste |
| Narrow screens `min-width:0` | Keep a 600px floor, let it scroll | At 420px the name column collapsed and price overprinted the swatch |
| Account shown in both bar and rail | Rail footer only | Same information twice |
| Dialog pinned top-left by `margin:0` | `margin:auto` centring | A global reset overrode the UA style |
| No bulk bar, no colophon | Bulk bar + colophon | Standard admin action, and a page needs a closing line |
| — | English edition with its own scale, dates and case rules | Copy translated word-for-word into a Chinese-fitted grid reads as a translation, not as an interface |

## 11. Acceptance mapping

| Criterion | Where it lands |
|---|---|
| AC-25 Admin creates category and style; storefront sees it after publish | 5.3 list → 5.4 editor aside "Publish state" |
| AC-26 Delete rejected when children or references exist | 5.3 action column "Delete" → 6.5 dialog stating the reference count |
| AC-27 Stale form triggers a version conflict | 5.4 aside "Metadata" shows `version`; conflict message in 5.5 |
| AC-28 Buy 2, restock 3 → 11 | 5.5 restock preview "after restock → 13" |
| AC-29 Shipping an unpaid order is rejected; one shipment after payment | 5.8 "Mark as shipped" renders only for `PAID` |
| Brief: no fabricated console statistics | 5.2 all four figures come from `COUNT`; no trend charts |

### 6.13 V3 completion pass

**Why it was rebuilt.** v2's typographic logic — paper, rules, chapter numbers — fixed the cheapness of the first version, but three consecutive rounds of subtraction turned *restraint* into *absence*. Evidence from walking the real interaction paths:

| Measure | v2 | What it reads as |
|---|---|---|
| Real imagery / product thumbnails | none, 8 flat colour blocks | obviously placeholders |
| Rail icons | 0 | a column of grey text, no scanning anchors |
| Brand-colour area | five 5px dots | effectively unused |
| Clickable elements | 103, **91 with no response at all** | a switchable static drawing, not a hi-fi prototype |
| Table data | 8 hard-coded rows | filters, search, pagination and bulk actions are props |

The "rushed" feeling was never a palette problem. It was a **completion problem**: the interface stopped at the wireframe stage, and every element looked like content that had not been filled in yet.

**Six corrections.**

1. **Surface hierarchy.** A new `--surface:#FFFFFF` work band (top bar, toolbar, table head, bulk bar) sits against the `--paper` sheet and floating white. These are **continuous horizontal bands, not white cards** — a deliberate guard against falling back to the first version's dozen boxes. The rail moves from flat black to warm `#211E1A` with a 3px terracotta brand rule at the top.
2. **Brand presence.** The dashboard gains a featured-style banner (terracotta gradient, large serif headline, garment visual, four facts); the active nav item takes a pale terracotta fill with a 3px bar.
3. **Imagery and iconography.** Eight garment silhouettes (turtleneck / shirt / scarf / coat / dress / jacket / polo / socks) in three colourways, with cloth shading, weave, seams, ribbing and shadow. A single 1.4px icon library returns to rail and top bar.
4. **Visible operations.** Status squares 5→7px with body-depth labels; `···` moves from hover-only to 55% opacity at rest; the top bar gains global search (`/` focuses), quick-create, notifications and the account chip.
5. **Real data.** Sixty bilingual records (20 styles × 3 colourways), interleaved by update time so colourways never run together. Filtering, search, pagination, rows-per-page, checkbox selection, bulk publish/unpublish, row menu, editor pre-fill, delete dialog, toasts and empty state all work; counts are derived from the data at render time.
6. **Honest feedback.** Every clickable element produces an observable response. Modules not yet built do not fake navigation — they state "lands from T13". Toasts cap at three, dismiss after 4s, pause on hover, and are cleared on language switch (transient feedback is not re-translated).

**Verification.** 26 end-to-end cases pass (search / filter / page / rows-per-page / bulk / row menu / editor pre-fill / rail navigation / ship / empty state / ZH⇄EN round trip / no horizontal overflow at 820 and 420); no JS errors. The prototype opens in a usable state, and language switching still follows the three node classes in §6.12.

**Implementation unchanged.** Colour, spacing, component dimensions and the bilingual rules stand. V3 adds a surface layer and an icon library outside the `html[data-lang]` overrides; T13 extends `admin.css` using the class list in §10.1.

