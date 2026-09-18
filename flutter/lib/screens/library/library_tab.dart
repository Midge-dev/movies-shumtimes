import 'package:flutter/widgets.dart';

import '../../kit/focusable_surface.dart';
import '../../kit/surface_style.dart';
import '../../kit/text.dart';
import '../../theme/tokens.dart';
import '../../theme/typography.dart';

const _tabShape = RoundedRectangleBorder(borderRadius: BorderRadius.only(topLeft: Radius.circular(10), topRight: Radius.circular(10)));
final _tabColors = SurfaceColors(
  container: AppColors.surface,
  content: AppColors.onSurfaceVariant,
  focusedContainer: AppColors.accent,
  focusedContent: AppColors.white,
  selectedContainer: AppColors.background,
  selectedContent: AppColors.onSurface,
);
const _tabBorder = SurfaceBorder(focused: SurfaceBorderSide.gradient(AppFocusTreatment.focusedGradient));
const _tabGlow = SurfaceGlow(focusedColor: AppColors.accentGlow);

/// Ports ui/library/LibraryScreen.kt's private `LibraryTab`.
class LibraryTab extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onClick;
  final FocusNode? focusNode;

  const LibraryTab({super.key, required this.label, required this.selected, required this.onClick, this.focusNode});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: selected ? 60 : 48,
      child: FocusableSurface(
        onClick: onClick,
        selected: selected,
        focusNode: focusNode,
        shape: _tabShape,
        colors: _tabColors,
        border: _tabBorder,
        glow: _tabGlow,
        child: Stack(
          alignment: Alignment.center,
          children: [
            if (selected)
              Positioned(
                top: 0,
                left: 0,
                right: 0,
                child: Container(
                  height: 3,
                  decoration: const BoxDecoration(
                    gradient: LinearGradient(colors: [AppColors.accentGlow, AppColors.accent]),
                    borderRadius: BorderRadius.only(topLeft: Radius.circular(3), topRight: Radius.circular(3)),
                  ),
                ),
              ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 26),
              child: AppText(label, style: selected ? AppTypography.titleMedium : AppTypography.bodyLarge),
            ),
          ],
        ),
      ),
    );
  }
}
