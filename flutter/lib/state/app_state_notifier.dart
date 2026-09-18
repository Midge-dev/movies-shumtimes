import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app_state.dart';

class AppStateNotifier extends Notifier<AppState> {
  @override
  AppState build() => const Checking();

  void navigateTo(AppState next) {
    state = next;
  }

  /// Ports MainActivity.kt's `returnTo` — restores the captured return
  /// state. Screens that need a fresh re-fetch on return (Home/Library)
  /// should do that themselves when they see their state become current
  /// again, same as the Kotlin `refreshReturnState` split.
  void returnTo(AppState target) {
    state = target;
  }
}

final appStateProvider = NotifierProvider<AppStateNotifier, AppState>(AppStateNotifier.new);
