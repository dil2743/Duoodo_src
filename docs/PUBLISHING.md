# Publishing on GitHub

## Signing key (already created)

The release key is in `~/duoodo-signing/` (never commit it; `.gitignore` excludes `*.jks`):

| File | GitHub secret |
|---|---|
| `RELEASE_KEYSTORE_BASE64.txt` | `RELEASE_KEYSTORE_BASE64` |
| `RELEASE_KEYSTORE_PASSWORD.txt` | `RELEASE_KEYSTORE_PASSWORD` |
| `RELEASE_KEY_ALIAS.txt` (`duoodo`) | `RELEASE_KEY_ALIAS` |
| `RELEASE_KEY_PASSWORD.txt` | `RELEASE_KEY_PASSWORD` |

Every release must be signed with this same key, or Android refuses to install an update over the
old version. **Back up `~/duoodo-signing/` now** (password manager or offline storage).

It was created with:

```bash
keytool -genkeypair -keystore duoodo-release.jks -alias duoodo \
  -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12
base64 -w0 duoodo-release.jks > RELEASE_KEYSTORE_BASE64.txt
```

## Push and release

1. Create a repo on GitHub and push:
   ```bash
   git remote add origin https://github.com/<you>/duoodo.git
   git push -u origin main
   ```
2. Add the secrets: repo → Settings → Secrets and variables → Actions, or with the GitHub CLI:
   ```bash
   cd ~/duoodo-signing
   for s in RELEASE_KEYSTORE_BASE64 RELEASE_KEYSTORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD; do
     gh secret set "$s" --repo <you>/duoodo < "$s.txt"
   done
   ```
3. Release: bump `versionCode`/`versionName` in `app/build.gradle.kts`
   (`versionCode = major*10000 + minor*100 + patch`), optionally add `docs/changelogs/<versionCode>.txt`, then
   ```bash
   git tag v0.2.0
   git push origin v0.2.0
   ```

The **Release** workflow checks the tag matches `versionName`, runs the tests, builds and signs the APK,
checks its permissions, and publishes a GitHub Release with the APK, its SHA-256 and the signing
certificate fingerprint. The **Build** workflow runs tests and an unsigned release build on every push
and pull request.

## Signing locally

```bash
export RELEASE_KEYSTORE=~/duoodo-signing/duoodo-release.jks
export RELEASE_KEYSTORE_PASSWORD=$(cat ~/duoodo-signing/RELEASE_KEYSTORE_PASSWORD.txt)
export RELEASE_KEY_ALIAS=duoodo
export RELEASE_KEY_PASSWORD=$(cat ~/duoodo-signing/RELEASE_KEY_PASSWORD.txt)
./gradlew assembleRelease
```

## Public releases from a private source repo

Source: `dil2743/Duoodo_src` (private). App: `dil2743/Duoodo` (public, README + releases only).
On every `v*.*.*` tag, `release.yml` creates the release in the source repo and the same release
(APK, `.sha256`, notes) in the public repo.

One-time setup:
1. Push `docs/public-repo/README.md` as the first commit of `dil2743/Duoodo` (a release needs a commit).
2. Create a fine-grained token: Repository access = only `dil2743/Duoodo`, Permissions = Contents: Read and write.
3. Add it to `dil2743/Duoodo_src` as the secret `PUBLIC_REPO_TOKEN`.
4. Make `dil2743/Duoodo_src` private. Build provenance attestation is skipped for private repos;
   the public release still carries the SHA-256 and signing-certificate fingerprint.
