package com.example.hom_e_app.core.auth

enum class FamilyRole {
    PARENT,
    CHILD
}

data class FamilySession(
    val uid: String,
    val memberId: String,
    val familyId: String,
    val role: FamilyRole,
    val displayName: String,
    val familyName: String?,
    val joinCode: String?,
    val email: String,
)

sealed interface SessionState {
    data object Idle : SessionState
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class Authenticated(val session: FamilySession) : SessionState
    data class ConfigurationRequired(val message: String) : SessionState
    data class Error(val message: String) : SessionState
}
