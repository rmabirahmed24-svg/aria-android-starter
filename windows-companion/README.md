# ARIA Windows Companion — planned, not implemented

The Android app in v0.5.0 does not remotely control Windows. This folder is a design note for a future companion service.

Planned safe architecture:
- Windows 10/11 tray application paired with Android over authenticated local network or a secure relay.
- Explicit device pairing, revocable tokens and per-action permissions.
- Allowlisted actions only (launch an app, show status, open a selected file); confirmation before destructive, financial, messaging or privacy-sensitive actions.
- No hidden screen capture, keylogging, credential extraction, arbitrary shell execution or silent file access.
- Separate Windows installer, signed builds, audit log and user-visible connection state.

A Windows companion executable is not included in this Android source archive.
