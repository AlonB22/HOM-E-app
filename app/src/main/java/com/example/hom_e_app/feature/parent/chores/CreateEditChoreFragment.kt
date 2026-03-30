package com.example.hom_e_app.feature.parent.chores

import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.example.hom_e_app.R
import com.example.hom_e_app.core.ui.BaseFragment
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class CreateEditChoreFragment : BaseFragment(R.layout.fragment_create_edit_chore) {

    companion object {
        const val ARG_FORM_MODE = "parent_chore_form_mode"
        const val ARG_TITLE = "parent_chore_title"
        const val ARG_DESCRIPTION = "parent_chore_description"
        const val ARG_CHILD_NAME = "parent_chore_child_name"
        const val ARG_POINTS = "parent_chore_points"

        const val MODE_CREATE = "create"
        const val MODE_EDIT = "edit"
    }

    override fun bindScreenActions() {
        bindBack(R.id.button_cancel_chore)

        val titleLayout = requireView().findViewById<TextInputLayout>(R.id.input_layout_chore_title)
        val titleInput = requireView().findViewById<TextInputEditText>(R.id.edit_text_chore_title)
        val descriptionLayout =
            requireView().findViewById<TextInputLayout>(R.id.input_layout_chore_description)
        val descriptionInput =
            requireView().findViewById<TextInputEditText>(R.id.edit_text_chore_description)
        val childLayout = requireView().findViewById<TextInputLayout>(R.id.input_layout_chore_child)
        val childInput =
            requireView().findViewById<MaterialAutoCompleteTextView>(R.id.auto_complete_chore_child)
        val pointsLayout = requireView().findViewById<TextInputLayout>(R.id.input_layout_chore_points)
        val pointsInput =
            requireView().findViewById<TextInputEditText>(R.id.edit_text_chore_points)
        val previewText = requireView().findViewById<TextView>(R.id.text_chore_preview)
        val formTitle = requireView().findViewById<TextView>(R.id.text_chore_form_title)
        val formMode = requireView().findViewById<TextView>(R.id.text_chore_form_mode)
        val formSubtitle = requireView().findViewById<TextView>(R.id.text_chore_form_subtitle)

        val childNames = listOf(
            getString(R.string.demo_child_maya),
            getString(R.string.demo_child_liam),
            getString(R.string.demo_child_noa)
        )
        childInput.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, childNames)
        )

        val isEditMode = arguments?.getString(ARG_FORM_MODE) == MODE_EDIT
        if (isEditMode) {
            formTitle.text = getString(R.string.create_edit_chore_edit_title)
            formMode.text = getString(R.string.create_edit_chore_edit_title)
            formSubtitle.text = getString(R.string.create_edit_chore_edit_hint)
            titleInput.setText(arguments?.getString(ARG_TITLE).orEmpty())
            descriptionInput.setText(arguments?.getString(ARG_DESCRIPTION).orEmpty())
            childInput.setText(arguments?.getString(ARG_CHILD_NAME).orEmpty(), false)
            pointsInput.setText(arguments?.getInt(ARG_POINTS)?.takeIf { it > 0 }?.toString().orEmpty())
        } else {
            formTitle.text = getString(R.string.create_edit_chore_create_title)
            formMode.text = getString(R.string.create_edit_chore_create_title)
            formSubtitle.text = getString(R.string.create_edit_chore_create_hint)
        }

        titleInput.doAfterTextChanged { titleLayout.error = null }
        descriptionInput.doAfterTextChanged { descriptionLayout.error = null }
        pointsInput.doAfterTextChanged { pointsLayout.error = null }
        childInput.doAfterTextChanged { childLayout.error = null }

        requireView().findViewById<android.view.View>(R.id.button_save_chore).setOnClickListener {
            val title = titleInput.text?.toString()?.trim().orEmpty()
            val description = descriptionInput.text?.toString()?.trim().orEmpty()
            val childName = childInput.text?.toString()?.trim().orEmpty()
            val points = pointsInput.text?.toString()?.trim()?.toIntOrNull()

            var isValid = true

            if (title.isBlank()) {
                titleLayout.error = getString(R.string.error_required_field)
                isValid = false
            } else {
                titleLayout.error = null
            }

            if (description.isBlank()) {
                descriptionLayout.error = getString(R.string.error_required_field)
                isValid = false
            } else if (description.length > 80) {
                descriptionLayout.error = getString(R.string.error_short_description_length)
                isValid = false
            } else {
                descriptionLayout.error = null
            }

            if (childName !in childNames) {
                childLayout.error = getString(R.string.error_select_child)
                isValid = false
            } else {
                childLayout.error = null
            }

            if (points == null || points <= 0) {
                pointsLayout.error = getString(R.string.error_positive_points)
                isValid = false
            } else {
                pointsLayout.error = null
            }

            if (!isValid) return@setOnClickListener

            previewText.text = getString(R.string.message_chore_preview, title, childName, points)
            Snackbar.make(requireView(), R.string.message_chore_saved_local, Snackbar.LENGTH_SHORT)
                .show()
        }
    }
}
