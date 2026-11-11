package com.byway.ui

/*
 * 확대 지도 조각의 주소와 밤 색(주행 화면 홈 맞춤 설계 2절).
 * 장면 그림을 만든 tools/make-covers.py 와 같은 층, 같은 밤 변환을 써서 홈 지도와 확대 지도가 같아 보이게 한다.
 */

private const val TILE_ROOT = "https://tiles.maps.eox.at/wmts/1.0.0"

// make-covers.py 의 night_map 값.
private const val MAP_PAPER = 246
private const val MAP_LAND = 26
private const val MAP_ROAD_SLOPE = 0.7
private const val MAP_DARKEST = 14
private const val MAP_BRIGHTEST = 84
private const val WATER_FROM = 5
private const val WATER_SPAN = 10

// make-covers.py 의 darkened 값. 대비 기준값은 도구가 그림마다 평균을 쓰지만, 조각마다 다르면 이음매가 생겨 장면 15개 평균으로 고정한다.
private const val NIGHT_BRIGHTNESS = 0.62
private const val NIGHT_CONTRAST = 1.12
private const val SAT_PIVOT = 45.0

/** 지도 보기 설정에 맞는 조각 주소. 위성은 CC BY 4.0인 2016년판이다(docs/권리점검.md). */
fun tileUrl(style: MapStyle, z: Int, x: Int, y: Int): String {
    val layer = if (style == MapStyle.MAP) "terrain-light_3857" else "s2cloudless_3857"
    return "$TILE_ROOT/$layer/default/g/$z/$y/$x.jpg"
}

private fun red(c: Int) = c shr 16 and 0xFF
private fun green(c: Int) = c shr 8 and 0xFF
private fun blue(c: Int) = c and 0xFF
private fun opaque(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

/** 일반 지도의 밤 화소. 물은 앱 밤 바탕색 [bg]로, 땅은 조금 밝게, 도로는 더 밝게 한다. */
fun nightMapPixel(rgb: Int, bg: Int): Int {
    val r = red(rgb)
    val g = green(rgb)
    val b = blue(rgb)
    val water = ((b - r).coerceIn(0, 255) - WATER_FROM).let { Math.floorDiv(it * 255, WATER_SPAN) }.coerceIn(0, 255)
    // 파이썬 PIL 의 회색값과 round(짝수 반올림)를 그대로 따른다.
    val gray = (r * 19595 + g * 38470 + b * 7471 + 0x8000) shr 16
    val lift = Math.rint(MAP_LAND + (MAP_PAPER - gray) * MAP_ROAD_SLOPE).toInt().coerceIn(MAP_DARKEST, MAP_BRIGHTEST)
    fun mix(base: Int): Int {
        val land = minOf(255, lift + base)
        return (base * water + land * (255 - water) + 127) / 255
    }
    return opaque(mix(red(bg)), mix(green(bg)), mix(blue(bg)))
}

/** 위성 지도의 밤 화소. 어둡게 한 뒤 대비를 조금 올린다. */
fun nightSatPixel(rgb: Int): Int {
    fun dark(c: Int) = Math.rint((c * NIGHT_BRIGHTNESS - SAT_PIVOT) * NIGHT_CONTRAST + SAT_PIVOT).toInt().coerceIn(0, 255)
    return opaque(dark(red(rgb)), dark(green(rgb)), dark(blue(rgb)))
}
