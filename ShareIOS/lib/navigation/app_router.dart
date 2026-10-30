import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:share_ios/library.dart';
import 'package:share_ios/navigation/routes.dart';
import 'package:share_ios/pc_server.dart';
import 'package:share_ios/premium_store.dart';
import 'package:share_ios/data/history_store.dart';
import 'package:share_ios/data/prefs.dart';
import 'package:share_ios/screens/apps_screen.dart';
import 'package:share_ios/screens/browser_screen.dart';
import 'package:share_ios/screens/category_screen.dart';
import 'package:share_ios/screens/clean_screen.dart';
import 'package:share_ios/screens/converter_screen.dart';
import 'package:share_ios/screens/downloader_screen.dart';
import 'package:share_ios/screens/files_tab.dart';
import 'package:share_ios/screens/group_screen.dart';
import 'package:share_ios/screens/history_screen.dart';
import 'package:share_ios/screens/home_tab.dart';
import 'package:share_ios/screens/me_tab.dart';
import 'package:share_ios/screens/player_tab.dart';
import 'package:share_ios/screens/premium_screen.dart';
import 'package:share_ios/screens/privacy_screen.dart';
import 'package:share_ios/screens/receive_screen.dart';
import 'package:share_ios/screens/safebox_screen.dart';
import 'package:share_ios/screens/send_screen.dart';
import 'package:share_ios/screens/settings_screen.dart';
import 'package:share_ios/screens/status_screen.dart';
import 'package:share_ios/screens/tools_tab.dart';
import 'package:share_ios/screens/pc_screen.dart';
import 'package:share_ios/shell.dart';

class AppServices {
  AppServices({
    required this.library,
    required this.premium,
    required this.prefs,
    required this.history,
    required this.server,
  });

  final FileLibrary library;
  final PremiumStore premium;
  final AppPrefs prefs;
  final HistoryStore history;
  final PcShare server;
}

GoRouter buildRouter(AppServices s) {
  return GoRouter(
    initialLocation: Routes.home,
    routes: [
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) => AppShell(navigationShell: navigationShell, services: s),
        branches: [
          StatefulShellBranch(routes: [
            GoRoute(
              path: Routes.home,
              builder: (_, __) => HomeTab(services: s),
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: Routes.files,
              builder: (_, __) => FilesTab(services: s),
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: Routes.player,
              builder: (_, __) => PlayerTab(services: s),
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: Routes.tools,
              builder: (_, __) => ToolsTab(services: s),
            ),
          ]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: Routes.me,
              builder: (_, __) => MeTab(services: s),
            ),
          ]),
        ],
      ),
      GoRoute(path: Routes.send, builder: (_, __) => SendScreen(services: s)),
      GoRoute(path: Routes.receive, builder: (_, __) => ReceiveScreen(services: s)),
      GoRoute(path: Routes.group, builder: (_, __) => GroupScreen(services: s)),
      GoRoute(path: Routes.pc, builder: (_, __) => PcScreen(library: s.library, premium: s.premium, server: s.server)),
      GoRoute(path: Routes.history, builder: (_, __) => HistoryScreen(services: s)),
      GoRoute(path: Routes.downloader, builder: (_, __) => DownloaderScreen(services: s)),
      GoRoute(path: Routes.converter, builder: (_, __) => ConverterScreen(services: s)),
      GoRoute(path: Routes.safebox, builder: (_, __) => SafeboxScreen(library: s.library, premium: s.premium)),
      GoRoute(path: Routes.status, builder: (_, __) => StatusScreen(services: s)),
      GoRoute(path: Routes.clean, builder: (_, __) => CleanScreen(services: s)),
      GoRoute(path: Routes.apps, builder: (_, __) => AppsScreen(services: s)),
      GoRoute(path: Routes.browser, builder: (_, __) => BrowserScreen(services: s)),
      GoRoute(path: Routes.settings, builder: (_, __) => SettingsScreen(services: s)),
      GoRoute(path: Routes.premium, builder: (_, __) => PremiumScreen(premium: s.premium)),
      GoRoute(path: Routes.privacy, builder: (_, __) => const PrivacyScreen()),
      GoRoute(
        path: '${Routes.category}/:kind',
        builder: (context, state) => CategoryScreen(services: s, kind: state.pathParameters['kind'] ?? 'received'),
      ),
    ],
  );
}
