package com.byway.service

import android.content.Context
import com.byway.core.geo.GeoPoint
import com.byway.core.share.PartyPin
import com.byway.core.share.PartyView
import com.byway.core.share.Position
import com.byway.core.share.PendingClear

/** 2초마다 한 번 파티와 주고받은 결과. */
sealed interface PartyTick {
    data class Seen(val view: PartyView) : PartyTick

    /** 파티가 끝났거나 내가 빠졌다(서버 404). 핀을 지우고 파티 상태를 비운다. */
    data object Ended : PartyTick

    /** 서버에 닿지 못했다. 파티는 그대로 두고 핀만 지운다. */
    data object Unreachable : PartyTick
}

/** 위치 공유가 켜져 있고 위치가 있으면 내 위치를 보내고, 파티를 받는다(공유 설계 6-5). 동의 전에는 위치를 보내지 않는다. */
fun partyTick(me: GeoPoint?, sharing: Boolean, send: (Position) -> Result<Unit>, fetch: () -> Result<PartyView>): PartyTick {
    if (sharing && me != null) send(Position(me.lat, me.lon))
    val got = fetch()
    // 끝난 파티는 별표한 멤버에게도 끝난 것이다. 다시 모인 파티에 아직 들어가지 않았어도 그렇다. 둘 다 파티 밖 화면에서 다시 들어간다.
    got.getOrNull()?.let { return if (it.open && it.joined) PartyTick.Seen(it) else PartyTick.Ended }
    return if ((got.exceptionOrNull() as? ShareError.Http)?.code == 404) PartyTick.Ended else PartyTick.Unreachable
}

private const val STALE_MS = 30_000L

/** 서버에 잠깐 닿지 못해도 마지막으로 받은 파티를 받은 뒤 30초까지 그대로 둔다. 주행 중 확대 지도가 깜빡이지 않는다. */
fun keptParty(previous: PartyView?, seenAt: Long?, now: Long): PartyView? =
    previous?.takeIf { seenAt != null && now - seenAt <= STALE_MS }

/** 지도에 그릴 핀. 내 핀과 30초 넘게 소식이 없는 핀은 뺀다. */
fun livePins(view: PartyView): List<PartyPin> {
    val mine = view.members.filter { it.me }.map { it.id }.toSet()
    return view.pins.filter { it.memberId !in mine && it.ageMs <= STALE_MS }
}

/** 지금 들어가 있는 파티 코드. 이 차에만 둔다. */
object MyParty {
    private const val PREFS = "share"
    private const val CODE = "party"

    fun code(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CODE, null)

    fun set(context: Context, code: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply { if (code == null) remove(CODE) else putString(CODE, code) }.apply()
    }

    /** 보내지 못한 코스 비우기("코드:제안 번호"). 정차 중 다시 보낸다(검토 I-2). */
    fun pendingClear(context: Context): PendingClear? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PENDING, null)?.split(":")?.let { parts ->
            parts.getOrNull(1)?.toIntOrNull()?.let { PendingClear(parts[0], it) }
        }

    fun setPendingClear(context: Context, pending: PendingClear?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (pending == null) remove(PENDING) else putString(PENDING, "${pending.code}:${pending.proposal}")
        }.apply()
    }

    private const val PENDING = "pending_clear"
}
