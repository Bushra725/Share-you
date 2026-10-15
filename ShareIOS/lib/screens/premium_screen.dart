import 'package:flutter/material.dart';
import 'package:share_ios/premium_store.dart';
import 'package:share_ios/theme.dart';
import 'package:url_launcher/url_launcher.dart';

class PremiumScreen extends StatefulWidget {
  const PremiumScreen({super.key, required this.premium});
  final PremiumStore premium;

  @override
  State<PremiumScreen> createState() => _PremiumScreenState();
}

class _PremiumScreenState extends State<PremiumScreen> {
  int selected = 1;

  Future<void> _open(String url) async {
    await launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
  }

  @override
  Widget build(BuildContext context) {
    final store = widget.premium;
    final products = store.products;
    if (selected >= products.length) selected = products.isEmpty ? 0 : products.length - 1;
    return Scaffold(
      appBar: AppBar(title: const Text('Premium')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              gradient: const LinearGradient(colors: [Color(0xFF28D4FF), Color(0xFF00C6F8)]),
              borderRadius: BorderRadius.circular(28),
            ),
            child: const Column(
              children: [
                Icon(Icons.workspace_premium, size: 48, color: navy),
                SizedBox(height: 8),
                Text('Share Premium', style: TextStyle(color: navy, fontSize: 24, fontWeight: FontWeight.w800)),
                Text('Any file size · No ads · Safebox', textAlign: TextAlign.center, style: TextStyle(color: navy)),
              ],
            ),
          ),
          const SizedBox(height: 16),
          const _Feature(icon: Icons.all_inclusive, text: 'Send files of any size. Free includes files up to 100 MB.'),
          const _Feature(icon: Icons.lock, text: 'Safebox for private photos, videos, and files on this iPhone.'),
          const _Feature(icon: Icons.favorite, text: 'Supports the next Share updates.'),
          const SizedBox(height: 12),
          if (store.isPremium)
            const ListTile(
              leading: Icon(Icons.check_circle, color: cyanInk),
              title: Text("You're Premium"),
            )
          else ...[
            if (store.loading) const Center(child: Padding(padding: EdgeInsets.all(12), child: CircularProgressIndicator())),
            RadioGroup<int>(
              groupValue: selected,
              onChanged: (value) => setState(() => selected = value ?? 0),
              child: Column(
                children: [
                  for (var i = 0; i < products.length; i++)
                    Card(
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(18),
                        side: BorderSide(color: selected == i ? cyan : const Color(0xFFE3EAF0), width: 2),
                      ),
                      child: RadioListTile<int>(
                        value: i,
                        title: Text(store.titleFor(products[i].id)),
                        subtitle: Text('${store.periodFor(products[i].id)}\n${products[i].description}'),
                        secondary: Text(products[i].price, style: const TextStyle(fontWeight: FontWeight.w800)),
                      ),
                    ),
                ],
              ),
            ),
            if (store.message != null)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(store.message!, style: const TextStyle(color: Colors.red)),
              ),
            const SizedBox(height: 12),
            ShareButton(
              label: 'Continue',
              onPressed: products.isEmpty ? null : () => store.buy(products[selected]),
            ),
            TextButton(onPressed: store.restore, child: const Text('Restore purchases')),
            const SizedBox(height: 8),
            const Text(
              'Payment is charged to your Apple ID. A subscription renews automatically unless you cancel at least 24 hours before the end of the current period. Apple charges the renewal within 24 hours of the end of the period. Manage or cancel anytime in Settings → Apple ID → Subscriptions.',
              style: TextStyle(fontSize: 12, color: Color(0xFF5B7380), height: 1.35),
            ),
            Wrap(
              alignment: WrapAlignment.center,
              children: [
                TextButton(onPressed: () => _open(PremiumStore.privacyPolicyUrl), child: const Text('Privacy Policy')),
                TextButton(onPressed: () => _open(PremiumStore.termsUrl), child: const Text('Terms of Use')),
                TextButton(onPressed: () => _open(PremiumStore.manageUrl), child: const Text('Manage subscription')),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

class _Feature extends StatelessWidget {
  const _Feature({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Icon(icon, color: cyanInk),
          const SizedBox(width: 12),
          Expanded(child: Text(text)),
        ],
      ),
    );
  }
}
