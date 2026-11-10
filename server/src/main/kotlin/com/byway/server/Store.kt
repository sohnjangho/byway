package com.byway.server

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/** 서버에 남는 것: 기기, 공유 코스, 파티와 멤버. 위치는 여기 쓰지 않는다(메모리, 60초). */
data class CourseRow(
    val id: String,
    val code: String?,
    val visibility: String,
    val partyCode: String?,
    val owner: String,
    val body: String,
    val finishes: Int,
    val hidden: Boolean,
)

/** [openedAt]은 파티를 열거나 다시 연 때다. 가상 멤버가 이때부터 움직인다. */
data class PartyRow(val code: String, val name: String, val courseId: String, val owner: String, val open: Boolean, val openedAt: Long = 0)

data class MemberRow(val partyCode: String, val device: String, val nickname: String, val avatar: String, val sharing: Boolean, val starred: Boolean)

/** SQLite 파일 하나. 연결도 하나라서 모든 호출을 이 객체로 줄 세운다. */
class Store private constructor(private val db: Connection) {
    companion object {
        fun open(file: File): Store {
            file.absoluteFile.parentFile?.mkdirs()
            val db = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
            db.createStatement().use { s ->
                s.executeUpdate("PRAGMA foreign_keys = ON")
                s.executeUpdate("CREATE TABLE IF NOT EXISTS device(id TEXT PRIMARY KEY, token_hash TEXT UNIQUE NOT NULL, created_at INTEGER NOT NULL)")
                s.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS course(id TEXT PRIMARY KEY, code TEXT UNIQUE, visibility TEXT NOT NULL, party_code TEXT, " +
                        "owner TEXT NOT NULL, body TEXT NOT NULL, finishes INTEGER NOT NULL DEFAULT 0, hidden INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)",
                )
                s.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS party(code TEXT PRIMARY KEY, name TEXT NOT NULL, course_id TEXT NOT NULL, owner TEXT NOT NULL, " +
                        "open INTEGER NOT NULL, created_at INTEGER NOT NULL)",
                )
                s.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS member(party_code TEXT NOT NULL, device TEXT NOT NULL, nickname TEXT NOT NULL, avatar TEXT NOT NULL, " +
                        "sharing INTEGER NOT NULL DEFAULT 0, starred INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(party_code, device))",
                )
            }
            return Store(db)
        }
    }

    @Synchronized
    fun addDevice(id: String, tokenHash: String, now: Long) =
        update("INSERT INTO device(id, token_hash, created_at) VALUES(?, ?, ?)", id, tokenHash, now)

    @Synchronized
    fun deviceFor(tokenHash: String): String? = query("SELECT id FROM device WHERE token_hash = ?", tokenHash) { it.getString(1) }.firstOrNull()

    @Synchronized
    fun addCourse(row: CourseRow, now: Long) = update(
        "INSERT INTO course(id, code, visibility, party_code, owner, body, created_at) VALUES(?, ?, ?, ?, ?, ?, ?)",
        row.id, row.code, row.visibility, row.partyCode, row.owner, row.body, now,
    )

    @Synchronized
    fun course(id: String): CourseRow? = query("SELECT $COURSE_COLUMNS FROM course WHERE id = ?", id, mapper = ::courseRow).firstOrNull()

    @Synchronized
    fun courseByCode(code: String): CourseRow? = query("SELECT $COURSE_COLUMNS FROM course WHERE code = ?", code, mapper = ::courseRow).firstOrNull()

    @Synchronized
    fun codeTaken(code: String): Boolean =
        query("SELECT 1 FROM course WHERE code = ? UNION SELECT 1 FROM party WHERE code = ?", code, code) { 1 }.isNotEmpty()

    /** 전체 공개(숨김 제외), 내가 올린 것, 내가 멤버인 파티의 파티 코스. */
    @Synchronized
    fun visibleCourses(device: String): List<CourseRow> = query(
        "SELECT $COURSE_COLUMNS FROM course WHERE (visibility = 'public' AND hidden = 0) OR owner = ? " +
            "OR (visibility = 'party' AND hidden = 0 AND party_code IN (SELECT party_code FROM member WHERE device = ?)) ORDER BY created_at",
        device, device, mapper = ::courseRow,
    )

    @Synchronized
    fun deleteCourse(id: String) = update("DELETE FROM course WHERE id = ?", id)

    @Synchronized
    fun addFinish(id: String): Boolean = update("UPDATE course SET finishes = finishes + 1 WHERE id = ?", id) > 0

    @Synchronized
    fun hide(id: String): Boolean = update("UPDATE course SET hidden = 1 WHERE id = ?", id) > 0

    /** 내 코스, 내 멤버 정보, 기기를 지운다. 내가 만든 파티는 남은 멤버가 없으면 지운다. */
    @Synchronized
    fun deleteDevice(device: String) {
        update("DELETE FROM course WHERE owner = ?", device)
        update("DELETE FROM member WHERE device = ?", device)
        update("DELETE FROM party WHERE code NOT IN (SELECT party_code FROM member)")
        update("DELETE FROM device WHERE id = ?", device)
    }

    @Synchronized
    fun addParty(row: PartyRow, now: Long) =
        update("INSERT INTO party(code, name, course_id, owner, open, created_at) VALUES(?, ?, ?, ?, ?, ?)", row.code, row.name, row.courseId, row.owner, row.open.bit, now)

    @Synchronized
    fun party(code: String): PartyRow? = query("SELECT code, name, course_id, owner, open, created_at FROM party WHERE code = ?", code) {
        PartyRow(it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getInt(5) == 1, it.getLong(6))
    }.firstOrNull()

    @Synchronized
    fun closeParty(code: String) = update("UPDATE party SET open = 0 WHERE code = ?", code)

    @Synchronized
    fun reopenParty(code: String, courseId: String, now: Long) =
        update("UPDATE party SET open = 1, course_id = ?, created_at = ? WHERE code = ?", courseId, now, code)

    @Synchronized
    fun setOwner(code: String, device: String) = update("UPDATE party SET owner = ? WHERE code = ?", device, code)

    @Synchronized
    fun deleteParty(code: String) {
        update("DELETE FROM member WHERE party_code = ?", code)
        update("DELETE FROM party WHERE code = ?", code)
    }

    @Synchronized
    fun partiesOf(device: String): List<String> = query("SELECT party_code FROM member WHERE device = ?", device) { it.getString(1) }

    @Synchronized
    fun putMember(row: MemberRow) = update(
        "INSERT OR REPLACE INTO member(party_code, device, nickname, avatar, sharing, starred) VALUES(?, ?, ?, ?, ?, ?)",
        row.partyCode, row.device, row.nickname, row.avatar, row.sharing.bit, row.starred.bit,
    )

    @Synchronized
    fun members(code: String): List<MemberRow> = query(
        "SELECT party_code, device, nickname, avatar, sharing, starred FROM member WHERE party_code = ? ORDER BY rowid", code,
    ) { MemberRow(it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getInt(5) == 1, it.getInt(6) == 1) }

    @Synchronized
    fun deleteMember(code: String, device: String) = update("DELETE FROM member WHERE party_code = ? AND device = ?", code, device)

    private fun update(sql: String, vararg args: Any?): Int = db.prepareStatement(sql).use { s ->
        args.forEachIndexed { i, a -> s.setObject(i + 1, a) }
        s.executeUpdate()
    }

    private fun <T> query(sql: String, vararg args: Any?, mapper: (ResultSet) -> T): List<T> = db.prepareStatement(sql).use { s ->
        args.forEachIndexed { i, a -> s.setObject(i + 1, a) }
        s.executeQuery().use { rs -> buildList { while (rs.next()) add(mapper(rs)) } }
    }

    private fun courseRow(rs: ResultSet) = CourseRow(
        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getInt(7), rs.getInt(8) == 1,
    )

    private val Boolean.bit get() = if (this) 1 else 0
}

private const val COURSE_COLUMNS = "id, code, visibility, party_code, owner, body, finishes, hidden"
