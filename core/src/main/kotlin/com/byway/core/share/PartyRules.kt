package com.byway.core.share

/**
 * 리더가 고른 코스를 승낙할지 물어야 하는가(함께달리기 설계 6-6, 6-10.4).
 * 들어와 있고, 제안이 있고, 아직 승낙하지 않았을 때만 묻는다. 답하기 전에 리더가 된 사람도 묻는다.
 */
fun needsAnswer(view: PartyView): Boolean {
    if (!view.joined || view.proposal <= 0 || view.courseId == null) return false
    return view.members.firstOrNull { it.me }?.accepted == false
}

/** 승낙한 사람은 출발하지 못했거나 코스를 끝냈어도 다시 출발할 수 있다(설계 6-10.2). */
fun canRestart(view: PartyView): Boolean =
    view.courseId != null && view.members.firstOrNull { it.me }?.accepted == true
