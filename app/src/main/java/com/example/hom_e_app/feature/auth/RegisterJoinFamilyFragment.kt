package com.example.hom_e_app.feature.auth

import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.hom_e_app.R
import com.example.hom_e_app.core.auth.FirebaseBootstrap
import com.example.hom_e_app.core.auth.SessionManager
import com.example.hom_e_app.core.auth.SessionNavigator
import com.example.hom_e_app.core.ui.BaseFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class RegisterJoinFamilyFragment : BaseFragment(R.layout.fragment_register_join_family) {

    private var selectedMode = RegistrationMode.PARENT_CREATE_FAMILY

    override fun bindScreenActions() {
        bindBack(R.id.button_back_to_login)

        val modeHelper = requireView().findViewById<TextView>(R.id.text_register_mode_helper)
        val parentGroup = requireView().findViewById<LinearLayout>(R.id.group_parent_registration)
        val childGroup = requireView().findViewById<LinearLayout>(R.id.group_child_registration)
        val modeToggle = requireView().findViewById<MaterialButtonToggleGroup>(R.id.toggle_register_mode)

        val parentNameLayout = requireView().findViewById<TextInputLayout>(R.id.input_layout_parent_name)
        val familyNameLayout = requireView().findViewById<TextInputLayout>(R.id.input_layout_family_name)
        val childNameLayout = requireView().findViewById<TextInputLayout>(R.id.input_layout_child_name)
        val joinCodeLayout = requireView().findViewById<TextInputLayout>(R.id.input_layout_join_code)
        val emailLayout = requireView().findViewById<TextInputLayout>(R.id.input_layout_register_email)
        val passwordLayout = requireView().findViewById<TextInputLayout>(R.id.input_layout_register_password)

        val parentNameInput = requireView().findViewById<TextInputEditText>(R.id.input_parent_name)
        val familyNameInput = requireView().findViewById<TextInputEditText>(R.id.input_family_name)
        val childNameInput = requireView().findViewById<TextInputEditText>(R.id.input_child_name)
        val joinCodeInput = requireView().findViewById<TextInputEditText>(R.id.input_join_code)
        val emailInput = requireView().findViewById<TextInputEditText>(R.id.input_register_email)
        val passwordInput = requireView().findViewById<TextInputEditText>(R.id.input_register_password)
        val submitButton = requireView().findViewById<MaterialButton>(R.id.button_register_submit)
        val backButton = requireView().findViewById<MaterialButton>(R.id.button_back_to_login)
        val statusView = requireView().findViewById<TextView>(R.id.text_register_status)
        val progressView = requireView().findViewById<LinearProgressIndicator>(R.id.progress_register)
        val configNoteView = requireView().findViewById<TextView>(R.id.text_register_demo_note)

        when (val availability = SessionManager.firebaseAvailability(requireContext())) {
            FirebaseBootstrap.Availability.Available -> {
                configNoteView.text = getString(R.string.register_demo_note)
                submitButton.isEnabled = true
            }

            is FirebaseBootstrap.Availability.Missing -> {
                configNoteView.text = availability.message
                submitButton.isEnabled = false
            }
        }

        joinCodeInput.filters = arrayOf(
            InputFilter.LengthFilter(6),
            InputFilter.AllCaps()
        )

        modeToggle.check(R.id.button_mode_parent)
        updateRegistrationMode(
            mode = RegistrationMode.PARENT_CREATE_FAMILY,
            modeHelper = modeHelper,
            parentGroup = parentGroup,
            childGroup = childGroup,
            joinCodeInput = joinCodeInput
        )

        modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener

            selectedMode = when (checkedId) {
                R.id.button_mode_child -> RegistrationMode.CHILD_JOIN_FAMILY
                else -> RegistrationMode.PARENT_CREATE_FAMILY
            }

            clearErrors(
                parentNameLayout,
                familyNameLayout,
                childNameLayout,
                joinCodeLayout,
                emailLayout,
                passwordLayout
            )

            updateRegistrationMode(
                mode = selectedMode,
                modeHelper = modeHelper,
                parentGroup = parentGroup,
                childGroup = childGroup,
                joinCodeInput = joinCodeInput
            )
        }

        submitButton.setOnClickListener {
            clearErrors(
                parentNameLayout,
                familyNameLayout,
                childNameLayout,
                joinCodeLayout,
                emailLayout,
                passwordLayout
            )
            statusView.isVisible = false

            val errors = AuthFormValidator.validateRegistration(
                mode = selectedMode,
                fullName = parentNameInput.text?.toString()?.trim().orEmpty(),
                familyName = familyNameInput.text?.toString()?.trim().orEmpty(),
                childName = childNameInput.text?.toString()?.trim().orEmpty(),
                email = emailInput.text?.toString()?.trim().orEmpty(),
                password = passwordInput.text?.toString().orEmpty(),
                joinCode = joinCodeInput.text?.toString()?.trim().orEmpty(),
            )

            parentNameLayout.error = errors[RegistrationField.PARENT_NAME]
            familyNameLayout.error = errors[RegistrationField.FAMILY_NAME]
            childNameLayout.error = errors[RegistrationField.CHILD_NAME]
            joinCodeLayout.error = errors[RegistrationField.JOIN_CODE]
            emailLayout.error = errors[RegistrationField.EMAIL]
            passwordLayout.error = errors[RegistrationField.PASSWORD]

            if (errors.isNotEmpty()) return@setOnClickListener

            setLoadingState(
                isLoading = true,
                submitButton = submitButton,
                backButton = backButton,
                progressView = progressView,
                statusView = statusView
            )

            viewLifecycleOwner.lifecycleScope.launch {
                val result = when (selectedMode) {
                    RegistrationMode.PARENT_CREATE_FAMILY -> SessionManager.registerParent(
                        context = requireContext(),
                        parentName = parentNameInput.text?.toString()?.trim().orEmpty(),
                        familyName = familyNameInput.text?.toString()?.trim().orEmpty(),
                        email = emailInput.text?.toString()?.trim().orEmpty(),
                        password = passwordInput.text?.toString().orEmpty()
                    )

                    RegistrationMode.CHILD_JOIN_FAMILY -> SessionManager.joinChild(
                        context = requireContext(),
                        childName = childNameInput.text?.toString()?.trim().orEmpty(),
                        joinCode = joinCodeInput.text?.toString()?.trim().orEmpty(),
                        email = emailInput.text?.toString()?.trim().orEmpty(),
                        password = passwordInput.text?.toString().orEmpty()
                    )
                }

                setLoadingState(
                    isLoading = false,
                    submitButton = submitButton,
                    backButton = backButton,
                    progressView = progressView,
                    statusView = statusView
                )

                result.onSuccess { session ->
                    SessionNavigator.openHome(findNavController(), session.role)
                }.onFailure { throwable ->
                    statusView.isVisible = true
                    statusView.text = SessionManager.errorMessage(throwable)
                }
            }
        }
    }

    private fun updateRegistrationMode(
        mode: RegistrationMode,
        modeHelper: TextView,
        parentGroup: LinearLayout,
        childGroup: LinearLayout,
        joinCodeInput: TextInputEditText,
    ) {
        val isParentMode = mode == RegistrationMode.PARENT_CREATE_FAMILY
        parentGroup.visibility = if (isParentMode) View.VISIBLE else View.GONE
        childGroup.visibility = if (isParentMode) View.GONE else View.VISIBLE
        modeHelper.text = getString(
            if (isParentMode) {
                R.string.register_parent_path_body
            } else {
                R.string.register_child_path_body
            }
        )
        joinCodeInput.inputType = if (isParentMode) {
            InputType.TYPE_CLASS_TEXT
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
    }

    private fun clearErrors(vararg layouts: TextInputLayout) {
        layouts.forEach { it.error = null }
    }

    private fun setLoadingState(
        isLoading: Boolean,
        submitButton: MaterialButton,
        backButton: MaterialButton,
        progressView: LinearProgressIndicator,
        statusView: TextView,
    ) {
        submitButton.isEnabled = !isLoading
        backButton.isEnabled = !isLoading
        progressView.isVisible = isLoading
        statusView.isVisible = isLoading
        statusView.text = if (isLoading) {
            getString(
                if (selectedMode == RegistrationMode.PARENT_CREATE_FAMILY) {
                    R.string.register_status_parent_loading
                } else {
                    R.string.register_status_child_loading
                }
            )
        } else {
            ""
        }
    }
}
