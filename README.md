# SP Launcher

Launcher Android bergaya iOS 27 Liquid Glass. Developer: SenzaProject (senzaproject98@gmail.com). Min Android 14 (API 34).
Dukung developer: https://t.me/senzaproject (tombol "Support me" di Pengaturan > Tentang).

## Build
    ./setup-toolchain.sh   # sekali per sesi: unduh bundletool (D8+aapt2+apksig) + android.jar 34 dari GitHub
    ./build.sh             # hasil: out/SP-Launcher.apk (tanda tangan: sp-release.p12)
    # toolchain di lokasi lain:  SP_TOOLCHAIN=/path/ke/toolchain ./build.sh

Catatan build: D8 lama di bundletool crash tanpa `javac -parameters` (sudah ada di build.sh).
JANGAN hilangkan `sp-release.p12`; keystore yang sama dibutuhkan agar APK baru bisa menimpa versi lama.
Naikkan VERSION_CODE/VERSION_NAME di version.properties tiap rilis. Uji logika murni: lihat `tools/tests/README.md`.

## v1.1.0
- **Widget**: widget bawaan (Jam Analog, Jam Digital, Tanggal, Kalender, Baterai, Pencarian) dengan pratinjau langsung
  di galeri widget, plus widget dari aplikasi lain lewat AppWidgetHost. Ukuran dapat diubah dari menu tekan-lama.
  Tambah lewat tombol "+" (mode edit) atau tekan-lama area kosong > Tambah Widget. Model: `LayoutStore.Item` tipe WIDGET
  (span + wid/provider), penempatan oleh `GridPacker` (ikon 1x1 mengisi sel kosong), seret-lepas di `DragController`.
- **Pusat Notifikasi** (`NotificationCenter` + `NotificationService`): tarik ke bawah dari bagian atas Home sisi kiri/tengah.
  Butuh "Akses notifikasi". Badge merah pada ikon aplikasi/folder (dapat dimatikan).
- **Control Center** (`ControlCenter`): tarik ke bawah dari bagian atas Home sisi kanan. Senter, kecerahan (izin "Ubah setelan
  sistem"), volume, kunci rotasi, Fokus (izin Jangan Ganggu), kontrol media, pintasan. Batas Android: aplikasi biasa tidak bisa
  mengubah Wi-Fi/Bluetooth/data seluler/mode pesawat langsung, jadi ubin itu membuka panel sistem (toggle langsung hanya bila
  SP Launcher dipasang sebagai aplikasi sistem).
- **Paket ikon** (`IconPacks`): gaya iOS 27 bawaan (gambar sendiri, bukan aset Apple), "Ikon asli", dan paket pihak ketiga
  format ADW/Nova (`appfilter.xml`). Pilih di Pengaturan > Tampilan > Paket Ikon.
- **Ikon aplikasi** baru (rumah + "27"), adaptif + monokrom.
- **Perbaikan**: panel folder/kategori yang tampak setengah berwarna (`Glass.locate` memakai posisi layout, bukan posisi
  yang sedang dianimasikan; backdrop memakai shader CLAMP). Label ikon dipotong rapi (`fitLabel`). Proxy seret folder memakai
  offset kaca yang benar.
- Teks disclaimer dihapus; diganti tombol **Support me** (Telegram).

## Belum ada
backup/restore susunan Home, sembunyikan aplikasi, Smart Stack, drag antar-folder, konsep layar kunci.
