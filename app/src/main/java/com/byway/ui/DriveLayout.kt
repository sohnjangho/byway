package com.byway.ui

import com.byway.service.MyDrives

/**
 * 코스를 달릴 때의 화면 배치. 출발할 때 플레오스 화면 그림을 보고 고른다(PM 11-11). 설정의 "주행 화면"은 처음 골라져 있는 값이다.
 * [panes]는 왼쪽부터의 칸 이름이다. 저장값은 이름의 소문자("split", "split_byway", "navi")다.
 * 3분할에서는 새로 띄운 앱이 가운데로 가고 가운데 있던 앱이 오른쪽으로 간다(주행 화면 3분할 설계, 11-11 실측).
 */
enum class DriveLayout(val title: String, val panes: List<String>, private val needsThird: Boolean) {
    SPLIT("3분할 · 차량 정보 + 내비 + 에움길", listOf("차량 정보", "내비", "에움길"), true),
    SPLIT_BYWAY("3분할 · 차량 정보 + 에움길 + 내비", listOf("차량 정보", "에움길", "내비"), true),
    NAVI("2분할 · 차량 정보 + 내비", listOf("차량 정보", "내비"), false);

    /** 지금 창에서 바로 출발할 수 있는가. 3분할은 에움길이 1/3 칸일 때, 2분할은 넓은 칸일 때다. */
    fun fits(window: WindowClass): Boolean = needsThird == (window == WindowClass.THIRD)

    companion object {
        fun of(key: String?): DriveLayout = entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: SPLIT
    }
}

internal fun currentDriveLayout(): DriveLayout = DriveLayout.of(MyDrives.driveLayout)

/** 설정의 "주행 화면" 아래 설명. 고른 값은 출발할 때 처음 골라져 있는 배치다. */
const val DRIVE_LAYOUT_NOTE = "출발할 때 그림을 보고 다시 고를 수 있습니다. 여기서 고른 배치가 처음 골라져 있습니다"

const val START_CHOICE_TITLE = "어떤 화면으로 달릴까요"
const val START_CHOICE_BUTTON = "이 화면으로 출발"
const val START_CHOICE_CLOSE = "닫기"
const val SPLIT_DRAG = "오른쪽 끝 ⋮을 왼쪽으로 끌어 셋으로 나누면 출발합니다"
const val UNSPLIT_DRAG = "⋮을 오른쪽 끝까지 끌어 2분할로 돌아가면 출발합니다"

/** 고른 배치로 가려면 사용자가 화면 나눔을 바꿔야 할 때의 안내. 지금 창에서 바로 출발하면 null 이다. */
fun dragHint(layout: DriveLayout, window: WindowClass): String? = when {
    layout.fits(window) -> null
    layout == DriveLayout.NAVI -> UNSPLIT_DRAG
    else -> SPLIT_DRAG
}

/** 고른 배치로 지금 출발할 것. */
data class ReadyStart(val request: StartRequest, val layout: DriveLayout)

/**
 * 고른 배치로 기다리던 출발을 지금 시작할 수 있으면 꺼낸다. 창이 그 배치에 맞고(사용자가 나누거나 되돌림) 같은 정차 회차일 때다.
 * 돌려주는 [Nav]에서는 안내가 지워져 있어, 폭 변화가 여러 번 들어와도 한 번만 출발한다.
 * 에움길이 가려진 사이 운전하고 다시 정차했으면([stop]이 다름) 지난 안내는 버린다. 고르지 않은 코스가 출발하지 않는다.
 */
fun takeGuide(nav: Nav, window: WindowClass, parked: Boolean, stop: Int): Pair<Nav, ReadyStart?> {
    val request = nav.splitGuide ?: return nav to null
    if (request.stop != stop) return nav.copy(splitGuide = null, startLayout = null) to null
    val layout = nav.startLayout ?: return nav to null
    if (!layout.fits(window) || !parked) return nav to null
    return nav.copy(splitGuide = null, startLayout = null) to ReadyStart(request, layout)
}
