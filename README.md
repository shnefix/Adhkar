<p align="center">
  <img src="docs/images/logo.svg" width="120" alt="Adhkar logo">
</p>

<h1 align="center">Adhkar</h1>

<p align="center">
  A calm, offline Android app for reading, counting and organising your daily adhkar.<br>
  <b>Free and open source. No login, no subscription, no ads.</b>
</p>

## Why Adhkar

Most azkar apps need an account, show ads, or send data to a server. Adhkar does none of that. It has
no internet permission at all, so it can't track you or send anything anywhere. Your azkar, folders and
progress stay on your phone.

It comes with the common daily azkar, and you can add your own: write any zikr or dua, choose how
many times to repeat it, and organise it into your own folders.

## Screenshots

| Home | Library | My azkar |
| :---: | :---: | :---: |
| <img src="docs/images/home.png" width="250" alt="Home"> | <img src="docs/images/library.png" width="250" alt="Library"> | <img src="docs/images/my-azkar.png" width="250" alt="My azkar"> |
| **Reader** | **Settings & reminders** | **Customize** |
| <img src="docs/images/reader.png" width="250" alt="Reader"> | <img src="docs/images/reminders.png" width="250" alt="Settings and reminders"> | <img src="docs/images/customize.png" width="250" alt="Customize"> |

## Features

- **Add your own azkar**: write any zikr or dua, set its repeat count, and put it in your own folders;
  edit, reorder or hide any folder, including the built-in ones
- Morning, evening, before-sleep and other azkar collections built in
- Focused Arabic reader with tap-to-advance, counters, notes, adjustable text size and keep-screen-on
- Reminders for morning, evening and before sleep; they can follow prayer times for your approximate
  location (optional), with a choice of calculation method (Egyptian, Umm al-Qura, MWL, ISNA and more)
  and the standard or Hanafi Asr time
- Optional tracking of the days you finish your morning and evening azkar, with streaks
- Home-screen widgets in many sizes and styles, including tap-to-count
- Import and export your azkar as a JSON file
- Full Arabic interface on Arabic phones (RTL), OLED black theme, bundled Amiri and DM Sans fonts

## Install

1. Open the [Releases page](../../releases) and download the latest `Adhkar-<version>.apk`.
2. Open the file on your phone. Android will ask you to allow installing apps from your browser or
   file manager; allow it for this install.
3. Requires Android 8.0 or newer.

## Privacy

| | |
| --- | --- |
| Internet access | **None.** The app does not request the `INTERNET` permission, so it cannot send or receive data. |
| Ads, analytics, tracking | None. No third-party SDKs for ads, analytics or crash reporting. |
| Account | None needed. |
| Location | Optional, approximate only (`ACCESS_COARSE_LOCATION`). Used on the phone to calculate prayer times for reminders. Deny it and fixed times are used. |
| Notifications | Only for the reminders you turn on. |
| Your data | Stored on the phone only. If you turn on Android's own backup, Android may include the app's data (azkar, settings, last approximate location) in your Google backup. |

Other permissions: `RECEIVE_BOOT_COMPLETED` (to restore reminders after a restart) and
`SCHEDULE_EXACT_ALARM` (to deliver reminders on time).

## Build from source

Requirements: JDK 17 and the Android SDK (Android Studio includes both).

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/Adhkar-<version>-debug.apk
./gradlew assembleRelease    # app/build/outputs/apk/release/Adhkar-<version>.apk
```

On Windows use `gradlew.bat`. Without a `keystore.properties` file the release APK is unsigned. To sign it,
copy `keystore.properties.example` to `keystore.properties` and fill in your own key; that file and any
`.jks` key are git-ignored.

## Tests

```bash
./gradlew testDebugUnitTest          # unit tests (prayer times, reminders, reader, import, tracking, widgets)
./gradlew lintDebug                  # Android lint
./gradlew connectedDebugAndroidTest  # instrumented tests on a device or emulator (database, import/export, widgets)
```

Note: `connectedDebugAndroidTest` uninstalls the app from the device afterwards, which deletes its data.
CI runs the unit tests and lint on every push, and the instrumented tests on an emulator.

## Releasing

1. Raise `versionCode` and set `versionName` in `app/build.gradle.kts`, then commit.
2. Push a tag named after the version, for example `git tag v1.1 && git push origin v1.1`.

The Release workflow builds the APK, checks it is signed with the release key, and publishes it as a
GitHub release. It needs four repository secrets: `RELEASE_KEYSTORE_BASE64` (the `.jks` file,
base64-encoded), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`.

## Contributing

Issues and pull requests are welcome. Please keep the app offline and free of tracking, run the unit
tests and lint before opening a pull request, and describe how you tested your change.

## License

[MIT](LICENSE) © 2026 Adhkar
