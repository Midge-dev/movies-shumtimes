import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:reelay/main.dart';

void main() {
  testWidgets('App boots without crashing', (WidgetTester tester) async {
    await tester.pumpWidget(const ProviderScope(child: ReelayApp()));

    // Checking currently renders the temporary Phase 3 kit showcase
    // (see app_root.dart) rather than a real splash/auth screen — just
    // confirm the app tree builds without throwing.
    expect(tester.takeException(), isNull);
  });
}
