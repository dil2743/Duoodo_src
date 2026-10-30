# Duoodo: Instructions & Engineering Standards

> **Note:** The full universal architecture, protocols, and developer guide are documented in [AGENTS.md](./AGENTS.md).

## Quick Agent Rules

1. **Sandboxing / Permissions:** NEVER add permissions reaching user data (`storage`, `media`, `camera`, `contacts`, `location`, `microphone`, `phone`, `SMS`). Run `scripts/check-apk-privacy.sh <apk>` to verify.
2. **Ephemeral:** NEVER add message history, chat logs, or persistent image galleries. Duoodo only holds the single latest received doodle.
3. **Cryptographic Integrity:** Respect domain separation in HKDF. Respect the 49 KB bucket payload ceiling in `Envelope.kt`. Never disable authenticated encryption.
4. **Widget Action Rule:** Always use `android.appwidget.action.APPWIDGET_UPDATE` for AppWidget receivers in `AndroidManifest.xml`.
5. **No Nested QEMU Emulation:** Never attempt to start headless QEMU emulators (`emulator -avd ...`) in virtual machine environments. Always use `SimulatedEndToEndTest.kt` for end-to-end integration testing.
6. **Build Environment:**
   - Java 17: `JAVA_HOME=/home/hkd7kor/android/jdk-17`
   - Gradle: `~/android/gradle-8.11.1/bin/gradle`
   - Proxy: `127.0.0.1:3128`
   - Test command: `JAVA_HOME=/home/hkd7kor/android/jdk-17 PATH=$JAVA_HOME/bin:$PATH ~/android/gradle-8.11.1/bin/gradle testDebugUnitTest`
