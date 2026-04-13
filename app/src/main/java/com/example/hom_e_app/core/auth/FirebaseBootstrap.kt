package com.example.hom_e_app.core.auth

import android.content.Context
import com.example.hom_e_app.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

object FirebaseBootstrap {

    sealed interface Availability {
        data object Available : Availability
        data class Missing(val message: String) : Availability
    }

    fun availability(context: Context): Availability {
        FirebaseApp.getApps(context).firstOrNull()?.let { return Availability.Available }

        runCatching { FirebaseApp.initializeApp(context) }
            .getOrNull()
            ?.let { return Availability.Available }

        val options = runtimeOptions(context)
        return if (options == null) {
            Availability.Missing(context.getString(R.string.firebase_setup_required_message))
        } else {
            Availability.Available
        }
    }

    fun initialize(context: Context): Result<FirebaseApp> = runCatching {
        FirebaseApp.getApps(context).firstOrNull()
            ?: FirebaseApp.initializeApp(context)
            ?: FirebaseApp.initializeApp(context, runtimeOptions(context) ?: error(context.getString(R.string.firebase_setup_required_message)))
            ?: error(context.getString(R.string.firebase_setup_required_message))
    }

    private fun runtimeOptions(context: Context): FirebaseOptions? {
        val appId = context.runtimeConfigString("firebase_runtime_application_id")
        val apiKey = context.runtimeConfigString("firebase_runtime_api_key")
        val projectId = context.runtimeConfigString("firebase_runtime_project_id")

        if (appId.isNullOrBlank() || apiKey.isNullOrBlank() || projectId.isNullOrBlank()) {
            return null
        }

        return FirebaseOptions.Builder()
            .setApplicationId(appId)
            .setApiKey(apiKey)
            .setProjectId(projectId)
            .apply {
                context.runtimeConfigString("firebase_runtime_storage_bucket")?.let(::setStorageBucket)
                context.runtimeConfigString("firebase_runtime_database_url")?.let(::setDatabaseUrl)
                context.runtimeConfigString("firebase_runtime_sender_id")?.let(::setGcmSenderId)
            }
            .build()
    }

    private fun Context.runtimeConfigString(name: String): String? {
        val identifier = resources.getIdentifier(name, "string", packageName)
        if (identifier == 0) return null
        return getString(identifier).trim().takeIf { it.isNotEmpty() }
    }
}
