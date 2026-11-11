package com.byway.core.share

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyRulesTest {
    private fun member(me: Boolean = true, leader: Boolean = false, accepted: Boolean = false) =
        PartyMember("m-1", "하나", "avatar-01", demo = false, me = me, sharing = false, starred = false, leader = leader, accepted = accepted)

    private fun view(me: PartyMember, proposal: Int = 1, courseId: String? = "c", joined: Boolean = true) =
        PartyView("ABC234", "하나의 파티", courseId, open = true, owner = me.leader, members = listOf(me), pins = emptyList(), proposal = proposal, leaderName = "둘", joined = joined)

    @Test
    fun noProposalNoQuestion() {
        assertFalse(needsAnswer(view(member(), proposal = 0, courseId = null)))
    }

    @Test
    fun pendingMemberIsAsked() {
        assertTrue(needsAnswer(view(member())))
    }

    @Test
    fun acceptedMemberIsNotAsked() {
        assertFalse(needsAnswer(view(member(accepted = true))))
    }

    /** 답하기 전에 리더가 된 사람도 묻는다(설계 6-10.4). */
    @Test
    fun aNewLeaderWhoHasNotAnsweredIsStillAsked() {
        assertTrue(needsAnswer(view(member(leader = true))))
    }

    /** 다시 모이기로 열린 파티에 아직 들어가지 않았으면 묻지 않는다(설계 6-10.6). */
    @Test
    fun notJoinedIsNotAsked() {
        assertFalse(needsAnswer(view(member(), joined = false)))
    }

    /**
     * 리더가 함께 달리기 코스를 달리고 끝내면 코스를 비워 다음엔 다시 고른다(PM 11-11 "코스를 고르지 않았는데 이미 지정됨").
     * 다른 코스를 혼자 달리고 끝낸 것은 비우지 않는다. 내가 올린 코스는 서버 id(s-)로 고르므로 기기 id(m-)와 함께 본다(검토 I-2).
     */
    @Test
    fun leaderEndsRunClearsCourse() {
        assertTrue(leaderEndsRun(view(member(leader = true, accepted = true)), setOf("c")))
        assertFalse(leaderEndsRun(view(member(leader = true, accepted = true)), setOf("other")))
        assertTrue(leaderEndsRun(view(member(leader = true, accepted = true), courseId = "s-9"), setOf("m-1", "s-9")))
        assertFalse(leaderEndsRun(view(member(accepted = true)), setOf("c")))
        assertFalse(leaderEndsRun(view(member(leader = true), proposal = 0, courseId = null), setOf("c")))
        assertFalse(leaderEndsRun(null, setOf("c")))
    }

    /** 승낙했고 코스가 살아 있으면 출발할 수 있다. 출발 화면 고르기를 닫았어도 다시 출발한다(검토 I-1). */
    @Test
    fun canStartWhileCourseIsSet() {
        assertTrue(canStart(view(member(accepted = true))))
        assertFalse(canStart(view(member())))
        assertFalse(canStart(view(member(accepted = true), proposal = 0, courseId = null)))
    }

    /** 비우기가 실패하면 다음에 다시 보낸다. 그사이 리더가 새로 고른 코스(제안 번호가 다름)는 지우지 않는다. */
    @Test
    fun clearIsRetriedOnlyForTheSameProposal() {
        val leader = view(member(leader = true, accepted = true), proposal = 3)
        assertTrue(retryClear(PendingClear("ABC234", 3), leader))
        assertFalse(retryClear(PendingClear("ABC234", 2), leader))
        assertFalse(retryClear(PendingClear("ZZZ999", 3), leader))
        assertFalse(retryClear(null, leader))
        assertFalse(retryClear(PendingClear("ABC234", 3), view(member(leader = true), proposal = 3, courseId = null)))
    }

    /** 함께 달리기 기본 이름(PM 11-11 "Road와 함께 달리기는 좀 이상함"): "닉네임님의 드라이브". */
    @Test
    fun togetherNameIsNicknamesDrive() {
        org.junit.Assert.assertEquals("하나님의 드라이브", togetherName("하나"))
        org.junit.Assert.assertEquals("Road님의 드라이브", togetherName(" Road "))
        org.junit.Assert.assertEquals("함께 드라이브", togetherName("  "))
    }
}
