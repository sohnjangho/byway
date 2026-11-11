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
 * 리더의 코스가 끝났을 때(끝까지 달림 또는 코스 끝내기) 서버의 코스를 비워야 하는가.
 * 비우면 다음 코스는 리더가 다시 고른다. 한 번 고른 코스가 남아 "고르지 않았는데 정해져 있음"이 되지 않게 한다(PM 11-11).
 */
fun leaderEndsRun(view: PartyView?): Boolean = view != null && view.owner && view.courseId != null
