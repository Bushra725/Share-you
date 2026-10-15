import 'dart:io';

import 'package:mime/mime.dart';
import 'package:share_ios/library.dart';
import 'package:share_ios/premium_store.dart';

class PcShare {
  HttpServer? _server;
  String? url;
  String? error;

  Future<void> start(FileLibrary library, PremiumStore premium) async {
    await stop();
    final key = DateTime.now().millisecondsSinceEpoch.toRadixString(36);
    try {
      _server = await HttpServer.bind(InternetAddress.anyIPv4, 8888);
    } on SocketException catch (e) {
      error = 'Could not open the PC page ($e).';
      return;
    }
    final ip = await _lanIp();
    url = 'http://$ip:8888/?k=$key';
    error = null;
    _server!.listen((request) => _handle(request, key, library, premium));
  }

  Future<void> stop() async {
    await _server?.close(force: true);
    _server = null;
    url = null;
  }

  Future<String> _lanIp() async {
    for (final interface in await NetworkInterface.list(type: InternetAddressType.IPv4)) {
      for (final addr in interface.addresses) {
        if (!addr.isLoopback) return addr.address;
      }
    }
    return '127.0.0.1';
  }

  Future<void> _handle(HttpRequest request, String key, FileLibrary library, PremiumStore premium) async {
    if (request.uri.queryParameters['k'] != key) {
      request.response.statusCode = HttpStatus.forbidden;
      request.response.write('This page is private.');
      await request.response.close();
      return;
    }
    try {
      if (request.method == 'GET' && request.uri.path == '/') {
        request.response.headers.contentType = ContentType.html;
        request.response.write(_page(library, key));
      } else if (request.method == 'GET' && request.uri.path.startsWith('/file/')) {
        final index = int.tryParse(request.uri.path.substring(6));
        final files = library.received;
        if (index == null || index < 0 || index >= files.length) {
          request.response.statusCode = HttpStatus.notFound;
        } else {
          final file = File(files[index].path);
          request.response.headers.set('Content-Disposition', 'attachment; filename="${files[index].name}"');
          await request.response.addStream(file.openRead());
        }
      } else if (request.method == 'POST' && request.uri.path == '/upload') {
        await _upload(request, library, premium);
      } else {
        request.response.statusCode = HttpStatus.notFound;
      }
    } catch (_) {
      request.response.statusCode = HttpStatus.internalServerError;
      request.response.write('Failed');
    }
    await request.response.close();
  }

  Future<void> _upload(HttpRequest request, FileLibrary library, PremiumStore premium) async {
    final boundary = request.headers.contentType?.parameters['boundary'];
    if (boundary == null) {
      request.response.statusCode = HttpStatus.badRequest;
      request.response.write('Missing file');
      return;
    }
    final transformer = MimeMultipartTransformer(boundary);
    await for (final part in request.cast<List<int>>().transform(transformer)) {
      final header = part.headers['content-disposition'] ?? '';
      final match = RegExp('filename="([^"]*)"').firstMatch(header);
      final name = (match?.group(1) ?? '').trim();
      if (name.isEmpty) continue;
      final bytes = await part.fold<List<int>>(<int>[], (a, b) => a..addAll(b));
      if (premium.blocks(bytes.length)) {
        request.response.statusCode = HttpStatus.forbidden;
        request.response.write('This file is over 100 MB. Premium is required.');
        return;
      }
      await library.importBytes(name: name, bytes: bytes, vault: false);
      request.response.statusCode = HttpStatus.ok;
      request.response.write('Saved');
      return;
    }
    request.response.statusCode = HttpStatus.badRequest;
    request.response.write('No file');
  }

  String _page(FileLibrary library, String key) {
    final rows = library.received.asMap().entries.map((e) {
      return '<li><a href="/file/${e.key}?k=$key">${_esc(e.value.name)}</a> · ${formatBytes(e.value.size)}</li>';
    }).join();
    return '''
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Share</title>
<style>body{font-family:-apple-system,sans-serif;margin:24px;color:#002B3A}button,a{color:#007FA6}</style>
</head><body>
<h1>Share</h1>
<p>This page stays on your Wi-Fi. It is not on the internet.</p>
<form method="post" action="/upload?k=$key" enctype="multipart/form-data">
<input type="file" name="file" required> <button>Send to iPhone</button>
</form>
<h2>On this iPhone</h2>
<ul>${rows.isEmpty ? '<li>No files yet</li>' : rows}</ul>
</body></html>
''';
  }

  String _esc(String s) => s.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('"', '&quot;');
}
