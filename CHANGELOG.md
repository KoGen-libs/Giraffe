# Changelog

All notable changes to this project are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [0.1.0-alpha02] - 2026-08-10

### Added
- Full-screen image preview: tapping an image message opens it full-screen with pinch-to-zoom
  (`net.engawapg.lib:zoomable`) and a share button.
- Full-screen, auto-playing video preview (`androidx.media3`) - video messages get a tappable
  thumbnail instead of no UI at all.
- Share button for unrecognized ("Unknown") binary message content, through the same in-app
  `FileProvider` the image/video previews use.
- "Copy whole request" action in the chat details screen's top bar - copies the URL, headers,
  and every request/response body in wire order as plain text. File content is never included,
  since it never lived in the copyable text field to begin with.
- Full unit test suite for the library - analyzer/parsers, DB converters, domain mappers,
  services, use cases, and ViewModels - where none existed before.

### Fixed
- KSP's generated sources weren't registered as a Gradle-tracked source directory, so Android
  Studio flagged every generated symbol as unresolved even though the build itself succeeded.
  Registering the directory to fix that then broke `:giraffe:publish` with Gradle "implicit
  dependency" validation errors on `extractReleaseAnnotations`, `sourceReleaseJar`, and
  `javaDocReleaseGeneration`, since none of them declared a dependency on the KSP task that
  actually produces it.

## [0.1.0-alpha01] - 2026-08-05
- Initial public release: a gRPC `ClientInterceptor` that logs traffic into a local Room
  database, an in-app Jetpack Compose UI (chat list + chat details) to browse it, automatic
  image/audio/video/binary content detection, and a system notification per intercepted call.
