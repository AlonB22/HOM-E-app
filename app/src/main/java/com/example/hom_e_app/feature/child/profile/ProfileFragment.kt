package com.example.hom_e_app.feature.child.profile

import android.widget.TextView
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.example.hom_e_app.R
import com.example.hom_e_app.core.auth.SessionManager
import com.example.hom_e_app.core.auth.SessionNavigator
import com.example.hom_e_app.core.ui.BaseFragment
import com.example.hom_e_app.feature.child.ChildDemoState
import com.google.android.material.snackbar.Snackbar

class ProfileFragment : BaseFragment(R.layout.fragment_profile) {

    override fun bindScreenActions() {
        requireView().findViewById<android.view.View>(R.id.button_sign_out).setOnClickListener {
            SessionManager.signOut(requireContext())
            Snackbar.make(requireView(), R.string.message_auth_signed_out, Snackbar.LENGTH_SHORT).show()
            SessionNavigator.openLogin(findNavController())
        }
    }

    override fun onResume() {
        super.onResume()
        bindProfileSummary()
    }

    private fun bindProfileSummary() {
        val summary = ChildDemoState.profileSummary()
        val session = SessionManager.currentSession
        val root = requireView()
        root.findViewById<TextView>(R.id.text_profile_name).text =
            session?.displayName ?: getString(summary.childNameRes)
        root.findViewById<TextView>(R.id.text_profile_family).text =
            getString(
                R.string.child_profile_family_label,
                session?.familyName ?: getString(summary.familyNameRes)
            )
        root.findViewById<TextView>(R.id.text_profile_email).apply {
            isVisible = !session?.email.isNullOrBlank()
            text = getString(R.string.child_profile_email_label, session?.email.orEmpty())
        }
        root.findViewById<TextView>(R.id.text_profile_points).text =
            getString(R.string.child_profile_points_label, summary.points)
        root.findViewById<TextView>(R.id.text_profile_progress).text = getString(
            R.string.child_profile_progress_label,
            summary.waitingApprovals,
            summary.requestedRewards
        )
    }
}
