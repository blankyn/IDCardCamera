# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build requirements

AGP 7.4.2 requires **JDK 11**. The shell here defaults to Java 8, which fails at configuration time with a confusing "No matching variant of com.android.tools.build:gradle:7.4.2" error. Prefix Gradle invocations:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jbr_dcevm-11.0.16/Contents/Home
```

Android Studio already uses `jbr-11` (`.idea/gradle.xml`). JitPack uses openjdk11 (`jitpack.yml`).

`gradlew` line 23 prints `cd: "./: No such file or directory` on every run — a quoting bug in this older wrapper script. Harmless; the build still resolves. Don't chase it.

## Common commands

```bash
./gradlew :app:assembleDebug                 # build demo app
./gradlew :idcard_lib:assembleRelease        # build library aar
./gradlew :idcard_lib:testDebugUnitTest      # unit tests (JVM)
./gradlew :idcard_lib:connectedDebugAndroidTest  # instrumented tests (device needed)
./gradlew :idcard_lib:lintDebug              # lint (abortOnError=false in both modules)
./gradlew clean
```

Run a single test:

```bash
./gradlew :idcard_lib:testDebugUnitTest --tests "com.blankm.idcardlib.ExampleUnitTest.addition_isCorrect"
```

Both modules only contain the generated `ExampleUnitTest` / `ExampleInstrumentedTest` stubs — there is no real test coverage. Note the stubs live under `com.blankm.*` while production code is `me.blankm.*`.

## Publishing

Publishing config is in `idcard_lib/bintray.gradle` (the name is historical; it applies `maven-publish` + `signing`, not Bintray).

```bash
./gradlew :idcard_lib:publishReleasePublicationToIdcard_libRepository
./gradlew :idcard_lib:publishToMavenLocal
```

Two things gate this:

- `debug_flag` in `bintray.gradle:15` — `true` publishes to the hardcoded local path `/Volumes/document/repo-local`; set `false` to publish to Sonatype (`s01.oss.sonatype.org`), which reads `artifactory_user` / `artifactory_password` from `local.properties` and `signing.*` from `gradle.properties`.
- `MAVEN_VERSION` in `gradle.properties` drives the library's `versionName`, and `versionCode` is derived by stripping dots from it (`idcard_lib/build.gradle:40`). A regex check (`idcard_lib/build.gradle:24`) rejects anything not matching `x.x(.x)(-SNAPSHOT)`, so the build fails at configure time on a malformed version.

All repositories are routed through Aliyun mirrors and the Gradle distribution comes from a Huawei Cloud mirror — expect these in `build.gradle` and `gradle/wrapper/gradle-wrapper.properties`.

## Module layout and the dependency gotcha

- `idcard_lib` — the published library, `io.github.blankyn:idcard_lib`.
- `app` — demo app (`me.blankm.idcardcamera`).

**`app` does not depend on the local library module.** `app/build.gradle:56-57` has the project dependency commented out in favor of the published artifact:

```gradle
//    implementation project(path: ':idcard_lib')
    implementation 'io.github.blankyn:idcard_lib:1.0.7'
```

To exercise local library changes in the demo app, swap those two lines. Otherwise the app silently builds against the released 1.0.7 aar and your edits appear to do nothing.

## Library architecture

`IDCardCameraSelect` is the whole public surface — a static factory over an `Activity` or `Fragment` (held in `WeakReference`) that launches one of two camera screens via `startActivityForResult`. Both activities are declared in the library manifest, so consumers get them automatically.

There are **two independent, parallel camera implementations** with no shared code path:

| Entry | Activity | Camera API |
|---|---|---|
| `openCamera(type)` | `CameraActivity` (711 lines) | legacy `android.hardware.Camera` via `CameraPreview`/`CameraUtils` |
| `takePhoto(type)` | `CameraXActivity` (604 lines) | CameraX (`ProcessCameraProvider`, `PreviewView`, `ImageCapture`) |

`CameraActivity` is the documented, README-supported path. `CameraXActivity` is the newer one and carries its own crop/save logic in `utils/Tools.java` rather than reusing the `cropper/` package. Changes to cropping or output-path behavior generally need to be made in both places.

**CameraX and appcompat are `compileOnly` in the library** (`idcard_lib/build.gradle:74-86`). Consumers must declare `androidx.camera:*` and `androidx.appcompat` themselves or `CameraXActivity` throws `NoClassDefFoundError` at runtime. The README only documents adding appcompat, so the CameraX path is effectively undocumented for consumers.

### Result contract

Callers read results in `onActivityResult`, matching on `resultCode == IDCardCameraSelect.RESULT_CODE` (`0x11`, not `RESULT_OK`) and unpacking with `IDCardCameraSelect.getImagePath(data)` → `List<String>` of file paths. `TYPE_IDCARD_All` returns two paths (front, back); the single-side types return one. See `app/src/main/java/me/blankm/idcardcamera/MainActivity.java:104`.

### Capture state machine

`CameraActivity.curIDCardCamera` encodes both the side and the source in one int: `0` = camera front, `1` = camera back, `2` = album front, `3` = album back. `settingCameraType()` reads it to swap the overlay mipmap, title, and tip margin; `cameraCropNext()` / `albumCropNext()` advance it and decide whether to loop for the second side or finish. Any change to the front/back sequencing touches all of these.

### Two crop pipelines inside CameraActivity

- **Camera capture** — `takePhoto()` grabs a preview frame via `setOneShotPreviewCallback`, then `cropImage()` auto-crops by computing the scan frame's position as proportions of the preview bounds, then hands the result to `CropImageView`/`CropOverlayView` for manual adjustment.
- **Album pick** — the picked `Uri` is resolved through `UriUtils` (branching on Android Q for `uriToFileApiQ` vs `getFileFromMediaUri` + EXIF rotation) into `AlbumClipImageView` for pan/zoom. `createClippedBitmap()` then uses `BitmapRegionDecoder` against the original file path when `mSampleSize > 1`, so large images are cropped without decoding the full bitmap. That path reconstructs the crop rect from the view's matrix values and un-rotates it via `getRealRect()`.

### Output files and cleanup

Camera output lands in `FileUtils.getImageCacheDir()` (external files dir + `/cache`), album output in `getExternalCacheDir()`. Nothing is cleaned automatically — consumers call `FileUtils.clearCache(context)`, typically in `onDestroy`.

### Permissions

`PermissionUtils.checkPermissionFirst(context, requestCode)` is the version-aware entry point: `READ_MEDIA_IMAGES` on API 33+, a no-op `Environment.isExternalStorageManager()` check on API 30-32 (`MANAGE_EXTERNAL_STORAGE` can't be requested via `requestPermissions`, and the code deliberately skips it), legacy read/write below that. On top of that, `CameraActivity.checkSelfPermission` is overridden to unconditionally report storage permissions as granted on API 30+ (`CameraActivity.java:680`). Denials route to `showPermissionsDialog` → app details settings, with `isEnterSetting` driving a re-check in `onResume`.

## Conventions

- Java only, source/target 1.8. `minSdk` 21, `targetSdk` 29, `compileSdk` 33.
- Comments and log messages are in Chinese; keep new comments consistent with the surrounding file.
- Library resource naming is inconsistent — four coexisting prefixes (`idcard_`, `idcard_lib_`, `picture_`, `ic_`) plus a set of unprefixed names that can collide with consumer resources: `AppTheme`, `colorPrimary`, `colorAccent`, `black`, `white`, `crop_fail`, `album_iv_bg`, `bg_bankcard`. `AppTheme` in particular already collides with the demo app's own `@style/AppTheme`. Prefix anything new with `idcard_`, and don't assume an existing unprefixed resource is unused by consumers before renaming it.
- `global/Constant.java` is `@Deprecated` (it uses the old public-external-storage layout) — use `FileUtils` / `Tools` for paths instead.
- The release keystore `buildscript/blankm.keystore` is committed with its credentials inline in `app/build.gradle:16-23`. It signs the demo app only, but avoid propagating that pattern.

## Optional config import

A Gemini CLI config exists at `~/.gemini/settings.json`. To import user-level items (MCP servers, slash commands, subagents, skills, instructions) into Claude Code, reply `/import` to see what's importable, then `/import --yes=<digest>` to apply it. If `/import` isn't available on this surface, run `claude import` from a terminal.
