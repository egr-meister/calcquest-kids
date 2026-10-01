# Verification notes

Status legend: **PASSED** (performed, result recorded) · **PENDING** (not performed yet).
Nothing is marked passed unless it was actually run.

## Environment used so far

The project was written in a sandbox without access to Google Maven / Maven Central, so Gradle,
the Android SDK, an emulator and a device were **not** available there.

| Check | Status | Notes |
|---|---|---|
| Domain logic + unit tests compiled and run with standalone `kotlinc` 2.4.20 (JDK 21) and a minimal JUnit-compatible runner | **PASSED** | 39/39 tests: calculator arithmetic/rounding/limits/÷0/input rules/history de-dup, generation validity for all topics × difficulties × 40 seeds, duplicate prevention incl. reversed operands, options, hints (every arithmetic statement checked), retry/no-penalty, solve-once, unlocking with disabled topics, per-difficulty progress, replay, retention, timer pause/expiry/recovery |
| `./gradlew testDebugUnitTest` (real Gradle/JUnit, Kotlin 2.2.10) | PENDING | first CI run |
| `./gradlew lintRelease` | PENDING | first CI run |
| Compose/Room/DataStore code compiles (KSP, Room schema export) | PENDING | first CI run; commit `app/schemas/com.calcquest.kids.data.local.AppDatabase/1.json` from the `room-schemas` artifact |
| Signed release APK/AAB build | PENDING | needs GitHub secrets |
| `apksigner verify --print-certs`, no `CN=Android Debug`, signer = release key | PENDING | CI `verify-release.sh` |
| AAB `jarsigner -verify` + signer = release key | PENDING | CI |
| Release permissions (aapt2 dump) | PENDING | CI |
| Native libraries / 16 KB (ELF + zipalign -P 16) | PENDING | CI report |
| R8 / resource shrinking | NOT ENABLED | enable only after the non-minified release is verified on a device |

## Device checklist (record device/emulator, Android version, artifact)

Device: ____________ Android: ____ Artifact: app-release.apk (SHA-256 ________)

| # | Check | Status |
|---|---|---|
| 1 | Fresh install, first launch in airplane mode, no permission prompts | PENDING |
| 2 | Calculator: + − × ÷, decimals, ±, ÷0 message, 1,000,000 limit, ≈ on 1 ÷ 3 | PENDING |
| 3 | History: entries, Use result, clear with confirmation, max 50 | PENDING |
| 4 | Addition / Subtraction / Multiplication / Division quests, Easy/Medium/Hard | PENDING |
| 5 | Wrong answer → ✗ stays disabled, guidance shown, then correct → explanation, Next | PENDING |
| 6 | Hint dialog text matches the expression; dot picture for small products | PENDING |
| 7 | Level completion → next level unlocks; disabled topic skipped; warning on enabling an earlier unfinished topic | PENDING |
| 8 | Resume after `adb shell am kill` (process death): same questions, same option order | PENDING |
| 9 | Difficulty change: new attempts only; separate route per difficulty | PENDING |
| 10 | Challenge Timer 30/60/120: pauses on hint/background/leave; restored paused after kill; time's up keeps answers available | PENDING |
| 11 | Sound on/off; device volume respected | PENDING |
| 12 | Rotation, tablet/resizable window, font size 200 %, TalkBack order and labels, Back behaviour (question → map, history → calculator, parent unsaved → discard dialog, map → exit) | PENDING |
| 13 | Resets: selected difficulty, all quests, history, all data (defaults restored) | PENDING |
| 14 | `adb logcat`: no crashes, no StrictMode/network errors | PENDING |
