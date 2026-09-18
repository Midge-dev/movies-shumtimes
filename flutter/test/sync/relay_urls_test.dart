import 'package:flutter_test/flutter_test.dart';
import 'package:reelay/sync/relay_urls.dart';

void main() {
  test('wss:// maps to https:// and strips path/query into base', () {
    final url = relayHttpUrl('wss://relay.example.com:8443/socket?token=abc');
    expect(url, isNotNull);
    expect(url!.base, 'https://relay.example.com:8443');
    expect(url.query, 'token=abc');
  });

  test('ws:// maps to http://', () {
    final url = relayHttpUrl('ws://192.168.1.10:8080');
    expect(url!.base, 'http://192.168.1.10:8080');
    expect(url.query, isNull);
  });

  test('unsupported schemes return null', () {
    expect(relayHttpUrl('https://relay.example.com'), isNull);
    expect(relayHttpUrl('not-a-url'), isNull);
  });

  test('blank host returns null', () {
    expect(relayHttpUrl('wss:///socket'), isNull);
  });
}
