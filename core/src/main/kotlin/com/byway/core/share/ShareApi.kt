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

@Serializable
data class NewParty(val courseId: String, val name: String, val profile: Profile)

/** [starred]와 [sharing]은 [me]일 때만 의미가 있다. [demo]는 시연용 가상 멤버다. */
@Serializable
data class PartyMember(
    val id: String,
    val nickname: String,
    val avatar: String,
    val demo: Boolean,
    val me: Boolean,
    val sharing: Boolean,
    val starred: Boolean,
)

@Serializable
data class PartyPin(val memberId: String, val lat: Double, val lon: Double)

/** [pins]에는 60초 안에 받은 위치만 온다. */
@Serializable
data class PartyView(
    val code: String,
    val name: String,
    val courseId: String,
    val open: Boolean,
    val owner: Boolean,
    val members: List<PartyMember>,
    val pins: List<PartyPin>,
)

@Serializable
data class Position(val lat: Double, val lon: Double)

@Serializable
data class Toggle(val on: Boolean)

@Serializable
data class Reopen(val courseId: String)
