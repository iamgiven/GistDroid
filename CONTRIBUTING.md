# Contributing to GistDroid

Thanks for taking the time to help. Bug reports, ideas and pull requests are all welcome.

## Reporting bugs and ideas

Open an [issue](https://github.com/iamgiven/GistDroid/issues/new/choose) and pick the bug report or feature
request template. For security issues, follow [SECURITY.md](SECURITY.md) instead of opening a public issue.

## Building

Requirements: JDK 17 and the Android SDK (Android Studio sets both up).

```bash
./gradlew assembleDebug        # debug APK in app/build/outputs/apk/debug/
./gradlew installDebug         # build and install on a connected device
```

Every push and pull request is also built by the [CI workflow](.github/workflows/ci.yml); its APK is
available under **Actions → the run → Artifacts**. To install one with the GitHub CLI and ADB:

```bash
gh run download <run-id> --name GistDroid-apk --dir /tmp/gistdroid
adb install -r /tmp/gistdroid/GistDroid-*.apk
```

## Project layout

| Path | What it holds |
|---|---|
| `app/src/main/java/.../data` | GitHub API client, Room database, settings, backup |
| `app/src/main/java/.../ui` | Compose screens, grouped by feature (`gists`, `local`, `editor`, `viewer`, `settings`) |
| `app/src/main/java/.../ui/components` | Shared UI: cards, syntax highlighter, language logos |
| `app/src/main/java/.../ui/theme` | Theme, built-in color palettes |

## Pull requests

- Keep each pull request focused on one change, and describe what it does and why.
- Follow the existing code style ([Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)).
- UI text is in English.
- Make sure `./gradlew assembleDebug` succeeds and try the change on a device or emulator.
- For visible changes, add before and after screenshots.
- New third-party code or assets need a license compatible with GPL-3.0; list them in
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

By contributing, you agree that your contributions are licensed under the project's
[GPL-3.0-or-later](LICENSE) license.

## Releasing (maintainers)

1. Set `appVersion` in `app/build.gradle.kts` (the version code is derived from it).
2. Move the entries under **Unreleased** in [CHANGELOG.md](CHANGELOG.md) to a new `## [x.y.z] - YYYY-MM-DD`
   section.
3. Commit, then tag and push: `git tag vx.y.z && git push origin vx.y.z`.

The [release workflow](.github/workflows/release.yml) checks that the tag matches `appVersion`, builds the
signed APK and publishes a GitHub release with the changelog section, checksums and the signing
certificate fingerprint.
