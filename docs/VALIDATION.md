# Pounce Next 0.4 — validation
Date: 13 September 2026. Test device: dedicated Android 15 / API35 Pixel5-profile emulator. No physical user phone was connected.

## Automated checks
- Signed release and debug builds passed.
- Release lint passed with zero errors. Remaining warnings are dependency-update suggestions, synchronous preference commits used for alarm persistence, and backup-configuration advice.
- **17 JVM tests passed:** QR payload validation (4), recurrence/DST/timer boundaries (13).
- **15 device integration tests passed:** device-protected storage; QR image round trip; live-camera luminance decoder including rotation/blank frames; one-use grace/scanning budgets; wrong/unverified/stale QR rejection; commitment dismissal guards; valid QR completion; emergency stop and late callbacks; expiry; routine import validation; system-alarm cancellation; rehearsal cancellation; repeated boot scheduling; direct-boot activity declaration.
- APK signature verified. Package: dev.pounce.alarm.next. Minimum Android8/API26, target35.
- Packaged manifest has no INTERNET or network-state permission.
- Audio PCM generation found zero clipped samples.

## Device interaction checks
- Today, generated-QR setup, wake screen and emergency dialog visually inspected.
- QR sharing opened Android's image share sheet; printing rendered a legible one-page A4 QR sheet.
- Camera permission handling and real emulator camera preview exercised. The same camera-frame decoder was tested with generated QR luminance frames.
- Emergency confirmation was exercised through the UI and the foreground alarm service stopped.
- The scanner dialog's navigation-bar overlap was corrected with full-window inset handling.

## Reboot and release checks
Two implementation issues found during reboot testing were corrected:
1. Repeated boot notifications could replace a pending catch-up alarm with tomorrow's occurrence. Unchanged future triggers are now retained; a device regression test covers the race.
2. The alarm activity needed an explicit directBootAware declaration. It now uses device-protected wake-screen preferences and applies lock-screen flags before Compose renders. Initial audio-focus denial is retried promptly during startup.

The final signed-APK locked-boot result is recorded in the release check below. An earlier retry that left the emulator force-stopped was discarded; Android deliberately does not deliver boot alarms to force-stopped apps.

Final signed release passed the scheduled locked-boot rehearsal on 13 September 2026 at 03:25 IST. The app was confirmed not force-stopped before reboot. With airplane mode on and user 0 still RUNNING_LOCKED (before first PIN unlock), AlarmManager fired the scheduled one-off alarm, the full-screen QR wake activity appeared, and AlarmService was foreground. AudioManager showed granted transient alarm audio focus and an unmuted MediaPlayer in state:started with USAGE_ALARM. Screenshot: artifacts/final-success.png. This used a generated emulator-only verified QR fixture; it did not scan a physical printed marker.

## Practical limits
Emulator state and media playback inspection do not establish physical speaker loudness, vibration strength, phone-vendor battery behavior or wake-up effectiveness. The generated-marker test fixture is not a physical location test. Scan the code printed from the user's own phone during tonight's lock-screen rehearsal.

The APK cannot recover the previous private signing key or installed alarm data. This build installs alongside the old app. The QR wake-up flow replaces the old step/photo mission focus; those old missions and follow-up checks were not reconstructed.

The source backup excludes signing material. A separate restricted local private recovery backup is retained on drive E.
