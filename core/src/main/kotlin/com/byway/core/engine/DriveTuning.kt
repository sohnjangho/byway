package com.byway.core.engine

data class DriveTuning(
    val simplifyToleranceMeters: Double = 300.0,
    val offCourseMeters: Double = 150.0,
    val offCourseFixes: Int = 3,
    val lookaheadMeters: Double = 3_000.0,
)
