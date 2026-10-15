import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:local_auth/local_auth.dart';
import 'package:share_ios/library.dart';
import 'package:share_ios/screens/premium_screen.dart';
import 'package:share_ios/premium_store.dart';
import 'package:share_ios/theme.dart';

class SafeboxScreen extends StatefulWidget {
  const SafeboxScreen({super.key, required this.library, required this.premium});
  final FileLibrary library;
  final PremiumStore premium;

  @override
  State<SafeboxScreen> createState() => _SafeboxScreenState();
}

class _SafeboxScreenState extends State<SafeboxScreen> {
  final _storage = const FlutterSecureStorage();
  final _pin = TextEditingController();
  final _auth = LocalAuthentication();
  bool ready = false;
  bool hasPin = false;
  bool unlocked = false;
  bool faceId = false;
  String? error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    hasPin = (await _storage.read(key: 'vault_pin')) != null;
    faceId = (await _storage.read(key: 'vault_face')) == '1';
    ready = true;
    if (mounted) setState(() {});
  }

  String _hash(String pin) => sha256.convert(utf8.encode('share-vault:$pin')).toString();

  Future<void> _savePin() async {
    final pin = _pin.text.trim();
    if (pin.length < 4 || pin.length > 6) {
      setState(() => error = 'Use 4 to 6 digits.');
      return;
    }
    await _storage.write(key: 'vault_pin', value: _hash(pin));
    _pin.clear();
    setState(() {
      hasPin = true;
      unlocked = true;
      error = null;
    });
  }

  Future<void> _unlock() async {
    final saved = await _storage.read(key: 'vault_pin');
    if (saved != null && saved == _hash(_pin.text.trim())) {
      _pin.clear();
      setState(() {
        unlocked = true;
        error = null;
      });
    } else {
      setState(() => error = 'That PIN does not match.');
    }
  }

  Future<void> _face() async {
    try {
      final ok = await _auth.authenticate(localizedReason: 'Unlock Safebox');
      if (ok) setState(() => unlocked = true);
    } on PlatformException {
      setState(() => error = 'Face ID is not available.');
    }
  }

  Future<void> _add() async {
    final result = await FilePicker.platform.pickFiles();
    final path = result?.files.single.path;
    if (path == null) return;
    final size = File(path).lengthSync();
    if (widget.premium.blocks(size)) {
      if (!mounted) return;
      await Navigator.push(context, MaterialPageRoute(builder: (_) => PremiumScreen(premium: widget.premium)));
      return;
    }
    await widget.library.importPath(path, vault: true);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Saved')));
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!widget.premium.isPremium) {
      return Scaffold(
        appBar: AppBar(title: const Text('Safebox')),
        body: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Text('Safebox is part of Premium. Files you add stay on this iPhone.', textAlign: TextAlign.center),
              const SizedBox(height: 16),
              ShareButton(
                label: 'See Premium',
                onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => PremiumScreen(premium: widget.premium))),
              ),
            ],
          ),
        ),
      );
    }
    if (!ready) return const Scaffold(body: Center(child: CircularProgressIndicator()));
    if (!unlocked) {
      return Scaffold(
        appBar: AppBar(title: const Text('Safebox')),
        body: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(hasPin ? 'Enter your PIN' : 'Create a 4 to 6 digit PIN', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700, color: navy)),
              const SizedBox(height: 12),
              TextField(
                controller: _pin,
                keyboardType: TextInputType.number,
                obscureText: true,
                maxLength: 6,
                decoration: const InputDecoration(border: OutlineInputBorder(), counterText: ''),
              ),
              if (error != null) Text(error!, style: const TextStyle(color: Colors.red)),
              const SizedBox(height: 12),
              ShareButton(label: hasPin ? 'Unlock' : 'Save PIN', onPressed: hasPin ? _unlock : _savePin),
              if (hasPin && faceId) TextButton(onPressed: _face, child: const Text('Use Face ID')),
            ],
          ),
        ),
      );
    }
    final files = widget.library.vault;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Safebox'),
        actions: [
          IconButton(
            onPressed: () => setState(() => unlocked = false),
            icon: const Icon(Icons.lock),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          SwitchListTile(
            title: const Text('Unlock with Face ID'),
            value: faceId,
            onChanged: (v) async {
              await _storage.write(key: 'vault_face', value: v ? '1' : '0');
              setState(() => faceId = v);
            },
          ),
          ShareButton(label: 'Add a file', onPressed: _add),
          const SizedBox(height: 12),
          if (files.isEmpty) const Text('Safebox is empty.') else
            for (final file in files)
              ListTile(
                title: Text(file.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                subtitle: Text(formatBytes(file.size)),
                trailing: IconButton(icon: const Icon(Icons.delete_outline), onPressed: () => widget.library.remove(file)),
              ),
        ],
      ),
    );
  }
}
