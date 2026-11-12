package com.byway.ui

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.byway.service.CourseCatalog
import com.byway.service.CourseStore
import com.byway.service.MyDrives
import com.byway.service.MyParty
import com.byway.service.MyProfile
import com.byway.service.Share
import com.byway.service.ShareSync

/**
 * 설정. 지도 보기, 주행 화면 배치, 달릴 때 켜고 끄는 것, 앱 정보, 내 기록 지우기를 한 화면에 둔다. 대화상자는 쓰지 않는다.
 * 넓은 창은 오른쪽에 지금 고른 지도가 보인다. [onChanged]는 지도 종류가 바뀌어 화면을 다시 그려야 할 때 부른다.
 */
internal fun settingsScreen(context: Context, kit: BrowseKit, onAbout: () -> Unit, onChanged: () -> Unit): View = with(context) {
    val third = kit.window == WindowClass.THIRD
    val full = kit.window == WindowClass.FULL
    FrameLayout(this).apply {
        setBackgroundColor(palette.bg)
        if (!third) {
            val shade = if (full) AtlasShade(false, 1220f, 1440f) else AtlasShade(false, 700f, 920f)
            val atlas = AtlasView(context)
            atlas.show(Tab.BROWSE, kit.window, kit.book.courses, null, null, night, shade)
            atlas.addCredit()
            addView(atlas, matchFrame())
        }
        val side = column().apply {
            isClickable = true
            val edge = dp(if (third) Dimens.SPACE_6 else Dimens.SPACE_8)
            setPadding(edge, if (third) dp(Dimens.SPACE_4) else edge, edge, edge)
            addView(label("설정", Dimens.HEADING, bold = true))
            addView(label("정차 중에만 바꿀 수 있습니다", Dimens.LABEL, palette.inkSecondary).atMostLines(1))
            addSpaced(group("지도 보기", mapChoice(onChanged)), Dimens.SPACE_8)
            addSpaced(label("일반 지도는 길과 물이 또렷하고, 위성은 실제 땅 모습이 보입니다", Dimens.LABEL, palette.inkSecondary), Dimens.SPACE_2)
            addSpaced(group("주행 화면", layoutChoice()), Dimens.SPACE_8)
            addSpaced(label(DRIVE_LAYOUT_NOTE, Dimens.LABEL, palette.inkSecondary), Dimens.SPACE_2)
            addSpaced(group("코스를 달릴 때", driveToggles(full)), Dimens.SPACE_8)
            addSpaced(group("정보와 기록", recordButtons(onAbout)), Dimens.SPACE_8)
            addSpaced(label("내 기록 지우기는 달린 기록, 만든 코스, 받은 코스, 프로필을 이 차와 공유 서버에서 지웁니다", Dimens.LABEL, palette.inkSecondary), Dimens.SPACE_2)
        }
        val sideSize = if (third) {
            FrameLayout.LayoutParams(MATCH, MATCH).apply { topMargin = dp(Dimens.RAIL_TOP_HEIGHT) }
        } else {
            FrameLayout.LayoutParams(dp(if (full) Dimens.SIDE_WIDTH_FULL else Dimens.SIDE_WIDTH), MATCH).apply { leftMargin = dp(Dimens.RAIL_WIDTH) }
        }
        addView(side, sideSize)
    }
}

private fun Context.group(title: String, body: View): View = column().apply {
    addView(label(title, Dimens.SMALL, bold = true))
    addSpaced(body, Dimens.SPACE_2)
}

private fun Context.mapChoice(onChanged: () -> Unit): View =
    chipRow(MapStyle.entries.map { Choice(it.name, it.title) }, currentMapStyle().name, Dimens.SMALL) { key ->
        MyDrives.mapStyle = key.lowercase()
        onChanged()
    }

/** 코스를 달릴 때의 화면 배치. 바꿔도 지금 화면은 그대로라 다시 그리지 않는다. */
private fun Context.layoutChoice(): View =
    chipRow(DriveLayout.entries.map { Choice(it.name, it.title) }, currentDriveLayout().name, Dimens.SMALL) { key ->
        MyDrives.driveLayout = key.lowercase()
    }

private fun Context.driveToggles(full: Boolean): View {
    val cells = listOf(
        toggleRow("차 안 연출", MyDrives.ambienceOn) { MyDrives.ambienceOn = it },
        toggleRow("음성 안내", MyDrives.voiceOn) { MyDrives.voiceOn = it },
        toggleRow("음악 앱 재생", MyDrives.musicOn) { MyDrives.musicOn = it },
        toggleRow("분위기 효과", MyDrives.moodOn) { MyDrives.moodOn = it },
    )
    return grid(cells, if (full) 4 else 2, dp(Dimens.TOUCH_MIN), gap = Dimens.SPACE_2)
}

private fun Context.recordButtons(onAbout: () -> Unit): View = LinearLayout(this).apply {
    var step = 0
    lateinit var clear: Button
    clear = quietButton(clearLabel(step)) {
        if (clearsOnPress(step)) clearMyData(context)
        step = nextClearStep(step)
        clear.text = clearLabel(step)
    }
    addView(quietButton("앱 정보", onAbout), LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = dp(Dimens.SPACE_3) })
    addView(clear, LinearLayout.LayoutParams(0, WRAP, 1f))
}

/** 이 차의 내 데이터를 지운다(공유 설계 6-2). 서버에는 내 코스·파티 멤버 정보·위치 삭제를 요청한다. */
private fun clearMyData(context: Context) {
    MyDrives.clearRecords(context)
    CourseStore.clearAll(context)
    MyProfile.set(context, null)
    MyParty.set(context, null)
    CourseCatalog.invalidate()
    // 서버 자료는 서버에 닿을 때 지운다. 실패하면 다음에 공유 서버에 닿을 때 다시 지운다(ShareSync).
    if (Share.available) ShareSync.deleteMeLater(context)
}
