import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:share_ios/ads.dart';
import 'package:share_ios/navigation/app_router.dart';
/// Bottom shell matching Android AppNav: 5 tabs, 64dp bar, soft selected pill.
class AppShell extends StatelessWidget {
  const AppShell({super.key, required this.navigationShell, required this.services});
  final StatefulNavigationShell navigationShell;
  final AppServices services;

  static const _items = [
    (Icons.home_outlined, Icons.home, 'Home'),
    (Icons.folder_outlined, Icons.folder, 'Files'),
    (Icons.play_circle_outline, Icons.play_circle, 'Player'),
    (Icons.widgets_outlined, Icons.widgets, 'Tools'),
    (Icons.person_outline, Icons.person, 'Me'),
  ];

  @override
  Widget build(BuildContext context) {
    final i = navigationShell.currentIndex;
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      body: navigationShell,
      bottomNavigationBar: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (i == 0 || i == 1) ShareBannerAd(premium: services.premium, secondary: i == 1),
          Material(
            elevation: 10,
            color: scheme.surface,
            child: SafeArea(
              top: false,
              child: SizedBox(
                height: 64,
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: Row(
                    children: [
                      for (var idx = 0; idx < _items.length; idx++)
                        Expanded(
                          child: InkWell(
                            onTap: () => navigationShell.goBranch(idx),
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                AnimatedContainer(
                                  duration: const Duration(milliseconds: 180),
                                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 3),
                                  decoration: BoxDecoration(
                                    color: i == idx ? scheme.primaryContainer : Colors.transparent,
                                    borderRadius: BorderRadius.circular(50),
                                  ),
                                  child: Icon(
                                    i == idx ? _items[idx].$2 : _items[idx].$1,
                                    size: 24,
                                    color: i == idx ? scheme.primary : scheme.onSurface,
                                  ),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  _items[idx].$3,
                                  style: TextStyle(
                                    fontSize: 11,
                                    fontWeight: i == idx ? FontWeight.bold : FontWeight.w500,
                                    color: i == idx ? scheme.primary : scheme.onSurface,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
