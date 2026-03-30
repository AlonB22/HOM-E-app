const fs = require("node:fs");
const path = require("node:path");
const process = require("node:process");
const admin = require("firebase-admin");

const args = parseArgs(process.argv.slice(2));

async function main() {
  initializeAdmin();

  const db = admin.firestore();
  const usersSnapshot = await db.collection("users").get();
  const usersMissingMemberId = usersSnapshot.docs.filter((doc) => {
    const memberId = doc.get("memberId");
    return typeof memberId !== "string" || memberId.trim() === "";
  });

  if (usersMissingMemberId.length === 0) {
    console.log("No users are missing memberId. Firestore membership migration is ready.");
    return;
  }

  console.log(
    `${args.apply ? "Applying" : "Dry run"} memberId backfill for ${usersMissingMemberId.length} user(s).`
  );

  const resolved = [];
  const unresolved = [];

  for (const userDoc of usersMissingMemberId) {
    const membershipSnapshot = await db.collection("familyMembers")
      .where("userId", "==", userDoc.id)
      .limit(2)
      .get();

    const matches = membershipSnapshot.docs;
    if (matches.length === 0) {
      unresolved.push({ uid: userDoc.id, reason: "No familyMembers match userId." });
      continue;
    }

    if (matches.length > 1) {
      unresolved.push({ uid: userDoc.id, reason: "Multiple familyMembers match userId." });
      continue;
    }

    const memberDoc = matches[0];
    const userFamilyId = userDoc.get("familyId");
    const userRole = userDoc.get("role");
    const memberFamilyId = memberDoc.get("familyId");
    const memberRole = memberDoc.get("role");

    if (typeof userFamilyId === "string" && userFamilyId !== memberFamilyId) {
      unresolved.push({
        uid: userDoc.id,
        reason: `familyId mismatch: user=${userFamilyId}, member=${memberFamilyId}.`,
      });
      continue;
    }

    if (typeof userRole === "string" && userRole !== memberRole) {
      unresolved.push({
        uid: userDoc.id,
        reason: `role mismatch: user=${userRole}, member=${memberRole}.`,
      });
      continue;
    }

    resolved.push({
      uid: userDoc.id,
      memberId: memberDoc.id,
      familyId: memberFamilyId,
      role: memberRole,
    });
  }

  if (resolved.length > 0) {
    console.log("");
    console.log("Resolvable users:");
    for (const entry of resolved) {
      console.log(`- ${entry.uid} -> ${entry.memberId} (${entry.role} in ${entry.familyId})`);
    }
  }

  if (args.apply && resolved.length > 0) {
    let batch = db.batch();
    let writesInBatch = 0;

    for (const entry of resolved) {
      batch.update(db.collection("users").doc(entry.uid), {
        memberId: entry.memberId,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      writesInBatch += 1;

      if (writesInBatch === 400) {
        await batch.commit();
        batch = db.batch();
        writesInBatch = 0;
      }
    }

    if (writesInBatch > 0) {
      await batch.commit();
    }
  }

  if (unresolved.length > 0) {
    console.log("");
    console.log("Unresolved users:");
    for (const entry of unresolved) {
      console.log(`- ${entry.uid}: ${entry.reason}`);
    }
  }

  console.log("");
  console.log(
    `Summary: ${resolved.length} resolvable, ${unresolved.length} unresolved, mode=${args.apply ? "apply" : "dry-run"}.`
  );

  if (unresolved.length > 0) {
    process.exitCode = 1;
    return;
  }

  if (!args.apply && resolved.length > 0) {
    process.exitCode = 2;
  }
}

function initializeAdmin() {
  if (admin.apps.length > 0) {
    return;
  }

  if (args.serviceAccount) {
    const serviceAccountPath = path.resolve(args.serviceAccount);
    const serviceAccount = JSON.parse(fs.readFileSync(serviceAccountPath, "utf8"));
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
      projectId: args.project ?? serviceAccount.project_id,
    });
    return;
  }

  admin.initializeApp({
    projectId: args.project,
  });
}

function parseArgs(argv) {
  const parsed = {
    apply: false,
    serviceAccount: process.env.GOOGLE_APPLICATION_CREDENTIALS,
    project: process.env.GCLOUD_PROJECT || process.env.GOOGLE_CLOUD_PROJECT,
  };

  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (arg === "--apply") {
      parsed.apply = true;
      continue;
    }

    if (arg === "--service-account") {
      parsed.serviceAccount = argv[index + 1];
      index += 1;
      continue;
    }

    if (arg === "--project") {
      parsed.project = argv[index + 1];
      index += 1;
      continue;
    }
  }

  return parsed;
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : error);
  process.exitCode = 1;
});
