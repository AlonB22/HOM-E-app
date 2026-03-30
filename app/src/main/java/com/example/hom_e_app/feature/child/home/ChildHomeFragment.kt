package com.example.hom_e_app.feature.child.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import com.example.hom_e_app.R
import com.example.hom_e_app.core.ui.BaseFragment
import com.example.hom_e_app.feature.child.ChildChore
import com.example.hom_e_app.feature.child.ChildDemoState
import com.example.hom_e_app.feature.child.ChildReward
import com.example.hom_e_app.feature.child.chores.ChoreDetailsFragment
import com.example.hom_e_app.feature.child.rewards.RewardDetailsFragment
import com.google.android.material.button.MaterialButton

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
        bindHomeContent()
    }

    private fun bindHomeContent() {
        val root = requireView()
        val points = ChildDemoState.currentPoints()
        val waitingCount = ChildDemoState.waitingApprovalCount()
        val nextReward = ChildDemoState.nextRewardGoal()

        root.findViewById<TextView>(R.id.text_points_value).text =
            getString(R.string.label_points_suffix, points)
        root.findViewById<TextView>(R.id.text_points_helper).text =
            if (nextReward == null) {
                getString(R.string.child_home_points_ready_all_requested)
            } else if (points >= nextReward.cost) {
                getString(R.string.child_home_points_ready_message, getString(nextReward.titleRes))
            } else {
                getString(
                    R.string.child_home_points_goal_message,
                    nextReward.cost - points,
                    getString(nextReward.titleRes)
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

        bindPriorityChores(root.findViewById(R.id.container_priority_chores))
        bindRewardPreview(root.findViewById(R.id.container_reward_preview))
    }

    private fun bindPriorityChores(container: LinearLayout) {
        val inflater = LayoutInflater.from(requireContext())
        container.removeAllViews()
        ChildDemoState.priorityChores().forEach { chore ->
            val itemView = inflater.inflate(R.layout.item_child_priority_chore, container, false)
            itemView.findViewById<TextView>(R.id.text_priority_title).text = getString(chore.titleRes)
            itemView.findViewById<TextView>(R.id.text_priority_points).text =
                getString(R.string.label_points_suffix, chore.points)
            itemView.findViewById<TextView>(R.id.text_priority_body).text =
                getString(chore.focusRes)
            bindStatusChip(
                labelView = itemView.findViewById(R.id.text_priority_status),
                appearance = ChildDemoState.choreStatusAppearance(chore.status)
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

    private fun bindRewardPreview(container: LinearLayout) {
        val inflater = LayoutInflater.from(requireContext())
        container.removeAllViews()
        ChildDemoState.previewRewards().forEach { reward ->
            val itemView = inflater.inflate(R.layout.item_child_reward_preview, container, false)
            itemView.findViewById<TextView>(R.id.text_reward_preview_title).text =
                getString(reward.titleRes)
            itemView.findViewById<TextView>(R.id.text_reward_preview_cost).text =
                getString(R.string.label_points_suffix, reward.cost)
            itemView.findViewById<TextView>(R.id.text_reward_preview_body).text =
                getString(reward.highlightRes)
            bindStatusChip(
                labelView = itemView.findViewById(R.id.text_reward_preview_status),
                appearance = ChildDemoState.rewardStatusAppearance(reward.requestStatus)
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

    private fun bindStatusChip(labelView: TextView, appearance: com.example.hom_e_app.feature.child.ChildStatusAppearance) {
        labelView.text = getString(appearance.labelRes)
        labelView.setBackgroundResource(appearance.backgroundRes)
        labelView.setTextColor(ContextCompat.getColor(requireContext(), appearance.textColorRes))
    }
}
