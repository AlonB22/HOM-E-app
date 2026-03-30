const test = require("node:test");
const assert = require("node:assert/strict");
const { readFile } = require("node:fs/promises");
const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require("@firebase/rules-unit-testing");
const {
  doc,
  getDoc,
  setDoc,
  updateDoc,
  writeBatch,
} = require("firebase/firestore");

const projectId = "demo-hom-e";

const ids = {
  familyA: "family-a",
  familyB: "family-b",
  parentUserA: "parent-user-a",
  parentUserB: "parent-user-b",
  childUserA: "child-user-a",
  childUserB: "child-user-b",
  parentMemberA: "parent-member-a",
  parentMemberB: "parent-member-b",
  childMemberA: "child-member-a",
  childMemberB: "child-member-b",
  rewardA: "reward-a",
  rewardRequestA: "reward-request-a",
  choreA: "chore-a",
  choreB: "chore-b",
};

let testEnv;

test.before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId,
    firestore: {
      host: "127.0.0.1",
      port: 8080,
      rules: await readFile("firestore.rules", "utf8"),
    },
  });
});

test.after(async () => {
  await testEnv.cleanup();
});

test.beforeEach(async () => {
  await testEnv.clearFirestore();
  await seedBaseState();
});

test("parent can create and edit an open chore for their child", async () => {
  const db = authedDb(ids.parentUserA);
  const choreRef = doc(db, "chores", ids.choreA);

  await assertSucceeds(
    setDoc(choreRef, createOpenChore({
      familyId: ids.familyA,
      assigneeMemberId: ids.childMemberA,
      assigneeName: "Child A",
      createdByMemberId: ids.parentMemberA,
    }))
  );

  await assertSucceeds(
    updateDoc(choreRef, {
      title: "Tidy your desk",
      description: "Sort books and wipe the desk",
      focus: "Sort books and wipe the desk",
      points: 7,
      assigneeMemberId: ids.childMemberA,
      assigneeName: "Child A",
      updatedAt: new Date(),
    })
  );
});

test("child can submit an assigned open chore", async () => {
  await seedDoc(`chores/${ids.choreA}`, createOpenChore({
    familyId: ids.familyA,
    assigneeMemberId: ids.childMemberA,
    assigneeName: "Child A",
    createdByMemberId: ids.parentMemberA,
  }));

  const db = authedDb(ids.childUserA);
  await assertSucceeds(
    updateDoc(doc(db, "chores", ids.choreA), {
      status: "submitted",
      submittedAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("parent can approve a submitted chore and award points in the same batch", async () => {
  await seedDoc(`chores/${ids.choreA}`, createSubmittedChore({
    familyId: ids.familyA,
    assigneeMemberId: ids.childMemberA,
    assigneeName: "Child A",
    createdByMemberId: ids.parentMemberA,
    points: 6,
  }));

  const db = authedDb(ids.parentUserA);
  const batch = writeBatch(db);
  batch.update(doc(db, "chores", ids.choreA), {
    status: "approved",
    reviewedByMemberId: ids.parentMemberA,
    reviewedAt: new Date(),
    updatedAt: new Date(),
  });
  batch.update(doc(db, "familyMembers", ids.childMemberA), {
    pointsBalance: 16,
    updatedAt: new Date(),
  });

  await assertSucceeds(batch.commit());
});

test("parent can reject a submitted chore without changing points", async () => {
  await seedDoc(`chores/${ids.choreA}`, createSubmittedChore({
    familyId: ids.familyA,
    assigneeMemberId: ids.childMemberA,
    assigneeName: "Child A",
    createdByMemberId: ids.parentMemberA,
    points: 6,
  }));

  const db = authedDb(ids.parentUserA);
  const batch = writeBatch(db);
  batch.update(doc(db, "chores", ids.choreA), {
    status: "rejected",
    reviewedByMemberId: ids.parentMemberA,
    reviewedAt: new Date(),
    updatedAt: new Date(),
  });
  batch.update(doc(db, "familyMembers", ids.childMemberA), {
    pointsBalance: 10,
    updatedAt: new Date(),
  });

  await assertSucceeds(batch.commit());
});

test("child can create a reward request for an active family reward", async () => {
  await seedDoc(`rewards/${ids.rewardA}`, createReward({
    familyId: ids.familyA,
    createdByMemberId: ids.parentMemberA,
  }));

  const db = authedDb(ids.childUserA);
  await assertSucceeds(
    setDoc(doc(db, "rewardRequests", ids.rewardRequestA), createRewardRequest({
      familyId: ids.familyA,
      rewardId: ids.rewardA,
      requestedByMemberId: ids.childMemberA,
      requestedByUserId: ids.childUserA,
      requestedByName: "Child A",
      rewardCost: 8,
    }))
  );
});

test("parent can approve a requested reward and deduct points in the same batch", async () => {
  await seedDoc(`rewardRequests/${ids.rewardRequestA}`, createRewardRequest({
    familyId: ids.familyA,
    rewardId: ids.rewardA,
    requestedByMemberId: ids.childMemberA,
    requestedByUserId: ids.childUserA,
    requestedByName: "Child A",
    rewardCost: 8,
  }));
  await seedDoc(`familyMembers/${ids.childMemberA}`, createFamilyMember({
    userId: ids.childUserA,
    familyId: ids.familyA,
    role: "child",
    displayName: "Child A",
    email: "child-a@example.com",
    pointsBalance: 12,
    joinCodeUsed: "JOINA1",
  }));

  const db = authedDb(ids.parentUserA);
  const batch = writeBatch(db);
  batch.update(doc(db, "rewardRequests", ids.rewardRequestA), {
    status: "approved",
    reviewedByMemberId: ids.parentMemberA,
    reviewedAt: new Date(),
    updatedAt: new Date(),
  });
  batch.update(doc(db, "familyMembers", ids.childMemberA), {
    pointsBalance: 4,
    updatedAt: new Date(),
  });

  await assertSucceeds(batch.commit());
});

test("parent can reject a requested reward without changing points", async () => {
  await seedDoc(`rewardRequests/${ids.rewardRequestA}`, createRewardRequest({
    familyId: ids.familyA,
    rewardId: ids.rewardA,
    requestedByMemberId: ids.childMemberA,
    requestedByUserId: ids.childUserA,
    requestedByName: "Child A",
    rewardCost: 8,
  }));

  const db = authedDb(ids.parentUserA);
  const batch = writeBatch(db);
  batch.update(doc(db, "rewardRequests", ids.rewardRequestA), {
    status: "rejected",
    reviewedByMemberId: ids.parentMemberA,
    reviewedAt: new Date(),
    updatedAt: new Date(),
  });
  batch.update(doc(db, "familyMembers", ids.childMemberA), {
    pointsBalance: 10,
    updatedAt: new Date(),
  });

  await assertSucceeds(batch.commit());
});

test("cross-family access is denied for reads and writes", async () => {
  await seedDoc(`chores/${ids.choreB}`, createOpenChore({
    familyId: ids.familyB,
    assigneeMemberId: ids.childMemberB,
    assigneeName: "Child B",
    createdByMemberId: ids.parentMemberB,
  }));

  const db = authedDb(ids.parentUserA);
  await assertFails(getDoc(doc(db, "chores", ids.choreB)));
  await assertFails(
    setDoc(doc(db, "chores", "cross-family-create"), createOpenChore({
      familyId: ids.familyA,
      assigneeMemberId: ids.childMemberB,
      assigneeName: "Child B",
      createdByMemberId: ids.parentMemberA,
    }))
  );
});

test("child is blocked from parent-only writes", async () => {
  const db = authedDb(ids.childUserA);

  await assertFails(
    setDoc(doc(db, "chores", "child-created-chore"), createOpenChore({
      familyId: ids.familyA,
      assigneeMemberId: ids.childMemberA,
      assigneeName: "Child A",
      createdByMemberId: ids.childMemberA,
    }))
  );

  await assertFails(
    updateDoc(doc(db, "familyMembers", ids.childMemberA), {
      pointsBalance: 999,
      updatedAt: new Date(),
    })
  );
});

function authedDb(uid) {
  return testEnv.authenticatedContext(uid).firestore();
}

async function seedBaseState() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();

    await setDoc(doc(db, "families", ids.familyA), {
      name: "Family A",
      joinCode: "JOINA1",
      createdByUid: ids.parentUserA,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
    await setDoc(doc(db, "families", ids.familyB), {
      name: "Family B",
      joinCode: "JOINB1",
      createdByUid: ids.parentUserB,
      createdAt: new Date(),
      updatedAt: new Date(),
    });

    await setDoc(doc(db, "users", ids.parentUserA), createUser({
      familyId: ids.familyA,
      memberId: ids.parentMemberA,
      role: "parent",
      displayName: "Parent A",
      email: "parent-a@example.com",
    }));
    await setDoc(doc(db, "users", ids.childUserA), createUser({
      familyId: ids.familyA,
      memberId: ids.childMemberA,
      role: "child",
      displayName: "Child A",
      email: "child-a@example.com",
    }));
    await setDoc(doc(db, "users", ids.parentUserB), createUser({
      familyId: ids.familyB,
      memberId: ids.parentMemberB,
      role: "parent",
      displayName: "Parent B",
      email: "parent-b@example.com",
    }));
    await setDoc(doc(db, "users", ids.childUserB), createUser({
      familyId: ids.familyB,
      memberId: ids.childMemberB,
      role: "child",
      displayName: "Child B",
      email: "child-b@example.com",
    }));

    await setDoc(doc(db, "familyMembers", ids.parentMemberA), createFamilyMember({
      userId: ids.parentUserA,
      familyId: ids.familyA,
      role: "parent",
      displayName: "Parent A",
      email: "parent-a@example.com",
    }));
    await setDoc(doc(db, "familyMembers", ids.childMemberA), createFamilyMember({
      userId: ids.childUserA,
      familyId: ids.familyA,
      role: "child",
      displayName: "Child A",
      email: "child-a@example.com",
      pointsBalance: 10,
      joinCodeUsed: "JOINA1",
    }));
    await setDoc(doc(db, "familyMembers", ids.parentMemberB), createFamilyMember({
      userId: ids.parentUserB,
      familyId: ids.familyB,
      role: "parent",
      displayName: "Parent B",
      email: "parent-b@example.com",
    }));
    await setDoc(doc(db, "familyMembers", ids.childMemberB), createFamilyMember({
      userId: ids.childUserB,
      familyId: ids.familyB,
      role: "child",
      displayName: "Child B",
      email: "child-b@example.com",
      pointsBalance: 4,
      joinCodeUsed: "JOINB1",
    }));
  });
}

async function seedDoc(path, data) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), path), data);
  });
}

function createUser({ familyId, memberId, role, displayName, email }) {
  return {
    familyId,
    memberId,
    role,
    displayName,
    email,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

function createFamilyMember({
  userId,
  familyId,
  role,
  displayName,
  email,
  pointsBalance = 0,
  joinCodeUsed,
}) {
  const member = {
    userId,
    familyId,
    role,
    displayName,
    email,
    pointsBalance,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
  if (joinCodeUsed) {
    member.joinCodeUsed = joinCodeUsed;
  }
  return member;
}

function createOpenChore({
  familyId,
  assigneeMemberId,
  assigneeName,
  createdByMemberId,
  points = 5,
}) {
  return {
    familyId,
    title: "Clean your room",
    description: "Pick up toys and vacuum the floor",
    focus: "Pick up toys and vacuum the floor",
    points,
    assigneeMemberId,
    assigneeName,
    status: "open",
    createdByMemberId,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

function createSubmittedChore({
  familyId,
  assigneeMemberId,
  assigneeName,
  createdByMemberId,
  points = 5,
}) {
  return {
    ...createOpenChore({
      familyId,
      assigneeMemberId,
      assigneeName,
      createdByMemberId,
      points,
    }),
    status: "submitted",
    submittedAt: new Date(),
  };
}

function createReward({ familyId, createdByMemberId }) {
  return {
    familyId,
    title: "Movie night",
    description: "Pick the family movie for tonight",
    highlight: "Pick the family movie for tonight",
    cost: 8,
    isActive: true,
    createdByMemberId,
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

function createRewardRequest({
  familyId,
  rewardId,
  requestedByMemberId,
  requestedByUserId,
  requestedByName,
  rewardCost,
}) {
  return {
    familyId,
    rewardId,
    requestedByMemberId,
    requestedByUserId,
    requestedByName,
    rewardTitle: "Movie night",
    rewardDescription: "Pick the family movie for tonight",
    rewardHighlight: "Pick the family movie for tonight",
    rewardCost,
    status: "requested",
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}
