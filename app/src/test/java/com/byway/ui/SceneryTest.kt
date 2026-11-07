package com.byway.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneryTest {
    @Test
    fun aPhotoWithItsCreditIsShown() {
        assertEquals("c1.jpg", sceneryFile("c1", "사진 작가 (CC BY 4.0)", setOf("c1.jpg")))
    }

    /** 출처를 밝힐 수 없는 사진은 쓰지 않는다. */
    @Test
    fun aPhotoWithoutACreditIsNotShown() {
        assertNull(sceneryFile("c1", "", setOf("c1.jpg")))
    }

    @Test
    fun aCreditWithoutAPhotoShowsNothing() {
        assertNull(sceneryFile("c1", "사진 작가 (CC BY 4.0)", emptySet()))
    }

    @Test
    fun aCourseWithAPhotoOffersScenery() {
        assertTrue(Picture.SCENERY in picturesFor(hasScenery = true))
    }

    @Test
    fun aCourseWithoutAPhotoDoesNotOfferScenery() {
        assertFalse(Picture.SCENERY in picturesFor(hasScenery = false))
    }

    @Test
    fun theSoftBackgroundIsTheCoursesOwnFile() {
        assertEquals("c1-soft.jpg", softFile("c1", setOf("c1-soft.jpg", "c1.jpg")))
    }

    @Test
    fun aCourseWithoutASoftFileHasNone() {
        assertNull(softFile("c1", setOf("c1.jpg")))
    }
}
