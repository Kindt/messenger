# Admin UI build (FR-112)

Статика встроенной админ-консоли: `modules/core-api/src/main/resources/admin-ui/`.

## Редактирование

| Артефакт | Источник | Сборка |
|----------|----------|--------|
| CSS | `admin-ui-build/src/styles.css` | `npm run build:styles` |
| JS bundle | `admin-ui/*.js` (порядок из `index.html`) | `npm run build:bundle` |
| i18n manifest | `admin-ui/locales/*.json` | `npm run build:manifest` |

Полный пайплайн (из этой папки или из `webui-build`):

```bash
cd modules/core-api/admin-ui-build
npm run build

# или из webui-build (те же скрипты, общие devDependencies):
cd modules/web-client/webui-build
npm run build:admin
npm run test:admin-i18n
```

Gradle: `:modules:core-api:buildAdminUiAssets`

Сгенерированные файлы коммитятся в classpath (`styles.css`, `admin.bundle.js`, `locales/manifest.json` с `keyCount`).
