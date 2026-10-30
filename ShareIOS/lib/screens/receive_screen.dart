import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:share_ios/data/history_store.dart';
import 'package:share_ios/navigation/app_router.dart';
import 'package:share_ios/navigation/routes.dart';
import 'package:share_ios/theme.dart';
import 'package:share_ios/widgets/components.dart';

/// Android Receive host UI: radar + QR + ready state, iOS import / PC path.
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
    ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Saved to Share library')));
  }

  @override
  Widget build(BuildContext context) {
    final name = widget.services.prefs.deviceName;
    final avatar = widget.services.prefs.avatar;
    final code = 'shareyou://$name';
    return Scaffold(
      backgroundColor: page,
      appBar: AppBar(title: const Text('Receive')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Center(
            child: RadarPulse(
              size: 240,
              child: AvatarBubble(index: avatar, size: 80, ring: true),
            ),
          ),
          const SizedBox(height: 8),
          Text(
            name,
            textAlign: TextAlign.center,
            style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 20, color: navy),
          ),
          const SizedBox(height: 4),
          const Text(
            'Ready to receive',
            textAlign: TextAlign.center,
            style: TextStyle(color: Color(0xFF5B7380)),
          ),
          const SizedBox(height: 20),
          SectionCard(
            child: Column(
              children: [
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(18)),
                  child: QrImageView(
                    data: code,
                    size: 180,
                    eyeStyle: const QrEyeStyle(eyeShape: QrEyeShape.square, color: navy),
                    dataModuleStyle: const QrDataModuleStyle(dataModuleShape: QrDataModuleShape.square, color: navy),
                  ),
                ),
                const SizedBox(height: 12),
                const Text(
                  'Friends on Android can scan this code. On iPhone, import from Files / AirDrop, or use Connect PC on the same Wi‑Fi.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Color(0xFF5B7380), height: 1.35),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          GradientButton(label: 'Import files into Share', icon: Icons.download, onPressed: _import),
          const SizedBox(height: 10),
          GradientButton(
            label: 'Connect PC',
            icon: Icons.computer,
            onPressed: () => context.push(Routes.pc),
          ),
        ],
      ),
    );
  }
}
