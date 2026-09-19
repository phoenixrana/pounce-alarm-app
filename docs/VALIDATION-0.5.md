# Pounce 0.5 validation

Date: 13 September 2026. Dedicated Android 15 / API35 emulator. No physical phone was connected.

## Build and automated checks

- Signed release and debug builds passed; release signature verified (v2), same private key and package as Pounce Next 0.4.
- Release lint: zero errors, 28 warnings. Warnings concern dependency versions, deliberate synchronous preference commits, backup configuration, localization/static widget text and layout advice.
- 26 JVM tests passed: 11 scheduling/clock-boundary tests and 15 mission-transition tests.
- 29 Android tests passed: 16 alarm/storage/migration/scheduling integration tests, seven camera vision/storage tests and six audio/volume/manifest tests.
- Full camera tests validate detailed-image similarity, unrelated/inverted rejection, plain/dark rejection, actual hue coverage, descriptor round-trip and corrupt-data rejection. These are synthetic bitmap tests, not physical camera accuracy measurements.
- Audio tests decode all three bundled sounds and validate volume restoration and preservation of manual changes. Installed manifest has no INTERNET permission.
- Real-alarm dismissal attempts and stale completion/replacement callbacks are rejected. Both replacement tasks and the final delayed check remain required. Real alarms do not expire after 20 minutes.
- Test fixture cleanup now cancels pending recovery alarms before clearing test state. This fixed interference in two scheduling tests; app cancellation code was unchanged.

Logs: artifacts/v05-final-build.log, artifacts/v05-test-build.log, artifacts/v05-device-tests-final.log. Final suite result: OK (29 tests).

## UI and release checks

- Home and mission library visually inspected on the emulator; card content and five-tab navigation render correctly.
- Calculations practice visually inspected and completed through its actual keypad for all four rounds. Completion dialog appeared.
- Cat gestures and contextual artwork render in home and practice views.
- Signed 0.4 APK upgraded in place to signed 0.5 with install -r. A saved legacy alarm remained present and its configuration migrated to the new default task stack.
- Scheduled signed-release alarm passed from a dozing/locked screen in airplane mode: AlarmManager started the foreground service, the full-screen Paw patrol mission appeared, audio focus was granted and MediaPlayer was unmuted in state:started with USAGE_ALARM. The wake screen had no QR, snooze, emergency-dismiss or finish action. Screenshot: artifacts/v05-release-wake.png. This was a screen-lock test, not a new before-first-unlock reboot test of version 0.5.

## Environment observations

The emulator experienced slow boot and System UI ANRs. One test invocation failed to start during overloaded Android boot. That invocation was discarded; the final 29-test suite ran successfully after sys.boot_completed became 1. A stale System UI error overlay was closed before visual verification. These emulator incidents are retained in artifacts and are not presented as successful app tests.

## Scope limits

A physical phone rehearsal is still required for speaker level, vibration motor strength, step sensing, proximity range, camera viewpoint/lighting and manufacturer battery restrictions. The screenshot and synthetic-image checks do not prove that a physical photo mission will match every scene, that push-up form is correct or that a person has left bed. General AI object recognition, live friend groups and iOS-specific surfaces are not claimed.

No source from Clucky was used. The comparison in CLUCKY-RESEARCH.md distinguishes public descriptions from tested Pounce behavior. The 0.4 validation report is historical only.
