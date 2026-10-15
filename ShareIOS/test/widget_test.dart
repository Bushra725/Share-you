import 'package:flutter_test/flutter_test.dart';
import 'package:share_ios/library.dart';
import 'package:share_ios/premium_store.dart';

void main() {
  test('formats file sizes', () {
    expect(formatBytes(512), '512 B');
    expect(formatBytes(2048), '2.0 KB');
  });

  test('free file limit is 100 MB', () {
    expect(PremiumStore.freeFileLimit, 100 * 1024 * 1024);
  });
}
