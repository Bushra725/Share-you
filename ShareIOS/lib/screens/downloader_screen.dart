import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:path/path.dart' as p;
import 'package:share_ios/data/history_store.dart';
import 'package:share_ios/navigation/app_router.dart';
import 'package:share_ios/theme.dart';
import 'package:share_ios/widgets/components.dart';

class DownloaderScreen extends StatefulWidget {
  const DownloaderScreen({super.key, required this.services});
  final AppServices services;

  @override
  State<DownloaderScreen> createState() => _DownloaderScreenState();
}

class _DownloaderScreenState extends State<DownloaderScreen> {
  final urlCtrl = TextEditingController();
  String? status;
  bool busy = false;

  @override
  void dispose() {
    urlCtrl.dispose();
    super.dispose();
  }

  Future<void> _download() async {
    final url = urlCtrl.text.trim();
    if (url.isEmpty) return;
    setState(() {
      busy = true;
      status = 'Downloading…';
    });
    try {
      final uri = Uri.parse(url);
      final res = await http.get(uri);
      if (res.statusCode < 200 || res.statusCode >= 300) {
        throw Exception('HTTP ${res.statusCode}');
      }
      final name = p.basename(uri.path).isEmpty ? 'download.bin' : p.basename(uri.path);
      final stored = await widget.services.library.importBytes(name: name, bytes: res.bodyBytes, vault: false);
      await widget.services.history.add(HistoryEntry(
        name: stored.name,
        size: stored.size,
        mime: res.headers['content-type'] ?? 'file',
        peer: uri.host,
        sent: false,
        at: DateTime.now(),
        path: stored.path,
      ));
      setState(() => status = 'Saved ${stored.name} (${formatBytes(stored.size)})');
    } catch (e) {
      setState(() => status = 'Download failed. For Instagram/TikTok/X use a direct media link, or open the site and share the file into Share.');
    } finally {
      setState(() => busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Downloader')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const SectionCard(
            child: Text(
              'Paste a public media URL. Social sites often block direct pulls; Share also accepts files shared into the app from Safari or other apps.',
              style: TextStyle(height: 1.35),
            ),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: urlCtrl,
            decoration: const InputDecoration(
              labelText: 'Video or file link',
              border: OutlineInputBorder(),
            ),
            keyboardType: TextInputType.url,
          ),
          const SizedBox(height: 12),
          ShareButton(label: busy ? 'Working…' : 'Download', onPressed: busy ? null : _download),
          if (status != null) ...[
            const SizedBox(height: 12),
            Text(status!, style: const TextStyle(color: navy)),
          ],
          const SizedBox(height: 16),
          Wrap(
            spacing: 8,
            children: const [
              Chip(label: Text('Instagram')),
              Chip(label: Text('TikTok')),
              Chip(label: Text('Facebook')),
              Chip(label: Text('X')),
              Chip(label: Text('YouTube')),
            ],
          ),
        ],
      ),
    );
  }
}
