# TV D-pad/Focus Regression Checklist

This documents every D-pad/focus bug class found and fixed the hard way,
on real hardware, in the current Compose implementation. It exists so that
if the UI is ever rebuilt in a different framework (Flutter or otherwise),
the new implementation can be tested against the same real-world failure
modes instead of rediscovering them one at a time again. Each item is a
behavior to verify, described independently of Compose — the "what broke"
notes are context for why the check matters, not something a tester needs
to understand to run the check.

See `ARCHITECTURE.md` §4 ("Focus management on Android TV") and §5 ("Home")
for the full technical writeup of the current implementation, if a specific
fix needs to be ported rather than just re-verified.

## 0. Testing methodology — read this first

**Every check below must be driven by real directional remote input, not a
tap/click simulator.** A tap goes straight to a click handler and never
exercises focus-search or focus-redirection logic at all — a prior
verification pass that used taps reported a broken menu as "working," and
the real bug (item 8 below) shipped to production anyway, only surfacing
when a real remote hit it. On Android this means `adb shell input keyevent
KEYCODE_DPAD_UP/DOWN/LEFT/RIGHT/CENTER`, not `adb shell input tap`; use
whatever the equivalent real-remote-event simulation is on the target
platform. Screenshot (or otherwise capture visible focus state) between each
press and confirm focus is visually on the intended element, not just that
"nothing crashed."

## 1. Closing an overlay must not lose focus to the nav rail

**Scenario:** Any transient overlay (a confirm dialog, a dropdown, a menu)
opens and closes while the element underneath it currently holds D-pad
focus.

**What went wrong before:** Conditionally swapping which composable exists
(`if (open) Overlay else Content`) rather than layering the overlay on top
disposed the currently-focused node for one frame. The platform's own
default "something must have focus" fallback filled that gap with whatever
was nearest — often a persistent always-focusable sidebar/nav rail — before
any app-level focus-restore code got a chance to run. This read to the user
as the nav drawer flickering open on its own, or an unrelated element (e.g.
a live "watch together" card) unexpectedly scrolling into view.

**Verify:** Open and close every dismissible overlay/menu/dialog in the app
via real remote input. Confirm the persistent nav sidebar never
expands/flickers, and no unrelated on-screen element reacts, during either
the open or close transition.

## 2. Removing a focused list item must land focus back in that list

**Scenario:** The user removes an item from a scrollable row (e.g. a
watchlist entry, a "continue watching" card, an actively-hosted session
card) while an action on that specific item is focused.

**What went wrong before:** removal is a real content change, not a toggle —
the item's own composable actually goes away. Without an explicit "reclaim
focus inside this row" step keyed off the row's item count, focus fell
through to wherever the default disposal search sent it (again, often the
nav rail).

**Verify:** Remove an item from every removable row while focused on that
item's own action. If the row still has items left, focus should land on
another item in the *same row*. If that was the last item, focus should
move to the next appropriate section, not the nav rail.

## 3. A long-press-to-confirm gesture's button release must not double-fire

**Scenario:** Long-pressing an item brings up a Remove/Cancel confirmation.
The physical remote button is often still held down at the exact moment the
confirmation UI appears and gains focus.

**What went wrong before:** the eventual release of that same button press
landed on whichever confirm/cancel control the overlay just focused, and
without an explicit guard, that trailing release was interpreted as the
user deliberately clicking it — i.e. the confirmation fired or cancelled
itself the instant it appeared, without ever giving the user a chance to
choose.

**Verify:** Long-press to open a confirm overlay, and confirm nothing
happens automatically on releasing that same button — the user must
press again, on a control they can see is now focused, to actually act.

## 4. Focusing a small element inside a larger visual unit must scroll the whole unit into view

**Scenario:** A card's actionable control (e.g. a "Join"/"Play" button)
sits visually below other content (artwork, title, status line) within the
same card, inside a scrollable list.

**What went wrong before:** the platform's default "bring the focused
element into view" behavior only guarantees that the literal focused
control's own small bounding box is visible — not the card as a whole. A
button near the bottom of a tall card could end up with only a sliver of
its parent card visible above/below it.

**Verify:** Scroll a list until a card with an off-center actionable
element is just below the viewport, then D-pad into it. Confirm the *whole*
card scrolls into view, not just the button.

## 5. A child's own "scroll into view" behavior must not conflict with its ancestor's

**Scenario:** A card that has its own explicit "scroll me into view on
focus" logic sits inside a scrollable list that *also* has default/implicit
bring-into-view behavior for its children.

**What went wrong before:** both fired independently on the same focus
event, double-adjusting the scroll position and producing a visible
stutter/flash (reported as "a glitchy line" flickering at a card edge when
navigating between cards in the row).

**Verify:** D-pad between adjacent cards in every horizontally-scrolling row
that has custom scroll-into-view logic. The scroll should be one smooth
motion per keypress, never a visible double-adjustment or flash.

## 6. A "re-run this correction on every relevant focus move" effect must not use a boolean that only flips once

**Scenario:** An effect needs to re-fire every time focus moves between two
or more sibling controls in the same region (e.g. re-scrolling to keep a
detail header visible no matter which action button is focused).

**What went wrong before:** a boolean tracker ("is focus currently anywhere
in this region") only transitions false→true once per visit. Moving focus
laterally between two buttons that are both already "in the region"
(true→true) never re-triggered anything, so the platform's own competing
default behavior won uncontested on every subsequent move within the
region.

**Verify:** With several sibling focusable controls in the same
focus-dependent-effect region, move focus back and forth between all of
them repeatedly and confirm the intended correction (scroll position, etc.)
re-applies on *every* move, not just the first one into the region.

## 7. A focused text field must let navigation keys "escape" toward a sibling when intended

**Scenario:** A search/text-entry field sits adjacent to another focusable
control, and the design intends a directional key press (while not actively
editing) to move focus to that sibling.

**What went wrong before:** the text field's own internal handling of
horizontal directional input (cursor movement) consumed the key before any
app-level focus-redirection logic ever saw it. A declarative "redirect focus
this direction" rule attached directly to the field compiled fine, looked
identical to working examples on non-text-field controls, and silently
never fired — this is easy to ship believing it works from code review
alone.

**Verify:** Focus a text field without entering edit mode, press the
direction that's supposed to escape to a sibling, and confirm — via an
actual on-screen focus-highlight check, not code inspection — that focus
visibly moved.

## 8. A menu's own internal navigation must not be misread as the user backing out

**Scenario:** An open menu/dropdown supports both (a) moving between its own
rows via Up/Down, and (b) auto-dismissing when focus leaves the menu
entirely.

**What went wrong before:** combining explicit internal-redirect navigation
with focus-loss-triggered auto-dismiss caused a transient one-frame "focus
left" blip — produced by the redirect itself, not a real exit — to be
misread as the user backing out. Pressing Down once inside the menu closed
it immediately.

**Verify:** Open every menu in the app and press Up/Down repeatedly,
including past the first/last row. The menu must never close from internal
navigation alone — only an explicit dismiss action (Back, tapping outside,
selecting an option) should close it. Also confirm pressing past the
top/last row does nothing (no focus escaping the menu, no wraparound)
unless wraparound is explicitly intended.

## 9. A scaled/animated card's internal seams must not show a rendering artifact

**Scenario:** A card whose whole subtree scales up on focus (a common
"focus magnification" treatment) has two visually adjacent regions (e.g. an
image area and an info panel) that meet at a fixed boundary.

**What went wrong before:** at non-1.0 scale factors, the boundary between
the two regions could land on a fractional pixel, producing a faint but
real horizontal seam — brighter or darker than either neighboring region —
confirmed via pixel sampling, not just a color-matching issue (matching the
two regions' colors exactly did not fix it).

**Verify:** Focus every card type that has a focus-scale animation and
inspect the boundary between its internal regions closely (pixel-level, not
just eyeballing at normal viewing distance) at the scaled-up size.

---

## Appendix: empirically-tuned timing values worth reusing as starting points

These required multiple rounds of real-device iteration to feel right —
worth starting from these values rather than re-deriving them from scratch,
even though the "correct" number is inherently framework/platform-specific:

- **~1.2s "quiet" window** after the last D-pad input before an autofocus/
  autoscroll correction is allowed to run, so it never yanks focus out from
  under active navigation (tuned up from an initial 800ms, which still felt
  premature).
- **~1.1s scroll animation duration** for a deliberate, purposeful-feeling
  auto-scroll (vs. an instant jump), using an eased curve rather than linear.
