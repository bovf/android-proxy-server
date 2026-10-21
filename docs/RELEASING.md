# Releasing Bovf Proxy

English | [简体中文](RELEASING.zh-CN.md)

The fork uses `io.github.bovf.proxy` and its own fixed signing certificate. Never use upstream's key or a debug key for releases.

## Signing And Backup

Keep the private keystore and password files outside the repository with directory mode `0700` and file mode `0600`. Make an encrypted offline backup of the entire signing directory, including the alias and passwords. Losing the key prevents normal updates to installations signed with it; generating another key is not a replacement. No offline backup is created automatically.

The public certificate's SHA-256 is pinned in `.github/release-certificate.sha256`. This is not a secret. Keep the same certificate for subsequent releases.

Use authenticated `gh` with access to this fork. Configure the existing repository Actions Secrets using standard input, never password arguments or chat. The example paths below are placeholders, not files to create in the repository:

```sh
base64 < private/release.p12 | gh secret set ANDROID_KEYSTORE_BASE64 --repo bovf/android-proxy-server
gh secret set ANDROID_KEYSTORE_PASSWORD --repo bovf/android-proxy-server < private/keystore-password
gh secret set ANDROID_KEY_ALIAS --repo bovf/android-proxy-server < private/key-alias
gh secret set ANDROID_KEY_PASSWORD --repo bovf/android-proxy-server < private/key-password
```

For PKCS12, the key password may be the same as the keystore password. Never print, commit, upload as an asset, or cache the private keystore or password files. The workflow uses environment-based password input to `keytool`, and disables configuration/build caches for production signing. PR jobs never receive signing secrets or publish.

## One Final Push

1. Confirm `origin` is the personal fork and `master` is the default branch. Preserve unrelated local changes. Inspect the remote branch, tags, releases, Actions permissions and protection rules.
2. Update `versionName` and increment `versionCode` in `app/build.gradle.kts`. For this release they are `1.1.3` and `5`. Keep English/Chinese docs and `docs/releases/<version>.md` consistent.
3. Run the relevant regressions, all unit tests and practical local builds with synthetic data. Review the complete diff and public assets for secrets and identifying network/environment data. Do not weaken the self-connection guard or disable checks.
4. Commit all intended changes. Inspect every commit being sent, then create the annotated tag on the final release commit. Confirm the tag does not already exist locally/remotely and both refs target that commit.

```sh
git tag -a v1.1.3 -m "Bovf Proxy v1.1.3"
git rev-parse HEAD 'v1.1.3^{commit}'
git push --atomic origin HEAD:refs/heads/master refs/tags/v1.1.3
```

This replaces the old branch-push, wait, tag-push sequence. **Do not make an earlier test push.** If protection blocks the atomic push, stop; no partial push, force push, retagging or replacement releases.

The tag workflow checks out the exact event commit. The test job must pass before the release job signs that same commit, checks tag/version, certificate, application ID and embedded revision, generates a checksum, and creates and verifies GitHub-native build provenance for the final APK. Only then does it create a new release containing the signed APK and `.sha256` file. Debug APKs and dependency reports are temporary Actions artifacts only. Existing releases/assets are never overwritten.

If the tagged run fails after the one push, investigate and report the failure; do not make another push or publish an unverified APK without separate authorization. Attestations are supported for public repositories on current GitHub plans; an unsupported account/plan or missing permissions must be reported, not silently bypassed.

## Verify Publication

Download both release assets into a clean directory. Verify the checksum, APK signature and certificate against the pinned digest. With Android build tools, inspect the manifest for application ID `io.github.bovf.proxy`, versionName/versionCode and `io.github.bovf.proxy.SOURCE_REVISION` equal to the release commit. Check the release tag's peeled commit and successful tagged Actions run.

```sh
sha256sum --check android-proxy-server-1.1.3-release.apk.sha256
apksigner verify --verbose --print-certs android-proxy-server-1.1.3-release.apk
gh attestation verify android-proxy-server-1.1.3-release.apk \
  --repo bovf/android-proxy-server \
  --signer-workflow bovf/android-proxy-server/.github/workflows/android-apk.yml \
  --source-ref refs/tags/v1.1.3 --source-digest RELEASE_COMMIT --deny-self-hosted-runners
```

Replace `RELEASE_COMMIT` with the verified full commit hash. Confirm provenance's APK digest and source commit match the downloaded file and tag. This proves a GitHub workflow/source relationship, **not an independently reproduced build**. Unit tests and local builds do not establish physical-phone, screen-off, hotspot or VPN behavior.
