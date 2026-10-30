import 'package:flutter/material.dart';
import 'package:share_ios/navigation/app_router.dart';
import 'package:share_ios/theme.dart';

class HistoryScreen extends StatelessWidget {
  const HistoryScreen({super.key, required this.services});
  final AppServices services;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Transfer history')),
      body: ListenableBuilder(
        listenable: services.history,
        builder: (context, _) {
          final entries = services.history.entries;
          if (entries.isEmpty) {
            return const Center(child: Text('No transfers yet.'));
          }
          return ListView.separated(
            itemCount: entries.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (context, i) {
              final e = entries[i];
              return ListTile(
                leading: Icon(e.sent ? Icons.upload : Icons.download, color: cyanInk),
                title: Text(e.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                subtitle: Text('${formatBytes(e.size)} · ${e.peer} · ${e.sent ? 'Sent' : 'Received'}'),
              );
            },
          );
        },
      ),
    );
  }
}
