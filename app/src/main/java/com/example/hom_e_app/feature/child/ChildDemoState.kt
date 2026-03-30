package com.example.hom_e_app.feature.child

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.hom_e_app.R

enum class ChildChoreStatus {
    NOT_STARTED,
    IN_PROGRESS,
    WAITING_APPROVAL
}

enum class ChildRewardRequestStatus {
    AVAILABLE,
    REQUESTED
}

data class ChildStatusAppearance(
    @StringRes val labelRes: Int,
    @DrawableRes val backgroundRes: Int,
    @ColorRes val textColorRes: Int
)

data class ChildChore(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val focusRes: Int,
    val points: Int,
    val status: ChildChoreStatus
)

data class ChildReward(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val highlightRes: Int,
    val cost: Int,
    val isActive: Boolean,
    val requestStatus: ChildRewardRequestStatus
)

data class ChildProfileSummary(
    @StringRes val childNameRes: Int,
    @StringRes val familyNameRes: Int,
    val points: Int,
    val waitingApprovals: Int,
    val requestedRewards: Int
)

object ChildDemoState {

    private data class MutableChore(
        val id: String,
        @StringRes val titleRes: Int,
        @StringRes val descriptionRes: Int,
        @StringRes val focusRes: Int,
        val points: Int,
        var status: ChildChoreStatus
    )

    private data class MutableReward(
        val id: String,
        @StringRes val titleRes: Int,
        @StringRes val descriptionRes: Int,
        @StringRes val highlightRes: Int,
        val cost: Int,
        val isActive: Boolean,
        var requestStatus: ChildRewardRequestStatus
    )

    private val choreOrder = listOf("kitchen_reset", "laundry_fold", "pet_corner", "trash_reset")
    private val rewardOrder = listOf("movie_night", "baking_session", "game_choice", "late_bedtime")

    private val chores = linkedMapOf(
        "kitchen_reset" to MutableChore(
            id = "kitchen_reset",
            titleRes = R.string.demo_chore_kitchen_title,
            descriptionRes = R.string.demo_chore_kitchen_body,
            focusRes = R.string.child_chore_focus_kitchen,
            points = 15,
            status = ChildChoreStatus.IN_PROGRESS
        ),
        "laundry_fold" to MutableChore(
            id = "laundry_fold",
            titleRes = R.string.child_demo_chore_laundry_title,
            descriptionRes = R.string.child_demo_chore_laundry_body,
            focusRes = R.string.child_chore_focus_laundry,
            points = 12,
            status = ChildChoreStatus.NOT_STARTED
        ),
        "pet_corner" to MutableChore(
            id = "pet_corner",
            titleRes = R.string.child_demo_chore_pet_title,
            descriptionRes = R.string.child_demo_chore_pet_body,
            focusRes = R.string.child_chore_focus_pet,
            points = 10,
            status = ChildChoreStatus.WAITING_APPROVAL
        ),
        "trash_reset" to MutableChore(
            id = "trash_reset",
            titleRes = R.string.demo_chore_trash_title,
            descriptionRes = R.string.demo_chore_trash_body,
            focusRes = R.string.child_chore_focus_trash,
            points = 8,
            status = ChildChoreStatus.NOT_STARTED
        )
    )

    private val rewards = linkedMapOf(
        "movie_night" to MutableReward(
            id = "movie_night",
            titleRes = R.string.demo_reward_movie_title,
            descriptionRes = R.string.demo_reward_movie_body,
            highlightRes = R.string.child_reward_highlight_movie,
            cost = 30,
            isActive = true,
            requestStatus = ChildRewardRequestStatus.AVAILABLE
        ),
        "baking_session" to MutableReward(
            id = "baking_session",
            titleRes = R.string.demo_reward_baking_title,
            descriptionRes = R.string.demo_reward_baking_body,
            highlightRes = R.string.child_reward_highlight_baking,
            cost = 45,
            isActive = true,
            requestStatus = ChildRewardRequestStatus.AVAILABLE
        ),
        "game_choice" to MutableReward(
            id = "game_choice",
            titleRes = R.string.child_demo_reward_game_title,
            descriptionRes = R.string.child_demo_reward_game_body,
            highlightRes = R.string.child_reward_highlight_game,
            cost = 18,
            isActive = true,
            requestStatus = ChildRewardRequestStatus.REQUESTED
        ),
        "late_bedtime" to MutableReward(
            id = "late_bedtime",
            titleRes = R.string.demo_reward_late_title,
            descriptionRes = R.string.demo_reward_late_body,
            highlightRes = R.string.child_reward_highlight_late,
            cost = 60,
            isActive = false,
            requestStatus = ChildRewardRequestStatus.AVAILABLE
        )
    )

    fun currentPoints(): Int = 38

    fun allChores(): List<ChildChore> = choreOrder.mapNotNull { id ->
        chores[id]?.toUiModel()
    }

    fun priorityChores(): List<ChildChore> = listOfNotNull(
        chores["kitchen_reset"]?.toUiModel(),
        chores["laundry_fold"]?.toUiModel(),
        chores["pet_corner"]?.toUiModel()
    )

    fun getChore(id: String): ChildChore? = chores[id]?.toUiModel()

    fun submitChore(id: String): Boolean {
        val chore = chores[id] ?: return false
        if (chore.status == ChildChoreStatus.WAITING_APPROVAL) {
            return false
        }
        chore.status = ChildChoreStatus.WAITING_APPROVAL
        return true
    }

    fun waitingApprovalCount(): Int = chores.values.count { it.status == ChildChoreStatus.WAITING_APPROVAL }

    fun activeRewards(): List<ChildReward> = rewardOrder.mapNotNull { id ->
        rewards[id]?.takeIf { it.isActive }?.toUiModel()
    }

    fun previewRewards(): List<ChildReward> = activeRewards().take(2)

    fun getReward(id: String): ChildReward? = rewards[id]?.toUiModel()

    fun requestReward(id: String): Boolean {
        val reward = rewards[id] ?: return false
        if (!reward.isActive || reward.requestStatus == ChildRewardRequestStatus.REQUESTED) {
            return false
        }
        reward.requestStatus = ChildRewardRequestStatus.REQUESTED
        return true
    }

    fun requestedRewardCount(): Int =
        rewards.values.count { it.requestStatus == ChildRewardRequestStatus.REQUESTED && it.isActive }

    fun nextRewardGoal(): ChildReward? = activeRewards()
        .filter { it.requestStatus == ChildRewardRequestStatus.AVAILABLE }
        .sortedBy { kotlin.math.abs(it.cost - currentPoints()) }
        .firstOrNull()

    fun profileSummary(): ChildProfileSummary = ChildProfileSummary(
        childNameRes = R.string.demo_child_maya,
        familyNameRes = R.string.demo_family_name,
        points = currentPoints(),
        waitingApprovals = waitingApprovalCount(),
        requestedRewards = requestedRewardCount()
    )

    fun choreStatusAppearance(status: ChildChoreStatus): ChildStatusAppearance = when (status) {
        ChildChoreStatus.NOT_STARTED -> ChildStatusAppearance(
            labelRes = R.string.label_status_not_started,
            backgroundRes = R.drawable.bg_status_neutral,
            textColorRes = R.color.status_neutral_text
        )

        ChildChoreStatus.IN_PROGRESS -> ChildStatusAppearance(
            labelRes = R.string.label_status_in_progress,
            backgroundRes = R.drawable.bg_status_attention,
            textColorRes = R.color.status_attention_text
        )

        ChildChoreStatus.WAITING_APPROVAL -> ChildStatusAppearance(
            labelRes = R.string.label_status_awaiting_parent,
            backgroundRes = R.drawable.bg_status_positive,
            textColorRes = R.color.status_positive_text
        )
    }

    fun rewardStatusAppearance(requestStatus: ChildRewardRequestStatus): ChildStatusAppearance =
        when (requestStatus) {
            ChildRewardRequestStatus.AVAILABLE -> ChildStatusAppearance(
                labelRes = R.string.label_status_active,
                backgroundRes = R.drawable.bg_status_positive,
                textColorRes = R.color.status_positive_text
            )

            ChildRewardRequestStatus.REQUESTED -> ChildStatusAppearance(
                labelRes = R.string.label_status_awaiting_parent,
                backgroundRes = R.drawable.bg_status_attention,
                textColorRes = R.color.status_attention_text
            )
        }

    private fun MutableChore.toUiModel(): ChildChore = ChildChore(
        id = id,
        titleRes = titleRes,
        descriptionRes = descriptionRes,
        focusRes = focusRes,
        points = points,
        status = status
    )

    private fun MutableReward.toUiModel(): ChildReward = ChildReward(
        id = id,
        titleRes = titleRes,
        descriptionRes = descriptionRes,
        highlightRes = highlightRes,
        cost = cost,
        isActive = isActive,
        requestStatus = requestStatus
    )
}
