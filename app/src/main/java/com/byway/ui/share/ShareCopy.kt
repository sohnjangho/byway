package com.byway.ui.share

import com.byway.core.share.Visibility

/** 공유 범위와 화면 문구(공유 설계 6-4). */
internal val SHARE_SCOPES = listOf(Visibility.PUBLIC to "전체", Visibility.PARTY to "파티", Visibility.LINK to "링크")

internal fun scopeTitle(scope: String): String = SHARE_SCOPES.firstOrNull { it.first == scope }?.second ?: scope

internal fun scopeHint(scope: String): String = when (scope) {
    Visibility.PUBLIC -> "더 많은 코스와 랭킹에서 모든 사람이 봅니다"
    Visibility.PARTY -> "고른 파티의 멤버만 봅니다"
    else -> "코드를 받은 사람만 봅니다"
}

/** 공유 동의 화면의 글. 무엇이 올라가는지, 누가 보는지, 언제 지워지는지를 적는다. */
internal fun consentLines(scope: String): List<String> = listOf(
    "코스 경로(처음과 끝 500m를 잘라 낸 것)와 이름이 공유 서버에 올라갑니다.",
    "${scopeHint(scope)}.",
    "닉네임이나 차 정보는 함께 올라가지 않습니다.",
    "공유를 끄거나 코스를 지우면 서버에서도 지웁니다.",
)

internal fun finishesText(count: Int): String = if (count == 0) "아직 완주 기록 없음" else "완주 ${count}회"

/** 파티 범위로 공유하려면 파티가 있어야 한다. 파티는 함께달리기 메뉴에서 만든다. */
internal const val PARTY_NEEDED = "먼저 함께달리기에서 파티를 만들거나 참여해 주세요"

internal const val OFFLINE_NOTE = "공유 서버에 연결할 수 없습니다. 앱에 든 코스는 그대로 쓸 수 있습니다"
