// Run AFTER `npx cap add android`. Makes every build update the previously installed app in place:
//  1. signs with the SAME fixed key every time (Android refuses updates signed with a different key)
//  2. uses an ever-increasing versionCode (Android refuses to "downgrade")
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const gradle = path.join(root, 'android', 'app', 'build.gradle');
const keystore = path.join(root, 'signing', 'countly.keystore').replace(/\\/g, '/');
if (!fs.existsSync(gradle)) { console.error('android/app/build.gradle not found'); process.exit(1); }
if (!fs.existsSync(keystore)) { console.error('signing/countly.keystore not found'); process.exit(1); }

let g = fs.readFileSync(gradle, 'utf8');
if (!g.includes('signingConfigs.countly')) {
  g = g.replace(/android\s*\{/, `android {
    signingConfigs {
        countly {
            storeFile file('${keystore}')
            storePassword 'countly-app'
            keyAlias 'countly'
            keyPassword 'countly-app'
        }
    }`);
  g = g.replace(/buildTypes\s*\{/, `buildTypes {
        debug {
            signingConfig signingConfigs.countly
        }`);
}
const code = Math.floor(Date.now() / 60000); // minutes since 1970: always rises, fits in an int until ~6000 AD
g = g.replace(/versionCode\s+\d+/, 'versionCode ' + code).replace(/versionName\s+"[^"]*"/, 'versionName "1.1.0"');
fs.writeFileSync(gradle, g);
console.log('Signing + versionCode ' + code + ' configured.');
