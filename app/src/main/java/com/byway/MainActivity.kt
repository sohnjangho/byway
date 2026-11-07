package com.byway

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = TextView(this).apply {
            text = "에움길\n최단 경로가 아니라, 그 길을 달리게 합니다."
            textSize = 18f
            setPadding(48, 96, 48, 48)
        }
        setContentView(view)
    }
}
