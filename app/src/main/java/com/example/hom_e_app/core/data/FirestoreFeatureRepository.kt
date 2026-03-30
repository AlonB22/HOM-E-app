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

    suspend fun loadChildChores(
        context: Context,
        session: FamilySession,
    ): Result<ChildChoresState> = runCatching {
        val chores = firestore(context)
            .collection(CHORES_COLLECTION)
            .whereEqualTo("familyId", session.familyId)
            .get()
            .await()
            .documents
            .mapNotNull(::toChore)
            .filter { it.assigneeMemberId == session.memberId }
            .sortedWith(compareByDescending<ChoreRecord> { it.updatedAtMillis }.thenBy { it.title.lowercase(Locale.US) })

        ChildChoresState(
            chores = chores.map(::toChildChoreItem),
            waitingCount = chores.count { it.status == ChoreStatus.SUBMITTED }
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
            .get()
            .await()
            .documents
            .mapNotNull(::toReward)
            .filter { it.isActive }
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
}

data class ParentChoreListItem(
    val id: String,
    val title: String,
    val description: String,
    val assigneeName: String,
    val points: Int,
    val status: ChoreStatus,
)

data class ChildChoresState(
    val chores: List<ChildChoreItem>,
    val waitingCount: Int,
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
