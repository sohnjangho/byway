package com.byway.ui

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText

/** 정차 화면에서만 쓰는 한 줄 입력 칸(코스 이름, 닉네임, 코드). 글자 크기와 높이는 버튼과 같다. */
internal fun Context.textField(hint: String, maxLength: Int, code: Boolean = false): EditText = EditText(this).apply {
    this.hint = hint
    textSize = Dimens.BODY
    setTextColor(palette.ink)
    setHintTextColor(palette.inkSecondary)
    isSingleLine = true
    minHeight = dp(Dimens.TOUCH_MIN)
    imeOptions = EditorInfo.IME_ACTION_DONE
    inputType = if (code) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS else InputType.TYPE_CLASS_TEXT
    filters = if (code) arrayOf(InputFilter.LengthFilter(maxLength), InputFilter.AllCaps()) else arrayOf(InputFilter.LengthFilter(maxLength))
    background = rounded(palette.bgRaised, Dimens.RADIUS_MD, palette.line)
    setPadding(dp(Dimens.SPACE_6), 0, dp(Dimens.SPACE_6), 0)
}
