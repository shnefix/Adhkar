# Azkar

A native Android app for reading, organising, and practising daily azkar. It is built with Kotlin and XML layouts;
there is no WebView or HTML/JavaScript runtime.

![Azkar app preview](app/src/main/res/drawable-nodpi/app_logo.png)

## Features

- Home dashboard with configurable collections and folders
- Library, My Azkar, folder editing, and a focused Arabic reader
- Adjustable Arabic text size, tap-to-advance reading, and keep-screen-on mode
- OLED black theme enabled by default, with a teal theme available in Reminders
- Daily local reminders for morning, evening, and before sleep
- Home-screen widgets in small, wide, and large layouts
- Import/export of azkar data with persistent local storage
- RTL and Arabic typography support using bundled Amiri and DM Sans fonts

## Screens

Home, Library, My Azkar, Reader, Add Zikr, Edit Zikr, Edit Folder, Customize Home, and Reminders.

## Build

1. Open this directory in Android Studio Koala or newer.
2. Use JDK 17 and let Gradle sync.
3. Select **Build > Build APK(s)**, or run the app on an Android device.

The project uses `minSdk 26`, `targetSdk 34`, and application ID `com.azkar.app`.

From a terminal on Windows, the debug APK can be built with:

```powershell
.\gradlew.bat assembleDebug
```

## Storage and reminders

`data/Store.kt` stores azkar, folders, home customisation, reader settings, and reminder switches in
`SharedPreferences` as JSON. Daily reminders use inexact alarms and are re-armed after reboot. Android 13+
requests notification permission when a reminder is enabled.

## Design

The UI uses the original design values, vector icons, and bundled DM Sans and Amiri fonts. The OLED palette uses
true black backgrounds to reduce power use on OLED displays.
