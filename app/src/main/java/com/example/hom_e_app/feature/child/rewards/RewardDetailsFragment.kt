package com.example.hom_e_app.feature.child.rewards

import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.hom_e_app.R
import com.example.hom_e_app.core.auth.SessionManager
import com.example.hom_e_app.core.data.ChildRewardDetails
import com.example.hom_e_app.core.data.FirestoreFeatureRepository
import com.example.hom_e_app.core.ui.BaseFragment
import com.example.hom_e_app.feature.child.ChildStatusAppearance
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class RewardDetailsFragment : BaseFragment(R.layout.fragment_reward_details) {

    override fun bindScreenActions() {
        requireView().findViewById<MaterialButton>(R.id.button_request_reward).setOnClickListener {
            requestCurrentReward()
        }
    }

    override fun onResume() {
        super.onResume()
        loadRewardDetails()
    }

    private fun loadRewardDetails() {
        val session = SessionManager.currentSession ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            FirestoreFeatureRepository.loadChildRewardDetails(requireContext(), session, currentRewardId())
                .onSuccess(::bindRewardDetails)
                .onFailure {
                    Snackbar.make(requireView(), SessionManager.errorMessage(it), Snackbar.LENGTH_LONG).show()
                }
        }
    }

    private fun requestCurrentReward() {
        val session = SessionManager.currentSession ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            FirestoreFeatureRepository.createRewardRequest(requireContext(), session, currentRewardId())
                .onSuccess {
                    Snackbar.make(requireView(), R.string.message_child_reward_requested, Snackbar.LENGTH_SHORT).show()
                    loadRewardDetails()
                }
                .onFailure {
                    Snackbar.make(requireView(), SessionManager.errorMessage(it), Snackbar.LENGTH_LONG).show()
                }
        }
    }

    private fun bindRewardDetails(details: ChildRewardDetails) {
        val reward = details.reward
        val root = requireView()
        val appearance = rewardStatusAppearance(reward.isRequested)
        val isRequested = reward.isRequested

        root.findViewById<TextView>(R.id.text_reward_detail_title).text = reward.title
        root.findViewById<TextView>(R.id.text_reward_detail_cost).text =
            getString(R.string.label_cost_value, reward.cost)
        root.findViewById<TextView>(R.id.text_reward_detail_body).text = reward.description
        root.findViewById<TextView>(R.id.text_reward_detail_status).apply {
            text = getString(appearance.labelRes)
            setBackgroundResource(appearance.backgroundRes)
            setTextColor(ContextCompat.getColor(requireContext(), appearance.textColorRes))
        }
        root.findViewById<TextView>(R.id.text_reward_request_note).text =
            if (isRequested) {
                getString(R.string.child_reward_requested_note)
            } else {
                getString(R.string.child_reward_request_ready_note)
            }
        root.findViewById<MaterialButton>(R.id.button_request_reward).apply {
            isEnabled = !isRequested
            text = getString(
                if (isRequested) R.string.action_reward_requested else R.string.action_request_reward
            )
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

    private fun currentRewardId(): String =
        arguments?.getString(ARG_REWARD_ID) ?: DEFAULT_REWARD_ID

    companion object {
        const val ARG_REWARD_ID = "child_reward_id"
        private const val DEFAULT_REWARD_ID = "movie_night"
    }
}
