/// Ports MainActivity.kt's `sealed interface AppState` (the Kotlin app has
/// no Jetpack Navigation / nav-graph — one hand-rolled sealed state drives
/// a single `when`/switch). `returnState` mirrors the Kotlin back-stack
/// pattern: back navigation restores the captured state and re-fetches
/// fresh data for it, rather than using a URL-based router. Field types are
/// placeholders (String ids) until the Phase 1 data layer lands real Plex
/// models; screens are wired in Phase 4.
sealed class AppState {
  const AppState();
}

class Checking extends AppState {
  const Checking();
}

class LoggedOut extends AppState {
  const LoggedOut();
}

class ConnectingToServer extends AppState {
  const ConnectingToServer();
}

class AppError extends AppState {
  final String message;
  final AppState retryState;

  const AppError({required this.message, required this.retryState});
}

class RelaySetup extends AppState {
  final AppState returnState;

  const RelaySetup({required this.returnState});
}

class Home extends AppState {
  const Home();
}

class Library extends AppState {
  final String sectionKey;

  const Library({required this.sectionKey});
}

class LoadingSection extends AppState {
  final AppState returnState;

  const LoadingSection({required this.returnState});
}

class Settings extends AppState {
  final AppState returnState;

  const Settings({required this.returnState});
}

class MovieDetail extends AppState {
  final String ratingKey;
  final AppState returnState;

  const MovieDetail({required this.ratingKey, required this.returnState});
}

class PersonFilmography extends AppState {
  final String personKey;
  final AppState returnState;

  const PersonFilmography({required this.personKey, required this.returnState});
}

class CollectionDetail extends AppState {
  final String collectionKey;
  final AppState returnState;

  const CollectionDetail({required this.collectionKey, required this.returnState});
}

class ShowSeasons extends AppState {
  final String showKey;
  final AppState returnState;

  const ShowSeasons({required this.showKey, required this.returnState});
}

class ShowEpisodes extends AppState {
  final String seasonKey;
  final AppState returnState;

  const ShowEpisodes({required this.seasonKey, required this.returnState});
}

class EpisodeDetail extends AppState {
  final String episodeKey;
  final AppState returnState;

  const EpisodeDetail({required this.episodeKey, required this.returnState});
}

class Lobby extends AppState {
  final String roomId;
  final AppState returnState;

  const Lobby({required this.roomId, required this.returnState});
}

class Player extends AppState {
  final String ratingKey;
  final AppState returnState;

  const Player({required this.ratingKey, required this.returnState});
}
