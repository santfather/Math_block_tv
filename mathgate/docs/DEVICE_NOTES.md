# DEVICE_NOTES — результаты фазы 0

Данные собраны с реального ТВ по adb (`192.168.0.59:5555`) + ручные проверки.

## Подтверждённые данные

| Параметр | Значение |
|---|---|
| Телевизор | Sony KD-65X85L, `BRAVIA_4K_VH22` (BRAVIA 4K VH22) |
| Прошивка | `STT2.230505.001.S136` (`Sony/BRAVIA_VH22_M_EU/BRAVIA_VH22:12/...`) |
| ОС | Android 12 (`ro.build.version.release = 12`) |
| **API level** | **31** (`ro.build.version.sdk`) → `minSdk = 31` подтверждён |
| Характеристики | `nosdcard,tv` → устройство TV |
| Платформа UI | **Google TV** (есть `com.google.android.apps.tv.launcherx`), а не классический Android TV |
| Аккаунт Google | **есть** (`santfather25@gmail.com`) → фаза 9 (Device Owner) неприменима без сброса |
| boot_count | 467 (на момент съёмки; меняется при загрузках) |

## Пакеты YouTube (подтверждено)

```
package:com.google.android.youtube.tvkids
package:com.google.android.youtube.tvmusic
package:com.google.android.youtube.tv
```

→ совпадает со списком по умолчанию в `core/Settings.kt`.

## Браузеры на ТВ (для фазы 8)

```
package:com.tvwebbrowser.v22
com.sony.dtv.browser.webappruntime / webappservice / webappplatform / webappruntimeextension
```

→ `com.tvwebbrowser.v22` — сторонний браузер; кандидат в список отслеживаемых (либо принять риск).

## Специальные возможности

- `settings get secure accessibility_enabled` = `0` (до фазы 3)
- `settings get secure enabled_accessibility_services` = `null` (до фазы 3)

→ сторонних служб доступности сейчас нет; на Android 12 «restricted settings» не ожидаются.

## Наблюдения фазы 3 (детект YouTube, проверено на устройстве)

Служба включается без UI из adb:

```bash
adb shell settings put secure enabled_accessibility_services com.mathgate/com.mathgate.service.GuardAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

Подтверждённые переходы (`adb logcat -s MathGate`):

```
foreground -> true  (package=com.google.android.youtube.tv)      # открыт YouTube
foreground -> false (package=com.google.android.apps.tv.launcherx) # нажат HOME
foreground -> true  (package=com.google.android.youtube.tv)      # снова открыт YouTube
screen OFF (interactive=false)                                    # KEYCODE_SLEEP
screen ON  (interactive=true)                                     # KEYCODE_WAKEUP
```

Важные особенности прошивки:

1. **`TYPE_WINDOWS_CHANGED` шумит.** Событие приходит от лаунчера в момент снятия его окна и
   после возврата в уже запущенный YouTube оставляло состояние в `false`. Решение: служба
   реагирует только на `TYPE_WINDOW_STATE_CHANGED`.
2. **Панель громкости** (`KEYCODE_VOLUME_UP/DOWN`) отдельного события `TYPE_WINDOW_STATE_CHANGED`
   не порождает — считать «уходом с YouTube» не нужно. Пакет `com.android.systemui` оставлен в
   whitelist на случай других системных оверлеев.
3. **Возврат в YouTube из лаунчера**: лаунчер на ~2–3 секунды снова становится передним планом
   (это реальный переход, ребёнок действительно на главном экране), затем YouTube снова `true`.
   Конечное состояние корректно.


## Наблюдения фазы 4 (блокирующий экран, проверено на устройстве)

Проверка UI выполнялась через `adb` (скриншоты + `input keyevent`):

```bash
adb shell am start -n com.mathgate/.ui.BlockActivity   # запуск экрана
adb shell input keyevent 23   # DPAD_CENTER (OK)
adb shell input keyevent 4    # KEYCODE_BACK — экран остаётся
adb shell input keyevent 3    # KEYCODE_HOME — уход в лаунчер
```

Особенности:

1. **`am start` и `exported=false`.** В release `BlockActivity` объявлена `android:exported="false"`
   (по требованию безопасности), поэтому `am start` падал с `SecurityException: ... not exported
   from uid 10061`. Решение: debug-only `app/src/debug/AndroidManifest.xml` с
   `tools:replace="android:exported"`, помечающий Activity экспортируемой только в debug-сборке.
2. **Пакет работает в compat-режиме.** В логе при старте:
   `ActivityTaskManager: *********** COMPAT FOR PKG com.mathgate: {320dpi always-compat}` —
   влияет на масштаб Compose-вёрстки; на текущий экран влияния не выявлено.
3. **Фокус пульта.** D-pad стрелки перемещают фокус по кнопкам, OK активирует; цифровые клавиши
   пульта приходят через `onPreviewKeyEvent` и вводят цифры даже при фокусе на кнопке.
4. **Авто-закрытие.** После верного ответа через ~900 мс `mCurrentFocus` возвращается в YouTube
   (или лаунчер, если запуск был из лаунчера).


## Наблюдения фазы 5 (enforcement, проверено на устройстве)

**Спайк: запуск Activity из `AccessibilityService`.** На Android 12 служба доступности может
запустить Activity поверх YouTube — оверлей `TYPE_ACCESSIBILITY_OVERLAY` (D-11) не нужен:

```
adb shell am start ...   # либо startActivity из службы с FLAG_ACTIVITY_NEW_TASK
foreground -> true (package=com.google.android.youtube.tv)
spike startActivity -> true
mCurrentFocus = com.mathgate/.ui.BlockActivity   # блок поверх YouTube
```

**Связка enforcement** (координатор в процессе приложения, `adb logcat -s MathGate`):

```
foreground -> true  (package=com.google.android.youtube.tv)   # YouTube
limit warning shown                                           # Toast за warnBeforeMs до лимита
challenge shown                                               # лимит: BlockActivity + MEDIA_PAUSE
foreground -> false (package=com.mathgate)                    # блок стал передним планом
challenge hidden                                              # верный ответ: MEDIA_PLAY, экран закрыт
foreground -> true  (package=com.google.android.youtube.tv)   # YouTube снова передний план
```

Особенности:

1. **Смена приложения блоком.** Когда `BlockActivity` выходит вперёд, `A11yForegroundDetector`
   получает `package=com.mathgate` → `foreground -> false`. В состоянии `ChallengePending` это
   ожидаемо: движок блок остаётся, а при повторном появлении YouTube снова шлёт `ShowChallenge`.
2. **Проверка сценариев.** Для ускорения `limitMs` временно снижался до 60 с, `warnBeforeMs` — до
   20 с; после проверки значения возвращены (15 мин / 60 с). Сценарии A1–A5 пройдены (A6 — по
   конструкции, время по `elapsedRealtime`).
3. **Медиа-пауза/резюм** отправляются через `AudioManager.dispatchMediaKeyEvent`
   (`KEYCODE_MEDIA_PAUSE`/`PLAY`); факт отправки фиксируется в логе (`challenge shown/hidden`).


## Наблюдения фазы 6 (живучесть, проверено на устройстве)

Состояние читалось из `files/datastore/mathgate.preferences_pb` через
`adb shell run-as com.mathgate cat ...` (файл — protobuf Preferences, ключи `state_type`,
`accumulated_ms`, `boot_count`, `clean_shutdown`).

**Перезагрузка (`adb reboot`).** `BOOT_COMPLETED` приходит с задержкой — сначала его получают
системные приложения Sony, наше приложение примерно через **~1.5 мин** после
`sys.boot_completed = 1`:

```
15:26:24  accessibility connected                      # система сама переподключила службу
15:26:27  coordinator started
15:27:28  boot receiver: guard service started (boot=468)   # наш BootReceiver
15:27:28  guard service started (interactive=true)
15:27:31  watchdog fallback -> false (a11yEnabled=true, stale=true)
```

- `boot_count` 467 → 468; `state_boot_count` = 468; `accumulated_ms` сохранён (A7/A8/A9).
- `LOCKED_BOOT_COMPLETED` на ТВ не приходит (нет экрана блокировки) — фактически работает
  `BOOT_COMPLETED`.

**Самовосстановление accessibility (A11).** Служба возвращается и при удалении из списка, и при
выключении мастер-переключателя:

```
settings delete secure enabled_accessibility_services   -> accessibility service re-added
settings put    secure accessibility_enabled 0          -> accessibility master switch re-enabled
                                                         -> accessibility connected
```

**«Потеря питания» (A10, временно `resetOnPowerLoss=true`).** `am force-stop` перед ребутом
(чтобы не записался `clean_shutdown=true`), затем `adb reboot`:

```
power loss detected: gate state reset     # accumulated_ms = 0
boot receiver: guard service started (boot=470)
```

При `resetOnPowerLoss=false` (по умолчанию) лога нет, `accumulated_ms` сохраняется (A9).

**Гибель процесса (A12).** `am force-stop` во время `Counting` → повторный запуск приложения:
`state_type` `Counting → Idle`, `accumulated_ms` восстановлен, сервис и координатор поднялcя.

**Watchdog (регрессия, исправлено в фазе 6).** Во время стабильного воспроизведения события
accessibility не приходят > 60 с (нет смены окна), heartbeat становится «устаревшим», и watchdog
переходил на резервный канал. Раньше он передавал в координатор `false` даже когда у
`UsageStats` не было показаний (нет прав / нет свежих переходов), из-за чего счётчик ложно
сбрасывался. Теперь резервный канал подаёт сигнал только при реальных показаниях:

```
foreground -> true (package=com.google.android.youtube.tv)   # 80 с без ложного сброса
```



## Наблюдения фазы 7 (родительский режим, проверено на устройстве)

Примерка UI через `adb` (скриншоты + `input keyevent`), после `pm clear com.mathgate` для чистого
мастера.

**Мастер первичной настройки (`SetupActivity`).** Проходит шаги `Welcome → Permissions →
Create PIN → Confirm PIN → Done`; PIN-пад принимает цифры и с пульта, и цифровыми клавишами
(`onPreviewKeyEvent`); после сохранения PIN `hasPin=true`.

**Найденный и исправленный баг фокуса на шаге разрешений.** Кнопки «Открыть» стоят справа и
геометрически не пересекаются по X с нижним рядом «Назад/Далее», поэтому фокус не уходил на
«Далее» (нажатие DOWN с первого «Открыть» прыгало на второй «Открыть»). Лечение — явная связка
`focusProperties { down = nextFocus }` на кнопке «Открыть» + `focusRequester` на «Далее».
После фикса DOWN от «Открыть» попадает на «Далее».

**PIN-гейт (`ParentActivity`).** Экран рисовался так, что кнопка «OK» уходила за нижнюю границу
(экран ТВ — 960×540 dp, `Override size: 1920x1080`, `Physical density: 320`): `uiautomator` её
вообще не видел, фокус до неё не доходил. Лечение — компактнее PIN-пад (`PinButton` 68→56 dp,
внутренний отступ 24→16 dp) и прокручиваемый столбец; теперь заголовок «Введите PIN родителя» и
«OK» видны одновременно.

**Проверенные сценарии фазы 7:**

- неверный PIN → «Неверный PIN», ввод очищается;
- четвёртая ошибка подряд → «Слишком много попыток. Подождите 3 с.» (живой отсчёт паузы 5 с;
  первые три ошибки бесплатны — `PinPolicy.FREE_ATTEMPTS = 3`);
- верный PIN → экран «Родительские настройки»;
- смена «Дневной лимит» 15 → 20 мин применяется без перезапуска, и после `am force-stop` +
  повторного запуска выбор сохраняется (значение в `files/datastore/mathgate.preferences_pb`);
- «Разблокировать сейчас» → `parentOverride()`, тост «Доступ к YouTube открыт», экран закрыт;
- запасной вход: долгое нажатие (≥ 1 с) на заголовок «Реши пример» в `BlockActivity` открывает
  PIN-гейт `ParentActivity`.

## Наблюдения фазы 8 (закрытие обходов, проверено на устройстве)

**Браузер.** `com.tvwebbrowser.v22` включён в дефолтный список отслеживаемых (и в опции настроек).
Открытие браузера распознаётся как «просмотр»:

```
foreground -> true  (package=com.tvwebbrowser.v22)
foreground -> false (package=com.google.android.apps.tv.launcherx)
```

**Голосовой поиск / кнопка пульта.** У `com.google.android.katniss` (Google TV) нет launcher-
активности: голосовой поиск сам YouTube не запускает. `intent VIEW` на ссылку `https://youtu.be/…`
открывает обычный YouTube, который уже отслеживается:

```
foreground -> true (package=com.google.android.youtube.tv)
```

Итог: запуск через голосовой поиск/ассистента приводит к тому же пакету и ловится детектором.

**Самовосстановление accessibility (повторно, фаза 8).** Удаление службы из списка → возврат за
**~80 мс**:

```
settings delete secure enabled_accessibility_services -> accessibility service re-added
```

**Остановка приложения (`am force-stop com.mathgate`) — реальный обход.** После `force-stop`
процесса нет, `enabled_accessibility_services` = `null`; автоматического восстановления нет ни
через 10, ни через 30 с (нет процесса, некому выполнить `SelfHealer`/`BootReceiver`). Сервис
поднимается только при следующем запуске приложения или после перезагрузки ТВ. Так как
`force-stop` требует adb или системного «Остановить», для ребёнка через пульт он недостижим, но
через системные настройки приложений — достижим (см. `PARENT_GUIDE.md`).

**PiP / картинка в картинке.** На прошивке нет фичи `android.software.picture_in_picture`
(`pm list features`), и `com.google.android.youtube.tv` не объявляет `supportsPictureInPicture`
(`dumpsys package`). PiP на этом ТВ недоступен, отдельного обхода не создаёт.

**Пакеты ТВ (фаза 8).** Найденные браузеры и медиа-пакеты: `com.tvwebbrowser.v22` (браузер),
`com.google.android.katniss` (Google TV), `com.google.android.youtube.tv` / `.tvkids` / `.tvmusic`.
Сторонние клиенты YouTube (SmartTube и подобные) на ТВ не установлены.

## Что ещё нужно проверить вручную (не критично для фаз 1–7)

- Поведение при выключении кнопкой пульта: сон или полное выключение; включён ли «быстрый запуск».
  Реагирует на `resetOnPowerLoss` (фаза 6): эвристика проверена симуляцией потери питания
  (`force-stop` + `reboot`), но реальное выключение пультом на ТВ ещё не проверялось — от него
  зависит, записывается ли `ACTION_SHUTDOWN`.

## Вывод команд (raw)

```text
ro.product.model            = BRAVIA 4K VH22
ro.build.version.release    = 12
ro.build.version.sdk        = 31
ro.build.fingerprint        = Sony/BRAVIA_VH22_M_EU/BRAVIA_VH22:12/STT2.230505.001.S136/682241:user/release-keys
ro.build.characteristics    = nosdcard,tv
settings global boot_count  = 467
settings secure accessibility_enabled            = 0
settings secure enabled_accessibility_services   = null
accounts                    = Account {name=santfather25@gmail.com, type=com.google}
```
