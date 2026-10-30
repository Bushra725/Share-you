import 'package:flutter/material.dart';
import 'package:share_ios/navigation/app_router.dart';
import 'package:share_ios/theme.dart';
import 'package:share_ios/widgets/components.dart';

/// Android Phone Cleaner hub layout (storage hero + deep-clean cards).
class CleanScreen extends StatelessWidget {
  const CleanScreen({super.key, required this.services});
  final AppServices services;

  @override
  Widget build(BuildContext context) {
    final items = services.library.received;
    final big = items.where((f) => f.size > 10 * 1024 * 1024).toList();
    final bigSize = big.fold<int>(0, (a, b) => a + b.size);

    return Scaffold(
      backgroundColor: page,
      appBar: AppBar(title: const Text('Phone Cleaner')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
        children: [
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              gradient: brandGradient,
              borderRadius: BorderRadius.circular(28),
            ),
            child: Row(
              children: [
                SizedBox(
                  width: 118,
                  height: 118,
                  child: Stack(
                    alignment: Alignment.center,
                    children: [
                      SizedBox(
                        width: 100,
                        height: 100,
                        child: CircularProgressIndicator(
                          value: 0.42,
                          strokeWidth: 10,
                          backgroundColor: Colors.white.withOpacity(0.35),
                          color: navy,
                        ),
                      ),
                      const Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text('42%', style: TextStyle(color: navy, fontWeight: FontWeight.w800, fontSize: 22)),
                          Text('used', style: TextStyle(color: navy, fontSize: 12)),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 18),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Storage used', style: TextStyle(color: navy.withOpacity(0.8), fontWeight: FontWeight.w600)),
                      const Text('Share library', style: TextStyle(color: navy, fontWeight: FontWeight.w800, fontSize: 22)),
                      Text(
                        '${formatBytes(items.fold<int>(0, (a, b) => a + b.size))} in app',
                        style: TextStyle(color: navy.withOpacity(0.85), fontSize: 13),
                      ),
                      const SizedBox(height: 10),
                      Material(
                        color: navy,
                        borderRadius: BorderRadius.circular(50),
                        child: InkWell(
                          onTap: () {},
                          borderRadius: BorderRadius.circular(50),
                          child: const Padding(
                            padding: EdgeInsets.symmetric(horizontal: 18, vertical: 9),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(Icons.cleaning_services, color: cyan1, size: 18),
                                SizedBox(width: 6),
                                Text('Clean junk', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 14),
          const Text('Chat apps', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16, color: navy)),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(child: _ChatCard(title: 'WhatsApp', subtitle: 'Import & clear', color: catPhoto, onTap: () {})),
              const SizedBox(width: 12),
              Expanded(child: _ChatCard(title: 'Telegram', subtitle: 'Import & clear', color: catApps, onTap: () {})),
            ],
          ),
          const SizedBox(height: 14),
          const Text('Deep clean', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16, color: navy)),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: _ToolCard(
                  icon: Icons.storage,
                  title: 'Big Files',
                  subtitle: big.isEmpty ? 'All clean' : formatBytes(bigSize),
                  color: catPdf,
                  onTap: () {},
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _ToolCard(
                  icon: Icons.copy_all,
                  title: 'Duplicate Files',
                  subtitle: 'Find exact copies',
                  color: catDocs,
                  onTap: () {},
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: _ToolCard(
                  icon: Icons.photo_library,
                  title: 'Photo Cleanup',
                  subtitle: 'Screenshots & more',
                  color: catPhoto,
                  onTap: () {},
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _ToolCard(
                  icon: Icons.video_library,
                  title: 'Video Cleanup',
                  subtitle: 'Large & short clips',
                  color: catVideo,
                  onTap: () {},
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: _ToolCard(
                  icon: Icons.music_note,
                  title: 'Audio Cleanup',
                  subtitle: 'Voice notes',
                  color: catPink,
                  onTap: () {},
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: _ToolCard(
                  icon: Icons.apps,
                  title: 'App Cleanup',
                  subtitle: 'Unused apps',
                  color: catApps,
                  onTap: () {},
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          SectionCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('Large files in Share', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16, color: navy)),
                if (big.isEmpty)
                  const Padding(
                    padding: EdgeInsets.only(top: 8),
                    child: Text('No large Share files yet.', style: TextStyle(color: Color(0xFF5B7380))),
                  )
                else
                  for (final f in big)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: const BrandIconBox(Icons.insert_drive_file, size: 40, iconSize: 22),
                      title: Text(f.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                      subtitle: Text(formatBytes(f.size)),
                      trailing: IconButton(
                        icon: const Icon(Icons.delete_outline),
                        onPressed: () => services.library.remove(f),
                      ),
                    ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _ChatCard extends StatelessWidget {
  const _ChatCard({required this.title, required this.subtitle, required this.color, required this.onTap});
  final String title;
  final String subtitle;
  final Color color;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(20),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              BrandIconBox(Icons.chat, size: 46, iconSize: 24, color: color),
              const SizedBox(height: 10),
              Text(title, style: const TextStyle(fontWeight: FontWeight.w700, color: navy)),
              Text(subtitle, style: const TextStyle(fontSize: 12, color: Color(0xFF5B7380))),
            ],
          ),
        ),
      ),
    );
  }
}

class _ToolCard extends StatelessWidget {
  const _ToolCard({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.color,
    required this.onTap,
  });
  final IconData icon;
  final String title;
  final String subtitle;
  final Color color;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(20),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              BrandIconBox(icon, size: 46, iconSize: 24, color: color),
              const SizedBox(height: 10),
              Text(title, style: const TextStyle(fontWeight: FontWeight.w700, color: navy)),
              Text(subtitle, style: const TextStyle(fontSize: 12, color: Color(0xFF5B7380))),
            ],
          ),
        ),
      ),
    );
  }
}
