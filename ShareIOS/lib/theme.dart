import 'package:flutter/material.dart';

const cyan = Color(0xFF00C6F8);
const cyanInk = Color(0xFF007FA6);
const navy = Color(0xFF002B3A);
const page = Color(0xFFF4F7FA);

ThemeData shareTheme() {
  final base = ThemeData(
    useMaterial3: true,
    colorScheme: ColorScheme.fromSeed(
      seedColor: cyan,
      primary: cyanInk,
      surface: Colors.white,
    ),
    scaffoldBackgroundColor: page,
  );
  return base.copyWith(
    appBarTheme: const AppBarTheme(
      backgroundColor: page,
      foregroundColor: navy,
      elevation: 0,
      centerTitle: true,
    ),
    navigationBarTheme: const NavigationBarThemeData(
      backgroundColor: Colors.white,
      indicatorColor: Color(0xFFD5F5FF),
      labelTextStyle: WidgetStatePropertyAll(TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
    ),
  );
}

class ShareButton extends StatelessWidget {
  const ShareButton({super.key, required this.label, required this.onPressed});
  final String label;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 52,
      child: DecoratedBox(
        decoration: BoxDecoration(
          gradient: const LinearGradient(colors: [Color(0xFF28D4FF), Color(0xFF00C6F8)]),
          borderRadius: BorderRadius.circular(16),
        ),
        child: ElevatedButton(
          onPressed: onPressed,
          style: ElevatedButton.styleFrom(
            backgroundColor: Colors.transparent,
            shadowColor: Colors.transparent,
            foregroundColor: navy,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          ),
          child: Text(label, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
        ),
      ),
    );
  }
}
