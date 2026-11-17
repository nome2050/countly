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
for (const f of ['CountlyWidget.java', 'WidgetBridgePlugin.java', 'MainActivity.java']) {
  fs.copyFileSync(path.join(root, 'native', f), path.join(javaDir, f));
}

// 2. Widget layout: header + 6 rows (rows are hidden/shown from code depending on widget height)
let rows = '';
for (let i = 1; i <= 6; i++) {
  rows += `
    <LinearLayout android:id="@+id/row${i}" android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="horizontal" android:gravity="center_vertical" android:paddingTop="5dp" android:paddingBottom="5dp" android:visibility="gone">
        <TextView android:id="@+id/dot${i}" android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="●" android:textSize="11sp" android:layout_marginEnd="10dp"/>
        <TextView android:id="@+id/name${i}" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1"
            android:textColor="#E9E7EF" android:textSize="15sp" android:maxLines="1" android:ellipsize="end"/>
        <TextView android:id="@+id/days${i}" android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textSize="15sp" android:textStyle="bold" android:layout_marginStart="8dp"/>
    </LinearLayout>`;
}
w(path.join(resDir, 'layout', 'widget_countly.xml'), `<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/root" android:layout_width="match_parent" android:layout_height="match_parent"
    android:orientation="vertical" android:padding="16dp" android:background="@drawable/widget_bg">
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="horizontal" android:gravity="center_vertical" android:layout_marginBottom="4dp">
        <TextView android:id="@+id/title" android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1"
            android:text="Countly" android:textSize="17sp" android:textStyle="bold"/>
        <TextView android:id="@+id/summary" android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textSize="13sp"/>
    </LinearLayout>${rows}
</LinearLayout>
`);

// 3. Background: pure AMOLED black, rounded
w(path.join(resDir, 'drawable', 'widget_bg.xml'), `<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#000000"/>
    <corners android:radius="26dp"/>
    <stroke android:width="1dp" android:color="#22FFFFFF"/>
</shape>
`);

// 4. Widget metadata
w(path.join(resDir, 'values', 'countly_widget_strings.xml'), `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="countly_widget_description">Your closest document deadlines at a glance.</string>
</resources>
`);
w(path.join(resDir, 'xml', 'countly_widget_info.xml'), `<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp" android:minHeight="110dp"
    android:minResizeWidth="180dp" android:minResizeHeight="110dp"
    android:targetCellWidth="4" android:targetCellHeight="2"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/widget_countly"
    android:previewLayout="@layout/widget_countly"
    android:description="@string/countly_widget_description"
    android:widgetCategory="home_screen"/>
`);

// 5. Register the receiver in the manifest
const mf = path.join(app, 'AndroidManifest.xml');
let xml = fs.readFileSync(mf, 'utf8');
if (!xml.includes('.CountlyWidget')) {
  const receiver = `
        <receiver android:name=".CountlyWidget" android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE"/>
                <action android:name="android.intent.action.DATE_CHANGED"/>
                <action android:name="android.intent.action.TIME_SET"/>
                <action android:name="android.intent.action.TIMEZONE_CHANGED"/>
            </intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/countly_widget_info"/>
        </receiver>
    `;
  if (!xml.includes('</application>')) { console.error('manifest has no </application>'); process.exit(1); }
  xml = xml.replace('</application>', receiver + '</application>');
  fs.writeFileSync(mf, xml);
}
console.log('Countly widget added.');
