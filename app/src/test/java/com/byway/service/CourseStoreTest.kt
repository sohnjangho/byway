package com.byway.service

import com.byway.core.course.Course
import com.byway.core.course.Section
import com.byway.core.course.writeCourse
import com.byway.core.geo.GeoPoint
import com.byway.core.share.SharedCourse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class CourseStoreTest {
    private fun course(id: String, name: String = id) =
        Course(id, name, listOf(GeoPoint(37.0, 127.0), GeoPoint(37.0, 127.1)), listOf(Section("1구간", 0, 1, "city")), attribution = "에움길 운전자 기록")

    private fun shared(id: String, mine: Boolean = false) = SharedCourse(id, "ABCDEF", "public", 0, mine, writeCourse(course(id)))

    @Test
    fun mergeKeepsAssetsFirst() {
        val merged = mergeCourses(
            assets = listOf(course("a", "앱")),
            mine = listOf(course("m-1"), course("a", "내 것")),
            received = listOf(course("s-1", "받은 것")),
            shared = listOf(course("s-1", "목록"), course("s-2")),
        )
        assertEquals(listOf("a", "m-1", "s-1", "s-2"), merged.map { it.id })
        assertEquals("앱", merged.first().name)
        assertEquals("받은 것", merged.first { it.id == "s-1" }.name)
    }

    @Test
    fun mergeDropsRemovedShared() {
        val files = CourseFiles(Files.createTempDirectory("store").toFile())
        files.replaceShared(listOf(shared("s-1"), shared("s-2")))
        files.replaceShared(listOf(shared("s-2")))
        assertEquals(listOf("s-2"), files.shared().map { it.id })
    }

    @Test
    fun myOwnUploadsAreNotListedTwice() {
        val files = CourseFiles(Files.createTempDirectory("store").toFile())
        files.saveMine(course("m-1"))
        files.replaceShared(listOf(shared("s-9", mine = true), shared("s-2")))
        files.saveReceived(shared("s-3"))
        assertEquals(listOf("m-1", "s-3", "s-2"), files.courses(assets = emptyList()).map { it.id })
        files.deleteMine("m-1")
        files.clearAll()
        assertTrue(files.courses(assets = emptyList()).isEmpty())
    }
}
