import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:share_ios/library.dart';
import 'package:share_ios/premium_store.dart';
import 'package:share_ios/screens/premium_screen.dart';
import 'package:share_ios/theme.dart';
import 'package:share_plus/share_plus.dart';

class SendScreen extends StatefulWidget {
  const SendScreen({super.key, required this.premium});
  final PremiumStore premium;

  @override
  State<SendScreen> createState() => _SendScreenState();
}

class _SendScreenState extends State<SendScreen> {
  List<PlatformFile> picked = [];

  Future<void> _pick() async {
    final result = await FilePicker.platform.pickFiles(allowMultiple: true, withData: false);
    if (result == null) return;
    setState(() => picked = result.files);
  }

  Future<void> _send() async {
    final tooBig = picked.where((f) => widget.premium.blocks(f.size)).toList();
    if (tooBig.isNotEmpty) {
      if (!mounted) return;
      await Navigator.push(context, MaterialPageRoute(builder: (_) => PremiumScreen(premium: widget.premium)));
      return;
    }
    final paths = picked.map((f) => f.path).whereType<String>().toList();
    if (paths.isEmpty) return;
    await SharePlus.instance.share(ShareParams(files: paths.map((path) => XFile(path)).toList()));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Send')),
      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Pick the files you want to send. iPhone opens the share sheet so you can AirDrop them or hand them to another app. Files larger than 100 MB need Premium.',
              style: TextStyle(color: navy, height: 1.4),
            ),
            const SizedBox(height: 16),
            ShareButton(label: 'Choose files', onPressed: _pick),
            const SizedBox(height: 16),
            Expanded(
              child: ListView(
                children: picked
                    .map((f) => ListTile(
                          contentPadding: EdgeInsets.zero,
                          title: Text(f.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                          subtitle: Text(formatBytes(f.size)),
                        ))
                    .toList(),
              ),
            ),
            ShareButton(label: 'Send', onPressed: picked.isEmpty ? null : _send),
          ],
        ),
      ),
    );
  }
}

class ReceiveScreen extends StatelessWidget {
  const ReceiveScreen({super.key, required this.library, required this.premium});
  final FileLibrary library;
  final PremiumStore premium;

  Future<void> _import(BuildContext context) async {
    final result = await FilePicker.platform.pickFiles(allowMultiple: true);
    if (result == null) return;
    for (final file in result.files) {
      if (premium.blocks(file.size)) {
        if (context.mounted) {
          await Navigator.push(context, MaterialPageRoute(builder: (_) => PremiumScreen(premium: premium)));
        }
        return;
      }
      final path = file.path;
      if (path != null) await library.importPath(path);
    }
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Saved in Share')));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Receive')),
      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Bring a file into Share from Files, Mail, or another app. It is saved on this iPhone and is not uploaded.',
              style: TextStyle(color: navy, height: 1.4),
            ),
            const SizedBox(height: 16),
            ShareButton(label: 'Import a file', onPressed: () => _import(context)),
          ],
        ),
      ),
    );
  }
}
