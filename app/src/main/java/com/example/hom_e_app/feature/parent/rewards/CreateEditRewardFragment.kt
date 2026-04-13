package com.example.hom_e_app.feature.parent.rewards

import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.hom_e_app.R
import com.example.hom_e_app.core.auth.SessionManager
import com.example.hom_e_app.core.data.FirestoreFeatureRepository
import com.example.hom_e_app.core.data.ParentRewardDraft
import com.example.hom_e_app.core.data.ParentRewardForm
import com.example.hom_e_app.core.ui.BaseFragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class CreateEditRewardFragment : BaseFragment(R.layout.fragment_create_edit_reward) {

    companion object {
        const val ARG_FORM_MODE = "parent_reward_form_mode"
        const val ARG_REWARD_ID = "parent_reward_id"
        const val ARG_TITLE = "parent_reward_title"
        const val ARG_DESCRIPTION = "parent_reward_description"
        const val ARG_POINTS = "parent_reward_points"
        const val ARG_IS_ACTIVE = "parent_reward_is_active"

        const val MODE_CREATE = "create"
        const val MODE_EDIT = "edit"
    }

    private var isSaving = false

    override fun bindScreenActions() {
        bindBack(R.id.button_cancel_reward)

        val root = requireView()
        val titleLayout = root.findViewById<TextInputLayout>(R.id.input_layout_reward_title)
        val titleInput = root.findViewById<TextInputEditText>(R.id.edit_text_reward_title)
        val descriptionLayout = root.findViewById<TextInputLayout>(R.id.input_layout_reward_description)
        val descriptionInput = root.findViewById<TextInputEditText>(R.id.edit_text_reward_description)
        val pointsLayout = root.findViewById<TextInputLayout>(R.id.input_layout_reward_points)
        val pointsInput = root.findViewById<TextInputEditText>(R.id.edit_text_reward_points)
        val activeSwitch = root.findViewById<MaterialSwitch>(R.id.switch_reward_active)
        val saveButton = root.findViewById<android.view.View>(R.id.button_save_reward)

        val isEditMode = arguments?.getString(ARG_FORM_MODE) == MODE_EDIT
        if (isEditMode) {
            titleInput.setText(arguments?.getString(ARG_TITLE).orEmpty())
            descriptionInput.setText(arguments?.getString(ARG_DESCRIPTION).orEmpty())
            pointsInput.setText(arguments?.getInt(ARG_POINTS)?.takeIf { it > 0 }?.toString().orEmpty())
            activeSwitch.isChecked = arguments?.getBoolean(ARG_IS_ACTIVE) == true
        } else {
            activeSwitch.isChecked = true
        }

        titleInput.doAfterTextChanged { titleLayout.error = null }
        descriptionInput.doAfterTextChanged { descriptionLayout.error = null }
        pointsInput.doAfterTextChanged { pointsLayout.error = null }

        saveButton.setOnClickListener {
            if (isSaving) return@setOnClickListener

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

            val session = SessionManager.currentSession ?: return@setOnClickListener
            val draft = ParentRewardDraft(
                title = title,
                description = description,
                cost = points!!,
                isActive = activeSwitch.isChecked,
            )

            viewLifecycleOwner.lifecycleScope.launch {
                setSavingState(true, saveButton)
                val result = if (isEditMode) {
                    FirestoreFeatureRepository.updateParentReward(
                        requireContext(),
                        session,
                        currentRewardId(),
                        draft,
                    )
                } else {
                    FirestoreFeatureRepository.createParentReward(requireContext(), session, draft)
                }
                setSavingState(false, saveButton)

                result.onSuccess {
                    Snackbar.make(
                        requireView(),
                        getString(
                            if (isEditMode) {
                                R.string.message_reward_updated
                            } else {
                                R.string.message_reward_created
                            }
                        ),
                        Snackbar.LENGTH_SHORT
                    ).show()
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }.onFailure {
                    Snackbar.make(requireView(), SessionManager.errorMessage(it), Snackbar.LENGTH_LONG).show()
                }
            }
        }

        if (isEditMode) {
            loadRewardForEdit()
        }
    }

    private fun loadRewardForEdit() {
        val session = SessionManager.currentSession ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            FirestoreFeatureRepository.loadParentRewardForm(requireContext(), session, currentRewardId())
                .onSuccess(::bindExistingReward)
                .onFailure {
                    Snackbar.make(requireView(), SessionManager.errorMessage(it), Snackbar.LENGTH_LONG).show()
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
        }
    }

    private fun bindExistingReward(reward: ParentRewardForm) {
        val root = requireView()
        root.findViewById<TextInputEditText>(R.id.edit_text_reward_title).setText(reward.title)
        root.findViewById<TextInputEditText>(R.id.edit_text_reward_description).setText(reward.description)
        root.findViewById<TextInputEditText>(R.id.edit_text_reward_points).setText(reward.cost.toString())
        root.findViewById<MaterialSwitch>(R.id.switch_reward_active).isChecked = reward.isActive
    }

    private fun setSavingState(isSaving: Boolean, saveButton: android.view.View) {
        this.isSaving = isSaving
        saveButton.isEnabled = !isSaving
    }

    private fun currentRewardId(): String =
        arguments?.getString(ARG_REWARD_ID) ?: error("Missing reward ID for edit mode.")
}
