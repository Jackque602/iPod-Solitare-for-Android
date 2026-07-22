# Controls

The game is fully playable by touch, by keyboard, or any mix of the two.
Touch and keyboard share one interaction model: select a source pile, then
select a destination.

## Touch

- **Tap the stock** to draw (or to turn the waste back over when empty).
- **Tap a card** to select it; **tap a destination** to move it there.
  Tapping the selection again deselects it; tapping another pile instead
  reselects that pile.
- **Tap a buried face-up card** in a column to pick up the whole run from
  that card down.
- **Double-tap** a card (waste or tableau top) to send it straight to a
  foundation when legal.
- **Bottom bar**: Undo · Hint · Draw/Auto · Refresh (e-ink mode).
- **Menu** (top-right): new game, restart deal, seed entry, draw mode,
  statistics, settings, controls, full refresh.

## Keyboard (Minimal Phone QWERTY)

A cursor (thick black border) marks the active pile. The top row is
foundations (the ace shelf) on the left, then the stock and waste on the
right; the bottom row is the seven tableau columns. Vertical movement
keeps your column; on a tableau column, **UP first extends the picked-up
run** one card at a time before leaving the column.

Default bindings (every one can be changed):

| Action | Keys |
|---|---|
| Move left / right | `DPAD ←` `→`, `J` / `L` |
| Move up / extend run | `DPAD ↑`, `I` |
| Move down / shrink run | `DPAD ↓`, `K` |
| Select / drop | `ENTER`, `DPAD CENTER` |
| Cancel selection | `X` |
| Draw from stock | `SPACE`, `D` |
| Send to foundation | `F` |
| Undo | `U`, `Z` |
| Hint | `H` |
| Auto-complete | `A` |
| New game | `N` |
| Restart deal | `R` |
| Menu | `M` |
| Statistics | `S` |
| Full screen refresh | `E` |

Inside dialogs, the arrow keys move focus between buttons, `ENTER`
activates, and `M`/`X` (Menu/Cancel) close the dialog.

## Rebinding keys

*Menu → Settings → Keyboard Bindings* (or *Menu → Controls → Change
Bindings*): choose **Set** next to an action, then press the key you want.
The key is automatically taken from any action that previously used it.
**Clear** unbinds an action, **Restore Defaults** brings back the table
above. Bindings are saved on the device.

## Hints

**Hint** describes the most productive move it can find (foundation moves
first, then moves that reveal hidden cards, then waste plays, then
drawing). Pressing it again cycles through every suggestion for the
current position.

## Auto-complete

Once the stock and waste are empty and no card is face down, **Auto**
finishes the game for you. Every card still earns its normal $5.
