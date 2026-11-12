package com.byway.core.engine

data class DriveTuning(
    /** 에뮬레이터 실측: 이보다 촘촘하면 내비가 경유지를 맞추려고 돌아가 코스보다 2배 넘게 길어진다. */
    val simplifyToleranceMeters: Double = 1_200.0,
    /** 지점 사이는 내비가 길을 고른다. 작은 이탈은 내비가 복귀시키므로 허용 오차를 넘을 때만 개입한다. */
    val offCourseMeters: Double = 1_300.0,
    val offCourseFixes: Int = 3,
    val lookaheadMeters: Double = 3_000.0,
)
