package com.byway.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.byway.core.course.CourseFilter
import com.byway.core.course.LengthBand
import com.byway.core.course.MOOD_KEYS
import com.byway.core.course.NearRange
import com.byway.core.course.isEmpty

/**
 * 검색 칸. 글자가 바뀔 때마다 [onQuery]를 부른다. 화면을 다시 만들지 않아야 키보드와 초점이 남는다.
 * 지우기(×)는 글이 있을 때만 보인다.
 */
internal fun Context.searchField(query: String, onQuery: (String) -> Unit): View {
    val field = textField(SEARCH_HINT, SEARCH_MAX).apply {
        setText(query)
        // 가로 화면에서 키보드가 전체 화면 입력 칸을 띄우면 목록이 줄어드는 것을 볼 수 없다. 앱 화면 위에 키보드만 올린다.
        imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        setPadding(dp(Dimens.SPACE_6), 0, dp(Dimens.TOUCH_MIN + Dimens.SPACE_2), 0)
    }
    val clear = label("×", Dimens.HEADING, palette.inkSecondary).apply {
        gravity = Gravity.CENTER
        contentDescription = "검색어 지우기"
        visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
        setOnClickListener { field.setText("") }
    }
    field.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            val text = s?.toString().orEmpty()
            clear.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
            onQuery(text)
        }
    })
    field.setOnEditorActionListener { view, _, _ ->
        hideKeyboard(view as EditText)
        true
    }
    return FrameLayout(this).apply {
        addView(field, FrameLayout.LayoutParams(MATCH, dp(Dimens.TOUCH_MIN)))
        addView(clear, FrameLayout.LayoutParams(dp(Dimens.TOUCH_MIN), dp(Dimens.TOUCH_MIN), Gravity.END or Gravity.CENTER_VERTICAL))
    }
}

private fun Context.hideKeyboard(field: EditText) {
    field.clearFocus()
    (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(field.windowToken, 0)
}

/**
 * 걸러 보기 줄: 버튼 다섯 개(내 주변, 무드, 거리, 지역, 풍경)와 걸린 값이 있을 때의 "모두 풀기".
 * 고른 값이 있는 버튼은 그 값을 보이고 강조한다. 누르면 그 아래에 칩 한 줄을 펼친다(한 번에 한 줄).
 */
internal fun Context.filterBar(filter: CourseFilter, open: String?, onOpen: (String?) -> Unit, onClear: () -> Unit): View =
    HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        val line = LinearLayout(context)
        val buttons = FILTER_KINDS.map { kind ->
            val set = when (kind) {
                "near" -> filter.near != null
                "mood" -> filter.mood != null
                "length" -> filter.length != null
                "region" -> filter.region != null
                else -> filter.theme != null
            }
            val arrow = if (open == kind) " ▴" else " ▾"
            filterChip(filterButtonText(kind, filter) + arrow, set || open == kind) { onOpen(kind.takeIf { it != open }) }
        }
        val clear = if (filter.copy(query = "").isEmpty()) null else filterChip(CLEAR_FILTERS, false, onClear)
        (buttons + listOfNotNull(clear)).forEachIndexed { i, chip ->
            line.addView(chip, LinearLayout.LayoutParams(WRAP, dp(Dimens.TOUCH_MIN)).apply { if (i > 0) marginStart = dp(Dimens.SPACE_3) })
        }
        addView(line)
    }.bounceAtEnds(vertical = false)

private fun Context.filterChip(title: String, on: Boolean, onClick: () -> Unit) = label(title, Dimens.SMALL).apply {
    gravity = Gravity.CENTER
    minHeight = dp(Dimens.TOUCH_MIN)
    setPadding(dp(Dimens.SPACE_6), 0, dp(Dimens.SPACE_6), 0)
    pressMotion()
    paint(this, on)
    setOnClickListener { onClick() }
}

/**
 * 펼친 걸러 보기의 칩 줄. 다시 누르면 풀린다. 내 주변은 위치를 모르면 칩을 흐리게 두고 이유를 적는다.
 * [onPick]은 바뀐 걸러 보기를 받는다.
 */
internal fun Context.filterOptions(kind: String, filter: CourseFilter, located: Boolean, counts: (CourseFilter) -> Int, onPick: (CourseFilter) -> Unit): View {
    // 값마다 "그 값을 고른 걸러 보기". 이미 고른 값을 다시 누르면 그 조건만 푼다.
    val choices: List<Pair<Choice, CourseFilter>> = when (kind) {
        "near" -> NearRange.entries.map { Choice(it.name, nearLabel(it)) to filter.copy(near = it) }
        "mood" -> MOOD_KEYS.map { Choice(it, moodLabel(it)) to filter.copy(mood = it) }
        "length" -> LengthBand.entries.map { Choice(it.name, lengthLabel(it)) to filter.copy(length = it) }
        "region" -> REGIONS.map { Choice(it, it) to filter.copy(region = it) }
        else -> SCENERY.map { (key, title) -> Choice(key, title) to filter.copy(theme = key) }
    }
    val selected = when (kind) {
        "near" -> filter.near?.name
        "mood" -> filter.mood
        "length" -> filter.length?.name
        "region" -> filter.region
        else -> filter.theme
    }
    val cleared = when (kind) {
        "near" -> filter.copy(near = null)
        "mood" -> filter.copy(mood = null)
        "length" -> filter.copy(length = null)
        "region" -> filter.copy(region = null)
        else -> filter.copy(theme = null)
    }
    // 내 주변이 아닌 칩에는 그 값을 골랐을 때 남는 코스 수를 붙인다(지금 둘러보기 칩과 같다).
    val titled = choices.map { (choice, next) ->
        Choice(choice.key, if (kind == "near") choice.title else "${choice.title} ${counts(next)}") to next
    }
    val pick = { key: String -> onPick(if (key == selected) cleared else titled.first { it.first.key == key }.second) }
    val row = chipRow(titled.map { it.first }, selected, Dimens.SMALL) { key -> pick(key) }
    if (kind != "near" || located) return row
    return column().apply {
        addView(row.apply { alpha = DIM })
        addSpaced(label(NO_POSITION, Dimens.LABEL, palette.inkSecondary).atMostLines(1), Dimens.SPACE_2)
    }
}

private const val SEARCH_MAX = 30
private const val DIM = 0.45f
