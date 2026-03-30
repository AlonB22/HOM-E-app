package com.example.hom_e_app.feature.auth

import android.util.Patterns

object AuthFormValidator {

    fun validateLogin(
        email: String,
        password: String,
    ): Map<LoginField, String> {
        val errors = linkedMapOf<LoginField, String>()

        if (email.isBlank()) {
            errors[LoginField.EMAIL] = "Email is required."
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errors[LoginField.EMAIL] = "Enter a valid email address."
        }

        if (password.isBlank()) {
            errors[LoginField.PASSWORD] = "Password is required."
        } else if (password.length < 6) {
            errors[LoginField.PASSWORD] = "Password must be at least 6 characters."
        }

        return errors
    }

    fun validateRegistration(
        mode: RegistrationMode,
        fullName: String,
        familyName: String,
        childName: String,
        email: String,
        password: String,
        joinCode: String,
    ): Map<RegistrationField, String> {
        val errors = linkedMapOf<RegistrationField, String>()

        when (mode) {
            RegistrationMode.PARENT_CREATE_FAMILY -> {
                if (fullName.length < 2) {
                    errors[RegistrationField.PARENT_NAME] = "Enter the parent's full name."
                }
                if (familyName.length < 3) {
                    errors[RegistrationField.FAMILY_NAME] = "Family name must be at least 3 characters."
                }
            }

            RegistrationMode.CHILD_JOIN_FAMILY -> {
                if (childName.length < 2) {
                    errors[RegistrationField.CHILD_NAME] = "Enter the child's display name."
                }
                if (joinCode.length != 6 || joinCode.any { !it.isLetterOrDigit() }) {
                    errors[RegistrationField.JOIN_CODE] = "Join code must be 6 letters or numbers."
                }
            }
        }

        if (email.isBlank()) {
            errors[RegistrationField.EMAIL] = "Email is required."
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errors[RegistrationField.EMAIL] = "Enter a valid email address."
        }

        if (password.isBlank()) {
            errors[RegistrationField.PASSWORD] = "Password is required."
        } else if (password.length < 6) {
            errors[RegistrationField.PASSWORD] = "Password must be at least 6 characters."
        }

        return errors
    }
}

enum class LoginField {
    EMAIL,
    PASSWORD
}

enum class RegistrationField {
    PARENT_NAME,
    FAMILY_NAME,
    CHILD_NAME,
    EMAIL,
    PASSWORD,
    JOIN_CODE
}
