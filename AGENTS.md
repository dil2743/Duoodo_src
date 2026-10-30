# Duoodo: Architecture, Protocols & Agent Guidelines

> **Target Audience:** Any AI coding assistant (Gemini, Claude, GPT, Cursor, Copilot) or human engineer contributing to or maintaining the Duoodo codebase.

---

## 1. Project Philosophy & Core Constraints

Duoodo is an intimate, ephemeral pair-doodling Android application designed to operate over public, untrusted infrastructure without servers, accounts, or privacy compromises.

### Non-Negotiable Tenets

1. **Zero Central Infrastructure:** No backend servers, no user accounts, no phone numbers, no emails, no databases. Communication occurs entirely peer-to-peer over public Nostr relays treated as untrusted key-value brokers.
2. **Zero Sensitive Permissions:** The app must remain strictly sandboxed.
   - **Forbidden permissions:** No access to external storage (`READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`), media images/videos, camera (`CAMERA`), contacts, location, or microphone.
   - **Verification script:** Every build must satisfy `scripts/check-apk-privacy.sh <apk>`, which checks `aapt2 dump permissions` and fails if any user data permission is present.
   - **Photos & QR Codes:** QR scanning delegates to Google Play Services (`GmsBarcodeScanning`); image/photo selection uses the Android Photo Picker (`ActivityResultContracts.PickVisualMedia()`). Neither requires runtime manifest permissions.
3. **Strict Ephemerality:** Only the single latest received doodle is stored on device. There is no message history, gallery, or historical archive. A new doodle atomically overwrites the old one. Unpairing purges all cryptographic keys and stores immediately.
4. **Data Leakage Prevention:**
   - Backups and device migrations are disabled in `data_extraction_rules.xml` (`exclude domain="root|file|database|sharedpref|external"`).
   - Cleartext HTTP traffic is blocked (`cleartextTrafficPermitted="false"`).
   - Only system Certificate Authorities are trusted (`certificates src="system"`); user-installed CAs cannot intercept WSS relay traffic.
   - In release builds, `WindowManager.LayoutParams.FLAG_SECURE` prevents private drawings from appearing in recent-app switcher snapshots and screenshots.

---

## 2. System Architecture

```
                          ┌───────────────────────┐
                          │   Public Nostr Relays │
                          │ (Untrusted Transport) │
                          └───────────▲───────────┘
                                      │
                 WSS (TLS only)       │  NIP-01 / NIP-33 / NIP-09
                 Parameterized App    │  Encrypted & Bucket-Padded
                 Data (Kind 30078)    │  Daily Rotating Slot Tags
                                      │
               ┌──────────────────────┴──────────────────────┐
               │                                             │
      ┌────────┴────────┐                           ┌────────┴────────┐
      │   Phone A       │                           │   Phone B       │
      │   (Initiator)   │   Ephemeral E2E Channel   │   (Responder)   │
      │                 │◄═════════════════════════►│                 │
      └─────────────────┘                           └─────────────────┘
```

### Directory Structure & Module Responsibilities

```
app/src/main/java/app/duoodo/
├── crypto/          # Cryptographic primitives (X25519, ChaCha20-Poly1305, BIP-340 Schnorr, HKDF)
├── data/            # Local storage (hardware-backed SecretStore, LatestDoodleStore, Settings)
├── model/           # Canvas marks (Stroke, TextMark, ImageMark) and binary DoodleCodec
├── nostr/           # Nostr wire protocol (NostrEvent, RelayPool, RelaySubscriber)
├── pairing/         # Ephemeral handshake (Invite, InviteAcceptor, Handshake, PairingRelay)
├── render/          # DoodleRenderer (multi-size centering, transparent halo, vector rasterization)
├── sync/            # Synchronization (NostrTransport, Envelope, Inbox, LiveService, CheckWorker)
├── ui/              # Jetpack Compose UI (DuoodoScreen, DrawTab, InboxTab, SettingsTab, Canvas)
│   └── components/  # Reusable UI widgets (BirdLogo, Footer, SectionCard, SettingRow)
└── widget/          # Android Glance Home Screen Widgets (DuoodoWidget, multi-size receivers)
```

---

## 3. Cryptographic Protocols & Security Boundaries

### 3.1. Ephemeral Handshake & Key Derivation

1. **Initiator:**
   - Generates ephemeral X25519 keypair $(a, A)$ and a 256-bit CSPRNG secret $s$.
   - Formats invite: `duoodo-` + Base64Url(`VERSION (2) || A (32B) || s (32B) || createdAt (8B)`).
   - Invites expire after 24 hours (`TTL_SECONDS = 86400`).
2. **Responder:**
   - Generates ephemeral X25519 keypair $(b, B)$.
   - Computes shared secret: $\text{shared} = \text{X25519}(b, A)$.
   - Derives root key:
     $$\text{root} = \text{HKDF}(\text{shared}, \text{salt}=\text{SHA256}(s, A, B), \text{info}=\text{"duoodo/v1/root"})$$
   - Encrypts public key $B$ with ChaCha20-Poly1305 using key $\text{HKDF}(s, \text{"duoodo/v1/invite/key"})$ and publishes under tag $\text{HKDF}(s, \text{"duoodo/v1/invite/tag"})$.
3. **Confirmation & Anti-Reflection:**
   - Initiator verifies reply, derives identical root, and publishes confirmation under $\text{HKDF}(\text{root}, \text{"duoodo/v1/confirm/tag"})$.
   - Keys are directionally mirrored:
     $$\text{Initiator Outgoing} = \text{Responder Incoming} = \text{INITIATOR\_TO\_RESPONDER}$$
     $$\text{Responder Outgoing} = \text{Initiator Incoming} = \text{RESPONDER\_TO\_INITIATOR}$$
4. **Mandatory Safety Code Verification:**
   - Both devices derive a 6-emoji sequence from $\text{HKDF}(\text{root}, \text{"duoodo/v1/safety"}, 6)$.
   - The UI immediately routes to `PairState.VerifySafety(emojis)`.
   - **No doodle can be sent or fetched until the user explicitly confirms the emojis match.** If an invite is intercepted, mathematical divergence between the roots causes the emojis to mismatch.

### 3.2. Message Unlinkability

To prevent public relays or eavesdroppers from linking a pair's messages over time:
- **Daily Key Rotation:** Nostr private/public signing keys rotate every UTC day:
  $$\text{nostrPrivateKey}(\text{dir}, \text{day}) = \text{Schnorr}(\text{HKDF}(\text{root}, \text{"duoodo/v1/nostr/}\$\{\text{dir.label}\}/\$\{\text{day}\}\text{"}))$$
- **Daily Slot Tag Rotation:** Replaceable event `#d` tags rotate daily per direction:
  $$\text{slotTag}(\text{dir}, \text{day}) = \text{HKDF}(\text{root}, \text{"duoodo/v1/slot/}\$\{\text{dir.label}\}/\$\{\text{day}\}\text{"}, 16)$$
- **Rollover Deletion:** On day transition, `deleteSlot(previousDay)` publishes a signed NIP-09 kind 5 deletion event targeting yesterday's slot.

### 3.3. Defenses Against Hostile Relays

Relays are public and untrusted. The following protections are enforced:
- **WebSocket Frame Ceiling:** Any incoming frame $> 128\text{ KB}$ is immediately dropped in `onMessage` before JSON parsing.
- **Envelope Size Guard:** `Envelope.open` drops ciphertexts $> 64\text{ KB}$ before decryption.
- **Bounded Channels:** All incoming channels use `capacity = 64` with `BufferOverflow.DROP_OLDEST` to prevent OOM denial-of-service.
- **Pre-Filtering:** Query and subscription handlers match `pubkey` and `d` tag against expected authors *before* executing expensive BIP-340 elliptic curve signature verification (`isValid()`).
- **Query Capping:** Queries limit accumulated events to `MAX_QUERY_EVENTS = 50`.

### 3.4. Local Storage Protection

- `SecretStore.kt`: Sensitive keys are encrypted with AES-256-GCM using hardware-backed keys from the Android Keystore. The filename is bound as Authenticated Additional Data (AAD). Stored in `context.noBackupFilesDir`.
- `LatestDoodleStore.kt`: The single latest doodle is written atomically via `AtomicFile` to `noBackupFilesDir`.

---

## 4. Doodle Model, Codec & Canvas Interactions

### 4.1. Mark Model (`app/duoodo/model/Doodle.kt`)

All coordinates on the canvas are normalized floats in $[0.0, 1.0]$.
- `Stroke`: Continuous points `floatArrayOf(x0, y0, x1, y1, ...)`, quantized color, and stroke width.
- `TextMark`: Plaintext string, center $(x, y)$, color, size fraction, and `rotation` (degrees).
- `ImageMark`: Compressed byte array (WebP/JPEG), center $(x, y)$, scale factor, and `rotation` (degrees).

### 4.2. Binary Wire Format (`DoodleCodec.kt`)

Currently at **`VERSION = 3`** (backward-compatible with `VERSION = 2`):
- Coordinates, sizes, widths, and rotations are quantized into 16-bit unsigned integers ($Q = 65535$).
- **Payload Limits (fits in 49 KB envelope bucket):**
  - `MAX_MARKS = 500`
  - `MAX_TOTAL_POINTS = 8000`
  - `MAX_IMAGE_BYTES = 40_000`
  - `MAX_TOTAL_IMAGE_BYTES = 45_000`
  - `MAX_TEXT_BYTES = 400`
  - `MAX_TOTAL_TEXT_BYTES = 2000`
- Encrypted payloads are padded to discrete power-of-two buckets ($1024, 4096, 16384, 49152$ bytes) in `Envelope.kt` to conceal stroke count and drawing complexity.

### 4.3. Multi-Touch Gestures (`DoodleCanvas.kt` & `DrawingState.kt`)

- Freehand and shapes take normal drag gestures.
- Text tool opens a text dialog on tap.
- **Emoji & Image Tools:**
  - Touch-down triggers `startPlacement(x, y)`.
  - Multi-touch gestures track pan, pinch zoom, and rotation in `updatePlacement(dx, dy, zoom, rot)`.
  - Releasing all touches calls `endPlacement()`, committing the scaled and rotated mark to the canvas.
  - Placed photos become base canvas layers (`ImageMark`); pens, markers, rainbows, and erasers can paint directly over them.

---

## 5. Home Screen Widgets (Glance)

### 5.1. Multi-Size Support

Duoodo supports 4 standard home screen widget sizes:
1. `2×2` (Square): `DuoodoWidgetReceiver` → `duoodo_widget_info.xml`
2. `2×4` (Wide card): `DuoodoWidgetReceiver2x4` → `duoodo_widget_info_2x4.xml`
3. `2×3` (Medium): `DuoodoWidgetReceiver2x3` → `duoodo_widget_info_2x3.xml`
4. `1×4` (Compact banner): `DuoodoWidgetReceiver1x4` → `duoodo_widget_info_1x4.xml`

**Critical Manifest Requirement:** All widget receivers in `AndroidManifest.xml` MUST declare the platform intent filter action:
```xml
<action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
```
*(Never use `android.intent.action.APPWIDGET_UPDATE`, as it will cause the Android OS and launchers to ignore the widgets).*

### 5.2. Adaptive Rendering & Transparency

- `DoodleRenderer.toBitmap` and `toTransparentBitmap` support arbitrary non-square dimensions `(widthPx, heightPx)`. Doodles are scaled proportionally to `minOf(widthPx, heightPx)` and centered, preventing distortion.
- In 1×4 banner widgets (`height < 90.dp`), the empty state renders as a compact horizontal `Row`.
- `Settings.widgetOpacity` provides a live slider from 0.0f (fully clear) to 1.0f (fully solid). Translucent values render with day/night frosted glass colors.
- `DuoodoWidget.refreshAll` updates all placed widgets across all 4 receiver classes using `getGlanceIdBy(appWidgetId)`.

---

## 6. Development, Testing & Release Workflows

### 6.1. Build Environment

- **JDK:** Java 17 (`JAVA_HOME=/home/hkd7kor/android/jdk-17`).
- **Gradle:** Use `~/android/gradle-8.11.1/bin/gradle` (the wrapper fails when reaching external proxy without explicit settings).
- **Proxy Configuration:**
  - Local HTTP/HTTPS proxy: `127.0.0.1:3128`.
  - When running git commands against GitHub: `git -c http.proxy=http://127.0.0.1:3128 ...`

### 6.2. Testing Guidelines

1. **Unit & Integration Suite:**
   ```bash
   JAVA_HOME=/home/hkd7kor/android/jdk-17 PATH=$JAVA_HOME/bin:$PATH ~/android/gradle-8.11.1/bin/gradle testDebugUnitTest
   ```
2. **End-to-End Simulation (`SimulatedEndToEndTest.kt`):**
   - **DO NOT attempt to launch nested QEMU emulators (`emulator -avd ...`) in headless virtual machines.** Nested virtualization exhausts host RAM/CPU and crashes the VM.
   - Use `SimulatedEndToEndTest.kt`. It runs in the JVM test harness using `FakeRelays`, verifying the entire handshake, safety verification, gesture manipulation, wire encoding/decoding, transport, and widget configuration in seconds.
3. **Privacy Verification:**
   ```bash
   ANDROID_HOME=/home/hkd7kor/android/sdk scripts/check-apk-privacy.sh app/build/outputs/apk/debug/app-debug.apk
   ```

### 6.3. Release Protocol

1. **Version Bump in `app/build.gradle.kts`:**
   - Formula: `versionCode = major * 10000 + minor * 100 + patch`
   - Example: `versionCode = 401`, `versionName = "0.4.1"`
2. **Changelog:**
   - Add `docs/changelogs/<versionCode>.txt`.
3. **Commit & Tag:**
   ```bash
   git commit -m "Bump version to X.Y.Z"
   git tag vX.Y.Z
   ```
4. **Push to Remote:**
   ```bash
   git -c http.proxy=http://127.0.0.1:3128 push origin master:main vX.Y.Z
   ```
5. **CI Automation:**
   - GitHub Actions (`.github/workflows/release.yml`) automatically triggers on `v*.*.*` tags.
   - It decodes the release key, tests, builds, signs, verifies permissions, checksums (`.sha256`), and publishes the GitHub Release to both the source repo (`Duoodo_src`) and the public app repo (`Duoodo`).
