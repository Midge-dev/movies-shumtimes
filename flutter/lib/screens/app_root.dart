import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/plex/plex_models.dart';
import '../state/app_state.dart';
import '../state/app_state_notifier.dart';
import '../state/data_providers.dart';
import '../theme/tokens.dart';
import 'auth/auth_screen.dart';
import 'common/loading_screen.dart';
import 'common/placeholder_screen.dart';
import 'navigation/app_navigation_drawer.dart';
import 'splash/splash_screen.dart';

// TEMPORARY: mock sections so AppNavigationDrawer is visible in the fast
// loop for visual verification. Remove once the real Home screen supplies
// sections from PlexServerApi.fetchSections().
const _mockSections = [
  PlexSection(key: 's1', title: 'Movies', type: 'movie'),
  PlexSection(key: 's2', title: 'Shows', type: 'show'),
];

const _splashMinHoldMs = 1400;
const _splashCrossfadeDuration = Duration(milliseconds: 200);

/// Renders whichever AppState is current, plus the splash-hold shell logic
/// ported from MainActivity.kt's AppRoot: splash stays up at minimum
/// SPLASH_MIN_HOLD_MS regardless of how fast the initial auth check
/// resolves, decoupled from it via a separate timer, then crossfades out.
class AppRoot extends ConsumerStatefulWidget {
  const AppRoot({super.key});

  @override
  ConsumerState<AppRoot> createState() => _AppRootState();
}

class _AppRootState extends ConsumerState<AppRoot> {
  bool _showSplash = true;
  final int _splashStartMs = DateTime.now().millisecondsSinceEpoch;
  bool _initialCheckStarted = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _checkStoredToken());
  }

  Future<void> _checkStoredToken() async {
    if (_initialCheckStarted) return;
    _initialCheckStarted = true;

    final token = await ref.read(secureTokenStoreProvider).loadToken();
    if (!mounted) return;

    final notifier = ref.read(appStateProvider.notifier);
    if (token == null) {
      notifier.navigateTo(const LoggedOut());
    } else {
      // TODO(Phase 4 / Home): resolve the Plex account + a reachable
      // server here and transition to RelaySetup/Home/AppError. Stubbed
      // at ConnectingToServer until those screens exist.
      notifier.navigateTo(const ConnectingToServer());
    }
  }

  void _maybeHideSplash(AppState state) {
    if (!_showSplash) return;
    if (state is Checking || state is ConnectingToServer) return;

    final elapsed = DateTime.now().millisecondsSinceEpoch - _splashStartMs;
    final remaining = _splashMinHoldMs - elapsed;
    if (remaining <= 0) {
      setState(() => _showSplash = false);
    } else {
      Future.delayed(Duration(milliseconds: remaining), () {
        if (mounted) setState(() => _showSplash = false);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(appStateProvider);
    ref.listen<AppState>(appStateProvider, (previous, next) => _maybeHideSplash(next));

    return ColoredBox(
      color: AppColors.background,
      child: AnimatedSwitcher(
        duration: _splashCrossfadeDuration,
        child: _showSplash ? const SplashScreen(key: ValueKey('splash')) : KeyedSubtree(key: const ValueKey('content'), child: _content(state)),
      ),
    );
  }

  Widget _content(AppState state) {
    return switch (state) {
      Checking() => const LoadingScreen('Loading…'),
      ConnectingToServer(:final username) =>
        LoadingScreen(username != null ? 'Logged in as $username — connecting to library…' : 'Connecting to library…'),
      LoggedOut() => AuthScreen(
          onLoggedIn: (token) {
            // TODO(Phase 4 / Home): kick off the same connect() pipeline
            // _checkStoredToken stubs above once RelaySetup/Home exist.
            ref.read(appStateProvider.notifier).navigateTo(const ConnectingToServer());
          },
        ),
      AppError(:final message) => PlaceholderScreen(label: 'Error: $message'),
      RelaySetup() => const PlaceholderScreen(label: 'RelaySetup'),
      // TEMPORARY: AppNavigationDrawer visual check — swap for the real
      // Home screen (with real sections/account) when it lands.
      Home() => AppNavigationDrawer(
          sections: _mockSections,
          isHomeSelected: true,
          isSettingsSelected: false,
          onSelectSection: (_) {},
          onOpenSettings: () {},
          onOpenHome: () {},
          versionName: '0.3.0',
          child: const PlaceholderScreen(label: 'Home content'),
        ),
      Library(:final sectionKey) => PlaceholderScreen(label: 'Library: $sectionKey'),
      LoadingSection() => const PlaceholderScreen(label: 'LoadingSection'),
      Settings() => const PlaceholderScreen(label: 'Settings'),
      MovieDetail(:final ratingKey) => PlaceholderScreen(label: 'MovieDetail: $ratingKey'),
      PersonFilmography(:final personKey) => PlaceholderScreen(label: 'PersonFilmography: $personKey'),
      CollectionDetail(:final collectionKey) => PlaceholderScreen(label: 'CollectionDetail: $collectionKey'),
      ShowSeasons(:final showKey) => PlaceholderScreen(label: 'ShowSeasons: $showKey'),
      ShowEpisodes(:final seasonKey) => PlaceholderScreen(label: 'ShowEpisodes: $seasonKey'),
      EpisodeDetail(:final episodeKey) => PlaceholderScreen(label: 'EpisodeDetail: $episodeKey'),
      Lobby(:final roomId) => PlaceholderScreen(label: 'Lobby: $roomId'),
      Player(:final ratingKey) => PlaceholderScreen(label: 'Player: $ratingKey'),
    };
  }
}
