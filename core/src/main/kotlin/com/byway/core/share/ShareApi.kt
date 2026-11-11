package com.byway.core.share

import kotlinx.serialization.Serializable

// 앱과 서버가 함께 쓰는 공유 API 자료형. 바꾸면 양쪽이 같이 바뀐다(공유 설계 6-4).

/** 공유 범위. */
object Visibility {
    const val PUBLIC = "public"
    const val PARTY = "party"
    const val LINK = "link"
    val ALL = listOf(PUBLIC, PARTY, LINK)
}

@Serializable
data class DeviceToken(val token: String)

/** [body]는 `writeCourse`로 쓴 코스 형식 v2 JSON 이다. */
@Serializable
data class UploadCourse(val visibility: String, val partyCode: String? = null, val body: String)

@Serializable
data class Uploaded(val id: String, val code: String?)

/** 서버에 있는 공유 코스. 파티 코스와 남이 올린 링크 코스는 [code]가 없다. */
@Serializable
data class SharedCourse(
    val id: String,
    val code: String?,
    val visibility: String,
    val finishes: Int,
    val mine: Boolean,
    val body: String,
)

@Serializable
data class Problem(val message: String)

/** 파티에서 보이는 나. 실명 대신 닉네임(최대 10자)과 앱에 든 아바타 그림 하나다. */
@Serializable
data class Profile(val nickname: String, val avatar: String)

const val NICKNAME_MAX = 10
val AVATARS = (1..10).map { "avatar-%02d".format(it) }

/** [courseId]가 없으면 코스 없이 연다(함께달리기 설계 6-2). 있으면 열자마자 그 코스를 제안 1번으로 고른 것과 같다. */
@Serializable
data class NewParty(val courseId: String? = null, val name: String, val profile: Profile)

/**
 * [starred]와 [sharing]은 [me]일 때만 의미가 있다. [demo]는 시연용 가상 멤버다.
 * [leader]는 이 사람이 리더라는 뜻이고, [accepted]는 리더가 지금 고른 코스를 승낙했다는 뜻이다(함께달리기 설계 6-3).
 */
@Serializable
data class PartyMember(
    val id: String,
    val nickname: String,
    val avatar: String,
    val demo: Boolean,
    val me: Boolean,
    val sharing: Boolean,
    val starred: Boolean,
    val leader: Boolean = false,
    val accepted: Boolean = false,
)

/** [ageMs]는 서버가 그 위치를 받은 뒤 지난 시간이다. 앱은 30초가 넘은 핀을 그리지 않는다. */
@Serializable
data class PartyPin(val memberId: String, val lat: Double, val lon: Double, val ageMs: Long = 0)

/**
 * [pins]에는 60초 안에 받은 위치만 온다. [owner]는 내가 리더라는 뜻이다.
 * [courseId]는 리더가 지금 고른 코스이고, [proposal]은 그 제안 번호다(0이면 아직 고르지 않음).
 * [joined]가 false 면 다시 모이기로 열린 파티에 아직 들어가지 않은 것이다.
 */
@Serializable
data class PartyView(
    val code: String,
    val name: String,
    val courseId: String?,
    val open: Boolean,
    val owner: Boolean,
    val members: List<PartyMember>,
    val pins: List<PartyPin>,
    val proposal: Int = 0,
    val leaderName: String = "",
    val joined: Boolean = true,
)

@Serializable
data class Position(val lat: Double, val lon: Double)

@Serializable
data class Toggle(val on: Boolean)

/** 코스 없이 다시 모일 수 있다. */
@Serializable
data class Reopen(val courseId: String? = null)

/** 리더가 고른 코스. */
@Serializable
data class Propose(val courseId: String)

/** [proposal]번 제안에 대한 답. 거부는 파티를 나가는 것이다. */
@Serializable
data class Answer(val proposal: Int, val accept: Boolean)

/** 리더 넘기기·내보내기의 대상. */
@Serializable
data class MemberRef(val memberId: String)
