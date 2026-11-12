package com.byway.ui

import com.byway.core.course.ScenePlace
import com.byway.core.course.Section
import com.byway.core.drive.Prompt
import org.junit.Assert.assertEquals
import org.junit.Test

class DriveReadoutTest {
    /** 구간 둘이 각각 10km 다. */
    private val meters = listOf(10_000.0, 10_000.0)

    @Test
    fun beforeTheFirstFixTheWholeCourseIsLeft() {
        val readout = readoutOf(meters, 0, null)
        assertEquals("20.0", readout.remainingKm)
        assertEquals(listOf(0f, 0f), readout.filled)
    }

    @Test
    fun theRemainingDistanceIsShownInKilometres() {
        assertEquals("7.5", readoutOf(meters, 1, 7_500.0).remainingKm)
    }

    @Test
    fun aPassedSectionIsFullAndTheCurrentOneIsPartlyFilled() {
        assertEquals(listOf(1f, 0.25f), readoutOf(meters, 1, 7_500.0).filled)
    }

    @Test
    fun theFirstSectionFillsWhileTheNextStaysEmpty() {
        assertEquals(listOf(0.5f, 0f), readoutOf(meters, 0, 15_000.0).filled)
    }

    /** 엔진의 물음은 주행 화면에서 버튼 하나가 된다. 누르면 그렇다는 답이고, 누르지 않으면 아니라는 답이다. */
    @Test
    fun aQuestionBecomesOneButton() {
        assertEquals("잠깐 들러요", promptButton(Prompt.DETOUR))
        assertEquals("코스 이어가기", promptButton(Prompt.RESUME))
        assertEquals(null, promptButton(null))
    }

    @Test
    fun aLostFixReplacesTheSpokenLine() {
        assertEquals("위치 확인 중", driveLine("강변 구간입니다.", stale = true))
        assertEquals("강변 구간입니다.", driveLine("강변 구간입니다.", stale = false))
    }

    @Test
    fun theResumeQuestionNamesTheCourseAndTheSection() {
        assertEquals("팔당 노을 코스 2구간을 이어갈까요?", resumeLine("팔당 노을", 2))
    }

    @Test
    fun theSectionNumberComesFromTheSavedPoint() {
        val sections = listOf(Section("숲길", 0, 4), Section("강변", 5, 9))
        assertEquals(1, sectionNoAt(sections, 0))
        assertEquals(2, sectionNoAt(sections, 6))
    }

    /** 코스 파일이 바뀌어 저장한 지점이 코스 밖이면 마지막 구간으로 본다. */
    @Test
    fun aSavedPointPastTheCourseCountsAsTheLastSection() {
        assertEquals(2, sectionNoAt(listOf(Section("숲길", 0, 4), Section("강변", 5, 9)), 99))
    }

    @Test
    fun theSectionCountStartsAtOne() {
        assertEquals("구간 2/3", sectionCount(1, 3))
    }

    private fun place(no: Int, km: Double, label: String = "경치", note: String = "") =
        ScenePlace(no = no, name = "곳$no", kind = "view", label = label, note = note, km = km, x = 0f, y = 0f)

    @Test
    fun placesAlreadyPassedDropOffTheList() {
        val places = listOf(place(1, 2.0), place(2, 5.0), place(3, 9.0))
        assertEquals(listOf(2, 3), placesAhead(places, 3.5, 4).map { it.no })
    }

    @Test
    fun theNearestPlaceComesFirstEvenIfNumberedLater() {
        val places = listOf(place(1, 9.0), place(2, 5.0))
        assertEquals(listOf(2, 1), placesAhead(places, 0.0, 4).map { it.no })
    }

    @Test
    fun theListStopsAtTheRowsThatFit() {
        val places = (1..6).map { place(it, it.toDouble()) }
        assertEquals(listOf(1), placesAhead(places, 0.0, 1).map { it.no })
    }

    @Test
    fun aPlaceRightHereStillCounts() {
        assertEquals(listOf(1), placesAhead(listOf(place(1, 3.0)), 3.0, 4).map { it.no })
    }

    @Test
    fun thereIsNothingAheadAtTheEnd() {
        assertEquals(emptyList<ScenePlace>(), placesAhead(listOf(place(1, 3.0)), 3.1, 4))
    }

    @Test
    fun theDistanceToAPlaceIsInKilometres() {
        assertEquals("1.5km", aheadKmText(place(1, 5.0), 3.5))
    }

    @Test
    fun thePlaceLineLeavesOutTheCourseKm() {
        assertEquals("경치 · 해질녘", aheadLine(place(1, 12.4, note = "해질녘")))
        assertEquals("경치", aheadLine(place(1, 12.4)))
    }
}
