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
