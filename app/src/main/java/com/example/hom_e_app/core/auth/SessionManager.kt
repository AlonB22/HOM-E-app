package com.example.hom_e_app.core.auth

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.Locale

object SessionManager {

    private const val USERS_COLLECTION = "users"
    private const val FAMILIES_COLLECTION = "families"
    private const val FAMILY_MEMBERS_COLLECTION = "familyMembers"

    private val joinCodeAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val secureRandom = SecureRandom()

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    val currentSession: FamilySession?
        get() = (sessionState.value as? SessionState.Authenticated)?.session

    fun errorMessage(throwable: Throwable): String = throwable.toUserMessage()

    fun firebaseAvailability(context: Context): FirebaseBootstrap.Availability =
        FirebaseBootstrap.availability(context)

    suspend fun restoreSession(context: Context): Result<FamilySession?> {
        _sessionState.value = SessionState.Loading
        val services = firebaseServices(context).getOrElse {
            val message = it.toUserMessage()
            _sessionState.value = SessionState.ConfigurationRequired(message)
            return Result.success(null)
        }

        val currentUser = services.auth.currentUser
        if (currentUser == null) {
            _sessionState.value = SessionState.SignedOut
            return Result.success(null)
        }

        return resolveMembership(
            firestore = services.firestore,
            user = currentUser,
            onFailure = {
                services.auth.signOut()
                _sessionState.value = SessionState.Error(it.toUserMessage())
            }
        ).map { session ->
            _sessionState.value = SessionState.Authenticated(session)
            session
        }
    }

    suspend fun login(
        context: Context,
        email: String,
        password: String,
    ): Result<FamilySession> {
        _sessionState.value = SessionState.Loading
        val services = firebaseServices(context).getOrElse {
            _sessionState.value = SessionState.Error(it.toUserMessage())
            return Result.failure(it)
        }

        return runCatching {
            services.auth.signInWithEmailAndPassword(email, password).await().user
                ?: error("Login completed without a Firebase user.")
        }.fold(
            onSuccess = { user ->
                resolveMembership(
                    firestore = services.firestore,
                    user = user,
                    onFailure = {
                        services.auth.signOut()
                        _sessionState.value = SessionState.Error(it.toUserMessage())
                    }
                ).onSuccess { session ->
                    _sessionState.value = SessionState.Authenticated(session)
                }
            },
            onFailure = {
                _sessionState.value = SessionState.SignedOut
                Result.failure(it)
            }
        )
    }

    suspend fun registerParent(
        context: Context,
        parentName: String,
        familyName: String,
        email: String,
        password: String,
    ): Result<FamilySession> {
        _sessionState.value = SessionState.Loading
        val services = firebaseServices(context).getOrElse {
            _sessionState.value = SessionState.Error(it.toUserMessage())
            return Result.failure(it)
        }

        return runCatching {
            services.auth.createUserWithEmailAndPassword(email, password).await().user
                ?: error("Registration completed without a Firebase user.")
        }.fold(
            onSuccess = { user ->
                val familyId = services.firestore.collection(FAMILIES_COLLECTION).document().id
                val memberId = services.firestore.collection(FAMILY_MEMBERS_COLLECTION).document().id
                val joinCode = generateJoinCode()

                val writeResult = runCatching {
                    services.firestore.runBatch { batch ->
                        batch.set(
                            services.firestore.collection(USERS_COLLECTION).document(user.uid),
                            mapOf(
                                "email" to email,
                                "displayName" to parentName,
                                "role" to FamilyRole.PARENT.name.lowercase(Locale.US),
                                "familyId" to familyId,
                                "memberId" to memberId,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp(),
                            )
                        )
                        batch.set(
                            services.firestore.collection(FAMILIES_COLLECTION).document(familyId),
                            mapOf(
                                "name" to familyName,
                                "joinCode" to joinCode,
                                "createdByUid" to user.uid,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp(),
                            )
                        )
                        batch.set(
                            services.firestore.collection(FAMILY_MEMBERS_COLLECTION).document(memberId),
                            mapOf(
                                "userId" to user.uid,
                                "familyId" to familyId,
                                "role" to FamilyRole.PARENT.name.lowercase(Locale.US),
                                "displayName" to parentName,
                                "email" to email,
                                "pointsBalance" to 0,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp(),
                            )
                        )
                    }.await()
                }

                if (writeResult.isFailure) {
                    cleanupFailedRegistration(services.auth, user)
                    _sessionState.value = SessionState.SignedOut
                    Result.failure(writeResult.exceptionOrNull()!!)
                } else {
                    val membershipResult = resolveMembership(
                        firestore = services.firestore,
                        user = user,
                        onFailure = {
                            _sessionState.value = SessionState.Error(it.toUserMessage())
                        }
                    )
                    if (membershipResult.isFailure) {
                        cleanupFailedRegistration(services.auth, user)
                    }
                    membershipResult.onSuccess { session ->
                        _sessionState.value = SessionState.Authenticated(session)
                    }
                }
            },
            onFailure = {
                _sessionState.value = SessionState.SignedOut
                Result.failure(it)
            }
        )
    }

    suspend fun joinChild(
        context: Context,
        childName: String,
        joinCode: String,
        email: String,
        password: String,
    ): Result<FamilySession> {
        _sessionState.value = SessionState.Loading
        val services = firebaseServices(context).getOrElse {
            _sessionState.value = SessionState.Error(it.toUserMessage())
            return Result.failure(it)
        }

        return runCatching {
            services.auth.createUserWithEmailAndPassword(email, password).await().user
                ?: error("Registration completed without a Firebase user.")
        }.fold(
            onSuccess = { user ->
                val familyResult = resolveFamilyByJoinCode(services.firestore, joinCode)
                if (familyResult.isFailure) {
                    cleanupFailedRegistration(services.auth, user)
                    _sessionState.value = SessionState.SignedOut
                    return@fold Result.failure(familyResult.exceptionOrNull()!!)
                }
                val familySnapshot = familyResult.getOrThrow()
                val familyId = familySnapshot.id
                val memberId = services.firestore.collection(FAMILY_MEMBERS_COLLECTION).document().id
                val normalizedJoinCode = joinCode.uppercase(Locale.US)

                val writeResult = runCatching {
                    services.firestore.runBatch { batch ->
                        batch.set(
                            services.firestore.collection(USERS_COLLECTION).document(user.uid),
                            mapOf(
                                "email" to email,
                                "displayName" to childName,
                                "role" to FamilyRole.CHILD.name.lowercase(Locale.US),
                                "familyId" to familyId,
                                "memberId" to memberId,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp(),
                            )
                        )
                        batch.set(
                            services.firestore.collection(FAMILY_MEMBERS_COLLECTION).document(memberId),
                            mapOf(
                                "userId" to user.uid,
                                "familyId" to familyId,
                                "role" to FamilyRole.CHILD.name.lowercase(Locale.US),
                                "displayName" to childName,
                                "email" to email,
                                "pointsBalance" to 0,
                                "joinCodeUsed" to normalizedJoinCode,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp(),
                            )
                        )
                    }.await()
                }

                if (writeResult.isFailure) {
                    cleanupFailedRegistration(services.auth, user)
                    _sessionState.value = SessionState.SignedOut
                    Result.failure(writeResult.exceptionOrNull()!!)
                } else {
                    val membershipResult = resolveMembership(
                        firestore = services.firestore,
                        user = user,
                        onFailure = {
                            _sessionState.value = SessionState.Error(it.toUserMessage())
                        }
                    )
                    if (membershipResult.isFailure) {
                        cleanupFailedRegistration(services.auth, user)
                    }
                    membershipResult.onSuccess { session ->
                        _sessionState.value = SessionState.Authenticated(session)
                    }
                }
            },
            onFailure = {
                _sessionState.value = SessionState.SignedOut
                Result.failure(it)
            }
        )
    }

    fun signOut(context: Context) {
        firebaseServices(context).getOrNull()?.auth?.signOut()
        _sessionState.value = SessionState.SignedOut
    }

    private suspend fun resolveMembership(
        firestore: FirebaseFirestore,
        user: FirebaseUser,
        onFailure: (Throwable) -> Unit,
    ): Result<FamilySession> {
        return runCatching {
            val userDocument = firestore.collection(USERS_COLLECTION)
                .document(user.uid)
                .get()
                .await()

            if (!userDocument.exists()) {
                error("No profile exists for this account.")
            }

            val memberId = userDocument.getString("memberId")
                ?.takeIf { it.isNotBlank() }
                ?: error("This account is missing its family membership link.")
            val familyId = userDocument.getString("familyId")
                ?.takeIf { it.isNotBlank() }
                ?: error("This account is missing its family link.")

            val membershipDocument = firestore.collection(FAMILY_MEMBERS_COLLECTION)
                .document(memberId)
                .get()
                .await()

            if (!membershipDocument.exists()) {
                error("No family membership exists for this account.")
            }

            if (membershipDocument.getString("userId") != user.uid) {
                error("This account is linked to the wrong membership record.")
            }

            if (membershipDocument.getString("familyId") != familyId) {
                error("This account has inconsistent family data.")
            }

            val role = membershipDocument.getString("role").toFamilyRole()
            val displayName = membershipDocument.getString("displayName")
                ?.takeIf { it.isNotBlank() }
                ?: user.displayName
                ?: user.email
                ?: "Family member"
            val familyDocument = firestore.collection(FAMILIES_COLLECTION)
                .document(familyId)
                .get()
                .await()

            FamilySession(
                uid = user.uid,
                memberId = memberId,
                familyId = familyId,
                role = role,
                displayName = displayName,
                familyName = familyDocument.getString("name"),
                joinCode = familyDocument.getString("joinCode"),
                email = user.email.orEmpty(),
            )
        }.onFailure(onFailure)
    }

    private suspend fun resolveFamilyByJoinCode(
        firestore: FirebaseFirestore,
        joinCode: String,
    ) = runCatching {
        val normalizedJoinCode = joinCode.uppercase(Locale.US)
        val familyQuery = firestore.collection(FAMILIES_COLLECTION)
            .whereEqualTo("joinCode", normalizedJoinCode)
            .limit(2)
            .get()
            .await()

        when {
            familyQuery.isEmpty -> error("Join code not found.")
            familyQuery.documents.size > 1 -> error("Join code matches multiple families. Fix the duplicate code before continuing.")
            else -> familyQuery.documents.first()
        }
    }

    private fun firebaseServices(context: Context): Result<FirebaseServices> = runCatching {
        val app = FirebaseBootstrap.initialize(context).getOrThrow()
        FirebaseServices(
            auth = FirebaseAuth.getInstance(app),
            firestore = FirebaseFirestore.getInstance(app),
        )
    }

    private suspend fun cleanupFailedRegistration(auth: FirebaseAuth, user: FirebaseUser) {
        runCatching { user.delete().await() }
        auth.signOut()
    }

    private fun generateJoinCode(length: Int = 6): String = buildString(length) {
        repeat(length) {
            append(joinCodeAlphabet[secureRandom.nextInt(joinCodeAlphabet.length)])
        }
    }

    private fun String?.toFamilyRole(): FamilyRole = when (this?.lowercase(Locale.US)) {
        "parent" -> FamilyRole.PARENT
        "child" -> FamilyRole.CHILD
        else -> error("Unsupported family role: $this")
    }

    private fun Throwable.toUserMessage(): String = when (this) {
        is FirebaseAuthException -> when (errorCode) {
            "ERROR_INVALID_EMAIL" -> "Enter a valid email address."
            "ERROR_WRONG_PASSWORD" -> "Incorrect password."
            "ERROR_USER_NOT_FOUND" -> "No account matches that email yet."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "An account with that email already exists."
            "ERROR_WEAK_PASSWORD" -> "Password must be at least 6 characters."
            "ERROR_NETWORK_REQUEST_FAILED" -> "Network error. Check the connection and try again."
            else -> localizedMessage ?: "Authentication failed."
        }

        else -> localizedMessage ?: "Something went wrong. Try again."
    }

    private data class FirebaseServices(
        val auth: FirebaseAuth,
        val firestore: FirebaseFirestore,
    )
}
