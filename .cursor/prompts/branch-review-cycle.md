# Цикл: branch review until merge-ready

Скопируй всё ниже. Укажи base branch: `main` (по умолчанию).

---

## Цель

Ветка готова к PR: review пройден, критические замечания исправлены, `buildIntegrity` PASS.

## Итерация

1. **Scope**
   ```powershell
   git status
   git log main..HEAD --oneline
   git diff main...HEAD --stat
   ```
2. **Review** — skill `superpowers-requesting-code-review`; шаблон `.cursor/skills/superpowers-requesting-code-review/code-reviewer.md`
3. **Triage** — Critical → Important → Minor; чинить Critical и Important в этом цикле
4. **Fix** — минимальные правки по file:line из review
5. **Verify**
   - `./gradlew.bat buildIntegrity`
   - UI-изменения: соответствующий Playwright tier
   - Hex: нет нового legacy в application
6. **Re-review** — только изменённые после fix файлы
7. **Repeat** до «Ready to merge: Yes» или «With fixes» с пустым Critical/Important

## Чеклист merge-ready

- [ ] Все требования spec/tasks (если feature branch) закрыты
- [ ] Нет секретов в diff
- [ ] Миграции Flyway + `docs/db/FLYWAY_AND_SCHEMA.md` при схеме
- [ ] Коммиты — только по просьбе пользователя
- [ ] Push — только `.\scripts\git-push.ps1` по просьбе

## Запрещено

- Force push main
- Amend чужих коммитов
- «LGTM» без diff review

## Успех

Assessment: **Ready to merge: Yes** + свежий `buildIntegrity` exit 0.

Краткий summary для PR body: Summary + Test plan (bullets).
