#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")" && pwd)"
build="$root/.android-build"
mkdir -p "$build/app/src/main/java/com/sitemark/camera" "$build/app/src/main/assets" "$build/app/src/main/res/drawable-nodpi" "$build/app/src/main/res/xml"
cp "$root/android-settings.gradle" "$build/settings.gradle"
cp "$root/android-app.gradle" "$build/app/build.gradle"
printf 'android.useAndroidX=true\norg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8\n' > "$build/gradle.properties"
cp "$root/AndroidManifest.xml" "$build/app/src/main/AndroidManifest.xml"
cp "$root/SiteMarkActivity.java" "$build/app/src/main/java/com/sitemark/camera/SiteMarkActivity.java"
cp "$root/camera_paths.xml" "$build/app/src/main/res/xml/camera_paths.xml"
cp "$root/icon-camera-512.png" "$build/app/src/main/res/drawable-nodpi/ic_launcher.png"
for f in index.html mobile.css mobile.js field.css field.js location.js places.js places.css launch.js launch.css simple.js simple.css manifest.webmanifest icon-camera-192.png icon-camera-512.png native.js; do cp "$root/$f" "$build/app/src/main/assets/$f"; done
# Every web asset is bundled in the APK. A browser service worker is unnecessary.
sed -i '/navigator\.serviceWorker\.register/d' "$build/app/src/main/assets/mobile.js"
sed -i 's@</body>@<script src="./native.js" defer></script></body>@' "$build/app/src/main/assets/index.html"
gradle -p "$build" --no-daemon :app:assembleDebug
mkdir -p "$root/android-artifacts"
cp "$build/app/build/outputs/apk/debug/app-debug.apk" "$root/android-artifacts/SiteMark-Android.apk"
