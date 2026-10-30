import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:share_ios/navigation/app_router.dart';
import 'package:share_ios/theme.dart';
import 'package:share_ios/widgets/components.dart';

class ConverterScreen extends StatefulWidget {
  const ConverterScreen({super.key, required this.services});
  final AppServices services;

  @override
  State<ConverterScreen> createState() => _ConverterScreenState();
}

class _ConverterScreenState extends State<ConverterScreen> {
  String? picked;
  String target = 'MP4';

  Future<void> _pick() async {
    final result = await FilePicker.platform.pickFiles(type: FileType.media);
    if (result == null) return;
    setState(() => picked = result.files.single.name);
  }

  @override
  Widget build(BuildContext context) {
    final formats = ['MP4', 'MP3', 'GIF', 'JPG', 'PNG', 'PDF'];
    return Scaffold(
      appBar: AppBar(title: const Text('Converter')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const SectionCard(
            child: Text(
              'Convert images, audio, and video — same tools menu as Android. Full encode pipelines ship next; for now pick a file and target format.',
              style: TextStyle(height: 1.35),
            ),
          ),
          const SizedBox(height: 16),
          ShareButton(label: 'Choose media', onPressed: _pick),
          if (picked != null) ...[
            const SizedBox(height: 12),
            Text('Selected: $picked', style: const TextStyle(fontWeight: FontWeight.w700)),
          ],
          const SizedBox(height: 16),
          const Text('Convert to', style: TextStyle(fontWeight: FontWeight.w700, color: navy)),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            children: [
              for (final f in formats)
                ChoiceChip(
                  label: Text(f),
                  selected: target == f,
                  onSelected: (_) => setState(() => target = f),
                ),
            ],
          ),
          const SizedBox(height: 16),
          ShareButton(
            label: 'Convert to $target',
            onPressed: picked == null
                ? null
                : () {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Queued $picked → $target. Native converters are being ported from Android.')),
                    );
                  },
          ),
        ],
      ),
    );
  }
}
