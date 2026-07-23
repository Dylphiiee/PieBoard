# PieBoard

Soundboard Android dengan floating button, bertema glassmorphism (hitam `#0F1014` + merah aksen `#FE0000`), sesuai PRD.

## Struktur

```
app/src/main/java/com/dylphiiee/pieboard/
├── data/        # Room entity, DAO, database, repository (copy file ke internal storage)
├── util/        # Prefs (SharedPreferences) & SoundPlayer (MediaPlayer, stream MEDIA)
├── ui/          # MainActivity, ViewModel, adapter, dialog tambah/edit sound
└── service/     # FloatingService (overlay draggable button + panel sound)
```

Fitur yang sudah diimplementasikan sesuai PRD:
- CRUD sound (nama + file audio, disalin ke `filesDir`), list dengan pagination 6 item/halaman.
- Slider volume master (persist), memutar audio lewat stream `USAGE_MEDIA`.
- Floating button: drag bebas, tap untuk buka panel daftar sound (overlay window, bukan popup activity), toggle ukuran, posisi & status persist.
- Foreground service dengan notifikasi prioritas minim.
- Permission flow: overlay (`SYSTEM_ALERT_WINDOW`), audio read, notifikasi (Android 13+).
- UI Material 3 + gaya glassmorphism (card translucent, border tipis, sheen atas, glow merah di background).

## Build lokal (Android Studio)

1. Buka folder ini di Android Studio (Studio akan menawarkan generate Gradle wrapper otomatis saat sync — terima saja, atau jalankan `gradle wrapper` manual kalau punya Gradle terinstall).
2. Sync project, lalu Run pada device/emulator.

Project ini **belum menyertakan `gradlew`/`gradle-wrapper.jar`** karena file biner tidak bisa dibuat dari sandbox pembuatan ini. Android Studio otomatis membuatkannya saat sync pertama, atau jalankan:

```bash
gradle wrapper --gradle-version 8.7
```

## Update aplikasi (tanpa uninstall)

Mulai versi 2.0, project ini menyertakan `app/debug.keystore` yang **sengaja di-commit** ke repo, dan `build.gradle.kts` dikonfigurasi supaya build debug (baik lokal maupun lewat GitHub Actions) selalu pakai keystore yang sama ini. Sebelumnya, tiap build APK di GitHub Actions ditandatangani pakai debug key acak yang beda-beda setiap runner, jadi APK versi baru dianggap "aplikasi lain" oleh Android dan mengharuskan uninstall APK lama dulu.

Dengan keystore yang konsisten + versionCode yang selalu dinaikkan tiap rilis, sekarang tinggal install APK baru di atas yang lama — otomatis ter-update tanpa perlu uninstall/hapus data.

**Penting:** kalau mau naikkan versi lagi ke depannya, cukup naikkan `versionCode` (misal 2 → 3) di `app/build.gradle.kts`, jangan hapus/ganti `debug.keystore`-nya.

## Build APK otomatis lewat GitHub Actions

Workflow `.github/workflows/build-apk.yml` sudah disediakan:

1. Push project ini ke repo GitHub kamu.
2. Workflow otomatis jalan setiap push ke `main`/`master`, atau bisa dipicu manual lewat tab **Actions → Build PieBoard APK → Run workflow**.
3. Setelah selesai, unduh APK dari bagian **Artifacts** di halaman run tersebut (nama artifact: `pieboard-debug-apk`).

Workflow ini men-build **debug APK** (sudah signed otomatis dengan debug keystore bawaan Android, langsung bisa di-install untuk testing). Kalau nanti butuh **release APK** yang signed dengan keystore sendiri, tambahkan job baru + secrets keystore (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, dst) — bisa saya bantu tambahkan kalau diperlukan.

## Catatan

- Min SDK 24 (Android 7.0), Target SDK 34.
- Efek glassmorphism dibuat dengan translucent surface + border tipis + gradient glow di background (bukan real backdrop-blur, supaya tetap kompatibel ke minSdk 24 tanpa library blur tambahan).
