You are an expert Android developer specializing in modern Android development (Kotlin, Jetpack Compose, Room, DataStore, Koin, NowInAndroid architecture, SDK 36, and R8 full mode).

Your task is to implement the complete "FlowKeyboard" (传送带旋转输入法) Android app in this repository (`/root/flow-keyboard`) end-to-end, including all source code, resources, build configuration, and full unit test suites.

## Context & Requirements
This application is inspired by the Google Japan Gboard Conveyor Belt Version ("Gboard くるくるバージョン").
Keys flow continuously on conveyor belts across the screen. The user can rest one finger on a fixed target crosshair line and tap to input characters as they pass by, or tap keys anywhere, or drag/fling the belts.

Key requirements confirmed with the user:
1. **Core Interaction**:
   - Auto-flowing cruise mode + fixed target crosshair line for one-finger typing.
   - Also supports tapping keys directly anywhere on the belt, and manual drag/fling inertial scrolling.
2. **Layout Orientation**:
   - Horizontal 4-lane conveyor (Track 0: Numbers & symbols, Track 1: Letters Q-P, Track 2: Letters A-M, Track 3: Functions Space/Backspace/Enter/Shift/Mode).
   - Vertical waterfall column mode (matching the Google prototype video) togglable via UI control or settings.
   - Keys arranged in QWERTY order by default, with option for A-Z alphabetical.
3. **Bilingual & Pinyin Learning**:
   - Direct English input.
   - Pinyin mode: typing letters enters Composing mode; candidate words from Room DB appear in a top horizontal scrolling CandidateBar.
   - Space commits top candidate. Selected words automatically increment frequency/weight in Room DB.
   - Typing WPM and key stroke statistics recorded in Room DB.
   - Pre-populated with ~1000 common Chinese words/characters (seeded on first DB creation).
4. **Haptics & Audio**:
   - Subtle conveyor belt turning click and key press mechanical switch sound (SoundPool) + haptic pulses (Vibrator). Togglable in settings.
5. **Theme**:
   - Material 3 Material You Dynamic Color (`dynamicLightColorScheme` / `dynamicDarkColorScheme`).
6. **Tech Stack & Exact Versions**:
   - `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`
   - Android Gradle Plugin: `9.0.1` (works with local Gradle 9.2.1 and `/opt/aapt2`)
   - Kotlin: `2.2.10`
   - Jetpack Compose BOM: `2026.09.00` (Material 3, UI, Graphics, Tooling)
   - Compose Compiler: `org.jetbrains.kotlin.plugin.compose` version `2.2.10`
   - KSP: `2.2.10-2.0.2`
   - Room: `2.8.5` (`androidx.room:room-runtime`, `room-ktx`, `room-compiler`)
   - DataStore: `1.2.1` (`androidx.datastore:datastore-preferences`)
   - Koin: `4.2.2` (`io.insert-koin:koin-android`, `koin-androidx-compose`)
   - Lifecycle / ViewModel: `2.10.0`
   - Activity Compose: `1.13.0`
   - Coroutines: `1.10.1`
   - JUnit: `4.13.2`, MockK: `1.13.13`, Coroutines Test: `1.10.1`
7. **R8 Full Mode**:
   - `android.enableR8.fullMode=true` in `gradle.properties`
   - Complete `proguard-rules.pro` keeping Room entities/DAOs, Koin modules, Compose runtimes, and `FlowKeyboardService`.
8. **Gradle Environment Settings**:
   - `gradle.properties` must have:
     ```properties
     org.gradle.jvmargs=-Xmx3072M -Dfile.encoding=UTF-8
     android.useAndroidX=true
     android.nonTransitiveRClass=true
     android.aapt2FromMavenOverride=/opt/aapt2
     android.enableR8.fullMode=true
     ```
   - `local.properties`: `sdk.dir=/opt/android-sdk` (already present)
   - Gradle wrapper: `/root/flow-keyboard/gradlew` (Gradle 9.2.1, already present)
9. **Full Unit Tests**:
   - `PhysicsCalculatorTest.kt`: Cyclic coordinate wrap-around, negative fling wrap-around, extreme velocity damping, crosshair hit detection, zero-speed test.
   - `PinyinEngineTest.kt`: Composing buffer, multi-letter matching, backspace handling, invalid long pinyin graceful degradation, empty buffer backspace safety.
   - `RoomDictionaryRepositoryTest.kt`: In-memory SQLite Room DB, prefix search, user selection self-learning weight inversion (frequently picked 2nd candidate jumps to 1st), WPM stat calculation.
   - `SettingsRepositoryTest.kt`: DataStore preferences read/write flow.
   - `KeyboardActionHandlerTest.kt`: MockK InputConnection, character commit, deleteSurroundingText, enter/space handling.

## Files to Create / Implement:
1. `settings.gradle.kts`
2. `gradle.properties`
3. `gradle/libs.versions.toml`
4. `build.gradle.kts` (root)
5. `app/build.gradle.kts`
6. `app/proguard-rules.pro`
7. `app/src/main/AndroidManifest.xml`
8. `app/src/main/res/xml/method.xml`
9. `app/src/main/res/values/strings.xml`, `colors.xml`, `themes.xml`
10. `app/src/main/java/com/flowkeyboard/android/`:
    - `FlowKeyboardApp.kt`: Application class initializing Koin.
    - `di/AppModule.kt`: Koin definitions for DB, DataStore, Repositories, Engines, ViewModels.
    - `model/`: `KeyItem.kt`, `KeyTrack.kt`, `DictionaryItem.kt`, `KeyboardSettings.kt`, `TypingStat.kt`.
    - `data/local/database/`:
      - `AppDatabase.kt`: Room DB with pre-population of ~1000 Chinese words/chars.
      - `DictionaryEntity.kt`, `DictionaryDao.kt`
      - `TypingStatEntity.kt`, `StatDao.kt`
    - `data/local/datastore/`: `FlowPreferencesDataSource.kt`
    - `data/repository/`:
      - `DictionaryRepository.kt` (interface & impl)
      - `SettingsRepository.kt` (interface & impl)
    - `engine/`:
      - `PhysicsCalculator.kt`: Pure mathematical functions for cyclic loop positioning, velocity damping, crosshair detection.
      - `PinyinEngine.kt`: Chinese Pinyin buffer state machine, syllable matching.
      - `FeedbackEngine.kt`: Audio and haptic feedback.
      - `KeyboardActionHandler.kt`: Dispatches actions to `InputConnection`.
    - `ui/theme/`: `Color.kt`, `Theme.kt`, `Type.kt` (Material You dynamic theming).
    - `ui/keyboard/`:
      - `FlowKeyboardService.kt`: `InputMethodService` implementing `LifecycleOwner`, `ViewModelStoreOwner`, `SavedStateRegistryOwner` with `ComposeView`.
      - `FlowKeyboardUiState.kt` & `FlowKeyboardAction.kt` (UDF).
      - `FlowKeyboardViewModel.kt`.
      - `FlowKeyboardContent.kt`: Keyboard root Composable.
      - `ConveyorBeltCanvas.kt`: Smooth 60fps conveyor belt canvas rendering keys, 3D depth, moving tracks, crosshair line, touch & fling gesture.
      - `CandidateBar.kt`: Horizontal candidate word bar.
      - `FlowControlBar.kt`: Speed slider, direction toggle, vertical/horizontal mode, pinyin/en toggle.
    - `ui/main/`:
      - `MainActivity.kt`: Setup wizard (Enable IME button, Select IME button), Interactive Conveyor Belt Sandbox with live EditText and settings controls.
      - `MainViewModel.kt` & `MainUiState.kt`.
11. `app/src/test/java/com/flowkeyboard/android/`:
    - `engine/PhysicsCalculatorTest.kt`
    - `engine/PinyinEngineTest.kt`
    - `engine/KeyboardActionHandlerTest.kt`
    - `data/RoomDictionaryRepositoryTest.kt`
    - `data/SettingsRepositoryTest.kt`

## Verification Instructions
After creating all files:
- Run `./gradlew testDebugUnitTest --info` to ensure all unit tests pass.
- Run `./gradlew assembleDebug` to ensure compilation and R8 Full Mode succeed.
- Fix any build or test issues that arise.

Please execute step by step and make sure all code is clean, robust, and completely functional!
