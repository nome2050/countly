<div align="center">

<img src="docs/logo.png" width="120" alt="Countly logo">

# Countly

**Never miss a renewal again.** A beautiful countdown tracker for your visa, iqama, passport, licence and every other document that expires.

[![Build APK](https://github.com/nome2050/countly/actions/workflows/build.yml/badge.svg)](https://github.com/nome2050/countly/actions/workflows/build.yml)
![Platform](https://img.shields.io/badge/platform-Android%2013%2B-3ddc84)
![Theme](https://img.shields.io/badge/theme-AMOLED%20black-000000)

[**Download the latest APK**](https://github.com/nome2050/countly/releases/latest)

</div>

---

## Screenshots

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/1-dashboard.jpg" width="220"><br><sub><b>Dashboard</b><br>health score and key numbers</sub></td>
    <td align="center"><img src="docs/screenshots/2-analytics.jpg" width="220"><br><sub><b>Analytics</b><br>12-month timeline, up next, insights</sub></td>
    <td align="center"><img src="docs/screenshots/3-documents.jpg" width="220"><br><sub><b>Documents</b><br>live countdown for each one</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/4-add-document.jpg" width="220"><br><sub><b>Add or edit</b><br>quick presets, custom warning window</sub></td>
    <td align="center"><img src="docs/screenshots/5-themes.jpg" width="220"><br><sub><b>10 themes</b><br>plus the home-screen widget button</sub></td>
    <td></td>
  </tr>
</table>

<sub>Screenshots are taken from the app's interface at phone size with sample data.</sub>

## Features

- **Countdown for every document:** days left, plus an exact "2 years, 3 months, 5 days" breakdown, with a progress ring.
- **Colour-coded status:** green when valid, amber when it is time to renew, red when it expires within 30 days or has expired.
- **Dashboard:** a document-health donut chart, key counters, a 12-month expiry timeline, your closest deadlines, and insights such as average time left.
- **Home-screen widgets in 4 sizes:** *Next* (2×2 countdown ring), *Health* (4×2 donut + counters), *List* (4×3 deadlines with progress bars) and *Dashboard* (4×4 donut, 12-month timeline and deadlines). They are drawn like the app, follow your theme and refresh daily.
- **19 categories with icons** (passport, visa, ID, licence, vehicle, insurance, health, study, work, home, card, plans, warranty, pet, gov, phone, member, utility, other).
- **Overdue in days, months or years** (or Auto) — your choice in Settings.
- **Backup / restore** of your documents from the settings sheet.
- **12 vibrant colour themes** on a true AMOLED black background, with rounded Material-style design and smooth animations.
- **Private and offline:** everything is stored on your phone. No account, no server, no tracking.
- **Made for real life:** quick presets (Passport, Iqama, Visa, Driving licence, Insurance, Exit/Re-entry), Arabic names supported, undo after delete, and the Back button closes sheets before leaving the app.

## Install on your phone

1. Open the [latest release](https://github.com/nome2050/countly/releases/latest) on your phone and download **Countly.apk**.
2. Tap the file. When Android asks, allow **Install unknown apps** for your browser or file manager.
3. If Play Protect warns about an unknown app, choose **Install anyway**. This is normal for apps installed outside the Play Store.

Designed for Android 13 and newer.

### Add the widgets
Four sizes: **Next** (2×2), **Health** (4×2), **List** (4×3) and **Dashboard** (4×4). Open Countly, tap the palette button in the top right, then **Add home-screen widget** (adds the List widget).
You can also long-press the home screen, open **Widgets**, and drag the widget you want out. Make it taller to show more documents.

## Build it yourself (no Android Studio needed)

The APK is built in the cloud with GitHub Actions:

1. Fork or push this repo to your own GitHub account.
2. Open **Actions**, then **Build APK**. A run starts on every push to `main` (or click **Run workflow**).
3. When it finishes, the APK is published on the repo's **Releases** page as `Countly.apk`.
   It is also attached to the run as an artifact (GitHub always zips artifacts, so the Releases page is the easier download).

## How it works

| Part | What it does |
| --- | --- |
| `www/` | The app itself: plain HTML, CSS and JavaScript with no frameworks. |
| [Capacitor](https://capacitorjs.com) | Wraps `www/` into a real Android app. |
| `native/` | The Java code for the four home-screen widgets (`WidgetRenderer` draws the dashboards as images) and the bridge that sends them your data. |
| `scripts/add-widget.js` | Adds the four widget sizes to the generated Android project during the build. |
| `scripts/add-branding.js` | Adds the app icon (including the Android 13+ themed icon), the black splash screen and the black system bars. |
| `scripts/add-signing.js` | Signs every build with the same key and gives it a rising version number, so it installs as an update and keeps your data. |
| `signing/countly.keystore` | The fixed signing key (personal-use debug key, not for the Play Store). |
| `.github/workflows/build.yml` | Builds the debug APK and publishes it. |

### Run it in a browser
```bash
cd www
python -m http.server 8000
```
Then open http://localhost:8000. The widget only works in the installed Android app.

## Project status

Countly is a debug build intended for personal use. The interface and date logic are covered by browser tests (about 90 checks across phone sizes, tablet and landscape). The widget drawing code is new and has only been compile-checked, so please report anything that looks off. The widget and Android integration were built to the standard Android APIs for versions 13 to 17; please open an issue if something looks wrong on your phone and include your phone model and Android version.

## Updating without losing data

Every build is signed with the same key (`signing/countly.keystore`) and has a higher version number than the last one, and the app ID never changes. Android therefore installs a new APK **over** the old one and keeps all your documents and settings. Documents from older versions load unchanged.

> If Android ever says *App not installed*, the installed copy was signed with a different key (this can happen once, when moving from builds made before fixed signing). Use **Settings > Copy backup** in the app first if you can, then uninstall and install the new APK once; after that every update installs in place.

## Privacy

Your documents are stored only on your phone. Countly has no analytics, no account and no server: it never sends your data anywhere.
