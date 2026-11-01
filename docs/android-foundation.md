# Android Foundation · v0.1

## Scope
- Native Android application using Kotlin and Jetpack Compose
- Workspace selection with local demonstration data
- Workspace-specific Today screen
- No real authentication, backend, or persisted data yet

## Next milestones
1. Extract design tokens and reusable UI components into SW.Design.
2. Add navigation and actual workflows for recruiting contacts and training RSVP.
3. Define versioned bootstrap API contracts and repository interfaces.
4. Implement OIDC login, organization membership and server-side authorization.
5. Add offline drafts, synchronization and accessibility tests.

## Architecture constraints
- UI configuration must be validated against a known component registry.
- Organization data isolation and permissions are enforced by the server.
- Do not send integration credentials to mobile clients.
- SW.VA integrations and white-label variants are deferred.
- No UI-only permission checks as a security boundary.

## Local development
Open the repository root in Android Studio with JDK 17 and Android SDK 35.
Sync Gradle, then run the `apps:android` configuration on an emulator or device.
The Gradle wrapper is not yet checked in; generate and commit it before CI/reproducible CLI builds.
