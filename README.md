# ReturnRight Watch

A native Kotlin / Jetpack Compose Wear OS app for Galaxy Watch7 and other Wear OS 3+ watches. It runs on the watch without a companion phone app.

## What is implemented

- Nearby stations, sorted by straight-line distance when location is available; alphabetical browsing otherwise.
- Touch scrolling and rotary/bezel scrolling on station and detail lists.
- Reported running, full, offline, maintenance, fault, and unknown status, plus a running-only filter.
- Station addresses, postal codes, opening hours, and remarks.
- An interactive OpenStreetMap street map with station markers, GPS position, drag-to-pan, zoom buttons, and re-centering.
- A compass arrow to the selected station, using true-north bearing and magnetic declination correction. The arrow follows the top of the watch display; hold the watch flat.
- Immediate manual refresh and automatic station checks with a ten-minute cooldown while the app is visible. GPS and compass listeners stop when the app goes into the background; the compass runs only on its screen.
- Cached station data with fetch age and stale/error labels. No background location permission is requested.
- An explicit demo using four synthetic stations and a simulated City Hall origin/heading. Demo data never enters the live cache.

The arrow and distances indicate a straight line, not a walking route. When you are within the GPS accuracy radius, the app asks you to look nearby. It does not claim you have reached the machine. Reported machine status and building opening hours are separate.

## Open and build

1. Install Android Studio and open **this directory** as a project.
2. Use Java 17 for Gradle, and install Android SDK Platform 35 and Build Tools 35.0.0 through SDK Manager. Let Studio create `local.properties` with your SDK path.
3. Sync Gradle, then run the `app` configuration on a Wear OS round emulator or Galaxy Watch7.

From a shell with `JAVA_HOME` and `ANDROID_HOME` configured:

```sh
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

To test only the navigation math and station parser without the Android SDK:

```sh
./gradlew -p core test
```

During project creation Java 17 and Android SDK 35 were installed inside `.tools/` because this Mac did not have an Android toolchain. `local.properties` points to this SDK. You can build without changing your shell configuration:

```sh
JAVA_HOME="$PWD/.tools/jdk-17.0.20.1+1/Contents/Home" ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

`.tools/` and `local.properties` are ignored by Git; other machines should use their own Java and SDK installation. The optional `scripts/bootstrap-android.sh` can install SDK 35 on Apple Silicon macOS (requires an explicit SDK license acceptance flag). The project includes the official Gradle 8.11.1 wrapper. CI configuration builds the APK, runs core tests, and runs Android lint on pushes and pull requests.

## Install on Galaxy Watch7

On the watch, enable Developer options by tapping **Software version** repeatedly in Settings → About watch → Software information. Turn on ADB debugging and Wireless debugging in Developer options. Put the watch and Mac on the same Wi-Fi network.

Use the pairing port/code shown by **Pair new device**, then the separate connection port shown on the wireless debugging screen:

```sh
adb pair WATCH_IP:PAIRING_PORT
adb connect WATCH_IP:CONNECTION_PORT
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open ReturnRight Watch, tap **Enable location**, and grant location access while using the app. Precise location gives better compass guidance. If the compass requests calibration, move your wrist in a figure 8 away from metal or magnets. A Wear OS emulator may lack a north-referenced rotation sensor; use demo mode to explore the compass layout.

## Station data

The official locator is [ReturnRight's station finder](https://returnright.sg/p/find-my-nearest-rvm). Its website uses this public, undocumented feed:

```text
GET https://returnright.sg/px-api/locations/access-token
GET https://returnright.sg/px-api/locations/nearby?lat=<watch-lat>&lng=<watch-lng>&radius=2000
GET https://returnright.sg/px-api/locations
```

The client obtains the short-lived access token in memory, then sends it with station requests. When a fresh watch location is available, it requests stations within two kilometres; otherwise it loads the general location list. No browser token or saved location is included in the app. This is an undocumented public website feed, so availability and response fields may change.

The observed location envelope is `{ "status": "ok", "data": [...] }`, with `id`, `locationName`, `latitude`, `longitude`, `address`, `postalCode`, `status`, `rvmOpeningHours`, and `rvm_remarks` fields. Schema details were also inspected in the public [ReturnRight data archive](https://github.com/cheeaun/returnright-data); its source code has not been incorporated.

Station-level status comes from the location feed; per-machine `bin-status` data is not currently requested. Fetch time means when the app downloaded the response, not when the machine last reported telemetry. After two minutes or a refresh failure, statuses are labelled cached/last reported. Unknown vendor states remain unknown, and the app does not silently substitute snapshots.

Missing, invalid, or outside-Singapore coordinates are excluded from the map/list. A successful response with no usable records produces an error and preserves the previous cache.

## Map data

Street tiles come from `https://tile.openstreetmap.org/{z}/{x}/{y}.png`. No Google Maps API key or billing account is needed. The app requests only visible tiles, sets an identifying User-Agent, displays attribution inside the round screen, and uses a 48 MB HTTP cache that honors server cache headers (seven-day fallback when no expiry is specified). There is no offline download or prefetch feature.

For distribution, publish an app contact address and review the [OpenStreetMap tile policy](https://operations.osmfoundation.org/policies/tiles/). Set `TILE_URL` to another licensed raster provider in `app/build.gradle.kts` if traffic or reliability requirements grow, and update the on-screen attribution to match. Cached revisits may work without a connection; uncached tiles show a map error and a retry control.

## Validation

Current version: **0.1.7**. It has been tested on a physical Galaxy Watch7; a hotspot run successfully obtained an access token and nearby station list. Unit tests cover station parsing, navigation math, refresh timing, and the HTTP client. CI runs tests, assembles a debug APK, and runs Android lint.

Automatic station checks run no more often than every ten minutes while the app is visible; manual refresh is available at any time. Recent cached results are shown with their age. Debug builds log request metadata for troubleshooting, but never log access-token values, response bodies, or location query coordinates.

## Project layout

```text
app/                         Wear UI, sensors, networking, station cache
  .../ui/                    List/detail, street map, compass screens
  .../navigation/            Foreground GPS and compass listeners
  .../data/                  Official feed client
core/                        Android-independent models, feed parser, geo math
  src/test/                  Navigation and API edge-case tests
.github/workflows/android.yml  Build/test/lint and debug APK artifact
```

Independent project; not affiliated with BCRS or ReturnRight. GPS coordinates are used locally for sorting, map placement, and bearing, and sent to ReturnRight for nearby searches while the app is open and a fresh location is available. Map tile requests reveal the viewed area to the tile provider. No analytics or account system is included.
