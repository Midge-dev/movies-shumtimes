import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart' show KeyEventResult, VoidCallback;

/// Detects a D-pad "select" key held long enough to count as a long-press.
/// GestureDetector.onLongPress never fires for this on Android TV — it's
/// pointer/touch-gesture-only, and a held DPAD_CENTER/select key sends one
/// raw key-down/key-up pair with real elapsed time between them, no OS
/// auto-repeat (confirmed via `adb shell input keyevent --longpress
/// KEYCODE_DPAD_CENTER` during the flutter-reelay PoC — see
/// project_flutter_focus_poc.md). A Timer started on key-down and
/// cancelled on key-up before it fires is what actually detects the hold.
/// Ports the real app's RoomCard-style select-button long-press (distinct
/// from DpadLongPress.kt's repeat-count-based arrow-key variant, which
/// isn't needed until a Phase 4 screen with arrow-key-hold behavior is
/// built).
class DpadLongPressDetector {
  DpadLongPressDetector({required this.onLongPress, this.threshold = const Duration(milliseconds: 500)});

  final VoidCallback onLongPress;
  final Duration threshold;

  // final, not const — a const Set literal of LogicalKeyboardKey values
  // doesn't compile (hit during the flutter-reelay PoC).
  static final selectKeys = {
    LogicalKeyboardKey.select,
    LogicalKeyboardKey.enter,
    LogicalKeyboardKey.gameButtonA,
  };

  Timer? _timer;
  bool _fired = false;

  KeyEventResult handle(KeyEvent event) {
    if (!selectKeys.contains(event.logicalKey)) return KeyEventResult.ignored;

    if (event is KeyDownEvent) {
      _timer ??= Timer(threshold, () {
        _fired = true;
        onLongPress();
      });
      return KeyEventResult.handled;
    }
    if (event is KeyUpEvent) {
      _timer?.cancel();
      _timer = null;
      if (_fired) {
        // Swallow the trailing release tied to the gesture that just fired
        // long-press, so it isn't also read as a click.
        _fired = false;
        return KeyEventResult.handled;
      }
      return KeyEventResult.ignored;
    }
    return KeyEventResult.ignored;
  }

  void dispose() => _timer?.cancel();
}
