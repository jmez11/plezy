# Quest window audio

Video playback on Horizon OS associates its Android audio session with the
player Activity's tracked window space, using the same routing as Far Side.
Horizon supplies source positioning and head tracking when the window moves.

`shared/QuestSpatialAudio.kt` discovers the window UUID through the Horizon
framework and binds the session through `media.has`. Both Binder descriptors
are checked before calling the private interfaces. Missing or denied interfaces
preserve ordinary playback; no additional permissions or SDK dependency are needed.

`WindowAudioRouting` retries while window/session metadata becomes ready and
clears callbacks and cached bindings at disposal. ExoPlayer supplies session IDs
through its audio callbacks. MPV video allocates an ID and passes it through
`audiotrack-session-id`, refreshing the binding after playback restarts. Background
music uses an application context and retains its ordinary audio behavior.

Build and device validation:

```powershell
cd android
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -Ptarget-platform=android-arm64
adb -s <quest> install -r ..\build\app\outputs\apk\debug\app-debug.apk
adb -s <quest> install -r ..\build\app\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb -s <quest> shell am instrument -w -e class com.edde746.plezy.shared.WindowAudioRoutingTest com.edde746.plezy.test/androidx.test.runner.AndroidJUnitRunner
```

The tests open a real window and play bundled audio through both production
cores. They require Horizon's audio dump to report EPS spatialization ON for
the actual session, including replacement ExoPlayer sessions. They skip on
ordinary Android. On failure, the dump is saved to the app cache as
`window-audio-failure.txt`. Perceived direction and head tracking while moving
the window still require a headset listening check.

Validated on Quest 3 on 2026-10-18: both device tests passed (EPS ON for MPV
and replacement ExoPlayer sessions). The installed release had a newer version
and different signature, so validation used the separate
`com.edde746.plezy.spatialtest` application ID with isolated provider authorities.
The local build override and logs are in `build/spatial-audio*`.
The Android JVM suite completed 614 tests: 613 passed; the unrelated
`WatchNextProviderTest.corruptContentAddressIsRefetchedWithoutPartialPublish`
failed at its artwork refetch assertion.
