import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

class StoredFile {
  StoredFile({required this.name, required this.path, required this.size, required this.savedAt, required this.vault});
  final String name;
  final String path;
  final int size;
  final DateTime savedAt;
  final bool vault;

  Map<String, dynamic> toJson() => {
        'name': name,
        'path': path,
        'size': size,
        'savedAt': savedAt.toIso8601String(),
        'vault': vault,
      };

  static StoredFile fromJson(Map<String, dynamic> json) => StoredFile(
        name: json['name'] as String,
        path: json['path'] as String,
        size: json['size'] as int,
        savedAt: DateTime.parse(json['savedAt'] as String),
        vault: json['vault'] as bool? ?? false,
      );
}

class FileLibrary extends ChangeNotifier {
  List<StoredFile> items = [];

  List<StoredFile> get received => items.where((f) => !f.vault).toList();
  List<StoredFile> get vault => items.where((f) => f.vault).toList();

  Future<void> load() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString('library') ?? '[]';
    final list = (jsonDecode(raw) as List).cast<Map<String, dynamic>>();
    items = list.map(StoredFile.fromJson).where((f) => File(f.path).existsSync()).toList();
    notifyListeners();
  }

  Future<Directory> _dir(bool vault) async {
    final root = await getApplicationDocumentsDirectory();
    final dir = Directory(p.join(root.path, vault ? 'vault' : 'received'));
    if (!dir.existsSync()) await dir.create(recursive: true);
    return dir;
  }

  Future<StoredFile> importBytes({required String name, required List<int> bytes, required bool vault}) async {
    final dir = await _dir(vault);
    final safe = p.basename(name).replaceAll(RegExp(r'[\\/:*?"<>|]'), '_');
    final file = File(p.join(dir.path, '${DateTime.now().millisecondsSinceEpoch}_$safe'));
    await file.writeAsBytes(bytes, flush: true);
    final stored = StoredFile(name: safe, path: file.path, size: bytes.length, savedAt: DateTime.now(), vault: vault);
    items = [stored, ...items];
    await _save();
    return stored;
  }

  Future<StoredFile> importPath(String source, {bool vault = false}) async {
    final bytes = await File(source).readAsBytes();
    return importBytes(name: p.basename(source), bytes: bytes, vault: vault);
  }

  Future<void> remove(StoredFile file) async {
    final disk = File(file.path);
    if (disk.existsSync()) await disk.delete();
    items = items.where((f) => f.path != file.path).toList();
    await _save();
  }

  Future<void> _save() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('library', jsonEncode(items.map((e) => e.toJson()).toList()));
    notifyListeners();
  }
}

String formatBytes(int bytes) {
  if (bytes < 1024) return '$bytes B';
  if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
  if (bytes < 1024 * 1024 * 1024) return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
}
