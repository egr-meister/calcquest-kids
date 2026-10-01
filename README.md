# CalcQuest Kids

Offline calculator and arithmetic quest app for children. Native Android, Kotlin + Jetpack Compose.
English UI. No network, no accounts, no ads, no analytics, no runtime permissions.

## Features

- **Quest Map** — vertically scrolling paper-map route with four landmarks: Addition Trail,
  Subtraction Cave, Multiplication Tower, Division Gate. Each shows level number, name, icon,
  status (Available / In progress / Completed / Locked / Turned off by a parent — always with
  text and an icon, never colour alone) and "N of 10 solved". Two-column route on screens
  ≥ 600 dp. A **List** toggle shows an accessible ordered list of the same route.
- **Quests** — 10 persisted questions per attempt, 4 answer choices, retry after a wrong answer
  (wrong choices stay marked ✗ and disabled), expression-specific hints, explanation after the
  correct answer, "Next question" (never auto-advances), saved progress, resume/restart.
- **Completion** — "10 of 10 solved", first-try count, question review, continue to next level,
  brief check-mark animation (off when "Reduce decorative animation" is on).
- **Calculator** — separate screen (bottom bar on the map), BigDecimal, one binary operation at a
  time, history of the latest 50 results with "Use result".
- **Parent settings** — 3-second press-and-hold entry (short tap / screen-reader double tap
  offers a typed-digits check instead; this is an accidental-entry guard, not authentication):
  topics, difficulty, Challenge Timer, sound, reduced animation, resets, privacy page.

## Architecture

Single `:app` module, manual DI (`AppContainer`), MVVM with `StateFlow`.

```
com.calcquest.kids
├── AppContainer, CalcQuestApp, MainActivity
├── data/local          Room entities, DAOs, AppDatabase, Migrations
├── data/repository     QuestRepository, CalculatorRepository, SettingsRepository (DataStore)
├── domain/calculator   CalculatorEngine (BigDecimal), CalculatorReducer (pure state machine)
├── domain/generation   QuestionGenerator, OptionGenerator, HintProvider
├── domain/quests       models, AnswerRules, RouteCalculator (unlocking), RetentionPolicy
├── domain/timer        QuestionTimer, ElapsedClock, WallClock
└── ui/                 map, question, completion, calculator, parent, theme, common
```

All arithmetic, generation, hints, unlock and timer logic is pure Kotlin with no Android
dependencies; random source and clocks are injected. Database work runs through Room's
suspend APIs (off the main thread). Answering, completing and resetting run in transactions.

## Toolchain (pinned)

| Tool | Version |
|---|---|
| JDK | 17 (Temurin in CI; 17+ locally) |
| Gradle (wrapper, committed) | 8.14.3 |
| Android Gradle Plugin | 8.12.0 |
| Kotlin / Compose compiler plugin | 2.2.10 |
| KSP | 2.2.10-2.0.2 |
| Compose BOM | 2025.08.00 |
| Room | 2.7.2 |
| DataStore Preferences | 1.1.7 |
| Navigation Compose | 2.9.0 |
| Lifecycle | 2.9.0 |
| Activity Compose | 1.10.1 |
| Core KTX / SplashScreen | 1.16.0 / 1.0.1 |
| Coroutines | 1.10.2 |
| Build-Tools (CI verification) | 36.0.0 |

**SDK:** `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`.
Versions live in `gradle/libs.versions.toml`. Every directly used library is a direct dependency
(no Material icons library — icons are bundled vector drawables).

### Android 16 (API 36) behaviour handled

- Edge-to-edge is mandatory: `enableEdgeToEdge()` + `WindowInsets.safeDrawing` (system bars and
  display cutouts) on every screen. No immersive mode, screen is never kept awake.
- Predictive Back is on by default: `android:enableOnBackInvokedCallback="true"`, only
  `BackHandler` (OnBackPressedDispatcher) is used, and only when there are unsaved changes.
- Large screens ignore orientation/resizability restrictions: the app declares none and adapts
  (two-column map, landscape calculator, resizable windows).

## Build

```bash
./gradlew testDebugUnitTest          # unit tests
./gradlew lintRelease                # release lint
./gradlew assembleDebug              # debug APK, no credentials needed
./gradlew assembleRelease bundleRelease   # requires release signing (below)
```

Outputs:

- APK: `app/build/outputs/apk/release/app-release.apk` (local install / verification)
- AAB: `app/build/outputs/bundle/release/app-release.aab` (**the only file uploaded to Google Play**)

## Calculator limits and rounding

- Digits 0–9, decimal point, + − × ÷, =, C, ⌫, ± (sign), History. No scientific functions,
  percentages, parentheses or expression evaluation.
- |operand| and |result| ≤ 1,000,000; max 6 fractional digits per operand (extra input is refused
  with a friendly notice).
- Results are rounded to 6 fractional digits with `HALF_UP`; trailing zeros removed; results that
  were rounded are shown with "≈" and stored with `rounded = true`.
- Division by zero and out-of-range results show friendly messages and create no history row.
- One Equals = one history row; repeated Equals does nothing; a digit after a result starts a new
  calculation; an operator after a result continues from it; an operator pressed before the second
  operand replaces the previous operator. Unfinished input is kept as a draft in DataStore.

## Quests

| Difficulty | Add/subtract operands | Factors | Dividend | Divisor |
|---|---|---|---|---|
| Easy (default) | 1–10 | 1–5 | 1–20 | 1–10 |
| Medium | 1–50 | 1–10 | 1–100 | 1–10 |
| Hard | 1–100 | 1–12 | 1–144 | 1–12 |

- **Generation:** each attempt draws 10 expressions from a finite pool of all valid expressions
  (no unbounded retries), spread across the answer range, without duplicates (a + b and b + a,
  a × b and b × a count as the same). Expressions from the previous attempt are avoided when the
  pool allows. Subtraction is non-negative; division is exact with positive quotients and never by
  zero. All 10 questions and their option order are written to Room **before** the first one is
  shown, so they survive rotation, navigation and process death.
- **Choices:** four distinct integers, one correct, shuffled position. Distractors are nearby
  totals, ±1/±2 counting slips, the wrong operation, nearby products/quotients; a bounded
  `answer ± d` fallback guarantees four. Add/subtract choices ≥ 0, multiply/divide ≥ 1.
- **Answering:** a question is solved once (transaction + pure `AnswerRules`); wrong answers are
  never penalised; repeated taps are ignored. First-try accuracy is recorded for the review only.
- **Unlocking:** the first enabled level is open; finishing all 10 questions of an enabled level
  unlocks the next *enabled* level (disabled topics never block). At least one topic stays
  enabled. Completion is stored per topic **and difficulty** (`level_progress`), so Easy progress
  does not open the Hard route. Completed levels stay replayable; replays create a fresh attempt
  and never remove completion. One active attempt per level/difficulty (Resume / Restart with
  confirmation). Enabling an unfinished earlier topic shows an explanation before saving.
- **Retention:** the latest 20 finished attempts per topic/difficulty are kept; completion records
  are a separate table and are never pruned.
- Changing difficulty affects only newly started attempts.

## Challenge Timer

Off by default; 30 / 60 / 120 s per question. The duration is fixed on a question the first time
it is shown, so settings changes apply from the next question. It starts when the question is
visible, pauses when the screen is left, the app goes to the background or the hint is open, and
stops when the question is solved. While running, remaining time is computed from
`SystemClock.elapsedRealtime()`; only a snapshot is persisted at each pause. After process death
or a reboot the timer is restored **paused** with a "Resume timer" button. At zero it shows
"Time's up — keep going at your pace." — answers stay available, nothing is counted as wrong and
progress/unlocking are unaffected. No notifications, alarms, services or sounds are used.

## Offline operation, privacy, storage, backup

- No `INTERNET` / `ACCESS_NETWORK_STATE` (they are also `tools:node="remove"`d in the manifest) and
  no runtime permissions. No networking libraries, WebView or JavaScript.
- Room (`calcquest.db`) stores calculations, attempts, questions and completion records;
  DataStore stores settings and the calculator draft. Both are app-private.
- `android:allowBackup="false"`, `fullBackupContent` (≤ Android 11) and `dataExtractionRules`
  (Android 12+) exclude every domain from cloud backup and device transfer.
- "Clear all local data" (with confirmation) deletes all tables and resets settings to: all topics,
  Easy, timer off, sound off.
- Room schemas are exported to `app/schemas/` (`room.schemaLocation`). `Migrations.ALL` is wired
  into the builder and there is **no** destructive fallback. When bumping the DB version, add a
  `Migration` and commit the new schema JSON.

### Permission verification

`scripts/verify-release.sh` dumps the packaged release manifest with `aapt2 dump permissions` and
fails on anything except androidx.core's app-private
`com.calcquest.kids.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (signature-level, not a runtime
permission). It also checks `allowBackup=false` and the presence of data-extraction rules.

## Release signing (PKCS12)

`app/build.gradle.kts` defines `signingConfigs.release` with `storeType = "PKCS12"` and assigns it
to the release build type. Credentials are read from environment variables
(`ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`)
or from an uncommitted `keystore.properties` in the project root:

```properties
storeFile=/absolute/path/to/calcquest-kids-release.p12
storePassword=...
keyAlias=calcquest-upload
keyPassword=...
```

If they are missing, `packageRelease`, `assembleRelease`, `bundleRelease` and
`signReleaseBundle` fail. There is no fallback to the debug key. Debug builds need no credentials.
`*.p12`, `*.jks`, `keystore.properties` are gitignored. Passwords are never printed.

### GitHub Secrets

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 calcquest-kids-release.p12` |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | `calcquest-upload` |
| `ANDROID_KEY_PASSWORD` | key password (same as the store password for PKCS12) |

### Upload key vs app signing key

With Play App Signing, the keystore here is the **upload key**: it signs the AAB you upload.
Google re-signs the APKs delivered to users with the **app signing key** that Google holds.
Keep the upload keystore and its passwords backed up privately; if it is lost, request an upload
key reset in Play Console. APKs built locally are signed with the upload key, so their certificate
differs from Play-delivered installs (uninstall before switching between them).

## CI (`.github/workflows/android.yml`)

1. JDK 17 + Android SDK Platform 36 + Build-Tools 36.0.0; committed wrapper (validated).
2. `testDebugUnitTest` and `lintRelease`; uploads reports and exported Room schemas.
3. Release job (not on pull requests): decodes the PKCS12 keystore to `$RUNNER_TEMP`, builds
   signed `assembleRelease bundleRelease`.
4. `scripts/verify-release.sh`:
   - `apksigner verify --print-certs` on the APK; fails on failure, on `CN=Android Debug`, or if the
     signer SHA-256 differs from the keystore certificate;
   - `jarsigner -verify` on the AAB plus signer SHA-256 comparison (a self-signed upload
     certificate is accepted);
   - release permissions and backup flags;
   - native-library inventory, ELF `LOAD` alignment (if any `.so`), `zipalign -c -P 16`.
5. Uploads the verified APK, AAB, mapping folder (if R8 is enabled) and the verification report.
6. Always deletes the decoded keystore.

No emulator test runs in CI.

## R8 / resource shrinking

Disabled by default (`calcquest.minify=false` in `gradle.properties`), as required until a signed
non-minified release has been verified on a device. To enable afterwards:
`./gradlew assembleRelease bundleRelease -Pcalcquest.minify=true`, then repeat the calculator,
generation, unlocking, timer, sound and persistence checks and keep
`app/build/outputs/mapping/release/mapping.txt`.

## 16 KB page-size compatibility

The dependency set contains no NDK code of its own (Compose, Room with the framework SQLite
driver, DataStore Preferences, Navigation, Lifecycle, coroutines). The expectation is **no `.so`
files** in the APK/AAB; the CI script inspects both artifacts and records the result in
`build/verification/report.md`. If a future dependency adds native libraries, the same script
checks every ELF `LOAD` segment for ≥ 16 KB alignment and runs `zipalign -c -P 16`; runtime
compatibility must then also be tested on a 16 KB emulator/device before being claimed.
Targeting API 36 alone does not prove 16 KB compatibility.

## Local verification on a device

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat --pid=$(adb shell pidof -s com.calcquest.kids)          # while using the app
adb logcat -b crash -d                                              # crashes
adb shell am kill com.calcquest.kids   # with the app in background: simulates process death
```

See `docs/VERIFICATION.md` for the checklist and the current status of each check.

## Google Play

Upload only `app-release.aab`. Content: Designed for children / families — no ads, no data
collection, no permissions; the Data safety form can declare that no data is collected or shared.
