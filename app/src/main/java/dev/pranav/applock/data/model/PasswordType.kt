package dev.pranav.applock.data.model

/**
 * Represents the result of a password verification attempt.
 * ADMIN  - The input matches the primary admin password; grants normal access.
 * GUEST  - The input matches the decoy guest password; grants access but also
 *           triggers silent intruder monitoring in the background.
 * INVALID - The input matches neither password; access is denied.
 */
enum class PasswordType {
    ADMIN,
    GUEST,
    INVALID
}
