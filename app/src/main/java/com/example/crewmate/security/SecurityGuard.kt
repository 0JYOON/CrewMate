package com.example.crewmate.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import com.example.crewmate.BuildConfig
import java.security.MessageDigest

/**
 * Release 빌드 전용 무결성(서명 위조 방지) 및 안티-디버깅 가드.
 * Debug 빌드에서는 항상 통과하며, Release 빌드에서 리패키징/재서명된 APK 실행을 차단합니다.
 */
object SecurityGuard {

    private const val EXPECTED_RELEASE_CERT_SHA256 =
        "1A3C7CF36D2DF3D97E989E070CDD4E678C4F588F336E59E14FABE4C4396A409A"

    fun verifyAppIntegrity(context: Context): Boolean {
        if (BuildConfig.DEBUG) return true
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return false
        return hasValidSigningCertificate(context)
    }

    @Suppress("DEPRECATION")
    private fun hasValidSigningCertificate(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                val signingInfo = info.signingInfo ?: return false
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            } ?: return false

            val digest = MessageDigest.getInstance("SHA-256")
            signatures.any { sig ->
                val hash = digest.digest(sig.toByteArray())
                val hex = hash.joinToString("") { "%02X".format(it) }
                hex.equals(EXPECTED_RELEASE_CERT_SHA256, ignoreCase = true)
            }
        } catch (_: Exception) {
            false
        }
    }
}
