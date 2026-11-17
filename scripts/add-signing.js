// Run AFTER `npx cap add android`. Makes every build update the previously installed app in place:
//  1. signs with the SAME fixed key every time (Android refuses updates signed with a different key)
//  2. uses an ever-increasing versionCode (Android refuses to "downgrade")
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const gradle = path.join(root, 'android', 'app', 'build.gradle');
if (!fs.existsSync(gradle)) { console.error('android/app/build.gradle not found'); process.exit(1); }

// Preferred: the key comes from GitHub Secrets (COUNTLY_KEYSTORE_BASE64 etc.).
// Fallback: the key committed in signing/ (same key, so updates keep working either way).
let keystore, storePass, alias, keyPass;
if (process.env.COUNTLY_KEYSTORE_BASE64) {
  keystore = path.join(root, 'android', 'countly-secret.keystore');
  fs.writeFileSync(keystore, Buffer.from(process.env.COUNTLY_KEYSTORE_BASE64, 'base64'));
  storePass = process.env.COUNTLY_KEYSTORE_PASSWORD;
  alias = process.env.COUNTLY_KEY_ALIAS || 'countly';
  keyPass = process.env.COUNTLY_KEY_PASSWORD || storePass;
  if (!storePass) { console.error('COUNTLY_KEYSTORE_PASSWORD secret is missing'); process.exit(1); }
  console.log('Signing with the key from GitHub Secrets.');
} else {
  keystore = path.join(root, 'signing', 'countly.keystore');
  storePass = keyPass = 'countly-app';
  alias = 'countly';
  console.log('Signing with the key committed in signing/ (no secrets configured).');
}
if (!fs.existsSync(keystore)) { console.error('keystore not found: ' + keystore); process.exit(1); }
keystore = keystore.replace(/\\/g, '/');

let g = fs.readFileSync(gradle, 'utf8');
if (!g.includes('signingConfigs.countly')) {
  g = g.replace(/android\s*\{/, `android {
    signingConfigs {
        countly {
            storeFile file('${keystore}')
            storePassword '${storePass}'
            keyAlias '${alias}'
            keyPassword '${keyPass}'
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
