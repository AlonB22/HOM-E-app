package com.example.hom_e_app.feature.shared

import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.hom_e_app.R
import com.example.hom_e_app.core.auth.SessionManager
import com.example.hom_e_app.core.auth.SessionNavigator
import com.example.hom_e_app.core.auth.SessionState
import com.example.hom_e_app.core.ui.BaseFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch

class SplashFragment : BaseFragment(R.layout.fragment_splash) {

    override fun bindScreenActions() {
        val ctaButton = requireView().findViewById<MaterialButton>(R.id.button_get_started)
        ctaButton.setOnClickListener { restoreSession() }
        observeSessionState()
        restoreSession()
    }

    private fun observeSessionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                SessionManager.sessionState.collect { state ->
                    bindState(state)
                }
            }
        }
    }

    private fun bindState(state: SessionState) {
        val root = requireView()
        val progress = root.findViewById<LinearProgressIndicator>(R.id.progress_splash)
        val status = root.findViewById<TextView>(R.id.text_splash_status)
        val button = root.findViewById<MaterialButton>(R.id.button_get_started)

        when (state) {
            SessionState.Idle,
            SessionState.Loading -> {
                progress.isVisible = true
                status.isVisible = true
                status.text = getString(R.string.splash_status_checking)
                button.isEnabled = false
                button.text = getString(R.string.action_get_started)
            }

            SessionState.SignedOut -> {
                progress.isVisible = false
                status.isVisible = false
                button.isEnabled = true
                SessionNavigator.openLogin(findNavController())
            }

            is SessionState.Authenticated -> {
                progress.isVisible = false
                status.isVisible = false
                button.isEnabled = true
                SessionNavigator.openHome(findNavController(), state.session.role)
            }

            is SessionState.ConfigurationRequired -> {
                progress.isVisible = false
                status.isVisible = false
                button.isEnabled = true
                SessionNavigator.openLogin(findNavController())
            }

            is SessionState.Error -> {
                progress.isVisible = false
                status.isVisible = true
                status.text = state.message
                button.isEnabled = true
                button.text = getString(R.string.action_try_again)
            }
        }
    }

    private fun restoreSession() {
        viewLifecycleOwner.lifecycleScope.launch {
            SessionManager.restoreSession(requireContext())
        }
    }
}
