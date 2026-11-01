<p align="center">
  <img src="docs/images/logo.svg" width="120" alt="Duoodo logo: a little white bird chirping “nizz” on an indigo tile">
</p>

<h1 align="center">Duoodo</h1>

<p align="center">
  <b>Little doodles for your favourite person.</b><br>
  Draw something, hit send, and it appears on their home screen a few seconds later.<br>
  Private by design: no accounts, no servers of ours, end-to-end encrypted.
</p>

<p align="center">
  <img src="docs/images/drawWithShapes-light.png" width="220" alt="Blush canvas home with inbox and settings icons, drawing tools, and the peeked indicator">
  &nbsp;
  <img src="docs/images/inboxWithDoodle-light.png" width="220" alt="Inbox panel with a received doodle and refresh, save, and clear controls">
  &nbsp;
  <img src="docs/images/settings-light.png" width="220" alt="Settings panel with blush widget-size buttons, widget styles, opacity, and tint controls">
</p>

Screenshots show the current source build with sample doodles; published releases may look different.

---

## Why Duoodo?

Texting is great, but sometimes you want to send something a bit more *you*: a quick sketch of
your coffee, an arrow pointing at "tonight?", a wobbly heart, a silly face. Duoodo is a tiny app
for exactly that, between exactly **two people**.

You draw. Your partner glances at their home screen and there it is, sitting in a widget next to
their clock. No feed, no likes, no history piling up. Just the latest little note from you,
replaced the moment you send a new one.

It was built with one firm rule: **nobody else gets to see it.** Not us, not an ad network, not the
servers that carry the message.

## Features

**One canvas, everything within reach**
- The canvas is the home screen. Open **Inbox** or **Settings** from the top-right icons, then close the panel to return to your drawing.
- Brushes and shapes are grouped in compact menus. The drawing stays in place while you change tools or open a panel.

**Drawing that's actually fun**
- ✏️ **Pen**, 🖍 **Marker** (soft, see-through highlighter) and 🌈 **Rainbow** (every stroke cycles through colours)
- ➡️ **Arrows**, **lines**, **boxes** and **circles** for pointing things out
- 🔤 **Text** in three sizes and any colour, with emoji support
- 🥰 **72 couple-focused emojis** for affection, everyday reactions, reassurance, dates, and cozy moments together
- **Emoji stamps and photos**: choose an item and tap to insert it once; the canvas then switches to Select
- **Move and resize**: in Select mode, tap an existing item and drag to move it. Pinch to resize or rotate, drag its corner handle, or use the size slider. Each edit supports undo / redo. Choose a drawing tool to paint over photos.
- 🧽 **Eraser**, **undo / redo**, and smooth curves so your lines look hand-drawn, not jagged

**Arrives in seconds, even when the app is closed**
- **Instant delivery** keeps a light connection open, so a new doodle lands on your partner's
  widget within a few seconds. It uses a silent notification, as Android requires for anything
  running in the background.
- Prefer to save battery? Switch it off and Duoodo checks about every 10 minutes instead.
- With **read receipts** enabled, a **🙈 peeked indicator** beside Send lets you know your partner has seen your doodle.

**A widget that fits your wallpaper**
- **Solid**: a clean white card.
- **Frosted**: a translucent card that lets your wallpaper glow through.
- **Clear**: just the ink, floating on your wallpaper.
- Choose **2×2, 2×4, 2×3, or 1×4**, then adjust the opacity and background tint in Settings.

**Feels at home on your phone**
- Soft blush and peach in light mode, muted rose in dark mode, following your system setting. The drawing paper stays white in both.
- **Save** a doodle you love as a PNG in its own `Pictures/Duoodo` folder, or **Clear** it.
- Pair by **scanning a QR code** or by **copying a code** over WhatsApp, Signal, or anything else.

<p align="center">
  <img src="docs/images/drawEmojiTool-light.png" width="200" alt="Canvas home with the curated couple-focused emoji picker">
  &nbsp;
  <img src="docs/images/selectedEmoji-light.png" width="200" alt="Selected emoji with an outline, corner resize handle, and size slider">
  &nbsp;
  <img src="docs/images/drawWithShapes-dark.png" width="200" alt="Canvas home in the muted rose dark theme">
  &nbsp;
  <img src="docs/images/settings-dark.png" width="200" alt="Settings and widget controls in dark mode">
</p>

### Widget style previews

These are illustrations made with the app's doodle renderer, not launcher screenshots. Actual widget dimensions and spacing depend on your launcher.

<p align="center">
  <img src="docs/images/widgetStyles-light.png" width="220" alt="Illustration of Solid, Frosted, and Clear widget styles on a light wallpaper">
  &nbsp;
  <img src="docs/images/widgetStyles-dark.png" width="220" alt="Illustration of Solid, Frosted, and Clear widget styles on a dark wallpaper">
</p>

## How it works (the short version)

1. **Pair once.** One of you taps **Invite my partner** and shows a QR code (or copies a code).
  The other scans it or pastes it. Compare the **six safety emojis** on both phones and confirm
  they match before exchanging doodles. You stay connected until one of you taps **Unpair**.
2. **Draw and send.** Your doodle is encrypted *on your phone* with a key only the two of you have.
3. **It travels through public relays.** These are volunteer-run servers on the open
   [Nostr](https://nostr.com) network. They pass along a scrambled blob they can't open, and they
   can't tell who it's from or who it's for.
4. **Your partner's phone picks it up**, unlocks it, and shows it in the app and on the widget.

There's no Duoodo server anywhere in this picture. No sign-up, no phone number, no email.

## Privacy & security

Duoodo collects nothing, because there's nowhere for it to go.

| | |
|---|---|
| **End-to-end encrypted** | Every doodle is sealed with ChaCha20-Poly1305 using keys agreed through X25519 when you pair. Only your partner's phone can open it. |
| **No identity** | No account, phone number or email. Each direction uses its own throwaway signing key that changes every day. |
| **Same size every time** | Doodles are padded to fixed sizes, so a relay can't even guess how detailed your drawing was. |
| **Leaves no trail** | Only the latest doodle exists. Sending a new one replaces it, and every message expires from the relays after 7 days. |
| **Can't be spoofed** | Every message is authenticated. Anything that isn't from your partner is silently ignored. |
| **Pairing can't be hijacked** | An invite works once, expires after 24 hours, and the inviting phone must confirm. A safety-code (6 emojis) lets you double-check nobody is in the middle. |
| **Tight sandbox** | Only internet and notifications, plus what instant delivery needs to run in the background. No camera (the QR scanner runs inside Google Play services), no storage, no contacts, no location. Backups are disabled, and keys live in the Android Keystore. |
| **Tells you when it's over** | If your partner unpairs, your app is told, removes their last doodle and stops sending. |

What a relay *can* see: that some phone, at some IP address, sent an encrypted blob at some time.
That's the same metadata any internet app exposes. If you want to hide even that, route your
phone through a VPN or Tor.

## Data & battery

- **Doodle payloads are padded to 1, 4, 16, or 48 KB.** Simple sketches use smaller buckets; photos and more detailed doodles can use larger ones.
- **Staying connected costs about 1–2 MB a month**: a tiny "still here?" ping once a minute.
- **Battery impact is small.** If your phone is aggressive about closing apps (Xiaomi, Samsung,
  Oppo…), tap **Settings → Allow background running** so deliveries aren't paused.

## Install

Grab the latest signed APK from the
[Releases page](../../releases/latest), open it on your phone, and allow
installing from your browser if Android asks. **Both of you need the app.**

Requirements: Android 8.0 or newer, with Google Play services for QR scanning (you can always paste
the code instead).

### Check your download is genuine

Every release lists the APK's SHA-256 checksum and is signed with the same certificate:

```
sha256sum -c Duoodo-vX.Y.Z.apk.sha256
apksigner verify --print-certs Duoodo-vX.Y.Z.apk
# SHA-256 of the signing certificate:
# 84:c0:0d:38:e1:f3:0a:f9:1d:c9:b9:fd:2a:eb:a5:90:24:9e:18:28:73:75:63:5e:00:f4:a7:16:8b:5c:e8:c1
```

## FAQ

**Can more than two people share a pairing?**
No. Duoodo is deliberately one-to-one.

**Is there a history of old doodles?**
No, only the latest one, by design. If you want to keep one, tap **Save**.

**What if my partner's phone is off when I send?**
The doodle waits (encrypted) on the relays for up to 7 days and shows up as soon as their phone is
back online.

**Does it work on iPhone?**
Not yet. Duoodo is Android-only for now.

**What happens if I uninstall?**
Your keys are gone with the app, so you'd pair again with a new invite.

---

## Credits

Made with ☕ and a lot of squiggles by **Dilshad** · [fire.dilshad@gmail.com](mailto:fire.dilshad@gmail.com)

If Duoodo made someone smile, that's the whole point.
