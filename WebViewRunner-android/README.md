# WebViewRunner Android integration

This folder is a complete Android Gradle project for turning WebViewRunner into an APK. It loads `https://pwthor.live/study/batches` directly in a native Android `WebView`; it does not use an iframe. `MainActivity.java` owns WebView navigation, cookies, DOM storage, the loading progress bar, script execution, timeouts, and friendly failure states.

## Build status

**The APK has not been compiled, installed, or runtime-tested in this sandbox.** The sandbox does not contain the Android SDK, Android build tools, Java/JDK, or a system Gradle installation. The source, Gradle files, Android resources, and wrapper files have been structurally checked, but an actual APK build and device test must be performed in AndroidIDE or another Android build environment.

## What is native-only

The browser preview at the project root is an interface demonstration. It does not create an iframe, does not load the target site inside the preview, and cannot call `WebView.evaluateJavascript()`. Its Run Script interaction is explicitly labeled as a preview simulation. The actual APK uses the Java `AUTHORIZED_SCRIPT` constant below the configuration banner in `MainActivity.java` and passes that configured body to `evaluateJavascript()` on the currently loaded page.

## Project structure

| Path | Responsibility |
| --- | --- |
| `app/src/main/java/live/pwthor/webviewrunner/MainActivity.java` | Native WebView setup, navigation, runner state machine, configured script, JSON result parsing, and 10-second timeout. |
| `app/src/main/res/layout/activity_main.xml` | Dark browser shell, navigation controls, loading progress bar, WebView, status panel, and spinner. |
| `app/src/main/res/drawable/` | Card, button, status, and launcher vector resources. |
| `app/src/main/res/values/` | App strings, colors, theme, and reusable button styles. |
| `app/src/main/AndroidManifest.xml` | Internet permission and launcher activity. |
| `build.gradle` / `app/build.gradle` | Android Gradle Plugin, SDK levels, Java 17, and AndroidX AppCompat dependency. |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/` | Gradle 8.2 wrapper scripts, jar, and pinned distribution URL. |

## Replace the script safely

Open `app/src/main/java/live/pwthor/webviewrunner/MainActivity.java` and edit only `AUTHORIZED_SCRIPT`. It is intentionally a separate Java constant. The initial script creates a small, page-local banner and returns an object with `ok`, `message`, and `source`; it does not click links, bypass an access control, or load an external script. Keep the replacement script authorized for the target page and make it a JavaScript function body that returns a value when useful.

The activity wraps the configured body only to capture a structured `{ok, result}` or `{ok:false, error}` response. It does not replace the configured body with a hidden fallback. If the body throws, the UI shows `Script Failed`; if the page returns successfully, the UI shows `Script Completed`. A navigation change or a 10-second timeout also restores the button and spinner and reports failure.

## Build on an Android phone with AndroidIDE

1. Install **AndroidIDE** from its official distribution on the Android phone that will build the APK. Grant its file access permission when requested.
2. Extract the supplied `WebViewRunner-android.zip` file into a writable folder on the phone, such as `Documents/WebViewRunner-android`. The extracted project root is the folder containing `settings.gradle`, `gradlew`, and `gradle/wrapper/`.
3. Open AndroidIDE, choose **Open existing project**, and select that extracted project root. Do not select only the `app` folder.
4. Allow the initial Gradle sync. The project pins Gradle `8.2`, Android Gradle Plugin `8.2.2`, `compileSdk 35`, `targetSdk 35`, `minSdk 26`, Java 17, and AndroidX AppCompat `1.7.0`. Network access is required on the first sync to download Gradle, the Android SDK platform/build tools, and Maven dependencies.
5. In AndroidIDE, choose **Build > Assemble Debug APK**. Alternatively, open AndroidIDE's terminal at the project root and run:

   ```bash
   chmod +x gradlew
   ./gradlew --version
   ./gradlew assembleDebug
   ```

6. Find the generated APK at `app/build/outputs/apk/debug/app-debug.apk`. Use AndroidIDE's install action, or open the APK with the Android package installer.
7. If Android blocks the install, open **Settings > Security / Install unknown apps**, allow AndroidIDE or the file manager used to open the APK, then retry. Disable that permission afterward if desired.
8. Launch **WebViewRunner**. The first screen load requires internet access. Use Back, Reload, and Home to exercise normal WebView navigation. The Run Script control stays unavailable until the current page finishes loading.

## Optional Termux build path

A phone with Termux and a Java 17 JDK can build the same extracted folder. From the Android project root, run `chmod +x gradlew && ./gradlew assembleDebug`. The wrapper downloads the pinned Gradle 8.2 distribution automatically; the Android SDK platform and build tools must also be installed and discoverable through `ANDROID_HOME` or `ANDROID_SDK_ROOT`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. AndroidIDE is recommended for a phone-only workflow because it handles the Android SDK, Gradle tooling, and APK installation in one environment.

## Runtime safety boundaries

The manifest requests only `android.permission.INTERNET`. The app keeps normal HTTP(S) navigation inside the WebView, enables JavaScript and DOM storage for the target site, preserves cookies through `CookieManager`, and does not clear the WebView cache or cookies on launch. Non-HTTP(S) schemes are not auto-followed. There is no automatic third-party link clicking, access-control bypass, or external bypass-script injection.
