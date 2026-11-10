package com.byway.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/**
 * 차의 내비를 앞으로 띄운다. Pleos 내비는 표준 지도 앱 분류(CATEGORY_APP_MAPS)를 받아서 패키지 이름을 몰라도 된다.
 * 3분할에서는 새로 띄운 앱이 가운데로 가고 가운데 있던 앱이 오른쪽으로 간다. 에뮬레이터에서 확인했다(주행 화면 3분할 설계).
 * 지도 앱이 없으면 false 다. 코스는 그대로 달린다.
 */
fun showNavigation(context: Context): Boolean = try {
    context.startActivity(
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
} catch (_: ActivityNotFoundException) {
    false
}
