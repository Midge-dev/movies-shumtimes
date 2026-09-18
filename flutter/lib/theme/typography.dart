import 'package:flutter/widgets.dart';

import 'tokens.dart';

/// Type roles per docs/design-tokens.md §Typography — role hierarchy is the
/// shared contract, not pixel values. Sizes below are a first TV-legible
/// pass for Flutter/Android; revisit once real screens are on the Shield.
class AppTypography {
  AppTypography._();

  static const _base = TextStyle(
    color: AppColors.onBackground,
    fontWeight: FontWeight.normal,
  );

  static final displaySmall = _base.copyWith(fontSize: 40, fontWeight: FontWeight.bold);
  static final displayMedium = _base.copyWith(fontSize: 32, fontWeight: FontWeight.bold);
  static final headlineMedium = _base.copyWith(fontSize: 24, fontWeight: FontWeight.w600);
  static final headlineSmall = _base.copyWith(fontSize: 20, fontWeight: FontWeight.w600);
  static final titleLarge = _base.copyWith(fontSize: 18, fontWeight: FontWeight.w600);
  static final titleMedium = _base.copyWith(fontSize: 16, fontWeight: FontWeight.w500);
  static final bodyLarge = _base.copyWith(fontSize: 16);
  static final bodyMedium = _base.copyWith(fontSize: 14);
}
