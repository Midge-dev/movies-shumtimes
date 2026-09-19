import 'package:flutter_secure_storage/flutter_secure_storage.dart';

abstract class SecureTokenStore {
  Future<void> saveToken(String token);
  Future<String?> loadToken();
  Future<void> clearToken();
}

const _tokenKey = 'plex_token';

/// Ports AndroidTokenStore.kt's role, but not its implementation — that
/// file hand-rolls Android Keystore AES-GCM encryption plus manual IV
/// handling; flutter_secure_storage already does equivalent per-platform
/// key management (Keystore on Android, Keychain on iOS/macOS) internally,
/// so none of that crypto needs porting.
class FlutterSecureTokenStore implements SecureTokenStore {
  final FlutterSecureStorage _storage;

  FlutterSecureTokenStore({FlutterSecureStorage? storage}) : _storage = storage ?? const FlutterSecureStorage();

  @override
  Future<void> saveToken(String token) => _storage.write(key: _tokenKey, value: token);

  @override
  Future<String?> loadToken() => _storage.read(key: _tokenKey);

  @override
  Future<void> clearToken() => _storage.delete(key: _tokenKey);
}
