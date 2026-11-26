# ARIA Windows Companion — planned companion module

This folder reserves the Windows companion integration. The Android app in this release does not yet control Windows PCs.

Planned secure implementation:
- Explicit pairing using a one-time code and a long random token.
- TLS or an authenticated private tunnel; no unauthenticated LAN control.
- Allowlisted actions only (launch approved apps, status checks).
- Wake-on-LAN requires the PC BIOS/network adapter to support it and the correct MAC address.
- Every privileged or destructive action requires confirmation.

Do not expose a control server directly to the public internet. This module is documentation only in v0.3.0.
