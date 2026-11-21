# GistDroid

A GitHub Gist client for Android built with Jetpack Compose and Material 3. Save code snippets to **GitHub Gist** or **locally** on your device.

## Features

- **Gists**: browse your own and starred gists, search, create, edit, delete, star, share links.
- **Local snippets**: stored in a Room database on the device, usable without an account. Upload to Gist anytime (and re-sync after editing).
- Save any gist as a local (offline) copy.
- Multiple files per snippet/gist.
- Lightweight syntax highlighting in the viewer and editor, line numbers, copy per file.
- Share text from any app → it opens as a new snippet.
- Material You dynamic color, light/dark theme.

## Sign-in & token security

GistDroid uses a **Personal Access Token**. Create a *classic* token with the `gist` scope at
<https://github.com/settings/tokens/new?scopes=gist&description=GistDroid> (or a fine-grained token with
the *Gists: read & write* permission) and paste it in **Settings**.

How the token is protected:

- Verified against the GitHub API before it is saved; classic tokens without the `gist` scope are rejected.
- Encrypted with AES-256-GCM using a non-exportable key in the **Android Keystore**, then stored in app-private DataStore.
- Backups and device-to-device transfer of app data are disabled.
- Only sent to `api.github.com` over HTTPS; cleartext traffic is disabled and the header is never sent to other hosts.
- The token field blocks screenshots/screen recording (`FLAG_SECURE`), disables IME learning, is never kept in saved instance state, and the clipboard is cleared after a successful paste.
- Signing out deletes the token and the Keystore key.

## Build

APKs are built by GitHub Actions (`.github/workflows/build.yml`) on every push to `main`:

- Download the APK from **Actions → run → Artifacts → GistDroid-apk**.
- Push a `v*` tag (e.g. `v1.0.0`) to publish a GitHub Release with the APK.

Release APKs are signed with the keystore in the `KEYSTORE_BASE64` + `KEYSTORE_PASSWORD` secrets
(PKCS12, alias `gistdroid`). Without those secrets the debug key is used.

### Install via ADB

```bash
gh run download --name GistDroid-apk --dir /tmp/gistdroid
adb install -r /tmp/gistdroid/GistDroid-*.apk
```

## Stack

Kotlin · Jetpack Compose · Material 3 · Navigation Compose · Room · DataStore · OkHttp · kotlinx.serialization
