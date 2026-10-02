# SIGNING — release-подпись и обновление

Документ для того, кто собирает и ставит приложение. Родителю он не нужен — см.
`PARENT_GUIDE.md`.

## Почему нужен свой ключ

Приложение распространяется не через Google Play, а установкой APK по `adb` (sideload).
Android требует, чтобы APK был подписан. Для обновления поверх установленной версии **подпись
должна совпадать** — иначе система откажет в установке (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) и
обновление потребует удаления приложения со сбросом всех настроек и PIN.

## Где что лежит

| Файл | Назначение | В git? |
|---|---|---|
| `mathgate/keystore/mathgate-release.jks` | сам ключ | **нет** (`.gitignore`) |
| `mathgate/keystore.properties` | пароли и путь к ключу | **нет** (`.gitignore`) |
| `docs/SIGNING.md` | этот документ | да |

`keystore.properties` (в каталоге `mathgate/`):

```properties
storeFile=keystore/mathgate-release.jks
storePassword=...
keyAlias=mathgate
keyPassword=...
```

`app/build.gradle.kts` читает этот файл автоматически. Если файла нет, release-сборка просто
остаётся неподписанной (debug-сборка работает всегда).

## Хранение ключа (важно)

- **Сделайте резервную копию** `mathgate-release.jks` и паролей и держите её вне репозитория
  (менеджер паролей, зашифрованный диск). Потеря ключа = невозможность обновлять приложение без
  переустановки.
- Никогда не коммитьте `.jks` и `keystore.properties`. Оба шаблона уже в `.gitignore`.
- Пароль в этом репозитории не хранится; задайте свой при генерации.

## Создать ключ с нуля

```bash
cd mathgate
mkdir -p keystore
keytool -genkeypair \
  -keystore keystore/mathgate-release.jks \
  -alias mathgate -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass '<пароль>' -keypass '<пароль>' \
  -dname "CN=Math Gate, O=Math Gate, C=PL"
# затем создайте keystore.properties по образцу выше
```

## Собрать и проверить подпись

```bash
cd mathgate
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk

# проверка подписи (PATH к build-tools подставьте свой):
$ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

Установка и настройка на ТВ — `scripts/install.sh` (см. `README.md`).

## Обновление без потери состояния

Состояние, настройки и PIN хранятся в Preferences DataStore
(`files/datastore/mathgate.preferences_pb`) и **сохраняются** при установке поверх:

```bash
scripts/install.sh            # gradlew assembleRelease + adb install -r + разрешения
```

Правила, чтобы обновление было безопасным:

1. **Подпись не менять** — обновлять тем же ключом (см. выше).
2. **`versionCode` увеличивать** при каждом обновлении (`app/build.gradle.kts`,
   `defaultConfig.versionCode`).
3. **Ключи DataStore только добавлять.** Preferences-DataStore не использует версии и миграции:
   новые ключи читаются с дефолтом, старые игнорируются. Удаление/переименование ключа без
   явного переноса значения = потеря данных (например, PIN и настройки).
4. **Проверка после обновления:** `scripts/check-device.sh` — версия, разрешения, службы.
5. Если поменяли дефолты в `core/Settings`, уже сохранённые значения у пользователя не изменятся
   (это ожидаемо).

Полный сброс (мастер и PIN заново):

```bash
adb shell pm clear com.mathgate
```
