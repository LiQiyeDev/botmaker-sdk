# ROADMAP

A running history of features and refactors for future Claude Code sessions. **Append here whenever
you add a feature or refactor** (this is required — see `CLAUDE.md` › Planning).

Format: newest first. Each dated entry has a **Done** list (what shipped) and, when relevant, updates
to **Deferred / next** (intentionally left for later, with enough context to pick up cold).

---

## 2026-07-08 — Telemetry carries the bot source line

**Done**
- `internal/observe/IpcObserver` now stamps each `Match`/`Click` telemetry event with the bot's source line
  (`botLine()` walks the current stack for the first non-`com.botmaker.*`/non-JDK frame — the user's bot
  class). Uses the shared wire v2 field (see `../botmaker-shared/ROADMAP.md`). Enables the Studio's
  running-block highlight on a plain run. Additive; a bot with no Studio attached still registers no observer.

## 2026-07-07 — Per-monitor CaptureSource (`Screen.at`)

**Done**
- **`Screen.at(int index)`** — a new public `CaptureSource` for a single monitor (0-based index into the OS
  screen-device list), so a bot can match against just one screen on a multi-monitor desktop instead of the
  whole virtual desktop (`CaptureSource.screen()` / `Screen.asSource()`). `origin()` is the monitor's
  top-left in virtual-screen space, so in-image matches convert to absolute clickable coordinates as usual.
- **`internal/capture/ScreenCapture`** gained `monitorBounds(int)` (AWT device bounds, falls back to the
  virtual desktop for an out-of-range index) and `captureMonitor(int)` (crops the single `captureDesktop()`
  grab to those bounds — one backend selection, no second capture path).
- Consumed by the Studio's generated `BotConfig.screen(index)` helper (capture-source picker → block code).
  Additive only; no existing public signature changed. Released bots need this SDK version for a
  specific-screen block; local dev picks it up via `./dev-install.sh`.

## 2026-07-07 — Observer SPI + Studio telemetry bridge

**Done**
- **New public, Studio-agnostic observability SPI** (`com.botmaker.sdk.api.observe`): `BotObserver`
  (default no-op `onMatch`/`onClick`), event records `MatchEvent`/`ClickEvent`, a `Surface` target
  (window title+bounds, or screen; `Surface.of(CaptureSource)` resolves it), and the `Bots` registry
  (copy-on-write `add/removeObserver`, `hasObservers`, `fireMatch`/`fireClick`). A first-class feature
  usable standalone — log actions, assert on them in tests, drive custom tooling. `Bots` is the one bit
  of static facade state (a deliberate, documented exception to the stateless-dispatcher style).
- **Emit sites** in the vision/interaction facades, each guarded by `Bots.hasObservers()` so a normal run
  builds/pays nothing: `ImageFinder.find(...)` (found + not-found), private `compare(...)`, `findAll(...)`
  (one event per match, or a not-found), and `ImageClicker.click`/`clickResult`/`clickAll` (left-click).
- **Internal, env-gated Studio bridge** `internal/observe/IpcObserver`: translates SDK events →
  `com.botmaker.shared.ipc.TelemetryEvent` and ships them via `TelemetryClient.fromEnvironment()`. It
  self-installs only when `BM_IPC_PORT` is set (Studio-launched). `Bots` loads it *by name* in a static
  block, so the public API keeps zero compile-time dependency on the bridge/socket — a bot never needs
  the Studio, and outside it no observer registers and no socket opens.
- Tests: `api/observe/BotsTest` (registration, fan-out, error isolation, `Surface`). Real end-to-end
  (find→click overlays) is validated with the Studio's stub bot.
- **`dev-install.sh` now routes a local SDK build to the local `botmaker-shared` build.** It rewrites
  `botmaker.shared.version` → `0.0.0-SNAPSHOT` in the temporary pom (restored on exit), alongside the
  existing groupId/version rewrites. Without this, once a release pins the property to a real tag (e.g.
  `v0.0.2`) a `local-SNAPSHOT` SDK build silently pulled shared from JitPack, so local shared changes were
  ignored. Studio also now auto-lists the installed `local-SNAPSHOT` in its version dropdown (see Studio
  ROADMAP), so there's nothing to type.

**Deferred / next**
- Optional raw `Mouse.click` (bare point, no template context) click events — left out for now.
- The SDK pom still pins `botmaker.shared.version` to the released tag; the umbrella `release.sh` bumps it
  to the new shared tag that carries the `ipc` package when this ships. For local dev build with
  `-Dbotmaker.shared.version=0.0.0-SNAPSHOT` after `botmaker-shared/dev-install.sh`.

## 2026-07-06 — Group/`Any`/`All` variants for the loop & existence helpers

**Done**
- **Lambda control-flow over an `ImageTemplateGroup`** (`ImageFinder`) — `whileExistsAny`/`ifExistsAny`
  hand the action the first visible match (`Consumer<MatchResult>`, first-match via `find(group)`);
  `whileExistsAll`/`ifExistsAll`/`untilExistsAny`/`untilExistsAll` take a `Runnable` ("all present" /
  "waiting" has no single meaningful match, mirroring `untilExists`). All are one-capture-per-check
  like the single-template originals.
- **Group/`All` existence booleans** — `exists(ImageTemplateGroup)` (any, first-match),
  `existsAll(ImageTemplate...)` and `existsAll(ImageTemplateGroup)` (every one visible; empty input is
  false), `notExists(ImageTemplateGroup)`. Complements the pre-existing `existsAny(...)`.
- **`findCompare(good, bad)` Javadoc** now documents `@param good`/`@param bad` (Studio surfaces these
  as argument labels + the "learn about it" description).
- All additive — no existing public signature changed. Tests: `ImageFinderGroupTest` (headless-safe
  `existsAll()` empty guard; screen-dependent paths stay in the manual `Main` harness).

**Deferred / next**
- `ImageWaiter.waitForAny/waitForAll/waitUntilGone` group overloads (nice-to-have; `ImageClicker`
  already covers group clicking).

## 2026-07-05 — Multi-template vision: `ImageTemplateGroup`, best-match, compare

**Done**
- **New value type `ImageTemplateGroup`** (`api.vision`) — immutable, non-empty, ordered wrapper
  around `List<ImageTemplate>` with `of(ImageTemplate...)` / `of(List)` factories and `toArray()`.
  Serves as the first-class multi-template value (Studio detects it as a special type → dedicated
  list picker).
- **`ImageFinder.find(ImageTemplateGroup)`** (+ region/confidence) — first-match over the group
  (delegates to `findAny`; keeps cheap short-circuit). Mirror `ImageClicker.click(group)`.
- **Re-introduced `findBest`/`clickBest`** (previously deleted 2026-07-03) with clearer semantics:
  exhaustive highest-score match. Overloads for a **single `ImageTemplate`** (returns the global
  argmax vs. `find`'s first-acceptable) and for an **`ImageTemplateGroup`** (best score across all
  templates).
- **`findCompare`/`clickCompare`** — a "good" template must out-score similar "bad" variants (other
  in-game states of the same element) **at the same location** by `ClickConfig.DEFAULT_COMPARE_MARGIN`
  (0.05). Overloads: `(good, bad)`, `(good, bad...)`, `(ImageTemplateGroup good, ImageTemplateGroup bad)`
  (+ region/margin). Single-capture: one screenshot, good located via `findBestMatch`, each bad
  re-scored in a padded window at good's location via new internal `OpencvManager.scoreAround(...)`.
- **Tests:** `ImageTemplateGroupTest` (guards/immutability), `ScoreAroundTest` (synthetic
  good-vs-distractor ranking, self-contained — no fixture image needed).

**Note:** the beta status let us add these to the public `api.vision` surface freely.

## 2026-07-03 — Vision API simplification

**Done**
- **Collapsed `api.vision` from 9 classes to 3 action classes** (+ the unchanged value/config types
  `MatchResult`/`ImageTemplate`/`ClickConfig`):
  - **`ImageFinder`** — now owns single-frame lookup *and* existence: `find`/`findAll`/`findAny`, the boolean
    `exists`/`notExists`/`existsAny` (moved from `ImageMatcher`), and lambda control-flow `whileExists` /
    `ifExists` (take `Consumer<MatchResult>` — one capture per check, hands the action the live match) and
    `untilExists` (takes `Runnable`, since no match exists while the template is absent).
  - **`ImageClicker`** — trimmed to `click`/`clickAny`/`clickAll`.
  - **`ImageWaiter`** — unchanged: `waitFor`/`waitUntilGone`/`waitAndClick`.
- **Deleted** `Vision` (+ `evaluate`/`snapshot`), `ImageState` (+ `ScreenState`), `ImageMatcher`, and the
  `…then…`/long-tail variants (`clickBest`, `clickFirst`, `clickUntilSuccess`, `clickWhileVisible`, `findBest`,
  `retryUntilFound`, `clickAndThen`, `clickThenWaitFor`, `waitForGoneThenClick`, `clickOrWaitAndClick`). The
  multi-template single-capture branch (`Vision.evaluate`/`ScreenState`) is replaced by the `whileExists`-style
  lambdas; deleted `VisionEvaluateTest`.
- **Callers updated:** `capture/CaptureSource` + `capture/Window` javadocs no longer reference
  `Vision`/`ImageState`; `Main` unaffected. Studio drops `ImageMatcher`/`ImageState` from its SDK-facade list.

## 2026-07-02 — Game launch API

**Done**
- **`api.launch.Game` facade.** New public static entry point exposed as Studio blocks:
  `Game.launch(String executablePath, String... args)` starts any executable via `ProcessBuilder`
  (detached), and `Game.launchSteam(String appId)` / `launchSteam(int)` hands a Steam appId to the
  local Steam client via the cross-platform `steam://rungameid/<appId>` URL, falling back to
  `steam -applaunch <appId>`. Launching a Steam game needs no login of ours — the installed, signed-in
  Steam client owns the session; we never touch credentials.
- **`internal/launch/UriLauncher`.** Small OS URL-handler opener (Desktop.browse → `xdg-open`/`open`/
  `rundll32`), mirroring the Studio's `util.BrowserLauncher` (duplicated because the SDK can't depend
  on the Studio). Used by `launchSteam` to invoke `steam://` URLs.

**Deferred / next**
- No "wait for game window" helper yet — the vision blocks (`ImageWaiter`) already poll for on-screen
  templates, which is how a test bot detects the game is up.

## 2026-06-30 — Linux click support

**Done**
- **Linux click works.** `api.interaction.Mouse.click` now routes through
  `internal/capture/core/NativeControllerFactory.get()` instead of calling the Windows-only
  `internal/capture/Clicker` (JNA `User32`) directly. This fixes
  `UnsatisfiedLinkError: Unable to load library 'user32'` on Linux, which surfaced whenever a click
  actually fired (i.e. when `ImageFinder` found the template on screen). Windows path is unchanged
  (factory → `WindowsController` → `Clicker` → `User32 PostMessage`).
- **Multi-monitor coordinates fixed.** Added `api.capture.Screen.captureOrigin()` (the virtual-screen
  origin from `ScreenCapture.getVirtualScreenBounds()`). `ImageFinder.find/findAll` and
  `ImageState.findWhichAreVisibleDetailed` now add this origin to match coordinates, so reported
  points are **absolute** screen coordinates. Previously they were image-local, so clicks landed off
  by the virtual origin whenever a monitor was placed left/above the primary (negative origin).
  Note: `ImageFinder.find/findAll` now return absolute coords (corrected contract).
- **Cursor save/restore on Linux click (X11 only).** `LinuxController.postLeftClickScreen` reads the
  pre-click pointer via X11 `XQueryPointer` (added the binding in `linux/X11.java`) against the
  default root window — same coordinate space as `XTestFakeMotionEvent` — and warps back after the
  click. Gated on `WAYLAND_DISPLAY == null`; skipped under Wayland (see below).

**Deferred / next**
- **Click without disturbing the cursor on native Wayland.** Under native Wayland the JVM is an
  XWayland client: it can *write* the pointer (warp/click via XTest) but **cannot read** the global
  cursor position, so save/restore is impossible there (it would teleport the cursor to a stale
  constant). Current interim: on Wayland we skip restore and leave the cursor on the click target.
  Proper fix: implement input injection via the **xdg-desktop-portal RemoteDesktop** interface
  (libei / PipeWire) — this is also what raises the one-time "allow control of pointer/keyboard"
  prompt. Alternative (weaker): window-relative motionless click via `XSendEvent`, but many apps
  ignore synthetic events and it can't reach native Wayland windows. Likely belongs alongside the
  capture backends as a new injection strategy.
- **GNOME/sway capture.** Add a portal/PipeWire `CaptureBackend` and wire into
  `CaptureBackend.select()` (noted in `CLAUDE.md` › Screen capture).
