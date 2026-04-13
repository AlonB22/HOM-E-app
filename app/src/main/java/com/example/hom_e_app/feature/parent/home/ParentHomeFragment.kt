package com.example.hom_e_app.feature.parent.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.hom_e_app.R
import com.example.hom_e_app.core.auth.SessionManager
import com.example.hom_e_app.core.auth.SessionNavigator
import com.example.hom_e_app.core.data.FirestoreFeatureRepository
import com.example.hom_e_app.core.data.ParentHomeSummaryState
import com.example.hom_e_app.core.ui.BaseFragment
import com.example.hom_e_app.feature.parent.chores.CreateEditChoreFragment
import com.example.hom_e_app.feature.parent.rewards.CreateEditRewardFragment
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ParentHomeFragment : BaseFragment(R.layout.fragment_parent_home) {
    override fun bindScreenActions() {
        val navController = findNavController()
        val root = requireView()

        bindSessionSummary()

        root.findViewById<android.view.View>(R.id.button_open_approvals).setOnClickListener {
            navController.navigate(R.id.approvalsFragment)
        }
        root.findViewById<android.view.View>(R.id.button_open_chores).setOnClickListener {
            navController.navigate(R.id.parentChoresFragment)
        }
        root.findViewById<android.view.View>(R.id.button_open_rewards).setOnClickListener {
            navController.navigate(R.id.parentRewardsFragment)
        }
        root.findViewById<android.view.View>(R.id.button_quick_create_chore).setOnClickListener {
            navController.navigate(
                R.id.createEditChoreFragment,
                bundleOf(CreateEditChoreFragment.ARG_FORM_MODE to CreateEditChoreFragment.MODE_CREATE)
            )
        }
        root.findViewById<android.view.View>(R.id.button_quick_create_reward).setOnClickListener {
            navController.navigate(
                R.id.createEditRewardFragment,
                bundleOf(CreateEditRewardFragment.ARG_FORM_MODE to CreateEditRewardFragment.MODE_CREATE)
            )
        }
        root.findViewById<android.view.View>(R.id.button_copy_join_code).setOnClickListener {
            copyJoinCode()
        }
        root.findViewById<android.view.View>(R.id.button_share_join_code).setOnClickListener {
            shareJoinCode()
        }
        root.findViewById<android.view.View>(R.id.button_sign_out_parent).setOnClickListener {
            SessionManager.signOut(requireContext())
            Snackbar.make(root, R.string.message_auth_signed_out, Snackbar.LENGTH_SHORT).show()
            SessionNavigator.openLogin(navController)
        }
    }

    override fun onResume() {
        super.onResume()
        bindSessionSummary()
        loadHomeSummary()
    }

    private fun bindSessionSummary() {
        val session = SessionManager.currentSession
        val root = view ?: return
        val familyName = session?.familyName?.takeIf { it.isNotBlank() }
            ?: getString(R.string.parent_home_family_name_fallback)
        val joinCode = session?.joinCode?.takeIf { it.isNotBlank() }

        root.findViewById<TextView>(R.id.text_parent_family_title).text =
            getString(R.string.parent_home_family_title, familyName)

        root.findViewById<TextView>(R.id.text_parent_family_name).text =
            getString(R.string.parent_home_family_name_label, familyName)
        root.findViewById<TextView>(R.id.text_parent_join_code).text =
            joinCode ?: getString(R.string.parent_home_join_code_missing)
        root.findViewById<TextView>(R.id.text_parent_join_code_helper).text = getString(
            if (joinCode == null) {
                R.string.parent_home_join_code_missing_helper
            } else {
                R.string.parent_home_join_code_helper
            }
        )
        root.findViewById<android.view.View>(R.id.button_copy_join_code).isVisible = joinCode != null
        root.findViewById<android.view.View>(R.id.button_share_join_code).isVisible = joinCode != null
    }

    private fun loadHomeSummary() {
        val session = SessionManager.currentSession ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            FirestoreFeatureRepository.loadParentHomeSummary(requireContext(), session)
                .onSuccess(::bindHomeSummary)
                .onFailure {
                    Snackbar.make(requireView(), SessionManager.errorMessage(it), Snackbar.LENGTH_LONG).show()
                }
        }
    }

    private fun bindHomeSummary(state: ParentHomeSummaryState) {
        val root = requireView()
        root.findViewById<TextView>(R.id.text_parent_overview_body).text = getString(
            R.string.parent_home_overview_live,
            state.activeChoresCount,
            state.pendingApprovalsCount,
            state.activeRewardsCount
        )
        root.findViewById<TextView>(R.id.text_parent_metric_pending_value).text =
            state.pendingApprovalsCount.toString()
        root.findViewById<TextView>(R.id.text_parent_metric_active_chores_value).text =
            state.activeChoresCount.toString()
        root.findViewById<TextView>(R.id.text_parent_metric_rewards_value).text =
            state.activeRewardsCount.toString()

        val focusBody = when {
            state.pendingApprovalsCount > 0 -> getString(
                R.string.parent_home_focus_pending,
                state.submittedChoreCount,
                state.pendingRewardRequestCount
            )

            state.openChoresCount > 0 -> getString(
                R.string.parent_home_focus_open_chores,
                state.openChoresCount,
                state.childCount
            )

            state.activeRewardsCount > 0 -> getString(
                R.string.parent_home_focus_rewards_only,
                state.activeRewardsCount
            )

            else -> getString(R.string.parent_home_focus_empty)
        }
        root.findViewById<TextView>(R.id.text_parent_focus_body).text = focusBody
        root.findViewById<TextView>(R.id.text_parent_focus_chip).apply {
            text = if (state.pendingApprovalsCount > 0) {
                getString(R.string.label_pending_count, state.pendingApprovalsCount)
            } else {
                getString(R.string.label_all_caught_up)
            }
            setBackgroundResource(
                if (state.pendingApprovalsCount > 0) {
                    R.drawable.bg_status_attention
                } else {
                    R.drawable.bg_status_positive
                }
            )
            setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (state.pendingApprovalsCount > 0) {
                        R.color.status_attention_text
                    } else {
                        R.color.status_positive_text
                    }
                )
            )
        }

        root.findViewById<TextView>(R.id.text_parent_family_pulse_body).text =
            if (state.childCount == 0) {
                getString(R.string.parent_home_family_pulse_empty)
            } else {
                getString(
                    R.string.parent_home_family_pulse_live,
                    state.childCount,
                    state.totalChildPoints
                )
            }
    }

    private fun copyJoinCode() {
        val joinCode = SessionManager.currentSession?.joinCode ?: return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.parent_home_join_code_clip_label), joinCode))
        Snackbar.make(requireView(), R.string.message_join_code_copied, Snackbar.LENGTH_SHORT).show()
    }

    private fun shareJoinCode() {
        val session = SessionManager.currentSession ?: return
        val joinCode = session.joinCode ?: return
        val shareText = getString(
            R.string.parent_home_join_code_share_message,
            session.familyName?.takeIf { it.isNotBlank() } ?: getString(R.string.parent_home_family_name_fallback),
            joinCode
        )
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                },
                getString(R.string.parent_home_join_code_share_chooser)
            )
        )
    }
}
