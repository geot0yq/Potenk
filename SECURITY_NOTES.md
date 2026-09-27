# Security and recovery boundary

- APKs supplied by the user were binary inputs. Their DEX/resources are kept
  under `android/recovery-reference/`; their original source was not present.
- The new Java source is reconstructed or new functionality, and is labelled
  accordingly in documentation/comments.
- The original Developer certificate was different from the local release
  certificate. Build Host therefore rejects an update signed by the wrong key.
- The Developer API key is stored through Android Keystore AES-GCM and is not
  embedded in source or GitHub Actions.
- The device never evaluates AI-generated Kotlin/Java. The AI response becomes
  a development plan and a signed request contract; source changes happen in
  GitHub/Gradle CI after review.
- Build Host only invokes Android's official package installer after package,
  hash, version, parseability, and installed signer checks. System confirmation
  is not bypassed.
- Candidate APKs are stored in application cache and are deleted on rejection
  or after post-install verification.