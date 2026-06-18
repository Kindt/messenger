"""Tests for module-based production sizing."""

from scripts.presentation import module_sizing as ms


def test_load_estimate_includes_all_required_modules():
    est = ms.estimate_from_load(ms.LoadInputs(registered_users=10_000))
    ids = {m.module_id for m in est.modules}
    assert "core-api" in ids
    assert "solr" in ids
    assert "postgres-hot" in ids
    assert est.peak_online > 0
    assert est.peak_msg_s > 0


def test_explicit_peak_online_overrides_derivation():
    est = ms.estimate_from_load(ms.LoadInputs(registered_users=500, peak_online=200))
    assert est.peak_online == 200


def test_ha_increases_app_nodes():
    plain = ms.estimate_from_load(ms.LoadInputs(registered_users=5_000))
    ha = ms.estimate_from_load(ms.LoadInputs(registered_users=5_000, ha=True))
    assert ha.app_nodes >= 2
    assert ha.total_ram_gb >= plain.total_ram_gb


def test_capacity_from_modules():
    cap = ms.estimate_capacity(
        tuple(m.id for m in ms.PRODUCTION_MODULES if m.required),
        hdd_tb=10.0,
    )
    assert cap.max_registered_users >= 1_000
    assert cap.max_peak_online > 0
    assert cap.storage_years_at_10k_ru > 0
