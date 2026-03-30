package com.example.hom_e_app.feature.parent.rewards

import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.example.hom_e_app.R
import com.example.hom_e_app.core.ui.BaseFragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class CreateEditRewardFragment : BaseFragment(R.layout.fragment_create_edit_reward) {

    companion object {
        const val ARG_FORM_MODE = "parent_reward_form_mode"
        const val ARG_TITLE = "parent_reward_title"
        const val ARG_DESCRIPTION = "parent_reward_description"
        const val ARG_POINTS = "parent_reward_points"
        const val ARG_IS_ACTIVE = "parent_reward_is_active"

        const val MODE_CREATE = "create"
        const val MODE_EDIT = "edit"
    }

    override fun bindScreenActions() {
        bindBack(R.id.button_cancel_reward)

        val titleLayout = requireView().findViewById<TextInputLayout>(R.id.input_layout_reward_title)
        val titleInput = requireView().findViewById<TextInputEditText>(R.id.edit_text_reward_title)
        val descriptionLayout =
            requireView().findViewById<TextInputLayout>(R.id.input_layout_reward_description)
        val descriptionInput =
            requireView().findViewById<TextInputEditText>(R.id.edit_text_reward_description)
        val pointsLayout =
            requireView().findViewById<TextInputLayout>(R.id.input_layout_reward_points)
        val pointsInput =
            requireView().findViewById<TextInputEditText>(R.id.edit_text_reward_points)
        val activeSwitch = requireView().findViewById<MaterialSwitch>(R.id.switch_reward_active)
        val previewText = requireView().findViewById<TextView>(R.id.text_reward_preview)
        val formTitle = requireView().findViewById<TextView>(R.id.text_reward_form_title)
        val formMode = requireView().findViewById<TextView>(R.id.text_reward_form_mode)
        val formSubtitle = requireView().findViewById<TextView>(R.id.text_reward_form_subtitle)

        val isEditMode = arguments?.getString(ARG_FORM_MODE) == MODE_EDIT
        if (isEditMode) {
            formTitle.text = getString(R.string.create_edit_reward_edit_title)
            formMode.text = getString(R.string.create_edit_reward_edit_title)
            formSubtitle.text = getString(R.string.create_edit_reward_edit_hint)
            titleInput.setText(arguments?.getString(ARG_TITLE).orEmpty())
            descriptionInput.setText(arguments?.getString(ARG_DESCRIPTION).orEmpty())
            pointsInput.setText(arguments?.getInt(ARG_POINTS)?.takeIf { it > 0 }?.toString().orEmpty())
            activeSwitch.isChecked = arguments?.getBoolean(ARG_IS_ACTIVE) == true
        } else {
            formTitle.text = getString(R.string.create_edit_reward_create_title)
            formMode.text = getString(R.string.create_edit_reward_create_title)
            formSubtitle.text = getString(R.string.create_edit_reward_create_hint)
            activeSwitch.isChecked = true
        }

        titleInput.doAfterTextChanged { titleLayout.error = null }
        descriptionInput.doAfterTextChanged { descriptionLayout.error = null }
        pointsInput.doAfterTextChanged { pointsLayout.error = null }

        requireView().findViewById<android.view.View>(R.id.button_save_reward).setOnClickListener {
            val title = titleInput.text?.toString()?.trim().orEmpty()
            val description = descriptionInput.text?.toString()?.trim().orEmpty()
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

            if (points == null || points <= 0) {
                pointsLayout.error = getString(R.string.error_positive_points)
                isValid = false
            } else {
                pointsLayout.error = null
            }

            if (!isValid) return@setOnClickListener

            val availability = if (activeSwitch.isChecked) {
                getString(R.string.label_status_active)
            } else {
                getString(R.string.label_status_inactive)
            }

            previewText.text = getString(R.string.message_reward_preview, title, points, availability)
            Snackbar.make(requireView(), R.string.message_reward_saved_local, Snackbar.LENGTH_SHORT)
                .show()
        }
    }
}
