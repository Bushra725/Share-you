import 'package:flutter/material.dart';
import 'package:share_ios/library.dart';
import 'package:share_ios/navigation/app_router.dart';
import 'package:share_ios/theme.dart';

class CategoryScreen extends StatelessWidget {
  const CategoryScreen({super.key, required this.services, required this.kind});
  final AppServices services;
  final String kind;

  bool _match(StoredFile f) {
    final n = f.name.toLowerCase();
    switch (kind) {
      case 'music':
        return n.endsWith('.mp3') || n.endsWith('.m4a') || n.endsWith('.wav') || n.endsWith('.aac');
      case 'videos':
        return n.endsWith('.mp4') || n.endsWith('.mov') || n.endsWith('.mkv');
      case 'photos':
        return n.endsWith('.jpg') || n.endsWith('.jpeg') || n.endsWith('.png') || n.endsWith('.heic') || n.endsWith('.gif');
      case 'docs':
        return n.endsWith('.pdf') || n.endsWith('.doc') || n.endsWith('.docx') || n.endsWith('.txt') || n.endsWith('.xls');
      case 'zips':
        return n.endsWith('.zip') || n.endsWith('.rar') || n.endsWith('.7z');
      default:
        return true;
    }
  }

  String get title {
    switch (kind) {
      case 'music':
        return 'Music';
      case 'videos':
        return 'Videos';
      case 'photos':
        return 'Photos';
      case 'docs':
        return 'Documents';
      case 'zips':
        return 'ZIP';
      default:
        return 'Received';
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: ListenableBuilder(
        listenable: services.library,
        builder: (context, _) {
          final files = services.library.received.where(_match).toList();
          if (files.isEmpty) {
            return const Center(child: Text('No files in this category yet.'));
          }
          return ListView.builder(
            itemCount: files.length,
            itemBuilder: (context, i) {
              final f = files[i];
              return ListTile(
                title: Text(f.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                subtitle: Text(formatBytes(f.size)),
                trailing: IconButton(
                  icon: const Icon(Icons.delete_outline),
                  onPressed: () => services.library.remove(f),
                ),
              );
            },
          );
        },
      ),
    );
  }
}
