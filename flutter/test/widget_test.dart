import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:reelay/main.dart';

void main() {
  testWidgets('App boots into the Checking placeholder', (WidgetTester tester) async {
    await tester.pumpWidget(const ProviderScope(child: ReelayApp()));

    expect(find.text('Checking'), findsOneWidget);
  });
}
