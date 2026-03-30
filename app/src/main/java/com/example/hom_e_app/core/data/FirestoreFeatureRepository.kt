package com.example.hom_e_app.core.data

import android.content.Context
import com.example.hom_e_app.core.auth.FamilyRole
import com.example.hom_e_app.core.auth.FamilySession
import com.example.hom_e_app.core.auth.FirebaseBootstrap
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Locale

object FirestoreFeatureRepository {

    private const val FAMILY_MEMBERS_COLLECTION = "familyMembers"
    private const val CHORES_COLLECTION = "chores"
    private const val REWARDS_COLLECTION = "rewards"
    private const val REWARD_REQUESTS_COLLECTION = "rewardRequests"

    suspend fun loadParentHomeSummary(
        context: Context,
        session: FamilySession,
    ): Result<ParentHomeSummaryState> = runCatching {
        require(session.role == FamilyRole.PARENT) { "Only parents can access the parent home." }
        val db = firestore(context)

        val familyMembers = db.collection(FAMILY_MEMBERS_COLLECTION)
            .whereEqualTo("familyId", session.familyId)
            .get()
            .await()
            .documents

        val childMembers = familyMembers.filter {
            it.getString("role")?.lowercase(Locale.US) == FamilyRole.CHILD.name.lowercase(Locale.US)
        }

        val chores = db.collection(CHORES_COLLECTION)
            .whereEqualTo("familyId", session.familyId)
            .get()
            .await()
            .documents
            .mapNotNull(::toChore)

        val rewards = db.collection(REWARDS_COLLECTION)
            .whereEqualTo("familyId", session.familyId)
            .get()
            .await()
            .documents
            .mapNotNull(::toReward)

        val rewardRequests = db.collection(REWARD_REQUESTS_COLLECTION)
            .whereEqualTo("familyId", session.familyId)
            .get()
            .await()
            .documents
            .mapNotNull(::toRewardRequest)

        val submittedChoreCount = chores.count { it.status == ChoreStatus.SUBMITTED }
        val pendingRewardRequestCount = rewardRequests.count { it.status == RewardRequestStatus.REQUESTED }

        ParentHomeSummaryState(
            pendingApprovalsCount = submittedChoreCount + pendingRewardRequestCount,
            activeChoresCount = chores.count { it.status == ChoreStatus.OPEN || it.status == ChoreStatus.SUBMITTED },
            activeRewardsCount = rewards.count { it.isActive },
            submittedChoreCount = submittedChoreCount,
            pendingRewardRequestCount = pendingRewardRequestCount,
            openChoresCount = chores.count { it.status == ChoreStatus.OPEN },
            childCount = childMembers.size,
            totalChildPoints = childMembers.sumOf { it.getLong("pointsBalance")?.toInt() ?: 0 },
        )
    }

    suspend fun loadParentChores(
        context: Context,
        session: FamilySession,
    ): Result<List<ParentChoreListItem>> = runCatching {
        firestore(context)
            .collection(CHORES_COLLECTION)
            .whereEqualTo("familyId", session.familyId)
            .get()
            .await()
            .documents
            .mapNotNull(::toChore)
            .sortedWith(compareByDescending<ChoreRecord> { it.updatedAtMillis }.thenBy { it.title.lowercase(Locale.US) })
            .map {
                ParentChoreListItem(
                    id = it.id,
                    title = it.title,
                    description = it.description,
                    assigneeName = it.assigneeName,
                    points = it.points,
                    status = it.status
                )
            }
    }

    suspend fun loadParentChoreForm(
        context: Context,
        session: FamilySession,
        choreId: String,
    ): Result<ParentChoreForm> = runCatching {
        require(session.role == FamilyRole.PARENT) { "Only parents can edit chores." }
        val snapshot = firestore(context).collection(CHORES_COLLECTION).document(choreId).get().await()
        val chore = toChore(snapshot) ?: error("Chore not found.")
        validateParentFamilyAccess(chore.familyId, session)
        ParentChoreForm(
            id = chore.id,
            title = chore.title,
            description = chore.description,
            assigneeMemberId = chore.assigneeMemberId,
            assigneeName = chore.assigneeName,
            points = chore.points,
            status = chore.status,
        )
    }

    suspend fun loadAssignableChildren(
        context: Context,
        session: FamilySession,
    ): Result<List<FamilyChildOption>> = runCatching {
        require(session.role == FamilyRole.PARENT) { "Only parents can assign chores." }
        firestore(context)
            .collection(FAMILY_MEMBERS_COLLECTION)
            .whereEqualTo("familyId", session.familyId)
            .whereEqualTo("role", FamilyRole.CHILD.name.lowercase(Locale.US))
            .get()
            .await()
            .documents
            .mapNotNull(::toFamilyChildOption)
            .sortedBy { it.displayName.lowercase(Locale.US) }
    }

    suspend fun createParentChore(
        context: Context,
        session: FamilySession,
        input: ParentChoreDraft,
    ): Result<Unit> = runCatching {
        require(session.role == FamilyRole.PARENT) { "Only parents can create chores." }
        val db = firestore(context)
        val assignee = readChildAssignee(db, session.familyId, input.assigneeMemberId)
        db.collection(CHORES_COLLECTION)
            .document()
            .set(
                mapOf(
                    "familyId" to session.familyId,
                    "title" to input.title,
                    "description" to input.description,
                    "focus" to input.description,
                    "points" to input.points,
                    "assigneeMemberId" to assignee.id,
                    "assigneeName" to assignee.displayName,
                    "status" to ChoreStatus.OPEN.rawValue,
                    "createdByMemberId" to session.memberId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            .await()
    }

    suspend fun updateParentChore(
        context: Context,
        session: FamilySession,
        choreId: String,
        input: ParentChoreDraft,
    ): Result<Unit> = runCatching {
        require(session.role == FamilyRole.PARENT) { "Only parents can edit chores." }
        val db = firestore(context)
        db.runTransaction { transaction ->
            val choreRef = db.collection(CHORES_COLLECTION).document(choreId)
            val chore = toChore(transaction.get(choreRef)) ?: error("Chore not found.")
            validateParentFamilyAccess(chore.familyId, session)
            if (chore.status != ChoreStatus.OPEN) {
                error("Only open chores can be edited.")
            }

            val assignee = readChildAssignee(transaction, db, session.familyId, input.assigneeMemberId)
            transaction.update(
                choreRef,
                mapOf(
                    "title" to input.title,
                    "description" to input.description,
                    "focus" to input.description,
                    "points" to input.points,
                    "assigneeMemberId" to assignee.id,
                    "assigneeName" to assignee.displayName,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            Unit
        }.await()
    }

    suspend fun loadParentRewards(
        context: Context,
        session: FamilySession,
    ): Result<List<ParentRewardListItem>> = runCatching {
        require(session.role == FamilyRole.PARENT) { "Only parents can manage rewards." }
        firestore(context)
            .collection(REWARDS_COLLECTION)
            .whereEqualTo("familyId", session.familyId)
            .get()
            .await()
            .documents
            .mapNotNull(::toReward)
            .sortedWith(compareByDescending<RewardRecord> { it.updatedAtMillis }.thenBy { it.title.lowercase(Locale.US) })
            .map {
                ParentRewardListItem(
                    id = it.id,
                    title = it.title,
                    description = it.description,
                    cost = it.cost,
                    isActive = it.isActive,
                )
            }
    }

    suspend fun loadParentRewardForm(
        context: Context,
        session: FamilySession,
        rewardId: String,
    ): Result<ParentRewardForm> = runCatching {
        require(session.role == FamilyRole.PARENT) { "Only parents can edit rewards." }
        val snapshot = firestore(context).collection(REWARDS_COLLECTION).document(rewardId).get().await()
        val reward = toReward(snapshot) ?: error("Reward not found.")
        validateParentFamilyAccess(reward.familyId, session)
        ParentRewardForm(
            id = reward.id,
            title = reward.title,
            description = reward.description,
            cost = reward.cost,
            isActive = reward.isActive,
        )
    }

    suspend fun createParentReward(
        context: Context,
        session: FamilySession,
        input: ParentRewardDraft,
    ): Result<Unit> = runCatching {
        require(session.role == FamilyRole.PARENT) { "Only parents can create rewards." }
        firestore(context)
            .collection(REWARDS_COLLECTION)
            .document()
            .set(
                mapOf(
                    "familyId" to session.familyId,
                    "title" to input.title,
                    "description" to input.description,
                    "highlight" to input.description,
                    "cost" to input.cost,
                    "isActive" to input.isActive,
                    "createdByMemberId" to session.memberId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            .await()
    }

    suspend fun updateParentReward(
        context: Context,
        session: FamilySession,
        rewardId: String,
        input: ParentRewardDraft,
    ): Result<Unit> = runCatching {
        require(session.role == FamilyRole.PARENT) { "Only parents can edit rewards." }
        val db = firestore(context)
        db.runTransaction { transaction ->
            val rewardRef = db.collection(REWARDS_COLLECTION).document(rewardId)
            val reward = toReward(transaction.get(rewardRef)) ?: error("Reward not found.")
            validateParentFamilyAccess(reward.familyId, session)
            transaction.update(
                rewardRef,
                mapOf(
                    "title" to input.title,
                    "description" to input.description,
                    "highlight" to input.description,
                    "cost" to input.cost,
                    "isActive" to input.isActive,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            Unit
        }.await()
    }

    suspend fun loadChildChores(
        context: Context,
        session: FamilySession,
    ): Result<ChildChoresState> = runCatching {
        val chores = firestore(context)
            .collection(CHORES_COLLECTION)
            .whereEqualTo("familyId", session.familyId)
            .whereEqualTo("assigneeMemberId", session.memberId)
            .get()
            .await()
            .documents
            .mapNotNull(::toChore)
            .sortedWith(compareByDescending<ChoreRecord> { it.updatedAtMillis }.thenBy { it.title.lowercase(Locale.US) })

        ChildChoresState(
            chores = chores.map(::toChildChoreItem),
            waitingCount = chores.count { it.status == ChoreStatus.SUBMITTED }
        )
    }

    suspend fun loadChildHomeSummary(
        context: Context,
        session: FamilySession,
    ): Result<ChildHomeSummaryState> = runCatching {
        require(session.role == FamilyRole.CHILD) { "Only children can access the child home." }
        val db = firestore(context)

        val chores = db.collection(CHORES_COLLECTION)
            .whereEqualTo("familyId", session.familyId)
            .whereEqualTo("assigneeMemberId", session.memberId)
            .get()
            .await()
            .documents
            .mapNotNull(::toChore)
            .sortedWith(compareByDescending<ChoreRecord> { it.updatedAtMillis }.thenBy { it.title.lowercase(Locale.US) })

        val rewards = db.collection(REWARDS_COLLECTION)
            .whereEqualTo("familyId", session.familyId)
            .whereEqualTo("isActive", true)
            .get()
            .await()
            .documents
            .mapNotNull(::toReward)
            .sortedWith(compareBy<RewardRecord> { it.cost }.thenBy { it.title.lowercase(Locale.US) })

        val pendingRequests = db.collection(REWARD_REQUESTS_COLLECTION)
            .whereEqualTo("familyId", session.familyId)
            .whereEqualTo("requestedByMemberId", session.memberId)
            .get()
            .await()
            .documents
            .mapNotNull(::toRewardRequest)
            .filter { it.status == RewardRequestStatus.REQUESTED }
            .associateBy { it.rewardId }

        val pointsBalance = readPointsBalance(db, session.memberId)
        val previewRewards = rewards.take(2).map { reward ->
            ChildRewardItem(
                id = reward.id,
                title = reward.title,
                description = reward.description,
                highlight = reward.highlight,
                cost = reward.cost,
                isRequested = pendingRequests.containsKey(reward.id),
            )
        }

        ChildHomeSummaryState(
            pointsBalance = pointsBalance,
            waitingCount = chores.count { it.status == ChoreStatus.SUBMITTED },
            priorityChores = chores
                .filter { it.status == ChoreStatus.OPEN }
                .take(3)
                .map(::toChildChoreItem),
            rewardPreview = previewRewards,
            nextReward = rewards
                .asSequence()
                .filter { !pendingRequests.containsKey(it.id) }
                .map { reward ->
                    ChildRewardItem(
                        id = reward.id,
                        title = reward.title,
                        description = reward.description,
                        highlight = reward.highlight,
                        cost = reward.cost,
                        isRequested = false,
                    )
                }
                .firstOrNull(),
        )
    }

    suspend fun loadChildChoreDetails(
        context: Context,
        session: FamilySession,
        choreId: String,
    ): Result<ChildChoreItem> = runCatching {
        val snapshot = firestore(context).collection(CHORES_COLLECTION).document(choreId).get().await()
        val chore = toChore(snapshot) ?: error("Chore not found.")
        validateChildChoreAccess(chore, session)
        toChildChoreItem(chore)
    }

    suspend fun submitChore(
        context: Context,
        session: FamilySession,
        choreId: String,
    ): Result<Unit> = runCatching {
        require(session.role == FamilyRole.CHILD) { "Only children can submit chores." }
        val db = firestore(context)
        db.runTransaction { transaction ->
            val choreRef = db.collection(CHORES_COLLECTION).document(choreId)
            val chore = toChore(transaction.get(choreRef)) ?: error("Chore not found.")
            validateChildChoreAccess(chore, session)
            if (chore.status != ChoreStatus.OPEN) {
                error(
                    when (chore.status) {
                        ChoreStatus.SUBMITTED -> "This chore is already waiting for parent review."
                        ChoreStatus.APPROVED -> "This chore was already approved."
                        ChoreStatus.REJECTED -> "Rejected chores cannot be submitted again."
                        ChoreStatus.OPEN -> "This chore cannot be submitted right now."
                    }
                )
            }

            transaction.update(
                choreRef,
                mapOf(
                    "status" to ChoreStatus.SUBMITTED.rawValue,
                    "submittedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            Unit
        }.await()
    }

    suspend fun loadChildRewards(
        context: Context,
        session: FamilySession,
    ): Result<ChildRewardsState> = runCatching {
        val db = firestore(context)
        val rewards = db.collection(REWARDS_COLLECTION)
            .whereEqualTo("familyId", session.familyId)
            .whereEqualTo("isActive", true)
            .get()
            .await()
            .documents
            .mapNotNull(::toReward)
            .sortedBy { it.title.lowercase(Locale.US) }

        val pendingRequests = db.collection(REWARD_REQUESTS_COLLECTION)
            .whereEqualTo("familyId", session.familyId)
            .whereEqualTo("requestedByMemberId", session.memberId)
            .get()
            .await()
            .documents
            .mapNotNull(::toRewardRequest)
            .filter { it.status == RewardRequestStatus.REQUESTED }
            .associateBy { it.rewardId }

        val pointsBalance = readPointsBalance(db, session.memberId)

        ChildRewardsState(
            pointsBalance = pointsBalance,
            requestedCount = pendingRequests.size,
            rewards = rewards.map { reward ->
                ChildRewardItem(
                    id = reward.id,
                    title = reward.title,
                    description = reward.description,
                    highlight = reward.highlight,
                    cost = reward.cost,
                    isRequested = pendingRequests.containsKey(reward.id),
                )
            }
        )
    }

    suspend fun loadChildRewardDetails(
        context: Context,
        session: FamilySession,
        rewardId: String,
    ): Result<ChildRewardDetails> = runCatching {
        val db = firestore(context)
        val rewardSnapshot = db.collection(REWARDS_COLLECTION).document(rewardId).get().await()
        val reward = toReward(rewardSnapshot) ?: error("Reward not found.")
        validateRewardAccess(reward, session)
        val pointsBalance = readPointsBalance(db, session.memberId)
        val hasPendingRequest = db.collection(REWARD_REQUESTS_COLLECTION)
            .whereEqualTo("familyId", session.familyId)
            .whereEqualTo("requestedByMemberId", session.memberId)
            .whereEqualTo("rewardId", rewardId)
            .get()
            .await()
            .documents
            .mapNotNull(::toRewardRequest)
            .any { it.status == RewardRequestStatus.REQUESTED }

        ChildRewardDetails(
            reward = ChildRewardItem(
                id = reward.id,
                title = reward.title,
                description = reward.description,
                highlight = reward.highlight,
                cost = reward.cost,
                isRequested = hasPendingRequest
            ),
            pointsBalance = pointsBalance
        )
    }

    suspend fun createRewardRequest(
        context: Context,
        session: FamilySession,
        rewardId: String,
    ): Result<Unit> = runCatching {
        require(session.role == FamilyRole.CHILD) { "Only children can request rewards." }
        val db = firestore(context)
        val rewardSnapshot = db.collection(REWARDS_COLLECTION).document(rewardId).get().await()
        val reward = toReward(rewardSnapshot) ?: error("Reward not found.")
        validateRewardAccess(reward, session)

        val hasPendingRequest = db.collection(REWARD_REQUESTS_COLLECTION)
            .whereEqualTo("familyId", session.familyId)
            .whereEqualTo("requestedByMemberId", session.memberId)
            .whereEqualTo("rewardId", rewardId)
            .get()
            .await()
            .documents
            .mapNotNull(::toRewardRequest)
            .any { it.status == RewardRequestStatus.REQUESTED }

        if (hasPendingRequest) {
            error("This reward is already waiting for parent review.")
        }

        val requestRef = db.collection(REWARD_REQUESTS_COLLECTION).document()
        requestRef.set(
            mapOf(
                "familyId" to session.familyId,
                "rewardId" to reward.id,
                "requestedByMemberId" to session.memberId,
                "requestedByUserId" to session.uid,
                "requestedByName" to session.displayName,
                "rewardTitle" to reward.title,
                "rewardDescription" to reward.description,
                "rewardHighlight" to reward.highlight,
                "rewardCost" to reward.cost,
                "status" to RewardRequestStatus.REQUESTED.rawValue,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    suspend fun loadApprovals(
        context: Context,
        session: FamilySession,
    ): Result<ApprovalsState> = runCatching {
        require(session.role == FamilyRole.PARENT) { "Only parents can review approvals." }
        val db = firestore(context)
        val chores = db.collection(CHORES_COLLECTION)
            .whereEqualTo("familyId", session.familyId)
            .get()
            .await()
            .documents
            .mapNotNull(::toChore)
            .filter { it.status == ChoreStatus.SUBMITTED }
            .sortedByDescending { it.updatedAtMillis }
            .map {
                ChoreApprovalItem(
                    id = it.id,
                    title = "${it.title} submitted",
                    description = "${it.assigneeName} marked this chore complete and is waiting for review.",
                    assigneeName = it.assigneeName,
                    points = it.points,
                )
            }

        val rewards = db.collection(REWARD_REQUESTS_COLLECTION)
            .whereEqualTo("familyId", session.familyId)
            .get()
            .await()
            .documents
            .mapNotNull(::toRewardRequest)
            .filter { it.status == RewardRequestStatus.REQUESTED }
            .sortedByDescending { it.updatedAtMillis }
            .map {
                RewardApprovalItem(
                    id = it.id,
                    title = it.rewardTitle,
                    description = "${it.requestedByName} requested this reward for ${it.rewardCost} points.",
                    requestedByName = it.requestedByName,
                    cost = it.rewardCost,
                )
            }

        ApprovalsState(
            pendingChores = chores,
            pendingRewards = rewards,
        )
    }

    suspend fun approveChore(
        context: Context,
        session: FamilySession,
        choreId: String,
    ): Result<Unit> = runCatching {
        require(session.role == FamilyRole.PARENT) { "Only parents can approve chores." }
        val db = firestore(context)
        db.runTransaction { transaction ->
            val choreRef = db.collection(CHORES_COLLECTION).document(choreId)
            val chore = toChore(transaction.get(choreRef)) ?: error("Chore not found.")
            validateParentFamilyAccess(chore.familyId, session)
            if (chore.status != ChoreStatus.SUBMITTED) {
                error("Only submitted chores can be approved.")
            }

            val memberRef = db.collection(FAMILY_MEMBERS_COLLECTION).document(chore.assigneeMemberId)
            val memberSnapshot = transaction.get(memberRef)
            val currentPoints = memberSnapshot.getLong("pointsBalance")?.toInt() ?: 0

            transaction.update(
                choreRef,
                mapOf(
                    "status" to ChoreStatus.APPROVED.rawValue,
                    "reviewedByMemberId" to session.memberId,
                    "reviewedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            transaction.update(
                memberRef,
                mapOf(
                    "pointsBalance" to currentPoints + chore.points,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            Unit
        }.await()
    }

    suspend fun rejectChore(
        context: Context,
        session: FamilySession,
        choreId: String,
    ): Result<Unit> = runCatching {
        require(session.role == FamilyRole.PARENT) { "Only parents can reject chores." }
        val db = firestore(context)
        db.runTransaction { transaction ->
            val choreRef = db.collection(CHORES_COLLECTION).document(choreId)
            val chore = toChore(transaction.get(choreRef)) ?: error("Chore not found.")
            validateParentFamilyAccess(chore.familyId, session)
            if (chore.status != ChoreStatus.SUBMITTED) {
                error("Only submitted chores can be rejected.")
            }

            transaction.update(
                choreRef,
                mapOf(
                    "status" to ChoreStatus.REJECTED.rawValue,
                    "reviewedByMemberId" to session.memberId,
                    "reviewedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            Unit
        }.await()
    }

    suspend fun approveRewardRequest(
        context: Context,
        session: FamilySession,
        requestId: String,
    ): Result<Unit> = runCatching {
        require(session.role == FamilyRole.PARENT) { "Only parents can approve reward requests." }
        val db = firestore(context)
        db.runTransaction { transaction ->
            val requestRef = db.collection(REWARD_REQUESTS_COLLECTION).document(requestId)
            val request = toRewardRequest(transaction.get(requestRef)) ?: error("Reward request not found.")
            validateParentFamilyAccess(request.familyId, session)
            if (request.status != RewardRequestStatus.REQUESTED) {
                error("Only requested rewards can be approved.")
            }

            val memberRef = db.collection(FAMILY_MEMBERS_COLLECTION).document(request.requestedByMemberId)
            val memberSnapshot = transaction.get(memberRef)
            val currentPoints = memberSnapshot.getLong("pointsBalance")?.toInt() ?: 0
            if (currentPoints < request.rewardCost) {
                error("${request.requestedByName} does not have enough points for this reward.")
            }

            transaction.update(
                requestRef,
                mapOf(
                    "status" to RewardRequestStatus.APPROVED.rawValue,
                    "reviewedByMemberId" to session.memberId,
                    "reviewedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            transaction.update(
                memberRef,
                mapOf(
                    "pointsBalance" to currentPoints - request.rewardCost,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            Unit
        }.await()
    }

    suspend fun rejectRewardRequest(
        context: Context,
        session: FamilySession,
        requestId: String,
    ): Result<Unit> = runCatching {
        require(session.role == FamilyRole.PARENT) { "Only parents can reject reward requests." }
        val db = firestore(context)
        db.runTransaction { transaction ->
            val requestRef = db.collection(REWARD_REQUESTS_COLLECTION).document(requestId)
            val request = toRewardRequest(transaction.get(requestRef)) ?: error("Reward request not found.")
            validateParentFamilyAccess(request.familyId, session)
            if (request.status != RewardRequestStatus.REQUESTED) {
                error("Only requested rewards can be rejected.")
            }

            transaction.update(
                requestRef,
                mapOf(
                    "status" to RewardRequestStatus.REJECTED.rawValue,
                    "reviewedByMemberId" to session.memberId,
                    "reviewedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            Unit
        }.await()
    }

    private suspend fun readPointsBalance(
        db: FirebaseFirestore,
        memberId: String,
    ): Int = db.collection(FAMILY_MEMBERS_COLLECTION)
        .document(memberId)
        .get()
        .await()
        .getLong("pointsBalance")
        ?.toInt()
        ?: 0

    private suspend fun readChildAssignee(
        db: FirebaseFirestore,
        familyId: String,
        memberId: String,
    ): FamilyChildOption {
        val snapshot = db.collection(FAMILY_MEMBERS_COLLECTION).document(memberId).get().await()
        return toFamilyChildOption(snapshot)?.also {
            require(it.familyId == familyId) { "This child belongs to a different family." }
        } ?: error("Selected child no longer exists.")
    }

    private fun readChildAssignee(
        transaction: com.google.firebase.firestore.Transaction,
        db: FirebaseFirestore,
        familyId: String,
        memberId: String,
    ): FamilyChildOption {
        val snapshot = transaction.get(db.collection(FAMILY_MEMBERS_COLLECTION).document(memberId))
        return toFamilyChildOption(snapshot)?.also {
            require(it.familyId == familyId) { "This child belongs to a different family." }
        } ?: error("Selected child no longer exists.")
    }

    private fun firestore(context: Context): FirebaseFirestore {
        val app = FirebaseBootstrap.initialize(context).getOrThrow()
        return FirebaseFirestore.getInstance(app)
    }

    private fun toChildChoreItem(chore: ChoreRecord): ChildChoreItem = ChildChoreItem(
        id = chore.id,
        title = chore.title,
        description = chore.description,
        focus = chore.focus,
        points = chore.points,
        status = chore.status
    )

    private fun toChore(snapshot: DocumentSnapshot): ChoreRecord? {
        val familyId = snapshot.getString("familyId") ?: return null
        val title = snapshot.getString("title") ?: return null
        val description = snapshot.getString("description") ?: return null
        val assigneeMemberId = snapshot.getString("assigneeMemberId") ?: return null
        return ChoreRecord(
            id = snapshot.id,
            familyId = familyId,
            title = title,
            description = description,
            focus = snapshot.getString("focus")
                ?: snapshot.getString("completionHint")
                ?: description,
            points = snapshot.getLong("points")?.toInt() ?: 0,
            assigneeMemberId = assigneeMemberId,
            assigneeName = snapshot.getString("assigneeName")
                ?: snapshot.getString("assigneeDisplayName")
                ?: "Child",
            status = ChoreStatus.from(snapshot.getString("status")),
            updatedAtMillis = snapshot.getDate("updatedAt")?.time ?: 0L,
        )
    }

    private fun toReward(snapshot: DocumentSnapshot): RewardRecord? {
        val familyId = snapshot.getString("familyId") ?: return null
        val title = snapshot.getString("title") ?: return null
        val description = snapshot.getString("description") ?: return null
        return RewardRecord(
            id = snapshot.id,
            familyId = familyId,
            title = title,
            description = description,
            highlight = snapshot.getString("highlight")
                ?: snapshot.getString("summary")
                ?: description,
            cost = snapshot.getLong("cost")?.toInt()
                ?: snapshot.getLong("pointsCost")?.toInt()
                ?: 0,
            isActive = snapshot.getBoolean("isActive") ?: true,
            updatedAtMillis = snapshot.getDate("updatedAt")?.time ?: 0L,
        )
    }

    private fun toRewardRequest(snapshot: DocumentSnapshot): RewardRequestRecord? {
        val familyId = snapshot.getString("familyId") ?: return null
        val rewardId = snapshot.getString("rewardId") ?: return null
        val requestedByMemberId = snapshot.getString("requestedByMemberId") ?: return null
        return RewardRequestRecord(
            id = snapshot.id,
            familyId = familyId,
            rewardId = rewardId,
            requestedByMemberId = requestedByMemberId,
            requestedByName = snapshot.getString("requestedByName") ?: "Child",
            rewardTitle = snapshot.getString("rewardTitle") ?: "Reward",
            rewardCost = snapshot.getLong("rewardCost")?.toInt() ?: 0,
            status = RewardRequestStatus.from(snapshot.getString("status")),
            updatedAtMillis = snapshot.getDate("updatedAt")?.time ?: 0L,
        )
    }

    private fun validateChildChoreAccess(chore: ChoreRecord, session: FamilySession) {
        require(session.role == FamilyRole.CHILD) { "Only children can access this chore flow." }
        require(chore.familyId == session.familyId) { "This chore belongs to a different family." }
        require(chore.assigneeMemberId == session.memberId) { "Children can only access their assigned chores." }
    }

    private fun validateRewardAccess(reward: RewardRecord, session: FamilySession) {
        require(session.role == FamilyRole.CHILD) { "Only children can access this reward flow." }
        require(reward.familyId == session.familyId) { "This reward belongs to a different family." }
        require(reward.isActive) { "This reward is not active right now." }
    }

    private fun validateParentFamilyAccess(familyId: String, session: FamilySession) {
        require(session.role == FamilyRole.PARENT) { "Only parents can review approvals." }
        require(familyId == session.familyId) { "This request belongs to a different family." }
    }

    private fun toFamilyChildOption(snapshot: DocumentSnapshot): FamilyChildOption? {
        val familyId = snapshot.getString("familyId") ?: return null
        val role = snapshot.getString("role")?.lowercase(Locale.US) ?: return null
        if (role != FamilyRole.CHILD.name.lowercase(Locale.US)) return null
        val displayName = snapshot.getString("displayName")
            ?.takeIf { it.isNotBlank() }
            ?: snapshot.getString("email")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return FamilyChildOption(
            id = snapshot.id,
            familyId = familyId,
            displayName = displayName,
        )
    }
}

data class ParentChoreListItem(
    val id: String,
    val title: String,
    val description: String,
    val assigneeName: String,
    val points: Int,
    val status: ChoreStatus,
)

data class ParentChoreForm(
    val id: String,
    val title: String,
    val description: String,
    val assigneeMemberId: String,
    val assigneeName: String,
    val points: Int,
    val status: ChoreStatus,
)

data class ParentChoreDraft(
    val title: String,
    val description: String,
    val assigneeMemberId: String,
    val points: Int,
)

data class FamilyChildOption(
    val id: String,
    val familyId: String,
    val displayName: String,
)

data class ParentRewardListItem(
    val id: String,
    val title: String,
    val description: String,
    val cost: Int,
    val isActive: Boolean,
)

data class ParentRewardForm(
    val id: String,
    val title: String,
    val description: String,
    val cost: Int,
    val isActive: Boolean,
)

data class ParentRewardDraft(
    val title: String,
    val description: String,
    val cost: Int,
    val isActive: Boolean,
)

data class ParentHomeSummaryState(
    val pendingApprovalsCount: Int,
    val activeChoresCount: Int,
    val activeRewardsCount: Int,
    val submittedChoreCount: Int,
    val pendingRewardRequestCount: Int,
    val openChoresCount: Int,
    val childCount: Int,
    val totalChildPoints: Int,
)

data class ChildChoresState(
    val chores: List<ChildChoreItem>,
    val waitingCount: Int,
)

data class ChildHomeSummaryState(
    val pointsBalance: Int,
    val waitingCount: Int,
    val priorityChores: List<ChildChoreItem>,
    val rewardPreview: List<ChildRewardItem>,
    val nextReward: ChildRewardItem?,
)

data class ChildChoreItem(
    val id: String,
    val title: String,
    val description: String,
    val focus: String,
    val points: Int,
    val status: ChoreStatus,
)

data class ChildRewardsState(
    val pointsBalance: Int,
    val requestedCount: Int,
    val rewards: List<ChildRewardItem>,
)

data class ChildRewardDetails(
    val reward: ChildRewardItem,
    val pointsBalance: Int,
)

data class ChildRewardItem(
    val id: String,
    val title: String,
    val description: String,
    val highlight: String,
    val cost: Int,
    val isRequested: Boolean,
)

data class ApprovalsState(
    val pendingChores: List<ChoreApprovalItem>,
    val pendingRewards: List<RewardApprovalItem>,
)

data class ChoreApprovalItem(
    val id: String,
    val title: String,
    val description: String,
    val assigneeName: String,
    val points: Int,
)

data class RewardApprovalItem(
    val id: String,
    val title: String,
    val description: String,
    val requestedByName: String,
    val cost: Int,
)

enum class ChoreStatus(val rawValue: String) {
    OPEN("open"),
    SUBMITTED("submitted"),
    APPROVED("approved"),
    REJECTED("rejected");

    companion object {
        fun from(value: String?): ChoreStatus = when (value?.lowercase(Locale.US)) {
            OPEN.rawValue -> OPEN
            SUBMITTED.rawValue -> SUBMITTED
            APPROVED.rawValue -> APPROVED
            REJECTED.rawValue -> REJECTED
            else -> OPEN
        }
    }
}

enum class RewardRequestStatus(val rawValue: String) {
    REQUESTED("requested"),
    APPROVED("approved"),
    REJECTED("rejected");

    companion object {
        fun from(value: String?): RewardRequestStatus = when (value?.lowercase(Locale.US)) {
            REQUESTED.rawValue -> REQUESTED
            APPROVED.rawValue -> APPROVED
            REJECTED.rawValue -> REJECTED
            else -> REQUESTED
        }
    }
}

private data class ChoreRecord(
    val id: String,
    val familyId: String,
    val title: String,
    val description: String,
    val focus: String,
    val points: Int,
    val assigneeMemberId: String,
    val assigneeName: String,
    val status: ChoreStatus,
    val updatedAtMillis: Long,
)

private data class RewardRecord(
    val id: String,
    val familyId: String,
    val title: String,
    val description: String,
    val highlight: String,
    val cost: Int,
    val isActive: Boolean,
    val updatedAtMillis: Long,
)

private data class RewardRequestRecord(
    val id: String,
    val familyId: String,
    val rewardId: String,
    val requestedByMemberId: String,
    val requestedByName: String,
    val rewardTitle: String,
    val rewardCost: Int,
    val status: RewardRequestStatus,
    val updatedAtMillis: Long,
)
