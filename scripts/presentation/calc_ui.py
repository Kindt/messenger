"""Reusable calculator shell markup."""

from __future__ import annotations

from html import escape


def calc_shell(
    shell_id: str,
    title: str,
    subtitle: str,
    form_html: str,
    result_id: str,
    run_id: str,
    *,
    accent: str = "indigo",
) -> str:
    return f"""
<div class="calc-shell calc-shell-{escape(accent)}" id="{escape(shell_id)}">
  <div class="calc-shell-head">
    <h4 class="calc-shell-title">{escape(title)}</h4>
    <p class="calc-shell-sub">{subtitle}</p>
  </div>
  <div class="calc-shell-body">
    <div class="calc-form-grid">{form_html}</div>
    <div class="calc-actions">
      <button type="button" class="btn-calc" id="{escape(run_id)}">Рассчитать</button>
    </div>
    <div class="calc-result-panel" id="{escape(result_id)}" aria-live="polite"></div>
  </div>
</div>"""


def field_number(
    field_id: str,
    label: str,
    value: int | str,
    *,
    min_val: int | float = 1,
    step: str = "1",
    placeholder: str = "",
) -> str:
    ph = f' placeholder="{escape(placeholder)}"' if placeholder else ""
    return f"""
<div class="calc-field">
  <label for="{escape(field_id)}">{escape(label)}</label>
  <input type="number" id="{escape(field_id)}" value="{value}" min="{min_val}" step="{step}"{ph}/>
</div>"""


def field_select(field_id: str, label: str, options: list[tuple[str, str]], selected: str = "") -> str:
    opts = []
    for val, text in options:
        sel = ' selected' if val == selected else ""
        opts.append(f'<option value="{escape(val)}"{sel}>{escape(text)}</option>')
    return f"""
<div class="calc-field">
  <label for="{escape(field_id)}">{escape(label)}</label>
  <select id="{escape(field_id)}">{''.join(opts)}</select>
</div>"""


def field_checkbox(field_id: str, label: str, checked: bool = True) -> str:
    chk = " checked" if checked else ""
    return f"""
<div class="calc-field calc-field-check">
  <label><input type="checkbox" id="{escape(field_id)}"{chk}/> {escape(label)}</label>
</div>"""
