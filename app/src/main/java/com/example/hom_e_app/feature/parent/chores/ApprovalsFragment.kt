package com.example.hom_e_app.feature.parent.chores

import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.hom_e_app.R
import com.example.hom_e_app.core.auth.SessionManager
import com.example.hom_e_app.core.data.ApprovalsState
import com.example.hom_e_app.core.data.ChoreApprovalItem
import com.example.hom_e_app.core.data.FirestoreFeatureRepository
import com.example.hom_e_app.core.data.RewardApprovalItem
import com.example.hom_e_app.core.ui.BaseFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ApprovalsFragment : BaseFragment(R.layout.fragment_approvals) {

    override fun onResume() {
        super.onResume()
        loadApprovals()
    }

    private fun loadApprovals() {
        val session = SessionManager.currentSession ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            FirestoreFeatureRepository.loadApprovals(requireContext(), session)
                .onSuccess(::bindApprovals)
                .onFailure {
                    Snackbar.make(requireView(), SessionManager.errorMessage(it), Snackbar.LENGTH_LONG).show()
                }
        }
    }

    private fun bindApprovals(state: ApprovalsState) {
        bindChoreRequests(state.pendingChores)
        bindRewardRequests(state.pendingRewards)
        updatePendingState(state.pendingChores.size, state.pendingRewards.size)
    }

    private fun bindChoreRequests(items: List<ChoreApprovalItem>) {
        val session = SessionManager.currentSession ?: return
        val container = requireView().findViewById<LinearLayout>(R.id.layout_chore_requests)
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        items.forEach { item ->
            val card = inflater.inflate(R.layout.item_approval_request_card, container, false)
            card.findViewById<TextView>(R.id.text_approval_title).text = item.title
            card.findViewById<TextView>(R.id.text_approval_body).text = item.description
            card.findViewById<MaterialButton>(R.id.button_approval_approve).setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    FirestoreFeatureRepository.approveChore(requireContext(), session, item.id)
                        .onSuccess {
                            Snackbar.make(requireView(), R.string.message_request_approved, Snackbar.LENGTH_SHORT).show()
                            loadApprovals()
                        }
                        .onFailure {
                            Snackbar.make(requireView(), SessionManager.errorMessage(it), Snackbar.LENGTH_LONG).show()
                        }
                }
            }
            card.findViewById<MaterialButton>(R.id.button_approval_decline).setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    FirestoreFeatureRepository.rejectChore(requireContext(), session, item.id)
                        .onSuccess {
                            Snackbar.make(requireView(), R.string.message_request_rejected, Snackbar.LENGTH_SHORT).show()
                            loadApprovals()
                        }
                        .onFailure {
                            Snackbar.make(requireView(), SessionManager.errorMessage(it), Snackbar.LENGTH_LONG).show()
                        }
                }
            }
            container.addView(card)
        }
    }

    private fun bindRewardRequests(items: List<RewardApprovalItem>) {
        val session = SessionManager.currentSession ?: return
        val container = requireView().findViewById<LinearLayout>(R.id.layout_reward_requests)
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        items.forEach { item ->
            val card = inflater.inflate(R.layout.item_approval_request_card, container, false)
            card.findViewById<TextView>(R.id.text_approval_title).text = item.title
            card.findViewById<TextView>(R.id.text_approval_body).text = item.description
            card.findViewById<MaterialButton>(R.id.button_approval_approve).setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    FirestoreFeatureRepository.approveRewardRequest(requireContext(), session, item.id)
                        .onSuccess {
                            Snackbar.make(requireView(), R.string.message_request_approved, Snackbar.LENGTH_SHORT).show()
                            loadApprovals()
                        }
                        .onFailure {
                            Snackbar.make(requireView(), SessionManager.errorMessage(it), Snackbar.LENGTH_LONG).show()
                        }
                }
            }
            card.findViewById<MaterialButton>(R.id.button_approval_decline).setOnClickListener {
                viewLifecycleOwner.lifecycleScope.launch {
                    FirestoreFeatureRepository.rejectRewardRequest(requireContext(), session, item.id)
                        .onSuccess {
                            Snackbar.make(requireView(), R.string.message_request_rejected, Snackbar.LENGTH_SHORT).show()
                            loadApprovals()
                        }
                        .onFailure {
                            Snackbar.make(requireView(), SessionManager.errorMessage(it), Snackbar.LENGTH_LONG).show()
                        }
                }
            }
            container.addView(card)
        }
    }

    private fun updatePendingState(remainingChores: Int, remainingRewards: Int) {
        requireView().findViewById<TextView>(R.id.text_chore_pending_count).text =
            getString(R.string.label_pending_count, remainingChores)
        requireView().findViewById<TextView>(R.id.text_reward_pending_count).text =
            getString(R.string.label_pending_count, remainingRewards)
        requireView().findViewById<TextView>(R.id.text_chore_empty_state).isVisible =
            remainingChores == 0
        requireView().findViewById<TextView>(R.id.text_reward_empty_state).isVisible =
            remainingRewards == 0
        requireView().findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_approvals_empty).isVisible =
            remainingChores == 0 && remainingRewards == 0
    }
}
