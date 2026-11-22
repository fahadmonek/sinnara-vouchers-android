# Sinnara Vouchers – Android app

Opens the voucher website (https://sinnara-vouchers.pages.dev/) and prints vouchers
directly to each branch's network thermal printer (ESC/POS, IP address + port 9100).
No other printing app is needed.

## Build (free, on GitHub)
1. Upload everything in this folder (including the `.github` folder) to a new private GitHub repository.
2. Open the **Actions** tab. The "Build Android app" workflow runs automatically (about 5 minutes).
3. Open **Releases** and download `SinnaraVouchers.apk` on the phone, then install it.

If the Actions tab is empty, create the file `.github/workflows/build-apk.yml`
with **Add file → Create new file** and paste the content from this folder.

## Change the website address
Edit `APP_URL` in `app/src/main/java/com/sinnara/vouchers/MainActivity.java`.

## Updating the app
Change `versionCode` (+1) and `versionName` in `app/build.gradle`, upload, and install the new APK
over the old one. Keep `app/sinnara-release.keystore` – updates must be signed with the same key.
