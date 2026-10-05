# Точка продолжения — 2026-10-05

## Контракт

- Пользователь возобновил работу: довести до готового качественного результата. Root — оркестратор и документы; весь продуктовый код/тестирование/deploy делегировать. Разрешён выбор моделей: Astra для сложного review, Sol для реализации. Лимит: root +3 агента.
- `SPEC.md` FROZEN v1.6; реальные interfaces/SQL — `BACKEND-HANDOFF.md`. Соседние репозитории: `botai-front`, `botai-ai`; текущий `botai-back`.
- Front main `develop` становится восстановленным большим Storefront + реальным учебным приложением. Квизы остаются моками. Не терять изменения, stash, исследовательские материалы и данные.
- 20 номеров, 140 тем, подборки и пробники — отдельные долговечные `Attempt`. HOME показывает все незавершённые: «Продолжить подборку №X» или «Продолжить пробник» + название; точное восстановление ответа/рисунка/фото/результата/позиции. Пустому пользователю каталог, никогда тестовый урок.
- Front → session/CSRF backend → доверенная версия задачи + фото → private AI; user ID из сессии. Реестр AI14–20 подготовлен, утверждённый обработчик только16. Новых промптов/критериев и платных вызовов/probe не делать (AI AGENTS).
- v1.6 onboarding: ready → существующая регистрация → await profile PATCH → HOME. Авторизованный идёт прямо через сохранение в HOME. Literal `/lesson` и декоративный streak обойдены в realAPI; mockmode сохранён.

## Текущий последний gate

**Оба итоговых образа запущены; остался узкий browser gate.** Digest `b63975290568371e0bded79a5f34a99c550037d59d3c1360fc9e9f32004c8f61` /478 файлов. Next16.3.8, matching eslint, sharp0.35.5, PostCSS8.5.23, baseline override2.11.0, shadcn dev. Clean npm ci/markers, lint89, regression43, types/build PASS. Независимый production audit0; dev17 (14high/3moderate) относится к build tooling. Actual local ARM/stage x86 inventories по23 manifests: версии подтверждены, dev CLI отсутствуют. DEPENDENCY-SECURITY-REVIEW.md: source + image gate PASS; обе full same-origin API проверки PASS.

- Bounded update без новых библиотек, major downgrades или audit force. Дополнительная UI-правка: длинный HOME CTA20 использует truncate с полным доступным именем; итоговый визуальный повтор ещё нужен.
- Старый browser PASS digest `dff3…` относится к версии до security update. Повторить на обоих новых образах: fresh console, login/HOME/catalog, exact answer+stroke save/reload, fresh unconsumed generated email URL, HOME320 label/различие подборки и пробника. Старую широкую матрицу не повторять.
- **Внешняя блокировка:** Mac locked, cua_repl getState без apps/browsers; createBrowserTab iab visible true/false недоступен. Async вопрос пользователю о разблокировке задан, ответа пока нет. Root open_in_codex site+DBguide только queued, не открыты. Не заявлять новый UI PASS.
- Tool thread limit не даёт восстановить old final_browser_qa; доступен `dependency_security_review`, он может продолжить narrow UI после восстановления среды. Старый QA действительно использовал cua_repl, НЕ node_repl bootstrap: createBrowserTab(iab,url) без visible:true работал. Сейчас documented visible:false тоже отказал. Никаких raw CDP/OS unlock обходов; повторить только после изменения доступности.
- Tunnel восстановлен: stage login/HOME/attempt открылись, reload session-bootstrap ещё не PASS. Runtime spaced login/CSRF/me/home200 (0.03–0.13s), remote healthy. Перед final QA закрыть task-owned старые вкладки localhost13000: cookies общие со stage localhost23000, фоновые запросы могут сбрасывать SESSION. Это гипотеза. Local использовать только12713000.
- Front локально сохранён в develop `6bcd2821a430268cfcee37eccde9f5fff7490423` обычным hook; source digest совпадает с обоими образами. Пока UI внешне недоступен, это сохранение проверенного кода, не финальный UI PASS. No push/main merge.

## Уже подтверждено — не повторять исследование

| Область | Результат и источник |
|---|---|
| Backend | 38 Java21 tests, 0 failures/skips; root читал JUnitXML. Реальные PG17/Mailpit/Garage через AWS SDK. V1–V4 неизменны; V5/V6. V4 upgrade сохраняет users/passwords/sessions. `CORE-IMPLEMENTATION.md`, `GRADING-IMPLEMENTATION.md`. |
| AI | Internal auth/capabilities/prepared16/provenance, private network. 460 offline PASS /3 existing skips /1 probe deselected; transport integer deadline360 fix:64 internal PASS. HTTP1.1 JDK adapter исправил реальный multipart h2c422. Подмножества тестов не суммировать. `ai-contract.md`, AI docs/grading/internal-api.md. |
| Данные | 140 source topics,100 оригинальных synthetic tasks (5/номер), exact20 template. Draft2027 max33:1–13=1,14=2,15=3,16=2,17=2,18=3,19=4,20=4. Полный чужой банк не копировался. Источники/решение в SPEC. |
| Security | Независимый fresh Astra code/HTTP review PASS для local/isolated mock. Ownership/CSRF/currentrole/disabled/private bytes/readonly grants/queue fencing/provenance проверены. Quota исправлена: immutable queued event time, rolling24h под lock; olddraft/cancel/retry/concurrent29+2→200/429. `SECURITY-CODE-REVIEW.md`. |
| Persistence | Все5 независимых findings CLOSED: erase-all, destructive Retry, SPA unmount, stale polling GET, clean drawing hydration. Owner-reset in-memory sessions + snapshot ACK/generation fencing; remote revision+dirty → явный conflict без silent rebase. Actual hook/store regressions и дополнительные race/isolation PASS. `FINAL-FLOW-REVIEW.md`. |
| Front final source | lint89 guards, API/auth/drawing43, tsc, production build PASS после security update. Богатый Storefront,20 каталог/темы/counts, реальные attempts/exams/favourites/profile/avatar/admin/onboarding. `front/docs/INTEGRATION-EVIDENCE.md`. |
| Runtime | Local+stage all7 services, full direct и same-origin HTTP smoke PASS: bytes→normalize→private S3→worker→AI mock16→demo result; unsupported14 без оценки; demo XP/streak0. PG+S3 backup/restore на обоих PASS. `RUNTIME-EVIDENCE.md`. |

## Browser доказательства

`front/docs/FINAL-BROWSER-QA.md` и `INTEGRATION-EVIDENCE.md` — точные IDs/скриншоты. Подтверждено:

- Две подборки1+2/7 и пробник20 сохраняют ответы, рисунок, фото, результат, позицию после logout/login/reload; все видны на HOME с различимыми CTA. Empty state и чужой attempt без утечки.
- Pro16 invalidSVG → actualPNG → cancel → explicit retry → async2/2 DEMO. Admin filter/detail/events/raw validated JSON/private photo; USER denied.
- Profile name/level/day/avatar upload/remove/reload. Fresh registration + реальные вопросы onboarding + Mailpit resend/verify + relogin. Account switch очищает предыдущую identity/session state.
- Большой Storefront1440/375/320, auth/guest CTA и геометрия. Adminer operator фактически открыл4 безопасных представления и вышел.
- Attempt `94dd0188-5c60-479f-be08-e21d54d63e47`: draw/save/reload, erase-all/reloadempty, rapid input/SPA/native Back/resume. При остановке только backend autosave500 + Retry500 сохраняют ответ98765 и рисунок; после restart RetrySaved + reload сохраняют оба. Это transport failure, не OS offline.
- Первый local production12713000: fresh tab login/HOME/catalog/resume43210+stroke, новый email token с точным127 URL успешно подтверждён; console empty. Повтор после security update нужен.
- Ограничение инструмента: CUA не поддерживает timed1400ms hold; сам старый commitment seal gesture не проверен вручную. Следующие вопросы/ready/register/save проверены реальным UI. Не заявлять полный seal-to-ready manual pass.

## Запуск и доступ

- Local Docker `botai-integration-local`: frontend **http://127.0.0.1:13000**, backend18082, Adminer18090, Mailpit18025, PG17.11/Garage2.3/AI mock приватные. Credentials `runtime/credentials.json`, env `runtime/.env.integration`, chmod0600/ignored; значения не печатать.
- Server SSH `ege-server` → root@135.106.182.11:22, key `~/.ssh/botai`. Три исторических чата подтвердили адрес; временный banner timeout прошёл. Повторно не искать без нового сбоя.
- Stage `/srv/botai-integration/{back,front,ai,runtime,backups}`, Docker `botai-integration-stage`, отдельные секреты/volumes. Front remote13000; через tunnel **http://localhost:23000**, backend28082, Adminer28090, Mailpit28025. Stage test/operator credentials скопированы в local ignored0600 `runtime/evidence/stage-credentials.json`; service secrets не копировались.
- Разные hostname обязательны: cookies игнорируют port. Local127 и stagelocalhost изолированы; shared-cookie-jar frontend smoke PASS. Stage FRONTEND_BASE_URL=http://localhost:23000; actual email origin smoke PASS на обоих.
- Tunnel восстановлен: supervisor session28816; control socket `runtime/stage-tunnel.sock`; master PID changes on reconnect; spaced health requests200. Не запускать дублирующий tunnel без проверки.
- Existing prod auth8082/AI8080/front3000/PG V1–V4 + develop18080/gateway8091 **не менялись**,4 identities/starttimes сверены. Публичный домен/TLS не переключался.
- Stage build bounded: dedicated BuildKit1.5GiB/1.5CPU/parallel1, Gradle384MiB/workers2, front heap1GiB. Никаких global prune, OS/Docker upgrade или down-v. Docker npm layer копирует `.npmrc` с ignore-scripts=true. Curated context исключает secrets/.dev-data/env/history/research.
- Backuprestore local `runtime/backups/20261004T220704Z`, server `/srv/botai-integration/backups/20261004T232603Z`: detached PG counts + S3 witness bytes равны, originals сохранены. Не повторять без нового риска.
- Public/live release требует отдельного gate: вынести migration superuser из long-lived backend env в one-shot job, проверить HTTPS/ingress/production policy. Текущий результат — работающий частный mock-стенд, не сертификация боевого AI.

## Сохранность develop

Front исходный HEAD `c74f8cd6f40f89de3cc03e9bf91fe5c8d8cb39f8`. Approved baseline ref `refs/recovery/2026-10-05/approved-baseline`=`3fff0f53a43905c13ed66a9bbfac3e02428d4ac8`. Все5 originals закреплены refs; hashes/provenance в WORKLOG/FRONTEND-RECOVERY. Не pop/drop/reapply whole stash. Backups `~/.codex/backups/botai-front-recovery-2026-10-05` и `botai-front-integration-2026-10-05`.

Front commit448 files, +21074/-2741; private/prohibited scan0. Все6 refs/5 stash сверены. Исторические Business Model/PaywallNotes/BigSiteNotes/research/mascot docs исключены,50 untracked не удалены;3 tracked historical docs остаются отдельно. QA checkpoint может быть изменён после commit. Точная allowlist7 project SKILL.md +SCOPE исправила ignore/precommit без bypass. Onest license whitespace сохранён verbatim. AI preexisting dirty changes сохранить. Backend runtime slice commit `add34cc`; Reviewed product/docs checkpoint сохранён local develop: только явные пути, никаких secrets/generated artifacts.

## Завершение

1. После восстановления браузера выполнить только указанный narrow gate на обоих окончательных образах; любые defects делегировать frontend owner, затем rebuild только при source delta.
2. Backend product/docs checkpoint сохранён; после final UI обновить QA evidence. Stack/tunnel оставить running.
3. Пользовательский brief — DELIVERY.md; actual DB — DATABASE-GUIDE.md. Честно назвать synthetic seed, mock16, unsupported другие extended и внешний UI blocker. Пока не объявлять весь запрос завершённым.
