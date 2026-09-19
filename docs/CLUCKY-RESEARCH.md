# Clucky and the Pounce mission rebuild

## Scope and evidence

This comparison concerns Clucky 1.2.0 as publicly described on 13 September 2026 and Pounce 0.5 for personal Android use. Clucky was examined through its official website, App Store description and release history, support page, and privacy policy. These establish advertised features and developer statements; they do not establish real-world alarm reliability or the exact implementation of every mission. No paid iPhone session was available for hands-on verification. Pounce implementation and tests are separately documented in VALIDATION-0.5.md.

## Clucky's product structure

Clucky combines an insistent character, cognitive and physical missions, and a reward loop. Its site illustrates math, memory, tilt, shake, typing and color tasks, animated rooster personality and streak rewards. It says solo use remains local and friend groups are optional. [1]

The App Store adds walking, push-ups, registered-photo matching and camera object hunting to that mission set. It advertises stacks of up to three tasks, difficulty selection, sound import, a loud mode and sound check, detailed morning history, five-tab navigation, widgets and iOS glance surfaces. Flock provides shared wake times and friend accountability. Version 1.2.0 describes alarm recovery, backup notifications, shorter setup, additional vector animation and mission rescue paths. The listing requires a subscription for these features. These are developer claims, not independent performance measurements. [2]

The privacy policy says camera analysis stays on-device and gives finding something blue as an example. Motion and alarm records also remain local; optional social features and operational analytics use external services. [3]

The core experience is therefore broader than an alarm with a puzzle attached. Before sleep, a person chooses a routine. During ringing, the character makes the next action obvious. Tasks create effort and small wins. After completion, history and rewards reinforce returning the next morning. Pounce 0.4 narrowed this loop too far by making a printed marker the centerpiece and reducing mission variety.

## Product decisions for Pounce

Pounce 0.5 restores variety while keeping the existing Android scheduling foundation. The central navigation separates Today, Alarms, Missions, Progress and Settings. A cat appears across the experience with nine randomized gestures and context-sensitive moods. The mission library doubles as practice, so a person can discover hardware or difficulty problems before an actual wake-up.

Two to five distinct tasks can be ordered for each alarm. The default sequence is walking, memory and calculations. This is a deliberate design response to completing two bedside puzzles and returning to sleep: movement comes first, followed by different forms of attention. It is a product hypothesis, not a guarantee of wakefulness. Walking estimates can be fooled by device movement, and a person can perform cognitive tasks while remaining sleepy.

A two-minute stay-awake stage follows the initial stack by default. Sound pauses during that stage while the screen explicitly says the alarm remains active. After the interval, walking and typing must be completed. No success history is recorded before the final check. This gives the person time to begin another morning activity and then requires renewed engagement. The interval and task progression survive process recreation.

QR setup and QR dismissal are removed. Real alarms have no snooze, sleep, emergency-dismiss or finish button in the app. A task that cannot work can be exchanged for two replacement tasks, not a successful completion. The substitution is also guarded against delayed callbacks from the original task. Practice retains an exit because rehearsal is a separate mode. Android system controls remain available: this is a user-owned personal app, not a kiosk or device-locking system.

## Mission implementation and limits

| Mission | Pounce behavior | Practical boundary |
|---|---|---|
| Calculations | Fresh seeded sums; higher difficulty adds multiplication | Attention, not physical movement |
| Memory | Watch and repeat a sequence on a nine-cell board | May still be done in bed |
| Typing | Reproduce a wake-up phrase | Does not prove alertness |
| Color confusion | Select the displayed ink color | Color discrimination may be unsuitable for some people |
| Yarn maze | Tilt through ordered checkpoints | Requires working motion sensing |
| Shake | Count distinct movements with spacing between them | Gentle handling is sufficient |
| Paw patrol | Native step events where available; disclosed acceleration-based estimate otherwise | Movement estimation is not location proof |
| Rise & repeat | Count near/far cycles using proximity sensing | Cannot verify push-up form or distinguish a hand |
| Photo destination | Compare a captured scene with a small registered visual descriptor | Approximate scene correlation; sensitive to viewpoint and light |
| Color hunt | Measure a requested color in the camera's central region | Recognizes color coverage, not general object identity |

Photo registration stores a compact descriptor instead of a photograph. The camera hunt intentionally uses measurable color criteria without presenting itself as AI object recognition. There is no external model download or camera upload. These limitations are shown in mission instructions and reflected in the feature comparison; they must not be hidden behind claims of exact Clucky parity.

## Phone signals and alarm behavior

The melody increases from a lower gain to the selected level over 45 seconds. Three built-in sounds and locally imported audio provide choice. Spoken cues repeat the next-action theme. An optional boost raises the system alarm stream to at least 85 percent while ringing and restores it afterwards if the person has not changed that stream themselves. Hardware volume controls remain respected.

Vibration repeats every five seconds, lengthens after fifteen seconds and increases requested amplitude through forty-five seconds where the device supports amplitude control. It is classified as alarm vibration. A phone without amplitude control uses the timed pattern alone. Emulator playback inspection cannot establish the actual strength of a physical vibration motor.

The wake screen turns on over the lock screen and gradually raises window brightness over twenty seconds. It uses steady light and includes a dim control. Brightness returns to its original setting when the wake screen is inactive. This uses the screen as an additional attention cue; no claim is made that phone light produces the same effect as outdoor daylight or a clinical light device.

Android exact alarms, a visible foreground playback service, device-protected state and boot receivers provide the scheduling foundation. Android documents exact-alarm permission and restrictions on long-running work. Audio playback follows audio-focus rules so calls and other system audio interruptions are handled. These platform mechanisms are necessary, but they cannot guarantee delivery when the phone is powered off, the app is force-stopped or permissions are revoked. [5][6]

Android provides both step detectors and counters, with activity-recognition permission required on recent Android versions. Hardware availability varies; Pounce discloses its movement-estimation fallback. [4]

## Parity and intentional differences

Pounce matches the broad solo categories of alarms, task stacks, cognitive and movement challenges, photo comparison, color camera hunting, local history, rewards, sound choice and a home-screen widget. It adds a longer maximum stack and a delayed two-task check. These differences justify testing whether the flow better supports staying up; they do not establish superiority.

There is no Flock server, friend nudging, social leaderboard, account, payment or subscription. Those features conflict with the chosen offline personal-use scope. Shared routines are local JSON files. Imported routines start disabled for review and exclude private camera descriptors and custom audio files. Pounce uses an Android widget and alarm notification instead of iOS-only Live Activities and Dynamic Island. It does not claim exact parity with Apple's alarm framework.

The interface is original cat-themed work. Clucky's chicken art, brand identity and source code were not copied. Original recovered Pounce kitten audio is retained, with attribution and locally generated music documented separately.

## Validation priorities

The most important release checks concern early dismissal and lost progress: a stale task callback must not advance a new mission; replacing a sensor task must require both replacements; the delayed stage must not record success; and recovery must preserve the stack index. Unit tests cover transition rules and scheduling boundaries. Device tests cover persisted transitions, callback revision checks, legacy configuration migration, camera measurements and scheduling integration.

Visual review must include small-screen scrolling, system insets, the numeric keypad, memory board, camera permission failure, photo registration and the task-only wake screen. A real-phone rehearsal is still required for step detection, proximity range, camera lighting, speaker level, vibration and vendor battery settings. Successful tests demonstrate implementation behavior within those conditions, not that every sleeper will wake.

## Sources

1. Clucky, [official product website](https://tryclucky.com/), accessed 13 September 2026. Character, illustrated missions, local solo use and rewards.
2. Adrian Angelo Abelarde, [Clucky App Store listing](https://apps.apple.com/us/app/clucky-loud-alarm-clock/id6792550911), version 1.2.0 dated 31 August 2026, accessed 13 September 2026. Advertised features and release changes.
3. Clucky, [Privacy Policy](https://tryclucky.com/privacy), updated 19 August 2026. Local camera and motion processing and optional external services.
4. Android Developers, [Motion sensors](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion), accessed 13 September 2026. Sensor types and permission requirements.
5. Android Developers, [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms), accessed 13 September 2026. Scheduling and permission model.
6. Android Developers, [Manage audio focus](https://developer.android.com/media/optimize/audio-focus), accessed 13 September 2026. Playback focus behavior.
7. Clucky, [Support](https://tryclucky.com/support), accessed 13 September 2026. Developer's alarm troubleshooting and account scope.
