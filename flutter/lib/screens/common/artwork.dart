import 'package:flutter/widgets.dart';

import '../../theme/tokens.dart';

/// Simplified port of ui/common/Artwork.kt — that file's real behavior is
/// an elaborate "lost signal" placeholder (cycling static-noise frames,
/// scanlines, a diagonal sweep, per-card stagger) while loading, which the
/// conversion plan explicitly defers as a polish pass: ship a plain
/// placeholder now, add the noise effect once screens are functionally
/// complete. `staggerDelayMs` is accepted but unused for the same reason
/// — kept in the API so call sites don't need to change again once the
/// real effect lands.
class Artwork extends StatelessWidget {
  final String? imageUrl;
  final int staggerDelayMs;
  final double noiseOpacity;

  const Artwork({super.key, this.imageUrl, this.staggerDelayMs = 0, this.noiseOpacity = 0.4});

  @override
  Widget build(BuildContext context) {
    final url = imageUrl;
    if (url == null) return const _Placeholder();

    return Image.network(
      url,
      fit: BoxFit.cover,
      loadingBuilder: (context, child, progress) => progress == null ? child : const _Placeholder(),
      errorBuilder: (context, error, stackTrace) => const _Placeholder(),
    );
  }
}

class _Placeholder extends StatelessWidget {
  const _Placeholder();

  @override
  Widget build(BuildContext context) => const ColoredBox(color: AppColors.surfaceVariant);
}
