import 'package:audioplayers/audioplayers.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:share_ios/navigation/app_router.dart';
import 'package:share_ios/theme.dart';
import 'package:share_ios/widgets/components.dart';

class PlayerTab extends StatefulWidget {
  const PlayerTab({super.key, required this.services});
  final AppServices services;

  @override
  State<PlayerTab> createState() => _PlayerTabState();
}

class _PlayerTabState extends State<PlayerTab> with SingleTickerProviderStateMixin {
  late final tab = TabController(length: 2, vsync: this);
  final player = AudioPlayer();
  String? current;
  final playlist = <({String name, String path, int size})>[];

  @override
  void dispose() {
    tab.dispose();
    player.dispose();
    super.dispose();
  }

  Future<void> _pickAudio({bool playAll = false}) async {
    final result = await FilePicker.platform.pickFiles(type: FileType.audio, allowMultiple: playAll);
    if (result == null || result.files.isEmpty) return;
    setState(() {
      playlist
        ..clear()
        ..addAll(
          result.files.where((f) => f.path != null).map((f) => (name: f.name, path: f.path!, size: f.size)),
        );
      current = playlist.first.name;
    });
    await player.play(DeviceFileSource(playlist.first.path));
  }

  Future<void> _playPath(String name, String path) async {
    setState(() => current = name);
    await player.play(DeviceFileSource(path));
  }

  @override
  Widget build(BuildContext context) {
    final music = [
      ...playlist,
      ...widget.services.library.received
          .where((f) {
            final n = f.name.toLowerCase();
            return n.endsWith('.mp3') || n.endsWith('.m4a') || n.endsWith('.wav') || n.endsWith('.aac');
          })
          .map((f) => (name: f.name, path: f.path, size: f.size)),
    ];
    final videos = widget.services.library.received.where((f) {
      final n = f.name.toLowerCase();
      return n.endsWith('.mp4') || n.endsWith('.mov') || n.endsWith('.mkv');
    }).toList();

    return Scaffold(
      backgroundColor: page,
      appBar: AppBar(
        title: const Text('Player'),
        bottom: TabBar(
          controller: tab,
          labelColor: cyanInk,
          unselectedLabelColor: const Color(0xFF5B7380),
          indicatorColor: cyanInk,
          tabs: const [
            Tab(icon: Icon(Icons.music_note), text: 'Music'),
            Tab(icon: Icon(Icons.video_library), text: 'Videos'),
          ],
        ),
      ),
      body: TabBarView(
        controller: tab,
        children: [
          ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Row(
                children: [
                  Expanded(child: GradientButton(label: 'Play all', icon: Icons.play_arrow, onPressed: () => _pickAudio(playAll: true))),
                  const SizedBox(width: 10),
                  Expanded(child: GradientButton(label: 'Shuffle', icon: Icons.shuffle, onPressed: () => _pickAudio(playAll: true))),
                ],
              ),
              if (current != null) ...[
                const SizedBox(height: 16),
                SectionCard(
                  child: Column(
                    children: [
                      Container(
                        width: 72,
                        height: 72,
                        decoration: const BoxDecoration(shape: BoxShape.circle, gradient: brandGradient),
                        child: const Icon(Icons.play_arrow, color: navy, size: 40),
                      ),
                      const SizedBox(height: 10),
                      Text('Now playing', style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant)),
                      Text(current!, textAlign: TextAlign.center, style: const TextStyle(fontWeight: FontWeight.w800, color: navy)),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          IconButton(onPressed: () => player.pause(), icon: const Icon(Icons.pause_circle, size: 40, color: cyanInk)),
                          IconButton(onPressed: () => player.resume(), icon: const Icon(Icons.play_circle, size: 40, color: cyanInk)),
                          IconButton(onPressed: () => player.stop(), icon: const Icon(Icons.stop_circle, size: 40, color: cyanInk)),
                        ],
                      ),
                    ],
                  ),
                ),
              ],
              const SizedBox(height: 16),
              if (music.isEmpty)
                const EmptyState(icon: Icons.music_note, text: 'No music yet. Tap Play all to pick songs.')
              else
                for (final f in music)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Container(
                      width: 48,
                      height: 48,
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(12),
                        gradient: brandGradient,
                      ),
                      child: const Icon(Icons.music_note, color: navy),
                    ),
                    title: Text(f.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                    subtitle: Text(formatBytes(f.size)),
                    tileColor: current == f.name ? Theme.of(context).colorScheme.primaryContainer : null,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    onTap: () => _playPath(f.name, f.path),
                  ),
            ],
          ),
          ListView(
            padding: const EdgeInsets.all(16),
            children: [
              GradientButton(
                label: 'Pick video',
                icon: Icons.video_library,
                onPressed: () async {
                  final result = await FilePicker.platform.pickFiles(type: FileType.video);
                  if (result == null || !mounted) return;
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text('Selected ${result.files.single.name}')),
                  );
                },
              ),
              const SizedBox(height: 16),
              if (videos.isEmpty)
                const EmptyState(icon: Icons.video_library, text: 'No videos in Share yet.')
              else
                GridView.builder(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: videos.length,
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                    crossAxisCount: 2,
                    mainAxisSpacing: 12,
                    crossAxisSpacing: 12,
                    childAspectRatio: 0.95,
                  ),
                  itemBuilder: (_, i) {
                    final f = videos[i];
                    return SectionCard(
                      padding: 10,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          AspectRatio(
                            aspectRatio: 16 / 10,
                            child: Container(
                              decoration: BoxDecoration(
                                color: catVideo.withOpacity(0.2),
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: const Icon(Icons.play_circle, color: catVideo, size: 40),
                            ),
                          ),
                          const SizedBox(height: 8),
                          Text(f.name, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w600)),
                          Text(formatBytes(f.size), style: const TextStyle(fontSize: 12, color: Color(0xFF5B7380))),
                        ],
                      ),
                    );
                  },
                ),
            ],
          ),
        ],
      ),
    );
  }
}
