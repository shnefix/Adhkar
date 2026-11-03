# Asynchronous database migration audit

## Baseline

The Room database is the durable source of truth for folders, azkar, and
schedules. The repository now uses suspend DAO methods and a serialized IO
transaction path. Its compatibility reads are immutable in-memory snapshots;
they no longer query Room or block the caller.

## Synchronous operations

| Method | Operation | Current callers | Main-thread risk | Priority |
| --- | --- | --- | --- | --- |
| `AzkarRepository.loadContent`, `replaceAllAsync` | One-time read and transaction | Initialization, import, ViewModels | No main-thread blocking | Migrated |
| `AzkarRepository` snapshot compatibility methods | In-memory reads/queued writes | Store integrations and unmigrated screens | No Room blocking; failures are logged | Compatibility |
| `Store.init` and migration/default-sync helpers | Parse, suspend Room reads/writes, migration | App, activity, widget/reminder entry points | Runs on IO and is awaited by consumers | Migrated |
| `Store` content methods (`addZikr`, folder/schedule mutations) | Snapshot update plus queued Room write | UI fragments and editor callbacks | No main-thread Room blocking | Compatibility |

`Store` cache-only reads (`folders`, `zikrs`, `readerItems`, counts derived
from cache) do not query Room, but consumers await the initialization gate
before using them. SharedPreferences operations remain synchronous by design
and are outside this Room migration.

## Main-thread call sites

- `AzkarApp.onCreate` and `MainActivity.onCreate` call non-blocking `Store.init`.
- `HomeFragment`, `LibraryFragment`, `MyAzkarFragment`, `FolderFragment`, and
  `EditFolderFragment` collect Room Flow through ViewModels (`FolderViewModel`
  combines it with `Store.hiddenZikrsFlow`).
- `AddZikrFragment` and `EditZikrFragment` write through `ZikrEditorViewModel`.
- Customization and import screens still use Store snapshots during
  interaction and invoke Store mutations from click/activity-result callbacks;
  those reads do not access Room.
- Reader, theme, and notification preferences live in `AppPrefs`
  (SharedPreferences only).
- `ReminderScheduler` and widget configuration/rendering intentionally retain
  synchronous Store compatibility because they are not lifecycle-bound UI
  screens and must return a complete snapshot to platform callbacks.

## Migration order

1. Add suspend repository reads/mutations and retain compatibility methods for
   widgets, reminders, and the migration bootstrap.
2. Move database-intensive screens to ViewModels that collect repository `Flow`.
3. Migrate additional editor/detail screens incrementally; do not maintain a
   second persistence implementation.
4. Keep compatibility boundaries explicit and measure first-frame and operation
   latency before/after each screen migration.

## Success criteria

- No Room query or transaction is awaited from a UI lifecycle callback on the
  migrated screen.
- Observable content is delivered through `Flow`.
- Existing Store consumers continue to receive complete snapshots.
- Tests cover suspend reads/writes, concurrent serialized mutations, and
  cancellation/lifecycle collection.

## Phase 1 result

- `AzkarRepository.loadContent` and `replaceAllAsync` now provide suspend
  one-time reads and transactional writes on `Dispatchers.IO`; no repository
  method calls `Future.get()`.
- Room `Flow` is collected by `HomeViewModel` and `HomeFragment` while the
  view lifecycle is `STARTED`; the Home screen no longer performs a blocking
  repository read during view creation.
- `Store` synchronous methods remain only as cache/settings compatibility APIs
  for widgets, reminders, and screens not yet migrated. They do not access Room:
  writes update the snapshot and enqueue a serialized IO transaction.
- `HomeFragment`, `LibraryFragment`, and `MyAzkarFragment` now use
  lifecycle-aware ViewModels over the repository Flow.
- `Store.init()` is non-blocking. Application, activity, widgets, and reminders
  await one background initialization gate before reading content.
- No runtime before/after latency benchmark was available in this environment.
  The existing `AzkarPerf` first-frame log remains the measurement hook for a
  device benchmark in the next phase.

## Phase 2 result

- Widget updates and refresh broadcasts use `goAsync()` plus `Dispatchers.IO`;
  reminder and boot receivers use the same pattern.
- Import persistence is now suspend/transactional and the import dialog reports
  success only after the Room write completes.
- Remaining Store-backed detail/editor screens use cache-only reads and
  non-blocking compatibility writes; their direct Flow/ViewModel migration is
  deferred to avoid duplicating editor state and changing behavior.

## Phase 3 result

- Compatibility writes are serialized in `AzkarRepository` through a chained
  IO write queue and a transaction mutex. A failed write is reported through a
  `Result`; a later write is never blocked by an earlier failure.
- `Store` uses generation-aware reconciliation. It restores the previous
  in-memory snapshot only when the failed operation is still the newest
  mutation, so stale failures cannot overwrite newer user changes.
- Widget refreshes are requested only after successful persistence or after a
  failed write has been reconciled. Refresh requests remain coalesced.
- Initialization waits for pending compatibility writes before publishing
  readiness, and initialization failures are surfaced to the foreground
  activity while remaining logged for background entry points.
- Widget and reminder receivers complete every `goAsync()` pending result in
  `finally`, including initialization and rendering failures.
- Production scans found no `Future.get()`, `runBlocking`,
  `allowMainThreadQueries()`, `Thread.sleep`, or generic blocking database
  helper. Test-only `runBlocking` and `allowMainThreadQueries()` remain in
  instrumentation setup.

## Remaining technical debt and risks

- Detail/editor screens still use the Store compatibility facade. Their reads
  are cache-only and their writes are asynchronous, but they do not yet expose
  per-operation UI error state.
- Widget and reminder platform callbacks retain compatibility APIs because they
  are not lifecycle-bound screens. They wait for the initialization gate before
  reading content and do not perform Room calls on the callback thread.
- No before/after runtime performance measurements were possible without a
  connected emulator or device. `AzkarPerf` remains the instrumentation hook
  for cold-start, first-content, import/export, and widget measurements.

## Phase 4 result

- `StoreRollbackTest` now verifies that a failed compatibility write restores
  the cache and leaves Room unchanged.
- The same test verifies that a subsequent newer mutation is retained after
  an older queued write fails, and that the failed write does not poison the
  queue.
- `AzkarRepository.awaitPendingWrites()` now propagates the contained
  `Result` failure. Initialization cannot report readiness after a queued
  persistence failure.
- Instrumentation coverage includes migration, import/export, repository
  concurrency, Flow cancellation, Store rollback, and widget rendering
  tests. The APK compiles successfully; execution requires a connected
  Android device or emulator.
- The final production scan found no `Future.get()`, `runBlocking`,
  `Thread.sleep()`, `allowMainThreadQueries()`, or generic blocking database
  helper. Runtime performance numbers remain unavailable without a device.
