import 'package:flutter/material.dart';

export 'package:share_ios/library.dart' show formatBytes;

const cyan1 = Color(0xFF28D4FF);
const cyan2 = Color(0xFF16C9F6);
const cyan3 = Color(0xFF00C6F8);
const cyan = cyan3;
const navy = Color(0xFF002B3A);
const cyanInk = Color(0xFF007FA6);
const page = Color(0xFFF4F7FA);

const catMusic = Color(0xFFFF5A5F);
const catApps = Color(0xFF3D8BFF);
const catVideo = Color(0xFF8B5CF6);
const catPhoto = Color(0xFF22C55E);
const catDocs = Color(0xFFF59E0B);
const catPdf = Color(0xFFEF4444);
const catSafe = Color(0xFF10B981);
const catDownload = Color(0xFF6366F1);
const catPink = Color(0xFFEC4899);
const catCyan = Color(0xFF06B6D4);

const brandGradient = LinearGradient(colors: [cyan1, cyan2, cyan3]);
const brandGradientV = LinearGradient(
  begin: Alignment.topCenter,
  end: Alignment.bottomCenter,
  colors: [cyan1, cyan3],
);

ThemeData shareTheme() {
  final scheme = ColorScheme.light(
    primary: cyanInk,
    onPrimary: Colors.white,
    primaryContainer: const Color(0xFFD5F5FF),
    onPrimaryContainer: const Color(0xFF00384D),
    secondary: cyan2,
    onSecondary: navy,
    tertiary: cyan3,
    surface: Colors.white,
    onSurface: const Color(0xFF0B2530),
    onSurfaceVariant: const Color(0xFF5B7380),
    outline: const Color(0xFFCAD6DE),
    outlineVariant: const Color(0xFFE3EAF0),
  );
  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: page,
    appBarTheme: const AppBarTheme(
      backgroundColor: page,
      foregroundColor: navy,
      elevation: 0,
      centerTitle: false,
      titleTextStyle: TextStyle(color: navy, fontWeight: FontWeight.w800, fontSize: 20),
    ),
    navigationBarTheme: NavigationBarThemeData(
      backgroundColor: Colors.white,
      indicatorColor: const Color(0xFFD5F5FF),
      labelTextStyle: WidgetStateProperty.all(
        const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
      ),
    ),
    cardTheme: CardThemeData(
      color: Colors.white,
      elevation: 0,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
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
          gradient: brandGradient,
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

