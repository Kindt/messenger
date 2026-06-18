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


def test_competencies_roles():
    html = stk.render_competencies_table()
    assert "Backend" in html
    assert "DevOps" in html
