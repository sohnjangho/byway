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
