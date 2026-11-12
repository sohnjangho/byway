package com.byway.ui

import com.byway.service.MyDrives

/**
 * 코스를 달릴 때의 화면 배치(주행 화면 3분할 설계 1절). PM이 설정에서 고르게 했다.
 * [SPLIT]은 왼쪽 차량 정보, 가운데 내비, 오른쪽 에움길이다. [NAVI]는 내비가 앱 칸(2/3)을 쓴다.
 * 저장값은 이름의 소문자("split", "navi")다.
 */
enum class DriveLayout(val title: String) {
    SPLIT("3분할 · 내비 가운데"),
    NAVI("내비 2/3 화면");

    companion object {
        fun of(key: String?): DriveLayout = entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: SPLIT
    }
}

internal fun currentDriveLayout(): DriveLayout = DriveLayout.of(MyDrives.driveLayout)

const val DRIVE_LAYOUT_NOTE = "3분할은 왼쪽 차량 정보, 가운데 내비, 오른쪽 에움길입니다"

/** 출발 버튼을 눌렀을 때 할 일. [GUIDE]는 화면을 셋으로 나누라고 안내하고 나뉠 때까지 기다린다. */
enum class StartPlan { GO, GUIDE }

/** 앱은 화면을 직접 나눌 수 없다. 3분할 설정인데 에움길이 1/3 칸이 아니면 사용자가 나누도록 안내한다. */
fun startPlan(layout: DriveLayout, window: WindowClass): StartPlan =
    if (layout == DriveLayout.SPLIT && window != WindowClass.THIRD) StartPlan.GUIDE else StartPlan.GO

const val SPLIT_GUIDE_TITLE = "주행 화면을 셋으로 나눕니다"
const val SPLIT_GUIDE_BODY = "오른쪽 끝 ⋮을 왼쪽으로 끌면 화면이 셋으로 나뉘고 바로 출발합니다"
const val SPLIT_GUIDE_PLAIN = "나누지 않고 출발"
const val SPLIT_GUIDE_CLOSE = "닫기"

/**
 * 안내 중인 출발을 지금 시작할 수 있으면 꺼낸다. 에움길 창이 1/3이 되었고(사용자가 나눔) 같은 정차 회차일 때다.
 * 돌려주는 [Nav]에서는 안내가 지워져 있어, 폭 변화가 여러 번 들어와도 한 번만 출발한다.
 * 에움길이 가려진 사이 운전하고 다시 정차했으면([stop]이 다름) 지난 안내는 버린다. 고르지 않은 코스가 출발하지 않는다.
 */
fun takeGuide(nav: Nav, window: WindowClass, parked: Boolean, stop: Int): Pair<Nav, StartRequest?> {
    val request = nav.splitGuide ?: return nav to null
    if (request.stop != stop) return nav.copy(splitGuide = null) to null
    if (window != WindowClass.THIRD || !parked) return nav to null
    return nav.copy(splitGuide = null) to request
}
