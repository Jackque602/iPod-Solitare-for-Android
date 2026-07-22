# Scoring

This app reproduces the original iPod click-wheel Solitaire money score:
a single cumulative Vegas-style balance that follows you across games.
It is a score, not currency — there is no betting, banking, gambling, or
anything to buy. It exists to make every deal feel like it counts.

## The rules

| Event | Effect |
|---|---|
| Start any new deal | **-$52** |
| Restart the current deal | **-$52** (a restart is a new deal) |
| Move a card onto a foundation | **+$5** |
| Move a card off a foundation | **-$5** |
| Draw, recycle, tableau moves | $0 |
| Undo | Exactly reverses the move's effect |
| Win a game (all 52 cards up) | Net **+$208** for the deal |

The win arithmetic: 52 cards × $5 − $52 buy-in = **+$208**.

## Properties

- **Cumulative and persistent**: the balance survives app restarts and
  device reboots, forever. It can be (and often is) negative.
- **Restoring is free**: reopening the app to a game in progress does not
  charge another $52 — the buy-in was paid when the deal started. Only
  actually starting a new deal (or restarting the current one) costs $52.
- **Undo is exact**: undoing a foundation move takes back exactly the $5
  it earned; nothing else changes the balance retroactively. The engine's
  test suite verifies that any sequence of moves fully undone returns the
  balance to precisely where it started.
- **Auto-complete pays normally**: each auto-played card earns its $5, so
  a game finished by auto-complete still nets +$208.

## Statistics definitions

- **Cumulative score** — the live balance described above.
- **Highest / lowest score** — high/low water marks the balance has ever
  touched (momentary values count, even if later undone).
- **Game score** — one deal's own result: −$52 + $5 × (cards on
  foundations). A win is +$208; an untouched deal is −$52.
- **Best / worst / average game score** — computed over completed games.
- **Completed games** — a deal ends as a **win** when all 52 cards reach
  the foundations, or as a **loss** when you abandon it (new game or
  restart) after having made at least one move. Abandoning a deal you
  never played is not recorded as a loss — but the $52 is still gone.
- **Foundation cards** — net count of cards ever moved onto foundations
  (undo subtracts).
- **Total time / moves** — accumulated from completed games.

## Verified by tests

The engine test suite pins these rules down, including
`a completed game always nets +208`, exact undo refunds, the
restore-without-recharge guarantee, and the watermark behavior of the
highest/lowest score. See `engine/src/test/`.
