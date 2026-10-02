# Math Gate — родительский контроль YouTube на Sony Bravia

Приложение для Android TV / Google TV, которое ограничивает «залипание» ребёнка в YouTube:
каждые **15 минут** просмотра видео ставится на паузу и поверх показывается математический
пример. Без правильного ответа YouTube остаётся заблокированным.

- Установка только вручную через `adb` (sideload), публикации в Google Play нет.
- Полностью локальное: нет разрешения `INTERNET`, нет аккаунтов, аналитики и сетевых запросов.
- Целевое устройство: **Sony KD-65X85L (BRAVIA, прошивка VH22), Android 12 / API 31**.

> Сопроводительная документация проекта. Техническое задание и план работ — в
> [`ROADMAP_tv.md`](ROADMAP_tv.md), фактический прогресс — в
> [`mathgate/docs/PROGRESS.md`](mathgate/docs/PROGRESS.md).

## Как это работает

1. Пока YouTube на переднем плане и экран включён — приложение копит время просмотра.
2. За 60 секунд до лимита показывается тихое предупреждение (Toast).
3. На 15 минутах воспроизведение ставится на паузу, поверх открывается экран с примером.
4. Правильный ответ → блок снимается, видео продолжается, счётчик начинается заново.
5. Неправильный ответ → блок остаётся, показывается новый пример, включается нарастающая пауза
   (защита от перебора).
6. Накопленное время **не сбрасывается** при закрытии/повторном открытии YouTube, выключении
   экрана, перезагрузке и отключении питания (сброс при потере питания — опциональный флаг).

## Статус

| Фаза | Название | Статус |
|---|---|---|
| 0 | Разведка устройства | готово |
| 1 | Каркас проекта | готово |
| 2 | Чистое ядро (автомат, часы, хранилище) | готово |
| 3 | Детект YouTube | готово (проверено на ТВ) |
| 4 | Блокирующий экран и UI с пульта | готово (проверено на ТВ) |
| 5 | Принуждение (enforcement) | готово (проверено на ТВ) |
| 6 | Живучесть и самовосстановление | готово (проверено на ТВ) |
| 7 | Родительский режим | готово (проверено на ТВ) |
| 8 | Закрытие обходов | готово (проверено на ТВ) |
| 9 | Device Owner (опционально) | не начато |
| 10 | Приёмка, сборка и поставка | не начато |

## Требования

- Android TV / Google TV на **Android 12+** (`minSdk = 31`, `compileSdk`/`targetSdk = 34`).
- JDK 17.
- Android SDK (путь к нему — в `mathgate/local.properties`, файл не коммитится).
- `adb` с доступом к телевизору (отладка по сети включена в параметрах разработчика).

## Структура репозитория

```
TV_block/
├── ROADMAP_tv.md                 # ТЗ и план по фазам
├── README.md
└── mathgate/                     # Android-проект (Gradle, модуль :app)
    ├── app/
    │   └── src/main/java/com/mathgate/
    │       ├── core/             # ЧИСТЫЙ Kotlin (без android.*) — автомат, часы, примеры
    │       ├── data/             # DataStore: состояние и настройки
    │       ├── detect/           # детект переднего плана (AccessibilityService + UsageStats)
    │       ├── service/          # службы, координатор, enforcement
    │       └── ui/               # экраны на Compose for TV
    ├── docs/                     # PROGRESS, DEVICE_NOTES, TEST_PLAN, PARENT_GUIDE, IDEAS
    └── scripts/                  # install.sh, check-device.sh
```

## Сборка и тесты

Все команды выполняются из каталога `mathgate/`:

```bash
./gradlew assembleDebug        # debug-APK
./gradlew test                 # unit-тесты (ядро на JVM)
./gradlew jacocoTestReport     # покрытие core/ (DoD: line >= 90%)
```

## Установка на телевизор

```bash
adb connect <IP-телевизора>:5555
mathgate/scripts/install.sh [adb-serial]     # сборка + install + разрешения + мастер настройки
```

Скрипт устанавливает debug-APK, выдаёт `WRITE_SECURE_SETTINGS` (нужно `SelfHealer`, фаза 6) и
включает службу доступности. Проверить состояние устройства:

```bash
mathgate/scripts/check-device.sh [adb-serial]
```

Включить службу доступности вручную:

```bash
adb shell settings put secure enabled_accessibility_services com.mathgate/com.mathgate.service.GuardAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

## Документация

| Документ | Содержание |
|---|---|
| [`ROADMAP_tv.md`](ROADMAP_tv.md) | ТЗ, архитектурные решения (D-01…D-12), фазы, сценарии приёмки |
| [`mathgate/docs/PROGRESS.md`](mathgate/docs/PROGRESS.md) | Отчёт по каждой фазе: сделано / проверено на ТВ / отклонения |
| [`mathgate/docs/DEVICE_NOTES.md`](mathgate/docs/DEVICE_NOTES.md) | Факты о телевизоре и особенности прошивки |
| [`mathgate/docs/TEST_PLAN.md`](mathgate/docs/TEST_PLAN.md) | Сценарии приёмки A1–A16 и матрица обходов |
| [`mathgate/docs/PARENT_GUIDE.md`](mathgate/docs/PARENT_GUIDE.md) | Инструкция для родителя (черновик) |
| [`mathgate/docs/IDEAS.md`](mathgate/docs/IDEAS.md) | Идеи вне текущего скоупа |

## Технологии

- **Kotlin** 2.0.21, **AGP** 8.7.3, **Gradle** 8.11.1.
- **Jetpack Compose for TV** (`androidx.tv:tv-material`) — D-pad-навигация и фокус.
- **Jetpack DataStore** (Preferences) — состояние и настройки.
- **AccessibilityService** (основной детект) + **UsageStatsManager** (резервный).
- Тесты: JUnit 5, Kotest, Turbine; покрытие — JaCoCo.

## Архитектура (кратко)

- `core/` — чистый Kotlin без зависимостей от Android: конечный автомат (`GateEngine`),
  генератор примеров (`ProblemGenerator`), модель настроек, интерфейс `Clock`.
  Время считается по `SystemClock.elapsedRealtime()`, поэтому смена даты/времени не влияет на счётчик.
- `detect/` — единый `StateFlow<Boolean>` «отслеживаемое приложение на переднем плане».
- `service/` — `GateCoordinator` владеет движком в процессе: подписан на детектор и экранные
  события, раз в секунду шлёт `Tick` и исполняет эффекты (`ShowChallenge`, `HideChallenge`,
  `PersistState`, `WarnAboutLimit`) через `ChallengeEnforcer` (запуск `BlockActivity`,
  `AudioManager.dispatchMediaKeyEvent`).
- `ui/` — `BlockActivity` (экран с примером) как тонкое представление над координатором.

Ключевые решения зафиксированы в `ROADMAP_tv.md` (раздел 2). Отдельно отмечено, что оверлей
`TYPE_ACCESSIBILITY_OVERLAY` (D-11) не понадобился: на Android 12 служба доступности может
запустить `BlockActivity` поверх YouTube.

## Приватность и ограничения

- Никаких сетевых запросов: разрешение `INTERNET` намеренно не добавлено.
- Содержимое экрана не читается (`canRetrieveWindowContent=false`), лог только локальный.
- Это **не абсолютная защита**: технически подкованный ребёнок может найти обходные пути.
  Матрица обходов (закрыто / частично / не закрыто) — в
  [`mathgate/docs/TEST_PLAN.md`](mathgate/docs/TEST_PLAN.md), честный список ограничений и
  рекомендации — в [`mathgate/docs/PARENT_GUIDE.md`](mathgate/docs/PARENT_GUIDE.md).
  Не закрываются без прав Device Owner: удаление приложения и «Остановить» в настройках
  (рекомендуется ограниченный/Kids-профиль Google TV).
