# Firestore Verification And Deployment

## Rules verification

Install the local tooling once:

```bash
npm install
```

Run the Firestore rules emulator suite:

```bash
npm run test:firestore-rules
```

The suite verifies the critical MVP role flows:

- Parent create and edit chore
- Child submit assigned chore
- Parent approve and reject chore
- Child create reward request
- Parent approve and reject reward request
- Cross-family denial
- Child blocked from parent-only writes

## Composite indexes

`firestore.indexes.json` remains empty on purpose.

Current app queries use document reads plus equality filters only, and they sort in app memory after the query returns. No current Firestore query shape in [`FirestoreFeatureRepository.kt`](C:\Coding Projects\Afeka\AndroidStudioProjects\HOM-E app\app\src\main\java\com\example\hom_e_app\core\data\FirestoreFeatureRepository.kt) requires a composite index for the MVP demo build.

## Existing-user memberId backfill

The new rules require `users/{uid}.memberId` to match exactly one `familyMembers/{memberId}` record for the signed-in user. Existing users without `memberId` must be backfilled before deploying the stricter rules to production.

Dry run first:

```bash
npm run backfill:member-id:dry-run -- --project YOUR_FIREBASE_PROJECT_ID --service-account path/to/serviceAccount.json
```

Apply only after reviewing the dry-run output:

```bash
npm run backfill:member-id -- --project YOUR_FIREBASE_PROJECT_ID --service-account path/to/serviceAccount.json
```

Safety behavior:

- Dry run is the default mode.
- Only users with exactly one matching `familyMembers` record are auto-resolved.
- The script refuses to auto-fix users with zero matches, multiple matches, or role/family mismatches.
- A dry run exits non-zero when any users still need backfill, so it can be used as a deployment gate.

## Safe deployment order

1. Run `npm run test:firestore-rules`.
2. Run `npm run backfill:member-id:dry-run`.
3. If the dry run reports resolvable users, run `npm run backfill:member-id`.
4. Re-run `npm run backfill:member-id:dry-run` until it reports no missing `memberId` values.
5. Deploy Firestore rules and indexes:

```bash
npx firebase-tools deploy --only firestore:rules,firestore:indexes
```
