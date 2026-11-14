package com.example.crewmate.data.remote.xcrew

import java.lang.ref.WeakReference

/**
 * 프로세스 전역 활성 XcrewWebViewSession 레지스트리
 * 
 * AppNavigation 등 포그라운드 UI에서 유지 중인 활성 웹뷰 세션을 등록해두어,
 * 백그라운드 리시버(WorkAlarmReceiver 등)에서 새로운 웹뷰/로그인 없이
 * 기존 세션 쿠키를 활용해 즉각 실시간 조회를 수행할 수 있도록 지원합니다.
 */
object XcrewSessionRegistry {
    private var activeSessionRef: WeakReference<XcrewWebViewSession>? = null

    fun register(session: XcrewWebViewSession) {
        activeSessionRef = WeakReference(session)
    }

    fun unregister(session: XcrewWebViewSession) {
        if (activeSessionRef?.get() === session) {
            activeSessionRef = null
        }
    }

    fun current(): XcrewWebViewSession? {
        val s = activeSessionRef?.get()
        return if (s != null && s.isSessionCurrentlyValid()) s else null
    }

    /**
     * 세션 유효 여부와 무관하게 인스턴스가 존재하는지 확인
     */
    fun rawCurrent(): XcrewWebViewSession? {
        return activeSessionRef?.get()
    }
}
