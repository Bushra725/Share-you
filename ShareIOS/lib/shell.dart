import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:share_ios/ads.dart';
import 'package:share_ios/navigation/app_router.dart';

class AppShell extends StatelessWidget {
  const AppShell({super.key, required this.navigationShell, required this.services});
  final StatefulNavigationShell navigationShell;
  final AppServices services;

  @override
  Widget build(BuildContext context) {
    final i = navigationShell.currentIndex;
    return Scaffold(
      body: navigationShell,
      bottomNavigationBar: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (i == 0 || i == 1) ShareBannerAd(premium: services.premium, secondary: i == 1),
          NavigationBar(
            selectedIndex: i,
            onDestinationSelected: navigationShell.goBranch,
            destinations: const [
              NavigationDestination(icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home), label: 'Home'),
              NavigationDestination(icon: Icon(Icons.folder_outlined), selectedIcon: Icon(Icons.folder), label: 'Files'),
              NavigationDestination(icon: Icon(Icons.play_circle_outline), selectedIcon: Icon(Icons.play_circle), label: 'Player'),
              NavigationDestination(icon: Icon(Icons.widgets_outlined), selectedIcon: Icon(Icons.widgets), label: 'Tools'),
              NavigationDestination(icon: Icon(Icons.person_outline), selectedIcon: Icon(Icons.person), label: 'Me'),
            ],
          ),
        ],
      ),
    );
  }
}
