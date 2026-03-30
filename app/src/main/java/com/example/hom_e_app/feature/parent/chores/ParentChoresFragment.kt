package com.example.hom_e_app.feature.parent.chores

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
import com.example.hom_e_app.core.data.ChoreStatus
import com.example.hom_e_app.core.data.FirestoreFeatureRepository
import com.example.hom_e_app.core.data.ParentChoreListItem
import com.example.hom_e_app.core.ui.BaseFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ParentChoresFragment : BaseFragment(R.layout.fragment_parent_chores) {

    override fun bindScreenActions() {
        view?.findViewById<android.view.View>(R.id.button_create_chore)?.setOnClickListener {
            findNavController().navigate(
                R.id.createEditChoreFragment,
                Bundle().apply {
                    putString(CreateEditChoreFragment.ARG_FORM_MODE, CreateEditChoreFragment.MODE_CREATE)
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        loadParentChores()
    }

    private fun loadParentChores() {
        val session = SessionManager.currentSession ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            FirestoreFeatureRepository.loadParentChores(requireContext(), session)
                .onSuccess(::bindParentChores)
                .onFailure {
                    Snackbar.make(requireView(), SessionManager.errorMessage(it), Snackbar.LENGTH_LONG).show()
                }
        }
    }

    private fun bindParentChores(chores: List<ParentChoreListItem>) {
        val root = requireView()
        val container = root.findViewById<LinearLayout>(R.id.container_parent_chores)
        val emptyState = root.findViewById<TextView>(R.id.text_parent_chores_empty)
        container.removeAllViews()
        emptyState.isVisible = chores.isEmpty()

        val inflater = LayoutInflater.from(requireContext())
        chores.forEach { chore ->
            val itemView = inflater.inflate(R.layout.item_parent_chore_card, container, false)
            itemView.findViewById<TextView>(R.id.text_parent_chore_title).text = chore.title
            itemView.findViewById<TextView>(R.id.text_parent_chore_body).text = chore.description
            itemView.findViewById<TextView>(R.id.text_parent_chore_meta).text =
                getString(R.string.label_assigned_to, chore.assigneeName)
            itemView.findViewById<TextView>(R.id.text_parent_chore_points).text =
                getString(R.string.label_points_suffix, chore.points)
            bindStatusChip(itemView.findViewById(R.id.text_parent_chore_status), chore.status)
            itemView.findViewById<MaterialButton>(R.id.button_parent_chore_edit).setOnClickListener {
                findNavController().navigate(
                    R.id.createEditChoreFragment,
                    Bundle().apply {
                        putString(CreateEditChoreFragment.ARG_FORM_MODE, CreateEditChoreFragment.MODE_EDIT)
                        putString(CreateEditChoreFragment.ARG_TITLE, chore.title)
                        putString(CreateEditChoreFragment.ARG_DESCRIPTION, chore.description)
                        putString(CreateEditChoreFragment.ARG_CHILD_NAME, chore.assigneeName)
                        putInt(CreateEditChoreFragment.ARG_POINTS, chore.points)
                    }
                )
            }
            container.addView(itemView)
        }
    }

    private fun bindStatusChip(labelView: TextView, status: ChoreStatus) {
        val (labelRes, backgroundRes, textColorRes) = when (status) {
            ChoreStatus.OPEN -> Triple(
                R.string.label_status_in_progress,
                R.drawable.bg_status_attention,
                R.color.status_attention_text
            )

            ChoreStatus.SUBMITTED -> Triple(
                R.string.label_status_ready_for_review,
                R.drawable.bg_status_positive,
                R.color.status_positive_text
            )

            ChoreStatus.APPROVED -> Triple(
                R.string.label_status_approved,
                R.drawable.bg_status_positive,
                R.color.status_positive_text
            )

            ChoreStatus.REJECTED -> Triple(
                R.string.label_status_rejected,
                R.drawable.bg_status_negative,
                R.color.status_negative_text
            )
        }
        labelView.text = getString(labelRes)
        labelView.setBackgroundResource(backgroundRes)
        labelView.setTextColor(ContextCompat.getColor(requireContext(), textColorRes))
    }
}
