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

/**
 * 지금 누가 운전하는가. 주행 규제는 손이 아니라 운전자의 시선을 기준으로 판단한다.
 * HANDS_OFF 는 핸들에서 손을 떼도 도로를 봐야 하는 단계라 규제를 풀지 않는다.
 */
enum class DriveMode { MANUAL, HANDS_OFF, EYES_OFF }

/**
 * 코스를 따라가게 하는 쪽의 능력. 지금은 내장 내비이고, 나중에는 자율주행이 같은 자리에 들어온다.
 * 경유지 개수가 늘거나 경로 전체를 받을 수 있게 되면 값만 바뀌고 코스 엔진은 그대로다.
 */
data class RouteAbility(val maxWaypoints: Int = 2, val acceptsWholePath: Boolean = false)

/** 코스를 실제로 따라가게 하는 쪽. platform 이 구현한다. */
interface RouteFollower {
    val ability: RouteAbility
    fun follow(route: DriveCommand.FollowRoute)
}

/** 일어난 일. */
sealed interface DriveEvent {
    data class CourseStarted(val course: Course) : DriveEvent
    data class LocationUpdated(val point: GeoPoint) : DriveEvent
    data class SectionEntered(val section: Section) : DriveEvent
    data class DrivingStateChanged(val parked: Boolean) : DriveEvent
    data class DriveModeChanged(val mode: DriveMode) : DriveEvent
    data object CourseFinished : DriveEvent
}

/** 해 달라는 일. 실행은 platform 이 맡는다. */
sealed interface DriveCommand {
    /** 코스를 따라가게 한다. path 는 경로 전체를 받을 수 있는 쪽에만 채운다. */
    data class FollowRoute(val leg: Leg, val path: List<GeoPoint>? = null) : DriveCommand
    data class Say(val text: String) : DriveCommand
    data class ShowLine(val text: String) : DriveCommand

    /** 화면이 그릴 주행 진행값. 문구가 아니라 값으로 주어 문구를 고쳐도 화면이 깨지지 않는다. */
    data class ShowProgress(val sectionIndex: Int, val remainingMeters: Double) : DriveCommand

    /** 차 안 상태를 바꾼다. 값이 null 인 항목은 건드리지 않는다. */
    data class SetCabin(val scene: CabinScene) : DriveCommand

    /** 구간 분위기에 맞는 배경음악을 튼다. track 은 forest, city, river 중 하나다. */
    data class PlayMusic(val track: String) : DriveCommand
    data object StopMusic : DriveCommand

    /** 정차 중에만 여는 화면. */
    data class ShowPanel(val panelId: String) : DriveCommand

    /** 다른 기능에게 사건을 알린다. 호스트가 처리하고 밖으로 나가지 않는다. */
    data class Publish(val event: DriveEvent) : DriveCommand
}

interface DriveFeature {
    val id: String
    fun onEvent(event: DriveEvent): List<DriveCommand>
}
