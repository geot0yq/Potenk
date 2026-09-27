# AI Anime Android Suite

Native Android Developer and Build Host applications reconstructed from the supplied APKs, with secure AI-assisted development requests and fail-closed updates.

## Run & Operate

- `pnpm --filter @workspace/api-server run dev` — run the API server (port 5000)
- `pnpm run typecheck` — full typecheck across all packages
- `pnpm run build` — typecheck + build all packages
- `pnpm --filter @workspace/api-spec run codegen` — regenerate API hooks and Zod schemas from the OpenAPI spec
- `pnpm --filter @workspace/db run push` — push DB schema changes (dev only)
- Required env: `DATABASE_URL` — Postgres connection string

### Android

- `cd android && ./gradlew :developer:assembleDebug :build-host:assembleDebug`
- `cd android && ./gradlew :developer:assembleRelease :build-host:assembleRelease`
- `ANDROID_SDK_ROOT` must point to an Android SDK with platform 35 and build-tools 35.0.0.
- `android/signing.properties` and `android/*.keystore` are local-only and ignored.

## Stack

- pnpm workspaces, Node.js 24, TypeScript 5.9
- API: Express 5
- DB: PostgreSQL + Drizzle ORM
- Validation: Zod (`zod/v4`), `drizzle-zod`
- API codegen: Orval (from OpenAPI spec)
- Build: esbuild (CJS bundle)
- Android: Gradle 8.10.2, Android Gradle Plugin 8.7.3, Java 19/17 CI, Android API 35

## Where things live

- `android/developer` — `com.example.aideveloper`, AI chat, floating assistant, development request contract, release update feed
- `android/build-host` — `com.example.aideveloper.host`, update validation and official Package Installer handoff
- `android/recovery-reference` — exact supplied APK/DEX/resource reference, not source
- `.github/workflows/android.yml` — one stable CI workflow for tests, lint, APK checks, artifacts, and signed Releases
- `GITHUB_SETUP.md` — one-time signing secret setup
- `SECURITY_NOTES.md` — recovered-vs-new boundary and safety model

## Architecture decisions

- Android never executes AI-generated source; it sends a structured request to GitHub/Build Host and keeps the device fail-closed.
- Build Host validates package identity, upgrade version, SHA-256, parseability, and installed signer before invoking the official installer.
- API keys are user-entered and encrypted with Android Keystore; none are stored in source or Actions.
- Release CI refuses to build without repository signing secrets; local release output uses an ignored local key only for installable smoke builds.

## Product

The Developer app preserves the original API settings and Arabic workflow while adding an interactive assistant, AI chat, a separate safe-development action, event-driven error reporting, and a fixed GitHub release updater. Build Host is the companion gate that accepts requests and candidate APKs, rejects unsafe updates, and uses Android's official installer.

## User preferences

_Populate as you build — explicit user instructions worth remembering across sessions._

## Gotchas

- The original APK source and signing keys were not supplied. Updating over the original installed Developer/Host APKs is intentionally blocked until the original matching keys are available.
- Debug variants have `.debug` application IDs; use Release APKs for the two-app integration.
- A GitHub Release needs the four signing secrets described in `GITHUB_SETUP.md`.

## Pointers

- See the `pnpm-workspace` skill for workspace structure, TypeScript setup, and package details
