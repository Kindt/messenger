# Desktop UX Spec

<!-- ARTIFACT:desktop-ux-spec -->

**Wave:** W0 | W1 | W2 | W3 | W4  
**Brief ref:**  
**Plan ref:** desktop-plan  
**Author:** Designer

## Product intent (1–2 sentences)

## User flows

1. 
2. 

## IA map — on screen vs hidden

| Feature / action | Zone (`desktop-ux-framework.md`) | On screen (tab/pane) | In settings / servers / menu | Why |
|------------------|-----------------------------------|----------------------|--------------------------------|-----|
| | shell:tab-chats | yes/no | | |

## Navigation graph

```text
(screen transitions)
```

## Layout — master–detail (Чаты)

| Viewport | Left pane | Right pane | Notes |
|----------|-----------|------------|-------|
| ≥960px | contacts | chat + composer | |
| <960px | (specify) | | |

## Surfaces & states

### Surface: (e.g. shell:tab-chats)

| State | UI | Copy (RU reference) | Primary action | DesktopUiIds |
|-------|-----|---------------------|----------------|--------------|
| Default | | | | |
| Empty | | | | |
| Loading | | | | |
| Error | | | | |
| Capability off | | | | |

### Surface: shell:tab-settings

| Sub-tab | Contents | On sub-tab vs nested dialog |
|---------|----------|----------------------------|
| Общие | | |
| Профиль | | |
| Уведомления | | |
| Файлы | | |
| Безопасность | | |

## Composer & chrome

| Control | Placement | Primary/secondary | Id |
|---------|-----------|-------------------|-----|
| Send | | primary | SEND |
| Attach | | | |
| Emoji | | popup | EMOJI_BTN |
| Call | | capability | |

## Capability gating (W3+)

| Capability | UI when ON | UI when OFF |
|------------|------------|-------------|
| | | tab disabled / hidden |

## `DesktopUiIds` map

| Element | Id (existing or NEW) | TestFX notes |
|---------|------------------------|--------------|
| | | |

## CSS / visual notes

- `desktop.css` classes to add/change:
- Bubble / status orb / title bar:

## i18n

RU reference strings (lab); future i18n keys if aligned with web:

## Accessibility

- Focus order (composer → send):
- Tooltips on icon-only:
- Min click targets:

## Parity references

- Web settings tab:
- Mobile tab equivalent:

## Handoff

- [ ] → UX Evaluator
