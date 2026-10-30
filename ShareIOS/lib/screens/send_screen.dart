import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:share_ios/data/history_store.dart';
import 'package:share_ios/navigation/app_router.dart';
import 'package:share_ios/theme.dart';
import 'package:share_ios/widgets/components.dart';
import 'package:share_plus/share_plus.dart';

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
    setState(() => picked.addAll(result.files));
  }

  Future<void> _send() async {
    if (picked.isEmpty) return;
    final paths = picked.map((f) => f.path).whereType<String>().toList();
    if (paths.isEmpty) return;
    final total = picked.fold<int>(0, (a, b) => a + (b.size));
    if (widget.services.premium.blocks(total)) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Free plan allows up to 100 MB. Get Premium for larger sends.')),
      );
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
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Shared via iOS share sheet.')));
    Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    final total = picked.fold<int>(0, (a, b) => a + b.size);
    return Scaffold(
      appBar: AppBar(title: const Text('Send')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const SectionCard(
            child: Text(
              'Pick files, then share to nearby devices, AirDrop, Messages, or any app — same flow as Android Send, using iOS share.',
              style: TextStyle(height: 1.35),
            ),
          ),
          const SizedBox(height: 16),
          ShareButton(label: 'Add files', onPressed: _pick),
          const SizedBox(height: 12),
          if (picked.isEmpty)
            const Text('No files selected yet.')
          else ...[
            Text('${picked.length} file(s) · ${formatBytes(total)}', style: const TextStyle(fontWeight: FontWeight.w700, color: navy)),
            for (final f in picked)
              ListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(f.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                subtitle: Text(formatBytes(f.size)),
                trailing: IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: () => setState(() => picked.remove(f)),
                ),
              ),
            const SizedBox(height: 12),
            ShareButton(label: 'Send', onPressed: _send),
          ],
        ],
      ),
    );
  }
}
