You are an expert Android test engineer using Kotlin, JUnit4, MockK, Coroutines Test, Room Testing, and Robolectric.

Your task is to write the complete unit test suites for FlowKeyboard in `/root/flow-keyboard`.
The domain models and engine classes already exist in:
- `com.flowkeyboard.android.model.*` (`KeyItem`, `KeyType`, `DictionaryItem`, `KeyboardSettings`, etc.)
- `com.flowkeyboard.android.engine.PhysicsCalculator`
- `com.flowkeyboard.android.engine.PinyinEngine`
- `com.flowkeyboard.android.engine.KeyboardActionHandler`
- `com.flowkeyboard.android.data.local.database.*` (`AppDatabase`, `DictionaryEntity`, `DictionaryDao`, etc.)
- `com.flowkeyboard.android.data.repository.*` (`DictionaryRepository`, `SettingsRepository`)

Please create the following test files in `/root/flow-keyboard/app/src/test/java/com/flowkeyboard/android/`:

1. `engine/PhysicsCalculatorTest.kt`:
   - Test `PhysicsCalculator.wrap`: positive numbers, negative coordinates (e.g., negative fling wrap-around), exact multiples of period, period edge cases.
   - Test `PhysicsCalculator.dampVelocity`: friction decay over time, extreme initial velocity clamped to `MAX_FLING_VELOCITY`, negative velocities, cutoff threshold to 0.
   - Test `PhysicsCalculator.advance`: offset advance with delta, period wrapping.
   - Test `PhysicsCalculator.cyclicDistance`: distance across cyclic loop boundary.
   - Test `PhysicsCalculator.crossesTarget`: target hit detection.
   - Test `PhysicsCalculator.keyAt`: mapping coordinate to key index under wrap-around.

2. `engine/PinyinEngineTest.kt`:
   - Test appending lowercase letters, uppercase conversion, rejecting non-letter characters.
   - Test apostrophe handling (allowed only between syllables, not at start, no double apostrophes).
   - Test backspace step by step, and backspacing on empty buffer (returns false, does not throw).
   - Test `matches` prefix lookup against `DictionaryItem`.
   - Test `syllables` splitting for valid strings ("nihao", "xian") and graceful handling/null for invalid sequences.
   - Test `clear`.

3. `engine/KeyboardActionHandlerTest.kt`:
   - Using MockK (`io.mockk.*`):
     - Mock `InputConnection` and `EditorInfo`.
     - Test `commit(text)` calling `inputConnection.commitText(text, 1)`.
     - Test `compose(text)` calling `inputConnection.setComposingText(text, 1)`.
     - Test `finishComposition()` and `cancelComposition()`.
     - Test `backspace()` when selection exists vs no selection.
     - Test `backspace()` with surrogate pairs (e.g. Emoji 4-byte characters) correctly deleting 2 chars.
     - Test `enter()` performing `performEditorAction` when action is valid, or committing "\n" fallback.
     - Test `handle(KeyItem)` for `CHARACTER`, `SPACE`, `BACKSPACE`, `ENTER`.

4. `data/RoomDictionaryRepositoryTest.kt`:
   - Testing `DictionaryDao` and `DictionaryRepository` using Robolectric (`@RunWith(RobolectricTestRunner::class)`) or in-memory Room SQLite database (`Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()`).
   - Test inserting entries, querying by prefix matching, frequency ranking.
   - Test self-learning: call `recordUsage` on a 2nd rank word multiple times, verify its frequency increases and it becomes the top suggestion.

5. `data/SettingsRepositoryTest.kt`:
   - Test updating and reading keyboard settings flow (speed, orientation, sound, haptic, crosshair).

Ensure all tests compile cleanly with standard imports and pass with `./gradlew testDebugUnitTest`.
