# Як зібрати APK

Проєкт не містить `gradle-wrapper.jar` (це бінарний файл), тому wrapper треба
згенерувати один раз — або дати це зробити Android Studio / CI.

---

## Варіант 1. Android Studio (найпростіший)

1. Встановіть Android Studio (Ladybug або новішу).
2. `File → Open` → вкажіть кореневу папку проєкту.
3. Студія сама запропонує завантажити Android SDK 35 і згенерує Gradle wrapper.
4. `Build → Build Bundle(s) / APK(s) → Build APK(s)`.

Готовий файл: `app/build/outputs/apk/debug/app-debug.apk`

---

## Варіант 2. Командний рядок (Linux/macOS, без Android Studio)

Потрібні JDK 17 і command-line tools.

```bash
# 1. JDK 17
sudo apt install openjdk-17-jdk unzip curl   # Debian/Ubuntu

# 2. Android command-line tools
mkdir -p ~/android-sdk/cmdline-tools && cd ~/android-sdk/cmdline-tools
curl -O https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-*.zip && mv cmdline-tools latest

export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin

# 3. Компоненти SDK
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# 4. Gradle (потрібен один раз, щоб створити wrapper)
sudo apt install gradle
cd /шлях/до/проєкту
gradle wrapper --gradle-version 8.9

# 5. Збірка
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug
```

Результат: `app/build/outputs/apk/debug/app-debug.apk`

Скинути на телефон:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Варіант 3. GitHub Actions (нічого не встановлювати локально)

У проєкті вже є `.github/workflows/build-apk.yml`.

1. Створіть репозиторій на GitHub і залийте туди папку проєкту.
2. Вкладка `Actions` → workflow `Build APK` запуститься автоматично при push.
3. Після завершення відкрийте run → розділ `Artifacts` → завантажте
   `NissanLeafDiag-debug-apk`.

Це безкоштовно для публічних репозиторіїв і не потребує SDK на вашому комп'ютері.

---

## Підпис для release-збірки

`assembleDebug` дає APK, підписаний debug-ключем — його достатньо, щоб
встановити на власний телефон (треба увімкнути «Встановлення з невідомих
джерел»). Для `assembleRelease` потрібен власний keystore:

```bash
keytool -genkey -v -keystore leafdiag.jks -keyalg RSA \
        -keysize 2048 -validity 10000 -alias leafdiag
```

і блок `signingConfigs` у `app/build.gradle.kts`.

---

## Що змінилося у версії 0.5.0

- Інтерфейс переписано на Jetpack Compose: одинадцять екранів замість одного
  списку кнопок.
- Стек оновлено під сучасну Android Studio: **AGP 9.4.0, Gradle 9.6.0,
  compileSdk 37, targetSdk 36, Compose BOM 2026.09.00**.
- Плагін `org.jetbrains.kotlin.android` **прибрано**: починаючи з AGP 9 Kotlin
  вбудований у сам плагін Android, і окремий призводить до помилки збірки.
  Через це зник і блок `kotlinOptions` — версію JVM задає `compileOptions`.
- Прибрано залежність від AppCompat: активність тепер `ComponentActivity`,
  тема — `android:Theme.Material.NoActionBar`.
- Обгортка Gradle (`gradlew`, `gradle/wrapper/`) тепер лежить у репозиторії.
  CI більше не генерує її сам: версія обгортки має збігатися з тією, під яку
  зібрано AGP.
- Додано юніт-тести на розбір протоколу (`app/src/test`), 35 штук. CI ганяє їх
  перед складанням APK.

---

## Локальна збірка

```bash
./gradlew testDebugUnitTest assembleDebug
```

Готовий файл: `app/build/outputs/apk/debug/app-debug.apk`

Потрібні Android Studio з SDK Platform 37 і JDK 17 або новіший — підійде той,
що йде в комплекті зі Studio (`Android Studio/jbr`). Якщо `java` у терміналі не
той, задайте його явно:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```

Встановити на телефон:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Часті помилки збірки

| Помилка | Причина |
| --- | --- |
| `Minimum supported Gradle version is 9.6.0` | обгортку відкотили назад; поверніть `gradle-9.6.0-bin.zip` у `gradle/wrapper/gradle-wrapper.properties` |
| `The 'org.jetbrains.kotlin.android' plugin is no longer required since AGP 9.0` | плагін Kotlin повернувся у `build.gradle.kts` — приберіть його |
| `requires libraries and applications to compile against version 37 or later` | `compileSdk` менший за 37 |
| `Failed to find Platform SDK with path: platforms;android-37` | у SDK немає платформи 37: Studio → SDK Manager → Android SDK Platform 37 |
| `Inconsistent JVM-target compatibility` | Gradle запущено іншим JDK; задайте `JAVA_HOME` |

---

## Що було виправлено перед збіркою 0.4

- `MainActivity.identifyEcus()` — рядки логу містили `\\n` замість `\n`,
  через що в журнал друкувалося літеральне `\n` замість переносу рядка.
- `app/build.gradle.kts` — додано явні `compileOptions` / `kotlinOptions`
  (JVM target 17). Без цього AGP 8.7 + Kotlin 2.0 на сучасних JDK часто падає
  з «Inconsistent JVM-target compatibility».
- Додано `buildTypes` з `applicationIdSuffix = ".debug"`, щоб debug-версія
  ставилася поряд із release, а не поверх неї.
- `lint { abortOnError = false }` — щоб попередження lint не блокували APK.
- Додано `.gitignore` і CI-workflow.
