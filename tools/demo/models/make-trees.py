"""재현 영상의 3D 도로에 심을 나무를 블렌더로 만든다. 활엽수 3종(줄기·가지 + 잎 뭉치), 침엽수 2종(처진 원뿔 층).
잎은 흰 바탕에 꼭짓점 색으로 위아래 밝기만 주고, 단풍색은 three.js 가 나무마다 곱한다. 높이는 약 7m.
사용: blender --background --python tools/demo/models/make-trees.py -- <출력.glb>"""
import math
import random
import sys

import bmesh
import bpy
from mathutils import Vector, noise

OUT = sys.argv[sys.argv.index('--') + 1]
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


def paint(ob, shade):
    """꼭짓점 색. shade(위치) 가 0~1 밝기를 돌려준다"""
    me = ob.data
    attr = me.color_attributes.new(name='Col', type='BYTE_COLOR', domain='CORNER')
    for poly in me.polygons:
        for li in poly.loop_indices:
            k = max(0.0, min(1.0, shade(me.vertices[me.loops[li].vertex_index].co)))
            attr.data[li].color = (k, k, k, 1.0)
    me.color_attributes.active_color = attr


def material(ob, name, color):
    mat = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    mat.diffuse_color = color
    ob.data.materials.append(mat)


def wood(name, rnd, branches, height):
    """줄기와 가지 뼈대에 Skin 수정자로 살을 붙인다. 가지 끝 좌표를 돌려준다"""
    bm = bmesh.new()
    base = bm.verts.new((0, 0, 0))
    mid = bm.verts.new((rnd.uniform(-0.1, 0.1), rnd.uniform(-0.1, 0.1), height * 0.45))
    top = bm.verts.new((rnd.uniform(-0.2, 0.2), rnd.uniform(-0.2, 0.2), height * 0.7))
    bm.edges.new((base, mid)); bm.edges.new((mid, top))
    tips = [top.co.copy()]
    for i in range(branches):
        a = i / branches * math.tau + rnd.uniform(-0.4, 0.4)
        start = mid if i % 2 else top
        reach = rnd.uniform(1.1, 1.8)
        tip = bm.verts.new((start.co.x + math.cos(a) * reach, start.co.y + math.sin(a) * reach, start.co.z + rnd.uniform(1.0, 1.8)))
        bm.edges.new((start, tip))
        tips.append(tip.co.copy())
    ob = link(name, bm)
    skin = ob.modifiers.new('skin', 'SKIN')
    for v, sv in zip(ob.data.vertices, ob.data.skin_vertices[0].data):
        r = 0.32 if v.co.z < 0.1 else 0.2 if v.co.z < height * 0.5 else 0.14 if (v.co.xy.length < 0.3) else 0.06
        sv.radius = (r, r)
    ob.modifiers.new('sub', 'SUBSURF').levels = 1
    apply_all(ob)
    material(ob, 'wood', (0.35, 0.26, 0.18, 1))
    return ob, tips


def blob(bm, center, radius, rnd):
    """노이즈로 울퉁불퉁하게 만든 잎 뭉치 하나"""
    made = bmesh.ops.create_icosphere(bm, subdivisions=2, radius=radius)
    seed = Vector((rnd.random() * 10, rnd.random() * 10, rnd.random() * 10))
    for v in made['verts']:
        n = v.co.normalized()
        v.co = v.co + n * noise.noise(v.co * 0.9 + seed) * radius * 0.35
        v.co.z *= 0.85
        v.co += center


def deciduous(i):
    rnd = random.Random(10 + i)
    height = 4.6 + rnd.uniform(-0.3, 0.4)
    trunk, tips = wood(f'deciduous_{i}_wood', rnd, 4 + i % 2, height)
    bm = bmesh.new()
    for tip in tips:
        blob(bm, tip + Vector((0, 0, 0.5)), rnd.uniform(1.1, 1.5), rnd)
    for _ in range(4):
        a = rnd.uniform(0, math.tau)
        blob(bm, Vector((math.cos(a) * 1.1, math.sin(a) * 1.1, height + rnd.uniform(0.2, 1.4))), rnd.uniform(1.0, 1.4), rnd)
    leaf = link(f'deciduous_{i}_leaf', bm)
    dec = leaf.modifiers.new('dec', 'DECIMATE'); dec.ratio = 0.55
    apply_all(leaf)
    zs = [v.co.z for v in leaf.data.vertices]
    lo, hi = min(zs), max(zs)
    paint(leaf, lambda co: 0.5 + 0.5 * (co.z - lo) / (hi - lo) + noise.noise(co * 1.7) * 0.12)
    material(leaf, 'leaf', (1, 1, 1, 1))
    for ob in (trunk, leaf):
        ob.location.x = i * 8


def conifer(i):
    rnd = random.Random(30 + i)
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=7, radius1=0.22, radius2=0.1, depth=2.4, matrix=__import__('mathutils').Matrix.Translation((0, 0, 1.2)))
    trunk = link(f'conifer_{i}_wood', bm)
    material(trunk, 'wood', (0.35, 0.26, 0.18, 1))
    bm = bmesh.new()
    layers = 5
    for k in range(layers):
        r = 2.0 - k * 0.36
        z = 1.8 + k * 1.15
        made = bmesh.ops.create_cone(bm, cap_ends=True, segments=11, radius1=r, radius2=0.05, depth=2.2, matrix=__import__('mathutils').Matrix.Translation((0, 0, z + 1.1)))
        for v in made['verts']:
            if v.co.z < z + 0.2:
                v.co.z -= rnd.uniform(0.1, 0.55)
                v.co.x *= rnd.uniform(0.85, 1.12); v.co.y *= rnd.uniform(0.85, 1.12)
    leaf = link(f'conifer_{i}_leaf', bm)
    zs = [v.co.z for v in leaf.data.vertices]
    lo, hi = min(zs), max(zs)
    paint(leaf, lambda co: 0.55 + 0.45 * (co.z - lo) / (hi - lo) - (0.12 if co.xy.length < 0.8 else 0))
    material(leaf, 'leaf', (1, 1, 1, 1))
    for ob in (trunk, leaf):
        ob.location.x = (3 + i) * 8


for i in range(3):
    deciduous(i)
for i in range(2):
    conifer(i)
for ob in bpy.context.scene.objects:
    ob.data.polygons.foreach_set('use_smooth', [False] * len(ob.data.polygons))
opts = dict(filepath=OUT, export_format='GLB', export_apply=True, export_yup=True)
try:
    bpy.ops.export_scene.gltf(**opts, export_vertex_color='ACTIVE')
except TypeError:
    bpy.ops.export_scene.gltf(**opts)
for ob in bpy.context.scene.objects:
    print(ob.name, len(ob.data.polygons))
