package com.example.hom_e_app.feature.parent.chores

import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.hom_e_app.R
import com.example.hom_e_app.core.auth.SessionManager
import com.example.hom_e_app.core.data.ChoreStatus
import com.example.hom_e_app.core.data.FamilyChildOption
import com.example.hom_e_app.core.data.FirestoreFeatureRepository
import com.example.hom_e_app.core.data.ParentChoreDraft
import com.example.hom_e_app.core.data.ParentChoreForm
import com.example.hom_e_app.core.ui.BaseFragment
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class CreateEditChoreFragment : BaseFragment(R.layout.fragment_create_edit_chore) {

    companion object {
        const val ARG_FORM_MODE = "parent_chore_form_mode"
        const val ARG_CHORE_ID = "parent_chore_id"
        const val ARG_TITLE = "parent_chore_title"
        const val ARG_DESCRIPTION = "parent_chore_description"
        const val ARG_CHILD_NAME = "parent_chore_child_name"
        const val ARG_POINTS = "parent_chore_points"

        const val MODE_CREATE = "create"
        const val MODE_EDIT = "edit"
    }

    private var childOptions: List<FamilyChildOption> = emptyList()
    private var selectedChildId: String? = null
    private var isSaving = false

    override fun bindScreenActions() {
        bindBack(R.id.button_cancel_chore)

        val root = requireView()
        val titleLayout = root.findViewById<TextInputLayout>(R.id.input_layout_chore_title)
        val titleInput = root.findViewById<TextInputEditText>(R.id.edit_text_chore_title)
        val descriptionLayout = root.findViewById<TextInputLayout>(R.id.input_layout_chore_description)
        val descriptionInput = root.findViewById<TextInputEditText>(R.id.edit_text_chore_description)
        val childLayout = root.findViewById<TextInputLayout>(R.id.input_layout_chore_child)
        val childInput = root.findViewById<MaterialAutoCompleteTextView>(R.id.auto_complete_chore_child)
        val pointsLayout = root.findViewById<TextInputLayout>(R.id.input_layout_chore_points)
        val pointsInput = root.findViewById<TextInputEditText>(R.id.edit_text_chore_points)
        val previewText = root.findViewById<TextView>(R.id.text_chore_preview)
        val formTitle = root.findViewById<TextView>(R.id.text_chore_form_title)
        val formMode = root.findViewById<TextView>(R.id.text_chore_form_mode)
        val formSubtitle = root.findViewById<TextView>(R.id.text_chore_form_subtitle)
        val saveButton = root.findViewById<android.view.View>(R.id.button_save_chore)

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
        childInput.doAfterTextChanged {
            childLayout.error = null
            selectedChildId = resolveUniqueChildId(it?.toString()?.trim().orEmpty())
        }
        childInput.setOnItemClickListener { _, _, position, _ ->
            selectedChildId = childOptions.getOrNull(position)?.id
        }

        saveButton.setOnClickListener {
            if (isSaving) return@setOnClickListener

            val title = titleInput.text?.toString()?.trim().orEmpty()
            val description = descriptionInput.text?.toString()?.trim().orEmpty()
            val childName = childInput.text?.toString()?.trim().orEmpty()
            val selectedChild = resolveSelectedChild(childName)
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

            if (selectedChild == null) {
                childLayout.error = if (childOptions.isEmpty()) {
                    getString(R.string.error_no_child_available)
                } else {
                    getString(R.string.error_select_child)
                }
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

            if (!isValid || selectedChild == null) return@setOnClickListener

            previewText.text = getString(R.string.message_chore_preview, title, selectedChild.displayName, points)

            val session = SessionManager.currentSession ?: return@setOnClickListener
            val draft = ParentChoreDraft(
                title = title,
                description = description,
                assigneeMemberId = selectedChild.id,
                points = points!!,
            )

            viewLifecycleOwner.lifecycleScope.launch {
                setSavingState(true, saveButton)
                val result = if (isEditMode) {
                    FirestoreFeatureRepository.updateParentChore(
                        requireContext(),
                        session,
                        currentChoreId(),
                        draft,
                    )
                } else {
                    FirestoreFeatureRepository.createParentChore(requireContext(), session, draft)
                }
                setSavingState(false, saveButton)

                result.onSuccess {
                    Snackbar.make(
                        requireView(),
                        getString(
                            if (isEditMode) {
                                R.string.message_chore_updated
                            } else {
                                R.string.message_chore_created
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

        loadChildren(childInput)
        if (isEditMode) {
            loadChoreForEdit()
        }
    }

    private fun loadChildren(childInput: MaterialAutoCompleteTextView) {
        val session = SessionManager.currentSession ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            FirestoreFeatureRepository.loadAssignableChildren(requireContext(), session)
                .onSuccess { options ->
                    childOptions = options
                    childInput.setAdapter(
                        ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_list_item_1,
                            options.map(FamilyChildOption::displayName)
                        )
                    )
                    val currentName = childInput.text?.toString()?.trim().orEmpty()
                    selectedChildId = resolveUniqueChildId(currentName)
                }
                .onFailure {
                    Snackbar.make(requireView(), SessionManager.errorMessage(it), Snackbar.LENGTH_LONG).show()
                }
        }
    }

    private fun loadChoreForEdit() {
        val session = SessionManager.currentSession ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            FirestoreFeatureRepository.loadParentChoreForm(requireContext(), session, currentChoreId())
                .onSuccess(::bindExistingChore)
                .onFailure {
                    Snackbar.make(requireView(), SessionManager.errorMessage(it), Snackbar.LENGTH_LONG).show()
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
        }
    }

    private fun bindExistingChore(chore: ParentChoreForm) {
        val root = requireView()
        root.findViewById<TextInputEditText>(R.id.edit_text_chore_title).setText(chore.title)
        root.findViewById<TextInputEditText>(R.id.edit_text_chore_description).setText(chore.description)
        root.findViewById<MaterialAutoCompleteTextView>(R.id.auto_complete_chore_child)
            .setText(chore.assigneeName, false)
        root.findViewById<TextInputEditText>(R.id.edit_text_chore_points).setText(chore.points.toString())
        selectedChildId = chore.assigneeMemberId

        if (chore.status != ChoreStatus.OPEN) {
            root.findViewById<android.view.View>(R.id.button_save_chore).isEnabled = false
            Snackbar.make(requireView(), R.string.error_chore_edit_locked, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun resolveSelectedChild(childName: String): FamilyChildOption? {
        val exactMatches = childOptions.filter { it.displayName == childName }
        return when {
            selectedChildId != null && exactMatches.any { it.id == selectedChildId } ->
                childOptions.firstOrNull { it.id == selectedChildId }
            exactMatches.size == 1 -> exactMatches.first()
            else -> null
        }
    }

    private fun resolveUniqueChildId(childName: String): String? {
        val exactMatches = childOptions.filter { it.displayName == childName }
        return exactMatches.singleOrNull()?.id
    }

    private fun setSavingState(isSaving: Boolean, saveButton: android.view.View) {
        this.isSaving = isSaving
        saveButton.isEnabled = !isSaving
    }

    private fun currentChoreId(): String =
        arguments?.getString(ARG_CHORE_ID) ?: error("Missing chore ID for edit mode.")
}
