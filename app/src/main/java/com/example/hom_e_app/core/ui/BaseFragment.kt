package com.example.hom_e_app.core.ui

import android.os.Bundle
import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

abstract class BaseFragment(@LayoutRes layoutResId: Int) : Fragment(layoutResId) {

    protected fun bindNavigation(vararg buttonDestinations: Pair<Int, Int>) {
        buttonDestinations.forEach { (buttonId, destinationId) ->
            view?.findViewById<View>(buttonId)?.setOnClickListener {
                findNavController().navigate(destinationId)
            }
        }
    }

    protected fun bindBack(@IdRes buttonId: Int) {
        view?.findViewById<View>(buttonId)?.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindScreenActions()
    }

    protected open fun bindScreenActions() = Unit
}
