package com.example.hom_e_app.feature.auth

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
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class LoginFragment : BaseFragment(R.layout.fragment_login) {

    override fun bindScreenActions() {
        bindNavigation(R.id.button_join_family to R.id.registerJoinFamilyFragment)

        val emailLayout = requireView().findViewById<TextInputLayout>(R.id.input_layout_login_email)
        val passwordLayout = requireView().findViewById<TextInputLayout>(R.id.input_layout_login_password)
        val emailInput = requireView().findViewById<TextInputEditText>(R.id.input_login_email)
        val passwordInput = requireView().findViewById<TextInputEditText>(R.id.input_login_password)
        val submitButton = requireView().findViewById<MaterialButton>(R.id.button_login_continue)
        val statusView = requireView().findViewById<TextView>(R.id.text_login_status)
        val progressView = requireView().findViewById<LinearProgressIndicator>(R.id.progress_login)
        val configNoteView = requireView().findViewById<TextView>(R.id.text_login_demo_note)

        when (val availability = SessionManager.firebaseAvailability(requireContext())) {
            FirebaseBootstrap.Availability.Available -> {
                configNoteView.text = getString(R.string.login_demo_note)
                submitButton.isEnabled = true
            }

            is FirebaseBootstrap.Availability.Missing -> {
                configNoteView.text = availability.message
                submitButton.isEnabled = false
            }
        }

        submitButton.setOnClickListener {
            emailLayout.error = null
            passwordLayout.error = null
            statusView.isVisible = false

            val errors = AuthFormValidator.validateLogin(
                email = emailInput.text?.toString()?.trim().orEmpty(),
                password = passwordInput.text?.toString().orEmpty(),
            )

            emailLayout.error = errors[LoginField.EMAIL]
            passwordLayout.error = errors[LoginField.PASSWORD]

            if (errors.isNotEmpty()) return@setOnClickListener

            setLoadingState(
                isLoading = true,
                submitButton = submitButton,
                progressView = progressView,
                statusView = statusView
            )

            viewLifecycleOwner.lifecycleScope.launch {
                val loginResult = SessionManager.login(
                    context = requireContext(),
                    email = emailInput.text?.toString()?.trim().orEmpty(),
                    password = passwordInput.text?.toString().orEmpty()
                )
                setLoadingState(
                    isLoading = false,
                    submitButton = submitButton,
                    progressView = progressView,
                    statusView = statusView
                )

                loginResult.onSuccess { session ->
                    SessionNavigator.openHome(findNavController(), session.role)
                }.onFailure { throwable ->
                    statusView.isVisible = true
                    statusView.text = SessionManager.errorMessage(throwable)
                }
            }
        }
    }

    private fun setLoadingState(
        isLoading: Boolean,
        submitButton: MaterialButton,
        progressView: LinearProgressIndicator,
        statusView: TextView,
    ) {
        submitButton.isEnabled = !isLoading
        progressView.isVisible = isLoading
        statusView.isVisible = isLoading
        statusView.text = if (isLoading) getString(R.string.login_status_loading) else ""
    }
}
