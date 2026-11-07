package com.byway

import com.byway.core.drive.DriveFeature
import com.byway.feature.ambience.AmbienceFeature

/** 부가 기능 목록. 켜고 끄는 유일한 곳이다. 줄마다 기능 하나만 쓴다. */
object FeatureRegistry {
    fun create(): List<DriveFeature> = listOf<DriveFeature>(
        AmbienceFeature(),
    )
}
