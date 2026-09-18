import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../state/app_state.dart';
import '../state/app_state_notifier.dart';
import '../theme/tokens.dart';
import 'common/placeholder_screen.dart';

/// Renders whichever AppState is current. Real screens replace the
/// PlaceholderScreen cases as Phase 4 lands them, in the order set out in
/// the conversion plan (splash/auth -> shell -> settings -> library ->
/// detail screens -> home -> lobby -> player).
class AppRoot extends ConsumerWidget {
  const AppRoot({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(appStateProvider);

    return ColoredBox(
      color: AppColors.background,
      child: switch (state) {
        Checking() => const PlaceholderScreen(label: 'Checking'),
        LoggedOut() => const PlaceholderScreen(label: 'LoggedOut (Auth)'),
        ConnectingToServer() => const PlaceholderScreen(label: 'ConnectingToServer'),
        AppError(:final message) => PlaceholderScreen(label: 'Error: $message'),
        RelaySetup() => const PlaceholderScreen(label: 'RelaySetup'),
        Home() => const PlaceholderScreen(label: 'Home'),
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
      },
    );
  }
}
