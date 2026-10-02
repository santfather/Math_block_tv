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



## Что ещё нужно проверить вручную (не критично для фаз 1–3)

- Поведение при выключении кнопкой пульта: сон или полное выключение; включён ли «быстрый запуск» (влияет на `resetOnPowerLoss`, фаза 6).
- `dumpsys window | grep mCurrentFocus` при **открытом** YouTube — снять в фазе 3 (сейчас ТВ на главном экране Google TV, `mFocusedApp = ...tv.launcherx/.coreservices.bootmode.DispatchActivity`).

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
