import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:share_ios/theme.dart';

/// Matches Android `SectionCard` — surface, 20 radius, no elevation/border.
class SectionCard extends StatelessWidget {
  const SectionCard({super.key, required this.child, this.padding = 16});
  final Widget child;
  final double padding;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Theme.of(context).colorScheme.surface,
      borderRadius: BorderRadius.circular(20),
      child: Padding(padding: EdgeInsets.all(padding), child: child),
    );
  }
}

/// Brand tile: Cyan1 → Cyan3 → #0094D4 (Android BrandTileGradient).
class BrandIconBox extends StatelessWidget {
  const BrandIconBox(this.icon, {super.key, this.size = 42, this.iconSize = 24, this.color});
  final IconData icon;
  final double size;
  final double iconSize;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        gradient: color == null
            ? const LinearGradient(colors: [cyan1, cyan3, Color(0xFF0094D4)])
            : LinearGradient(colors: [Color.lerp(color!, Colors.white, 0.28)!, color!]),
        borderRadius: BorderRadius.circular(size * 0.3),
      ),
      child: Icon(icon, color: Colors.white, size: iconSize),
    );
  }
}

/// Android ToolTile: 54×54, radius 17, light→deep gradient of tool color.
class ToolTile extends StatelessWidget {
  const ToolTile({
    super.key,
    required this.icon,
    required this.label,
    required this.color,
    required this.onTap,
    this.badge,
  });
  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;
  final String? badge;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(14),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 10),
        child: Column(
          children: [
            Container(
              width: 54,
              height: 54,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(17),
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [Color.lerp(color, Colors.white, 0.28)!, color],
                ),
              ),
              child: Icon(icon, color: Colors.white, size: 28),
            ),
            const SizedBox(height: 6),
            Text(
              label,
              textAlign: TextAlign.center,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w500,
                color: Theme.of(context).colorScheme.onSurface,
              ),
            ),
            if (badge != null && badge!.isNotEmpty)
              Text(
                badge!,
                style: TextStyle(fontSize: 11, color: Theme.of(context).colorScheme.onSurfaceVariant),
              ),
          ],
        ),
      ),
    );
  }
}

class TileGrid extends StatelessWidget {
  const TileGrid({super.key, required this.tiles, this.columns = 4});
  final List<ToolTile> tiles;
  final int columns;

  @override
  Widget build(BuildContext context) {
    final rows = <Widget>[];
    for (var i = 0; i < tiles.length; i += columns) {
      final slice = tiles.sublist(i, (i + columns).clamp(0, tiles.length));
      rows.add(
        Row(
          children: [
            for (final t in slice) Expanded(child: t),
            for (var j = slice.length; j < columns; j++) const Expanded(child: SizedBox()),
          ],
        ),
      );
    }
    return Column(children: rows);
  }
}

class BigAction extends StatelessWidget {
  const BigAction({super.key, required this.label, required this.icon, required this.onTap});
  final String label;
  final IconData icon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        Material(
          color: Colors.white,
          shape: const CircleBorder(),
          child: InkWell(
            customBorder: const CircleBorder(),
            onTap: onTap,
            child: SizedBox(
              width: 92,
              height: 92,
              child: Center(
                child: Container(
                  width: 72,
                  height: 72,
                  decoration: const BoxDecoration(shape: BoxShape.circle, gradient: brandGradient),
                  child: Icon(icon, color: navy, size: 36),
                ),
              ),
            ),
          ),
        ),
        const SizedBox(height: 8),
        Text(label, style: const TextStyle(color: navy, fontWeight: FontWeight.bold, fontSize: 16)),
      ],
    );
  }
}

class PremiumBanner extends StatelessWidget {
  const PremiumBanner({super.key, required this.onTap});
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: Ink(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(20),
            gradient: const LinearGradient(colors: [Color(0xFF0B2530), Color(0xFF004E66)]),
          ),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                Container(
                  width: 46,
                  height: 46,
                  decoration: const BoxDecoration(color: Color(0xFFFFC83D), shape: BoxShape.circle),
                  child: const Icon(Icons.workspace_premium, color: navy, size: 28),
                ),
                const SizedBox(width: 12),
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Go Premium', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 17)),
                      Text(
                        'Unlimited file size • No ads • Safebox',
                        style: TextStyle(color: Color(0xCCFFFFFF), fontSize: 12),
                      ),
                    ],
                  ),
                ),
                const Icon(Icons.chevron_right, color: Colors.white),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class MenuRow extends StatelessWidget {
  const MenuRow({
    super.key,
    required this.icon,
    required this.title,
    this.subtitle,
    required this.onTap,
  });
  final IconData icon;
  final String title;
  final String? subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 8),
      leading: BrandIconBox(icon, size: 44, iconSize: 26),
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.w600)),
      subtitle: subtitle == null ? null : Text(subtitle!),
      trailing: const Icon(Icons.chevron_right),
      onTap: onTap,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
    );
  }
}

/// Android AvatarBubble — BrandGradient circle + emoji, optional white ring.
class AvatarBubble extends StatelessWidget {
  const AvatarBubble({super.key, required this.index, this.size = 44, this.ring = false});
  final int index;
  final double size;
  final bool ring;

  static const avatars = ['🐱', '🦊', '🐼', '🐸', '🐵', '🦄', '🐯', '🐨', '🐙', '🐧', '🦁', '🐶'];

  @override
  Widget build(BuildContext context) {
    final emoji = avatars[index % avatars.length];
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        gradient: brandGradient,
        border: ring ? Border.all(color: Colors.white, width: 3) : null,
      ),
      child: Text(emoji, style: TextStyle(fontSize: size * 0.5)),
    );
  }
}

/// Android RadarPulse — 3 expanding Cyan3 rings, 2200ms.
class RadarPulse extends StatefulWidget {
  const RadarPulse({super.key, required this.child, this.size = 220});
  final Widget child;
  final double size;

  @override
  State<RadarPulse> createState() => _RadarPulseState();
}

class _RadarPulseState extends State<RadarPulse> with SingleTickerProviderStateMixin {
  late final controller = AnimationController(vsync: this, duration: const Duration(milliseconds: 2200))..repeat();

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: widget.size,
      height: widget.size,
      child: AnimatedBuilder(
        animation: controller,
        builder: (context, child) {
          return CustomPaint(
            painter: _RadarPainter(controller.value),
            child: Center(child: child),
          );
        },
        child: widget.child,
      ),
    );
  }
}

class _RadarPainter extends CustomPainter {
  _RadarPainter(this.p);
  final double p;

  @override
  void paint(Canvas canvas, Size size) {
    final maxR = math.min(size.width, size.height) / 2;
    final c = Offset(size.width / 2, size.height / 2);
    for (var i = 0; i < 3; i++) {
      final f = (p + i / 3) % 1.0;
      final paint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 3
        ..color = cyan3.withOpacity((1 - f) * 0.45);
      canvas.drawCircle(c, maxR * (0.3 + 0.7 * f), paint);
    }
  }

  @override
  bool shouldRepaint(covariant _RadarPainter old) => old.p != p;
}

class EmptyState extends StatelessWidget {
  const EmptyState({super.key, required this.icon, required this.text, this.action});
  final IconData icon;
  final String text;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(40),
      child: Column(
        children: [
          Container(
            width: 84,
            height: 84,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: Theme.of(context).colorScheme.surfaceContainerHighest,
            ),
            child: Icon(icon, size: 40, color: Theme.of(context).colorScheme.onSurfaceVariant),
          ),
          const SizedBox(height: 14),
          Text(text, textAlign: TextAlign.center, style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant)),
          if (action != null) ...[const SizedBox(height: 16), action!],
        ],
      ),
    );
  }
}

class GradientButton extends StatelessWidget {
  const GradientButton({super.key, required this.label, required this.onPressed, this.icon});
  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: DecoratedBox(
        decoration: BoxDecoration(
          gradient: brandGradient,
          borderRadius: BorderRadius.circular(14),
        ),
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: onPressed,
            borderRadius: BorderRadius.circular(14),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  if (icon != null) ...[
                    Icon(icon, color: navy, size: 20),
                    const SizedBox(width: 8),
                  ],
                  Text(label, style: const TextStyle(color: navy, fontWeight: FontWeight.bold, fontSize: 16)),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
