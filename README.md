# GistDroid

Klien GitHub Gist untuk Android dengan UI Material 3 (Jetpack Compose). Simpan snippet kode ke **GitHub Gist** atau **secara lokal** di HP.

## Fitur

- **Gist**: lihat gist milikmu & yang diberi bintang, cari, buat, edit, hapus, beri bintang, bagikan tautan.
- **Snippet lokal**: disimpan di database Room di HP, bisa dipakai tanpa akun. Unggah ke Gist kapan saja (dan sinkronkan ulang setelah diedit).
- Simpan gist ke lokal (salinan offline).
- Satu snippet/gist bisa berisi banyak file.
- Syntax highlighting ringan di tampilan & editor, nomor baris, salin per file.
- Bagikan teks dari aplikasi lain → langsung jadi snippet baru.
- Material You (warna dinamis), tema terang/gelap.

## Login

Aplikasi memakai **Personal Access Token**. Buat token *classic* dengan scope `gist` di
<https://github.com/settings/tokens/new?scopes=gist&description=GistDroid>, lalu tempel di tab **Setelan**.
Token disimpan di penyimpanan privat aplikasi (backup dimatikan).

## Build

APK dibangun oleh GitHub Actions (`.github/workflows/build.yml`) setiap push ke `main`:

- Hasil APK ada di tab **Actions → run → Artifacts → GistDroid-apk**.
- Push tag `v*` (mis. `v1.0.0`) untuk membuat GitHub Release berisi APK.

APK release ditandatangani dengan keystore dari secret `KEYSTORE_BASE64` + `KEYSTORE_PASSWORD`
(format PKCS12, alias `gistdroid`). Tanpa secret itu, APK ditandatangani dengan debug key.

### Install ke HP via ADB

```bash
gh run download --name GistDroid-apk --dir /tmp/gistdroid
adb install -r /tmp/gistdroid/GistDroid-*.apk
```

## Stack

Kotlin · Jetpack Compose · Material 3 · Navigation Compose · Room · DataStore · OkHttp · kotlinx.serialization
