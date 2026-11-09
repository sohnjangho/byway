package com.byway.core.engine

import com.byway.core.course.Course
import com.byway.core.course.Leg
import com.byway.core.course.Section
import com.byway.core.course.Stop
import com.byway.core.drive.DriveCommand
import com.byway.core.drive.DriveCommand.Ask
import com.byway.core.drive.DriveCommand.FollowRoute
import com.byway.core.drive.DriveCommand.Publish
import com.byway.core.drive.DriveCommand.Say
import com.byway.core.drive.DriveCommand.ShowProgress
import com.byway.core.drive.DriveEvent
import com.byway.core.drive.DriveFeature
import com.byway.core.drive.Prompt
import com.byway.core.drive.RouteAbility
import com.byway.core.geo.GeoPoint
import com.byway.core.geo.farthestFromChord

/** 꺼졌다 켜도 코스를 이어가려고 남기는 것. [progress]는 마지막으로 경로를 바꾼 때의 코스 위 지점이다. */
data class RunSnapshot(val courseId: String, val progress: Int)

/**
 * 핵심 ②③: 앞에 남은 지점을 내비에 올리고, 벗어나면 먼저 묻고 코스로 되돌린다.
 * 운전자가 내비에 직접 넣은 경로는 건드리지 않고 멈춰서 기다린다.
 * 엔진은 시각을 모른다. 기다리는 시간은 위치 사건의 횟수로 센다.
 */
class CourseEngine(
    private val tuning: DriveTuning = DriveTuning(),
    private val ability: RouteAbility = RouteAbility(),
    private val stopsOf: (Course) -> List<Stop> = { planStops(it, it.toleranceMeters ?: tuning.simplifyToleranceMeters) },
) : DriveFeature {
    override val id = "course"

    private var run: Run? = null

    /** 정차 여부는 코스를 시작하기 전에도 온다. */
    private var parked = false

    fun snapshot(): RunSnapshot? = run?.snapshot()

    override fun onEvent(event: DriveEvent): List<DriveCommand> = when (event) {
        is DriveEvent.CourseStarted -> start(event.course, event.resumeFrom)
        is DriveEvent.LocationUpdated -> run?.onLocation(event.point).orEmpty()
        is DriveEvent.DrivingStateChanged -> {
            parked = event.parked
            run?.onParked().orEmpty()
        }
        DriveEvent.RouteTakenOver, DriveEvent.RouteCancelled -> run?.pause().orEmpty()
        DriveEvent.DestinationArrived -> run?.onArrived().orEmpty()
        is DriveEvent.Answered -> run?.onAnswer(event.yes).orEmpty()
        DriveEvent.CourseStopped -> {
            run = null
            emptyList()
        }
        else -> emptyList()
    }

    private fun start(course: Course, resumeFrom: Int): List<DriveCommand> {
        val planned = stopsOf(course)
        val from = resumeFrom.coerceIn(0, course.path.lastIndex)
        val started = Run(course, if (ability.acceptsWholePath) listOf(planned.last()) else planned, from)
        run = started
        return listOf(
            started.routeAhead(),
            Say(if (from > 0) Messages.courseResumed(course.name) else Messages.courseStart(course.name)),
            Publish(DriveEvent.SectionEntered(started.section)),
        )
    }

    /** 접근 중 → 코스 위 ⇄ 묻는 중 → 코스 밖(들르는 중이거나 복귀 중). 멈춤은 어디서든 들어간다. */
    private enum class Phase { APPROACHING, ON_COURSE, ASKING, AWAY, PAUSED }

    private inner class Run(private val course: Course, private val planned: List<Stop>, private val from: Int) {
        private val path = course.path
        private val travelled = path.runningFold(0.0 to path.first()) { (sum, previous), point ->
            sum + previous.distanceTo(point) to point
        }.drop(1).map { it.first }

        /** 내비는 지점 사이 길을 스스로 고른다. 코스 허용 오차 안의 차이는 이탈로 보지 않는다. */
        private val strayLimit = maxOf(
            tuning.offCourseMeters,
            (course.toleranceMeters ?: tuning.simplifyToleranceMeters) + STRAY_MARGIN_METERS,
        )

        /** 같은 구간에서 이탈이 되풀이되면 지점을 하나 더한다. */
        private var stops = planned
        private var phase = Phase.APPROACHING
        private var progress = from
        private var checkpoint = from
        private var destination = planned.last()

        /** 지난 교체 때 앞 경로와 이어 붙인 지점. 이 지점을 지나야 다시 교체한다. */
        private var shared: Stop? = null
        private var routedAt = -1
        private var approachRouted = false
        private var strayFixes = 0
        private var waitedFixes = 0
        private var nearFixes = 0

        /** 복귀 경로의 첫 지점. 운전자가 들르는 중이면 없다. */
        private var target: Stop? = null
        private var asked: Prompt? = null

        /** 다음 지점(계획 기준)마다 센 이탈 횟수. */
        private val strays = mutableMapOf<Int, Int>()

        /** 이 지점을 지날 때까지는 이탈을 보지 않는다. */
        private var freeUntil = -1
        private var last: GeoPoint? = null

        var section: Section = sectionAt(from)
            private set

        fun snapshot() = RunSnapshot(course.id, checkpoint)

        /**
         * 앞에 남은 지점에서 경유지와 목적지를 뽑아 올린다. [first]는 맨 앞에 끼우는 지점(출발점, 복귀 지점)이고
         * [join]은 앞 경로의 목적지였던 지점이다. 경로 전체를 받는 쪽에는 지금 지점부터 끝까지의 좌표를 함께 준다.
         */
        fun routeAhead(first: Stop? = null, join: Stop? = null): FollowRoute {
            val picked = pick(first)
            destination = picked.last()
            shared = join
            routedAt = progress
            return FollowRoute(Leg(picked.dropLast(1), destination), if (ability.acceptsWholePath) path.drop(progress) else null)
        }

        private fun pick(first: Stop? = null): List<Stop> {
            val ahead = stops.filter { it.pathIndex > (first?.pathIndex ?: progress) }
            return (listOfNotNull(first) + ahead).take(ability.maxWaypoints + 1).ifEmpty { listOf(stops.last()) }
        }

        fun onLocation(point: GeoPoint): List<DriveCommand> {
            val jump = last?.distanceTo(point) ?: 0.0
            last = point
            return when (phase) {
                Phase.APPROACHING -> whileApproaching(point)
                Phase.ON_COURSE -> whileOnCourse(point, jump)
                Phase.ASKING -> whileAsking(point)
                Phase.AWAY -> whileAway(point)
                Phase.PAUSED -> emptyList()
            }
        }

        /** 첫 위치에서 한 번 정한다: 출발점 근처면 그대로, 코스 중간이면 그 자리부터, 멀면 출발점으로 안내한다. */
        private fun whileApproaching(point: GeoPoint): List<DriveCommand> {
            if (nearestIndex(point, lastIndexWithin(tuning.lookaheadMeters)).distance <= strayLimit) {
                phase = Phase.ON_COURSE
                return whileOnCourse(point, 0.0)
            }
            if (approachRouted) return emptyList()
            approachRouted = true
            val joined = firstRunNearest(point)
            if (joined != null) {
                phase = Phase.ON_COURSE
                progress = joined
                checkpoint = joined
                val route = routeAhead()
                return listOf(route) + advance(if (from == 0) listOf(Say(Messages.JOINED_MIDWAY)) else emptyList())
            }
            if (from > 0) return rejoin(point)
            return listOf(routeAhead(Stop(Messages.START_POINT, path[from], from)), Say(Messages.TO_START))
        }

        private fun whileOnCourse(point: GeoPoint, jump: Double): List<DriveCommand> {
            val reach = tuning.lookaheadMeters + if (jump > tuning.gapJumpMeters) jump * GAP_REACH else 0.0
            val freed = if (freeUntil > progress) lastIndexWithin(tuning.lookaheadMeters, freeUntil) else 0
            val nearest = nearestIndex(point, maxOf(lastIndexWithin(reach), freed))
            if (nearest.distance > strayLimit) return onStray(point)
            strayFixes = 0
            progress = maxOf(progress, nearest.index)
            if (progress >= freeUntil) freeUntil = -1
            return advance()
        }

        /** 진행 지점이 바뀐 뒤에 함께 하는 일: 구간 안내, 완주, 다음 경로 잇기, 진행값. [before]는 그 앞에 낼 명령이다. */
        private fun advance(before: List<DriveCommand> = emptyList()): List<DriveCommand> = buildList {
            addAll(before)
            addAll(sectionChange())
            if (isFinished()) {
                addAll(finish())
                return@buildList
            }
            addAll(swapIfClose())
            add(report())
        }

        /** 목적지에 닿기 전에 다음 경로를 올린다. 앞 경로의 목적지가 새 경로의 첫 경유지라 길이 끊기지 않는다. */
        private fun swapIfClose(): List<DriveCommand> {
            if (progress == routedAt) return emptyList()
            if (travelled[destination.pathIndex] - travelled[progress] > tuning.legSwapAheadMeters) return emptyList()
            if (shared?.let { progress < it.pathIndex } == true) return emptyList()
            if (pick().last() == destination) return emptyList()
            checkpoint = progress
            return listOf(routeAhead(join = destination), Say(Messages.NEXT_LEG, alert = true))
        }

        private fun onStray(point: GeoPoint): List<DriveCommand> {
            if (parked || progress < freeUntil) return emptyList()
            strayFixes++
            if (strayFixes < tuning.offCourseFixes) return emptyList()
            strayFixes = 0
            val stretch = planned.firstOrNull { it.pathIndex > progress }?.pathIndex ?: path.lastIndex
            val count = (strays[stretch] ?: 0) + 1
            strays[stretch] = count
            return when {
                count >= FREE_AT -> {
                    freeUntil = stretch
                    listOf(Say(Messages.FREE, alert = true))
                }
                count == EXTRA_STOP_AT -> {
                    addBranchStop(stretch)
                    rejoin(point)
                }
                else -> {
                    phase = Phase.ASKING
                    waitedFixes = 0
                    ask(Prompt.DETOUR, Messages.ASK_DETOUR)
                }
            }
        }

        /** 내비가 지점 사이에서 다른 길을 고른다. 그 구간에서 직선과 가장 멀리 떨어진 점을 지점으로 더해 길을 잡아 준다. */
        private fun addBranchStop(stretchEnd: Int) {
            val stretchStart = planned.lastOrNull { it.pathIndex < stretchEnd }?.pathIndex ?: 0
            val (index, away) = farthestFromChord(path, maxOf(stretchStart, progress), stretchEnd) ?: return
            if (away < BRANCH_MIN_METERS || stops.any { it.pathIndex == index }) return
            stops = (stops + Stop(sectionAt(index).name, path[index], index)).sortedBy { it.pathIndex }
        }

        private fun whileAsking(point: GeoPoint): List<DriveCommand> {
            if (nearestIndex(point, lastIndexWithin(tuning.lookaheadMeters)).distance <= strayLimit) {
                phase = Phase.ON_COURSE
                return unask() + whileOnCourse(point, 0.0)
            }
            if (parked) return emptyList()
            waitedFixes++
            if (waitedFixes < tuning.askWaitFixes) return emptyList()
            return unask() + rejoin(point)
        }

        fun onAnswer(yes: Boolean): List<DriveCommand> = when (asked) {
            Prompt.DETOUR -> if (yes) {
                phase = Phase.AWAY
                target = null
                nearFixes = 0
                unask() + Say(Messages.DETOUR_OK)
            } else {
                unask() + rejoin(last ?: path[progress])
            }
            Prompt.RESUME -> if (yes) resume() else emptyList()
            null -> emptyList()
        }

        /** 복귀 경로를 한 번에 올린다. 복귀 지점이 첫 경유지이고 그 뒤 지점이 이어진다. */
        private fun rejoin(point: GeoPoint): List<DriveCommand> {
            phase = Phase.AWAY
            nearFixes = 0
            checkpoint = progress
            val to = rejoinTarget(point)
            target = to
            return listOf(routeAhead(first = to), Say(Messages.REJOINING, alert = true))
        }

        /**
         * 코스로 돌아올 자리. 차 옆 자리에서 [DriveTuning.rejoinAheadMeters]만큼 앞이다. 바로 뒤를 가리켜 유턴시키지 않고,
         * 멀리 있는 다음 지점까지 코스를 건너뛰지도 않는다. 그 가까이에 지점이 있으면 그 지점을 쓴다.
         * 코스가 되돌아와 차 가까이를 지나는 곳이 있어, 차 옆 자리는 탐색 범위 안에서만 찾는다.
         */
        private fun rejoinTarget(point: GeoPoint): Stop {
            val abreast = nearestIndex(point, lastIndexWithin(tuning.lookaheadMeters)).index
            val at = (abreast..path.lastIndex).firstOrNull { travelled[it] - travelled[abreast] >= tuning.rejoinAheadMeters } ?: path.lastIndex
            val close = stops.firstOrNull { it.pathIndex >= at && travelled[it.pathIndex] - travelled[at] <= tuning.rejoinAheadMeters }
            return close ?: Stop(Messages.REJOIN_POINT, path[at], at)
        }

        /** 코스 밖에서는 경로를 다시 올리지 않는다. 안쪽으로 들어와 연속으로 잡히거나 복귀 지점을 지나면 돌아온 것이다. */
        private fun whileAway(point: GeoPoint): List<DriveCommand> {
            val to = target
            val nearest = nearestIndex(point, lastIndexWithin(tuning.lookaheadMeters, to?.pathIndex ?: progress))
            val passed = to != null && nearest.distance <= strayLimit && nearest.index >= to.pathIndex
            nearFixes = if (nearest.distance <= tuning.rejoinMeters) nearFixes + 1 else 0
            if (!passed && nearFixes < tuning.rejoinFixes) return emptyList()
            val skipped = course.sections.filter { it.startIndex > progress && it.endIndex < nearest.index }.map { it.name }
            phase = Phase.ON_COURSE
            strayFixes = 0
            target = null
            progress = maxOf(progress, nearest.index)
            checkpoint = progress
            return advance(
                buildList {
                    add(Say(Messages.REJOINED, alert = true))
                    if (skipped.isNotEmpty()) add(Say(Messages.skipped(skipped)))
                },
            )
        }

        /** 운전자가 내비에 다른 목적지를 넣었거나 경로를 껐다. 경로를 올리지 않고 위치만 받는다. */
        fun pause(): List<DriveCommand> {
            if (phase == Phase.PAUSED) return emptyList()
            phase = Phase.PAUSED
            checkpoint = progress
            asked = Prompt.RESUME
            return listOf(Say(Messages.PAUSED, alert = true), Ask(Prompt.RESUME), Publish(DriveEvent.CoursePaused))
        }

        fun onArrived(): List<DriveCommand> =
            if (phase == Phase.PAUSED) listOf(Say(Messages.ASK_RESUME, alert = true)) else emptyList()

        private fun resume(): List<DriveCommand> {
            val back = unask() + Publish(DriveEvent.SectionEntered(section))
            val point = last
            if (point != null && nearestIndex(point, lastIndexWithin(tuning.lookaheadMeters)).distance > strayLimit) {
                return back + rejoin(point)
            }
            phase = Phase.ON_COURSE
            strayFixes = 0
            return back + routeAhead() + Say(Messages.RESUMING)
        }

        fun onParked(): List<DriveCommand> = if (phase != Phase.PAUSED && parkedAtEnd()) finish() else emptyList()

        private fun ask(prompt: Prompt, text: String): List<DriveCommand> {
            asked = prompt
            return listOf(Say(text, alert = true), Ask(prompt))
        }

        private fun unask(): List<DriveCommand> {
            if (asked == null) return emptyList()
            asked = null
            return listOf(Ask(null))
        }

        private fun sectionChange(): List<DriveCommand> {
            val now = sectionAt(progress)
            if (now == section) return emptyList()
            section = now
            return listOf(Say(Messages.sectionEntered(now.name)), Publish(DriveEvent.SectionEntered(now)))
        }

        private fun isFinished() = progress == path.lastIndex || parkedAtEnd()

        /** 종점 앞 주차장에 세워도 끝까지 달린 것이다. 종점이 탐색 범위에 들어온 뒤에만 본다. 출발점과 종점이 같은 코스가 있다. */
        private fun parkedAtEnd(): Boolean {
            val point = last ?: return false
            return parked && lastIndexWithin(tuning.lookaheadMeters) == path.lastIndex &&
                point.distanceTo(path.last()) <= tuning.finishRadiusMeters
        }

        private fun finish(): List<DriveCommand> {
            run = null
            return unask() + listOf(Say(Messages.FINISHED), Publish(DriveEvent.CourseFinished))
        }

        private fun report() = ShowProgress(course.sections.indexOf(section), travelled.last() - travelled[progress])

        private fun sectionAt(index: Int) = course.sections.first { index in it.startIndex..it.endIndex }

        private fun lastIndexWithin(meters: Double, start: Int = progress): Int {
            val limit = travelled[start] + meters
            return (start..path.lastIndex).last { travelled[it] <= limit }
        }

        /** 같은 도로를 되밟는 코스가 있어 진행 지점부터 [lastIndex]까지만 찾는다. */
        private fun nearestIndex(point: GeoPoint, lastIndex: Int): Nearest {
            var best = Nearest(progress, point.distanceTo(path[progress]))
            for (i in progress until lastIndex) {
                val distance = point.distanceToSegment(path[i], path[i + 1])
                if (distance < best.distance) {
                    val closerEnd = if (point.distanceTo(path[i]) <= point.distanceTo(path[i + 1])) i else i + 1
                    best = Nearest(closerEnd, distance)
                }
            }
            return best
        }

        /**
         * 코스 전체에서 차와 가까운 자리. 되밟는 코스에서 돌아오는 쪽으로 건너뛰지 않도록,
         * 진행 지점부터 훑어 처음으로 허용 오차 안에 드는 이어진 구간에서만 고른다.
         */
        private fun firstRunNearest(point: GeoPoint): Int? {
            var best: Nearest? = null
            for (i in progress..path.lastIndex) {
                val distance = point.distanceTo(path[i])
                if (distance <= strayLimit) {
                    if (best == null || distance < best.distance) best = Nearest(i, distance)
                } else if (best != null) {
                    break
                }
            }
            return best?.index
        }
    }

    private data class Nearest(val index: Int, val distance: Double)

    private companion object {
        const val STRAY_MARGIN_METERS = 100.0

        /** 위치가 끊겼다 돌아오면 건너뛴 직선 거리의 이만큼을 더 찾는다. 길은 직선보다 길다. */
        const val GAP_REACH = 2.0

        /** 같은 구간에서 두 번째 이탈이면 지점을 더하고, 세 번째면 그 구간은 내비에 맡긴다. */
        const val EXTRA_STOP_AT = 2
        const val FREE_AT = 3
        const val BRANCH_MIN_METERS = 50.0
    }
}
