[![Maven Central](https://img.shields.io/maven-central/v/io.github.eugenprog/giraffe)](https://central.sonatype.com/artifact/io.github.eugenprog/giraffe)

# Giraffe: User Guide

**Giraffe** is an Android library that intercepts gRPC traffic and gives you an in-app, chat-style viewer for it - no proxy, no desktop tool, works straight from the device the app is running on.

[Читать на русском](README.ru.md)

**Core Principles:**
* **Zero Setup:** Just add the dependency - no KSP plugin, no annotations, nothing to configure on your side.
* **On-Device:** Traffic is logged straight into a local, private Room database - nothing leaves the phone.
* **Content-Aware:** Images, audio, video, and arbitrary binary payloads are recognized and pulled out even when they're embedded inside an otherwise text/JSON-shaped protobuf message.

---

## 🚀 Installation and Setup

### Step 1: Add the Dependency

The library is published on **Maven Central** as a single, self-contained AAR.

```kotlin
dependencies {
    // Check the badge above for the latest version
    implementation("io.github.eugenprog:giraffe:<version>")
}
```
Giraffe uses KSP internally to build its own UI, but that's already baked into the published artifact - you don't need to apply the KSP plugin or configure anything to consume it.

### Step 2: Grant the Notification Permission (Android 13+)

Giraffe posts a system notification for every intercepted call so you can jump straight to it. On API 33+, that needs the runtime `POST_NOTIFICATIONS` permission - Giraffe declares it in its manifest, but your app still has to request it at runtime, same as for any other notification:
```kotlin
if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
    != PackageManager.PERMISSION_GRANTED
) {
    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
}
```
Without it, traffic is still logged and viewable in-app - you just won't get notified about it.

---

## ⚙️ How to Use

### 1. Attach the Interceptor

Add `GiraffeInterceptor` to the gRPC channel you want to inspect:
```kotlin
val channel = AndroidChannelBuilder
    .forAddress(host, port)
    .context(context)
    .usePlaintext()
    .intercept(GiraffeInterceptor(context.applicationContext))
    .build()
```
Every request and response on that channel is now logged - decoded field-by-field where possible - and a traffic notification is posted for it.

### 2. Open the Viewer

Tap a traffic notification (or launch `GiraffeActivity` yourself, e.g. from a debug menu) to open Giraffe's own screen: a list of calls, and a details screen per call with the full request/response history.

* Tap an **image** or **video** bubble to open a full-screen preview - pinch-to-zoom for images, auto-playing for video - with a share button.
* Tap the small **copy** icon under any message to copy just that message's text.
* Tap the **copy** icon in the details screen's top bar to copy the *entire* call as plain text - URL, headers, and every request/response body in wire order - ready to paste into a bug report. File content is never included.
* Select chats in the list to delete them; their cached media files are removed along with them.

---

## ✨ Features

* Recognizes images, audio, video, and arbitrary binary payloads even when they're buried inside an otherwise-JSON-shaped protobuf message, and replaces just that embedded chunk with a placeholder so the rest of the message stays readable.
* Persists everything in a local Room database, so the log survives navigating away, process death, and app restarts.
* Ships with its own Jetpack Compose UI (MVI-based) - nothing to build yourself.

---

## ⚠️ Important Notes

1. **This is a debug tool.** Don't leave it wired into a production traffic path - gate the `GiraffeInterceptor(...)` call behind `BuildConfig.DEBUG` or a dedicated build variant.
2. **`loggingEnabled = false`** on `GiraffeInterceptor` only silences its `Log.d` output - traffic is still recorded to the database and still triggers notifications regardless of this flag.
3. **Media files** are written to the app's private cache directory and shared out (from the preview screens) through Giraffe's own `FileProvider` - you don't need to declare one yourself.

[README.ru](README.ru.md)
