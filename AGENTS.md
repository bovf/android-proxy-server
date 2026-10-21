# Project Agent Instructions

These instructions apply to the entire `android-proxy-server` repository. They supplement higher-level agent instructions. When instructions conflict, follow the instruction with the narrower scope unless it violates a higher-priority safety rule.

Modified for the bovf fork: independent app identity and single-push release gates.

## Project Contract

- The default branch is `master`.
- Bovf Proxy is an independent Android HTTP and SOCKS5 proxy server (`io.github.bovf.proxy`).
- HTTP uses port `8080` by default.
- SOCKS5 uses port `1080` by default.
- Both protocols are unauthenticated. Do not add authentication, accounts, analytics, advertising, telemetry, crash reporting, remote configuration, or tracking without an explicit user request.
- The app must not use Android `VpnService`, create a VPN interface, or claim the VPN slot.
- Preserve operation alongside another active VPN. Proxy egress must continue to follow Android's current default route.
- Keep the product focused on proxy serving. Do not add hotspot management, tethering controls, unrelated network tools, or inherited branding and features from other projects.

## Build And Test Policy

- Do not run Gradle builds, Android builds, unit tests, emulator tests, or install build dependencies locally unless the user explicitly asks for a local build or local test.
- Use `.github/workflows/android-apk.yml` for tests and APK builds.
- A push to `master` runs CI without release-signing credentials and produces temporary Debug Actions Artifacts.
- A documentation-only change does not require a version bump or release tag unless the user explicitly requests a release.
- Do not describe a build as successful until the relevant GitHub Actions run has completed successfully.

## Privacy Requirements

- Treat privacy and minimal data exposure as release-blocking requirements.
- Never reuse real values observed in the user's machine, network, screenshots, logs, configuration, credentials, conversation, or prior commits in public examples or repository content.
- Protected environment fingerprints include real private or public IP addresses, private subnet patterns, internal domains, hostnames, SSIDs, device names, system usernames, absolute filesystem paths, enterprise names, regions, time zones, and environment-specific port combinations.
- Use generic examples such as `192.168.1.x`, suitable RFC documentation addresses, `example.com`, `user`, and `host`.
- Do not commit credentials, cookies, tokens, signing material, keystores, generated authentication files, local configuration, or sensitive logs.
- Remove identifying metadata from images before committing or publishing them. Review visible image content for identifying network, account, device, and environment details.
- Before every commit, push, tag, Actions artifact publication, or GitHub Release, scan the relevant diff and generated public files for IPs, domains, email addresses, usernames, absolute paths, credentials, and other environment fingerprints.
- If sensitive data entered Git history or a published artifact, stop publication and assess history rewriting, artifact or run deletion, release replacement, credential rotation, and signing impact. A normal follow-up commit alone is not sufficient cleanup.

## Documentation

- English is the default documentation language.
- Every user-facing Markdown document should have a matching Simplified Chinese file using the `.zh-CN.md` suffix, unless the file is intentionally language-neutral.
- Keep English and Chinese documents behaviorally equivalent when changing commands, ports, capabilities, limitations, privacy claims, build instructions, or release instructions.
- Keep language-switch links valid in both directions.
- Use generic, privacy-safe values in diagrams, screenshots, command examples, tests, and troubleshooting steps.

## Signing Secrets

- Release signing values must remain in GitHub Actions Secrets.
- Never commit a keystore or print, expose, copy into documentation, or persist secret values in artifacts or logs.
- The workflow uses these secret names:
  - `ANDROID_KEYSTORE_BASE64`
  - `ANDROID_KEYSTORE_PASSWORD`
  - `ANDROID_KEY_ALIAS`
  - `ANDROID_KEY_PASSWORD`

## Release Procedure

Perform a release only when the user explicitly requests one.

1. Update both `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Confirm documentation and release notes match the intended version and behavior.
3. Run the privacy scan against the complete release diff and public assets.
4. With user authorization, run local regressions, unit tests, and practical builds before publication.
5. Commit the complete release changes and inspect every commit to be sent. Do not make a preliminary test push.
6. Create an annotated tag named exactly `v${versionName}` on the final intended release commit.
7. Confirm origin is the personal fork, both refs target that commit, and the remote branch has not moved. Perform ONE atomic push: `git push --atomic origin HEAD:refs/heads/master refs/tags/v${versionName}`. If protection prevents it, stop; do not fall back to partial or force pushes.
8. Monitor the tag workflow: it must test the exact tagged commit before signing, compare the APK certificate to the private keystore and pinned public certificate digest, check identity/version/revision, and verify GitHub-native provenance before publishing.
9. Confirm the GitHub Release contains only the signed `android-proxy-server-${versionName}-release.apk` and its `.sha256` checksum. Never overwrite existing releases, tags, or assets.
10. Keep the Debug APK and dependency report in GitHub Actions Artifacts only. PR builds must not receive signing secrets or publish.
11. Download the published APK and independently check its checksum, certificate, application ID, version, embedded revision, and provenance against the release commit. Provenance is not an independently reproduced build.
12. If the final push's workflow fails, investigate and report it. Do not make another push, retag, or publish an unverified APK without separate authorization. See `docs/RELEASING.md`.

## Git Safety

- Use the repository's configured commit identity unless the user specifies one. For user-authored commits in this repository, use `hect0x7 <93357912+hect0x7@users.noreply.github.com>` when requested.
- Do not force-push, delete remote branches, tags, releases, Actions runs, artifacts, or other remote data without explicit authorization for that specific operation.
- Prior authorization for one cleanup or history rewrite does not authorize future destructive operations.
- Before pushing, confirm the target remote and branch, inspect the commits being sent, and repeat the privacy scan.
