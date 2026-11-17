// Run AFTER `npx cap add android`. Replaces the default Capacitor icon / splash with the Countly logo
// and makes the system bars black. Uses vector drawables only, so no image tools are needed.
const fs = require('fs');
const path = require('path');

const res = path.join(__dirname, '..', 'android', 'app', 'src', 'main', 'res');
if (!fs.existsSync(res)) { console.error('android project not found - run "npx cap add android" first'); process.exit(1); }
const w = (p, s) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, s); };

const ARC = 'M71.837,70.058 A24,24 0 1 1 71.837,37.942';

// Colour logo (adaptive-icon foreground, 108dp grid, content stays inside the 66dp safe zone)
w(path.join(res, 'drawable', 'countly_icon_fg.xml'), `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="${ARC}" android:strokeWidth="9" android:strokeLineCap="round" android:fillColor="#00000000">
        <aapt:attr name="android:strokeColor">
            <gradient android:type="linear" android:startX="30" android:startY="30" android:endX="78" android:endY="78">
                <item android:offset="0" android:color="#FF8ECBFF"/>
                <item android:offset="1" android:color="#FFC3B4FF"/>
            </gradient>
        </aapt:attr>
    </path>
    <path android:pathData="M78,54 m-5.5,0 a5.5,5.5 0 1 0 11,0 a5.5,5.5 0 1 0 -11,0" android:fillColor="#FFFFFFFF"/>
    <path android:pathData="M54,54 m-5,0 a5,5 0 1 0 10,0 a5,5 0 1 0 -10,0" android:fillColor="#998ECBFF"/>
</vector>
`);

// Single-colour version for Android 13+ "themed icons"
w(path.join(res, 'drawable', 'countly_icon_mono.xml'), `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="${ARC}" android:strokeWidth="9" android:strokeLineCap="round" android:strokeColor="#FFFFFFFF" android:fillColor="#00000000"/>
    <path android:pathData="M78,54 m-5.5,0 a5.5,5.5 0 1 0 11,0 a5.5,5.5 0 1 0 -11,0" android:fillColor="#FFFFFFFF"/>
    <path android:pathData="M54,54 m-5,0 a5,5 0 1 0 10,0 a5,5 0 1 0 -10,0" android:fillColor="#FFFFFFFF"/>
</vector>
`);

w(path.join(res, 'values', 'countly_colors.xml'), `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="countly_icon_bg">#FF000000</color>
</resources>
`);

const adaptive = `<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/countly_icon_bg"/>
    <foreground android:drawable="@drawable/countly_icon_fg"/>
    <monochrome android:drawable="@drawable/countly_icon_mono"/>
</adaptive-icon>
`;
w(path.join(res, 'mipmap-anydpi-v26', 'ic_launcher.xml'), adaptive);
w(path.join(res, 'mipmap-anydpi-v26', 'ic_launcher_round.xml'), adaptive);

// Launch screen: remove Capacitor's default splash images, use black + logo
for (const d of fs.readdirSync(res)) {
  if (!d.startsWith('drawable')) continue;
  for (const f of fs.readdirSync(path.join(res, d))) {
    if (/^splash\.(png|jpg|jpeg|webp)$/i.test(f)) fs.unlinkSync(path.join(res, d, f));
  }
}
w(path.join(res, 'drawable', 'splash.xml'), `<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item><shape android:shape="rectangle"><solid android:color="#FF000000"/></shape></item>
    <item android:width="132dp" android:height="132dp" android:gravity="center" android:drawable="@drawable/countly_icon_fg"/>
</layer-list>
`);

// Black status bar / navigation bar, black launch background (only touches styles it recognises)
const sf = path.join(res, 'values', 'styles.xml');
if (fs.existsSync(sf)) {
  let s = fs.readFileSync(sf, 'utf8');
  if (!s.includes('countly-bars')) {
    s = s.replace(/(<style name="AppTheme\.NoActionBar"[^>]*>)/,
      '$1\n        <!-- countly-bars -->\n        <item name="android:statusBarColor">#FF000000</item>\n        <item name="android:navigationBarColor">#FF000000</item>\n        <item name="android:windowBackground">#FF000000</item>');
    if (s.includes('Theme.SplashScreen')) {
      s = s.replace(/(<style name="AppTheme\.NoActionBarLaunch"[^>]*>)/,
        '$1\n        <item name="windowSplashScreenBackground">#FF000000</item>');
    }
    fs.writeFileSync(sf, s);
  }
}
console.log('Countly branding added.');
