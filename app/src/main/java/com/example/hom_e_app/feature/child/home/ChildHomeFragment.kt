package com.example.hom_e_app.feature.child.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.hom_e_app.R
import com.example.hom_e_app.core.auth.SessionManager
import com.example.hom_e_app.core.data.ChildChoreItem
import com.example.hom_e_app.core.data.ChildHomeSummaryState
import com.example.hom_e_app.core.data.ChildRewardItem
import com.example.hom_e_app.core.data.ChoreStatus
import com.example.hom_e_app.core.data.FirestoreFeatureRepository
import com.example.hom_e_app.core.ui.BaseFragment
import com.example.hom_e_app.feature.child.ChildStatusAppearance
import com.example.hom_e_app.feature.child.chores.ChoreDetailsFragment
import com.example.hom_e_app.feature.child.rewards.RewardDetailsFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ChildHomeFragment : BaseFragment(R.layout.fragment_child_home) {

    override fun bindScreenActions() {
        val navController = findNavController()
        requireView().findViewById<View>(R.id.button_open_my_chores).setOnClickListener {
            navController.navigate(R.id.childChoresFragment)
        }
        requireView().findViewById<View>(R.id.button_open_rewards_store).setOnClickListener {
            navController.navigate(R.id.childRewardsFragment)
        }
        requireView().findViewById<View>(R.id.button_open_profile).setOnClickListener {
            navController.navigate(R.id.profileFragment)
        }
        requireView().findViewById<View>(R.id.button_review_waiting).setOnClickListener {
            navController.navigate(R.id.childChoresFragment)
        }
    }

    override fun onResume() {
        super.onResume()
        loadHomeContent()
    }

    private fun loadHomeContent() {
        val session = SessionManager.currentSession ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            FirestoreFeatureRepository.loadChildHomeSummary(requireContext(), session)
                .onSuccess(::bindHomeContent)
                .onFailure {
                    Snackbar.make(requireView(), SessionManager.errorMessage(it), Snackbar.LENGTH_LONG).show()
                }
        }
    }

    private fun bindHomeContent(state: ChildHomeSummaryState) {
        val root = requireView()
        val points = state.pointsBalance
        val waitingCount = state.waitingCount
        val nextReward = state.nextReward

        root.findViewById<TextView>(R.id.text_points_value).text =
            getString(R.string.label_points_suffix, points)
        root.findViewById<TextView>(R.id.text_points_helper).text =
            if (nextReward == null) {
                getString(R.string.child_home_points_ready_all_requested)
            } else if (points >= nextReward.cost) {
                getString(R.string.child_home_points_ready_message, nextReward.title)
            } else {
                getString(
                    R.string.child_home_points_goal_message,
                    nextReward.cost - points,
                    nextReward.title
                )
            }

        root.findViewById<TextView>(R.id.text_waiting_count).text =
            getString(R.string.child_home_waiting_count, waitingCount)
        root.findViewById<TextView>(R.id.text_waiting_body).text =
            if (waitingCount == 0) {
                getString(R.string.child_home_waiting_empty)
            } else {
                getString(R.string.child_home_waiting_body)
            }

        bindPriorityChores(root.findViewById(R.id.container_priority_chores), state.priorityChores)
        bindRewardPreview(root.findViewById(R.id.container_reward_preview), state.rewardPreview)
    }

    private fun bindPriorityChores(container: LinearLayout, chores: List<ChildChoreItem>) {
        val inflater = LayoutInflater.from(requireContext())
        container.removeAllViews()
        if (chores.isEmpty()) {
            container.addView(createEmptySummaryText(R.string.child_home_priority_empty))
            return
        }

        chores.forEach { chore ->
            val itemView = inflater.inflate(R.layout.item_child_priority_chore, container, false)
            itemView.findViewById<TextView>(R.id.text_priority_title).text = chore.title
            itemView.findViewById<TextView>(R.id.text_priority_points).text =
                getString(R.string.label_points_suffix, chore.points)
            itemView.findViewById<TextView>(R.id.text_priority_body).text =
                chore.focus
            bindStatusChip(
                labelView = itemView.findViewById(R.id.text_priority_status),
                appearance = choreStatusAppearance(chore.status)
            )
            itemView.findViewById<MaterialButton>(R.id.button_priority_open).setOnClickListener {
                findNavController().navigate(
                    R.id.choreDetailsFragment,
                    Bundle().apply {
                        putString(ChoreDetailsFragment.ARG_CHORE_ID, chore.id)
                    }
                )
            }
            container.addView(itemView)
        }
    }

    private fun bindRewardPreview(container: LinearLayout, rewards: List<ChildRewardItem>) {
        val inflater = LayoutInflater.from(requireContext())
        container.removeAllViews()
        if (rewards.isEmpty()) {
            container.addView(createEmptySummaryText(R.string.child_home_rewards_preview_empty))
            return
        }

        rewards.forEach { reward ->
            val itemView = inflater.inflate(R.layout.item_child_reward_preview, container, false)
            itemView.findViewById<TextView>(R.id.text_reward_preview_title).text =
                reward.title
            itemView.findViewById<TextView>(R.id.text_reward_preview_cost).text =
                getString(R.string.label_points_suffix, reward.cost)
            itemView.findViewById<TextView>(R.id.text_reward_preview_body).text =
                reward.highlight
            bindStatusChip(
                labelView = itemView.findViewById(R.id.text_reward_preview_status),
                appearance = rewardStatusAppearance(reward.isRequested)
            )
            itemView.findViewById<MaterialButton>(R.id.button_reward_preview_open).setOnClickListener {
                findNavController().navigate(
                    R.id.rewardDetailsFragment,
                    Bundle().apply {
                        putString(RewardDetailsFragment.ARG_REWARD_ID, reward.id)
                    }
                )
            }
            container.addView(itemView)
        }
    }

    private fun choreStatusAppearance(status: ChoreStatus): ChildStatusAppearance = when (status) {
        ChoreStatus.OPEN -> ChildStatusAppearance(
            labelRes = R.string.label_status_in_progress,
            backgroundRes = R.drawable.bg_status_attention,
            textColorRes = R.color.status_attention_text
        )

        ChoreStatus.SUBMITTED -> ChildStatusAppearance(
            labelRes = R.string.label_status_awaiting_parent,
            backgroundRes = R.drawable.bg_status_positive,
            textColorRes = R.color.status_positive_text
        )

        ChoreStatus.APPROVED -> ChildStatusAppearance(
            labelRes = R.string.label_status_approved,
            backgroundRes = R.drawable.bg_status_positive,
            textColorRes = R.color.status_positive_text
        )

        ChoreStatus.REJECTED -> ChildStatusAppearance(
            labelRes = R.string.label_status_rejected,
            backgroundRes = R.drawable.bg_status_negative,
            textColorRes = R.color.status_negative_text
        )
    }

    private fun rewardStatusAppearance(isRequested: Boolean): ChildStatusAppearance =
        if (isRequested) {
            ChildStatusAppearance(
                labelRes = R.string.label_status_awaiting_parent,
                backgroundRes = R.drawable.bg_status_attention,
                textColorRes = R.color.status_attention_text
            )
        } else {
            ChildStatusAppearance(
                labelRes = R.string.label_status_active,
                backgroundRes = R.drawable.bg_status_positive,
                textColorRes = R.color.status_positive_text
            )
        }

    private fun bindStatusChip(labelView: TextView, appearance: ChildStatusAppearance) {
        labelView.text = getString(appearance.labelRes)
        labelView.setBackgroundResource(appearance.backgroundRes)
        labelView.setTextColor(ContextCompat.getColor(requireContext(), appearance.textColorRes))
    }

    private fun createEmptySummaryText(messageRes: Int): TextView = TextView(requireContext()).apply {
        text = getString(messageRes)
        TextViewCompat.setTextAppearance(this, R.style.TextAppearance_HOME_App_Supporting)
    }
}
