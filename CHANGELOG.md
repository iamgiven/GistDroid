# Changelog

All notable changes to GistDroid are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.0.1] - 2026-11-24

### Fixed

- Removing a file from the editor's file list now asks for confirmation.
- The file name and line count stay on one line in the file editor's top bar.
- Password managers no longer offer to autofill file names or code in the editor.

### Changed

- Updated the build to Gradle 9.9.2, Android Gradle Plugin 9.5.2 and Kotlin 2.4.21, with Room 2.8.5 and
  kotlinx.serialization 1.13.0.

## [1.0.0] - 2026-11-24

First public release.

### Added

- Browse, search, create, edit, delete, star and share your gists and starred gists.
- Local snippets stored on the device, usable without an account; upload one to Gist as secret or public,
  and sync it again after editing.
- Save any gist as an offline local copy.
- Several files per gist or snippet: add, upload from the device, rename, reorder and remove them.
- Code viewer and editor with syntax highlighting, line numbers, word wrap and adjustable text size;
  download or share a file.
- Language logos on list cards (from [Simple Icons](https://simpleicons.org)).
- Share text from other apps to start a snippet, and import code files.
- Back up local snippets and gists to a JSON file, and restore them.
- Material You dynamic color, six built-in color palettes, and light and dark themes.
- Personal access token sign-in, with the token encrypted by an Android Keystore key.
- Ko-fi support link in Settings → About.

[Unreleased]: https://github.com/iamgiven/GistDroid/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/iamgiven/GistDroid/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/iamgiven/GistDroid/releases/tag/v1.0.0
