package com.byway.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1/3 칸은 지도가 주인공이다. 지도를 키우고 아래 글은 줄인다(화면 다듬기 설계 2절). */
class ThirdLayoutTest {
    @Test
    fun thirdMapIsTheMainPart() {
        assertEquals(752, Dimens.THIRD_DRIVE_SCENE_HEIGHT)
        assertEquals(800, Dimens.THIRD_SCENE_HEIGHT)
        assertTrue(Dimens.THIRD_DRIVE_SCENE_HEIGHT * 2 > Tokens.WINDOW_TALL_HEIGHT)
    }

    /** 줄여도 남은 거리 숫자가 가장 크고, 가장 작은 글 토큰보다 작아지지 않는다. */
    @Test
    fun thirdDriveNumberStaysLargest() {
        assertTrue(Dimens.THIRD_DRIVE_NUMBER > Dimens.THIRD_DRIVE_NAME)
        assertTrue(Dimens.THIRD_DRIVE_NAME > Dimens.THIRD_DRIVE_SECTION)
        assertTrue(Dimens.THIRD_DRIVE_SECTION >= Dimens.LABEL)
        assertTrue(Dimens.THIRD_DRIVE_NUMBER < Dimens.DRIVE_NUMBER)
        assertTrue(Dimens.THIRD_DRIVE_NAME < Dimens.SIDE_NAME)
    }

    @Test
    fun thirdHomeNameIsSmaller() {
        assertTrue(Dimens.THIRD_NAME < Dimens.SIDE_NAME)
        assertTrue(Dimens.THIRD_NAME >= Dimens.HEADING)
    }

    /** 1/3 주행 지도는 사진 띠가 없으니 줄이지 않은 장면을 쓴다. 키운 칸 안에 경로와 번호가 다 들어간다. */
    @Test
    fun theDriveRouteFitsTheTallerMap() {
        val routeBoxBottom = 1000f
        val pin = 24f
        val rect = sceneRect(WindowClass.THIRD, 841f, 1210f)
        assertTrue(rect.top + routeBoxBottom * rect.height / 1210f + pin <= Dimens.THIRD_DRIVE_SCENE_HEIGHT)
    }
}
