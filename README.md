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

`data/Store.kt` stores azkar, folders, and reminder schedules through Room. Lightweight settings such as home
customisation, reader settings, reading positions, and notification switches remain in `SharedPreferences`.
Daily reminders use inexact alarms and are re-armed after reboot. Android 13+ requests notification permission
when a reminder is enabled.

## Architecture

The project uses a Gradle version catalog, Hilt for dependency injection, and Room/Flow for the structured
azkar content repository. The `domain` package contains use cases over that repository.

### Room migration status

The `Store` facade now reads and writes folders, azkar, and reminders through Room while retaining its
compatibility API for platform integrations. Room one-time reads and transactions are suspend functions on
`Dispatchers.IO`; compatibility reads use an immutable in-memory snapshot and compatibility writes enqueue one
transaction on a serialized IO scope, so UI callbacks never wait on Room. Widget refreshes are coalesced and
posted asynchronously after mutations.
On first start after this update, legacy JSON content in `SharedPreferences` is validated, copied transactionally
into Room with IDs and list ordering preserved, read back for verification, and only then marked migrated.
A JSON backup is retained in preferences for recovery. Duplicate or malformed legacy records are not imported.

SharedPreferences remains intentionally responsible for lightweight settings and compatibility data: theme,
reader preferences, home order/visibility, reading positions, notification settings, and the legacy migration
backup. `HomeFragment`, `LibraryFragment`, and `MyAzkarFragment` collect repository `Flow` through lifecycle-
aware ViewModels. Other screens retain the Store facade for cache-only reads and compatibility mutations while
they are migrated incrementally.

Application, widget, and reminder entry points call `Store.init()` without blocking and await its single
background initialization gate before reading content. Initialization loads Room, validates and imports legacy
JSON, applies defaults and schedule migrations in order, waits for queued transactions, and surfaces failures
through logging or the foreground startup path. Widget broadcasts and reminder receivers use `goAsync()` and
IO coroutines so launcher and alarm callbacks do not perform blocking database work.

Compatibility writes are serialized through the repository's IO queue. Store keeps a generation number for
optimistic snapshots: failed writes return an explicit failure result and roll back only when no newer mutation
has superseded them. Widget refreshes are coalesced and requested after persistence or rollback, so an
uncommitted optimistic state is not rendered as durable content.

## Design

The UI uses the original design values, vector icons, and bundled DM Sans and Amiri fonts. The OLED palette uses
true black backgrounds to reduce power use on OLED displays.
