# SP Launcher

Launcher Android bergaya iOS 27 Liquid Glass. Developer: SenzaProject (senzaproject98@gmail.com). Min Android 14 (API 34).

## Build
    ./setup-toolchain.sh   # sekali per sesi: unduh bundletool (D8+aapt2+apksig) + android.jar 34 dari GitHub
    ./build.sh             # hasil: out/SP-Launcher.apk (tanda tangan: sp-release.p12)

Catatan build: D8 lama di bundletool crash tanpa `javac -parameters` (sudah ada di build.sh).
JANGAN hilangkan `sp-release.p12`; keystore yang sama dibutuhkan agar APK baru bisa menimpa versi lama.
Naikkan VERSION_CODE/VERSION_NAME di version.properties tiap rilis.

## Status fase 1 (v1.0.0)
Home (grid, halaman, dock), folder, mode edit + drag-drop, App Library + pencarian, Liquid Glass (blur/tint/rim),
4 gaya ikon, wallpaper preset/galeri, Settings + Tentang.

## Fase berikutnya (belum dibuat)
Widget Android (AppWidgetHost), Control Center (senter, volume, shortcut Settings), Notification Center
(NotificationListenerService), backup/restore layout, icon pack, hide apps, badge notifikasi, Smart Stack,
drag antar-folder, lock screen konsep.
