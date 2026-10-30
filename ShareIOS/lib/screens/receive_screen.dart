import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:share_ios/data/history_store.dart';
import 'package:share_ios/navigation/app_router.dart';
import 'package:share_ios/theme.dart';
import 'package:share_ios/widgets/components.dart';

class ReceiveScreen extends StatefulWidget {
  const ReceiveScreen({super.key, required this.services});
  final AppServices services;

  @override
  State<ReceiveScreen> createState() => _ReceiveScreenState();
}

class _ReceiveScreenState extends State<ReceiveScreen> {
  Future<void> _import() async {
    final result = await FilePicker.platform.pickFiles(allowMultiple: true);
    if (result == null) return;
    for (final f in result.files) {
      final path = f.path;
      if (path == null) continue;
      final stored = await widget.services.library.importPath(path);
      await widget.services.history.add(HistoryEntry(
        name: stored.name,
        size: stored.size,
        mime: 'file',
        peer: 'Import',
        sent: false,
        at: DateTime.now(),
        path: stored.path,
      ));
    }
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Saved to Share library.')));
  }

  @override
  Widget build(BuildContext context) {
    final code = 'shareyou://${widget.services.prefs.deviceName}';
    return Scaffold(
      appBar: AppBar(title: const Text('Receive')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          SectionCard(
            child: Column(
              children: [
                const Text('Ready to receive', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 18, color: navy)),
                const SizedBox(height: 8),
                const Text(
                  'On Android, nearby phones join over Wi‑Fi. On iPhone, import from Files/AirDrop or use Connect PC on the same Wi‑Fi.',
                  textAlign: TextAlign.center,
                  style: TextStyle(height: 1.35, color: Color(0xFF5B7380)),
                ),
                const SizedBox(height: 16),
                QrImageView(data: code, size: 180),
                Text(widget.services.prefs.deviceName, style: const TextStyle(fontWeight: FontWeight.w700)),
              ],
            ),
          ),
          const SizedBox(height: 16),
          ShareButton(label: 'Import files into Share', onPressed: _import),
        ],
      ),
    );
  }
}
