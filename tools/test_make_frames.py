"""코스 훑어보기 자료를 만드는 규칙을 시험한다. 실행: python3 tools/test_make_frames.py"""
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("make_frames", Path(__file__).parent / "make-frames.py")
frames = importlib.util.module_from_spec(spec)
spec.loader.exec_module(frames)

COURSE = {
    "sections": [
        {"name": "숲길", "mood": "forest", "startIndex": 0, "endIndex": 4},
        {"name": "강변", "mood": "river", "startIndex": 5, "endIndex": 8},
    ],
    "elevation": [10.0, 110.0],
}
PLACES = [{"no": 1, "km": 1.0}, {"no": 2, "km": 8.0}]


class PickTest(unittest.TestCase):
    def test_frames_are_spread_evenly_from_start_to_end(self):
        along = [i * 100.0 for i in range(101)]
        self.assertEqual(frames.picks(along, 5), [0, 25, 50, 75, 100])

    def test_a_short_course_has_no_repeated_frame(self):
        self.assertEqual(frames.picks([0.0, 50.0], 16), [0, 1])


class TrackTest(unittest.TestCase):
    def setUp(self):
        self.path = [[37.0 + i * 0.0005, 127.0] for i in range(1000)]
        self.along = frames.distances(self.path)
        self.track = frames.track(self.path, self.along, lambda point: (point[1], point[0]), 200)

    def test_the_track_stays_small(self):
        self.assertLessEqual(len(self.track), 202)

    def test_the_track_keeps_both_ends(self):
        self.assertEqual(self.track[0][2], 0)
        self.assertAlmostEqual(self.track[-1][2], self.along[-1] / 1000, places=2)

    def test_distance_only_grows_along_the_track(self):
        kms = [point[2] for point in self.track]
        self.assertEqual(kms, sorted(kms))


class InfoTest(unittest.TestCase):
    def info(self, index, km, course=COURSE):
        return frames.info(course, PLACES, index, km, 9.0)

    def test_a_frame_names_its_section(self):
        self.assertEqual((self.info(7, 7.0)["section"], self.info(7, 7.0)["mood"]), ("강변", "river"))

    def test_the_last_point_belongs_to_the_last_section(self):
        self.assertEqual(self.info(9, 9.0)["section"], "강변")

    def test_a_nearby_place_is_named_by_its_number(self):
        self.assertEqual(self.info(1, 1.5)["placeNo"], 1)

    def test_a_far_place_is_left_out(self):
        self.assertIsNone(self.info(4, 4.5)["placeNo"])

    def test_height_follows_the_elevation_samples(self):
        self.assertEqual(self.info(0, 0.0)["elev"], 10)
        self.assertEqual(self.info(9, 9.0)["elev"], 110)

    def test_height_is_looked_up_by_distance_not_by_point_number(self):
        """고도는 거리가 고른 표본이다. 좌표가 한쪽에 몰린 코스에서도 그 거리의 고도를 쓴다."""
        course = {**COURSE, "elevation": [0.0, 100.0, 200.0]}
        self.assertEqual(frames.info(course, [], 8, 1.0, 10.0)["elev"], 0)
        self.assertEqual(frames.info(course, [], 8, 5.0, 10.0)["elev"], 100)
        self.assertEqual(frames.info(course, [], 1, 10.0, 10.0)["elev"], 200)

    def test_a_course_without_elevation_has_no_height(self):
        self.assertIsNone(self.info(0, 0.0, {**COURSE, "elevation": []})["elev"])

    def test_a_course_without_places_names_none(self):
        self.assertIsNone(frames.info(COURSE, [], 1, 1.0, 9.0)["placeNo"])


class DistanceTest(unittest.TestCase):
    def test_distance_uses_the_same_formula_as_the_app(self):
        """앱(Geo.kt)과 식이 다르면 홈의 거리와 띠 끝의 km가 0.1 어긋난다. 위도 1도는 약 111.195km 다."""
        self.assertAlmostEqual(frames.distances([[37.0, 127.0], [38.0, 127.0]])[-1], 111_194.9, delta=1.0)

    def test_distance_shrinks_with_latitude_along_a_parallel(self):
        self.assertAlmostEqual(frames.distances([[37.0, 127.0], [37.0, 128.0]])[-1], 88_803.0, delta=5.0)


if __name__ == "__main__":
    unittest.main()
