import 'package:flutter/widgets.dart';

import '../../theme/tokens.dart';
import '../../theme/typography.dart';

const _bloomDurationMs = 900;
const _markDurationMs = 500;
const _wordmarkDurationMs = 500;
const _wordmarkDelayMs = 450;
const _kickerDurationMs = 700;
const _kickerDelayMs = 650;
const _entranceTotalMs = _kickerDelayMs + _kickerDurationMs; // longest end time, 1350ms

const _breathePeriodMs = 1800;

/// Ports ui/splash/SplashScreen.kt: a staggered entrance (bloom glow, mark
/// scale/alpha, wordmark offset/alpha, kicker letter-spacing, each with its
/// own delay) plus an infinite "breathe" pulse behind the logo mark.
class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> with TickerProviderStateMixin {
  late final AnimationController _entrance;
  late final AnimationController _breathe;

  late final Animation<double> _bloomScale;
  late final Animation<double> _markScale;
  late final Animation<double> _markAlpha;
  late final Animation<double> _wordmarkOffset;
  late final Animation<double> _wordmarkAlpha;
  late final Animation<double> _kickerTracking;

  @override
  void initState() {
    super.initState();
    _entrance = AnimationController(duration: const Duration(milliseconds: _entranceTotalMs), vsync: this);
    _breathe = AnimationController(duration: const Duration(milliseconds: _breathePeriodMs), vsync: this);

    _bloomScale = _interval(0, _bloomDurationMs).drive(Tween(begin: 0.35, end: 1.0));
    _markScale = _interval(0, _markDurationMs).drive(Tween(begin: 0.6, end: 1.0));
    _markAlpha = _interval(0, _markDurationMs).drive(Tween(begin: 0.0, end: 1.0));
    _wordmarkOffset = _interval(_wordmarkDelayMs, _wordmarkDelayMs + _wordmarkDurationMs).drive(Tween(begin: 24.0, end: 0.0));
    _wordmarkAlpha = _interval(_wordmarkDelayMs, _wordmarkDelayMs + _wordmarkDurationMs).drive(Tween(begin: 0.0, end: 1.0));
    _kickerTracking = _interval(_kickerDelayMs, _kickerDelayMs + _kickerDurationMs).drive(Tween(begin: 0.06, end: 0.32));

    _entrance.forward();
    _breathe.repeat(reverse: true);
  }

  /// Compose's `tween()` defaults to FastOutSlowIn easing (Material
  /// motion's standard curve) unless an explicit easing is passed —
  /// Flutter's Curves.fastOutSlowIn is the same design.
  CurvedAnimation _interval(int startMs, int endMs) {
    return CurvedAnimation(
      parent: _entrance,
      curve: Interval(startMs / _entranceTotalMs, endMs / _entranceTotalMs, curve: Curves.fastOutSlowIn),
    );
  }

  @override
  void dispose() {
    _entrance.dispose();
    _breathe.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ColoredBox(
      color: AppColors.background,
      child: Center(
        child: AnimatedBuilder(
          animation: Listenable.merge([_entrance, _breathe]),
          builder: (context, child) {
            final breathe = _breathe.value;
            final breatheScale = 1.0 + breathe * 0.08;
            final breatheAlpha = 0.5 + breathe * 0.35;

            return Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                SizedBox(
                  width: 320,
                  height: 320,
                  child: Stack(
                    alignment: Alignment.center,
                    children: [
                      Opacity(
                        opacity: (_markAlpha.value * breatheAlpha).clamp(0.0, 1.0),
                        child: Transform.scale(
                          scale: _bloomScale.value * breatheScale,
                          child: DecoratedBox(
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              gradient: RadialGradient(
                                colors: [AppColors.accentGlow.withValues(alpha: 0.35), AppColors.transparent],
                              ),
                            ),
                          ),
                        ),
                      ),
                      Opacity(
                        opacity: _markAlpha.value,
                        child: Transform.scale(
                          scale: _markScale.value,
                          child: const SizedBox(
                            width: 260,
                            height: 260 * 696 / 1181,
                            child: Image(image: AssetImage('assets/images/logo_mark.png'), fit: BoxFit.contain),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.only(top: 20),
                  child: Opacity(
                    opacity: _wordmarkAlpha.value,
                    child: Transform.translate(
                      offset: Offset(0, _wordmarkOffset.value),
                      child: const SizedBox(
                        width: 260,
                        height: 22,
                        child: Image(image: AssetImage('assets/images/logo_wordmark.png'), fit: BoxFit.contain),
                      ),
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.only(top: 24),
                  child: Opacity(
                    opacity: _wordmarkAlpha.value,
                    child: Text(
                      'Watch together',
                      style: AppTypography.bodyLarge.copyWith(
                        color: AppColors.white.withValues(alpha: 0.7),
                        letterSpacing: _kickerTracking.value * AppTypography.bodyLarge.fontSize!,
                      ),
                    ),
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}
