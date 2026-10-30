# Azkar (native Kotlin)

Native Android (Kotlin + XML) conversion of the nine HTML screens. No WebView, no HTML/JS at runtime.

Build: open this folder in Android Studio (Koala or newer, JDK 17), let Gradle sync, then
**Build > Build APK(s)** (or Run on a device). minSdk 26, targetSdk 34. Package: `com.azkar.app`.

## Screens
Home, Library, My azkar, Reader, Add zikr, Edit zikr, Edit folder (also the folder view), Customize Home, Reminders.

## Where the design values came from
Every colour, size, radius, padding, gap, font size/weight and line height is copied from the inline CSS of the
screens (CSS px = dp, font px = sp). Icons are the original SVG paths as vector drawables. Fonts are the DM Sans
(400/500/700) and Amiri (400/700) files embedded in the HTML, converted to TTF in `res/font`.
Placeholder text uses Chrome's default placeholder grey (#757575) because the HTML does not set one.

## Storage
`data/Store.kt` keeps azkar, folders, Home customisation, reader settings and reminder switches in
SharedPreferences (JSON). Everything persists across restarts.

## Reminders
Daily local notifications at 6:00 AM (Morning), 5:00 PM (Evening), 10:00 PM (Before sleep), using inexact alarms
(no special permission), re-armed after reboot. Android 13+ asks for notification permission when a reminder is switched on.
"# Azkar-brsmi" 
