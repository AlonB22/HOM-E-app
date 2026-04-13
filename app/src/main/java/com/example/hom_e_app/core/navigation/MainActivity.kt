package com.example.hom_e_app.core.navigation

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.hom_e_app.R
import com.example.hom_e_app.core.auth.FamilyRole
import com.example.hom_e_app.core.auth.SessionManager
import com.example.hom_e_app.core.auth.SessionNavigator
import com.example.hom_e_app.core.auth.SessionState
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var navController: NavController

    private val toolbarHiddenDestinations = setOf(
        R.id.splashFragment,
        R.id.loginFragment
    )

    private val parentTopLevelDestinations = setOf(
        R.id.parentHomeFragment,
        R.id.parentChoresFragment,
        R.id.parentRewardsFragment
    )

    private val childTopLevelDestinations = setOf(
        R.id.childHomeFragment,
        R.id.childChoresFragment,
        R.id.childRewardsFragment
    )

    private val parentSecondaryBackTitleDestinations = setOf(
        R.id.approvalsFragment,
        R.id.createEditChoreFragment,
        R.id.createEditRewardFragment
    )

    private var currentBottomMenuRes: Int? = null
    private var toolbarBackTitleView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.top_app_bar)
        bottomNavigation = findViewById(R.id.bottom_navigation)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        toolbar.setNavigationOnClickListener { navController.navigateUp() }
        bottomNavigation.setOnItemSelectedListener { item ->
            if (item.itemId != navController.currentDestination?.id) {
                navController.navigate(item.itemId)
            }
            true
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (enforceRoleGate(destination.id)) {
                return@addOnDestinationChangedListener
            }

            toolbar.isVisible = destination.id !in toolbarHiddenDestinations
            if (toolbar.isVisible) {
                toolbar.title = destination.label ?: getString(R.string.app_name)
            }

            when {
                destination.id in parentTopLevelDestinations -> {
                    clearToolbarCustomView()
                    toolbar.navigationIcon = null
                    showBottomMenu(R.menu.menu_parent_bottom_nav, destination.id)
                }

                destination.id in childTopLevelDestinations -> {
                    clearToolbarCustomView()
                    toolbar.navigationIcon = null
                    showBottomMenu(R.menu.menu_child_bottom_nav, destination.id)
                }

                destination.id == R.id.splashFragment || destination.id == R.id.loginFragment -> {
                    clearToolbarCustomView()
                    toolbar.navigationIcon = null
                    bottomNavigation.isVisible = false
                }

                destination.id in parentSecondaryBackTitleDestinations -> {
                    showBackToolbarTitle(destination.label)
                    toolbar.navigationIcon = null
                    bottomNavigation.isVisible = false
                }

                else -> {
                    clearToolbarCustomView()
                    toolbar.navigationIcon = AppCompatResources.getDrawable(
                        this,
                        androidx.appcompat.R.drawable.abc_ic_ab_back_material
                    )
                    bottomNavigation.isVisible = false
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                SessionManager.sessionState.collect {
                    navController.currentDestination?.id?.let(::enforceRoleGate)
                }
            }
        }
    }

    private fun showBottomMenu(menuRes: Int, selectedDestinationId: Int) {
        if (currentBottomMenuRes != menuRes) {
            bottomNavigation.menu.clear()
            bottomNavigation.inflateMenu(menuRes)
            currentBottomMenuRes = menuRes
        }

        bottomNavigation.isVisible = true
        bottomNavigation.menu.findItem(selectedDestinationId)?.isChecked = true
    }

    private fun showBackToolbarTitle(title: CharSequence?) {
        toolbar.title = ""
        val backTitleView = toolbarBackTitleView ?: LayoutInflater.from(this)
            .inflate(R.layout.view_toolbar_back_title, toolbar, false)
            .also { view ->
                view.findViewById<ImageView>(R.id.image_toolbar_back).apply {
                    setImageDrawable(
                        AppCompatResources.getDrawable(
                            this@MainActivity,
                            androidx.appcompat.R.drawable.abc_ic_ab_back_material
                        )
                    )
                    setOnClickListener { navController.navigateUp() }
                }
                view.findViewById<TextView>(R.id.text_toolbar_back_title).setOnClickListener {
                    navController.navigateUp()
                }
                toolbarBackTitleView = view
            }

        backTitleView.findViewById<TextView>(R.id.text_toolbar_back_title).text =
            title ?: getString(R.string.app_name)

        if (backTitleView.parent == null) {
            val layoutParams = Toolbar.LayoutParams(
                Toolbar.LayoutParams.WRAP_CONTENT,
                Toolbar.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.START or Gravity.BOTTOM
            }
            toolbar.addView(backTitleView, layoutParams)
        }
    }

    private fun clearToolbarCustomView() {
        toolbarBackTitleView?.let { customView ->
            if (customView.parent === toolbar) {
                toolbar.removeView(customView)
            }
        }
    }

    private fun enforceRoleGate(destinationId: Int): Boolean {
        val requiredRole = SessionNavigator.roleForDestination(destinationId) ?: return false
        val state = SessionManager.sessionState.value

        return when (state) {
            is SessionState.Authenticated -> {
                if (state.session.role == requiredRole) {
                    false
                } else {
                    showRoleMessage(requiredRole)
                    navController.navigate(
                        SessionNavigator.homeDestination(state.session.role),
                        null,
                        SessionNavigator.rootResetNavOptions()
                    )
                    true
                }
            }

            SessionState.SignedOut,
            SessionState.Idle,
            SessionState.Loading,
            is SessionState.ConfigurationRequired,
            is SessionState.Error -> {
                navController.navigate(R.id.loginFragment, null, SessionNavigator.rootResetNavOptions())
                true
            }
        }
    }

    private fun showRoleMessage(requiredRole: FamilyRole) {
        val messageRes = if (requiredRole == FamilyRole.PARENT) {
            R.string.message_role_gate_parent_only
        } else {
            R.string.message_role_gate_child_only
        }
        Snackbar.make(findViewById(android.R.id.content), messageRes, Snackbar.LENGTH_SHORT).show()
    }
}
