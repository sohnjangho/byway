package com.byway.core.share

import kotlin.random.Random

/** 차 안에서 불러 주고 받아 적는 코드. 헷갈리는 0·1·O·I 를 뺐다. */
const val CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
const val CODE_LENGTH = 6

fun newCode(random: Random): String = String(CharArray(CODE_LENGTH) { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] })

fun isCode(text: String): Boolean = text.length == CODE_LENGTH && text.all { it in CODE_ALPHABET }
