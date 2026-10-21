# Security Policy

English | [简体中文](SECURITY.zh-CN.md)

## Supported Versions

Security fixes are applied to the latest version on the `master` branch. Older APK artifacts are not maintained after a newer release is available.

## Deployment Boundary

Bovf Proxy (a modified Android Proxy Server fork) is designed for a trusted local network. HTTP and SOCKS5 services do not implement authentication and listen on all local interfaces when enabled.

- Do not expose proxy ports directly to the internet.
- Do not enable the server on untrusted public Wi-Fi or hotspot networks.
- Use Android firewall or network-isolation controls when clients on the local network are not trusted.
- Stop both proxy services when they are not needed.

## VPN And Loop Prevention

The app does not use `VpnService`. Outbound connections follow Android's default route, including another active VPN. Connections originating from the phone itself are rejected to reduce recursive proxy loops, but external proxy chains must still be configured carefully.

## Release Integrity

Modified for the bovf fork: tagged Release APKs are built only after tests pass on the exact tagged commit. Before publication, the workflow verifies the APK signature against the private keystore and pinned public certificate digest, checks the fork's application ID, version and source revision, and verifies GitHub-native provenance for the final APK. A SHA-256 checksum accompanies the APK. Provenance does not prove an independently reproduced build. Signing keys and passwords are not stored in the repository or provided to PR jobs.

Plain-HTTP forwarding removes Proxy-Authorization while retaining origin Authorization. This is not proxy authentication: access remains unauthenticated. See [Release Instructions](docs/RELEASING.md) for verification and signing-key backup.

## Reporting A Vulnerability

Do not publish exploit details, credentials, private network addresses, or captured proxy traffic in a public issue. Use the repository's private vulnerability-reporting channel when available. If no private channel is available, open a public issue containing only a request for private contact and a minimal non-sensitive summary.

Include the affected version, Android version, reproduction conditions, expected behavior, and security impact. Remove tokens, passwords, traffic payloads, and personal information from all reports.
