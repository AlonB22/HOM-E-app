# HOM-E

<p align="center">
  <img src="logo.png" alt="HOM-E logo" width="220">
</p>

HOM-E is an Android family chores app built for a course final project. It focuses on a clear parent-child workflow:

- parents create chores and rewards
- children join the family with a join code
- children submit chore completion
- parents approve or reject submissions
- children earn points
- children request rewards
- parents approve or reject reward requests

The app is implemented with classic Android XML screens and Firebase. It is intentionally scoped as a polished MVP rather than a broad family-management platform.

## Project Goals

The project was designed around these constraints:

- XML UI only, no Jetpack Compose
- Android 11+ runtime support (`minSdk = 30`)
- Firebase Authentication for sign-in and registration
- Cloud Firestore for family, chore, reward, request, and points data
- clear separation between parent and child access
- clean code structure suitable for a public GitHub portfolio project

## Core Features

### Parent Flow

- Create a family account
- View the family join code from the parent home screen
- Create chores with:
  - title
  - short description
  - assigned child
  - point value
- Create rewards with:
  - title
  - short description
  - point cost
  - active/inactive state
- Review a combined approvals queue for:
  - chore submissions
  - reward requests
- Approve or reject requests

### Child Flow

- Create a child account and join an existing family with a 6-character join code
- View assigned chores
- Open chore details and submit completion
- View current points balance
- Browse active rewards
- Request a reward
- View child-safe account/profile info and sign out

## Product Scope

This MVP intentionally stays narrow.

### Included

- one parent account per family
- multiple child accounts per family
- one-time chores only
- points awarded only after parent approval
- reward requests approved only by parent
- role-based routing from Firebase session + Firestore membership

### Not Included

- finance / expense tracking
- recurring chores
- proof photo uploads
- notifications
- badges, streaks, leaderboards, or heavy gamification
- due dates
- multiple parent accounts

## Tech Stack

- Kotlin
- Android Views + XML layouts
- Navigation Component
- Material Components
- Firebase Authentication
- Cloud Firestore
- Firestore Security Rules
- Firestore Emulator rule tests

## Architecture

The app follows a practical student-project structure:

- `core/auth`
  - Firebase bootstrap
  - session restore
  - login/register/join flows
  - role/session routing
- `core/data`
  - Firestore repository access
- `core/navigation`
  - single-activity navigation host
- `feature/auth`
  - login
  - register / join family
- `feature/parent/*`
  - parent home
  - chores
  - rewards
  - approvals
- `feature/child/*`
  - child home
  - chores
  - rewards
  - profile

The app uses a single activity with multiple fragments and role-based navigation. Parent and child share one design system, while copy, emphasis, and actions differ by role.

## Firestore Data Model

Main collections:

- `users`
- `families`
- `familyMembers`
- `chores`
- `rewards`
- `rewardRequests`

Important ownership rules:

- `familyMembers` is the source of truth for:
  - family membership
  - role
  - points balance
- `users.role` and `users.familyId` are convenience fields only
- points change only inside parent approval flows
- reward requests store snapshot fields so later reward edits do not break old requests

### Main Status Flows

Chores:

- `open`
- `submitted`
- `approved`
- `rejected`

Reward requests:

- `requested`
- `approved`
- `rejected`

## Security Model

The app enforces role separation both in UI and in Firestore Security Rules.

Key protections:

- children cannot access parent-only write flows
- children can only submit chores assigned to them
- children can only create reward requests for themselves
- parent-only approval flows control points changes
- family scoping is derived from authoritative membership data

The repository includes Firestore rule tests for the main role-based flows.

## Firebase Setup

This repository does not include private Firebase credentials.

### Required

Create your own Firebase project and configure:

- Firebase Authentication
  - Email/Password provider
- Cloud Firestore

Register an Android app with package name:

`com.example.hom_e_app`

Then add Firebase config in one of these ways:

### Recommended

Place `google-services.json` at:

`app/google-services.json`

This file is ignored by Git and should not be committed.

### Alternative

Fill the runtime values in:

`app/src/main/res/values/firebase_runtime_config.xml`

If neither is configured, login/register actions will stay blocked by design.

## Build And Run

### Android app

Open the project in Android Studio and use the bundled JBR/JDK when possible.

Typical local issue:

- some environments default to older Java runtimes that fail Gradle startup
- Android Studio's bundled JBR is the safest option for this project

Build from the command line:

```bash
./gradlew assembleDebug
```

On Windows:

```powershell
.\gradlew.bat assembleDebug
```

## Firestore Rules Verification

Install the local Firebase tooling once:

```bash
npm install
```

Run the Firestore rules test suite:

```bash
npm run test:firestore-rules
```

Verified flows include:

- parent create/edit chore
- child submit assigned chore
- parent approve/reject chore
- child create reward request
- parent approve/reject reward request
- cross-family denial
- child denial on parent-only writes

## Deployment Notes

The repo includes:

- `firestore.rules`
- `firestore.indexes.json`
- `firebase.json`
- Firestore rule tests
- a backfill script for `users.memberId`

Before deploying stricter rules to an existing project, run the backfill flow described in:

`firestore/DEPLOYMENT.md`

Important note:

- stricter rules expect `users/{uid}.memberId` to exist
- existing users without that field must be backfilled before rollout

## Current State Of The App

At this stage the app already includes:

- real Firebase Authentication login/register flows
- real family creation and join-code onboarding
- real Firestore-backed parent chore/reward creation
- real Firestore-backed child chore submission
- real Firestore-backed reward requests
- real parent approval/rejection flows
- real points updates on approval
- Firestore-backed parent and child home summaries
- deployed Firestore rules and emulator rule tests

## Known Limitations

- one parent account per family in the current MVP
- no recurring chores
- no advanced analytics or notifications
- duplicate child display names can still create UX ambiguity in selection/dropdowns
- the package name is still `com.example.hom_e_app`


## Repository Notes

Files intentionally ignored from Git:

- `app/google-services.json`
- service account JSON files
- local IDE files
- Gradle/build output
- local machine config

## Future Improvements

- parent-side child management polish for duplicate names
- better profile/account experience
- optional recurring chore support
- more robust production deployment tooling
- save used rewards
- screenshots and demo video links in this README
  

## Author

Course final project by Alon Berla.
