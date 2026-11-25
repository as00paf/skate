# Skate engine: USD -> glTF conversion for the asset store (AST-16e). Run by the editor, never by hand:
#   blender -b --factory-startup --python-exit-code 1 --python skate_usd_to_gltf.py -- <args.json>
# The arguments are a JSON file (Windows mangles quotes in command-line arguments): input (the root .usd/.usda/.usdc/
# .usdz), out_dir (empty folder to write into), name (output base name). Writes <name>.gltf, <name>.bin and the
# textures next to them (glTF separate: textures stay PNG/JPG files), then report.json.
import bpy
import json
import os
import sys

PREVIEW_NODES = {
    "ShaderNodeBsdfPrincipled", "ShaderNodeTexImage", "ShaderNodeNormalMap", "ShaderNodeOutputMaterial",
    "ShaderNodeSeparateColor", "ShaderNodeSeparateRGB", "ShaderNodeCombineColor", "ShaderNodeMapping",
    "ShaderNodeTexCoord", "ShaderNodeUVMap", "NodeFrame", "NodeReroute", "ShaderNodeMath", "ShaderNodeVectorMath",
    "ShaderNodeAttribute",
}


def args():
    with open(sys.argv[sys.argv.index("--") + 1], encoding="utf-8") as f:
        return json.load(f)


def call(op, **options):
    """Runs an operator with only the options this Blender version knows (option names change between releases)."""
    supported = {p.identifier for p in op.get_rna_type().properties}
    return op(**{k: v for k, v in options.items() if k in supported})


def empty_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def material_report():
    """Materials Blender could not express with plain Principled BSDF nodes (MDL-only materials end up here)."""
    out = []
    for m in bpy.data.materials:
        if m.users == 0:
            continue
        if m.node_tree is None:
            out.append({"material": m.name, "nodes": []})
            continue
        nodes = {n.bl_idname for n in m.node_tree.nodes}
        if "ShaderNodeBsdfPrincipled" not in nodes or nodes - PREVIEW_NODES:
            out.append({"material": m.name, "nodes": sorted(nodes - PREVIEW_NODES)})
    return out


def prune_empties():
    """Deletes empties with no mesh below them (SimReady grasp points, joints, material scopes)."""
    def has_mesh(o):
        return o.type == "MESH" or any(has_mesh(c) for c in o.children)
    doomed = [o for o in bpy.data.objects if o.type == "EMPTY" and not has_mesh(o)]
    for o in doomed:
        bpy.data.objects.remove(o, do_unlink=True)


def missing_images():
    out = []
    for img in bpy.data.images:
        if img.source != "FILE" or img.packed_file is not None:
            continue
        path = bpy.path.abspath(img.filepath)
        if path and not os.path.isfile(path):
            out.append(img.filepath)
    return out


def main():
    a = args()
    source, out_dir, name = a["input"], a["out_dir"], a["name"]
    os.makedirs(out_dir, exist_ok=True)
    empty_scene()
    # Blender converts the stage's metersPerUnit and upAxis into its own metres/Z-up scene; the glTF exporter then
    # writes metres, Y-up. Physics prims are skipped: colliders and mass stay a PHY follow-up (AST-16 §8).
    # Instancing off: instanceable prims become real meshes, otherwise the exporter also writes their hidden prototype.
    call(bpy.ops.wm.usd_import, filepath=source, support_scene_instancing=False, import_materials=True, import_usd_preview=True,
         set_material_blend=True, apply_unit_conversion_scale=True, scale=1.0, import_guide=False,
         import_proxy=False, import_render=True, import_cameras=False, import_lights=False,
         import_skeletons=True, import_shapes=True, import_blendshapes=True, read_mesh_uvs=True,
         read_mesh_colors=True, create_collection=False)
    meshes = [o for o in bpy.data.objects if o.type == "MESH"]
    if not meshes:
        raise RuntimeError("No meshes imported from " + source)
    prune_empties()

    path = os.path.join(out_dir, name + ".gltf")
    call(bpy.ops.export_scene.gltf, filepath=path, export_format="GLTF_SEPARATE", export_texture_dir="textures",
         export_image_format="AUTO", export_yup=True, export_apply=True, use_selection=False, export_cameras=False,
         export_lights=False, export_extras=False)

    outputs = []
    for root, _, files in os.walk(out_dir):
        for f in files:
            rel = os.path.relpath(os.path.join(root, f), out_dir).replace(os.sep, "/")
            if rel != "report.json":
                outputs.append(rel)
    report = {
        "blender": bpy.app.version_string,
        "main": name + ".gltf",
        "meshes": len(meshes),
        "simplifiedMaterials": material_report(),
        "missingImages": missing_images(),
        "outputs": sorted(outputs),
    }
    with open(os.path.join(out_dir, "report.json"), "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)


main()
