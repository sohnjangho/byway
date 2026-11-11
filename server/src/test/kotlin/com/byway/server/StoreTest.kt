package com.byway.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.DriverManager

class StoreTest {
    /** 11-11 이전 스키마. 시연 서버 파일이 이 모양이다. */
    private fun oldDatabase(): File {
        val file = tempDb()
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { db ->
            db.createStatement().use { s ->
                s.executeUpdate(
                    "CREATE TABLE party(code TEXT PRIMARY KEY, name TEXT NOT NULL, course_id TEXT NOT NULL, owner TEXT NOT NULL, " +
                        "open INTEGER NOT NULL, created_at INTEGER NOT NULL)",
                )
                s.executeUpdate(
                    "CREATE TABLE member(party_code TEXT NOT NULL, device TEXT NOT NULL, nickname TEXT NOT NULL, avatar TEXT NOT NULL, " +
                        "sharing INTEGER NOT NULL DEFAULT 0, starred INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(party_code, device))",
                )
                s.executeUpdate("INSERT INTO party VALUES('A39Z8V', '하나의 파티', 'river', 'd1', 1, 5)")
                s.executeUpdate("INSERT INTO member VALUES('A39Z8V', 'd1', '하나', 'avatar-01', 0, 0)")
            }
        }
        return file
    }

    /** 코스를 보고 들어온 옛 파티는 제안 1번에 모두 승낙한 것으로 읽는다. 다시 열어도 그대로다. */
    @Test
    fun oldDatabaseGainsColumns() {
        val file = oldDatabase()
        val store = Store.open(file)
        val party = requireNotNull(store.party("A39Z8V"))
        assertEquals("river", party.courseId)
        assertEquals(1, party.proposal)
        val member = store.members("A39Z8V").single()
        assertTrue(member.present)
        assertEquals(1, member.accepted)
        assertEquals(1, Store.open(file).party("A39Z8V")?.proposal)
    }

    /** 들어온 순서는 리더를 넘겨받는 순서다. 위치 공유를 껐다 켜도 바뀌지 않는다. */
    @Test
    fun memberOrderSurvivesUpdates() {
        val store = Store.open(tempDb())
        store.addParty(PartyRow("ABC234", "파티", null, "a", open = true), 1)
        listOf("a", "b", "c").forEach { store.putMember(MemberRow("ABC234", it, it, "avatar-01", sharing = false, starred = false)) }
        store.putMember(MemberRow("ABC234", "a", "a", "avatar-01", sharing = true, starred = false))
        assertEquals(listOf("a", "b", "c"), store.members("ABC234").map { it.device })
        assertTrue(store.members("ABC234").first().sharing)
    }

    @Test
    fun partyWithoutCourseReadsNull() {
        val store = Store.open(tempDb())
        store.addParty(PartyRow("ABC234", "파티", null, "a", open = true), 1)
        assertNull(store.party("ABC234")?.courseId)
        assertEquals(0, store.party("ABC234")?.proposal)
    }

    @Test
    fun proposeRaisesTheNumber() {
        val store = Store.open(tempDb())
        store.addParty(PartyRow("ABC234", "파티", null, "a", open = true), 1)
        assertEquals(1, store.propose("ABC234", "river", 10))
        assertEquals(2, store.propose("ABC234", "sea", 20))
        val party = requireNotNull(store.party("ABC234"))
        assertEquals("sea", party.courseId)
        assertEquals(2, party.proposal)
        assertEquals(20, party.openedAt)
    }
}
