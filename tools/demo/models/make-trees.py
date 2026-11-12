"""재현 영상의 3D 도로에 심을 나무를 블렌더로 만든다. 한국에서 흔한 수종 5가지.
  deciduous_0 느티나무 · deciduous_1 단풍나무 · deciduous_2 은행나무 · conifer_0 소나무(적송) · conifer_1 메타세쿼이아
잎은 흰 바탕에 꼭짓점 색 'Col' 로 밝기(아래·안쪽 어둡게)만 주고, 계절색은 three.js 가 나무마다 곱한다.
줄기에도 'Col' 을 넣는다(대부분 흰색, 소나무만 윗줄기가 불그스름). 기준 높이는 약 7m.
사용: blender --background --python tools/demo/models/make-trees.py -- <출력.glb>"""
import math
import random
import sys

import bmesh
import bpy
from mathutils import Vector, noise

OUT = sys.argv[sys.argv.index('--') + 1]
LEAF_BUDGET = 2600    # 한 종 잎 삼각형 상한(Decimate 비율을 여기에 맞춘다)
WOOD_BUDGET = 700     # 한 종 줄기 삼각형 상한. 잎+줄기 6,000 이하(시뮬레이터가 약 3,700 그루를 InstancedMesh 로 그린다)
NORMAL_BLEND = 0.4    # 잎 법선에 섞을 수관 중심 방향 비율(뭉치들이 한 덩어리 수관으로 읽히게)
bpy.ops.wm.read_factory_settings(use_empty=True)


def link(name, bm):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    bpy.context.scene.collection.objects.link(ob)
    return ob


def apply_all(ob):
    bpy.context.view_layer.objects.active = ob
    ob.select_set(True)
    for m in list(ob.modifiers):
        bpy.ops.object.modifier_apply(modifier=m.name)
    ob.select_set(False)


def tris(ob):
    return sum(len(p.vertices) - 2 for p in ob.data.polygons)


def cap(ob, budget):
    """삼각형이 예산을 넘으면 Decimate(collapse) 로 줄인다"""
    n = tris(ob)
    if n > budget:
        dec = ob.modifiers.new('dec', 'DECIMATE'); dec.ratio = budget / n
        apply_all(ob)


def paint(ob, shade):
    """꼭짓점 색 'Col'. shade(꼭짓점) 이 밝기(0~1) 또는 (r, g, b) 를 돌려준다"""
    me = ob.data
    attr = me.color_attributes.new(name='Col', type='BYTE_COLOR', domain='CORNER')
    cache = {}
    for v in me.vertices:
        k = shade(v)
        k = (k, k, k) if isinstance(k, float) else k
        cache[v.index] = tuple(max(0.0, min(1.0, c)) for c in k) + (1.0,)
    for li, loop in enumerate(me.loops):
        attr.data[li].color = cache[loop.vertex_index]
    me.color_attributes.active_color = attr


def material(ob, name, color):
    mat = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    mat.diffuse_color = color
    ob.data.materials.append(mat)


# ---------- 줄기: 뼈대(선) 에 Skin 수정자로 살을 붙인다 ----------
class Skeleton:
    def __init__(self):
        self.bm = bmesh.new()
        self.radii = []

    def chain(self, start, pts):
        """start(꼭짓점 또는 None) 에서 pts[(좌표, 반지름)] 를 이어 붙이고 마지막 꼭짓점을 돌려준다"""
        prev = start
        for co, r in pts:
            v = self.bm.verts.new(co)
            self.radii.append(r)
            if prev is not None:
                self.bm.edges.new((prev, v))
            prev = v
        return prev

    def build(self, name, color=None):
        radii = self.radii
        ob = link(name, self.bm)
        ob.modifiers.new('skin', 'SKIN')
        for sv, r in zip(ob.data.skin_vertices[0].data, radii):
            sv.radius = (r, r)
        ob.modifiers.new('sub', 'SUBSURF').levels = 1
        apply_all(ob)
        cap(ob, WOOD_BUDGET)
        paint(ob, color or (lambda v: 1.0))
        material(ob, 'wood', (0.35, 0.26, 0.18, 1))
        return ob


def bend(a, b, n, sag=0.0, wobble=0.0, rnd=None):
    """a→b 를 n 토막 낸 점들(a 제외). sag 만큼 가운데가 처지고 wobble 만큼 흔들린다"""
    out = []
    for k in range(1, n + 1):
        t = k / n
        p = a.lerp(b, t) + Vector((0, 0, -sag * 4 * t * (1 - t)))
        if rnd and k < n:
            p += Vector((rnd.uniform(-1, 1), rnd.uniform(-1, 1), rnd.uniform(-0.3, 0.3))) * wobble
        out.append(p)
    return out


# ---------- 잎 뭉치 수관 ----------
def blob(bm, center, radius, zs, rnd):
    """노이즈 두 옥타브로 울퉁불퉁하게 만든 잎 뭉치 하나(zs: 세로 납작함). 만든 면을 돌려준다"""
    made = bmesh.ops.create_icosphere(bm, subdivisions=3, radius=radius)
    seed = Vector((rnd.random() * 10, rnd.random() * 10, rnd.random() * 10))
    for v in made['verts']:
        n = v.co.normalized()
        # 큰 혹(낮은 주파수) + 잔 혹(높은 주파수)
        d = noise.noise(v.co * 0.8 + seed) * 0.42 + noise.noise(v.co * 3.2 + seed * 1.7) * 0.22
        v.co = v.co + n * d * radius
        v.co.z *= zs
        v.co += center
    return {f for v in made['verts'] for f in v.link_faces}


def inside(p, c, r, zs, keep):
    d = p - c
    d.z /= zs
    return d.length < r * keep


def crown(name, spec, rnd, blend=NORMAL_BLEND):
    """spec[(중심, 반지름, 납작함)] 으로 잎 뭉치를 만들어 하나의 수관 물체로 묶는다"""
    bm = bmesh.new()
    blobs = [(blob(bm, c, r, zs, rnd), c, r, zs) for c, r, zs in spec]
    # 다른 뭉치 속에 묻혀 안 보이는 면을 지워 삼각형을 아낀다
    dead = set()
    for i, (faces, _, _, _) in enumerate(blobs):
        for f in faces:
            p = f.calc_center_median()
            if any(inside(p, c, r, zs, 0.6) for j, (_, c, r, zs) in enumerate(blobs) if j != i):
                dead.add(f)
    bmesh.ops.delete(bm, geom=list(dead), context='FACES')
    leaf = link(name, bm)
    cap(leaf, LEAF_BUDGET)
    me = leaf.data
    zs_ = [v.co.z for v in me.vertices]
    lo, hi = min(zs_), max(zs_)
    mid = sum((v.co for v in me.vertices), Vector()) / len(me.vertices)
    reach = max((v.co - mid).length for v in me.vertices)

    def raw(v):
        h = (v.co.z - lo) / (hi - lo)                   # 위일수록 밝게
        out = min(1.0, (v.co - mid).length / reach)     # 바깥일수록 밝게
        up = v.normal.z * 0.5 + 0.5                     # 아래를 향한 면은 그늘
        return 0.4 * h + 0.35 * out ** 1.5 + 0.25 * up + noise.noise(v.co * 1.7) * 0.05
    vals = [raw(v) for v in me.vertices]
    a, b = min(vals), max(vals)
    paint(leaf, lambda v: 0.35 + 0.65 * (vals[v.index] - a) / (b - a))   # 나무마다 0.35~1.0 으로 편다
    me.normals_split_custom_set_from_vertices([
        (v.normal * (1 - blend) + (v.co - mid).normalized() * blend).normalized() for v in me.vertices])
    material(leaf, 'leaf', (1, 1, 1, 1))
    return leaf


def ring(rnd, n, cx, cy, cz, rx, rz, r_lo, r_hi, zs, jitter=0.3):
    """중심 둘레에 뭉치 n 개를 고리로 놓는 spec"""
    out = []
    turn = rnd.uniform(0, math.tau)
    for k in range(n):
        a = turn + k / n * math.tau + rnd.uniform(-jitter, jitter)
        out.append((Vector((cx + math.cos(a) * rx, cy + math.sin(a) * rx, cz + rnd.uniform(-rz, rz))), rnd.uniform(r_lo, r_hi), zs))
    return out


# ---------- 침엽수 가지 층 ----------
def tier(bm, rnd, edge_z, h, r, segs=22, rings=4, spiky=1.0, droop=1.0):
    """가지 층 하나. 윗면은 가장자리로 갈수록 처지고 끝은 가지 끝처럼 들쭉날쭉하다.
    아랫면은 줄기 쪽으로 오목하게 닫는다. (아랫면 꼭짓점, 가장자리 변) 을 돌려준다"""
    turn = rnd.uniform(0, math.tau)
    apex = bm.verts.new((rnd.uniform(-0.04, 0.04), rnd.uniform(-0.04, 0.04), edge_z + h))
    edge = []
    for k in range(segs):
        a = turn + k / segs * math.tau
        spike = (1.0 if k % 2 else 0.0) * rnd.uniform(0.6, 1.0) + rnd.uniform(-0.25, 0.25)
        re = r * (0.88 + 0.22 * spike * spiky)                          # 가지 끝은 더 멀리
        drop = (rnd.uniform(0.05, 0.45) + 0.2 * spike * spiky) * droop  # 가지 끝은 더 늘어진다
        edge.append((a, re, drop))
    rs = []
    for j in range(1, rings + 1):
        s = j / rings
        row = []
        for a, re, drop in edge:
            # 위는 곧게 내려오다 바깥에서 휘어 처지는 단면
            z = edge_z + h - h * (0.8 * s + 0.2 * s ** 3) - drop * s ** 3
            rr = re * s * (1 - 0.06 * s * s)
            row.append(bm.verts.new((math.cos(a) * rr, math.sin(a) * rr, z)))
        rs.append(row)
    for k in range(segs):
        bm.faces.new((apex, rs[0][k], rs[0][(k + 1) % segs]))
    for j in range(rings - 1):
        for k in range(segs):
            q = (k + 1) % segs
            bm.faces.new((rs[j][k], rs[j + 1][k], rs[j + 1][q], rs[j][q]))
    # 아랫면: 가장자리 → 안쪽 고리 → 줄기 밑 한 점
    inner = [bm.verts.new((math.cos(a) * re * 0.45, math.sin(a) * re * 0.45, edge_z + h * 0.18)) for a, re, _ in edge]
    hub = bm.verts.new((0, 0, edge_z + h * 0.42))
    rim = rs[-1]
    for k in range(segs):
        q = (k + 1) % segs
        bm.faces.new((rim[q], inner[q], inner[k], rim[k]))
        bm.faces.new((hub, inner[k], inner[q]))
    sharp = [bm.edges.get((rim[k], rim[(k + 1) % segs])) for k in range(segs)]
    sharp += [bm.edges.get((rim[k], rs[-2][k])) for k in range(segs)]   # 가장자리 날을 세운다
    return inner + [hub], sharp


# ---------- 수종 ----------
def zelkova(name, rnd):
    """느티나무: 짧고 굵은 줄기가 낮게 갈라져 부채꼴로 퍼지고, 수관은 넓고 둥글다(높이 7m, 폭 7~8m)"""
    sk = Skeleton()
    fork = sk.chain(None, [(Vector((0, 0, 0)), 0.42), (Vector((0, 0, 0.9)), 0.36), (Vector((0.05, 0, 1.8)), 0.33)])
    tips = []
    limbs = 6
    for k in range(limbs):
        a = k / limbs * math.tau + rnd.uniform(-0.3, 0.3)
        out = Vector((math.cos(a), math.sin(a), 0))
        mid = Vector((0, 0, 1.8)) + out * 1.0 + Vector((0, 0, 1.3))
        end = Vector((0, 0, 1.8)) + out * rnd.uniform(2.3, 2.8) + Vector((0, 0, rnd.uniform(2.3, 2.8)))
        sk.chain(fork, [(mid, 0.17), (end, 0.07)])
        tips.append(end)
    sk.chain(fork, [(Vector((0.1, 0, 3.4)), 0.16), (Vector((0, 0.1, 4.8)), 0.08)])
    wood = sk.build(name + '_wood')
    spec = [(t + Vector((0, 0, 0.4)), rnd.uniform(1.2, 1.5), 0.75) for t in tips]
    spec += ring(rnd, 8, 0, 0, 5.4, 2.4, 0.3, 1.0, 1.3, 0.75)      # 위쪽 고리
    spec += ring(rnd, 5, 0, 0, 6.0, 1.1, 0.2, 1.1, 1.3, 0.7)       # 꼭대기
    spec += [(Vector((0, 0, 5.0)), 2.0, 0.6)]                       # 속을 채워 틈으로 하늘이 보이지 않게
    return wood, crown(name + '_leaf', spec, rnd)


def maple(name, rnd):
    """단풍나무: 작은 키(5~6m), 밑에서 여러 갈래로 갈라진 줄기, 납작한 수관이 층층이 옆으로 퍼진다"""
    sk = Skeleton()
    base = sk.chain(None, [(Vector((0, 0, 0)), 0.2)])
    tips = []
    for k in range(3):
        a = k / 3 * math.tau + rnd.uniform(-0.3, 0.3)
        out = Vector((math.cos(a), math.sin(a), 0))
        p1 = out * 0.5 + Vector((0, 0, 1.4))
        p2 = out * 1.2 + Vector((0, 0, 2.7))
        p3 = out * 1.9 + Vector((rnd.uniform(-0.3, 0.3), rnd.uniform(-0.3, 0.3), 3.8))
        end = sk.chain(base, [(p1, 0.13), (p2, 0.09), (p3, 0.05)])
        tips.append(p3)
        side = Vector((math.cos(a + 1.1), math.sin(a + 1.1), 0))
        sk.chain(end, [(p3 + side * 0.9 + Vector((0, 0, 0.5)), 0.03)])
    wood = sk.build(name + '_wood')
    spec = []
    for z, rx, n, r in ((3.0, 2.2, 7, 0.95), (3.9, 1.7, 6, 0.9), (4.7, 0.9, 4, 0.8)):  # 층층이 납작한 잎 판
        spec += ring(rnd, n, 0, 0, z, rx, 0.15, r * 0.9, r * 1.1, 0.5)
    spec += [(Vector((0, 0, 3.6)), 1.5, 0.55)]
    return wood, crown(name + '_leaf', spec, rnd)


def ginkgo(name, rnd):
    """은행나무: 곧은 줄기에 짧게 비스듬히 오르는 가지, 위로 좁고 긴 타원 수관(가로수)"""
    sk = Skeleton()
    pts = [(Vector((rnd.uniform(-0.03, 0.03), rnd.uniform(-0.03, 0.03), z)), 0.27 - 0.025 * z) for z in (0, 1.2, 2.4, 3.6, 4.8, 6.0, 7.0)]
    trunk = [sk.chain(None, pts[:1])]
    for co, r in pts[1:]:
        trunk.append(sk.chain(trunk[-1], [(co, r)]))
    for k, z in enumerate((2.6, 3.4, 4.2, 5.0, 5.8)):
        a = k * 2.4 + rnd.uniform(-0.3, 0.3)
        v = trunk[min(len(trunk) - 1, int(z / 1.2))]
        o = Vector((math.cos(a), math.sin(a), 0)) * (1.0 - 0.1 * k)
        sk.chain(v, [(v.co + o + Vector((0, 0, 0.9)), 0.04)])
    wood = sk.build(name + '_wood')
    spec = []
    for z, rx, n in ((2.9, 0.55, 4), (3.8, 0.95, 5), (4.8, 1.0, 5), (5.8, 0.7, 4), (6.7, 0.3, 3)):
        spec += ring(rnd, n, 0, 0, z, rx, 0.2, 0.75, 0.95, 0.85, jitter=0.5)
    spec += [(Vector((0, 0, 4.6)), 1.1, 1.6)]
    return wood, crown(name + '_leaf', spec, rnd, blend=0.45)


def pine(name, rnd):
    """소나무(적송): S자로 굽은 줄기(위쪽 불그스름), 옆으로 뻗은 가지 끝에 우산처럼 납작한 잎 덩어리"""
    sk = Skeleton()
    s_pts = [(0, 0, 0), (0.35, 0.05, 1.4), (0.05, -0.1, 2.7), (-0.35, 0.05, 3.9), (-0.1, 0.15, 5.0), (0.25, 0.1, 5.9)]
    trunk = [sk.chain(None, [(Vector(s_pts[0]), 0.3)])]
    for k, p in enumerate(s_pts[1:], 1):
        trunk.append(sk.chain(trunk[-1], [(Vector(p), 0.3 - 0.035 * k)]))
    pads = []
    for k, (ti, ln) in enumerate(((2, 2.2), (3, 2.0), (3, 1.7), (4, 1.6), (5, 1.1))):
        v = trunk[ti]
        a = k * 2.3 + rnd.uniform(-0.4, 0.4)
        end = v.co + Vector((math.cos(a) * ln, math.sin(a) * ln, rnd.uniform(0.3, 0.8)))
        sk.chain(v, [(p, 0.08 - 0.02 * i) for i, p in enumerate(bend(v.co, end, 2, sag=-0.2))])
        pads.append(end)
    pads.append(trunk[-1].co.copy())

    def bark(v):
        t = max(0.0, min(1.0, (v.co.z - 1.5) / 2.5))   # 아래 회갈색 → 위 적갈색
        return (0.78 + 0.22 * t, 0.74 - 0.16 * t, 0.70 - 0.30 * t)
    wood = sk.build(name + '_wood', bark)
    spec = []
    for p in pads:
        spec.append((p + Vector((0, 0, 0.35)), rnd.uniform(1.0, 1.25), 0.42))  # 납작한 잎 판
        spec += ring(rnd, 3, p.x, p.y, p.z + 0.3, 0.8, 0.1, 0.6, 0.8, 0.45)
    return wood, crown(name + '_leaf', spec, rnd, blend=0.2)


def metasequoia(name, rnd):
    """메타세쿼이아: 높고 좁은 원뿔(폭:높이 ≈ 1:4), 촘촘한 층이 부드럽게 늘어진다"""
    sk = Skeleton()
    pts = [(Vector((0, 0, z)), 0.3 - 0.026 * z) for z in (0, 2.5, 5.0, 7.5, 9.6)]
    v = sk.chain(None, pts[:1])
    for co, r in pts[1:]:
        v = sk.chain(v, [(co, r)])
    wood = sk.build(name + '_wood')
    bm = bmesh.new()
    under, sharp = set(), []
    layers = 13
    for k in range(layers):
        t = k / (layers - 1)
        r = 1.35 * (1 - t) ** 0.9 + 0.2 + rnd.uniform(-0.06, 0.06)
        edge_z = 1.3 + t * 8.2 + rnd.uniform(-0.05, 0.05)
        h = 0.95 + 0.6 * t                              # 꼭대기 층이 뾰족하게 솟는다
        bottom, rim = tier(bm, rnd, edge_z, h, r, segs=18, rings=4, spiky=0.6, droop=0.7)
        under.update(bottom)
        sharp += rim
    for e in sharp:
        if e is not None:
            e.smooth = False
    low = {v.co.copy().freeze() for v in under}
    leaf = link(name + '_leaf', bm)
    zs = [v.co.z for v in leaf.data.vertices]
    lo, hi = min(zs), max(zs)

    def shade(v):
        h = (v.co.z - lo) / (hi - lo)
        if v.co.copy().freeze() in low:
            return 0.35 + 0.1 * h                        # 층 아랫면은 그늘
        out = min(1.0, v.co.xy.length / (1.55 * (1 - 0.85 * h) + 0.2))
        return 0.42 + 0.58 * (0.45 * h + 0.55 * out) + noise.noise(v.co * 2.1) * 0.05
    paint(leaf, shade)
    material(leaf, 'leaf', (1, 1, 1, 1))
    return wood, leaf


KINDS = [('deciduous_0', zelkova), ('deciduous_1', maple), ('deciduous_2', ginkgo), ('conifer_0', pine), ('conifer_1', metasequoia)]
for slot, (name, make) in enumerate(KINDS):
    for ob in make(name, random.Random(10 + slot)):
        ob.location.x = slot * 9
# 줄기·잎 모두 부드럽게. 메타세쿼이아 층 가장자리만 위에서 표시한 날(sharp edge)로 꺾인다
for ob in bpy.context.scene.objects:
    ob.data.polygons.foreach_set('use_smooth', [True] * len(ob.data.polygons))
opts = dict(filepath=OUT, export_format='GLB', export_apply=True, export_yup=True)
try:
    bpy.ops.export_scene.gltf(**opts, export_vertex_color='ACTIVE')
except TypeError:
    bpy.ops.export_scene.gltf(**opts)
for ob in bpy.context.scene.objects:
    print(ob.name, tris(ob), 'tris')
