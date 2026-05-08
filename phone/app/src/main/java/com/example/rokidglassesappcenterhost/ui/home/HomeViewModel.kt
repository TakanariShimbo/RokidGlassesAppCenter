package com.example.rokidglassesappcenterhost.ui.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.example.cxrglobal.auth.AuthResult
import com.example.cxrglobal.auth.AuthorizationHelper
import com.example.rokidglassesappcenterhost.Constants
import com.example.rokidglassesappcenterhost.R
import com.example.rokidglassesappcenterhost.ui.list.AppListActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel : ViewModel() {
    private var appContext: Context? = null

    private val _isRokidAIAppInstalled = MutableStateFlow(false)
    val isRokidAIAppInstalled = _isRokidAIAppInstalled.asStateFlow()

    private val _isAuthorized = MutableStateFlow(false)
    val isAuthorized = _isAuthorized.asStateFlow()

    private val _tokenResult = MutableStateFlow("")
    val tokenResult = _tokenResult.asStateFlow()

    private var token = ""

    fun checkRokidAIAppInstalled(act: Activity) {
        appContext = act.applicationContext
        _isRokidAIAppInstalled.value = AuthorizationHelper.isRequiredRokidAppInstalled(act)
    }

    fun requestAuthorization(act: Activity, requestCode: Int) {
        AuthorizationHelper.requestAuthorization(act, requestCode)
    }

    fun startAppList(context: Context) {
        context.startActivity(Intent(context, AppListActivity::class.java).apply {
            putExtra(Constants.EXTRA_TOKEN, token)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    fun checkAuthorizationResult(resultCode: Int, data: Intent?) {
        val result = AuthorizationHelper.parseAuthorizationResult(resultCode, data)
        Log.d(TAG, "checkAuthorizationResult: ${result.javaClass.name}")
        token = when (result) {
            is AuthResult.AuthSuccess -> {
                _isAuthorized.value = true
                _tokenResult.value = tr(R.string.auth_success)
                result.token
            }
            is AuthResult.AuthFail -> {
                _isAuthorized.value = false
                _tokenResult.value = tr(R.string.auth_failed)
                ""
            }
            is AuthResult.AuthCancel -> {
                _isAuthorized.value = false
                _tokenResult.value = tr(R.string.auth_cancelled)
                ""
            }
        }
    }

    private fun tr(@StringRes resId: Int): String =
        appContext?.getString(resId) ?: ""

    companion object {
        private const val TAG = "HomeViewModel"
    }
}
