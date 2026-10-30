import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:share_ios/navigation/app_router.dart';
import 'package:share_ios/navigation/routes.dart';
import 'package:share_ios/theme.dart';
import 'package:share_ios/widgets/components.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key, required this.services});
  final AppServices services;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: ListenableBuilder(
        listenable: services.prefs,
        builder: (context, _) {
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              SectionCard(
                padding: 4,
                child: Column(
                  children: [
                    MenuRow(
                      icon: Icons.phone_iphone,
                      title: 'Device name',
                      subtitle: services.prefs.deviceName,
                      onTap: () async {
                        final ctrl = TextEditingController(text: services.prefs.deviceName);
                        final name = await showDialog<String>(
                          context: context,
                          builder: (ctx) => AlertDialog(
                            title: const Text('Device name'),
                            content: TextField(controller: ctrl, maxLength: 24),
                            actions: [
                              TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
                              TextButton(onPressed: () => Navigator.pop(ctx, ctrl.text), child: const Text('Save')),
                            ],
                          ),
                        );
                        if (name != null) await services.prefs.setName(name);
                      },
                    ),
                    MenuRow(icon: Icons.workspace_premium, title: 'Premium', onTap: () => context.push(Routes.premium)),
                    MenuRow(icon: Icons.privacy_tip, title: 'Privacy', onTap: () => context.push(Routes.privacy)),
                    MenuRow(
                      icon: Icons.brightness_6,
                      title: 'Theme',
                      subtitle: const ['System', 'Light', 'Dark'][services.prefs.themeMode.clamp(0, 2)],
                      onTap: () async {
                        final mode = await showDialog<int>(
                          context: context,
                          builder: (ctx) => SimpleDialog(
                            title: const Text('Theme'),
                            children: [
                              for (final e in [(0, 'System'), (1, 'Light'), (2, 'Dark')])
                                SimpleDialogOption(
                                  onPressed: () => Navigator.pop(ctx, e.$1),
                                  child: Text(e.$2),
                                ),
                            ],
                          ),
                        );
                        if (mode != null) await services.prefs.setThemeMode(mode);
                      },
                    ),
                  ],
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}
