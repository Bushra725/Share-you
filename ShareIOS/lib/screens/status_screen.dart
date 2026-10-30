import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:share_ios/data/history_store.dart';
import 'package:share_ios/navigation/app_router.dart';
import 'package:share_ios/theme.dart';
import 'package:share_ios/widgets/components.dart';

class StatusScreen extends StatelessWidget {
  const StatusScreen({super.key, required this.services});
  final AppServices services;

  Future<void> _import(BuildContext context) async {
    final result = await FilePicker.platform.pickFiles(type: FileType.media, allowMultiple: true);
    if (result == null) return;
    for (final f in result.files) {
      final path = f.path;
      if (path == null) continue;
      final stored = await services.library.importPath(path);
      await services.history.add(HistoryEntry(
        name: stored.name,
        size: stored.size,
        mime: 'status',
        peer: 'Status',
        sent: false,
        at: DateTime.now(),
        path: stored.path,
      ));
    }
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Saved status media to Share.')));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Status saver')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const SectionCard(
            child: Text(
              'Android can read WhatsApp statuses from shared storage. On iOS, open WhatsApp status → Share → Save to Files, then import here.',
              style: TextStyle(height: 1.4),
            ),
          ),
          const SizedBox(height: 16),
          ShareButton(label: 'Import status media', onPressed: () => _import(context)),
        ],
      ),
    );
  }
}
