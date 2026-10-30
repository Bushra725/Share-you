import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:share_ios/navigation/app_router.dart';
import 'package:share_ios/theme.dart';
import 'package:share_ios/widgets/components.dart';

class AppsScreen extends StatelessWidget {
  const AppsScreen({super.key, required this.services});
  final AppServices services;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Apps')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const SectionCard(
            child: Text(
              'Android lists installed APKs. On iPhone, share app documents or IPA/files from Files instead.',
              style: TextStyle(height: 1.35),
            ),
          ),
          const SizedBox(height: 16),
          ShareButton(
            label: 'Import app documents',
            onPressed: () async {
              final result = await FilePicker.platform.pickFiles(allowMultiple: true);
              if (result == null) return;
              for (final f in result.files) {
                final path = f.path;
                if (path != null) await services.library.importPath(path);
              }
              if (context.mounted) {
                ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Imported into Share.')));
              }
            },
          ),
        ],
      ),
    );
  }
}
