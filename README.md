# Digital Ledger — native Kotlin (Jetpack Compose)

A from-scratch native port of your HTML app (dl2_0_0.html): same tabs, same data format, same fonts
(Orbitron + Share Tech Mono), same 188 logo icons, same 19 widgets, same startup animations and sounds.

## Build
* **Android Studio:** File > Open this folder, let Gradle sync, press Run.
  The first build downloads the two fonts automatically (needs internet). If that fails, put
  `Orbitron.ttf` and `ShareTechMono.ttf` into `app/src/main/assets/fonts/`.
* **No Android Studio:** upload this folder to a GitHub repo; the Actions tab builds `app-debug.apk`.

## Moving your data from the HTML app
Customize > Backup & restore. A backup file or "DLBACKUP1:..." code from the HTML app restores here as-is
(and backups made here restore in the HTML app).
