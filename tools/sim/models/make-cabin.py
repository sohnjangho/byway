"""시뮬레이터의 차 실내를 블렌더로 만든다. Pleos 커넥트 첫 적용 차량 '더 뉴 그랜저'(2026.05) 기준. 왼쪽 운전석. 운전자 눈은 (-0.37, 0, 1.2) m.

실차 근거(출시 기사: 뉴시스·아시아경제·더팩트·머니투데이 2026.05.14):
- 실내 중앙에 17인치 대형 디스플레이(Pleos 커넥트), 화면 분할 가능
- 운전자 전방 시선이 닿는 곳에 슬림 디스플레이를 추가(차속·변속단·경로)
- 전자식 변속 레버를 상하로 조작하는 레버 타입으로 변경
- 전동식 히든 에어벤트(돌출 노브 없음), 스마트 비전 루프(유리 천장)
가정(기사에 없음): 디스플레이 비율 16:9(에뮬레이터 2560×1440 과 같음, 37.6×21.2cm), 대시보드 가운데 매립,
운전대 원 스포크 모양, 변속 레버는 운전대 기둥 오른쪽, 실내 폭 1.56m(현행 그랜저 차폭 1,880mm 기준).
블렌더 좌표: X 오른쪽, Y 앞, Z 위(glTF 로 내보내면 three.js 에서 -Z 가 앞). 바닥 높이 0 = 도로면.
이름 붙은 부품: screen(센터 16:9), slim(운전석 슬림), ambient(조명 띠), window_fl/fr/rl/rr(창문), wheel(운전대), glass_*(유리).
사용: blender --background --python tools/sim/models/make-cabin.py -- <출력.glb>"""
import math
import sys

import bmesh
import bpy
from mathutils import Matrix, Vector

OUT = sys.argv[sys.argv.index('--') + 1]
bpy.ops.wm.read_factory_settings(use_empty=True)
COL = bpy.context.scene.collection
MATS = {}


def mat(name, rgba, rough=0.7, metal=0.0, emit=None):
    if name in MATS:
        return MATS[name]
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = m.node_tree.nodes['Principled BSDF']
    bsdf.inputs['Base Color'].default_value = rgba
    bsdf.inputs['Roughness'].default_value = rough
    bsdf.inputs['Metallic'].default_value = metal
    if rgba[3] < 1:
        bsdf.inputs['Alpha'].default_value = rgba[3]
        m.blend_method = 'BLEND' if hasattr(m, 'blend_method') else None
    if emit:
        bsdf.inputs['Emission Color'].default_value = emit
        bsdf.inputs['Emission Strength'].default_value = 3.0
    MATS[name] = m
    return m


def obj(name, bm, material, bevel=0.0):
    me = bpy.data.meshes.new(name)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    COL.objects.link(ob)
    me.materials.append(material)
    if bevel:
        mod = ob.modifiers.new('bevel', 'BEVEL')
        mod.width = bevel
        mod.segments = 2
    return ob


def box(name, center, size, material, rot=(0, 0, 0), bevel=0.0):
    bm = bmesh.new()
    m = Matrix.Translation(center) @ Matrix.Rotation(rot[2], 4, 'Z') @ Matrix.Rotation(rot[1], 4, 'Y') @ Matrix.Rotation(rot[0], 4, 'X') @ Matrix.Diagonal((*size, 1))
    bmesh.ops.create_cube(bm, size=1.0, matrix=m)
    return obj(name, bm, material, bevel)


def beam(name, a, b, thick, material):
    """두 점을 잇는 각진 기둥"""
    a, b = Vector(a), Vector(b)
    d = b - a
    rot = d.to_track_quat('Z', 'Y').to_matrix().to_4x4()
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0, matrix=Matrix.Translation((a + b) / 2) @ rot @ Matrix.Diagonal((thick, thick * 0.8, d.length, 1)))
    return obj(name, bm, material, 0.01)


def plane(name, center, w, h, material, tilt=0.0, face='y'):
    """화면·유리 판. face='y' 면 운전자(-Y)를 보고, 'x' 면 옆을 본다"""
    bm = bmesh.new()
    if face == 'y':
        m = Matrix.Translation(center) @ Matrix.Rotation(math.radians(90) - tilt, 4, 'X') @ Matrix.Diagonal((w, h, 1, 1))
    else:
        m = Matrix.Translation(center) @ Matrix.Rotation(math.radians(90), 4, 'Y') @ Matrix.Rotation(math.radians(90), 4, 'Z') @ Matrix.Diagonal((w, h, 1, 1))
    bmesh.ops.create_grid(bm, x_segments=1, y_segments=1, size=0.5, matrix=m)
    return obj(name, bm, material)


dark = mat('trim_dark', (0.03, 0.032, 0.036, 1), 0.6)
mid = mat('trim_mid', (0.09, 0.09, 0.1, 1), 0.5)
leather = mat('leather', (0.16, 0.13, 0.11, 1), 0.55)
liner = mat('headliner', (0.55, 0.53, 0.5, 1), 0.9)
body = mat('body', (0.75, 0.76, 0.78, 1), 0.3, 0.6)
chrome = mat('chrome', (0.55, 0.56, 0.58, 1), 0.2, 1.0)
glass = mat('glass', (0.6, 0.7, 0.75, 0.12), 0.05)
screen = mat('screen', (0, 0, 0, 1), 0.2)
light = mat('ambient', (1, 0.85, 0.6, 1), 0.5, 0, (1, 0.85, 0.6, 1))

# 바닥, 천장, 문
box('floor', (0, -0.5, 0.18), (1.56, 3.1, 0.04), dark)
for name, c, s in (('roof_front', (0, 0.12, 1.52), (1.5, 0.3, 0.05)), ('roof_rear', (0, -1.6, 1.52), (1.5, 0.5, 0.05)), ('roof_mid', (0, -0.48, 1.52), (1.5, 0.12, 0.05)),
                  ('roof_l', (-0.68, -0.6, 1.52), (0.14, 2.2, 0.05)), ('roof_r', (0.68, -0.6, 1.52), (0.14, 2.2, 0.05))):
    box(name, c, s, liner, bevel=0.01)
plane('glass_roof', (0, -0.62, 1.545), 1.2, 1.8, glass, tilt=math.radians(90))
for s, n in ((-1, 'l'), (1, 'r')):
    box(f'door_{n}', (s * 0.81, -0.55, 0.6), (0.07, 2.7, 0.76), leather, bevel=0.015)
    box(f'sill_{n}', (s * 0.78, -0.55, 0.97), (0.1, 2.7, 0.04), dark, bevel=0.01)
    box(f'armrest_{n}', (s * 0.74, -0.35, 0.72), (0.07, 0.5, 0.05), mid, bevel=0.01)
# 대시보드와 보닛
box('dash', (0, 0.84, 0.83), (1.62, 0.6, 0.3), dark, bevel=0.06)
box('dash_top', (0, 0.98, 0.985), (1.62, 0.5, 0.03), mid, bevel=0.01)
box('vent', (0, 0.54, 0.86), (1.3, 0.02, 0.012), dark)
box('vent_low', (0, 0.6, 0.72), (1.1, 0.02, 0.01), dark)
box('ambient', (0, 0.535, 0.905), (1.45, 0.008, 0.008), light)
box('fascia', (0, 0.48, 0.52), (0.3, 0.42, 0.56), dark, bevel=0.03)
box('console', (0, -0.3, 0.45), (0.25, 1.0, 0.2), mid, bevel=0.03)
box('hood', (0, 1.75, 0.86), (1.7, 1.5, 0.05), body, rot=(math.radians(-4), 0, 0), bevel=0.04)
# 센터 화면(16:9, 약 17인치)과 운전석 슬림 화면
# 화면 아랫변이 대시보드 윗면(약 1.0m) 바로 위에 오도록 세운다. 묻히면 아래쪽이 잘린다
box('screen_bezel', (0.02, 0.675, 1.115), (0.405, 0.025, 0.24), dark, rot=(math.radians(-12), 0, 0), bevel=0.008)
plane('screen', (0.02, 0.66, 1.115), 0.376, 0.2115, screen, tilt=math.radians(12))
# 슬림 화면: 덮개는 화면 판 뒤에만 둔다(앞에 오면 화면을 가린다)
plane('slim', (-0.37, 0.70, 1.03), 0.32, 0.072, screen, tilt=math.radians(15))
box('slim_back', (-0.37, 0.725, 1.025), (0.34, 0.03, 0.09), dark, rot=(math.radians(-15), 0, 0), bevel=0.008)
# 운전대
bpy.ops.mesh.primitive_torus_add(major_radius=0.185, minor_radius=0.019, major_segments=40, minor_segments=10, location=(-0.37, 0.42, 0.86), rotation=(math.radians(65), 0, 0))
wheel = bpy.context.active_object
wheel.name = 'wheel'
wheel.data.materials.append(leather)
for ang in (270,):
    a = math.radians(ang)
    spoke = beam('spoke', (-0.37, 0.42, 0.86), (-0.37 + math.cos(a) * 0.17, 0.42 + math.sin(a) * 0.17 * math.cos(math.radians(65)), 0.86 + math.sin(a) * 0.17 * math.sin(math.radians(65))), 0.03, mid)
box('hub', (-0.37, 0.42, 0.86), (0.16, 0.045, 0.08), mid, rot=(math.radians(-25), 0, 0), bevel=0.02)
beam('column', (-0.37, 0.45, 0.84), (-0.37, 0.7, 0.74), 0.07, dark)
beam('shifter', (-0.33, 0.52, 0.81), (-0.2, 0.47, 0.84), 0.022, chrome)
# 기둥과 앞뒤 유리 틀
for s, n in ((-1, 'l'), (1, 'r')):
    beam(f'apillar_{n}', (s * 0.77, 0.98, 0.98), (s * 0.66, 0.22, 1.5), 0.08, dark)
    beam(f'bpillar_{n}', (s * 0.79, -0.48, 0.97), (s * 0.71, -0.48, 1.5), 0.11, dark)
    beam(f'cpillar_{n}', (s * 0.77, -1.6, 0.97), (s * 0.64, -1.25, 1.5), 0.13, dark)
beam('header', (-0.66, 0.22, 1.5), (0.66, 0.22, 1.5), 0.08, dark)
beam('rear_header', (-0.64, -1.25, 1.5), (0.64, -1.25, 1.5), 0.08, dark)
beam('rear_sill', (-0.72, -1.82, 1.0), (0.72, -1.82, 1.0), 0.06, dark)
box('shelf', (0, -1.72, 0.98), (1.4, 0.32, 0.03), dark)
# 유리: 앞·뒤는 고정, 옆 창문 네 장은 내릴 수 있다
plane('glass_front', (0, 0.6, 1.24), 1.4, 0.82, glass, tilt=math.radians(58))
plane('glass_rear', (0, -1.55, 1.24), 1.25, 0.6, glass, tilt=math.radians(-55))
for s, n in ((-1, 'l'), (1, 'r')):
    plane(f'window_f{n}', (s * 0.8, -0.13, 1.22), 0.66, 0.46, glass, face='x')
    plane(f'window_r{n}', (s * 0.79, -1.03, 1.22), 0.95, 0.44, glass, face='x')
# 좌석
for x, n in ((-0.37, 'driver'), (0.37, 'passenger')):
    box(f'seat_{n}', (x, -0.33, 0.45), (0.52, 0.52, 0.13), leather, bevel=0.04)
    box(f'back_{n}', (x, -0.62, 0.82), (0.52, 0.11, 0.66), leather, rot=(math.radians(-12), 0, 0), bevel=0.04)
    box(f'head_{n}', (x, -0.69, 1.25), (0.26, 0.09, 0.17), leather, bevel=0.03)
box('seat_rear', (0, -1.33, 0.45), (1.32, 0.5, 0.13), leather, bevel=0.04)
box('back_rear', (0, -1.58, 0.8), (1.32, 0.11, 0.6), leather, rot=(math.radians(-15), 0, 0), bevel=0.04)
# 거울
box('mirror', (0, 0.4, 1.38), (0.25, 0.03, 0.075), dark, bevel=0.01)
beam('mirror_stem', (0, 0.42, 1.42), (0, 0.32, 1.5), 0.02, dark)
for s, n in ((-1, 'l'), (1, 'r')):
    box(f'sidemirror_{n}', (s * 0.98, 0.62, 1.02), (0.07, 0.16, 0.11), body, bevel=0.02)
    box(f'sidemirror_glass_{n}', (s * 0.98, 0.535, 1.02), (0.055, 0.005, 0.09), chrome)

for ob in COL.objects:
    if ob.modifiers:
        bpy.context.view_layer.objects.active = ob
        for m in list(ob.modifiers):
            bpy.ops.object.modifier_apply(modifier=m.name)
bpy.ops.export_scene.gltf(filepath=OUT, export_format='GLB', export_apply=True, export_yup=True)
print('objects', len(COL.objects))
