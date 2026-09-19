import 'package:flutter/widgets.dart';

import '../../kit/text.dart';
import '../../theme/tokens.dart';
import 'app_loading_indicator.dart';

/// Ports ui/common/LoadingScreen.kt.
class LoadingScreen extends StatelessWidget {
  final String message;

  const LoadingScreen(this.message, {super.key});

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: AppColors.background,
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const AppLoadingIndicator(),
            const SizedBox(height: 16),
            AppText(message),
          ],
        ),
      ),
    );
  }
}
