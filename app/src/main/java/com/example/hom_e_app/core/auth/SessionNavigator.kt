package com.example.hom_e_app.core.auth

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.navOptions
import com.example.hom_e_app.R

object SessionNavigator {

    private val parentDestinations = setOf(
        R.id.parentHomeFragment,
        R.id.parentChoresFragment,
        R.id.parentRewardsFragment,
        R.id.createEditChoreFragment,
        R.id.approvalsFragment,
        R.id.createEditRewardFragment,
    )

    private val childDestinations = setOf(
        R.id.childHomeFragment,
        R.id.childChoresFragment,
        R.id.childRewardsFragment,
        R.id.choreDetailsFragment,
        R.id.rewardDetailsFragment,
        R.id.profileFragment,
    )

    fun roleForDestination(destinationId: Int): FamilyRole? = when (destinationId) {
        in parentDestinations -> FamilyRole.PARENT
        in childDestinations -> FamilyRole.CHILD
        else -> null
    }

    fun homeDestination(role: FamilyRole): Int = when (role) {
        FamilyRole.PARENT -> R.id.parentHomeFragment
        FamilyRole.CHILD -> R.id.childHomeFragment
    }

    fun openHome(navController: NavController, role: FamilyRole) {
        navController.navigate(
            homeDestination(role),
            null,
            rootResetNavOptions()
        )
    }

    fun openLogin(navController: NavController) {
        navController.navigate(
            R.id.loginFragment,
            null,
            rootResetNavOptions()
        )
    }

    fun rootResetNavOptions(): NavOptions = navOptions {
        popUpTo(R.id.app_nav_graph) {
            inclusive = true
        }
        launchSingleTop = true
    }
}
