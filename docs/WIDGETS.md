# Azkar home-screen widgets: the full catalog

This file lists every home-screen widget in the app. Each widget has its own section, and every section uses the same fields. You can read it top to bottom, or search for a widget ID (e.g. `B_SMALL`) or a class name.

- **Total:** 35 widgets: 3 classic, 12 design widgets, 6 tracking widgets and 14 black & yellow widgets (section 6).
- **Source of truth:** the code paths in [Where the code lives](#where-the-code-lives). If this file and the code disagree, the code is right.
- **Units:** sizes are in dp, and grid cells are launcher cells (columns × rows).

---

## 1. Summary table

| # | ID | English label | Arabic label | Group | Cells | Design size (dp) | Tap does |
|---|----|---------------|--------------|-------|-------|------------------|----------|
| 1 | `SMALL` | Adhkar · Small | أذكار · صغير | Classic | 2×2 | 158×158 | Set by the user (default: +1) |
| 2 | `MEDIUM` | Adhkar · Wide | أذكار · عريض | Classic | 4×2 | 338×158 | Set by the user (default: +1) |
| 3 | `LARGE` | Adhkar · Large | أذكار · كبير | Classic | 4×4 | 338×354 | Set by the user (default: +1) |
| 4 | `B_SMALL` | Azkar tap disc small | قرص الأذكار الصغير | Design: tap disc | 2×2 | 158×158 | +1 |
| 5 | `B_MEDIUM` | Azkar tap disc medium | قرص الأذكار المتوسط | Design: tap disc | 4×2 | 338×158 | +1 |
| 6 | `D_SMALL` | Azkar morning and evening small | أذكار الصباح والمساء الصغير | Design: morning & evening | 2×2 | 158×158 | +1 |
| 7 | `D_MEDIUM` | Azkar morning and evening medium | أذكار الصباح والمساء المتوسط | Design: morning & evening | 4×2 | 338×158 | +1 |
| 8 | `D_LARGE` | Azkar morning and evening large | أذكار الصباح والمساء الكبير | Design: morning & evening | 4×4 | 338×354 | +1 |
| 9 | `H_SMALL` | Azkar beads small | خرز الأذكار الصغير | Design: bead string | 2×2 | 158×158 | +1 |
| 10 | `H_MEDIUM` | Azkar beads medium | خرز الأذكار المتوسط | Design: bead string | 4×2 | 338×158 | +1 |
| 11 | `H_LARGE` | Azkar beads large | خرز الأذكار الكبير | Design: bead string | 4×4 | 338×354 | +1, plus a −1 button |
| 12 | `J_STRIP_DHIKR` | Azkar dhikr strip | شريط الذكر | Design: strip | 4×1 | 338×74 | +1 |
| 13 | `J_STRIP_PROGRESS` | Azkar progress strip | شريط تقدم الأذكار | Design: strip | 4×1 | 338×74 | +1, or next zikr on morning/evening folders |
| 14 | `J_TALL_RING` | Azkar tall ring | حلقة الأذكار الطويلة | Design: tall | 2×4 | 158×354 | +1 |
| 15 | `J_TALL_TIMELINE` | Azkar tall timeline | خط أذكار زمني | Design: tall | 2×4 | 158×354 | +1 |
| 16 | `C_STREAK_SMALL` | Azkar streak small | مواظبة الأذكار صغيرة | Tracking | 2×2 | 158×158 | Opens the app |
| 17 | `C_STREAK_MEDIUM` | Azkar streak medium | مواظبة الأذكار متوسطة | Tracking | 4×2 | 338×158 | Opens the app |
| 18 | `C_STREAK_LARGE` | Azkar streak large | مواظبة الأذكار كبيرة | Tracking | 4×4 | 338×354 | Opens the app |
| 19 | `G_COUNTDOWN_SMALL` | Azkar countdown small | العد التنازلي للأذكار | Tracking | 2×2 | 158×158 | Opens the app |
| 20 | `F_BENTO_SMALL` | Azkar bento small | ملخص الأذكار صغير | Tracking | 2×2 | 158×158 | Opens the app |
| 21 | `T_TODAY_MEDIUM` | Azkar today medium | يوم الأذكار متوسط | Tracking | 4×2 | 338×158 | Opens the app |

**Minimum sizes by cell count:** 2×2 = 110×110 dp, 4×2 = 250×110 dp, 4×4 = 250×250 dp, 4×1 = 250×74 dp, 2×4 = 110×250 dp. Every widget can be resized both horizontally and vertically.

---

## 2. Rules every widget follows

### Drawing
- Each widget is drawn as **one bitmap** on a `Canvas`, then shown in an `ImageView` inside `RemoteViews`. The layout XML only holds the image and invisible tap areas.
- The bitmap is drawn at the widget's real size on the launcher, with the design size as a fallback. Rotating the phone uses the landscape width and height. Large bitmaps are scaled down so they stay within the `RemoteViews` size limit.
- Arabic text uses `StaticLayout` (right to left) and shrinks until it fits its box. Letter spacing is turned off when the UI language is Arabic, because it breaks the joins between letters.
- **Fonts:** Amiri (bold and regular) for Arabic, DM Sans (bold and regular) for Latin text and numbers.
- **Empty folder:** shows "No azkar in this folder" / "Tap to open Azkar".
- **Tracking off** (tracking widgets only): shows "Tracking is off" / "Turn it on in Settings".

### Colour palette (design and tracking widgets, OLED black)

| Name | Hex | Used for |
|------|-----|----------|
| gold | `#E3C06E` | Accent, progress, filled beads, the +1 button |
| gold_soft | `#EBCB82` | Large Arabic text |
| gold_mid | `#8A743A` | Calendar "mid" level |
| gold_halo | `#4A3F22` | Halo around the focused bead, session windows, calendar "low" level |
| cream | `#F7F3E8` | Main text |
| muted | `#A9A699` | Captions |
| track | `#262626` | Empty part of bars and rings |
| bead_off | `#2E2E2E` | Beads not yet counted |
| outline | `#3A3A3A` | Pending dots |
| card_border | `#232323` | Widget border |
| chip | `#1A1A1A` | Chip background |
| inner | `#111111` | Inner discs and tiles |
| inner_border | `#1F1F1F` | Tile borders, future calendar days |
| missed | `#141414` | Missed calendar days |
| ink | `#000000` | Background, text drawn on gold |

### How counting works (`WidgetLogic`)
- **+1 (`ACTION_COUNT`):** adds one recitation to the current zikr. When the zikr reaches its target, the widget moves to the next one. The tap after the last zikr starts the folder again.
- **−1 (`ACTION_UNDO`):** takes one recitation back, stepping back to the previous zikr if needed.
- **Next (`ACTION_NEXT`):** skips to the next zikr and stops at the end of the folder.
- **Step (`ACTION_STEP`):** counts the whole current zikr as done and shows the next one, then starts the folder again after the last.
- **Reset (`ACTION_RESET`):** goes back to the start of the folder.
- Progress is shared: other widgets on the same folder move with it. For folders that remember their position, the reader resumes where the widget stopped. Taps on a tracked folder are recorded for the streak.
- Taps that change counts go to `WidgetActionReceiver`, which is not exported and handles one tap at a time. The widget providers are exported (the launcher needs this) and only accept redraws.

### Refreshing
- Widgets redraw at midnight, when the time or time zone changes, when they are resized, and when the app closes (`WidgetUpdater.updateAll`).
- There is also a repeating refresh every 60 minutes by default (`Appearance.refreshMinutes`). Countdown, bento and today widgets refresh every **15 minutes**.
- Tracking widgets also set an alarm for the next session start or end, so "now" and "next" change on time.

### Which folder a widget shows
- **Classic widgets:** chosen in `WidgetConfigActivity` when the widget is placed. They can be reconfigured later.
- **Design widgets:** set in the app's widget editor. The default is the first folder.
- **Tracking widgets:** follow the tracked folders. They are **disabled in the manifest** and only enabled while at least one folder is tracked (`NewWidgetUpdater.syncTrackingProviders`). Disabling them removes any that are placed.

---

## 3. Classic widgets (configurable style)

These three providers draw one of four **styles**, picked in the config screen. Not every style is offered at every size:

| Style | Arabic | Sizes it can draw | Sizes offered for new widgets |
|-------|--------|-------------------|-------------------------------|
| `CLASSIC` | كلاسيكي | Small, Medium, Large | Small |
| `CHECKLIST` | قائمة | Medium, Large | Medium |
| `TASBIH` | حبات التسبيح | Small, Large | Small, Large |
| `CALLIGRAPHY` | خط عربي | Small, Medium | Small, Medium |

**Tap options** (`Appearance.tapAction`): `INCREMENT` (default), `NEXT_ZIKR`, `OPEN_APP`, `OPEN_READER`. Other appearance options: font size (28 sp), line height (1.5), alignment, text and background colour, corner radius (22 dp), show counter, and refresh interval.

### 3.1 `SMALL`: Adhkar · Small
- **Provider:** `SmallWidgetProvider` · **Info:** `xml/widget_small_info.xml` · **Layout:** `widget_plain`
- **Cells:** 2×2 (min 110×110 dp)
- **Classic style:** the folder name in gold capitals with a moon icon, the current zikr in Arabic, a progress bar and "count / target".
- **Tasbih style:** a ring of up to 33 beads with the count in the middle and "of N" under it.
- **Calligraphy style:** a "DHIKR" eyebrow, a large Arabic zikr and the count at the bottom.

### 3.2 `MEDIUM`: Adhkar · Wide
- **Provider:** `MediumWidgetProvider` · **Info:** `xml/widget_medium_info.xml` · **Layout:** `widget_plain`
- **Cells:** 4×2 (min 250×110 dp)
- **Classic style:** "FOLDER · X OF N", the Arabic zikr on the left, and a progress ring with the count on the right.
- **Checklist style:** "FOLDER AZKAR" with done/total, and three rows (the previous, current and next zikr). Each row has a check, ring or empty circle. The current row is highlighted and shows count/target.
- **Calligraphy style:** "FOLDER · X OF N", a large Arabic zikr, and segments (or a bar, for more than 24 azkar) along the bottom.

### 3.3 `LARGE`: Adhkar · Large
- **Provider:** `LargeWidgetProvider` · **Info:** `xml/widget_large_info.xml`
- **Layout:** `widget_classic_large` (Classic), `widget_tasbih_large` (Tasbih), otherwise `widget_plain`
- **Cells:** 4×4 (min 250×250 dp)
- **Classic style:** a header with a position chip, the Arabic zikr in a panel, a small ring with the count, and a button (↺ once the folder is finished). The button and a second area both count.
- **Checklist style:** the folder name, "N azkar", done/total, a progress bar, and a scrolling list of rows that keeps the current zikr in the middle.
- **Tasbih style:** "Tasbih" and "Round r of R · total", a count chip, a large bead ring with the Arabic zikr inside, a **Reset** button and a main count button.

---

## 4. Design widgets (OLED, fixed design)

They all use `BaseAzkarWidgetProvider` with an `AzkarWidgetFamily` value and are drawn by `NewWidgetRenderer`. A tap anywhere sends **+1**, except where noted.

### 4.1 `B_SMALL`: Azkar tap disc small
- **Provider:** `BSmallWidgetProvider` · **Info:** `xml/azkar_widget_b_small_info.xml` · **Layout:** `azkar_widget_b_small`
- **Cells:** 2×2 · **Design:** 158×158 dp
- **Shows:** a progress ring around a dark disc, the count in large numbers, and "TAP" in gold under it.

### 4.2 `B_MEDIUM`: Azkar tap disc medium
- **Provider:** `BMediumWidgetProvider` · **Info:** `xml/azkar_widget_b_medium_info.xml` · **Layout:** `azkar_widget_b_medium`
- **Cells:** 4×2 · **Design:** 338×158 dp
- **Shows:** a disc on the left with the count and "of N". On the right: "Folder · X of N", the current zikr in Arabic, and "Next: <label>".

### 4.3 `D_SMALL`: Azkar morning and evening small
- **Provider:** `DSmallWidgetProvider` · **Info:** `xml/azkar_widget_d_small_info.xml` · **Layout:** `azkar_widget_d_small`
- **Cells:** 2×2 · **Design:** 158×158 dp
- **Shows:** "Today", then two rings side by side for **Morning** and **Evening**. Each ring shows that folder's progress today, with the number done in the middle (a check once finished).
- **Data:** the morning and evening folders are found by ID, so they still work after a rename or translation. Progress counts what was read anywhere today, in the reader or on any widget.

### 4.4 `D_MEDIUM`: Azkar morning and evening medium
- **Provider:** `DMediumWidgetProvider` · **Info:** `xml/azkar_widget_d_medium_info.xml` · **Layout:** `azkar_widget_d_medium`
- **Cells:** 4×2 · **Design:** 338×158 dp
- **Shows:** two cards, Morning and Evening, each with "done / N", a progress bar and "Next: <label>" (or "Completed"). The session happening now is highlighted in gold and marked "Now".
- **Current session:** taken from the prayer-time windows when a location is known, otherwise from the reminder times (`AzkarSessionClock`).

### 4.5 `D_LARGE`: Azkar morning and evening large
- **Provider:** `DLargeWidgetProvider` · **Info:** `xml/azkar_widget_d_large_info.xml` · **Layout:** `azkar_widget_d_large`
- **Cells:** 4×4 · **Design:** 338×354 dp
- **Shows:** two stacked cards, "Morning azkar" and "Evening azkar". Each has a "done / N" chip (or ✓ Done), the current zikr in Arabic, a progress bar and "X of N recited". The current session is highlighted.

### 4.6 `H_SMALL`: Azkar beads small
- **Provider:** `HSmallWidgetProvider` · **Info:** `xml/azkar_widget_h_small_info.xml` · **Layout:** `azkar_widget_h_small`
- **Cells:** 2×2 · **Design:** 158×158 dp
- **Shows:** "Tasbih", three beads on a string with a large gold bead in the middle holding the count, and "of N · tap" at the bottom.
- **Beads:** one round is up to 33 beads. Targets above 33 are counted in rounds.

### 4.7 `H_MEDIUM`: Azkar beads medium
- **Provider:** `HMediumWidgetProvider` · **Info:** `xml/azkar_widget_h_medium_info.xml` · **Layout:** `azkar_widget_h_medium`
- **Cells:** 4×2 · **Design:** 338×158 dp
- **Shows:** "Tasbih · Round r of R" with count/target, a string of 7 beads that grow towards the focused gold bead (which holds the count), and the Arabic zikr at the bottom.

### 4.8 `H_LARGE`: Azkar beads large
- **Provider:** `HLargeWidgetProvider` · **Info:** `xml/azkar_widget_h_large_info.xml` · **Layout:** `azkar_widget_h_large`
- **Cells:** 4×4 · **Design:** 338×354 dp
- **Shows:** a header ("Tasbih", "Folder · X of N", and a count/target chip), a 9-bead string, the Arabic zikr in large gold text, "Round r of R · N beads each", round segments (up to 8), and two buttons: **−1** and **+1**.
- **Tap:** anywhere sends +1. The bottom-left area (56×44 dp, 16 dp from the edges) sends **−1** (`ACTION_UNDO`).

### 4.9 `J_STRIP_DHIKR`: Azkar dhikr strip
- **Provider:** `JStripDhikrWidgetProvider` · **Info:** `xml/azkar_widget_j_strip_dhikr_info.xml` · **Layout:** `azkar_widget_j_strip_dhikr`
- **Cells:** 4×1 · **Design:** 338×74 dp
- **Shows:** one row: the current zikr in Arabic and "count / target" on the right. There is no button: a tap anywhere sends +1.

### 4.10 `J_STRIP_PROGRESS`: Azkar progress strip
- **Provider:** `JStripProgressWidgetProvider` · **Info:** `xml/azkar_widget_j_strip_progress_info.xml` · **Layout:** `azkar_widget_j_strip_progress`
- **Cells:** 4×1 · **Design:** 338×74 dp
- **Shows:** "<Folder> azkar" and "X of N" above a progress bar for the whole folder.
- **Tap:** on the morning and evening folders, a tap moves to the next zikr (`ACTION_STEP`), because the strip has no repeat count. On other folders a tap sends +1.

### 4.11 `J_TALL_RING`: Azkar tall ring
- **Provider:** `JTallRingWidgetProvider` · **Info:** `xml/azkar_widget_j_tall_ring_info.xml` · **Layout:** `azkar_widget_j_tall_ring`
- **Cells:** 2×4 · **Design:** 158×354 dp
- **Shows:** from top to bottom: the folder name, a progress ring with the count and "of N", the Arabic zikr in gold, "Next: <label>", and a full-width **+1** button.

### 4.12 `J_TALL_TIMELINE`: Azkar tall timeline
- **Provider:** `JTallTimelineWidgetProvider` · **Info:** `xml/azkar_widget_j_tall_timeline_info.xml` · **Layout:** `azkar_widget_j_tall_timeline`
- **Cells:** 2×4 · **Design:** 158×354 dp
- **Shows:** the folder name and "X/N", then a vertical timeline of azkar with the current one kept in the middle. Done azkar have gold dots and muted text. The current zikr has a ringed dot and larger gold text. Pending azkar have outlined dots. The line is gold up to the current zikr.

---

## 5. Tracking widgets (streaks and sessions)

They all use the shared layout `azkar_widget_tracking`, are drawn from a `TrackingModel`, and are only offered while a folder is tracked. **A tap opens the app** on the session they show, or on the first tracked folder. They don't count.

The `TrackingModel` fields they use:
- current `streak` and `best`
- `today` progress
- `week`: the last 7 days
- `grid`: 5 weeks, starting on the locale's first day of the week
- `sessions`: today's morning and evening windows
- `focus`: the session running now, otherwise the next one
- `dhikr`: where the reader stopped, or the first zikr

### 5.1 `C_STREAK_SMALL`: Azkar streak small
- **Provider:** `CStreakSmallWidgetProvider` · **Info:** `xml/azkar_widget_c_streak_small_info.xml`
- **Cells:** 2×2 · **Design:** 158×158 dp
- **Shows:** "Streak", a large number with "days", and 7 dots for the last week. Finished days are gold, and today is a gold ring until it is finished.

### 5.2 `C_STREAK_MEDIUM`: Azkar streak medium
- **Provider:** `CStreakMediumWidgetProvider` · **Info:** `xml/azkar_widget_c_streak_medium_info.xml`
- **Cells:** 4×2 · **Design:** 338×158 dp
- **Shows:** "Streak · N days" and "Best N", then 7 weekday circles with their initials. Finished days are gold with a check, today has a gold ring and dot, and other days are outlined.

### 5.3 `C_STREAK_LARGE`: Azkar streak large
- **Provider:** `CStreakLargeWidgetProvider` · **Info:** `xml/azkar_widget_c_streak_large_info.xml`
- **Cells:** 4×4 · **Design:** 338×354 dp
- **Shows:** a "Streak" header with the date range and an "N days" chip, a 5-week calendar with weekday headers, and a legend at the bottom ("Best streak N days", then less → more swatches).
- **Day colours:** gold (FULL), gold_mid (MID), gold_halo (LOW), missed (MISSED). Future days are outlined, and today has a gold border.

### 5.4 `G_COUNTDOWN_SMALL`: Azkar countdown small
- **Provider:** `GCountdownSmallWidgetProvider` · **Info:** `xml/azkar_widget_g_countdown_small_info.xml`
- **Cells:** 2×2 · **Design:** 158×158 dp · **Refresh:** every 15 min, plus at each session boundary
- **Shows:** a large `H:MM` countdown.
  - **During a session:** "<Session> · Now", "hours, minutes left", and "<Session> ends at <time>".
  - **Otherwise:** "Next azkar", "hours, minutes", and "<Session> · <start time>".

### 5.5 `F_BENTO_SMALL`: Azkar bento small
- **Provider:** `FBentoSmallWidgetProvider` · **Info:** `xml/azkar_widget_f_bento_small_info.xml`
- **Cells:** 2×2 · **Design:** 158×158 dp · **Refresh:** every 15 min
- **Shows:** four tiles in a 2×2 grid:
  1. Today's progress in %, on a gold tile.
  2. The main folder's done/total.
  3. The day streak.
  4. Time left in the current session or time until the next one ("2h", "35m"). With no session, this tile shows the best streak.

### 5.6 `T_TODAY_MEDIUM`: Azkar today medium
- **Provider:** `TTodayMediumWidgetProvider` · **Info:** `xml/azkar_widget_t_today_medium_info.xml`
- **Cells:** 4×2 · **Design:** 338×158 dp · **Refresh:** every 15 min
- **Shows:** "Today" with "X left" or "<Session> starts in X" on the right. Under it, a bar running from midnight to midnight, with dim windows for each session, gold up to now, and a dot at the current time. Session labels with start times sit under the bar. No zikr text is shown.

---

## 6. Black & yellow widgets

Drawn by `GoldWidgetRenderer` in a 206 px = 158 dp mock-up grid, scaled to the widget. `tap` and `openFolder` in `AzkarWidgetFamily` decide what a tap does.

| ID | Size | Shows | Tap does |
|----|------|-------|----------|
| `K_RINGS_SMALL` / `K_RINGS_MEDIUM` | 2×2 / 4×2 | Rings for morning, evening and the widget's folder | Opens the current session's folder |
| `A_DIAL_SMALL` / `A_DIAL_MEDIUM` | 2×2 / 4×2 | A 24-hour dial with the azkar times (medium: the times and time left) | Opens the current session's folder |
| `P_AFTER_MEDIUM` | 4×2 | Folder name, "done / total" chip, three step bars, the current zikr and "X of N". No +1 button | +1 |
| `P_AFTER_LARGE` | 4×4 | The previous, current and next steps | +1 |
| `I_COUNTERS_LARGE` | 4×4 | Four counters for the first azkar of the folder | Each counter counts its own zikr |
| `N_TALL_STACK` | 2×4 | Morning, evening and the widget's folder stacked | Opens the current session's folder |
| `U_FRIDAY_MEDIUM` | 4×2 | Surat al-Kahf and a folder to count | One area opens al-Kahf, the other sends +1 |
| `Z6_MORNING_SMALL` | 2×2 | Gold morning card | Opens the morning azkar |
| `G5_STAR_SMALL` | 2×2 | Eight-point star | Opens the widget's folder (`WIDGET_FOLDER`) |
| `G_ROSETTE_SMALL` | 2×2 | Rosette with «الله» | Opens the widget's folder (`WIDGET_FOLDER`) |
| `M8_SUNRISE` ("Morning & evening medium") | 4×2 | During the morning window, the sunrise design. During the evening window, the moon design. Otherwise, the tahlil design | Morning or evening: opens that folder. Otherwise: opens the app, and the corner button shares |
| `M7_TAHLIL_FULL` | 4×2 | «لا إله إلا الله وحده لا شريك له» | Opens the app, and the corner button shares |

- **Morning & evening widget:** the windows come from `AzkarSessionClock.windows` (prayer times when a location is set, else the reminder time plus 4 hours). `AzkarSessionClock.pick` chooses the design, and the widget sets an alarm for `AzkarSessionClock.boundary` so it switches on time. It replaced the separate moon widget (`M8_MOON`, removed in this version).
- **Picker order** (`WidgetPickerDialog`): small widgets list Classic, Calligraphy, Tasbih, then the families in enum order, except that the tap disc (`B_SMALL`) comes before morning and evening (`D_SMALL`). See `pickerStyles` and `pickerFamilies`.

---

## 7. Where the code lives

All paths are relative to `app/src/main/`.

| What | File |
|------|------|
| Classic providers, `WidgetUpdater`, action constants | `java/com/azkar/app/widgets/AzkarWidgetProviders.kt` |
| `AzkarWidgetFamily` enum, `BaseAzkarWidgetProvider`, `NewWidgetUpdater` | `java/com/azkar/app/widgets/NewWidgetProviders.kt` |
| One-line design providers (B, D, H, J) | `java/com/azkar/app/widgets/{B,D,H,J…}WidgetProvider.kt` |
| Tracking providers (C, G, F, T) | `java/com/azkar/app/widgets/TrackingWidgetProviders.kt` |
| Classic renderer (4 styles) | `java/com/azkar/app/widgets/WidgetRenderer.kt` |
| Design and tracking renderer, `AzkarWidgetSizes`, `AzkarSessionClock` | `java/com/azkar/app/widgets/render/NewWidgetRenderer.kt` |
| Black & yellow renderer | `java/com/azkar/app/widgets/render/GoldWidgetRenderer.kt` |
| Tests | `src/test/.../widgets/WidgetChangesTest.kt`, `src/androidTest/.../widgets/WidgetFamiliesRenderTest.kt` |
| `WidgetModel`, `WidgetKind`, `WidgetStyle` | `java/com/azkar/app/widgets/WidgetModels.kt` |
| `TrackingModel` | `java/com/azkar/app/widgets/TrackingModel.kt` |
| Config, appearance, progress storage, `WidgetLogic` (tap rules) | `java/com/azkar/app/widgets/WidgetStore.kt` |
| Tap handling | `java/com/azkar/app/widgets/WidgetActionReceiver.kt` |
| Midnight and time-change redraws | `java/com/azkar/app/widgets/WidgetClockReceiver.kt` |
| Pinning from inside the app | `java/com/azkar/app/widgets/WidgetPinReceiver.kt` |
| Classic config screen | `java/com/azkar/app/widgets/WidgetConfigActivity.kt` |
| Provider info (size, resize, preview) | `res/xml/*widget*_info.xml` |
| Layouts (image and tap areas) | `res/layout/azkar_widget_*.xml`, `widget_plain.xml`, `widget_classic_large.xml`, `widget_tasbih_large.xml` |
| Labels and descriptions | `res/values/strings.xml`, `res/values-ar/strings.xml` |
| Colours | `res/values/colors.xml` (`azkar_widget_*`) |
| Manifest receivers | `AndroidManifest.xml` |
