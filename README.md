# SPLauncher

Launcher Android bergaya iOS 27 Liquid Glass. Developer: SenzaProject (senzaproject98@gmail.com). Min Android 14 (API 34).
Dukung developer: https://t.me/senzaproject (tombol "Support me" di Pengaturan > Tentang).

## Build
    ./setup-toolchain.sh   # sekali per sesi: unduh bundletool (D8+aapt2+apksig) + android.jar 34 dari GitHub
    ./build.sh             # hasil: out/SP-Launcher.apk (tanda tangan: sp-release.p12)
    # toolchain di lokasi lain:  SP_TOOLCHAIN=/path/ke/toolchain ./build.sh

Catatan build: D8 lama di bundletool crash tanpa `javac -parameters` (sudah ada di build.sh).
JANGAN hilangkan `sp-release.p12`; keystore yang sama dibutuhkan agar APK baru bisa menimpa versi lama.
Naikkan VERSION_CODE/VERSION_NAME di version.properties tiap rilis. Uji logika murni: lihat `tools/tests/README.md`.

## Pembaruan UI (desain baru)
- **Pengaturan Launcher** mengikuti mockup baru: kartu header SPLauncher, grup *Tampilan Utama / Ikon & Widget / Efek Liquid Glass*,
  grup *Personalisasi* (Tema, Wallpaper, Animasi Transisi, Tata Letak Ikon, Laci Aplikasi), grup *Fitur Lainnya*
  (Notifikasi, Gestur, Kunci Layar, Bantuan & Umpan Balik). "Tampilan Utama" membuka panel **Opsi Tampilan Launcher**
  (Layout Ikon, Ukuran Ikon 5 tingkat, Efek Liquid Glass, Animasi Transisi, Tata Letak Widget, Laci Aplikasi).
  Semua opsi lama tetap ada (Pengaturan Lanjutan, Gestur, Kunci Layar, dll).
- **Wallpaper bawaan** diganti 6 gambar Liquid Glass (`res/drawable-nodpi/wp_1..6.jpg`).
- **Animasi Transisi**: Liquid Glass / Zoom / Pudar / Tanpa Animasi (membuka aplikasi, folder, menu, pencarian).
- **Laci Aplikasi**: geser ke atas di area Home membuka daftar semua aplikasi A-Z (bisa dimatikan, kembali ke App Library saja).
- **Tata Letak Widget**: widget baru ditaruh di atas (default) atau bawah halaman.
- **Tema Jadwal**: mode gelap otomatis pada jam yang diatur. **Folder Transparan** dapat dimatikan.

## v1.0.0 (Beta Test)
- **Versi**: VERSION_NAME `1.0.0 (Beta Test)`, VERSION_CODE 4 (dinaikkan agar bisa menimpa build 3).
- **Perbaikan blur**: slider "Intensitas Blur" sebelumnya tidak punya posisi "tanpa blur" (0% tetap buram) dan rentang
  efeknya sempit. Kini 0% = tanpa blur, 1-100% = bitmap makin kecil + radius makin besar; pengecilan bertahap
  (tanpa aliasing); perhitungan blur pindah ke thread latar (`Glass.prepare` / `Glass.commit`) sehingga UI tidak tersendat.
- **Mode Kaca** (Pengaturan > Liquid Glass): Blur (penuh), **Kaca Cair** (ringan: tanpa sampling backdrop, warna dasar dari
  rata-rata wallpaper + kilau), Padat. Menggantikan toggle "Kurangi Transparansi" (nilai lama dimigrasikan ke Padat).
- **Recents tumpukan kartu**: Pengaturan > Pengalih Aplikasi > Gaya Kartu (Berjajar / Tumpukan, default Tumpukan).
  Kartu saling menimpa, yang di tengah paling besar dan paling depan, geser berhenti = otomatis menempel ke tengah.

- **Zona atas gaya iOS 27**: tarik dari sudut kiri atas = Pusat Notifikasi, tengah = Cari, sudut kanan atas = Control Center
  (Pengaturan > Gestur > "Zona Atas Gaya iOS 27", default aktif; matikan untuk kembali ke pembagian lama kiri/tengah = Notifikasi).

- **Ikon bawaan iOS 27**: 15 peran aplikasi sistem (Telepon, Pesan, Kamera, Pengaturan, Jam, Kalkulator, Galeri, Mail, Catatan,
  File, Kontak, Kompas, Cuaca, Perekam Suara, Musik) kini memakai sprite PNG dari lembar ikon iOS 27
  (`res/drawable-nodpi/ios_*.png`, 192px). Kalender dan Unduhan tetap digambar glyph (tanggal harus dinamis).
  Catatan: ini gambar bergaya Apple; hati-hati bila APK dipublikasikan luas (potensi klaim hak cipta/merek).

- **UI Pengaturan baru** (mengikuti mockup): halaman utama dengan kolom cari + kartu header + 7 kategori (Personalisasi, Layar & Tampilan,
  Aplikasi, Gestur, Notifikasi, Keamanan & Privasi, Tentang) yang masing-masing membuka halaman sendiri. Personalisasi memuat kartu Tema,
  kartu Gaya Ikon (iOS 27 / Klasik / Minimalis), Efek Liquid Glass (toggle + mode + kekuatan), Wallpaper, dan "Atur Layar Beranda"
  (Widget & Tata Letak: 4x6 / 5x6 / 6x7). Semua opsi lama tetap ada, hanya dipindah ke halaman yang sesuai.

## v1.2.0
- **Pengalih Aplikasi iOS 27** (`AppSwitcher`, `Recents`, `SPAccessibilityService`): kartu aplikasi berjajar horizontal di atas wallpaper
  yang diburamkan, ikon + nama di atas kartu, ketuk = buka, geser kartu ke atas = tutup, tombol "Hapus Semua".
  Dibuka dari: geser ke atas pada dock, ketuk dua kali di Home (opsional), atau otomatis menggantikan layar Recents bawaan
  (butuh layanan Aksesibilitas; Recents bawaan sempat terlihat sebentar lalu dialihkan). Sumber daftar: Aksesibilitas
  (urutan jendela depan) atau Akses Penggunaan (cadangan). Pratinjau jendela memakai `takeScreenshot` Aksesibilitas, disimpan
  lokal di cache, dapat dimatikan/dihapus di Pengaturan.
  Batas Android: aplikasi launcher biasa tidak bisa menjadi penyedia Recents sistem (itu komponen milik OEM), jadi cara
  kerjanya adalah mengalihkan, bukan menggantikan langsung.
- **Ketuk dua kali di Home**: Kunci layar (via Aksesibilitas `GLOBAL_ACTION_LOCK_SCREEN`), Cari, atau Pengalih Aplikasi.
  "Double tap to wake" (membangunkan layar) adalah fitur firmware perangkat, bukan launcher.
- **Kustomisasi baru**: bentuk ikon (squircle/bulat/kotak), ukuran teks label, jumlah baris, titik halaman, sembunyikan bilah
  status, kunci susunan Home, redupkan wallpaper, kelengkungan & kepekatan dock, nonaktifkan tarik-ke-bawah untuk pencarian,
  serta opsi Pengalih Aplikasi (pratinjau, nama, hapus semua, hentikan proses, ukuran kartu).
- **Perbaikan build**: `build.sh` dan workflow tidak lagi bergantung pada bit executable (`bash build.sh`).

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
  SPLauncher dipasang sebagai aplikasi sistem).
- **Paket ikon** (`IconPacks`): gaya iOS 27 bawaan (gambar sendiri, bukan aset Apple), "Ikon asli", dan paket pihak ketiga
  format ADW/Nova (`appfilter.xml`). Pilih di Pengaturan > Tampilan > Paket Ikon.
- **Ikon aplikasi** baru (rumah + "27"), adaptif + monokrom.
- **Perbaikan**: panel folder/kategori yang tampak setengah berwarna (`Glass.locate` memakai posisi layout, bukan posisi
  yang sedang dianimasikan; backdrop memakai shader CLAMP). Label ikon dipotong rapi (`fitLabel`). Proxy seret folder memakai
  offset kaca yang benar.
- Teks disclaimer dihapus; diganti tombol **Support me** (Telegram).

## Belum ada
backup/restore susunan Home, sembunyikan aplikasi, Smart Stack, drag antar-folder, konsep layar kunci.

## v1.0.1 (Beta Test)
- **Recents (Pengalih Aplikasi)**: Dock tidak lagi tampil di atas Recents (Dock punya elevation 8dp; Pengalih Aplikasi kini dinaikkan di atasnya).
  Judul kartu (ikon + nama) kini sejajar satu baris, kartu samping digelapkan agar kartu tengah menonjol.
  Saat dialihkan dari Recents bawaan, Pengalih Aplikasi dipasang langsung (tanpa memudar) sehingga Home tidak berkedip.
  Pratinjau lebih segar (ambil ulang tiap 2,5 detik, sebelumnya 6 detik).
- **Layar Nol**: latar kini opak (wallpaper diburamkan), menutupi Dock, titik halaman, dan ikon Home sehingga tidak tembus lagi di balik widget.

## v1.0.2 (Beta Test)
- **Bug Dock/overlay**: lapisan overlay kini permanen di atas Dock (elevation 40dp). Sebelumnya Dock tidak ikut redup saat folder dibuka dan bisa menimpa panel.
- **Folder**: wallpaper, ikon Home, dan Dock di belakang panel folder kini diburamkan (RenderEffect) sehingga ikon tidak tembus jelas di balik kaca.
  **Ukuran folder bebas**: seret pegangan di pojok kanan bawah panel; jumlah kolom menyesuaikan lebar, ukuran tersimpan per folder.
  Tekan lama ikon folder > "Ukuran Folder: Otomatis" untuk mengembalikan.
- **Layar Nol dapat dikustomisasi** (tombol "Ubah" atau tekan lama judul): tambah widget (bawaan atau dari aplikasi lain lewat galeri widget),
  hapus, urutkan (panah), setengah/penuh lebar (dua widget bersebelahan), tinggi bebas (seret garis putih di bawah widget),
  ganti/sembunyikan judul, atur peredam latar 0-90%. Judul kini putih agar terbaca di latar gelap. Tombol Kembali keluar dari mode Ubah dulu.
- **Gaya ikon Kaca**: lebih jelas (sebelumnya terlalu pudar).
- **Recents**: kartu bertumpuk baru (kartu tengah penuh, kartu samping mengintip ~25% dan digelapkan, judul hanya di kartu tengah).
  Pratinjau isi aplikasi lebih andal: jendela sistem/keyboard tidak lagi membatalkan pengambilan, pemicu bertambah
  (gulir, ketuk, ketik), resolusi lebih tinggi. Layanan Aksesibilitas menambah jenis event: setelah update, matikan lalu hidupkan lagi layanannya.
