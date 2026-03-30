package com.example.hom_e_app.feature.child.chores

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
import com.example.hom_e_app.core.data.ChildChoreItem
import com.example.hom_e_app.core.data.ChoreStatus
import com.example.hom_e_app.core.data.FirestoreFeatureRepository
import com.example.hom_e_app.core.ui.BaseFragment
import com.example.hom_e_app.feature.child.ChildStatusAppearance
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ChildChoresFragment : BaseFragment(R.layout.fragment_child_chores) {

    override fun onResume() {
        super.onResume()
        loadChoreCards()
    }

    private fun loadChoreCards() {
        val session = SessionManager.currentSession ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            FirestoreFeatureRepository.loadChildChores(requireContext(), session)
                .onSuccess { state ->
                    bindChoreCards(state.chores, state.waitingCount)
                }
                .onFailure {
                    Snackbar.make(requireView(), SessionManager.errorMessage(it), Snackbar.LENGTH_LONG).show()
                }
        }
    }

    private fun bindChoreCards(chores: List<ChildChoreItem>, waitingCount: Int) {
        val root = requireView()
        root.findViewById<TextView>(R.id.text_child_chores_summary).text =
            getString(R.string.child_chores_summary, chores.size, waitingCount)

        val container = root.findViewById<LinearLayout>(R.id.container_child_chores)
        val emptyState = root.findViewById<TextView>(R.id.text_child_chores_empty)
        container.removeAllViews()
        emptyState.isVisible = chores.isEmpty()

        val inflater = LayoutInflater.from(requireContext())
        chores.forEach { chore ->
            val itemView = inflater.inflate(R.layout.item_child_chore_card, container, false)
            itemView.findViewById<TextView>(R.id.text_child_chore_title).text = chore.title
            itemView.findViewById<TextView>(R.id.text_child_chore_body).text = chore.description
            itemView.findViewById<TextView>(R.id.text_child_chore_focus).text = chore.focus
            itemView.findViewById<TextView>(R.id.text_child_chore_points).text =
                getString(R.string.label_points_suffix, chore.points)
            bindStatusChip(
                itemView.findViewById(R.id.text_child_chore_status),
                choreStatusAppearance(chore.status)
            )
            itemView.findViewById<MaterialButton>(R.id.button_child_chore_open).setOnClickListener {
                findNavController().navigate(
                    R.id.choreDetailsFragment,
                    Bundle().apply { putString(ChoreDetailsFragment.ARG_CHORE_ID, chore.id) }
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

    private fun bindStatusChip(labelView: TextView, appearance: ChildStatusAppearance) {
        labelView.text = getString(appearance.labelRes)
        labelView.setBackgroundResource(appearance.backgroundRes)
        labelView.setTextColor(ContextCompat.getColor(requireContext(), appearance.textColorRes))
    }
}
