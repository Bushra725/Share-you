import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/material.dart';
import 'package:share_ios/library.dart';
import 'package:share_ios/theme.dart';

class PlayerScreen extends StatefulWidget {
  const PlayerScreen({super.key, required this.library});
  final FileLibrary library;

  @override
  State<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends State<PlayerScreen> {
  final _player = AudioPlayer();
  String? current;
  bool playing = false;

  @override
  void initState() {
    super.initState();
    _player.onPlayerStateChanged.listen((state) {
      if (mounted) setState(() => playing = state == PlayerState.playing);
    });
  }

  @override
  void dispose() {
    _player.dispose();
    super.dispose();
  }

  bool _audio(String name) {
    final lower = name.toLowerCase();
    return lower.endsWith('.mp3') || lower.endsWith('.m4a') || lower.endsWith('.wav') || lower.endsWith('.aac') || lower.endsWith('.ogg');
  }

  Future<void> _play(StoredFile file) async {
    await _player.stop();
    await _player.play(DeviceFileSource(file.path));
    setState(() => current = file.name);
  }

  @override
  Widget build(BuildContext context) {
    final tracks = widget.library.received.where((f) => _audio(f.name)).toList();
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const Text('Player', style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800, color: navy)),
        const SizedBox(height: 8),
        Text(current == null ? 'Choose a track saved in Share.' : current!, style: const TextStyle(color: cyanInk)),
        if (current != null)
          Row(
            children: [
              IconButton(
                onPressed: () => playing ? _player.pause() : _player.resume(),
                icon: Icon(playing ? Icons.pause_circle : Icons.play_circle, size: 42, color: cyanInk),
              ),
              IconButton(onPressed: () => _player.stop(), icon: const Icon(Icons.stop_circle, size: 36)),
            ],
          ),
        if (tracks.isEmpty)
          const Padding(padding: EdgeInsets.only(top: 24), child: Text('Import an MP3, M4A, or WAV file to play it here.'))
        else
          for (final track in tracks)
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.music_note, color: cyanInk),
              title: Text(track.name, maxLines: 1, overflow: TextOverflow.ellipsis),
              subtitle: Text(formatBytes(track.size)),
              onTap: () => _play(track),
            ),
      ],
    );
  }
}
