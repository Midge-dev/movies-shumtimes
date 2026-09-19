import 'package:flutter/widgets.dart';

import '../../data/plex/plex_image_url.dart';
import '../../data/plex/plex_models.dart';
import '../../kit/card.dart';
import '../../kit/text.dart';
import '../../theme/tokens.dart';
import '../../theme/typography.dart';
import '../common/artwork.dart';

/// Ports ui/library/MovieDetailSections.kt's `CastCrewRow`.
class CastCrewRow extends StatelessWidget {
  final PlexServer server;
  final List<PlexPerson> cast;
  final List<PlexPerson> crew;
  final ValueChanged<PlexPerson> onSelectPerson;

  const CastCrewRow({super.key, required this.server, required this.cast, required this.crew, required this.onSelectPerson});

  @override
  Widget build(BuildContext context) {
    final people = [...cast, ...crew];
    if (people.isEmpty) return const SizedBox.shrink();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        const Padding(
          padding: EdgeInsets.only(left: 32, bottom: 16),
          child: AppText('Cast & Crew', style: AppTypography.titleLarge),
        ),
        SizedBox(
          height: 150,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 32),
            itemCount: people.length,
            separatorBuilder: (context, index) => const SizedBox(width: 18),
            itemBuilder: (context, index) {
              final person = people[index];
              final subtitle = person.role ?? (crew.contains(person) ? 'Crew' : null);
              return _CastMemberAvatar(
                key: ValueKey(person.id ?? person.tag),
                server: server,
                person: person,
                subtitle: subtitle,
                onClick: () => onSelectPerson(person),
              );
            },
          ),
        ),
      ],
    );
  }
}

class _CastMemberAvatar extends StatelessWidget {
  final PlexServer server;
  final PlexPerson person;
  final String? subtitle;
  final VoidCallback onClick;

  const _CastMemberAvatar({super.key, required this.server, required this.person, this.subtitle, required this.onClick});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 112,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.center,
        mainAxisSize: MainAxisSize.min,
        children: [
          SizedBox(
            width: 84,
            height: 84,
            child: AppCard(
              onClick: onClick,
              shape: const CircleBorder(),
              child: person.thumb != null
                  ? SizedBox.expand(child: Artwork(imageUrl: PlexImageUrl.of(server, person.thumb)))
                  : ColoredBox(
                      color: AppColors.surfaceVariant,
                      child: Center(
                        child: AppText(
                          person.tag.isNotEmpty ? person.tag[0].toUpperCase() : '?',
                          style: AppTypography.titleMedium,
                          color: AppColors.white,
                        ),
                      ),
                    ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.only(top: 10),
            child: AppText(person.tag, maxLines: 1, overflow: TextOverflow.ellipsis, textAlign: TextAlign.center),
          ),
          if (subtitle != null)
            Padding(
              padding: const EdgeInsets.only(top: 3),
              child: AppText(subtitle!, color: AppColors.onSurfaceVariant, maxLines: 1, overflow: TextOverflow.ellipsis, textAlign: TextAlign.center),
            ),
        ],
      ),
    );
  }
}

/// Ports ui/library/MovieDetailSections.kt's `RatingsReviewsSection`.
class RatingsReviewsSection extends StatelessWidget {
  final double? rating;
  final double? audienceRating;
  final String? ratingImage;
  final String? audienceRatingImage;
  final List<PlexReview> reviews;

  const RatingsReviewsSection({
    super.key,
    this.rating,
    this.audienceRating,
    this.ratingImage,
    this.audienceRatingImage,
    this.reviews = const [],
  });

  @override
  Widget build(BuildContext context) {
    if (rating == null && audienceRating == null && reviews.isEmpty) return const SizedBox.shrink();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 32, bottom: 16),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            mainAxisSize: MainAxisSize.min,
            children: [
              const AppText('Ratings & Reviews', style: AppTypography.titleLarge),
              if (reviews.isNotEmpty) ...[
                const SizedBox(width: 14),
                AppText('${reviews.length} critic reviews', color: AppColors.onSurfaceVariant),
              ],
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 32),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (rating != null) _RatingTile(percent: (rating! * 10).toInt(), label: 'Critics', image: ratingImage),
              if (rating != null && audienceRating != null) const SizedBox(width: 16),
              if (audienceRating != null) _RatingTile(percent: (audienceRating! * 10).toInt(), label: 'Audience', image: audienceRatingImage),
            ],
          ),
        ),
        if (reviews.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(top: 4),
            child: SizedBox(
              height: 140,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 14),
                itemCount: reviews.length > 3 ? 3 : reviews.length,
                separatorBuilder: (context, index) => const SizedBox(width: 16),
                itemBuilder: (context, index) => _ReviewCard(key: ValueKey(reviews[index].tag), review: reviews[index]),
              ),
            ),
          ),
      ],
    );
  }
}

class _RatingTile extends StatelessWidget {
  final int percent;
  final String label;
  final String? image;

  const _RatingTile({required this.percent, required this.label, this.image});

  Color _badgeColor() {
    final suffix = image?.split('.').last.toLowerCase();
    if (suffix == 'fresh' || suffix == 'ripe' || suffix == 'certified') return const Color(0xFF2E7D4F);
    if (suffix == 'rotten' || suffix == 'spilled') return const Color(0xFF7D2E2E);
    return AppColors.surfaceVariant;
  }

  @override
  Widget build(BuildContext context) {
    return AppCard(
      onClick: () {},
      child: Container(
        decoration: BoxDecoration(color: AppColors.white.withValues(alpha: 0.04), borderRadius: BorderRadius.circular(8)),
        padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(width: 40, height: 40, decoration: BoxDecoration(color: _badgeColor(), borderRadius: BorderRadius.circular(6))),
            const SizedBox(width: 14),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                AppText('$percent%', style: AppTypography.headlineMedium),
                AppText(label, color: AppColors.onSurfaceVariant),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ReviewCard extends StatelessWidget {
  final PlexReview review;

  const _ReviewCard({super.key, required this.review});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 280,
      child: AppCard(
        onClick: () {},
        child: Container(
          decoration: BoxDecoration(color: AppColors.white.withValues(alpha: 0.04), borderRadius: BorderRadius.circular(8)),
          padding: const EdgeInsets.all(18),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              if (review.source != null) AppText(review.source!.toUpperCase(), color: AppColors.accentGlow),
              Padding(
                padding: const EdgeInsets.only(top: 10),
                child: AppText(review.text, maxLines: 4, overflow: TextOverflow.ellipsis),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Ports ui/library/MovieDetailSections.kt's `PosterRow` — reused for
/// related-hub rows and co-star rows.
class PosterRow extends StatelessWidget {
  final String title;
  final List<PlexOnDeckItem> items;
  final PlexServer server;
  final ValueChanged<PlexOnDeckItem> onClick;

  const PosterRow({super.key, required this.title, required this.items, required this.server, required this.onClick});

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) return const SizedBox.shrink();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 32, top: 4, bottom: 16),
          child: AppText(title, style: AppTypography.titleLarge),
        ),
        SizedBox(
          height: 232,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 32),
            itemCount: items.length,
            separatorBuilder: (context, index) => const SizedBox(width: 18),
            itemBuilder: (context, index) {
              final item = items[index];
              return _RelatedPoster(key: ValueKey(item.ratingKey), server: server, item: item, onClick: () => onClick(item));
            },
          ),
        ),
      ],
    );
  }
}

class _RelatedPoster extends StatelessWidget {
  final PlexServer server;
  final PlexOnDeckItem item;
  final VoidCallback onClick;

  const _RelatedPoster({super.key, required this.server, required this.item, required this.onClick});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 132,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          AspectRatio(
            aspectRatio: 2 / 3,
            child: AppCard(
              onClick: onClick,
              child: SizedBox.expand(child: Artwork(imageUrl: PlexImageUrl.of(server, item.thumb))),
            ),
          ),
          Padding(
            padding: const EdgeInsets.only(top: 10),
            child: AppText(item.title, maxLines: 1, overflow: TextOverflow.ellipsis),
          ),
        ],
      ),
    );
  }
}
