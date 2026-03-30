package com.example.hom_e_app.feature.child.rewards

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.hom_e_app.R
import com.example.hom_e_app.core.auth.SessionManager
import com.example.hom_e_app.core.data.ChildRewardItem
import com.example.hom_e_app.core.data.FirestoreFeatureRepository
import com.example.hom_e_app.core.ui.BaseFragment
import com.example.hom_e_app.feature.child.ChildStatusAppearance
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ChildRewardsFragment : BaseFragment(R.layout.fragment_child_rewards) {

    override fun onResume() {
        super.onResume()
        loadRewards()
    }

    private fun loadRewards() {
        val session = SessionManager.currentSession ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            FirestoreFeatureRepository.loadChildRewards(requireContext(), session)
                .onSuccess { state ->
                    bindRewards(state.rewards, state.pointsBalance, state.requestedCount)
                }
                .onFailure {
                    Snackbar.make(requireView(), SessionManager.errorMessage(it), Snackbar.LENGTH_LONG).show()
                }
        }
    }

    private fun bindRewards(rewards: List<ChildRewardItem>, pointsBalance: Int, requestedCount: Int) {
        val root = requireView()
        root.findViewById<TextView>(R.id.text_rewards_summary).text =
            getString(R.string.child_rewards_summary, pointsBalance, requestedCount)

        val container = root.findViewById<LinearLayout>(R.id.container_child_rewards)
        val emptyState = root.findViewById<TextView>(R.id.text_child_rewards_empty)
        container.removeAllViews()
        emptyState.isVisible = rewards.isEmpty()

        val inflater = LayoutInflater.from(requireContext())
        rewards.forEach { reward ->
            val itemView = inflater.inflate(R.layout.item_child_reward_card, container, false)
            itemView.findViewById<TextView>(R.id.text_child_reward_title).text = reward.title
            itemView.findViewById<TextView>(R.id.text_child_reward_body).text = reward.description
            itemView.findViewById<TextView>(R.id.text_child_reward_highlight).text = reward.highlight
            itemView.findViewById<TextView>(R.id.text_child_reward_cost).text =
                getString(R.string.label_points_suffix, reward.cost)
            bindStatusChip(
                itemView.findViewById(R.id.text_child_reward_status),
                rewardStatusAppearance(reward.isRequested)
            )
            itemView.findViewById<MaterialButton>(R.id.button_child_reward_open).setOnClickListener {
                findNavController().navigate(
                    R.id.rewardDetailsFragment,
                    Bundle().apply { putString(RewardDetailsFragment.ARG_REWARD_ID, reward.id) }
                )
            }
            container.addView(itemView)
        }
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
}
