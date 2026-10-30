import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:share_ios/navigation/app_router.dart';
import 'package:share_ios/navigation/routes.dart';
import 'package:share_ios/theme.dart';
import 'package:share_ios/widgets/components.dart';

class GroupScreen extends StatelessWidget {
  const GroupScreen({super.key, required this.services});
  final AppServices services;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Group share')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const SectionCard(
            child: Text(
              'Create a group room, invite nearby people, and share many files at once — matching the Android Group share flow. On iOS, start with Send or Connect PC for multiparty sharing on the same Wi‑Fi.',
              style: TextStyle(height: 1.4),
            ),
          ),
          const SizedBox(height: 16),
          ShareButton(label: 'Send to group via Share', onPressed: () => context.push(Routes.send)),
          const SizedBox(height: 12),
          ShareButton(label: 'Connect PC for LAN share', onPressed: () => context.push(Routes.pc)),
        ],
      ),
    );
  }
}
