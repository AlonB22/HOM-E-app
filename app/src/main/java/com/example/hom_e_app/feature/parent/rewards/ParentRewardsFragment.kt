package com.example.hom_e_app.feature.parent.rewards

import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import com.example.hom_e_app.R
import com.example.hom_e_app.core.ui.BaseFragment

class ParentRewardsFragment : BaseFragment(R.layout.fragment_parent_rewards) {

    private data class DemoReward(
        val title: String,
        val description: String,
        val cost: Int,
        val isActive: Boolean
    )

    override fun bindScreenActions() {
        val navController = findNavController()
        view?.findViewById<android.view.View>(R.id.button_create_reward)?.setOnClickListener {
            navController.navigate(
                R.id.createEditRewardFragment,
                bundleOf(CreateEditRewardFragment.ARG_FORM_MODE to CreateEditRewardFragment.MODE_CREATE)
            )
        }

        bindEditButton(
            buttonId = R.id.button_edit_reward_movie,
            reward = DemoReward(
                title = getString(R.string.demo_reward_movie_title),
                description = getString(R.string.demo_reward_movie_body),
                cost = 45,
                isActive = true
            )
        )
        bindEditButton(
            buttonId = R.id.button_edit_reward_late,
            reward = DemoReward(
                title = getString(R.string.demo_reward_late_title),
                description = getString(R.string.demo_reward_late_body),
                cost = 60,
                isActive = false
            )
        )
        bindEditButton(
            buttonId = R.id.button_edit_reward_baking,
            reward = DemoReward(
                title = getString(R.string.demo_reward_baking_title),
                description = getString(R.string.demo_reward_baking_body),
                cost = 55,
                isActive = true
            )
        )
    }

    private fun bindEditButton(buttonId: Int, reward: DemoReward) {
        view?.findViewById<android.view.View>(buttonId)?.setOnClickListener {
            findNavController().navigate(
                R.id.createEditRewardFragment,
                bundleOf(
                    CreateEditRewardFragment.ARG_FORM_MODE to CreateEditRewardFragment.MODE_EDIT,
                    CreateEditRewardFragment.ARG_TITLE to reward.title,
                    CreateEditRewardFragment.ARG_DESCRIPTION to reward.description,
                    CreateEditRewardFragment.ARG_POINTS to reward.cost,
                    CreateEditRewardFragment.ARG_IS_ACTIVE to reward.isActive
                )
            )
        }
    }
}
