import 'package:flutter/material.dart';
import 'package:share_ios/pc_server.dart';
import 'package:share_ios/premium_store.dart';
import 'package:share_ios/library.dart';
import 'package:share_ios/theme.dart';

class PcScreen extends StatefulWidget {
  const PcScreen({super.key, required this.library, required this.premium, required this.server});
  final FileLibrary library;
  final PremiumStore premium;
  final PcShare server;

  @override
  State<PcScreen> createState() => _PcScreenState();
}

class _PcScreenState extends State<PcScreen> {
  bool busy = false;

  Future<void> _toggle() async {
    setState(() => busy = true);
    if (widget.server.url == null) {
      await widget.server.start(widget.library, widget.premium);
    } else {
      await widget.server.stop();
    }
    if (mounted) setState(() => busy = false);
  }

  @override
  void dispose() {
    widget.server.stop();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final url = widget.server.url;
    return Scaffold(
      appBar: AppBar(title: const Text('Connect to PC')),
      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Keep Share open. Join the same Wi-Fi on the computer and type the address below. The page works without an internet connection.',
              style: TextStyle(color: navy, height: 1.4),
            ),
            const SizedBox(height: 20),
            if (url != null)
              SelectableText(url, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700, color: cyanInk)),
            if (widget.server.error != null) Text(widget.server.error!, style: const TextStyle(color: Colors.red)),
            const Spacer(),
            ShareButton(
              label: busy ? 'Please wait' : (url == null ? 'Start PC page' : 'Stop'),
              onPressed: busy ? null : _toggle,
            ),
          ],
        ),
      ),
    );
  }
}
