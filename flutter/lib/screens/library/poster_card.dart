import 'package:flutter/widgets.dart';

import '../../kit/card.dart';
import '../../kit/text.dart';
import '../../theme/tokens.dart';
import '../common/artwork.dart';

const _posterWidth = 160.0;
const _posterAspectRatio = 2 / 3;

/// Shared by CollectionDetailScreen/PersonFilmographyScreen/ShowSeasonsScreen
/// — Kotlin doesn't share a helper between these either (each screen
/// declares its own near-identical `*Poster` composable), but since this
/// is a literal copy-paste in the source, porting it once here avoids
/// tripling the duplication for no reason.
class PosterCard extends StatelessWidget {
  final String? imageUrl;
  final String title;
  final String? subtitle;
  final VoidCallback onClick;
  final FocusNode? focusNode;
  final bool autofocus;
  final int staggerDelayMs;

  const PosterCard({
    super.key,
    this.imageUrl,
    required this.title,
    this.subtitle,
    required this.onClick,
    this.focusNode,
    this.autofocus = false,
    this.staggerDelayMs = 0,
  });

  @override
  Widget build(BuildContext context) {
    // Align loosens the grid cell's tight incoming constraint — a SizedBox
    // can't override an already-tight constraint from its parent (the same
    // gotcha documented in project_flutter_focus_poc.md), and
    // SliverGridDelegateWithFixedCrossAxisCount always hands each item a
    // tight constraint matching the computed cell size.
    return Align(
      alignment: Alignment.topCenter,
      child: SizedBox(
        width: _posterWidth,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            AspectRatio(
              aspectRatio: _posterAspectRatio,
              child: AppCard(
                onClick: onClick,
                focusNode: focusNode,
                autofocus: autofocus,
                child: SizedBox.expand(
                  child: Artwork(imageUrl: imageUrl, staggerDelayMs: staggerDelayMs),
                ),
              ),
            ),
            Padding(
              padding: const EdgeInsets.only(top: 16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  AppText(title, maxLines: 1, overflow: TextOverflow.ellipsis),
                  if (subtitle != null) AppText(subtitle!, color: AppColors.onSurfaceVariant),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
