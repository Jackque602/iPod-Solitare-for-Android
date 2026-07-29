# iPod Solitaire for the Minimal Phone

A complete, fully offline Klondike Solitaire for Android that faithfully
recreates the classic iPod click-wheel Solitaire experience — including its
cumulative money scoring — optimized for the Minimal Phone's monochrome
e-ink display and physical QWERTY keyboard.

All artwork and code are original. Nothing is copied from Apple.

## Features

- **Klondike Solitaire** with Draw 3 (default) and Draw 1 modes and full
  rule validation (alternating colors descending on the tableau,
  ace-to-king single-suit foundations, kings-only on empty columns,
  movable runs).
- **Original iPod money scoring**: every new deal costs $52, every card
  moved to a foundation earns $5, a finished game nets exactly **+$208**.
  The score is cumulative forever, may go negative, and is purely a score —
  no betting, banking, or gambling. See [docs/SCORING.md](docs/SCORING.md).
- **Undo** with exact score restoration, **hints** that cycle through
  productive moves, **restart deal**, **new game**, **deterministic shuffle
  seeds** (share a seed to replay the identical deal), and one-tap
  **auto-complete** once nothing is hidden.
- **Automatic save/restore**: the game persists after every move and
  resumes exactly where it was — without charging the $52 buy-in again.
- **E-Ink mode (default)**: pure black-on-white UI, zero animations, no
  ripples, no gradients or shadows, stable layouts that only redraw changed
  regions, a coarse-by-default timer, and a manual **full-refresh** button
  to clear ghosting.
- **Monochrome card design** with four accessibility options for telling
  the "red" suits apart without color: fully inverted red cards - black
  face, white markings (default, unmistakable on 1-bit e-ink), outlined
  symbols, suit letters, or all-filled.
- **Full keyboard play** on the Minimal Phone's QWERTY with configurable
  bindings for every action. See [docs/CONTROLS.md](docs/CONTROLS.md).
- **Adaptive layout**: the classic portrait board on tall screens, and a
  compact layout with a vertical action rail for near-square or wide
  screens such as the Motorola Razr cover display - with safe-area
  insets keeping the board clear of its camera cutouts.
- **Statistics**: cumulative score with high/low water marks, wins, losses,
  streaks, moves, time, foundation cards, best/worst/average game score,
  per-game history, and JSON export/import.

## Project layout

| Module | Contents |
|---|---|
| `engine/` | Pure Kotlin (zero Android dependencies): rules, dealer with deterministic shuffle, scoring, undo/replay, hints, auto-complete, session bookkeeping, statistics, keyboard/cursor models, JSON codecs. Fully unit tested on the JVM. |
| `app/` | Jetpack Compose UI (MVVM): `GameViewModel` + DataStore persistence + monochrome Compose views, dialogs, and keyboard wiring. JVM unit tests for the ViewModel and instrumented Compose UI tests. |

The split is deliberate: every game rule, score calculation and
persistence format lives in `engine` where it is exhaustively tested
(100+ tests), and the Android layer stays a thin shell.

## Building

See [BUILDING.md](BUILDING.md). Short version: open in Android Studio and
run, or `./gradlew :app:assembleDebug`. CI builds the debug APK and runs
the full test suite on every push (see `.github/workflows/build.yml`);
the APK is downloadable from the workflow's artifacts.

## Documentation

- [BUILDING.md](BUILDING.md) — build, test, and install instructions
- [docs/CONTROLS.md](docs/CONTROLS.md) — touch and keyboard controls, rebinding
- [docs/SCORING.md](docs/SCORING.md) — the iPod money scoring rules in detail
