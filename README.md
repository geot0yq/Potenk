# AI Anime Android Suite

Two native Android applications reconstructed from the supplied APK binaries:

- **AI Anime Developer** (`com.example.aideveloper`)
- **AI Anime Build Host** (`com.example.aideveloper.host`)

The source preserves the supplied Arabic/API workflow as far as the binary
evidence allows and adds a secure AI chat, a floating assistant, a separate
development-request action, fail-closed update validation, and an official
Android Package Installer handoff.

The original APKs did not include original Kotlin/Java source or signing
keystores. `android/recovery-reference/` contains the exact supplied APKs and
recovered binary resources as reference material. New/reconstructed code is
marked in documentation and comments; it is not presented as recovered source.

See:

- `android/README.md` — build and architecture
- `GITHUB_SETUP.md` — one-time signing setup
- `SECURITY_NOTES.md` — security boundary and original-signing limitation
- `.github/workflows/android.yml` — test, lint, APK validation, and release flow

Build Host now has a **Build and update** action. When it has a GitHub Actions
token stored in its Android Keystore, a received request starts `android.yml`,
waits for the matching successful run, and passes only that run's
`Developer.apk` through the existing package/signature/version gate. Android
may still show its normal installer confirmation.