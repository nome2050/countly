// Run AFTER `npx cap add android`. Adds the Countly home-screen widget to the generated Android project.
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const app = path.join(root, 'android', 'app', 'src', 'main');
const javaDir = path.join(app, 'java', 'com', 'countly', 'app');
const resDir = path.join(app, 'res');

if (!fs.existsSync(app)) {
  console.error('android project not found - run "npx cap add android" first');
  process.exit(1);
}

const w = (p, s) => { fs.mkdirSync(path.dirname(p), { recursive: true }); fs.writeFileSync(p, s); };

// 1. Java sources (MainActivity is replaced so it registers the bridge plugin)
fs.mkdirSync(javaDir, { recursive: true });
for (const f of ['BaseWidget.java', 'WidgetRenderer.java', 'CountlyWidget.java', 'WidgetNext.java',
                 'WidgetHealth.java', 'WidgetFull.java', 'Notifier.java', 'NotifyReceiver.java', 'BootReceiver.java',
                 'WidgetBridgePlugin.java', 'MainActivity.java']) {
  fs.copyFileSync(path.join(root, 'native', f), path.join(javaDir, f));
}

// 2. Widget layout: the widgets are drawn as one bitmap (donut, rings, bars, icons) by WidgetRenderer
w(path.join(resDir, 'layout', 'widget_countly.xml'), `<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/root" android:layout_width="match_parent" android:layout_height="match_parent">
    <ImageView android:id="@+id/img" android:layout_width="match_parent" android:layout_height="match_parent"
        android:scaleType="fitXY" android:contentDescription="Countly"/>
</FrameLayout>
`);

// 3. Static previews shown in the widget picker
const sample = (title, rows) => `<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent"
    android:orientation="vertical" android:padding="16dp" android:background="@drawable/widget_bg">
    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:text="${title}" android:textColor="#2EA8FF" android:textSize="16sp" android:textStyle="bold"/>${rows.map(([n, d, c]) => `
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:paddingTop="6dp">
        <TextView android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:text="${n}" android:textColor="#F2F1F7" android:textSize="14sp"/>
        <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="${d}" android:textColor="${c}" android:textSize="14sp" android:textStyle="bold"/>
    </LinearLayout>`).join('')}
</LinearLayout>
`;
const R3 = [['Iqama', '23 days', '#FF4D5E'], ['Passport', '4 months', '#FFB524'], ['Insurance', '1 year', '#30E88D']];
w(path.join(resDir, 'layout', 'widget_preview_list.xml'), sample('Countly', R3));
w(path.join(resDir, 'layout', 'widget_preview_next.xml'), sample('Iqama', [['23', 'days left', '#FF4D5E']]));
w(path.join(resDir, 'layout', 'widget_preview_health.xml'), sample('Next · Iqama', [['Insurance', '7 days', '#FF4D5E'], ['Passport', '4 months', '#FFB524'], ['Visa', '2 years', '#30E88D']]));
w(path.join(resDir, 'layout', 'widget_preview_full.xml'), sample('Countly', [...R3, ['Visa', '2 years', '#30E88D']]));

// 4. Background used by previews: pure AMOLED black, rounded
w(path.join(resDir, 'drawable', 'widget_bg.xml'), `<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#000000"/>
    <corners android:radius="26dp"/>
    <stroke android:width="1dp" android:color="#22FFFFFF"/>
</shape>
`);

// 4b. Status-bar icon for reminder notifications (a bell)
w(path.join(resDir, 'drawable', 'ic_stat_countly.xml'), `<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:pathData="M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.9,2 2,2zM18,16v-5c0,-3.07 -1.63,-5.64 -4.5,-6.32V4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.64,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z"/>
</vector>
`);

// 5. Widget metadata: four sizes
const sizes = [
  // [class, xml name, label, description, minW, minH, resizeW, resizeH, cellsW, cellsH, preview]
  ['CountlyWidget', 'countly_widget_info', 'Countly List', 'Your closest deadlines with progress bars.', 250, 180, 180, 110, 4, 3, 'list'],
  ['WidgetNext', 'countly_widget_next_info', 'Countly Next', 'A big countdown ring for your next expiry.', 110, 110, 110, 110, 2, 2, 'next'],
  ['WidgetHealth', 'countly_widget_health_info', 'Countly Glance', 'Your most urgent document in big numbers, plus the next three.', 250, 110, 180, 110, 4, 2, 'health'],
  ['WidgetFull', 'countly_widget_full_info', 'Countly Dashboard', 'Donut, 12-month timeline and upcoming deadlines.', 250, 250, 250, 180, 4, 4, 'full'],
];
let strings = '';
for (const [cls, xml, label, desc, mw, mh, rw, rh, cw, ch, pv] of sizes) {
  strings += `    <string name="${xml}_desc">${desc}</string>\n    <string name="${xml}_label">${label}</string>\n`;
  w(path.join(resDir, 'xml', xml + '.xml'), `<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="${mw}dp" android:minHeight="${mh}dp"
    android:minResizeWidth="${rw}dp" android:minResizeHeight="${rh}dp"
    android:targetCellWidth="${cw}" android:targetCellHeight="${ch}"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/widget_countly"
    android:previewLayout="@layout/widget_preview_${pv}"
    android:description="@string/${xml}_desc"
    android:widgetCategory="home_screen"/>
`);
}
w(path.join(resDir, 'values', 'countly_widget_strings.xml'), `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="countly_widget_description">Your closest document deadlines at a glance.</string>
${strings}</resources>
`);

// 6. Register the receivers + permissions in the manifest
const mf = path.join(app, 'AndroidManifest.xml');
let xml = fs.readFileSync(mf, 'utf8');
if (!xml.includes('POST_NOTIFICATIONS')) {
  const perms = `    <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
    <uses-permission android:name="android.permission.READ_CALENDAR"/>
    <uses-permission android:name="android.permission.VIBRATE"/>
    `;
  if (!xml.includes('<application')) { console.error('manifest has no <application'); process.exit(1); }
  xml = xml.replace('<application', perms + '<application');
}
if (!xml.includes('.CountlyWidget')) {
  let receivers = '';
  for (const [cls, x] of sizes) {
    receivers += `
        <receiver android:name=".${cls}" android:exported="true" android:label="@string/${x}_label">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE"/>
                <action android:name="android.intent.action.DATE_CHANGED"/>
                <action android:name="android.intent.action.TIME_SET"/>
                <action android:name="android.intent.action.TIMEZONE_CHANGED"/>
            </intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/${x}"/>
        </receiver>`;
  }
  if (!xml.includes('</application>')) { console.error('manifest has no </application>'); process.exit(1); }
  receivers += `
        <receiver android:name=".NotifyReceiver" android:exported="false"/>
        <receiver android:name=".BootReceiver" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED"/>
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED"/>
            </intent-filter>
        </receiver>`;
  xml = xml.replace('</application>', receivers + '\n    </application>');
  fs.writeFileSync(mf, xml);
}
console.log('Countly widgets added (4 sizes).');
