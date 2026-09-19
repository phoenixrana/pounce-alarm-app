# Pounce 0.5

Personal offline Android alarm with an animated cat and required wake-up missions. Active source: E:\To-Do\pounce.

## Install and set up

Install releases/Pounce-0.5.0.apk on Android 8 or newer. It updates Pounce Next 0.4 in place using the same package and signing key; the launcher now says Pounce. The original 0.3 app remains a separate installation because its signing key was lost. Existing 0.4 alarms migrate to walking, memory and math plus a delayed check. Open every migrated alarm to review its new tasks before bedtime.

1. Complete the phone checks on Today: precise timing, notifications, lock-screen access and system alarm volume.
2. Visit Missions and practise the tasks you want. Register a photo destination there if using photo matching.
3. In Alarms, choose a time and a stack of 2–5 tasks. Put walking or a destination photo in the stack to encourage leaving bed. The default is walking, memory, then math.
4. Keep Stay-awake check enabled: after the first stack, a two-minute quiet interval leads to another walking and typing check. Success is recorded only after the final tasks.
5. Select a sound, sound level, vibration and light. Boost system alarm volume is optional and restores the previous level afterwards unless you changed it yourself.
6. In Settings, run the one-minute lock-screen rehearsal with the same DND/Bluetooth settings you use overnight. Physical sensors, sound and vibration need testing on your own phone.

Real alarms have no QR, snooze, Let me sleep, emergency-dismiss or finish button. All tasks must be completed. If a task cannot work, it can be replaced by two tasks; the remaining stack still follows. Practice has its own finish control. Android system controls remain available. Real alarms no longer silently expire after 20 minutes; only practice has that limit.

## Mission library

Calculations, memory patterns, typing, ink-color matching, tilt checkpoints, shake counts, walking, proximity repetitions, registered-photo comparison and camera color hunting. Difficulty is configurable for each alarm.

Walking uses native step sensing where available and a disclosed motion estimate otherwise. Proximity repetitions cannot verify push-up form. Photo matching is approximate scene correlation and needs a similar view and lighting; a compact descriptor is stored instead of a photograph. Color hunt measures pixel color coverage, not general object identity. These tasks encourage engagement; none proves wakefulness or physical location.

## Other features

Five tabs: Today, Alarms, Missions, Progress, Settings. Nine random cat gestures and six contextual moods, reduced-motion control, three built-in sounds, private audio import, repeated spoken cues, 45-second sound escalation, alarm vibration, 20-second steady screen brightening, next-alarm home widget, streaks, milestone rewards and local wake history. Shared JSON routines start disabled on import and exclude camera references and custom audio files.

Everything works without an account, subscription, social server or internet permission. Optional Clucky friend groups and iOS-only glance surfaces are not reproduced. See docs/CLUCKY-RESEARCH.md for the researched comparison and explicit gaps.

## Reliability and verification

Exact alarm scheduling, foreground playback, device-protected state, reboot rearming and mission revision guards. Alarms cannot run on a powered-off phone; force-stop, revoked permissions or vendor restrictions can prevent delivery. No claim is made that the app is impossible to stop or guaranteed to wake everyone.

See docs/VALIDATION-0.5.md for checks actually performed. The 0.4 validation report is historical and must not be read as validation of this release.

## Build and recovery

JDK17, Gradle8.9, AGP8.7.3, Kotlin1.9.24, Compose1.7.5, compile/target35, min26. scripts/build.ps1 builds debug and runs JVM tests/lint. scripts/release.ps1 builds and verifies the signed APK. scripts/backup-source.py creates a source ZIP with no signing secrets and a separate restricted private recovery ZIP on E.

The release key remains in private/ and is required for future updates. Never share that directory or the private recovery ZIP. Source/build outputs stay on E; SDK and tool caches remain on C.

## Build from GitHub

Clone the repository and open it in Android Studio, or install JDK 17 and Android SDK 35, set JAVA_HOME and ANDROID_HOME, then run:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

On macOS/Linux, use `sh ./gradlew assembleDebug testDebugUnitTest lintDebug`. Android Studio can create the ignored `local.properties` file for your SDK path. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Signing keys, local SDK settings, build outputs, APKs and recovery archives are intentionally excluded from Git. Release builds require the original private signing files; the scripts under `scripts/` retain paths for the original Windows workstation. A debug build cannot replace an installed release with a different signature.
