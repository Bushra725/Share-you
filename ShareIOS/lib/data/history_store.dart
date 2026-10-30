import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

class HistoryEntry {
  HistoryEntry({
    required this.name,
    required this.size,
    required this.mime,
    required this.peer,
    required this.sent,
    required this.at,
    this.path,
  });

  final String name;
  final int size;
  final String mime;
  final String peer;
  final bool sent;
  final DateTime at;
  final String? path;

  Map<String, dynamic> toJson() => {
        'name': name,
        'size': size,
        'mime': mime,
        'peer': peer,
        'sent': sent,
        'at': at.toIso8601String(),
        'path': path,
      };

  static HistoryEntry fromJson(Map<String, dynamic> j) => HistoryEntry(
        name: j['name'] as String,
        size: j['size'] as int,
        mime: j['mime'] as String? ?? 'application/octet-stream',
        peer: j['peer'] as String? ?? '',
        sent: j['sent'] as bool? ?? false,
        at: DateTime.tryParse(j['at'] as String? ?? '') ?? DateTime.now(),
        path: j['path'] as String?,
      );
}

class HistoryStore extends ChangeNotifier {
  List<HistoryEntry> entries = [];

  Future<void> load() async {
    final p = await SharedPreferences.getInstance();
    final raw = p.getString('transfer_history') ?? '[]';
    final list = (jsonDecode(raw) as List).cast<Map<String, dynamic>>();
    entries = list.map(HistoryEntry.fromJson).toList();
    notifyListeners();
  }

  Future<void> add(HistoryEntry e) async {
    entries = [e, ...entries].take(200).toList();
    final p = await SharedPreferences.getInstance();
    await p.setString('transfer_history', jsonEncode(entries.map((e) => e.toJson()).toList()));
    notifyListeners();
  }
}
