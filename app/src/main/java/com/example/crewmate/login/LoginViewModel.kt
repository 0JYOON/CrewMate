package com.example.crewmate.login

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.crewmate.data.remote.xcrew.XcrewHttpException
import com.example.crewmate.data.remote.xcrew.XcrewLoginFailureException
import com.example.crewmate.data.remote.xcrew.XcrewLoginNetworkException
import com.example.crewmate.data.remote.xcrew.XcrewLoginPageException
import com.example.crewmate.data.remote.xcrew.XcrewLoginRepository
import com.example.crewmate.data.remote.xcrew.XcrewLoginSessionException
import com.example.crewmate.data.remote.xcrew.XcrewLoginUnexpectedResponseException
import com.example.crewmate.data.remote.xcrew.XcrewNetworkException
import com.example.crewmate.data.remote.xcrew.XcrewResponseException
import com.example.crewmate.data.remote.xcrew.XcrewSessionExpiredException
import com.example.crewmate.data.remote.xcrew.XcrewWebViewSession
import com.example.crewmate.data.remote.xcrew.XcrewWebViewXhrDataSource
import com.example.crewmate.data.remote.xcrew.XcrewWorkScheduleParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private const val LOGIN_TAG = "XcrewLogin"

class LoginViewModel(
    private val loginRepository: XcrewLoginRepository,
    private val xcrewSession: XcrewWebViewSession,
    private val credentialStore: SecureCredentialStore,
    autoLoginOnStart: Boolean = true,
) : ViewModel() {
    private var autoLoginJob: Job? = null
    private val _uiState = MutableStateFlow(
        LoginUiState(
            isLoading = autoLoginOnStart,
            isAutoLogin = autoLoginOnStart,
            isCheckingSavedCredentials = true,
        ),
    )
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        if (autoLoginOnStart) {
            loadSavedCredentialsAndAutoLogin()
        } else {
            loadSavedCredentialsWithoutAutoLogin()
        }
    }

    fun login(employeeNumber: String, password: String) {
        val currentState = _uiState.value
        if (currentState.isLoading) return
        val normalizedEmployeeNumber = employeeNumber.trim()
        if (normalizedEmployeeNumber.isBlank() || password.isEmpty()) {
            _uiState.value = currentState.copy(errorMessage = "사번과 비밀번호를 입력해 주세요.")
            return
        }

        val rememberLogin = currentState.rememberLogin
        _uiState.value = currentState.copy(
            isLoading = true,
            isAutoLogin = false,
            isCheckingSavedCredentials = false,
            employeeNumber = normalizedEmployeeNumber,
            password = password,
            errorMessage = null,
            canRetryAutoLogin = false,
        )
        viewModelScope.launch {
            attemptLogin(normalizedEmployeeNumber, password, rememberLogin, isAutoLogin = false)
        }
    }

    fun setRememberLogin(remember: Boolean) {
        _uiState.value = _uiState.value.copy(rememberLogin = remember)
    }

    fun setEmployeeNumber(employeeNumber: String) {
        _uiState.value = _uiState.value.copy(employeeNumber = employeeNumber)
    }

    fun setPassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    fun deleteSavedLogin() {
        viewModelScope.launch {
            runCatching { credentialStore.clear() }
            _uiState.value = _uiState.value.copy(
                employeeNumber = "",
                password = "",
                rememberLogin = false,
                canRetryAutoLogin = false,
                errorMessage = "저장된 로그인 정보가 삭제되었습니다.",
            )
        }
    }

    fun retryAutoLogin() {
        if (_uiState.value.isLoading || autoLoginJob?.isActive == true) return
        autoLoginJob = viewModelScope.launch {
            when (val result = readCredentials()) {
                CredentialReadResult.Empty -> _uiState.value = LoginUiState(
                    errorMessage = "저장된 로그인 정보가 없습니다. 다시 로그인해 주세요.",
                )
                CredentialReadResult.Unreadable -> _uiState.value = LoginUiState(
                    errorMessage = "저장된 로그인 정보를 읽을 수 없습니다. 다시 로그인해 주세요.",
                )
                is CredentialReadResult.Available -> {
                    _uiState.value = LoginUiState(
                        isLoading = true,
                        isAutoLogin = true,
                        rememberLogin = true,
                        employeeNumber = result.credentials.employeeNumber,
                        password = result.credentials.password,
                    )
                    Log.i(LOGIN_TAG, "AUTO_LOGIN_START")
                    attemptLogin(
                        result.credentials.employeeNumber,
                        result.credentials.password,
                        rememberLogin = true,
                        isAutoLogin = true,
                    )
                }
            }
        }
    }

    fun switchToManualLogin() {
        autoLoginJob?.cancel()
        val current = _uiState.value
        _uiState.value = LoginUiState(
            employeeNumber = current.employeeNumber,
            password = current.password,
            rememberLogin = current.rememberLogin,
        )
    }

    private fun loadSavedCredentialsWithoutAutoLogin() {
        viewModelScope.launch {
            when (val result = readCredentials()) {
                CredentialReadResult.Empty -> _uiState.value = LoginUiState()
                CredentialReadResult.Unreadable -> _uiState.value = LoginUiState(
                    errorMessage = "저장된 로그인 정보를 읽을 수 없습니다. 다시 로그인해 주세요.",
                )
                is CredentialReadResult.Available -> {
                    _uiState.value = LoginUiState(
                        rememberLogin = true,
                        employeeNumber = result.credentials.employeeNumber,
                        password = result.credentials.password,
                    )
                }
            }
        }
    }

    private fun loadSavedCredentialsAndAutoLogin() {
        autoLoginJob = viewModelScope.launch {
            when (val result = readCredentials()) {
                CredentialReadResult.Empty -> _uiState.value = LoginUiState()
                CredentialReadResult.Unreadable -> _uiState.value = LoginUiState(
                    errorMessage = "저장된 로그인 정보를 읽을 수 없습니다. 다시 로그인해 주세요.",
                )
                is CredentialReadResult.Available -> {
                    _uiState.value = LoginUiState(
                        isLoading = true,
                        isAutoLogin = true,
                        rememberLogin = true,
                        employeeNumber = result.credentials.employeeNumber,
                        password = result.credentials.password,
                    )
                    Log.i(LOGIN_TAG, "AUTO_LOGIN_START")
                    attemptLogin(
                        result.credentials.employeeNumber,
                        result.credentials.password,
                        rememberLogin = true,
                        isAutoLogin = true,
                    )
                }
            }
        }
    }

    private suspend fun readCredentials(): CredentialReadResult = try {
        credentialStore.read()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        runCatching { credentialStore.clear() }
        CredentialReadResult.Unreadable
    }

    private suspend fun attemptLogin(
        employeeNumber: String,
        password: String,
        rememberLogin: Boolean,
        isAutoLogin: Boolean,
    ) {
        try {
            if (!isAutoLogin && !rememberLogin) credentialStore.clear()
            val userInfo = loginRepository.login(employeeNumber, password, diagnosticsEnabled = !isAutoLogin)
            if (userInfo.first.isNotEmpty() || userInfo.second.isNotEmpty()) {
                xcrewSession.setUserInfo(userInfo.first, userInfo.second)
            }

            if (!isAutoLogin) {
                if (rememberLogin) credentialStore.save(employeeNumber, password)
                else credentialStore.clear()
            }

            val currentMonth = withContext(Dispatchers.Default) {
                YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"))
            }
            if (!isAutoLogin) Log.i(LOGIN_TAG, "LOGIN_SCHEDULE_PREFLIGHT_START month=$currentMonth")
            XcrewWebViewXhrDataSource(xcrewSession, XcrewWorkScheduleParser())
                .getWorkSchedules(currentMonth)
            xcrewSession.markAuthenticated()
            if (!isAutoLogin) Log.i(LOGIN_TAG, "LOGIN_SCHEDULE_PREFLIGHT_SUCCESS")
            if (isAutoLogin) Log.i(LOGIN_TAG, "AUTO_LOGIN_SUCCESS")
            _uiState.value = LoginUiState(
                isLoggedIn = true,
                rememberLogin = rememberLogin,
                employeeNumber = employeeNumber,
                password = password,
            )
        } catch (_: TimeoutCancellationException) {
            handleRetryableFailure(
                employeeNumber,
                password,
                rememberLogin,
                isAutoLogin,
                "로그인 후 근무표 조회가 시간 초과되었습니다. 다시 시도해 주세요.",
                "SCHEDULE_PREFLIGHT_TIMEOUT",
                networkFailure = true,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: XcrewLoginFailureException) {
            handleAuthenticationFailure(employeeNumber, password, rememberLogin, isAutoLogin)
        } catch (_: XcrewSessionExpiredException) {
            handleAuthenticationFailure(employeeNumber, password, rememberLogin, isAutoLogin)
        } catch (failure: XcrewHttpException) {
            if (failure.statusCode == 401 || failure.statusCode == 403) {
                handleAuthenticationFailure(employeeNumber, password, rememberLogin, isAutoLogin)
            } else {
                handleRetryableFailure(
                    employeeNumber,
                    password,
                    rememberLogin,
                    isAutoLogin,
                    "근무표를 불러오지 못했습니다. 네트워크를 확인한 뒤 다시 시도해 주세요.",
                    "SCHEDULE_HTTP_${failure.statusCode}",
                    networkFailure = failure.statusCode == 0 || failure.statusCode >= 500,
                )
            }
        } catch (_: XcrewLoginNetworkException) {
            handleRetryableFailure(
                employeeNumber,
                password,
                rememberLogin,
                isAutoLogin,
                "네트워크 연결을 확인한 뒤 다시 시도해 주세요.",
                "NETWORK",
                networkFailure = true,
            )
        } catch (failure: XcrewLoginPageException) {
            handleRetryableFailure(
                employeeNumber,
                password,
                rememberLogin,
                isAutoLogin,
                "Xcrew 로그인 페이지를 확인할 수 없습니다. 연결을 확인한 뒤 다시 시도해 주세요.",
                "LOGIN_PAGE_${failure.reason}",
            )
        } catch (_: XcrewLoginSessionException) {
            handleRetryableFailure(
                employeeNumber,
                password,
                rememberLogin,
                isAutoLogin,
                "Xcrew 세션을 준비하지 못했습니다. 다시 시도해 주세요.",
                "SESSION",
            )
        } catch (_: XcrewLoginUnexpectedResponseException) {
            handleRetryableFailure(
                employeeNumber,
                password,
                rememberLogin,
                isAutoLogin,
                "Xcrew 응답을 확인할 수 없습니다. 다시 시도해 주세요.",
                "UNEXPECTED_RESPONSE",
            )
        } catch (_: CredentialStorageException) {
            runCatching { credentialStore.clear() }
            _uiState.value = LoginUiState(
                errorMessage = "로그인 정보 저장에 실패했습니다. 저장 설정을 확인하고 다시 로그인해 주세요.",
                employeeNumber = employeeNumber,
                password = password,
            )
        } catch (failure: XcrewResponseException) {
            handleRetryableFailure(
                employeeNumber,
                password,
                rememberLogin,
                isAutoLogin,
                "근무표를 불러오지 못했습니다. 다시 시도해 주세요.",
                "SCHEDULE_${failure::class.simpleName}",
                networkFailure = failure is XcrewNetworkException,
            )
        } catch (failure: Exception) {
            handleRetryableFailure(
                employeeNumber,
                password,
                rememberLogin,
                isAutoLogin,
                "로그인 처리 중 오류가 발생했습니다. 다시 시도해 주세요.",
                "UNEXPECTED_${failure::class.simpleName}",
            )
        }
    }

    private fun handleAuthenticationFailure(
        employeeNumber: String,
        password: String,
        rememberLogin: Boolean,
        isAutoLogin: Boolean,
    ) {
        if (isAutoLogin) {
            Log.w(LOGIN_TAG, "AUTO_LOGIN_AUTH_FAILED")
            _uiState.value = LoginUiState(
                errorMessage = AUTO_LOGIN_AUTH_FAILURE_MESSAGE,
                rememberLogin = rememberLogin,
                employeeNumber = employeeNumber,
                password = password,
            )
        } else {
            _uiState.value = LoginUiState(
                errorMessage = LOGIN_FAILURE_MESSAGE,
                rememberLogin = rememberLogin,
                employeeNumber = employeeNumber,
                password = password,
            )
        }
    }

    private fun handleRetryableFailure(
        employeeNumber: String,
        password: String,
        rememberLogin: Boolean,
        isAutoLogin: Boolean,
        message: String,
        reason: String,
        networkFailure: Boolean = false,
    ) {
        if (isAutoLogin && networkFailure) Log.w(LOGIN_TAG, "AUTO_LOGIN_NETWORK_FAILED")
        else if (!isAutoLogin) Log.w(LOGIN_TAG, "LOGIN_FAILURE_STAGE=$reason")
        _uiState.value = LoginUiState(
            errorMessage = if (isAutoLogin) AUTO_LOGIN_RETRY_MESSAGE else message,
            rememberLogin = rememberLogin,
            employeeNumber = employeeNumber,
            password = password,
            canRetryAutoLogin = isAutoLogin || rememberLogin,
        )
    }
}

private const val LOGIN_FAILURE_MESSAGE = "로그인에 실패했습니다. 사번 또는 비밀번호를 확인해 주세요."
private const val AUTO_LOGIN_AUTH_FAILURE_MESSAGE = "자동 로그인에 실패했습니다. 다시 로그인해 주세요."
private const val AUTO_LOGIN_RETRY_MESSAGE = "자동 로그인에 실패했습니다. 연결을 확인하거나 다시 시도해 주세요."

data class LoginUiState(
    val isLoading: Boolean = false,
    val isAutoLogin: Boolean = false,
    val isCheckingSavedCredentials: Boolean = false,
    val isLoggedIn: Boolean = false,
    val errorMessage: String? = null,
    val employeeNumber: String = "",
    val password: String = "",
    val rememberLogin: Boolean = false,
    val canRetryAutoLogin: Boolean = false,
)

class LoginViewModelFactory(
    private val xcrewSession: XcrewWebViewSession,
    private val credentialStore: SecureCredentialStore,
    private val autoLoginOnStart: Boolean = true,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            throw IllegalArgumentException("Unsupported ViewModel type")
        }
        return LoginViewModel(
            XcrewLoginRepository(),
            xcrewSession,
            credentialStore,
            autoLoginOnStart,
        ) as T
    }
}