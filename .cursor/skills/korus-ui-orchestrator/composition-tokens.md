# Composition tokens — messenger shell layout

Designer **MUST** reuse these patterns (axis F). Engineer **MUST NOT** invent ad-hoc spacing for settings/thread/empty blocks.

Source of truth: `modules/web-client/webui-build/src/styles.css` (rebuild → `styles.css`).

---

## Settings modal

| Pattern | Class / testid | Use |
|---------|------------------|-----|
| Tab strip | `.settings-tablist`, `settings-tab-{id}` | IA zones (general, profile, notifications, links, security) |
| Panel | `#settings-panel-{id}`, `.settings-tabpanel` | One panel per tab |
| Section title | `.settings-subtitle` | Before a block list — **required** |
| Row | `.settings-row` | label + control; destructive rows separated |
| Hint | `.settings-hint` | Cross-tab pointers (e.g. DND → notifications) |
| Icon action | `.btn-icon` / `.btn-icon-svg` | Secondary actions in row |

**IA map:** [`settings-ia-inventory.md`](settings-ia-inventory.md)

---

## Empty / status blocks

| Surface | Classes | testid |
|---------|---------|--------|
| Global search empty | `.global-search-empty`, `-title`, `.global-search-hint` | `global-search-empty` |
| Thread search empty | `.thread-search-empty`, `-title`, `.thread-search-hint` | `thread-search-empty` |

Rule: **title + hint** (LABEL-RED-03). No lone emoji in title.

---

## Thread / composer

| Pattern | Class | Notes |
|---------|-------|-------|
| Thread body | `.thread`, `.messages` | scroll region |
| Composer | `[data-testid=message-composer]` | min height guards in visual tier |
| Message actions | `.msg-actions .btn-icon` | hover desktop; touch on mobile |
| Format bar | `.composer-format .btn-icon` | B/I/code/emoji |

---

## Header / shell

| Pattern | Class | Notes |
|---------|-------|-------|
| Shell grid | `.messenger-shell`, `.call-open` | call panel clamp right |
| Header actions | `.hdr-btn-optional`, `.btn-icon` | hide optional at narrow widths |
| Status | `.ws-status`, `.presence-pill` | text + CSS, not emoji-only |

---

## Breakpoints (reference)

| px | Behavior |
|----|----------|
| 960 | sidebar/thread single-pane rules (`korus-webui-mobile`) |
| 520 | composer / touch targets |

---

## Evaluator (axis F)

- [ ] New block uses table above or justified exception in ux-spec
- [ ] Destructive control not adjacent to primary save
- [ ] Empty state follows title + hint pattern
