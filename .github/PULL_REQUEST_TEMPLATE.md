## Summary

<!-- Кратко: что меняется и зачем -->

## Test plan

- [ ] `./gradlew buildIntegrity` (или точечные модули)
- [ ] Локальный smoke / QEMU при изменении runtime (если применимо)

## Performance & validation (spec 025)

Отметьте применимое. Пропускайте пункты, не затронутые PR.

### Web UI / first load (FR-175, SC-024)

- [ ] Критический путь не раздут: `npm run test:first-load` в `webui-build` (CI gate)
- [ ] При росте bundle/CSS — обоснован в описании PR или обновлён baseline в `scripts/perf/baselines/`

### Load / k6 (FR-067, FR-148)

- [ ] При изменении API hot-path — прогон k6 pilot на lab (QEMU `127.0.0.1:18080` или CI guest)
- [ ] Пороги `p(95)` в `scripts/load/pilot-*.js` не ослаблены без записи в baseline

### JDBC / E2EE hot-path (FR-177)

- [ ] Нет широкого `catch (Exception)` в message/chat JDBC без причины
- [ ] E2EE/MLS send path не добавляет синхронных блокировок вне документированного hot-path

### Ops / deploy docs

- [ ] Изменения nginx/korus-web — см. `docs/deploy/KORUS_WEB_NGINX_MIGRATION.md`
- [ ] Read-replica / partitioning — только lab; prod до Sep 2026+ (spec 015 backlog)
