# Bovf Proxy

English | [简体中文](README.zh-CN.md)

A personal fork of [Android Proxy Server](https://github.com/hect0x7/android-proxy-server): a minimal, open-source, ad-free HTTP/SOCKS5 proxy for Android. Modified by bovf for credential stripping, response backpressure, independent identity, and verified signed releases.

Application ID: `io.github.bovf.proxy` (installs alongside upstream; settings are separate). Kotlin namespaces and upstream attribution are retained.

<img src="art/app-icon.png" width="160" alt="Android Proxy Server icon">

## Screenshot (upstream appearance; the fork is titled Bovf Proxy)

| App |
|:---:|
| <img src="art/app-screenshot.jpg" width="180" alt="Android Proxy Server running HTTP and SOCKS5 proxies"> |

## Features

- HTTP proxy on port `8080` by default
- HTTPS and SSH TCP tunneling through `CONNECT`
- SOCKS5 TCP `CONNECT` on port `1080` by default
- Independent HTTP and SOCKS5 switches and configurable ports
- Listens on local network interfaces for LAN clients
- Rejects connections originating from the phone itself to prevent recursive proxy loops
- Uses Android's current default network, including another active VPN
- Runs as a user-controlled foreground service
- Runs a local listener self-test before reporting startup success
- Displays a persistent health bar with live transfer rates and active connections
- Displays cumulative traffic statistics and in-memory session logs

## Quick Start

Connect the Android phone and client devices to the same trusted LAN, start HTTP and/or SOCKS5 in the app, then enter the phone's LAN IP and the displayed port in the client proxy settings.

```text
Client device        Home router          Android phone             Internet
192.168.1.x   <---->  Same Wi-Fi/LAN  <---->  Proxy server  <---->  VPN/default route
                                               8080 HTTP
                                               1080 SOCKS5
```

For an existing unauthenticated HTTP proxy helper, edit the HTTP port in the app to **3128** and point the helper at the phone's displayed LAN address. Leave username/password empty. The default remains `8080`; no shell or Nix configuration changes are needed.

See the illustrated [Getting Started Guide](docs/GETTING_STARTED.md) for protocol selection, setup examples, and troubleshooting.

## Privacy And Security

The app contains no advertising, analytics, telemetry, crash reporting, accounts, purchases, rating prompts, remote configuration, or automatic-update SDKs. Settings remain in Android private app storage, while traffic statistics and session logs remain in memory and are cleared when the service stops.

The proxy has no authentication and listens on `0.0.0.0`. Enable it only on trusted networks and never expose its ports directly to the internet.

Read the [Privacy Policy](PRIVACY.md) and [Security Policy](SECURITY.md) for details.

## VPN Compatibility

This app does not use Android `VpnService` and does not create a VPN interface. Outbound proxy connections follow Android's current default route, so the server can remain active while another VPN app is connected and send traffic through that VPN.

## Lock-screen operation

While either proxy listener is running, the foreground service holds a partial wake lock so the proxy can continue processing connections after the screen turns off. On Android 6.0 and later, open **Background Operation** in the app and tap **Allow** to exempt the app from battery optimization; otherwise Doze may suspend network access during extended idle periods. Some device vendors also require enabling unrestricted battery usage, background activity, or autostart in system settings.

## Build And Release

`master` and PR CI run unit tests and build a temporary Debug APK without signing secrets. Release preparation uses local validation, then **one atomic push** of the completed default branch and annotated version tag; there is no preliminary test push.

```sh
# After validation and committing all release changes:
git tag -a v1.1.2 -m "Bovf Proxy v1.1.2"
git push --atomic origin HEAD:refs/heads/master refs/tags/v1.1.2
```

The tag workflow tests the exact tagged commit, signs with the fork's dedicated key, verifies its pinned certificate, application ID, version and embedded source revision, then attests the final APK and verifies its GitHub provenance before publication. The release contains the signed APK and a SHA-256 checksum; existing assets are never overwritten. Provenance identifies the workflow/source, not an independently reproduced build.

See [Release Instructions](docs/RELEASING.md) for signing-key backup, secure secret configuration and download verification, and [1.1.2 release notes](docs/releases/1.1.2.md).

Local tests and builds (Release is unsigned without signing environment variables):

```sh
bash ./gradlew :proxycore:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

The project contains two modules:

- `app`: Compose UI and Android foreground service
- `proxycore`: HTTP, HTTPS `CONNECT`, and SOCKS5 TCP forwarding

The proxy core does not depend on Android `VpnService`.

## Limitations

- SOCKS5 supports unauthenticated TCP `CONNECT` only
- SOCKS5 `UDP ASSOCIATE` and `BIND` are not supported
- HTTP and SOCKS5 username/password authentication is not supported
- Physical-phone, screen-off/Doze, hotspot and concurrent-VPN behavior require device testing; local socket tests do not establish those guarantees
- Listener startup checks confirm local acceptance, not internet/VPN reachability

## License

Copyright 2026 hect0x7. Licensed under the Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
