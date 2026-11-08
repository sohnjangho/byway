package com.byway.ui

/**
 * 치수와 글자 크기. 화면 코드에 숫자를 흩어 놓지 않는다. 글자는 24sp 아래로 내리지 않는다.
 * 토큰에 있는 값은 [Tokens]에서 받는다. 여기 숫자로 적힌 것은 앱 배치에만 쓰는 값이다.
 */
internal object Dimens {
    const val DRIVE_NUMBER = Tokens.TEXT_DRIVE_NUMBER
    const val DRIVE_NUMBER_SPACING = -0.03f
    const val DRIVE_SECTION = Tokens.TEXT_DRIVE_SECTION
    const val DRIVE_UNIT = Tokens.TEXT_DRIVE_UNIT
    const val DRIVE_LINE = Tokens.TEXT_DRIVE_LINE

    const val TITLE = Tokens.TEXT_TITLE
    const val VALUE = Tokens.TEXT_VALUE
    const val HEADING = Tokens.TEXT_HEADING
    const val BODY = Tokens.TEXT_BODY
    const val SMALL = Tokens.TEXT_SMALL
    const val LABEL = Tokens.TEXT_LABEL
    const val BRAND = Tokens.TEXT_BRAND

    const val SPACE_2 = Tokens.SPACE_2
    const val SPACE_3 = Tokens.SPACE_3
    const val SPACE_4 = Tokens.SPACE_4
    const val SPACE_6 = Tokens.SPACE_6
    const val SPACE_8 = Tokens.SPACE_8
    const val SPACE_10 = Tokens.SPACE_10

    const val RADIUS_SM = Tokens.RADIUS_SM
    const val RADIUS_MD = Tokens.RADIUS_MD
    const val RADIUS_LG = Tokens.RADIUS_LG

    const val TOUCH_MIN = Tokens.TOUCH_MIN
    const val BUTTON_LARGE = Tokens.BUTTON_LARGE
    const val BUTTON_MEDIUM = Tokens.BUTTON_MEDIUM
    const val BUTTON_SMALL = Tokens.BUTTON_SMALL
    const val PICK_HEIGHT = Tokens.PICK_HEIGHT
    const val BUTTON_PADDING = 32
    const val HAIRLINE = 1
    const val CREDIT_PADDING = 4

    const val COURSE_LINE = Tokens.COURSE_LINE.toFloat()
    const val COURSE_LINE_THUMB = Tokens.COURSE_LINE_THUMB.toFloat()
    const val COURSE_OUTLINE = 4f
    const val STOP_RADIUS = 7f
    const val CAR_RADIUS = 13f

    /** 주간은 그림자로 카드를 띄우고 야간은 면 색으로 나눈다. */
    const val CARD_SHADOW_DAY = 6
    const val CARD_SHADOW_NIGHT = 2

    const val RAIL_WIDTH = 156
    const val RAIL_ITEM_HEIGHT = 88
    const val RAIL_TOP_HEIGHT = 92

    const val SIDE_WIDTH = 620
    const val SIDE_WIDTH_FULL = 1120
    const val SIDE_INFO_WIDTH = 540
    const val SIDE_BOTTOM = 152
    const val SIDE_NAME = 68f
    const val SIDE_NAME_THIRD = 52f
    const val SOFT_WIDTH = 760
    const val SOFT_WIDTH_FULL = 1240
    const val SOFT_HEIGHT = 640
    const val THIRD_SCENE_HEIGHT = 732
    const val SCENE_CREDIT_BOTTOM = 176
    const val SCENE_CREDIT_WIDTH = 760
    const val THIRD_SOFT_HEIGHT = 520
    const val STRIP_CARD_WIDTH = 284
    const val STRIP_CARD_HEIGHT = 112
    const val STRIP_THUMB = 96
    const val PLACE_NUMBER = 36
    const val AVATAR_SMALL = 60
    const val AVATAR_LARGE = 104

    const val TEXT_PANE_WIDTH = 640
    const val PICTURE_NARROW_HEIGHT = 220
    const val SUMMARY_PICTURE_HEIGHT = 320

    const val HERO_HEIGHT = 420
    const val HERO_WEIGHT = 1030f
    const val TOP_LIST_WEIGHT = 548f
    const val HERO_WELL_HEIGHT = 130
    const val THUMB = 88
    const val THEME_TILE_HEIGHT = 104
    const val SHELF_HEIGHT = 320
    const val GRID_CELL = 320

    const val RANK_NUMBER_WIDE = 88
    const val RANK_NUMBER_NARROW = 72
    const val RANK_COVER_WIDE_W = 170
    const val RANK_COVER_WIDE_H = 150
    const val RANK_COVER_NARROW = 120

    const val PROFILE_WIDTH = 500
    const val MINE_SHELF_HEIGHT = 380
    const val RECORD_CELL_HEIGHT = 112
    const val TOGGLE_WIDTH = 84
    const val TOGGLE_HEIGHT = 48
    const val TOGGLE_KNOB = 36
    const val TOGGLE_INSET = 6

    const val CHART_HEIGHT = 100
    const val CHART_NARROW_HEIGHT = 80
    const val SECTION_ROW_HEIGHT = 44
    const val DOT = 16

    const val DRIVE_EDGE = 48
    const val DRIVE_BAR_HEIGHT = 10
    const val CARD_BAR_HEIGHT = 6
    const val BAR_GAP = 4
}
