package com.example.hom_e_app.feature.child.chores

import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.hom_e_app.R
import com.example.hom_e_app.core.auth.SessionManager
import com.example.hom_e_app.core.data.ChildChoreItem
import com.example.hom_e_app.core.data.ChoreStatus
import com.example.hom_e_app.core.data.FirestoreFeatureRepository
import com.example.hom_e_app.core.ui.BaseFragment
import com.example.hom_e_app.feature.child.ChildStatusAppearance
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ChoreDetailsFragment : BaseFragment(R.layout.fragment_chore_details) {

    override fun bindScreenActions() {
        requireView().findViewById<MaterialButton>(R.id.button_submit_chore).setOnClickListener {
            submitCurrentChore()
        }
    }

    override fun onResume() {
        super.onResume()
        loadChoreDetails()
    }

    private fun loadChoreDetails() {
        val session = SessionManager.currentSession ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            FirestoreFeatureRepository.loadChildChoreDetails(requireContext(), session, currentChoreId())
                .onSuccess(::bindChoreDetails)
                .onFailure {
                    Snackbar.make(requireView(), SessionManager.errorMessage(it), Snackbar.LENGTH_LONG).show()
                }
        }
    }

    private fun submitCurrentChore() {
        val session = SessionManager.currentSession ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            FirestoreFeatureRepository.submitChore(requireContext(), session, currentChoreId())
                .onSuccess {
                    Snackbar.make(requireView(), R.string.message_child_chore_submitted, Snackbar.LENGTH_SHORT).show()
                    loadChoreDetails()
                }
                .onFailure {
                    Snackbar.make(requireView(), SessionManager.errorMessage(it), Snackbar.LENGTH_LONG).show()
                }
        }
    }

    private fun bindChoreDetails(chore: ChildChoreItem) {
        val root = requireView()
        val appearance = choreStatusAppearance(chore.status)
        val isWaiting = chore.status == ChoreStatus.SUBMITTED

        root.findViewById<TextView>(R.id.text_chore_detail_title).text = chore.title
        root.findViewById<TextView>(R.id.text_chore_detail_points).text =
            getString(R.string.label_points_value, chore.points)
        root.findViewById<TextView>(R.id.text_chore_detail_body).text = chore.description
        root.findViewById<TextView>(R.id.text_chore_review_note).text =
            if (isWaiting) {
                getString(R.string.child_chore_review_waiting)
            } else {
                getString(R.string.child_chore_review_idle)
            }

        root.findViewById<TextView>(R.id.text_chore_detail_status).apply {
            text = getString(appearance.labelRes)
            setBackgroundResource(appearance.backgroundRes)
            setTextColor(ContextCompat.getColor(requireContext(), appearance.textColorRes))
        }

        root.findViewById<MaterialButton>(R.id.button_submit_chore).apply {
            isEnabled = chore.status == ChoreStatus.OPEN
            text = getString(
                if (isWaiting) R.string.action_chore_submitted else R.string.action_submit_chore
            )
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

    private fun currentChoreId(): String =
        arguments?.getString(ARG_CHORE_ID) ?: DEFAULT_CHORE_ID

    companion object {
        const val ARG_CHORE_ID = "child_chore_id"
        private const val DEFAULT_CHORE_ID = "kitchen_reset"
    }
}
