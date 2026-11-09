package com.byway.platform

/** 연출이 바꾸는 차 안 항목. */
enum class CabinItem { WINDOW, FAN, LIGHT }

/**
 * 연출이 손대기 전의 값과 마지막에 쓴 값을 항목별로 기억한다.
 * 코스가 끝나면 운전자가 그 뒤로 건드리지 않은 항목만 처음 값으로 되돌린다.
 */
class CabinMemory {
    private val before = mutableMapOf<CabinItem, Int>()
    private val written = mutableMapOf<CabinItem, Int>()

    val items: Set<CabinItem> get() = before.keys.toSet()

    fun knows(item: CabinItem) = item in before

    /** [was]는 이 항목을 처음 쓰기 직전에 읽은 값이다. 두 번째부터는 처음 값을 그대로 둔다. */
    fun wrote(item: CabinItem, was: Int, value: Int) {
        before.putIfAbsent(item, was)
        written[item] = value
    }

    /** 되돌릴 값. 지금 값이 우리가 마지막에 쓴 값과 다르면 운전자가 바꾼 것이라 건드리지 않는다. */
    fun restores(item: CabinItem, now: Int): Int? {
        val original = before[item] ?: return null
        return original.takeIf { written[item] == now && original != now }
    }

    fun clear() {
        before.clear()
        written.clear()
    }

    /** 앱이 꺼졌다 켜져도 되돌릴 수 있게 글자로 남긴다. */
    fun encode(): String = before.keys.joinToString(",") { "${it.name}:${before[it]}:${written[it]}" }

    companion object {
        fun decode(text: String): CabinMemory = CabinMemory().apply {
            text.split(",").forEach { part ->
                val fields = part.split(":")
                val item = CabinItem.entries.firstOrNull { it.name == fields.getOrNull(0) } ?: return@forEach
                val was = fields.getOrNull(1)?.toIntOrNull() ?: return@forEach
                val value = fields.getOrNull(2)?.toIntOrNull() ?: return@forEach
                wrote(item, was, value)
            }
        }
    }
}
