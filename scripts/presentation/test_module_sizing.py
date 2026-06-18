"""Tests for module-based production sizing."""

from scripts.presentation import module_sizing as ms


def test_load_estimate_includes_all_required_modules():
    est = ms.estimate_from_load(ms.LoadInputs(registered_users=10_000))
    ids = {m.module_id for m in est.modules}
    assert "core-api" in ids
    assert "solr" in ids
    assert "postgres-hot" in ids
    assert "worker-message-pipeline" in ids
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


def test_replicas_increase_ram_not_always_online():
    base = ms.estimate_capacity(
        tuple(m.id for m in ms.PRODUCTION_MODULES if m.required),
        backup="none",
        module_replicas={"core-api": 1},
    )
    scaled = ms.estimate_capacity(
        tuple(m.id for m in ms.PRODUCTION_MODULES if m.required),
        backup="none",
        module_replicas={"core-api": 3, "ws-gateway": 2},
    )
    assert scaled.total_ram_gb > base.total_ram_gb
    assert scaled.max_peak_online > base.max_peak_online


def test_vcpu_scales_with_load():
    low = ms.estimate_from_load(ms.LoadInputs(registered_users=1_000))
    high = ms.estimate_from_load(ms.LoadInputs(registered_users=100_000, peak_msg_s=120.0))
    assert high.total_vcpu > low.total_vcpu


def test_capacity_considers_vcpu():
    cap = ms.estimate_capacity(
        ("core-api", "ws-gateway", "worker-message-pipeline", "postgres-hot"),
        module_replicas={"core-api": 1},
    )
    assert cap.total_vcpu >= 4
    assert cap.bottleneck


def test_required_modules_cannot_be_excluded():
    cap = ms.estimate_capacity(
        ("core-api", "ws-gateway"),
        backup="none",
    )
    assert "postgres-hot" in cap.enabled_module_ids
    assert "redis" in cap.enabled_module_ids
    assert "nats" in cap.enabled_module_ids


def test_optional_archive_and_solr_can_be_excluded():
    cap = ms.estimate_capacity(
        (
            "core-api",
            "ws-gateway",
            "worker-message-pipeline",
            "web-lb",
            "minio",
            "keycloak",
        ),
        backup="none",
    )
    assert "postgres-archive" not in cap.enabled_module_ids
    assert "solr" not in cap.enabled_module_ids
    assert "zookeeper" not in cap.enabled_module_ids
    assert "worker-indexer" not in cap.enabled_module_ids
    assert "worker-archiver" not in cap.enabled_module_ids


def test_zookeeper_requires_solr():
    cap = ms.estimate_capacity(
        ("core-api", "zookeeper", "solr"),
        backup="none",
    )
    assert "zookeeper" in cap.enabled_module_ids
    cap_no = ms.estimate_capacity(
        ("core-api", "zookeeper"),
        backup="none",
    )
    assert "zookeeper" not in cap_no.enabled_module_ids


def test_prod_full_default_includes_solr_and_archive():
    cap = ms.estimate_capacity(tuple(), backup="none")
    assert "solr" in cap.enabled_module_ids
    assert "postgres-archive" in cap.enabled_module_ids
    assert "worker-retention" in cap.enabled_module_ids
    assert "livekit" not in cap.enabled_module_ids


def test_worker_archiver_requires_archive_pg():
    cap = ms.estimate_capacity(
        ("core-api", "worker-archiver", "solr"),
        backup="none",
    )
    assert "worker-archiver" not in cap.enabled_module_ids
    cap_ok = ms.estimate_capacity(
        ("core-api", "worker-archiver", "postgres-archive"),
        backup="none",
    )
    assert "worker-archiver" in cap_ok.enabled_module_ids


def test_livekit_optional():
    est = ms.estimate_from_load(ms.LoadInputs(registered_users=5_000))
    assert not any(m.module_id == "livekit" for m in est.modules)
    est_lk = ms.estimate_from_load(
        ms.LoadInputs(registered_users=5_000),
        enabled_ids=ms.prod_full_default_enabled() | {"livekit"},
    )
    assert any(m.module_id == "livekit" for m in est_lk.modules)


def test_backup_increases_disk_and_ram():
    plain = ms.estimate_from_load(ms.LoadInputs(registered_users=10_000, backup="none"))
    dr = ms.estimate_from_load(ms.LoadInputs(registered_users=10_000, backup="dr"))
    assert dr.total_ram_gb > plain.total_ram_gb
    assert dr.hdd_tb > plain.hdd_tb


def test_high_load_scales_out_instance_count():
    est = ms.estimate_from_load(ms.LoadInputs(registered_users=500_000, peak_msg_s=200.0))
    pg = next(m for m in est.modules if m.module_id == "postgres-hot")
    api = next(m for m in est.modules if m.module_id == "core-api")
    pipe = next(m for m in est.modules if m.module_id == "worker-message-pipeline")
    assert pg.count >= 2
    assert api.count >= 4
    assert pipe.count >= 4
    for m in (pg, api, pipe):
        assert m.ram_gb / m.count < m.ram_gb * 0.55


def test_module_resources_are_integer_ceil():
    est = ms.estimate_from_load(ms.LoadInputs(registered_users=12_345, peak_msg_s=17.3))
    for m in est.modules:
        assert m.ram_gb == int(m.ram_gb)
        assert m.vcpu == int(m.vcpu)
        assert m.ram_gb >= 1
        assert m.vcpu >= 1
