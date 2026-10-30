import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:share_ios/navigation/app_router.dart';
import 'package:share_ios/theme.dart';
import 'package:share_ios/widgets/components.dart';

class BrowserScreen extends StatelessWidget {
  const BrowserScreen({super.key, required this.services});
  final AppServices services;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Browse')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const SectionCard(
            child: Text(
              'Browse and import files from iCloud Drive, On My iPhone, and other providers — iOS equivalent of Android internal storage browse.',
              style: TextStyle(height: 1.35),
            ),
          ),
          const SizedBox(height: 16),
          ShareButton(
            label: 'Open file browser',
            onPressed: () async {
              final result = await FilePicker.platform.pickFiles(allowMultiple: true);
              if (result == null) return;
              for (final f in result.files) {
                final path = f.path;
                if (path != null) await services.library.importPath(path);
              }
              if (context.mounted) {
                ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Added to Share library.')));
              }
            },
          ),
          const SizedBox(height: 16),
          ListenableBuilder(
            listenable: services.library,
            builder: (context, _) {
              final files = services.library.received;
              return SectionCard(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('In Share', style: TextStyle(fontWeight: FontWeight.w700, color: navy)),
                    if (files.isEmpty)
                      const Padding(padding: EdgeInsets.only(top: 8), child: Text('Empty'))
                    else
                      for (final f in files)
                        ListTile(
                          contentPadding: EdgeInsets.zero,
                          title: Text(f.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                          subtitle: Text(formatBytes(f.size)),
                        ),
                  ],
                ),
              );
            },
          ),
        ],
      ),
    );
  }
}
