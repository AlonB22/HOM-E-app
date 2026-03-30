package com.example.hom_e_app.feature.parent.rewards

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
import com.example.hom_e_app.core.data.FirestoreFeatureRepository
import com.example.hom_e_app.core.data.ParentRewardListItem
import com.example.hom_e_app.core.ui.BaseFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ParentRewardsFragment : BaseFragment(R.layout.fragment_parent_rewards) {

    override fun bindScreenActions() {
        view?.findViewById<android.view.View>(R.id.button_create_reward)?.setOnClickListener {
            findNavController().navigate(
                R.id.createEditRewardFragment,
                Bundle().apply {
                    putString(CreateEditRewardFragment.ARG_FORM_MODE, CreateEditRewardFragment.MODE_CREATE)
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        loadParentRewards()
    }

    private fun loadParentRewards() {
        val session = SessionManager.currentSession ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            FirestoreFeatureRepository.loadParentRewards(requireContext(), session)
                .onSuccess(::bindRewards)
                .onFailure {
                    Snackbar.make(requireView(), SessionManager.errorMessage(it), Snackbar.LENGTH_LONG).show()
                }
        }
    }

    private fun bindRewards(rewards: List<ParentRewardListItem>) {
        val root = requireView()
        val container = root.findViewById<LinearLayout>(R.id.container_parent_rewards)
        val emptyState = root.findViewById<TextView>(R.id.text_parent_rewards_empty)
        container.removeAllViews()
        emptyState.isVisible = rewards.isEmpty()

        val inflater = LayoutInflater.from(requireContext())
        rewards.forEach { reward ->
            val itemView = inflater.inflate(R.layout.item_parent_reward_card, container, false)
            itemView.findViewById<TextView>(R.id.text_parent_reward_title).text = reward.title
            itemView.findViewById<TextView>(R.id.text_parent_reward_body).text = reward.description
            itemView.findViewById<TextView>(R.id.text_parent_reward_points).text =
                getString(R.string.label_points_suffix, reward.cost)
            bindStatusChip(itemView.findViewById(R.id.text_parent_reward_status), reward.isActive)
            itemView.findViewById<MaterialButton>(R.id.button_parent_reward_edit).setOnClickListener {
                findNavController().navigate(
                    R.id.createEditRewardFragment,
                    Bundle().apply {
                        putString(CreateEditRewardFragment.ARG_FORM_MODE, CreateEditRewardFragment.MODE_EDIT)
                        putString(CreateEditRewardFragment.ARG_REWARD_ID, reward.id)
                        putString(CreateEditRewardFragment.ARG_TITLE, reward.title)
                        putString(CreateEditRewardFragment.ARG_DESCRIPTION, reward.description)
                        putInt(CreateEditRewardFragment.ARG_POINTS, reward.cost)
                        putBoolean(CreateEditRewardFragment.ARG_IS_ACTIVE, reward.isActive)
                    }
                )
            }
            container.addView(itemView)
        }
    }

    private fun bindStatusChip(labelView: TextView, isActive: Boolean) {
        val (labelRes, backgroundRes, textColorRes) = if (isActive) {
            Triple(
                R.string.label_status_active,
                R.drawable.bg_status_positive,
                R.color.status_positive_text
            )
        } else {
            Triple(
                R.string.label_status_inactive,
                R.drawable.bg_status_attention,
                R.color.status_attention_text
            )
        }
        labelView.text = getString(labelRes)
        labelView.setBackgroundResource(backgroundRes)
        labelView.setTextColor(ContextCompat.getColor(requireContext(), textColorRes))
    }
}
