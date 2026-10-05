# BotAI: текущая точка — 2026-10-05

## Результат и входы

Готовы два частных mock-стенда, каждый с7 работающими сервисами: большой Storefront+учебный frontend, Java21 backend, PG17.11, private Garage2.3, AI mock, Mailpit, Adminer. Public production auth8082/AI8080/front3000/DB V1–V4 и develop18080/gateway8091 не менялись.

| Вход | Mac | Сервер через tunnel |
|---|---|---|
| Сайт | http://127.0.0.1:13000 | http://localhost:23000 |
| База | http://127.0.0.1:18090/?pgsql=postgres&username=botai_operator&db=botai | http://localhost:28090/?pgsql=postgres&username=botai_operator&db=botai |
| Почта | http://127.0.0.1:18025 | http://localhost:28025 |

Credentials0600/ignored: `runtime/credentials.json` и `runtime/evidence/stage-credentials.json`; server `/srv/botai-integration/runtime/credentials.json`. `test@test.test`: USER+Pro/emailVerified, actual same-origin login+aiReview PASS на обоих. Пароль только private/user message. **Пользователь явно просит сохранить все старые тестовые аккаунты; ничего не удалять.**

Tunnel: owned reconnecting supervisor, control socket `runtime/stage-tunnel.sock`; PID/exec session меняются при reconnect, актуальная session только в private checkpoint. Не создавать дубликат без проверки. Local использовать127, stagelocalhost: cookies не разделяются портами. Stage FRONTEND_BASE_URL=http://localhost:23000.

## Код и доказательства

- Front `develop`: implementation `6bcd282`, final docs `f6d8babff89c9049e2d15131cc193c40a0a72d0b`. Оба образа совпадают с478-path SHA256 `b63975290568371e0bded79a5f34a99c550037d59d3c1360fc9e9f32004c8f61`; никакого нового source delta.
- Back `develop`: reviewed product/runtime/docs локально сохранены; runtime slice `add34cc`. Java38 tests, realPG/Garage SDK, quota queued-event rolling24h/HTTP1.1, strict AI validation PASS. Внешний push/live switch не выполнялись.
- Front lint89, API/auth/drawing43, types/build и независимые persistence regressions PASS. Полный same-origin HTTP roundtrip обоих образов и shared-cookie-jar isolation PASS.
- Независимый final Safari: оба login/HOME/catalog/точный answer+старыйstroke save/reload, local ADMIN list, обе свежие generated email URLs→явное UIconfirm PASS. Fresh console reload Errors0; local warning1 unreadable, причина неизвестна.
- Exact320×568 AX подтверждает полное20-number название и отдельный exam. Визуальный clipping самого CTA не проверен (native scroll limit); physical1400ms seal-hold не автоматизирован. Safari Develop restored0, inspector/RDM exited, Chrome пользователя untouched.
- Next16.3.8/sharp0.35.5: production audit0, actual standalone23 manifests на ARM/x86; CLI/tool trees отсутствуют. Dev audit17 (14high/3moderate) остаётся; это не полный SBOM/OS-CVE scan.
- Backuprestore PASS: local `runtime/backups/20261004T220704Z`, server `/srv/botai-integration/backups/20261004T232603Z`; detached PG counts+S3 bytes совпали, active volumes сохранены. Не повторять без нового риска.

## Границы и сохранность

AI только mock; registry14–20, доступен16, остальные unsupported без ложной нулевой оценки. Synthetic bank100, demo без реального XP/streak, quizzes mock. Новых промптов/paid calls/probes не делать. Public live потребует one-shot Flyway/credential separation и HTTPS/ingress gate; это отдельная работа.

Все6 recovery refs/5 original stashes и approved baseline сохранены; не pop/drop/reapply whole stash. Provenance в FRONTEND-RECOVERY/WORKLOG. Исторические research/business docs и AI preexisting dirty changes не трогать. No down-v/global prune/OS upgrades/production data replacement.

Пользовательский итог: DELIVERY.md. Практический DB smoke/схема: DATABASE-GUIDE.md. Запуск/доступ: RUNTIME-RUNBOOK.md. Actual runtime/security: RUNTIME-EVIDENCE.md, SECURITY-CODE-REVIEW.md, DEPENDENCY-SECURITY-REVIEW.md. UI матрица/ограничения: `botai-front/docs/FINAL-BROWSER-QA.md`. Дальнейшая работа — только по новому указанию пользователя; готовые сервисы и tunnel оставить running.
