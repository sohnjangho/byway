/* tools/make-theme.py 가 design/tokens.json 에서 만든 파일이다. 직접 고치지 않는다. */
package com.byway.ui

/** 지금 테마의 색. 화면은 색 값을 직접 쓰지 않고 이것만 읽는다. */
data class Palette(
    val bg: Int,
    val bgDeep: Int,
    val bgRaised: Int,
    val bgElevated: Int,
    val bgPressed: Int,
    val line: Int,
    val lineSubtle: Int,
    val ink: Int,
    val inkSecondary: Int,
    val inkMuted: Int,
    val accent: Int,
    val accentPressed: Int,
    val accentSoft: Int,
    val onAccent: Int,
    val autonomy: Int,
    val sectionForest: Int,
    val sectionCity: Int,
    val sectionRiver: Int,
    val airForest: Int,
    val airCity: Int,
    val airRiver: Int,
    val photoScrim: Int,
    val onPhoto: Int,
    val softWash: Int,
)

val NightPalette = Palette(
    bg = 0xFF131417.toInt(),
    bgDeep = 0xFF0D0E10.toInt(),
    bgRaised = 0xFF1D1E21.toInt(),
    bgElevated = 0xFF26282D.toInt(),
    bgPressed = 0xFF313236.toInt(),
    line = 0xFF44464E.toInt(),
    lineSubtle = 0x14FFFFFF,
    ink = 0xFFF7F8FA.toInt(),
    inkSecondary = 0xFFC7C9CE.toInt(),
    inkMuted = 0xFFA4A8AE.toInt(),
    accent = 0xFFF0A45A.toInt(),
    accentPressed = 0xFFD4883E.toInt(),
    accentSoft = 0x26F0A45A,
    onAccent = 0xFF181006.toInt(),
    autonomy = 0xFF6A57FF.toInt(),
    sectionForest = 0xFF6EE798.toInt(),
    sectionCity = 0xFF8CA8F9.toInt(),
    sectionRiver = 0xFF4FD1E8.toInt(),
    airForest = 0xFF0F1C18.toInt(),
    airCity = 0xFF111827.toInt(),
    airRiver = 0xFF0D1D25.toInt(),
    photoScrim = 0xCC0D0E10.toInt(),
    onPhoto = 0xFFF7F8FA.toInt(),
    softWash = 0x8C131417.toInt(),
)

val DayPalette = Palette(
    bg = 0xFFF7F8FA.toInt(),
    bgDeep = 0xFFEDEEF2.toInt(),
    bgRaised = 0xFFFFFFFF.toInt(),
    bgElevated = 0xFFFFFFFF.toInt(),
    bgPressed = 0xFFEDEEF2.toInt(),
    line = 0xFFE0E1E6.toInt(),
    lineSubtle = 0x12131417,
    ink = 0xFF131417.toInt(),
    inkSecondary = 0xFF44464E.toInt(),
    inkMuted = 0xFF686A72.toInt(),
    accent = 0xFFA85E14.toInt(),
    accentPressed = 0xFF8F4F10.toInt(),
    accentSoft = 0x1FA85E14,
    onAccent = 0xFFFFFFFF.toInt(),
    autonomy = 0xFF5A46FA.toInt(),
    sectionForest = 0xFF24753D.toInt(),
    sectionCity = 0xFF3B528F.toInt(),
    sectionRiver = 0xFF167487.toInt(),
    airForest = 0xFFE8F4EC.toInt(),
    airCity = 0xFFE9EEF9.toInt(),
    airRiver = 0xFFE4F3F6.toInt(),
    photoScrim = 0xCC0D0E10.toInt(),
    onPhoto = 0xFFF7F8FA.toInt(),
    softWash = 0x8CF7F8FA.toInt(),
)

/** 글자 크기(sp)와 길이(dp). */
object Tokens {
    const val SPACE_2 = 8
    const val SPACE_3 = 12
    const val SPACE_4 = 16
    const val SPACE_6 = 24
    const val SPACE_8 = 32
    const val SPACE_10 = 40
    const val RADIUS_SM = 16
    const val RADIUS_MD = 24
    const val RADIUS_LG = 32
    const val RADIUS_WINDOW = 40
    const val WINDOW_WIDTH = 1700
    const val WINDOW_HEIGHT = 1184
    const val WINDOW_SPLIT_WIDTH = 840
    const val WINDOW_FULL_WIDTH = 2560
    const val WINDOW_TALL_HEIGHT = 1210
    const val TOUCH_MIN = 76
    const val BUTTON_LARGE = 100
    const val BUTTON_MEDIUM = 80
    const val BUTTON_SMALL = 64
    const val PICK_HEIGHT = 44
    const val COURSE_LINE = 6
    const val COURSE_LINE_THUMB = 3
    const val TEXT_DRIVE_NUMBER = 112f
    const val TEXT_DRIVE_UNIT = 36f
    const val TEXT_DRIVE_SECTION = 40f
    const val TEXT_DRIVE_LINE = 32f
    const val TEXT_TITLE = 48f
    const val TEXT_VALUE = 40f
    const val TEXT_HEADING = 36f
    const val TEXT_BODY = 28f
    const val TEXT_SMALL = 26f
    const val TEXT_LABEL = 24f
    const val TEXT_BRAND = 40f
}
