import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

class AppPrefs extends ChangeNotifier {
  String deviceName = 'iPhone';
  int avatar = 0;
  int themeMode = 0; // 0 system, 1 light, 2 dark

  Future<void> load() async {
    final p = await SharedPreferences.getInstance();
    deviceName = p.getString('device_name') ?? 'iPhone';
    avatar = p.getInt('avatar') ?? 0;
    themeMode = p.getInt('theme_mode') ?? 0;
    notifyListeners();
  }

  Future<void> setName(String name) async {
    deviceName = name.trim().isEmpty ? deviceName : name.trim();
    final p = await SharedPreferences.getInstance();
    await p.setString('device_name', deviceName);
    notifyListeners();
  }

  Future<void> cycleAvatar() async {
    avatar = (avatar + 1) % 12;
    final p = await SharedPreferences.getInstance();
    await p.setInt('avatar', avatar);
    notifyListeners();
  }

  Future<void> setThemeMode(int mode) async {
    themeMode = mode;
    final p = await SharedPreferences.getInstance();
    await p.setInt('theme_mode', mode);
    notifyListeners();
  }
}
