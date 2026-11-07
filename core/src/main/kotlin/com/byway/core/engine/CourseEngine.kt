package com.byway.core.engine

import com.byway.core.course.Course
import com.byway.core.course.Leg
import com.byway.core.course.Section
import com.byway.core.course.Stop
import com.byway.core.drive.DriveCommand
import com.byway.core.drive.DriveCommand.Publish
import com.byway.core.drive.DriveCommand.Say
import com.byway.core.drive.DriveCommand.ShowLine
import com.byway.core.drive.DriveCommand.ShowRoute
import com.byway.core.drive.DriveEvent
import com.byway.core.drive.DriveFeature
import com.byway.core.geo.GeoPoint

/** 핵심 ②③: 구간을 순서대로 내비에 올리고, 이탈하면 코스로 되돌린다. */
class CourseEngine(
    private val tuning: DriveTuning = DriveTuning(),
    private val stopsOf: (Course) -> List<Stop> = { planStops(it, it.toleranceMeters ?: tuning.simplifyToleranceMeters) },
) : DriveFeature {
    override val id = "course"

    private var run: Run? = null

    override fun onEvent(event: DriveEvent): List<DriveCommand> = when (event) {
        is DriveEvent.CourseStarted -> start(event.course)
        is DriveEvent.LocationUpdated -> run?.onLocation(event.point).orEmpty()
        else -> emptyList()
    }

    private fun start(course: Course): List<DriveCommand> {
        val started = Run(course, planLegs(stopsOf(course)))
        run = started
        return listOf(
            ShowRoute(started.currentLeg),
            Say(Messages.courseStart(course.name)),
            Publish(DriveEvent.SectionEntered(started.section)),
        )
    }

    private inner class Run(private val course: Course, private val legs: List<Leg>) {
        private val path = course.path
        private val travelled = path.runningFold(0.0 to path.first()) { (sum, previous), point ->
            sum + previous.distanceTo(point) to point
        }.drop(1).map { it.first }

        private var legIndex = 0
        private var progress = 0
        private var strayFixes = 0
        private var rejoining = false

        var section: Section = sectionAt(0)
            private set
        val currentLeg get() = legs[legIndex]

        fun onLocation(point: GeoPoint): List<DriveCommand> =
            if (rejoining) whileRejoining(point) else whileOnCourse(point)

        private fun whileOnCourse(point: GeoPoint): List<DriveCommand> {
            val windowEnd = lastIndexWithin(tuning.lookaheadMeters)
            val nearest = nearestIndex(point, windowEnd)
            if (nearest.distance > tuning.offCourseMeters) return onStray(point)
            strayFixes = 0
            progress = maxOf(progress, nearest.index)
            val legChanged = advanceLegs()
            return buildList {
                addAll(sectionChange())
                if (isFinished()) {
                    addAll(finish())
                    return@buildList
                }
                if (legChanged) {
                    add(ShowRoute(remainingLeg()))
                    add(Say(Messages.NEXT_LEG))
                }
                add(progressLine())
            }
        }

        private fun onStray(point: GeoPoint): List<DriveCommand> {
            strayFixes++
            if (strayFixes < tuning.offCourseFixes) return emptyList()
            rejoining = true
            val index = nearestIndex(point, path.lastIndex).index
            val target = Stop(Messages.REJOIN_POINT, path[index], index)
            return listOf(ShowRoute(Leg(emptyList(), target)), Say(Messages.REJOINING))
        }

        private fun whileRejoining(point: GeoPoint): List<DriveCommand> {
            val nearest = nearestIndex(point, path.lastIndex)
            if (nearest.distance > tuning.offCourseMeters) return emptyList()
            rejoining = false
            strayFixes = 0
            progress = nearest.index
            advanceLegs()
            return buildList {
                addAll(sectionChange())
                if (isFinished()) {
                    addAll(finish())
                    return@buildList
                }
                add(ShowRoute(remainingLeg()))
                add(Say(Messages.REJOINED))
                add(progressLine())
            }
        }

        private fun advanceLegs(): Boolean {
            val before = legIndex
            while (legIndex < legs.lastIndex && progress >= legs[legIndex].destination.pathIndex) legIndex++
            return legIndex != before
        }

        private fun remainingLeg() = currentLeg.copy(waypoints = currentLeg.waypoints.filter { it.pathIndex > progress })

        private fun sectionChange(): List<DriveCommand> {
            val now = sectionAt(progress)
            if (now == section) return emptyList()
            section = now
            return listOf(Say(Messages.sectionEntered(now.name)), Publish(DriveEvent.SectionEntered(now)))
        }

        private fun isFinished() = progress == path.lastIndex

        private fun finish(): List<DriveCommand> {
            run = null
            return listOf(Say(Messages.FINISHED), Publish(DriveEvent.CourseFinished))
        }

        private fun progressLine() = ShowLine(Messages.line(section.name, travelled.last() - travelled[progress]))

        private fun sectionAt(index: Int) = course.sections.first { index in it.startIndex..it.endIndex }

        private fun lastIndexWithin(meters: Double): Int {
            val limit = travelled[progress] + meters
            return (progress..path.lastIndex).last { travelled[it] <= limit }
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
    }

    private data class Nearest(val index: Int, val distance: Double)
}
