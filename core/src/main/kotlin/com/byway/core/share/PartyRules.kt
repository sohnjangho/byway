package com.byway.core.share

/**
 * 리더가 고른 코스를 승낙할지 물어야 하는가(함께달리기 설계 6-6, 6-10.4).
 * 들어와 있고, 제안이 있고, 아직 승낙하지 않았을 때만 묻는다. 답하기 전에 리더가 된 사람도 묻는다.
 */
fun needsAnswer(view: PartyView): Boolean {
    if (!view.joined || view.proposal <= 0 || view.courseId == null) return false
    return view.members.firstOrNull { it.me }?.accepted == false
}

/**
 * 리더가 함께 달리기 코스를 달리다 끝냈을 때(끝까지 달림 또는 코스 끝내기) 서버의 코스를 비워야 하는가.
 * 비우면 다음 코스는 리더가 다시 고른다(PM 11-11 "고르지 않았는데 정해져 있음"). [runIds]는 달린 코스의 기기 id 와
 * 그 코스를 올린 서버 id 다. 다른 코스를 혼자 달리고 끝낸 것은 비우지 않는다(검토 I-2).
 */
fun leaderEndsRun(view: PartyView?, runIds: Set<String>): Boolean =
    view != null && view.owner && view.courseId != null && view.courseId in runIds

/** 승낙했고 코스가 살아 있으면 출발할 수 있다. 출발 화면 고르기를 닫았어도 다시 출발한다(검토 I-1). */
fun canStart(view: PartyView): Boolean = view.courseId != null && view.members.firstOrNull { it.me }?.accepted == true

/** 보내지 못한 코스 비우기. 어느 함께 달리기의 몇 번째 제안이었는지 둔다. */
data class PendingClear(val code: String, val proposal: Int)

/** 보내지 못한 비우기를 다시 보낼까. 그사이 리더가 새로 고른 코스(제안 번호가 다름)는 지우지 않는다. */
fun retryClear(pending: PendingClear?, view: PartyView): Boolean =
    pending != null && view.owner && view.code == pending.code && view.proposal == pending.proposal && view.courseId != null

/** 함께 달리기 기본 이름(PM 11-11): "닉네임님의 드라이브". 닉네임이 비면 "함께 드라이브"다. */
fun togetherName(nickname: String): String {
    val name = nickname.trim()
    return if (name.isEmpty()) "함께 드라이브" else "${name}님의 드라이브"
}
