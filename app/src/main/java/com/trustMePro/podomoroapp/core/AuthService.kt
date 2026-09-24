package com.trustMePro.podomoroapp.core

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

sealed class AuthResult {
    data object Success : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class AuthService(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { fAuth ->
            trySend(fAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithEmail(email: String, pass: String): AuthResult {
        if (email.isBlank() || pass.isBlank()) {
            return AuthResult.Error("Vui lòng điền đầy đủ email và mật khẩu.")
        }
        return try {
            auth.signInWithEmailAndPassword(email.trim(), pass).await()
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(friendlyErrorMessage(e))
        }
    }

    suspend fun registerWithEmail(email: String, pass: String): AuthResult {
        if (email.isBlank() || pass.isBlank()) {
            return AuthResult.Error("Vui lòng điền đầy đủ email và mật khẩu.")
        }
        if (pass.length < 6) {
            return AuthResult.Error("Mật khẩu phải có ít nhất 6 ký tự.")
        }
        return try {
            auth.createUserWithEmailAndPassword(email.trim(), pass).await()
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(friendlyErrorMessage(e))
        }
    }

    suspend fun sendPasswordResetEmail(email: String): AuthResult {
        if (email.isBlank()) {
            return AuthResult.Error("Vui lòng nhập địa chỉ email để nhận liên kết đặt lại mật khẩu.")
        }
        return try {
            auth.sendPasswordResetEmail(email.trim()).await()
            AuthResult.Success
        } catch (e: Exception) {
            AuthResult.Error(friendlyErrorMessage(e))
        }
    }

    fun signOut() {
        auth.signOut()
    }

    private fun friendlyErrorMessage(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("The email address is badly formatted", ignoreCase = true) ->
                "Địa chỉ email không đúng định dạng."
            msg.contains("The email address is already in use", ignoreCase = true) ->
                "Email này đã được đăng ký trước đó. Vui lòng đăng nhập."
            msg.contains("There is no user record", ignoreCase = true) ||
            msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
            msg.contains("invalid credential", ignoreCase = true) ||
            msg.contains("wrong password", ignoreCase = true) ->
                "Email hoặc mật khẩu không chính xác."
            msg.contains("network error", ignoreCase = true) ->
                "Lỗi kết nối mạng. Vui lòng kiểm tra kết nối Internet."
            msg.contains("too many requests", ignoreCase = true) ->
                "Đăng nhập thất bại quá nhiều lần. Vui lòng thử lại sau ít phút."
            else -> e.localizedMessage ?: "Đã xảy ra lỗi xác thực."
        }
    }
}
