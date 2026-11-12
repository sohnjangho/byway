package com.byway.core.engine

data class DriveTuning(
    /** 에뮬레이터 실측: 이보다 촘촘하면 내비가 경유지를 맞추려고 돌아가 코스보다 2배 넘게 길어진다. */
    val simplifyToleranceMeters: Double = 1_200.0,
    /** 지점 사이는 내비가 길을 고른다. 작은 이탈은 내비가 복귀시키므로 허용 오차를 넘을 때만 개입한다. */
    val offCourseMeters: Double = 1_300.0,
    val offCourseFixes: Int = 3,
    val lookaheadMeters: Double = 3_000.0,
    /** 목적지에 닿기 전에 다음 경로로 잇는 거리. 내비가 "목적지 도착"으로 안내를 끝내지 않게 한다. */
    val legSwapAheadMeters: Double = 1_000.0,
    /** 벗어났을 때 묻고 답을 기다리는 위치 횟수. 위치는 달리는 동안 1초에 한 번쯤 온다. */
    val askWaitFixes: Int = 15,
    /** 복귀 지점은 차 옆 자리에서 이만큼 앞이다. 바로 뒤를 가리켜 유턴시키지 않고, 멀리 건너뛰지도 않는다. */
    val rejoinAheadMeters: Double = 500.0,
    /** 복귀는 이탈 기준보다 안쪽으로 들어와 연속으로 잡혀야 인정한다. 경계를 오가며 안내가 되풀이되지 않게 한다. */
    val rejoinMeters: Double = 300.0,
    val rejoinFixes: Int = 2,
    /** 탐색 범위 밖에서 코스를 만났을 때, 코스를 따라 이만큼 나아가야 합류로 본다. 코스를 가로지르는 길과 구별한다. */
    val joinAlongMeters: Double = 300.0,
    /** 복귀 기준 밖이라도 허용 오차 안에 이만큼 연속으로 있으면 코스 위로 본다. 내비가 나란한 큰길로 가는 경우다. */
    val settleFixes: Int = 30,
    /** 종점에서 이 안에 세우면 끝까지 달린 것으로 본다. */
    val finishRadiusMeters: Double = 300.0,
    /** 연속한 두 위치가 이보다 멀면 그 사이 위치가 끊긴 것으로 보고 넓게 다시 찾는다. */
    val gapJumpMeters: Double = 500.0,
)
