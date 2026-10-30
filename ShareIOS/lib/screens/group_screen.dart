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
      backgroundColor: page,
      appBar: AppBar(title: const Text('Group share')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text(
            'Share with several people at once. Create a group, then others join — on iPhone use AirDrop or Connect PC for the shared drop.',
            style: TextStyle(color: Color(0xFF5B7380), height: 1.35),
          ),
          const SizedBox(height: 16),
          SectionCard(
            child: InkWell(
              onTap: () => context.push(Routes.send),
              borderRadius: BorderRadius.circular(16),
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: Row(
                  children: [
                    Container(
                      width: 56,
                      height: 56,
                      decoration: const BoxDecoration(shape: BoxShape.circle, gradient: brandGradient),
                      child: const Icon(Icons.groups, color: navy, size: 30),
                    ),
                    const SizedBox(width: 14),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Create a group', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16, color: navy)),
                          Text('Pick files and share to multiple people', style: TextStyle(color: Color(0xFF5B7380), fontSize: 13)),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          const SizedBox(height: 16),
          SectionCard(
            child: InkWell(
              onTap: () => context.push(Routes.receive),
              borderRadius: BorderRadius.circular(16),
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: Row(
                  children: [
                    Container(
                      width: 56,
                      height: 56,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: Theme.of(context).colorScheme.primaryContainer,
                      ),
                      child: Icon(Icons.qr_code_scanner, color: Theme.of(context).colorScheme.primary, size: 30),
                    ),
                    const SizedBox(width: 14),
                    const Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Join a group', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 16, color: navy)),
                          Text('Open Receive and import shared files', style: TextStyle(color: Color(0xFF5B7380), fontSize: 13)),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
