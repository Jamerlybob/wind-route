# Rules for every Codex task in this repo

Read `AGENTS.md` first. Its hard constraints apply in full. On top of them:

- **Do not commit, push, or edit `STATE.md` or `docs/ROADMAP.md`.** Leave your
  work uncommitted in the working tree. Claude reviews, tests on the emulator
  and commits.
- **Never open, print or copy `local.properties`.** It holds the Maps key.
- **No new dependencies.** Nothing may be added to `gradle/libs.versions.toml`
  or `app/build.gradle.kts`. If you think one is needed, stop and say so in
  your final message instead.
- **No new calls to Google.** The Routes API quota is capped at about 30
  requests a day. Only an explicit tap on "Show wind" may call it. Suggestions,
  restores, redraws and comparisons must never trigger a Routes request.
- **James is learning Java from this code.** Small classes, plain names, no
  clever streams or generics. Comment the why at the density of
  `WindMath.java` and `PolylineDecoder.java`.
- Pure logic goes in a plain Java class with no Android imports and gets JUnit
  4 tests in `app/src/test/java/io/github/jamerlybob/windroute/`.
- All user-visible text goes in `res/values/strings.xml`.

## Build

```
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set ANDROID_HOME=C:\Users\james\AppData\Local\Android\Sdk
gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

All three must pass before you finish. If the sandbox stops Gradle from
running at all, do not fight it: say so plainly in the final message.

## Final message

List: files added, files changed, anything in the spec you did not do and why,
any assumption you made about an API that you could not verify, and the result
of the build command.
