"""Tests for stack/competency/plugin sections."""

from scripts.presentation import stacks as stk


def test_node_stack_table_has_core_nodes():
    html = stk.render_node_stack_table()
    assert "core-api" in html
    assert "PostgreSQL" in html
    assert "tech-stack-nodes" in html


def test_competitor_stacks_excludes_korus():
    html = stk.render_competitor_stacks_table()
    assert "eXpress" in html or "express" in html.lower()
    assert "Kafka" in html
    assert "tech-competitor-stacks" in html


def test_plugin_tiers_l0_l3():
    html = stk.render_plugin_platform()
    assert "L0" in html and "L3" in html
    assert "core-api" in html
    assert "tech-plugins" in html


def test_external_stack_profiles_block_is_honest():
    html = stk.render_external_stack_profiles()
    assert "tech-external-stack-profiles" in html
    assert "Поставляемый стек" in html
    assert "Стек заказчика" in html
    assert "Кандидаты на замену" in html
    assert "Отложенная live-проверка" in html
    assert "проверки подключения" in html
    assert "границ ответственности" in html
    assert "ok/warning/blocked" in html
    assert "проверить в лаборатории с вендором" in html
    assert "нагрузочная проверка" in html
    assert "external/BYO" not in html
    assert "desired manifest" not in html
    assert "lab cutover readiness" not in html
    assert "Поставляется в полном промышленном составе" not in html
    assert "эксплуатационная приёмка и нагрузочная проверка выполняются отдельно" in html


def test_competencies_roles():
    html = stk.render_competencies_table()
    assert "Backend" in html
    assert "DevOps" in html
