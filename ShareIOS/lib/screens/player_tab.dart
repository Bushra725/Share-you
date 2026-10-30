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

  @override
  void dispose() {
    tab.dispose();
    player.dispose();
    super.dispose();
  }

  Future<void> _pickAudio() async {
    final result = await FilePicker.platform.pickFiles(type: FileType.audio);
    final path = result?.files.single.path;
    if (path == null) return;
    setState(() => current = result!.files.single.name);
    await player.play(DeviceFileSource(path));
  }

  Future<void> _pickVideo() async {
    final result = await FilePicker.platform.pickFiles(type: FileType.video);
    if (result == null) return;
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('Selected ${result.files.single.name}. Open from Files to play inline later.')),
    );
  }

  @override
  Widget build(BuildContext context) {
    final music = widget.services.library.received.where((f) {
      final n = f.name.toLowerCase();
      return n.endsWith('.mp3') || n.endsWith('.m4a') || n.endsWith('.wav') || n.endsWith('.aac');
    }).toList();
    final videos = widget.services.library.received.where((f) {
      final n = f.name.toLowerCase();
      return n.endsWith('.mp4') || n.endsWith('.mov') || n.endsWith('.mkv');
    }).toList();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Player'),
        bottom: TabBar(
          controller: tab,
          labelColor: cyanInk,
          tabs: const [Tab(text: 'Music'), Tab(text: 'Videos')],
        ),
      ),
      body: TabBarView(
        controller: tab,
        children: [
          ListView(
            padding: const EdgeInsets.all(16),
            children: [
              ShareButton(label: 'Pick music', onPressed: _pickAudio),
              if (current != null) ...[
                const SizedBox(height: 12),
                SectionCard(child: Text('Now playing: $current', style: const TextStyle(fontWeight: FontWeight.w700))),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    IconButton(onPressed: () => player.pause(), icon: const Icon(Icons.pause_circle, size: 40, color: cyanInk)),
                    IconButton(onPressed: () => player.resume(), icon: const Icon(Icons.play_circle, size: 40, color: cyanInk)),
                    IconButton(onPressed: () => player.stop(), icon: const Icon(Icons.stop_circle, size: 40, color: cyanInk)),
                  ],
                ),
              ],
              const SizedBox(height: 16),
              const Text('From Share library', style: TextStyle(fontWeight: FontWeight.w700, color: navy)),
              if (music.isEmpty)
                const Padding(padding: EdgeInsets.only(top: 8), child: Text('No music files yet.'))
              else
                for (final f in music)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const BrandIconBox(Icons.music_note, color: catMusic),
                    title: Text(f.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                    subtitle: Text(formatBytes(f.size)),
                    onTap: () async {
                      setState(() => current = f.name);
                      await player.play(DeviceFileSource(f.path));
                    },
                  ),
            ],
          ),
          ListView(
            padding: const EdgeInsets.all(16),
            children: [
              ShareButton(label: 'Pick video', onPressed: _pickVideo),
              const SizedBox(height: 16),
              if (videos.isEmpty)
                const Text('No videos in Share yet.')
              else
                for (final f in videos)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const BrandIconBox(Icons.movie, color: catVideo),
                    title: Text(f.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                    subtitle: Text(formatBytes(f.size)),
                  ),
            ],
          ),
        ],
      ),
    );
  }
}
