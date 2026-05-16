# Firebase Custom Claims for Aura Admin Moderation

Roadmap N-2. The canonical source of truth for community-moderation authorization
is a `admin: true` Firebase Auth Custom Claim, enforced server-side by
[`database.rules.json`](../database.rules.json). Client-side checks in
`VoteRepository.isAdmin` are only used to show / hide the moderation UI;
they cannot bypass the rules.

## Granting a user the admin claim

Run once per admin from a trusted environment (your machine, Cloud Shell,
or a Cloud Function). You need a Firebase service-account key with the
**Firebase Authentication Admin** role.

### Node script (recommended)

```js
// grant-admin.js
const admin = require("firebase-admin");
admin.initializeApp({
  credential: admin.credential.cert(require("./service-account.json")),
});

const uid = process.argv[2];
if (!uid) {
  console.error("Usage: node grant-admin.js <uid>");
  process.exit(1);
}

admin
  .auth()
  .setCustomUserClaims(uid, { admin: true })
  .then(() => {
    console.log(`Granted admin to ${uid}.`);
    console.log("The user must sign out and back in (or wait up to 1 hour) for the claim to propagate.");
    process.exit(0);
  })
  .catch((err) => {
    console.error(err);
    process.exit(1);
  });
```

Run:

```bash
node grant-admin.js <uid>
```

### Revoking

```js
admin.auth().setCustomUserClaims(uid, null);
// or:
admin.auth().setCustomUserClaims(uid, { admin: false });
```

After revocation, force the user to re-sign-in (or revoke their refresh
tokens with `admin.auth().revokeRefreshTokens(uid)`) so the stale ID
token can't continue to assert `admin: true` until expiry.

## Propagation

After `setCustomUserClaims` is called, the new claim is **not** visible
on the client until that user gets a fresh ID token. Three paths:

1. **Automatic on next refresh** — Firebase Auth rotates ID tokens every
   1 hour. Worst case the claim is live within an hour.
2. **Force on sign-in** — Aura calls `VoteRepository.refreshAdminFromClaims()`
   in `FreeVibeApp.warmCommunityIdentity()`, which invokes
   `currentUser.getIdToken(true)` (forceRefresh = true). New admins
   become live on the next cold start.
3. **Explicit force** — programmatic UI button can call
   `voteRepository.refreshAdminFromClaims()` from a coroutine.

## Deploying the security rules

The rules in `database.rules.json` are not deployed automatically. Deploy
manually whenever they change:

```bash
firebase login                                  # one-time
firebase use freevibe-aura                     # project id; verify with `firebase projects:list`
firebase deploy --only database                # uploads database.rules.json
```

CI verification (optional): add a step that runs
`firebase deploy --only database:rules --project=verify --dry-run` on
every PR that touches `database.rules.json`.

## One-cycle device-ID fallback

`VoteRepository.adminDeviceIdHashes` (SHA-256-hashed Settings.Secure.ANDROID_ID
values) remains as a fallback during the migration. Once every admin has
rotated through a Custom-Claim-bearing ID token (≤ 1 h after their first
sign-in following the backend grant), remove the hash list and the
`adminUserIds` set.

Tracking: file an issue against this repo titled "Remove device-ID admin
fallback" after granting admins their custom claims; close it once all
admins confirm they still see the moderation UI on a fresh install.
