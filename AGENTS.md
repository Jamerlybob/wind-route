# AGENTS.md

Shared rules for any coding agent (Claude Code, Codex) working on WindRoute.
Read `STATE.md` first for where the work is, then `docs/ROADMAP.md`.

## What this is

A native Android app for cyclists: type two places, see the route coloured by
headwind, tailwind and crosswind for the time you will be riding it. James owns
it, does not write the code himself, and is studying Java, so the code must be
readable enough to learn from.

## Hard constraints

- **Java, not Kotlin. XML Views, not Compose.**
- **No new library without asking James.** The app uses AndroidX, Material and
  the Google Maps SDK. HTTP is `HttpURLConnection`, JSON is `org.json`.
- **Logic stays out of the Activity.** Anything that can be a plain Java class
  with no Android imports is one, with JUnit tests. `wind/`, `route/` and
  `weather/` are that layer.
- **Comment the why** wherever the logic is not obvious. Match the density of
  `WindMath.java` and `PolylineDecoder.java`.
- **The Maps key never enters git.** It lives in `local.properties`. CI builds
  without it on purpose. An APK built with the key has the key inside it, so
  never attach one to a public release or issue.
- **Nothing that costs money.** James decided on 2026-10-08 that he will never
  pay for this app. Use only free APIs, or paid ones strictly inside their
  free monthly allowance, and say so before adding any call that is billed
  per use. No Places autocomplete.
- **Check API docs, do not rely on memory**, for Google Routes and Open-Meteo.
  Google requires its cycling-route warnings to be shown with the route.

## Working routine

1. `git status` and `git log --oneline -8`, then read `STATE.md`.
2. Take the top unticked item in `docs/ROADMAP.md` unless James says otherwise.
3. Unit test the logic. Then test on the emulator yourself (recipe below) and
   look at a screenshot. James is usually remote and cannot test for you.
4. Update `STATE.md`, tick the roadmap, commit with a clear message, push.
5. Send James the new APK and a screenshot.

## Build and test

```
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Emulator (AVD `Medium_Phone`, 1080x2400), from `%LOCALAPPDATA%\Android\Sdk`:

```
emulator\emulator.exe -avd Medium_Phone -no-audio -no-boot-anim -no-snapshot-save -no-window
platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
platform-tools\adb.exe shell input keyevent KEYCODE_WAKEUP
platform-tools\adb.exe shell am start -n io.github.jamerlybob.windroute/.MainActivity
platform-tools\adb.exe shell input tap 540 203          (From field)
platform-tools\adb.exe shell input text "Mission%sBay,%sAuckland"
platform-tools\adb.exe shell screencap -p /sdcard/s.png  then  adb pull
platform-tools\adb.exe emu kill
```

The first screenshot after boot can be black; wake the screen and retry.

## Two agents

Either agent can run a session. Nothing lives only in chat: if the next agent
needs it, it is in `STATE.md`, `docs/` or the code. Good work to hand to Codex:
a bounded, well-specified task with tests (a parser, a pure calculation, a
settings screen from a written spec). Keep design decisions and anything that
touches the key or billing with whoever is talking to James.