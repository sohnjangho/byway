package com.byway.core.drive

import com.byway.core.course.Course
import com.byway.core.course.Leg
import com.byway.core.course.Section
import com.byway.core.geo.GeoPoint

/** 차 안 연출 한 장면. 창문 0~10, 풍량과 조명은 차종 범위 안에서 쓴다. */
data class CabinScene(
    val windowPosition: Int? = null,
    val fanSpeed: Int? = null,
    val ambientBrightness: Int? = null,
)

/** 일어난 일. */
sealed interface DriveEvent {
    data class CourseStarted(val course: Course) : DriveEvent
    data class LocationUpdated(val point: GeoPoint) : DriveEvent
    data class SectionEntered(val section: Section) : DriveEvent
    data class DrivingStateChanged(val parked: Boolean) : DriveEvent
    data object CourseFinished : DriveEvent
}

/** 해 달라는 일. 실행은 platform 이 맡는다. */
sealed interface DriveCommand {
    data class ShowRoute(val leg: Leg) : DriveCommand
    data class Say(val text: String) : DriveCommand
    data class ShowLine(val text: String) : DriveCommand

    /** 차 안 상태를 바꾼다. 값이 null 인 항목은 건드리지 않는다. */
    data class SetCabin(val scene: CabinScene) : DriveCommand

    /** 정차 중에만 여는 화면. */
    data class ShowPanel(val panelId: String) : DriveCommand

    /** 다른 기능에게 사건을 알린다. 호스트가 처리하고 밖으로 나가지 않는다. */
    data class Publish(val event: DriveEvent) : DriveCommand
}

interface DriveFeature {
    val id: String
    fun onEvent(event: DriveEvent): List<DriveCommand>
}
