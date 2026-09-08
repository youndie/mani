package io.github.youndie.mani.feature.user

import io.github.youndie.mani.feature.auth.LoginParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Границы проверяются по одной: правило, отвергающее всё сразу, выглядит рабочим и на верном
 * вводе тоже.
 */
class CredentialsTest {
    @Test
    fun ordinaryCredentialsPass() {
        assertNull(credentialsProblem(LoginParams("vasya", "hunter22")))
        assertNull(credentialsProblem(LoginParams("a-b_c9", "12345678")))
    }

    @Test
    fun anEmptyNameAndPasswordAreRefused() {
        // Ровно то, что принималось раньше: пустое имя с пустым паролем заводило пользователя.
        assertNotNull(credentialsProblem(LoginParams("", "")))
    }

    @Test
    fun aNameOutsideTheLengthIsRefused() {
        assertNotNull(credentialsProblem(LoginParams("ab", "hunter22")))
        assertNull(credentialsProblem(LoginParams("abc", "hunter22")))
        assertNull(credentialsProblem(LoginParams("a".repeat(32), "hunter22")))
        assertNotNull(credentialsProblem(LoginParams("a".repeat(33), "hunter22")))
    }

    @Test
    fun aNameOutsideTheAlphabetIsRefused() {
        assertNotNull(credentialsProblem(LoginParams("Vasya", "hunter22")))
        assertNotNull(credentialsProblem(LoginParams("va sya", "hunter22")))
        assertNotNull(credentialsProblem(LoginParams("вася", "hunter22")))
    }

    /** Префикс песочницы занимать нельзя: по нему уборщик отличает её от живого аккаунта. */
    @Test
    fun theDemoPrefixIsReserved() {
        assertNotNull(credentialsProblem(LoginParams("demo-1a2b3c4d", "hunter22")))
        assertNull(credentialsProblem(LoginParams("demonstration", "hunter22")))
    }

    @Test
    fun aShortPasswordIsRefused() {
        assertNotNull(credentialsProblem(LoginParams("vasya", "hunter2")))
        assertNull(credentialsProblem(LoginParams("vasya", "hunter22")))
    }

    /** Текст уходит человеку в форму, поэтому он про пароль, а не про «поле 2». */
    @Test
    fun theProblemNamesWhatToFix() {
        assertEquals(
            "Password must be at least 8 characters long",
            credentialsProblem(LoginParams("vasya", "short")),
        )
    }
}
