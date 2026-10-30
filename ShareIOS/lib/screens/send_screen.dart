import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:share_ios/data/history_store.dart';
import 'package:share_ios/navigation/app_router.dart';
import 'package:share_ios/navigation/routes.dart';
import 'package:share_ios/theme.dart';
import 'package:share_ios/widgets/components.dart';
import 'package:share_plus/share_plus.dart';

/// Android Send flow adapted for iOS: pick files → radar + share sheet / AirDrop.
class SendScreen extends StatefulWidget {
  const SendScreen({super.key, required this.services});
  final AppServices services;

  @override
  State<SendScreen> createState() => _SendScreenState();
}

class _SendScreenState extends State<SendScreen> {
  final picked = <PlatformFile>[];

  Future<void> _pick() async {
    final result = await FilePicker.platform.pickFiles(allowMultiple: true);
    if (result == null) return;
    setState(() {
      picked
        ..clear()
        ..addAll(result.files);
    });
  }

  Future<void> _send() async {
    if (picked.isEmpty) {
      await _pick();
      return;
    }
    final paths = picked.map((f) => f.path).whereType<String>().toList();
    if (paths.isEmpty) return;
    final total = picked.fold<int>(0, (a, b) => a + b.size);
    if (widget.services.premium.blocks(total)) {
      if (!mounted) return;
      context.push(Routes.premium);
      return;
    }
    await SharePlus.instance.share(ShareParams(files: paths.map(XFile.new).toList()));
    for (final f in picked) {
      await widget.services.history.add(HistoryEntry(
        name: f.name,
        size: f.size,
        mime: f.extension ?? 'file',
        peer: 'Share sheet',
        sent: true,
        at: DateTime.now(),
        path: f.path,
      ));
    }
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Shared')));
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final total = picked.fold<int>(0, (a, b) => a + b.size);
    final avatar = widget.services.prefs.avatar;
    return Scaffold(
      backgroundColor: page,
      appBar: AppBar(title: const Text('Send')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          SectionCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  picked.isEmpty ? 'No files selected' : '${picked.length} file(s) • ${formatBytes(total)}',
                  style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16, color: navy),
                ),
                const SizedBox(height: 8),
                if (picked.isEmpty)
                  TextButton.icon(
                    onPressed: _pick,
                    icon: const Icon(Icons.add),
                    label: const Text('Add files'),
                  )
                else
                  SizedBox(
                    height: 52,
                    child: ListView.separated(
                      scrollDirection: Axis.horizontal,
                      itemCount: picked.length,
                      separatorBuilder: (_, __) => const SizedBox(width: 8),
                      itemBuilder: (_, i) {
                        final f = picked[i];
                        return Chip(
                          avatar: const Icon(Icons.insert_drive_file, size: 18),
                          label: Text(f.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                          onDeleted: () => setState(() => picked.removeAt(i)),
                        );
                      },
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 20),
          Center(
            child: RadarPulse(
              size: 220,
              child: AvatarBubble(index: avatar, size: 76, ring: true),
            ),
          ),
          const Padding(
            padding: EdgeInsets.all(8),
            child: Text(
              'Ask your friend to tap Receive, then share with AirDrop, Messages, or any nearby app',
              textAlign: TextAlign.center,
              style: TextStyle(color: Color(0xFF5B7380)),
            ),
          ),
          const SizedBox(height: 8),
          GradientButton(
            label: picked.isEmpty ? 'Choose files' : 'Send',
            icon: Icons.upload,
            onPressed: _send,
          ),
          const SizedBox(height: 12),
          GradientButton(
            label: 'Add more files',
            icon: Icons.add,
            onPressed: _pick,
          ),
          const SizedBox(height: 20),
          SectionCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Row(
                  children: [
                    Icon(Icons.wifi, color: cyanInk),
                    SizedBox(width: 8),
                    Expanded(
                      child: Text('Same Wi‑Fi / AirDrop', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16, color: navy)),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                const Text(
                  'On iPhone, files leave through the system share sheet — AirDrop to nearby Apple devices, or Messages, Mail, and more. For a PC on the same Wi‑Fi, use Connect PC from Home.',
                  style: TextStyle(color: Color(0xFF5B7380), height: 1.35),
                ),
                TextButton(
                  onPressed: () => context.push(Routes.pc),
                  child: const Text('Open Connect PC'),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
