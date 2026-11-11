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

/**
 * [openedAt]은 파티를 열거나, 다시 열거나, 리더가 코스를 고른 때다. 가상 멤버가 이때부터 움직인다.
 * [owner]는 리더 기기다. [courseId]는 리더가 지금 고른 코스(없으면 null, DB 에는 빈 문자열)이고 [proposal]은 그 제안 번호다.
 */
data class PartyRow(
    val code: String,
    val name: String,
    val courseId: String?,
    val owner: String,
    val open: Boolean,
    val openedAt: Long = 0,
    val proposal: Int = 0,
)

/**
 * [accepted]는 이 멤버가 승낙한 제안 번호다. [present]가 false 면 별표만 남기고 파티에 들어와 있지 않다.
 * [blocked]는 리더가 내보낸 기기다. 닉네임·아바타는 지우고 다시 들어오지 못하게 막아 둔다.
 */
data class MemberRow(
    val partyCode: String,
    val device: String,
    val nickname: String,
    val avatar: String,
    val sharing: Boolean,
    val starred: Boolean,
    val accepted: Int = 0,
    val present: Boolean = true,
    val blocked: Boolean = false,
)

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
                addColumns(db)
            }
            return Store(db)
        }

        /**
         * 11-11 함께달리기 열을 그 자리에서 늘린다. 시연 서버 파일의 파티가 깨지지 않게 표는 다시 만들지 않는다.
         * 제안 번호를 처음 더할 때, 코스를 보고 들어온 옛 파티는 제안 1번에 모두 승낙한 것으로 맞춘다.
         */
        private fun addColumns(db: Connection) = db.createStatement().use { s ->
            fun has(table: String, column: String) =
                s.executeQuery("PRAGMA table_info($table)").use { rs -> generateSequence { if (rs.next()) rs.getString("name") else null }.toList() }.contains(column)
            if (!has("party", "proposal")) {
                s.executeUpdate("ALTER TABLE party ADD COLUMN proposal INTEGER NOT NULL DEFAULT 0")
                s.executeUpdate("UPDATE party SET proposal = 1 WHERE course_id != ''")
                if (!has("member", "accepted")) s.executeUpdate("ALTER TABLE member ADD COLUMN accepted INTEGER NOT NULL DEFAULT 0")
                s.executeUpdate("UPDATE member SET accepted = 1 WHERE party_code IN (SELECT code FROM party WHERE proposal = 1)")
            }
            if (!has("member", "accepted")) s.executeUpdate("ALTER TABLE member ADD COLUMN accepted INTEGER NOT NULL DEFAULT 0")
            if (!has("member", "present")) s.executeUpdate("ALTER TABLE member ADD COLUMN present INTEGER NOT NULL DEFAULT 1")
            if (!has("member", "blocked")) s.executeUpdate("ALTER TABLE member ADD COLUMN blocked INTEGER NOT NULL DEFAULT 0")
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

    /** 전체 공개(숨김 제외), 내가 올린 것, 내가 멤버인 파티의 파티 코스, 내가 멤버인 파티가 달리는 코스. */
    @Synchronized
    fun visibleCourses(device: String): List<CourseRow> = query(
        "SELECT $COURSE_COLUMNS FROM course WHERE (visibility = 'public' AND hidden = 0) OR owner = ? " +
            "OR (visibility = 'party' AND hidden = 0 AND party_code IN (SELECT party_code FROM member WHERE device = ?)) " +
            "OR (hidden = 0 AND id IN (SELECT course_id FROM party WHERE code IN (SELECT party_code FROM member WHERE device = ?))) ORDER BY created_at",
        device, device, device, mapper = ::courseRow,
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
    fun addParty(row: PartyRow, now: Long) = update(
        "INSERT INTO party(code, name, course_id, owner, open, created_at, proposal) VALUES(?, ?, ?, ?, ?, ?, ?)",
        row.code, row.name, row.courseId.orEmpty(), row.owner, row.open.bit, now, row.proposal,
    )

    @Synchronized
    fun party(code: String): PartyRow? = query("SELECT code, name, course_id, owner, open, created_at, proposal FROM party WHERE code = ?", code) {
        PartyRow(it.getString(1), it.getString(2), it.getString(3).ifEmpty { null }, it.getString(4), it.getInt(5) == 1, it.getLong(6), it.getInt(7))
    }.firstOrNull()

    /** 리더가 코스를 고른다. 제안 번호는 늘기만 해서, 앞 제안에 한 승낙이 새 제안의 승낙으로 읽히지 않는다. 새 번호를 돌려준다. */
    @Synchronized
    fun propose(code: String, courseId: String, now: Long): Int {
        update("UPDATE party SET course_id = ?, proposal = proposal + 1, created_at = ? WHERE code = ?", courseId, now, code)
        return query("SELECT proposal FROM party WHERE code = ?", code) { it.getInt(1) }.first()
    }

    @Synchronized
    fun closeParty(code: String) = update("UPDATE party SET open = 0 WHERE code = ?", code)

    @Synchronized
    fun reopenParty(code: String, courseId: String?, now: Long) {
        update("UPDATE party SET open = 1, course_id = '', created_at = ? WHERE code = ?", now, code)
        courseId?.let { propose(code, it, now) }
    }

    @Synchronized
    fun setOwner(code: String, device: String) = update("UPDATE party SET owner = ? WHERE code = ?", device, code)

    @Synchronized
    fun deleteParty(code: String) {
        update("DELETE FROM member WHERE party_code = ?", code)
        update("DELETE FROM party WHERE code = ?", code)
    }

    @Synchronized
    fun partiesOf(device: String): List<String> = query("SELECT party_code FROM member WHERE device = ?", device) { it.getString(1) }

    /** 있으면 고치고 없으면 넣는다. 고칠 때 rowid(들어온 순서)를 지킨다. */
    @Synchronized
    fun putMember(row: MemberRow) = update(
        "INSERT INTO member(party_code, device, nickname, avatar, sharing, starred, accepted, present, blocked) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT(party_code, device) DO UPDATE SET nickname = excluded.nickname, avatar = excluded.avatar, sharing = excluded.sharing, " +
            "starred = excluded.starred, accepted = excluded.accepted, present = excluded.present, blocked = excluded.blocked",
        row.partyCode, row.device, row.nickname, row.avatar, row.sharing.bit, row.starred.bit, row.accepted, row.present.bit, row.blocked.bit,
    )

    /** 들어온 순서. 들어와 있지 않은 별표 멤버와 막힌 기기도 함께 돌려준다. */
    @Synchronized
    fun members(code: String): List<MemberRow> = query(
        "SELECT party_code, device, nickname, avatar, sharing, starred, accepted, present, blocked FROM member WHERE party_code = ? ORDER BY rowid", code,
    ) {
        MemberRow(
            it.getString(1), it.getString(2), it.getString(3), it.getString(4), it.getInt(5) == 1, it.getInt(6) == 1,
            it.getInt(7), it.getInt(8) == 1, it.getInt(9) == 1,
        )
    }

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
