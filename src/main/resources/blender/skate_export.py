# Skate engine: .blend -> GLB export (AST-19). Run by the editor, never by hand:
#   blender -b --factory-startup --python-exit-code 1 <file.blend> --python skate_export.py -- <args.json>
# The arguments are a JSON file (not a command-line string: Windows mangles quotes in arguments): out_dir, name, action_mode ("FILES" | "EMBEDDED"), apply_modifiers, only_visible,
# export_actions. Writes <name>.glb, <name>@<action>.glb per action in FILES mode, and report.json.
import bpy
import json
import os
import sys

STANDARD_NODES = {
    "ShaderNodeBsdfPrincipled", "ShaderNodeTexImage", "ShaderNodeNormalMap", "ShaderNodeOutputMaterial",
    "ShaderNodeSeparateColor", "ShaderNodeSeparateRGB", "ShaderNodeCombineColor", "ShaderNodeMapping",
    "ShaderNodeTexCoord", "ShaderNodeUVMap", "NodeFrame", "NodeReroute", "ShaderNodeGroup",
}


def args():
    with open(sys.argv[sys.argv.index("--") + 1], encoding="utf-8") as f:
        return json.load(f)


def safe(name):
    cleaned = "".join(c if c.isalnum() or c in "-_" else "_" for c in name).strip("_")
    return cleaned or "action"


def gltf(path, **options):
    supported = {p.identifier for p in bpy.ops.export_scene.gltf.get_rna_type().properties}
    bpy.ops.export_scene.gltf(filepath=path, **{k: v for k, v in options.items() if k in supported})


def material_report():
    simplified = []
    for m in bpy.data.materials:
        if m.users == 0 or not m.use_nodes or m.node_tree is None:
            continue
        other = sorted({n.bl_idname for n in m.node_tree.nodes} - STANDARD_NODES)
        if other:
            simplified.append({"material": m.name, "nodes": other})
    return simplified


def missing_images():
    out = []
    for img in bpy.data.images:
        if img.source != "FILE" or img.packed_file is not None:
            continue
        path = bpy.path.abspath(img.filepath)
        if path and not os.path.isfile(path):
            out.append({"image": img.name, "path": img.filepath})
    return out


def main():
    a = args()
    out_dir, name = a["out_dir"], a["name"]
    os.makedirs(out_dir, exist_ok=True)
    scene = bpy.context.scene
    report = {
        "blender": bpy.app.version_string,
        "unitScale": scene.unit_settings.scale_length,
        "objects": [{"name": o.name, "type": o.type} for o in bpy.data.objects],
        "simplifiedMaterials": material_report(),
        "missingImages": missing_images(),
        "libraries": [lib.filepath for lib in bpy.data.libraries],
        "actions": [],
        "skippedActions": [],
        "outputs": [],
    }
    common = dict(export_format="GLB", export_yup=True, export_apply=bool(a.get("apply_modifiers", True)),
                  use_visible=bool(a.get("only_visible", False)), export_image_format="AUTO")
    armatures = [o for o in bpy.data.objects if o.type == "ARMATURE"]
    actions = [act for act in bpy.data.actions if a.get("export_actions", True)]
    files_mode = a.get("action_mode", "FILES") == "FILES"

    model = name + ".glb"
    gltf(os.path.join(out_dir, model), export_animations=not files_mode and bool(actions),
         export_animation_mode="ACTIONS", **common)
    report["outputs"].append(model)

    if files_mode and armatures:
        for act in actions:
            if getattr(act, "is_empty", False):
                report["skippedActions"].append({"action": act.name, "reason": "no keyframes"})
                continue
            for arm in armatures:
                arm.animation_data_create()
                arm.animation_data.action = act
                if hasattr(arm.animation_data, "action_slot") and getattr(act, "slots", None):
                    arm.animation_data.action_slot = act.slots[0]
            clip = "%s@%s.glb" % (name, safe(act.name))
            gltf(os.path.join(out_dir, clip), export_animations=True, export_animation_mode="ACTIVE_ACTIONS", **common)
            report["actions"].append({"action": act.name, "file": clip})
            report["outputs"].append(clip)
    elif files_mode and actions:
        report["skippedActions"] += [{"action": act.name, "reason": "no armature"} for act in actions]

    with open(os.path.join(out_dir, "report.json"), "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)


main()
