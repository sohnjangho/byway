package com.roadcraft

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = TextView(this).apply {
            text = "RoadCraft\n코스를 달리는 동안, 차가 함께 준비합니다."
            textSize = 18f
            setPadding(48, 96, 48, 48)
        }
        setContentView(view)
    }
}
