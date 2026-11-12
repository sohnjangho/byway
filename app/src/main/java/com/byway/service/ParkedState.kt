package com.byway.service

/**
 * 정차로 볼지 정하는 유일한 식. 기어가 P 이고 플랫폼 주행 규제가 걸려 있지 않을 때만 정차다.
 * [requiresDo]가 null 이면 규제 값을 얻지 못한 것이고 기어로 판단한다.
 */
fun parkedFrom(gearParked: Boolean, requiresDo: Boolean?): Boolean = gearParked && requiresDo != true

/** 정차 여부를 바꾼다. 움직이다 정차하면 정차 회차를 하나 올린다. */
fun withParked(state: DriveState, parked: Boolean): DriveState =
    state.copy(parked = parked, stop = if (parked && !state.parked) state.stop + 1 else state.stop)
