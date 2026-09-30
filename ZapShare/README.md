# ZapShare — offline file transfer app (Android)

A SHAREit-style app built with **Kotlin + Jetpack Compose**, themed in `#28D4FF / #16C9F6 / #00C6F8`.
Everything runs on-device. **No paid APIs, no servers, no accounts.**

> The app uses an original name and icon ("ZapShare"). Publishing a clone under the SHAREit name or logo
> would get rejected (trademark), so rename it to anything you like in `res/values/strings.xml`
> and `applicationId` in `app/build.gradle.kts`.

---

## 1. Run it

1. Install **Android Studio** (Ladybug or newer), which includes the Android SDK.
2. `File → Open` → select the `ZapShare` folder. Let Gradle sync (it downloads the libraries).
3. Plug in a phone (USB debugging on) → **Run ▶**.
4. To test transfer you need **two real phones**. Emulators can't make Wi-Fi hotspots.

With Cursor: open the same folder in Cursor to edit, and keep Android Studio (or `./gradlew assembleDebug`) for building and running.
Build a release: `Build → Generate Signed App Bundle / APK`.

---

## 2. Features & how each one works

| Feature | How (all free / on-device) | Code |
|---|---|---|
| **Fast transfer without internet** | Receiver starts an Android *LocalOnlyHotspot* (a private Wi-Fi network, no router or data needed). Sender joins it and streams raw bytes over a TCP socket with 512 KB buffers and 2 MB socket buffers, a high-performance Wi-Fi lock and no encryption overhead. Typically 20–60 MB/s on 5 GHz phones. | `transfer/` |
| **QR code connect** | Receiver shows a QR (hotspot name, password, IP, port). Sender scans it with ZXing and joins automatically (`WifiNetworkSpecifier`). | `TransferModels.kt` (`HostInfo.toQr`), `TransferScreens.kt` |
| **Same Wi-Fi mode** | Uses mDNS/NSD discovery so nearby phones show up in a list. There's also a manual-IP option. | `Network.kt` (`NsdHelper`) |
| **Two-way transfer** | Once connected, both phones can "Send more" at any time. | `Peer.kt` |
| **Group share** | One phone hosts, others scan to join. The host relays every received file to all members. | `TransferManager.markReceived` |
| **Connect to PC** | Phone runs a tiny web server (NanoHTTPD). The PC opens `http://phone-ip:8888/?k=code` in any browser: drag-and-drop upload to the phone, download what the phone shares. It can also create a hotspot for the PC. | `pc/PcServer.kt`, `assets/pc/index.html` |
| **All file types** | Photos, videos, music, apps (APK) and documents, plus an **"Any file"** picker for everything else. Received files are sorted into `Download/ZapShare/<Photos\|Videos\|Music\|Apps\|Documents\|Files>`. | `SelectFilesScreen` |
| **Send apps** | Shares the installed APK. The receiver gets an **Install** button. | `MediaRepository.apps` |
| **Video downloader** (Facebook, Instagram, X/Twitter, TikTok, 1000+ sites) | Bundled open-source **yt-dlp** engine (`youtubedl-android`). Paste a link, or use *Share → ZapShare* from those apps. Offers MP4 or MP3. Update the engine from *Me → Update video downloader engine*. | `downloader/VideoDownloader.kt` |
| **WhatsApp status saver** | Reads WhatsApp's `.Statuses` folder, either through a one-time folder grant or with All files access. | `downloader/StatusRepository.kt` |
| **Music player** | Media3 ExoPlayer in a background `MediaSessionService`: lock-screen/notification controls, shuffle, repeat, global mini-player. | `player/PlaybackService.kt` |
| **Video player** | ExoPlayer with fullscreen/rotate, speed 0.5×–2×, and a folder grid. | `PlayerScreens.kt` |
| **File manager** | Categories, storage stats, full folder browser (copy/move/rename/delete/new folder), search, large-file cleaner, apps manager. | `FileScreens.kt` |
| **File preview** | Built-in viewers for images (pinch-zoom), video, audio, **PDF**, text/code, **ZIP contents** and **APK info**. Anything else goes to "Open with". | `PreviewScreen.kt` |
| **File converter** | Image JPG⇄PNG⇄WEBP + compress/resize, Images→PDF, PDF→Images, Text→PDF, and with **FFmpeg**: Video→MP3/M4A, Audio→WAV/OGG, Video→MP4, compress to 720p/480p, Video→GIF, MKV remux. | `converter/Converters.kt` |
| **Safebox** | PIN (+ optional fingerprint). Files are encrypted with AES-256-GCM in 1 MB chunks. The key is wrapped by the Android Keystore, and names are hidden. You can remove the originals from the gallery after adding. | `safebox/Vault.kt` |
| Extras | Transfer history, dark mode, avatars/device name, share-sheet integration, background progress notification. | |

---

## What's new in 1.1

- **New bottom bar**: icons are outlined black when off, filled brand-blue when on, and every tab gets an equal width so nothing spills off the screen.
- **Brand-blue icons** everywhere (tools, file browser, cleaner, converter, settings). Settings icons are bigger.
- **Music player close (✕)** on the mini player and the Now Playing screen: stops the music and removes the player.
- **Video player**: controls (time bar, fullscreen, settings) sit above the navigation bar; true fullscreen in landscape.
- **Downloader**: short "Download video" header with Facebook / Instagram / X / TikTok icons.
  The four icons in `res/drawable/ic_platform_*.xml` are **placeholders**. Replace them with the official icons from each
  company's brand-resources page, keeping the same file names.
- **WhatsApp status saver**: Open WhatsApp button, WhatsApp / WA Business switch, auto-refresh when you come back,
  Save and **Repost to WhatsApp** on each status.
- **Safebox**: "Add → Photos & videos" opens an in-app gallery. The selected items are encrypted into the Safebox and
  removed from the gallery (Android asks you to confirm the delete).
- **Languages**: Settings → Language offers English, Urdu, Hindi, Arabic, Spanish, French, Portuguese, Indonesian and Turkish (Android 8+).
  About 90 main strings are translated (navigation, home, tools, settings, premium, downloader). The remaining screens still use
  English text written in Kotlin. To translate more, select a string in Cursor or Android Studio → *Extract string resource*, then add it to each `values-xx/strings.xml`.
- **Premium** (Google Play Billing):
  - Free plan: files up to **100 MB each**. A larger file opens the Premium plan.
  - Free plan shows AdMob banner ads with a *Remove ads* link to Premium.
  - **Safebox is Premium-only**.
  - Premium banner on Home, Tools, Me and Settings. A paywall offers Monthly / Yearly / Lifetime plans and a *Restore purchases* button.

### Premium setup (Play Console)
1. Upload the app (internal testing track is enough) → *Monetize → Products*.
2. Create subscriptions `zapshare_premium_monthly` and `zapshare_premium_yearly` (one base plan each) and an in-app product `zapshare_premium_lifetime`.
3. Add your Gmail as a license tester. Test purchases are free.
4. The prices on the paywall come from Play automatically. The prices in `Premium.kt` are only shown before the store loads.
5. **Debug builds** have *Settings → Debug: unlock Premium* for testing without Play. It is hidden in release builds.

For production, verify purchases on a server with the Google Play Developer API. Local checks can be bypassed on rooted phones.

### Ads setup (AdMob)
- The manifest app ID and `Ads.BANNER_UNIT_ID` are **Google's public test IDs**. Replace both with your own from admob.google.com before release.
- For users in the EU/UK you must show a consent form (Google's free *User Messaging Platform* SDK) before loading ads.

---

## 3. Project map

```
app/src/main/java/com/zapshare/app/
├── ZapApp.kt / MainActivity.kt      app start, share-intent handling
├── transfer/                         ⚡ engine: hotspot, QR join, sockets, group relay, foreground service
├── pc/                               PC web share (NanoHTTPD)
├── downloader/                       yt-dlp downloader + WhatsApp statuses
├── player/                           background music service
├── converter/                        image / PDF / FFmpeg conversions
├── safebox/                          encrypted vault
├── premium/                          Play Billing + AdMob banner
├── data/                             MediaStore queries, prefs, history
├── util/                             file + permission helpers
└── ui/                               theme, components, navigation, screens
```

Wire protocol (`Protocol` in `TransferModels.kt`): `[type:1 byte][header length:4][JSON header][raw file bytes]`.
The simplicity is on purpose: no per-chunk overhead means maximum throughput.

---

## 4. Things to know before publishing

- **Google Play policies**
  - `MANAGE_EXTERNAL_STORAGE` (full file manager) must be declared in Play Console as a *file manager* use case. If you don't want that review, remove it from the manifest. The app falls back to MediaStore plus the system file picker.
  - **Downloaders:** Play rejects apps that download from **YouTube**. yt-dlp supports YouTube, so either keep the app outside Play, or block YouTube URLs in `VideoDownloader.download()` (a one-line check). Also add terms saying users may only download content they have rights to.
  - `REQUEST_INSTALL_PACKAGES` needs a declaration in Play Console ("file sharing").
- **APK size:** yt-dlp + FFmpeg add roughly 30–60 MB per CPU architecture. Uploading an **App Bundle (.aab)** lets Play deliver only the right architecture. If you don't need the downloader or FFmpeg conversions, delete the two `youtubedl-android` lines, `downloader/VideoDownloader.kt` and the FFmpeg part of the converter, and the app drops to about 10 MB.
- **Instagram/Facebook** often require login for some posts; public posts work. When a site changes, use "Update engine" (yt-dlp updates are free).
- **Split APK apps** (many Play Store apps): only the base APK is sent. For full support you can zip the splits as `.apks` and install them with `PackageInstaller` sessions.
- **Android 8–12:** Location must be ON for the hotspot. The app prompts for it.

## 5. Ideas for next steps in Cursor
- Resume interrupted transfers (send a byte offset in the header).
- Wi-Fi Direct as a second offline path for phones that refuse LocalOnlyHotspot.
- Optional TLS on the socket for public places.
- Duplicate-file finder in the cleaner (hash files of equal size).
- iOS / desktop companion (the PC web page already works on Mac & Linux).

Library credits: Jetpack Compose, Media3/ExoPlayer, Coil, ZXing, NanoHTTPD, yt-dlp / youtubedl-android, FFmpeg.
