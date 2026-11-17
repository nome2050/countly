# Countly
Countdown tracker for visa, iqama, passport and other document expiry dates.
Black AMOLED Material design, 10 colour themes, dashboard analytics, and a home-screen widget.

## Build the APK (no Android tools needed)
1. Create an empty GitHub repo named `countly`, push this folder to it.
2. Open the repo > **Actions** > **Build APK** > wait for the green tick (~5 min).
3. Open the run > **Artifacts** > download `countly-apk` > unzip > `app-debug.apk`.
4. Copy it to the phone and install (allow "Install unknown apps" once).

## How it is built
`www/` is the app. The workflow generates the Android project with Capacitor, then
`scripts/add-widget.js` (home-screen widget + bridge) and `scripts/add-branding.js` (icon, splash, black bars)
patch it before Gradle builds the APK.

## Run in a browser
`cd www && python -m http.server 8000` then open http://localhost:8000
