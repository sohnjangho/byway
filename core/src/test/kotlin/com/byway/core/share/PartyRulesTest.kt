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

    /** 리더의 코스가 끝나면 코스를 비워 다음엔 다시 고른다(PM 11-11 "코스를 고르지 않았는데 이미 지정됨"). */
    @Test
    fun leaderEndsRunClearsCourse() {
        assertTrue(leaderEndsRun(view(member(leader = true, accepted = true))))
        assertFalse(leaderEndsRun(view(member(accepted = true))))
        assertFalse(leaderEndsRun(view(member(leader = true), proposal = 0, courseId = null)))
        assertFalse(leaderEndsRun(null))
    }

    /** 함께 달리기 기본 이름(PM 11-11 "Road와 함께 달리기는 좀 이상함"): "닉네임님의 드라이브". */
    @Test
    fun togetherNameIsNicknamesDrive() {
        org.junit.Assert.assertEquals("하나님의 드라이브", togetherName("하나"))
        org.junit.Assert.assertEquals("Road님의 드라이브", togetherName(" Road "))
        org.junit.Assert.assertEquals("함께 드라이브", togetherName("  "))
    }
}
