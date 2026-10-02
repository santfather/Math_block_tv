# PROGRESS — Math Gate

Обновляется после каждой фазы. Формат отчёта — раздел 9 в `ROADMAP_tv.md`.

## Текущий статус

- Фаза 0 (разведка устройства): **готово**.
- Фаза 1 (каркас проекта): **готово**.
- Фаза 2 (чистое ядро): **готово**.
- Фаза 3 (детект YouTube): **готово** — проверено на реальном ТВ.
- Фаза 4 (блокирующий экран и UI с пульта): **готово** — проверено на реальном ТВ.
- Фаза 5 (принуждение/enforcement): **готово** — проверено на реальном ТВ.
- Фаза 6 (живучесть и самовосстановление): **готово** — проверено на реальном ТВ.
- Фаза 7 (родительский режим): **готово** — проверено на реальном ТВ.
- Фаза 8 (закрытие обходов): **готово** — матрица обходов проверена на реальном ТВ.
- Фазы 9-10: не начаты.

---

## Фаза 0: разведка устройства — готово

- Что сделано: собраны параметры ТВ через adb (с рабочей машины): `ro.build.version.sdk = 31`,
  `ro.build.version.release = 12`, fingerprint `STT2.230505.001.S136`, model `BRAVIA 4K VH22`,
  `characteristics = nosdcard,tv`, `boot_count = 467`, launcher `com.google.android.apps.tv.launcherx`
  (Google TV), аккаунт Google присутствует, accessibility выключена.
- Что проверено на устройстве: подтверждены 3 YouTube-пакета — `com.google.android.youtube.tv`,
  `com.google.android.youtube.tvkids`, `com.google.android.youtube.tvmusic`.
- Отклонения от roadmap и причины: фаза 9 (Device Owner) неприменима — устройство уже настроено
  с аккаунтом Google, первичная настройка не выполняется.
- Открытые вопросы к пользователю: поведение при выключении пультом (сон/полное выключение) и
  режим быстрого запуска не проверены руками (влияет на `resetOnPowerLoss`, фаза 6).
- Следующий шаг: заполнить `DEVICE_NOTES.md` (выполнено) и перейти к фазе 1.

## Фаза 1: каркас проекта — готово

- Что сделано: Gradle-каркас (Kotlin 2.0.21, AGP 8.7.3, wrapper 8.11.1), `AndroidManifest.xml`
  без разрешения `INTERNET`, ресурсы, пакеты `core/`, `data/`, `detect/`, `service/`, `ui/`.
- Что проверено на устройстве: APK устанавливается на ТВ (adb), `SetupActivity` запускается
  (`mFocusedApp = com.mathgate/.ui.SetupActivity`).
- Отклонения от roadmap и причины: нет.
- Открытые вопросы к пользователю: нет.
- Следующий шаг: фаза 2.

## Фаза 2: чистое ядро — готово

- Что сделано:
  - `ProblemGenerator` — детерминированная генерация примеров (4 уровня, `Random(seed)`,
    защита от повтора, ответ всегда ≥ 0).
  - `GateEngine` — конечный автомат по разделу 4: события `YouTubeForeground/Background`,
    `ScreenOff/On`, `Tick`, `AnswerSubmitted`, `ParentOverride`, `Reboot`; эффекты
    `PersistState`, `ShowChallenge`, `HideChallenge`. Время — по `SystemClock.elapsedRealtime()`.
    Реализованы правила 1-6, нарастающий кулдаун за неверные ответы.
  - `GateState` — `Idle` / `Counting` / `ChallengePending(problem, attempts, cooldownUntilElapsedMs)`.
  - `DataStoreGateStore` — персистентность состояния и настроек на Preferences DataStore.
  - `AndroidClock` — `Clock` на базе `SystemClock.elapsedRealtime()` и `BOOT_COUNT`.
- Что проверено на устройстве: не применимо (ядро без `android.*`, проверяется на JVM).
- Отклонения от roadmap и причины: нет.
- Открытые вопросы к пользователю: нет.
- Следующий шаг: фаза 3 — детект YouTube.

### Тесты и покрытие (фаза 2)

- `./gradlew :app:testDebugUnitTest :app:jacocoTestReport` → **BUILD SUCCESSFUL**, 22 теста.
- Jacoco, пакет `com/mathgate/core`: LINE **95.0%** (153/161), BRANCH **88.3%** (68/77),
  INSTRUCTION **93.5%** (975/1043). DoD (line ≥ 90%) выполнен.

## Фаза 3: детект YouTube — готово

- Что сделано:
  - `detect/ForegroundDetector.kt` — интерфейс + чистый `ForegroundFilter` (классификация
    пакета: `WATCHED` / `NOT_WATCHED` / `IGNORE`) и whitelist системных оверлеев
    (`com.android.systemui` и т.п.).
  - `detect/A11yForegroundDetector` — основной детектор: события окна → `StateFlow<Boolean>`,
    оверлеи и `null` не меняют состояние. Чистый Kotlin, покрыт тестами.
  - `detect/UsageStatsDetector` — резервный детектор: опрос `UsageStatsManager.queryEvents`
    (последний `ACTIVITY_RESUMED`), та же классификация.
  - `service/GuardAccessibilityService` — принимает `onAccessibilityEvent`, реагирует только на
    `TYPE_WINDOW_STATE_CHANGED`, фильтрует по отслеживаемым пакетам (из настроек, с дефолтом),
    пишет переходы в `Log` и `EventLog`.
  - `service/GuardForegroundService` — минимальная версия: foreground-сервис с уведомлением,
    динамический ресивер `ACTION_SCREEN_ON/OFF`, опрос резервного детектора (только когда
    accessibility выключена). Полная логика (watchdog, SelfHealer) — фаза 6.
  - `core/EventLog` — кольцевой лог на 500 записей (in-memory), общий для сервисов.
  - `MathGateApp` — держит общие компоненты (`eventLog`, `gateStore`, `a11yForegroundDetector`).
  - `SetupActivity` поднимает `GuardForegroundService` при открытии приложения.
- Что проверено на устройстве (Sony BRAVIA, Android 12, `logcat -s MathGate`):
  - доступность включена через adb (`settings put secure enabled_accessibility_services`);
  - открытие YouTube → `foreground -> true (com.google.android.youtube.tv)`;
  - HOME → `foreground -> false (com.google.android.apps.tv.launcherx)`;
  - повторное открытие YouTube из лаунчера → конечное состояние снова `true`;
  - экран в сон (`KEYCODE_SLEEP`) → `screen OFF (interactive=false)`, пробуждение
    (`KEYCODE_WAKEUP`) → `screen ON (interactive=true)`;
  - панель громкости отдельного a11y-события не даёт (текущий `systemui`-whitelist достаточен).
- Отклонения от roadmap и причины:
  - Добавлен `core/EventLog.kt` (нет в §3) — нужен общий кольцевой лог для отладки и будущей
    статистики (фаза 7); оставлен чистым Kotlin и покрыт тестами.
  - `GuardForegroundService` начат в фазе 3 (в §5 он отнесён к фазе 6), т.к. задача 3 фазы 3
    требует подписку на `SCREEN_ON/OFF` «в foreground service».
  - `onAccessibilityEvent` реагирует только на `TYPE_WINDOW_STATE_CHANGED`: `TYPE_WINDOWS_CHANGED`
    присылал событие от лаунчера в момент снятия его окна и оставлял состояние в `false`
    (подробности в `DEVICE_NOTES.md`).
  - Объединение основного и резервного детекторов в один источник для автомата отложено до
    фазы 5 (там происходит подписка движка на сигнал).
- Открытые вопросы к пользователю: нет.
- Следующий шаг: фаза 4 — `BlockActivity` (Compose for TV, D-pad, цифровая панель).

### Тесты и покрытие (фаза 3)

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:jacocoTestReport` → **BUILD SUCCESSFUL**,
  34 теста (добавлены `EventLogTest`, `ForegroundFilterTest`, `A11yForegroundDetectorTest`).
- Jacoco, `com/mathgate/core`: LINE **95.5%** (171/179), BRANCH **88.6%** (70/79),
  INSTRUCTION **94.2%** (1112/1180).

## Фаза 4: блокирующий экран и UI с пульта — готово

- Что сделано:
  - `core/ChoiceOptions.kt` — режим 4 вариантов (D-09): детерминированный по `seed` набор из
    4 различных неотрицательных чисел, всегда содержащий правильный ответ, отсортированный.
  - `ui/theme/Color.kt`, `ui/theme/Theme.kt` — высококонтрастная палитра и две темы:
    `MathGateTheme` (material3, для обычных экранов) и `MathGateTvTheme` (tv-material3,
    обязательна для Compose-for-TV компонентов).
  - `ui/BlockActivity.kt` — блокирующий экран целиком: крупный пример (шрифт 72sp),
    поле ответа, цифровая панель 0–9 + «Стереть» + «Ответить»; баннер результата
    («Правильно!» / «Попробуй ещё» + «Подожди N с»); `FLAG_KEEP_SCREEN_ON`;
    `OnBackPressedCallback` — BACK игнорируется. Навигация D-pad стрелками, фокус по
    умолчанию на цифре «5»; цифровые клавиши пульта через `onPreviewKeyEvent`
    (`Key.Zero..Nine`, `NumPad0..9`, `Backspace`/`Delete`). Режим 4 вариантов включается
    настройкой `multipleChoice`, по умолчанию выключен (цифровой ввод).
  - Логика вынесена в движок (`GateEngine`): верный ответ → `feedback = CORRECT` и авто-закрытие,
    неверный → движок сам генерирует новый пример и кулдаун.
- Что проверено на устройстве (Sony BRAVIA, Android 12, `adb`):
  - экран запускается из `adb shell am start -n com.mathgate/.ui.BlockActivity`
    (`mCurrentFocus = com.mathgate/.ui.BlockActivity`), рендерит пример, поле «Ответ»,
    панель 0–9 + «Стереть»/«Ответить», фокус на «5»;
  - D-pad: OK вводит цифру, стрелки перемещают фокус, «Стереть» очищает ввод;
  - цифровые клавиши пульта вводят цифры;
  - верный ответ → зелёное «Правильно!» и авто-закрытие (`mCurrentFocus` вернулся в YouTube);
  - неверный ответ → красное «Попробуй ещё» + «Подожди N с» и новый пример;
  - BACK (keycode 4) экран не закрывает, HOME (keycode 3) уходит в лаунчер;
  - режим 4 вариантов: 4 отсортированных варианта (верный присутствует), выбор верного →
    «Правильно!» + авто-закрытие, выбор неверного → «Попробуй ещё» + кулдаун + новый пример.
- Отклонения от roadmap и причины:
  - Добавлен debug-only `app/src/debug/AndroidManifest.xml`: в release `BlockActivity` объявлена
    `android:exported="false"`, поэтому `adb shell am start` падал с `SecurityException`. Debug-
    override помечает Activity экспортируемой только в debug-сборке, release остаётся закрытым.
- Открытые вопросы к пользователю: нет.
- Следующий шаг: фаза 5 — enforcement (спайк запуска `BlockActivity` из
  `AccessibilityService` vs оверлей `TYPE_ACCESSIBILITY_OVERLAY`, D-11; пауза медиа).

### Тесты и покрытие (фаза 4)

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:jacocoTestReport` → **BUILD SUCCESSFUL**,
  39 тестов (добавлен `ChoiceOptionsTest`, 5 тестов).
- Jacoco, `com/mathgate/core`: LINE **94.8%** (183/193), BRANCH **87.4%** (76/87),
  INSTRUCTION **94.0%** (1180/1255). DoD (line ≥ 90%) выполнен.

## Фаза 5: принуждение (enforcement) — готово

- Что сделано:
  - `core/GateEngine` — новый эффект `WarnAboutLimit` (предупреждение за `warnBeforeMs` до лимита,
    один раз за цикл счётчика; `warnBeforeMs = 0` отключает; флаг сбрасывается в `enterChallenge`).
    Тикер уже проверяет лимит не реже раза в секунду (`TICK_INTERVAL_MS = 1_000L`).
  - `service/ChallengeEnforcer` — платформенная сторона эффектов: запуск `BlockActivity` поверх
    YouTube (`startActivity` + `FLAG_ACTIVITY_NEW_TASK`), пауза/возобновление медиа
    (`AudioManager.dispatchMediaKeyEvent(KEYCODE_MEDIA_PAUSE/PLAY)`, D-06), тихий `Toast`
    предупреждения. Все вызовы обёрнуты в try/catch с записью в `EventLog`.
  - `service/GateCoordinator` — единый владелец движка в процессе: держит `StateFlow<GateState?>`,
    создаёт `GateEngine` из персистентного состояния (`reconcile`: `Counting → Idle` по A12),
    подписан на `ForegroundDetector` (главный сигнал) и на `ACTION_SCREEN_ON/OFF` (динамический
    ресивер), крутит тикер раз в секунду и исполняет эффекты (`PersistState`/`ShowChallenge`/
    `HideChallenge`/`WarnAboutLimit`). `submitAnswer(value)` возвращает `CORRECT`/`WRONG`/`IGNORED`
    (кулдаун — `IGNORED`).
  - `ui/BlockActivity` переписан как тонкое представление над `GateCoordinator`: UI-контроллер
    (`BlockController`) в обычном потоке берёт pending-пример из общего движка (`realMode`),
    а вне pending-состояния (например, `adb shell am start`) работает автономно на демо-примере,
    чтобы каждый элемент оставался проверяемым.
  - `MathGateApp` — ленивые `challengeEnforcer` и `gateCoordinator`; `GuardAccessibilityService`
    и `GuardForegroundService` стартуют координатор (`gateCoordinator.start()`, идемпотентно).
  - `res/values/strings.xml` — строка `block_warning` («Скоро перерыв…»).
- Что проверено на устройстве (Sony BRAVIA, Android 12, `adb`). Для ускорения ручных тестов
  `limitMs` временно снижался до 60 с и `warnBeforeMs` до 20 с, затем значения возвращены
  (15 мин / 60 с) и приложение переустановлено:
  - **A1** — YouTube на переднем плане → через 40 с `limit warning shown` (Toast), через 60 с
    `challenge shown`, `mCurrentFocus = com.mathgate/.ui.BlockActivity`; медиа-пауза отправлена.
  - **A2** — верный ответ → `challenge hidden`, `mCurrentFocus` вернулся в YouTube, видео
    возобновлено; счётчик сброшен (следующий блок снова через полные 60 с).
  - **A3** — неверный ответ → блок остаётся, красное «Попробуй ещё» + «Подожди N с», новый пример.
  - **A4** — HOME → лаунчер; повторный запуск YouTube → `challenge shown`, блок возвращается,
    тот же pending-пример (состояние сохраняется).
  - **A5** — 39 с просмотра + уход в лаунчер + повторный YouTube + 21 с → суммарно лимит,
    блок появился (накопление времени работает).
  - **A6** — по конструкции: время считается по `SystemClock.elapsedRealtime()` (`AndroidClock`),
    смена настенных часов не влияет (D-03); `Reboot` сбрасывает кулдаун, но сохраняет `accumulatedMs`.
- Отклонения от roadmap и причины:
  - Оверлей `TYPE_ACCESSIBILITY_OVERLAY` (D-11) **не понадобился**: спайк подтвердил, что на
    Android 12 `AccessibilityService` может запустить Activity поверх YouTube (детали в
    `DEVICE_NOTES.md`).
  - Экранные события (`SCREEN_ON/OFF`) слушают и `GuardForegroundService`, и `GateCoordinator`
    (динамический ресивер). Дублирование намеренное: координатор самодостаточен, а сервис
    остаётся точкой входа фазы 6 (watchdog). Свести к одному источнику при желании — фаза 6.
- Открытые вопросы к пользователю: нет.
- Следующий шаг: фаза 6 — устойчивость (boot receiver, watchdog/self-healer, `resetOnPowerLoss`).

### Тесты и покрытие (фаза 5)

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:jacocoTestReport` → **BUILD SUCCESSFUL**,
  41 тест (добавлены 2 теста `WarnAboutLimit` в `GateEngineTest`).
- Jacoco, `com/mathgate/core`: LINE **95.0%** (191/201), BRANCH **88.2%** (82/93),
  INSTRUCTION **94.4%** (1222/1294). DoD (line ≥ 90%) выполнен.

## Фаза 6: живучесть и самовосстановление — готово

- Что сделано:
  - `data/GateStore` — в `PersistedState` добавлен `bootCount` (номер загрузки, при которой
    состояние было записано) и интерфейс `BootInfo(bootCount, cleanShutdown)` +
    `readBootInfo()`/`writeBootInfo()`. `DataStoreGateStore` хранит ключи `boot_count`,
    `clean_shutdown`, `state_boot_count`.
  - `service/BootReceiver` (D-07) — на `BOOT_COMPLETED`/`LOCKED_BOOT_COMPLETED` через
    `goAsync()`: читает `resetOnPowerLoss` и `BootInfo`; при `resetOnPowerLoss && !cleanShutdown`
    сбрасывает состояние (`Idle(0L)`); всегда записывает `BootInfo(bootCount, cleanShutdown=false)`
    и поднимает `GuardForegroundService` + `SelfHealer.ensureAccessibilityEnabled()`.
  - `service/SelfHealer` (D-08) — `ContentObserver` на `ENABLED_ACCESSIBILITY_SERVICES`
    **и** `ACCESSIBILITY_ENABLED`: дописывает наш компонент обратно в список и включает мастер-
    переключатель, если он был выключен. Требует `WRITE_SECURE_SETTINGS` (выдаётся один раз
    через adb). Запись идемпотентна — наблюдатель сходится без цикла.
  - `detect/DetectorHeartbeat` — чистый Kotlin-маркер живости основного канала
    (`mark()`/`isStale()`); `GuardAccessibilityService` отмечает его на каждом событии.
  - `service/GuardForegroundService` — watchdog: если accessibility включён и heartbeat свежий,
    резервный канал молчит; иначе опрашивает `UsageStatsDetector` и передаёт результат в
    координатор (`onFallbackForeground`). Резервный канал подаёт сигнал **только при наличии
    реальных показаний** (нет прав/нет свежих переходов → состояние основного канала не
    переопределяется). `ACTION_SHUTDOWN` в этом же сервисе пишет `BootInfo(cleanShutdown=true)`.
  - `service/GateCoordinator` — при старте сравнивает `persisted.bootCount` с текущим
    `clock.bootCount()`: после перезагрузки `ChallengePending` сохраняется, но кулдаун
    сбрасывается (`elapsedRealtime` обнулился); `Counting` восстанавливается как `Idle(accumulated)`.
    Новый метод `onFallbackForeground(watched)`.
- Что проверено на устройстве (Sony BRAVIA, Android 12, `adb`; состояние сверялось чтением
  `files/datastore/mathgate.preferences_pb` через `run-as`):
  - **A7/A8** — реальная перезагрузка ТВ (`adb reboot`): `boot_count` 467→468, `BOOT_COMPLETED`
    доставлен (с задержкой ~1.5 мин после `sys.boot_completed`), `boot receiver: guard service
    started (boot=468)`, foreground-сервис и accessibility поднялись автоматически; состояние
    сохранено.
  - **A9** — «потеря питания» при `resetOnPowerLoss=false` (по умолчанию): после ребута лог
    `power loss detected` отсутствует, `accumulated_ms` сохранён.
  - **A10** — то же при `resetOnPowerLoss=true` (временно, затем возвращено `false`): лог
    `power loss detected: gate state reset`, `accumulated_ms = 0`.
  - **A11** — самовосстановление accessibility: удаление сервиса из списка → `accessibility
    service re-added`; выключение мастер-переключателя → `accessibility master switch re-enabled`;
    сервис переподключается (`accessibility connected`).
  - **A12** — `am force-stop` во время `Counting` → повторный запуск приложения: сервис и
    координатор поднялись, `state_type` `Counting → Idle`, `accumulated_ms` восстановлен
    (182 985 мс без потерь).
- Отклонения от roadmap и причины:
  - `LOCKED_BOOT_COMPLETED` объявлен в манифесте, но на ТВ нет экрана блокировки — фактически
    приходит `BOOT_COMPLETED` (в `DEVICE_NOTES.md`).
  - Порог «тишины» основного канала — `HEARTBEAT_STALE_MS = 60_000L` (roadmap: «N минут»);
    резервный канал подаёт сигнал только при реальных показаниях UsageStats, поэтому ложное
    переключение при простое основного канала не сбрасывает счётчик.
- Открытые вопросы к пользователю: нет.
- Следующий шаг: фаза 7 — экран родителя (PIN), настройки лимита/сложности/пакетов, статистика.

### Тесты и покрытие (фаза 6)

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:jacocoTestReport` → **BUILD SUCCESSFUL**,
  45 тестов (добавлены `DetectorHeartbeatTest` — 3 теста; `GateEngineTest` — сохранение
  `ChallengePending` со сбросом кулдауна после ребута).
- Jacoco, `com/mathgate/core`: LINE **95.0%** (191/201), BRANCH **88.2%** (82/93),
  INSTRUCTION **94.4%** (1222/1294). DoD (line ≥ 90%) выполнен.

## Фаза 7: родительский режим — готово

- Что сделано:
  - `core/PinPolicy` — правила PIN: формат 4–6 цифр (`MIN_LENGTH`/`MAX_LENGTH`), бесплатные
    попытки (`FREE_ATTEMPTS = 3`) и нарастающая пауза `0, 0, 0, 5 с, 10 с, 20 с, …` с потолком
    5 мин (`lockoutMs`). Ядро осталось без `android.*` и без криптографии.
  - `data/PinHasher` — PBKDF2-HMAC-SHA256, 120 000 итераций, 256 бит; `newSalt()`,
    `hash(pin, salt)`, `verify(pin, salt, hash)` со сравнением через `MessageDigest.isEqual`
    (constant-time). PIN хранится **только хешем с солью** (`PinCredentials`).
  - `data/GateStore` — чтение/запись `PinCredentials`, `PinGuard` (`failedAttempts`,
    `lockoutUntilWallMs`) и `PinGuard()` по умолчанию; `readPin()`/`writePin()`.
  - `ui/PinPad` — общий PIN-пад (маскированные слоты, D-pad, цифровые клавиши пульта,
    `Backspace`/`Delete`); используется и мастером, и PIN-гейтом.
  - `ui/SetupActivity` переписан в мастер первичной настройки: `Welcome → Permissions →
    Create PIN → Confirm PIN → Done`. На шаге разрешений статусы и кнопки «Открыть»
    (accessibility + usage access); PIN сохраняется хешем (PBKDF2 считает в `Dispatchers.Default`,
    показывая «Проверка…»). Если PIN уже есть, лаунчерный вход уводит прямо в `ParentActivity`.
  - `ui/ParentActivity` — PIN-гейт (`PinGateScreen`) и экран «Родительские настройки»:
    дневной лимит (пресеты 5–60 мин), сложность (уровни 1–4), режим ввода (Цифры/Варианты),
    список контролируемых пакетов (YouTube/tvkids/tvmusic), переключатели «Предупреждать до
    лимита» и «Сбрасывать таймер после отключения питания», статистика
    (блокировки/неверные ответы/всего просмотра), кнопки «Разблокировать сейчас»,
    «Сбросить таймер», «Назад». Пауза за неверные PIN — с живым отсчётом. `ParentActivity`
    объявлена `exported=true` (системный `settingsActivity` из другого процесса), защита — PIN.
  - `ui/BlockActivity` — запасной вход в родительский режим: долгое нажатие на заголовок
    «Реши пример» (`combinedClickable`) открывает `ParentActivity`.
  - `core/GateEngine`/`service/GateCoordinator` — `currentStats()` (блокировки, неверные ответы,
    всего просмотра) и `updateSettings(settings)` (применение без перезапуска) + `resetTimer()`.
- Что проверено на устройстве (Sony BRAVIA, Android 12, `adb`; подробности в `DEVICE_NOTES.md`):
  - мастер проходит все шаги, PIN-пад работает с пульта и цифровых клавиш, PIN сохраняется;
  - неверный PIN отклоняется; четвёртая ошибка подряд → пауза с живым отсчётом (5 с);
  - верный PIN → «Родительские настройки»; смена лимита 15 → 20 мин применяется сразу и
    сохраняется после перезапуска приложения;
  - «Разблокировать сейчас» → тост «Доступ к YouTube открыт» и закрытие;
  - долгое нажатие на заголовок `BlockActivity` открывает PIN-гейт;
  - найдены и исправлены два бага UI: D-pad-фокус на шаге разрешений и недостижимая кнопка «OK»
    на PIN-гейте (компактнее пад + прокрутка).
- Отклонения от roadmap и причины:
  - Вместо Argon2 (roadmap) используется PBKDF2-HMAC-SHA256: он доступен в стандартной
    библиотеке JVM/Android без сторонних зависимостей при тех же параметрах стойкости.
  - `ParentActivity` помечена `exported=true` — иначе системный вход `settingsActivity`
    недоступен; защита обеспечивается PIN, а не export-флагом.
- Открытые вопросы к пользователю: нет.
- Следующий шаг: фаза 8 — закрытие обходов (другие клиенты YouTube, браузер, Cast и т.д.).

### Тесты и покрытие (фаза 7)

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:jacocoTestReport` → **BUILD SUCCESSFUL**,
  **58 тестов** (добавлены `PinPolicyTest` — 4, `PinHasherTest` — 4; `GateEngineTest` — статистика,
  `updateSettings`, `ResetTimer`).
- Jacoco, `com/mathgate/core`: LINE **96%** (224/233), BRANCH **89%** (101/113),
  INSTRUCTION **95%** (1471/1537). DoD (line ≥ 90%) выполнен.

## Фаза 8: закрытие обходов — готово

- Что сделано:
  - `core/Settings` — браузер ТВ `com.tvwebbrowser.v22` добавлен в `DEFAULT_WATCHED_PACKAGES`
    (по умолчанию блокируется целиком: YouTube в браузере — то же видео, а URL без чтения
    контента не виден).
  - `ui/ParentActivity` — `com.tvwebbrowser.v22` добавлен в `WatchedPackageOptions`
    (опция «Браузер» в списке контролируемых приложений, снимается/ставится родителем).
  - `res/values/strings.xml` — строка `package_browser` («Браузер»).
  - Заполнена матрица обходов в `TEST_PLAN.md` (11 строк: закрыто / частично / не закрыто +
    причина); список ограничений и рекомендации — в `PARENT_GUIDE.md`; наблюдения устройства —
    в `DEVICE_NOTES.md`.
- Что проверено на устройстве (Sony BRAVIA, Android 12, `adb`):
  - браузер: открытие `com.tvwebbrowser.v22` → `foreground -> true`, уход → `false`;
  - голосовой поиск/ассистент: у `com.google.android.katniss` нет launcher-активности; запуск по
    ссылке (`intent VIEW youtu.be`) открывает тот же `com.google.android.youtube.tv` → ловится;
  - accessibility: удаление службы из списка → самовосстановление за **~80 мс**;
  - `am force-stop com.mathgate` — **не восстанавливается** (нет процесса, служебные
    `enabled_accessibility_services = null`) ни за 10, ни за 30 с; подъём только при запуске
    приложения или перезагрузке → честно задокументированный обход;
  - PiP: фичи `android.software.picture_in_picture` нет на прошивке, YouTube не объявляет
    `supportsPictureInPicture` → обхода нет;
  - сторонние клиенты YouTube на ТВ не установлены.
- Отклонения от roadmap и причины:
  - URL-фильтр в браузере (roadmap) неприменим без чтения контента
    (`canRetrieveWindowContent=false`) — браузер блокируется целиком.
  - Удаление приложения и «Остановить» на обычном ТВ без Device Owner закрыть нельзя (фаза 9
    неприменима: устройство с аккаунтом Google) — вынесены в ограничения `PARENT_GUIDE.md`.
- Открытые вопросы к пользователю: нет.
- Следующий шаг: фаза 9 (Device Owner) — только по явной просьбе; иначе фаза 10 (приёмка,
  сборка, поставка).

### Тесты и покрытие (фаза 8)

- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:jacocoTestReport` → **BUILD SUCCESSFUL**,
  **58 тестов** (новых тестов нет: изменения фазы 8 — конфигурация и UI, ядро не менялось).
- Jacoco, `com/mathgate/core`: LINE **96%** (224/233), BRANCH **89%** (101/113),
  INSTRUCTION **95%** (1471/1537). DoD (line ≥ 90%) выполнен.

---

## Известные проблемы

- Дефолт `multipleChoice` продублирован в `DataStoreGateStore.readSettings()` (`?: false`)
  вместо значения из `Settings`; можно свести к одному источнику.
- Настройки (список пакетов) перечитываются детектором при подключении сервиса; применение
  изменений без перезапуска — фаза 7 (реализовано `updateSettings`, движок читает настройки
  из общего стора).
- При возврате в уже запущенный YouTube лаунчер на короткое время (< 3 с) снова считается
  передним планом — конечное состояние корректно (детали в `DEVICE_NOTES.md`).
- `GateCoordinator` живёт в процессе приложения; после гибели процесса сервис поднимается
  (`START_STICKY`/`AccessibilityService`), а накопленное время восстанавливается (A12, фаза 6).
- Резервный канал watchdog опирается на «Usage access» (`PACKAGE_USAGE_STATS`); без него он
  не переопределяет состояние основного канала (безопасный режим, фаза 6).
- `BOOT_COMPLETED` на этом ТВ приходит с задержкой ~1.5 мин после `sys.boot_completed`
  (сначала получают системные приложения) — учитывать в ручных проверках.

## Следующий шаг

Фаза 9 (Device Owner) неприменима без сброса ТВ (есть аккаунт Google) и выполняется только по
явной просьбе пользователя. Иначе — фаза 10: приёмка, финальная сборка и поставка
(`PARENT_GUIDE.md` — итоговая инструкция).
