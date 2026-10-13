# Echo Loop

A casual puzzle-platformer for **Android** and **iOS**, built with **Kotlin Multiplatform** and
**Compose Multiplatform**. One shared codebase, all art drawn in code, all sounds synthesised in code.
No ads and no in-app purchases.

## How the game works

You guide a little character through side-view rooms to the glowing exit portal. The twist: **you cooperate with
your own past runs.**

### Controls (one-touch, landscape)
| Action | Input |
| --- | --- |
| Run | **Hold** the left half of the screen. The character always runs in the direction it faces. |
| Turn around | Automatic: the character turns when it bumps into a wall (solid tile or closed door). |
| Jump | **Tap** the right half of the screen. |
| End the loop | Tap **END LOOP** (bottom centre). |
| Pause | Pause button (top right, left of the menu). Tap anywhere to resume. |
| Menu | Hamburger button (top right): restart level, skip level, main menu, restart game, sound, high scores, change name. |

### The loop mechanic
- When you tap **END LOOP**, your current run ends and a **ghost** replays that exact run, then stays where it ended
  (even mid-air).
- Ghosts are **one-way platforms** you can jump onto, and they **press plates**.
- Dying (spikes or pits) discards that loop. Running out of time also leaves a ghost.

### Hazards and objects
- **Spikes** and **pits**: touching them kills the run.
- **Doors** (`A`–`E`) are open only while the plate with the same letter (`a`–`e`) is pressed, by you or by a ghost.
- **Exit portal**: reach it to finish the level.

### Levels and scoring
- 100 levels: 8 hand-built tutorials, then 92 generated levels, each proven solvable by a solver.
- Level score = `max(100, 1000 − ticks − 75 × deaths)`.
- The top 10 scores are saved with the player name. Scores are recorded on "Restart game", on finishing all levels,
  or when a new player replaces the current one.

## Project layout

```
app/        Thin Android host (MainActivity calls setGameContent() from shared). Landscape only.
shared/     All game code (Kotlin Multiplatform)
  commonMain/  sim (Level, World, LoopSession), gen (Solver, PuzzleGen), levels, UI screens, Graphics, Sound
  androidMain/ Android storage, sound, back handler
  iosMain/     iOS storage, sound, back handler, MainViewController
  jvmMain/     Level generator entry point (JVM, tests and tooling only)
iosApp/     iOS app. Xcode project is generated from project.yml with XcodeGen.
```

Package / bundle id: `com.conley.game`.

Physics runs on a fixed-point integer simulation at 60 ticks per second, so ghosts replay runs exactly.

## Requirements

Common:
- JDK 17 or newer (Android Studio's bundled JDK works). Gradle will fetch the toolchain it needs.
- Android Studio (recent version with Android Gradle Plugin 9.x support).

For iOS (macOS only):
- Xcode 15 or newer with an iOS 16+ simulator or device
- [XcodeGen](https://github.com/yonaskolb/XcodeGen): `brew install xcodegen`

## Run on Android

**Android Studio**
1. Open this folder in Android Studio and let Gradle sync.
2. Pick the `app` run configuration, choose an emulator or a connected device (USB debugging on), and press **Run**.

**Command line**
```bash
./gradlew :app:installDebug
```
A device or emulator must be connected (`adb devices`). Minimum Android version is 7.0 (API 24).

## Run on iOS

1. Generate the Xcode project (only needed the first time, or after editing `iosApp/project.yml`):
   ```bash
   cd iosApp
   xcodegen generate
   ```
2. Open `iosApp/EchoLoop.xcodeproj` in Xcode.
3. Choose a simulator or device at the top and press **Run**.
   - A build phase runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode`, which builds the Kotlin code into the
     `Shared` framework. The first build is slow.
   - If Xcode cannot find Java, the script falls back to Android Studio's bundled JDK. Otherwise set `JAVA_HOME`.
4. **Real iPhone:** select the `EchoLoop` target → *Signing & Capabilities* → pick your Apple ID team (a free account
   works for personal testing), and enable Developer Mode on the phone.

**Command line (simulator)**
```bash
cd iosApp
xcodebuild -project EchoLoop.xcodeproj -scheme EchoLoop -configuration Release \
  -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 15' build
```
Use the **Release** configuration to judge real performance; Debug is noticeably laggier on iOS.

The app is landscape-only.

## Tests

```bash
./gradlew :shared:jvmTest :shared:iosSimulatorArm64Test
```
`LevelCatalogTest` checks that there are 100 well-formed, unique, solvable levels.

## Regenerating levels

Levels 9–100 live in `shared/src/commonMain/.../levels/GeneratedLevels.kt` (**generated, do not edit by hand**).
```bash
./gradlew :shared:jvmRun --args="8000 92"
```
The arguments are seeds per puzzle family and levels to keep. It also writes `shared/levels.txt`.

Hidden solver hints in level text: `*` means "park a ghost standing here", `~` means "park a ghost hovering here".
They are ignored at runtime.

## Troubleshooting

- **`NoSuchMethodError` at launch:** Compose code must be compiled in the `shared` module, not `app`.
- **Stale paths after moving the folder:** delete `build`, `app/build`, `shared/build`, `.gradle/configuration-cache`
  and `.kotlin`, then rebuild.
- **iOS build cannot find the `Shared` framework:** build once from Xcode so the build phase runs the Gradle task.

## Ideas for later

More obstacle types, a level select screen, analytics, store listings and signing, CI (GitHub Actions on macOS for iOS),
TestFlight / Play internal testing.
