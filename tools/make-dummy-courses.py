#!/usr/bin/env python3
"""지도 없는 더미 코스 150개를 만든다(더 많은 코스 설계 4절).

실제 지명과 대략의 좌표 표에서 출발 → 경유 1~2곳 → 도착을 고른다. 코스 이름은 지어낸 것이고 시연용이다.
시드가 고정이라 다시 돌려도 같은 파일이 나온다.

    python3 tools/make-dummy-courses.py > app/src/main/assets/dummy/courses.json
"""
import json
import math
import random

# 지역마다 (지명, 위도, 경도, 풍경). 좌표는 공개 지명의 대략 자리다.
PLACES = {
    "수도권": [
        ("남양주 물의정원", 37.5960, 127.3290, "river"), ("가평 자라섬", 37.8170, 127.5250, "river"),
        ("가평 남이섬 선착장", 37.7900, 127.5260, "river"), ("청평호", 37.7270, 127.4300, "lake"),
        ("포천 산정호수", 38.0700, 127.3170, "lake"), ("포천 아트밸리", 37.9180, 127.2050, "mountain"),
        ("연천 전곡리", 38.0240, 127.0700, "river"), ("연천 재인폭포", 38.0720, 127.1290, "river"),
        ("파주 임진각", 37.8890, 126.7400, "river"), ("파주 헤이리", 37.7880, 126.6950, "city"),
        ("강화 전등사", 37.6210, 126.5110, "forest"), ("강화 동막해변", 37.5930, 126.4570, "coast"),
        ("송도 센트럴파크", 37.3920, 126.6390, "city"), ("영종도 을왕리", 37.4470, 126.3730, "coast"),
        ("시흥 오이도", 37.3450, 126.6870, "coast"), ("대부도 방아머리", 37.2850, 126.5730, "coast"),
        ("화성 제부도 입구", 37.1680, 126.6280, "coast"), ("화성 궁평항", 37.1150, 126.6820, "coast"),
        ("평택호 관광단지", 36.9230, 126.9180, "lake"), ("안성 팜랜드", 37.0010, 127.2250, "forest"),
        ("용인 에버랜드", 37.2940, 127.2020, "city"), ("이천 설봉공원", 37.2120, 127.4590, "forest"),
        ("여주 신륵사", 37.2950, 127.6530, "river"), ("양평 용문사", 37.5420, 127.5690, "mountain"),
        ("하남 미사 조정경기장", 37.5660, 127.1900, "river"), ("북악스카이웨이 팔각정", 37.6010, 126.9810, "mountain"),
        ("남산 N서울타워", 37.5510, 126.9880, "city"), ("한강 반포", 37.5100, 126.9960, "river"),
        ("과천 서울대공원", 37.4280, 127.0170, "forest"), ("수원 화성", 37.2870, 127.0120, "city"),
        ("양주 장흥", 37.7280, 126.9440, "forest"), ("동두천 소요산", 37.9450, 127.0600, "mountain"),
        ("가평 아침고요수목원", 37.7440, 127.3530, "forest"), ("가평 호명호수", 37.7570, 127.4800, "lake"),
        ("김포 대명항", 37.6430, 126.5470, "coast"), ("일산 호수공원", 37.6560, 126.7650, "lake"),
        ("남양주 수종사", 37.5550, 127.3080, "mountain"),
    ],
    "강원": [
        ("춘천 소양강댐", 37.9450, 127.8150, "lake"), ("춘천 의암호", 37.8890, 127.6980, "lake"),
        ("홍천 수타사", 37.7880, 127.9800, "forest"), ("인제 자작나무숲", 38.0080, 128.2010, "forest"),
        ("인제 내린천", 37.9680, 128.3770, "river"), ("양구 파로호", 38.1500, 127.9500, "lake"),
        ("화천 산천어축제장", 38.1060, 127.7080, "river"), ("철원 고석정", 38.1890, 127.2930, "river"),
        ("속초 영금정", 38.2120, 128.6010, "coast"), ("설악산 소공원", 38.1720, 128.4870, "mountain"),
        ("양양 낙산사", 38.1250, 128.6270, "coast"), ("양양 하조대", 38.0260, 128.7180, "coast"),
        ("고성 화진포", 38.4720, 128.4420, "lake"), ("고성 통일전망대", 38.5850, 128.3780, "coast"),
        ("미시령 옛길", 38.2190, 128.4170, "mountain"), ("한계령", 38.1050, 128.4290, "mountain"),
        ("강릉 경포대", 37.7950, 128.8970, "lake"), ("강릉 안목해변", 37.7720, 128.9480, "coast"),
        ("강릉 정동진", 37.6900, 129.0340, "coast"), ("동해 추암", 37.4790, 129.1590, "coast"),
        ("삼척 장호항", 37.2860, 129.3150, "coast"), ("삼척 환선굴", 37.3260, 129.0360, "mountain"),
        ("태백 바람의언덕", 37.2150, 128.9630, "mountain"), ("정선 아우라지", 37.4530, 128.7270, "river"),
        ("정선 병방치", 37.3800, 128.6600, "mountain"), ("영월 한반도지형", 37.2190, 128.3530, "river"),
        ("영월 청령포", 37.1780, 128.4440, "river"), ("대관령 양떼목장", 37.6880, 128.7520, "mountain"),
        ("평창 월정사", 37.7310, 128.5920, "forest"), ("횡성호", 37.5000, 128.0250, "lake"),
        ("원주 소금산", 37.3770, 127.8180, "mountain"), ("홍천 비발디파크", 37.6450, 127.6800, "mountain"),
        ("강릉 주문진항", 37.8930, 128.8300, "coast"),
    ],
    "충청": [
        ("단양 도담삼봉", 37.0000, 128.3440, "river"), ("단양 만천하스카이워크", 36.9810, 128.3480, "river"),
        ("제천 의림지", 37.1730, 128.2120, "lake"), ("제천 옥순봉", 36.9700, 128.1880, "lake"),
        ("충주댐", 37.0040, 127.9950, "lake"), ("충주 탄금대", 36.9910, 127.9150, "river"),
        ("괴산 산막이옛길", 36.7450, 127.8460, "lake"), ("괴산 화양구곡", 36.6370, 127.8460, "forest"),
        ("보은 법주사", 36.5420, 127.8320, "mountain"), ("청주 상당산성", 36.6680, 127.5470, "mountain"),
        ("청남대", 36.4600, 127.4930, "lake"), ("대청호 전망대", 36.3800, 127.4900, "lake"),
        ("계룡산 동학사", 36.3550, 127.2530, "mountain"), ("공주 공산성", 36.4630, 127.1260, "river"),
        ("부여 궁남지", 36.2730, 126.9110, "lake"), ("서천 신성리 갈대밭", 36.0660, 126.8020, "river"),
        ("보령 대천해수욕장", 36.3150, 126.5120, "coast"), ("안면도 꽃지해변", 36.4980, 126.3360, "coast"),
        ("태안 만리포", 36.7870, 126.1430, "coast"), ("서산 해미읍성", 36.7130, 126.5480, "city"),
        ("당진 왜목마을", 37.0450, 126.5140, "coast"), ("아산 외암마을", 36.7360, 127.0190, "forest"),
        ("천안 독립기념관", 36.7830, 127.2230, "forest"), ("예산 예당호", 36.6170, 126.8030, "lake"),
        ("홍성 남당항", 36.5370, 126.4580, "coast"), ("금산 인삼시장", 36.1090, 127.4880, "city"),
        ("영동 월류봉", 36.1890, 127.8740, "river"), ("옥천 부소담악", 36.3550, 127.6430, "lake"),
        ("세종 호수공원", 36.4980, 127.2740, "lake"), ("청양 칠갑산", 36.4140, 126.8960, "mountain"),
    ],
    "경상": [
        ("안동 하회마을", 36.5390, 128.5180, "river"), ("안동 월영교", 36.5770, 128.7540, "river"),
        ("영주 부석사", 36.9990, 128.6870, "mountain"), ("영주 무섬마을", 36.7290, 128.6230, "river"),
        ("문경새재", 36.7630, 128.0750, "mountain"), ("상주 경천섬", 36.4300, 128.2280, "river"),
        ("봉화 청량산", 36.7880, 128.9100, "mountain"), ("울진 불영계곡", 36.9690, 129.3260, "forest"),
        ("울진 후포항", 36.6780, 129.4530, "coast"), ("영덕 강구항", 36.3590, 129.3890, "coast"),
        ("영덕 해맞이공원", 36.4600, 129.4110, "coast"), ("포항 호미곶", 36.0770, 129.5690, "coast"),
        ("포항 영일대", 36.0560, 129.3780, "coast"), ("경주 불국사", 35.7900, 129.3320, "forest"),
        ("경주 보문호", 35.8430, 129.2800, "lake"), ("경주 문무대왕릉", 35.7390, 129.4870, "coast"),
        ("청송 주산지", 36.3730, 129.0480, "lake"), ("청송 주왕산", 36.3930, 129.1590, "mountain"),
        ("영천 보현산 천문대", 36.1640, 128.9770, "mountain"), ("팔공산 동화사", 35.9940, 128.6960, "mountain"),
        ("대구 수성못", 35.8280, 128.6170, "lake"), ("칠곡 가산산성", 36.0300, 128.5700, "mountain"),
        ("구미 금오산", 36.1110, 128.3270, "mountain"), ("고령 대가야", 35.7280, 128.2660, "city"),
        ("합천 해인사", 35.8010, 128.0980, "forest"), ("합천호", 35.5680, 128.0310, "lake"),
        ("거창 수승대", 35.7350, 127.8200, "river"), ("함양 상림", 35.5250, 127.7290, "forest"),
        ("산청 남사예담촌", 35.2700, 127.9690, "forest"), ("밀양 영남루", 35.4880, 128.7460, "river"),
        ("울산 대왕암", 35.4920, 129.4400, "coast"), ("울산 간절곶", 35.3590, 129.3600, "coast"),
        ("영남알프스 웰컴센터", 35.5590, 129.0490, "mountain"), ("창녕 우포늪", 35.5550, 128.4130, "lake"),
        ("청도 운문사", 35.6380, 128.9450, "forest"),
    ],
    "전라": [
        ("전주 한옥마을", 35.8150, 127.1530, "city"), ("완주 대아저수지", 35.9970, 127.3150, "lake"),
        ("진안 마이산", 35.7600, 127.4200, "mountain"), ("무주 구천동", 35.8900, 127.7400, "forest"),
        ("무주 덕유산", 35.8890, 127.7360, "mountain"), ("남원 광한루", 35.4030, 127.3830, "city"),
        ("지리산 성삼재", 35.3080, 127.5150, "mountain"), ("구례 화엄사", 35.2550, 127.4970, "forest"),
        ("임실 옥정호", 35.6220, 127.1470, "lake"), ("정읍 내장산", 35.4830, 126.8900, "forest"),
        ("고창 선운사", 35.5000, 126.5700, "forest"), ("고창 고인돌", 35.4480, 126.6630, "city"),
        ("부안 곰소항", 35.5880, 126.6150, "coast"), ("군산 은파호수", 35.9500, 126.7020, "lake"),
        ("군산 선유도", 35.8090, 126.4160, "coast"), ("김제 벽골제", 35.7690, 126.8550, "river"),
        ("익산 미륵사지", 36.0120, 127.0310, "city"), ("담양 죽녹원", 35.3270, 126.9870, "forest"),
        ("담양 메타세쿼이아길", 35.3260, 127.0060, "forest"), ("무등산 증심사", 35.1310, 126.9450, "mountain"),
        ("화순 적벽", 35.0360, 127.0400, "river"), ("나주 영산포", 34.9930, 126.7200, "river"),
        ("영광 백수해안도로", 35.2560, 126.3600, "coast"), ("함평 돌머리해변", 35.0820, 126.4730, "coast"),
        ("무안 회산백련지", 34.9800, 126.4840, "lake"), ("목포 유달산", 34.7900, 126.3720, "mountain"),
        ("영암 월출산", 34.7600, 126.7000, "mountain"), ("강진 다산초당", 34.5830, 126.7490, "forest"),
        ("해남 땅끝마을", 34.2980, 126.5260, "coast"), ("장흥 정남진", 34.5320, 126.9900, "coast"),
        ("보성 녹차밭", 34.7140, 127.0820, "forest"),
    ],
    "남해안": [
        ("여수 돌산공원", 34.7290, 127.7360, "coast"), ("여수 향일암", 34.5920, 127.8030, "coast"),
        ("여수 오동도", 34.7450, 127.7660, "coast"), ("순천만 습지", 34.8860, 127.5090, "river"),
        ("순천 낙안읍성", 34.9070, 127.3410, "city"), ("광양 망덕포구", 34.9400, 127.7660, "river"),
        ("하동 화개장터", 35.1880, 127.6230, "river"), ("하동 최참판댁", 35.1680, 127.6460, "forest"),
        ("남해 독일마을", 34.8000, 128.0400, "coast"), ("남해 다랭이마을", 34.7270, 127.8930, "coast"),
        ("남해 상주은모래비치", 34.7190, 127.9900, "coast"), ("사천 삼천포대교", 34.9400, 128.0700, "coast"),
        ("고성 상족암", 34.8820, 128.2930, "coast"), ("통영 동피랑", 34.8450, 128.4270, "city"),
        ("통영 달아공원", 34.7960, 128.3940, "coast"), ("거제 바람의언덕", 34.7420, 128.6630, "coast"),
        ("거제 학동몽돌해변", 34.7700, 128.6400, "coast"), ("거제 구조라", 34.8070, 128.6900, "coast"),
        ("창원 저도 연륙교", 35.0870, 128.5050, "coast"), ("부산 해운대", 35.1590, 129.1600, "coast"),
        ("부산 광안리", 35.1530, 129.1190, "coast"), ("부산 태종대", 35.0530, 129.0870, "coast"),
        ("부산 해동용궁사", 35.1880, 129.2230, "coast"), ("부산 다대포", 35.0460, 128.9660, "coast"),
        ("진해 경화역", 35.1490, 128.6970, "city"),
    ],
    "제주": [
        ("제주 용두암", 33.5160, 126.5120, "coast"), ("이호테우해변", 33.4980, 126.4530, "coast"),
        ("곽지해변", 33.4500, 126.3050, "coast"), ("협재해변", 33.3940, 126.2390, "coast"),
        ("금오름", 33.3540, 126.3060, "mountain"), ("차귀도 포구", 33.3110, 126.1630, "coast"),
        ("송악산", 33.2000, 126.2920, "coast"), ("산방산", 33.2370, 126.3120, "mountain"),
        ("중문 주상절리", 33.2380, 126.4250, "coast"), ("천지연폭포", 33.2470, 126.5600, "forest"),
        ("쇠소깍", 33.2520, 126.6230, "river"), ("남원 큰엉", 33.2620, 126.7060, "coast"),
        ("표선해변", 33.3260, 126.8410, "coast"), ("성산일출봉", 33.4590, 126.9400, "coast"),
        ("섭지코지", 33.4240, 126.9300, "coast"), ("종달리 해안도로", 33.4970, 126.9100, "coast"),
        ("월정리 해변", 33.5560, 126.7960, "coast"), ("함덕해변", 33.5430, 126.6690, "coast"),
        ("비자림", 33.4910, 126.8080, "forest"), ("사려니숲길", 33.4170, 126.6200, "forest"),
        ("1100고지", 33.3570, 126.4630, "mountain"), ("성판악", 33.3850, 126.6200, "mountain"),
        ("새별오름", 33.3660, 126.3570, "mountain"), ("다랑쉬오름", 33.4780, 126.8220, "mountain"),
        ("교래자연휴양림", 33.4440, 126.6690, "forest"), ("산굼부리", 33.4340, 126.6890, "mountain"),
        ("한라수목원", 33.4700, 126.4930, "forest"),
    ],
}

COUNTS = {"수도권": 30, "강원": 25, "충청": 20, "경상": 25, "전라": 20, "남해안": 15, "제주": 15}
ROAD = 1.3  # 직선 합을 도로 거리로 어림하는 배율
BANDS = [(15, 30), (32, 60), (62, 100), (102, 135)]
BAND_SHARE = [0.25, 0.35, 0.25, 0.15]
WORD = {"coast": "해안길", "river": "강변길", "lake": "호숫길", "mountain": "고갯길", "forest": "숲길", "city": "도심 드라이브"}
THEME_NAME = {"coast": "바다", "river": "강", "lake": "호수", "mountain": "산", "forest": "숲", "city": "도심"}
REAL_NAMES = {"애월 노을", "백운호수", "변산 노을", "청풍호", "달맞이 해안", "감포 해안", "곤지암 숲",
              "광교산", "한라 숲", "헌화로 해안", "만항재", "물미 해안", "팔당 노을", "섬진강", "양평 강변"}


def km(a, b):
    la1, lo1, la2, lo2 = map(math.radians, (a[1], a[2], b[1], b[2]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * 6371 * math.asin(math.sqrt(h))


def batchim(word):
    code = ord(word[-1]) - 0xAC00
    return 0 <= code < 11172 and code % 28 != 0


def obj(word):
    return word + ("을" if batchim(word) else "를")


def short(name):
    """앞의 시군 이름을 떼어 코스 이름을 짧게 한다. 떼면 너무 짧거나 일반 낱말만 남으면 그대로 둔다."""
    parts = name.split(" ")
    rest = " ".join(parts[1:])
    generic = {"전망대", "관광단지", "호수공원", "포구", "고인돌", "인삼시장", "선착장", "해안도로"}
    return rest if len(parts) > 1 and len(parts[0]) <= 3 and len(rest) >= 3 and rest not in generic else name


def chain(places, start, target, rnd, used_pairs):
    """출발에서 시작해 표기 거리가 [target] 구간에 들도록 지점을 잇는다. 못 맞추면 None."""
    lo, hi = target
    want = rnd.uniform(lo, hi) / ROAD
    legs = 2 if want < 25 else 3
    step = want / legs
    picked = [start]
    for _ in range(legs):
        here = picked[-1]
        best = None
        for p in places:
            if p in picked:
                continue
            d = km(here, p)
            if d < 3 or d > 45:
                continue
            # 앞 지점들로 되돌아가지 않게: 출발에서 멀어지는 쪽을 조금 더 친다.
            back = max(0.0, km(start, here) - km(start, p)) if len(picked) > 1 else 0.0
            score = abs(d - step) + back * 0.8 + rnd.random() * 2
            if best is None or score < best[0]:
                best = (score, p)
        if best is None:
            return None
        picked.append(best[1])
    total = sum(km(a, b) for a, b in zip(picked, picked[1:])) * ROAD
    if not lo <= total <= hi:
        return None
    key = frozenset((picked[0][0], picked[-1][0]))
    if key in used_pairs:
        return None
    return picked, round(total)


def tags(points, rnd):
    themes = []
    for p in points:
        if p[3] not in themes:
            themes.append(p[3])
    themes = sorted(themes, key=lambda t: t == "city")[:2]
    moods = rnd.sample(["alone", "date", "family"], rnd.choice([1, 1, 2]))
    if "coast" in themes:
        times = rnd.sample(["sunset", "sunrise", "day", "night"], 2)
    else:
        times = rnd.sample(["day", "sunset", "sunrise", "night"], rnd.choice([1, 2]))
    seasons = [rnd.choice(["spring", "summer", "autumn", "winter"])]
    wet = any(t in ("forest", "mountain", "lake") for t in themes)
    weathers = ["rain"] if rnd.random() < (0.4 if wet else 0.12) else []
    return {"themes": themes, "moods": moods, "times": times, "seasons": seasons, "weathers": weathers}


def make():
    rnd = random.Random(20261111)
    out = []
    names = set()
    for region, count in COUNTS.items():
        places = PLACES[region]
        starts_used = {p[0]: 0 for p in places}
        used_pairs = set()
        made = []
        bands = []
        for i, share in enumerate(BAND_SHARE):
            bands += [BANDS[i]] * round(count * share)
        bands = (bands + [BANDS[1]] * count)[:count]
        rnd.shuffle(bands)
        for target in bands:
            result = None
            for attempt in range(400):
                # 덜 쓴 출발지부터. 한 출발지는 두 번까지만.
                order = sorted(places, key=lambda p: (starts_used[p[0]], rnd.random()))
                start = order[0] if attempt < 200 else rnd.choice(places)
                if starts_used[start[0]] >= 2 and attempt < 200:
                    continue
                band = target if attempt < 300 else BANDS[(BANDS.index(target) + attempt) % len(BANDS)]
                result = chain(places, start, band, rnd, used_pairs)
                if result:
                    break
            if result is None:
                raise SystemExit(f"{region}: 코스를 만들지 못함")
            points, total = result
            starts_used[points[0][0]] += 1
            used_pairs.add(frozenset((points[0][0], points[-1][0])))
            made.append((points, total))
        for n, (points, total) in enumerate(made, 1):
            t = tags(points, rnd)
            word = WORD[t["themes"][0]]
            name = f"{short(points[0][0])}–{short(points[-1][0])} {word}"
            if name in names or name in REAL_NAMES:
                name = f"{short(points[0][0])}–{short(points[1][0])}–{short(points[-1][0])} {word}"
            names.add(name)
            vias = [p[0] for p in points[1:-1]]
            words = [THEME_NAME[x] for x in t["themes"]]
            scenery = words[0] if len(words) == 1 else words[0] + ("과 " if batchim(words[0]) else "와 ") + words[1]
            summary = f"{points[0][0]}에서 출발해 {obj(' · '.join(vias))} 지나 {points[-1][0]}까지, {obj(scenery)} 따라 달리는 {region} 길."
            out.append({
                "id": f"dummy-{list(COUNTS).index(region) + 1}-{n:02d}",
                "name": name,
                "region": region,
                "summary": summary,
                "km": total,
                "points": [{"name": p[0], "lat": p[1], "lon": p[2]} for p in points],
                "tags": t,
            })
    # 지역마다 비 오는 날 코스가 하나는 있게 한다.
    for region in COUNTS:
        mine = [c for c in out if c["region"] == region]
        if not any(c["tags"]["weathers"] for c in mine):
            mine[0]["tags"]["weathers"] = ["rain"]
    return out


if __name__ == "__main__":
    print(json.dumps(make(), ensure_ascii=False, indent=1))
