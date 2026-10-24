#!/usr/bin/env python3
"""Generates the Lottie animations of the Jauge design system, in the brand colors (BRANDING.md).

Writes core/designsystem/src/commonMain/composeResources/files/lottie/{loading,success}.json.
Run from the repository root: python3 tools/lottie/generate_animations.py
"""
import json
import math
import os

MINT = "#3CCB9F"
AMBER = "#FFB23F"
CORAL = "#FF5B3A"
SAGE = "#A9B3AE"
WHITE = "#FFFFFF"

FRAME_RATE = 60
SIZE = 200
OUTPUT = os.path.join("core", "designsystem", "src", "commonMain", "composeResources", "files", "lottie")


def color(hex_color):
    value = hex_color.lstrip("#")
    return [round(int(value[i:i + 2], 16) / 255, 4) for i in (0, 2, 4)] + [1]


def static(value):
    return {"a": 0, "k": value}


def animated(keyframes, ease=True):
    """keyframes: list of (frame, value). Values are lists. Ease in and out unless ease is False (linear)."""
    result = []
    for index, (frame, value) in enumerate(keyframes):
        keyframe = {"t": frame, "s": value if isinstance(value, list) else [value]}
        if index < len(keyframes) - 1:
            dimension = len(keyframe["s"])
            if ease:
                keyframe["o"] = {"x": [0.42] * dimension, "y": [0] * dimension}
                keyframe["i"] = {"x": [0.58] * dimension, "y": [1] * dimension}
            else:
                keyframe["o"] = {"x": [0] * dimension, "y": [0] * dimension}
                keyframe["i"] = {"x": [1] * dimension, "y": [1] * dimension}
        result.append(keyframe)
    return {"a": 1, "k": result}


def group_transform():
    return {
        "ty": "tr", "p": static([0, 0]), "a": static([0, 0]), "s": static([100, 100]),
        "r": static(0), "o": static(100), "sk": static(0), "sa": static(0),
    }


def layer_transform(position, rotation=None, opacity=None, scale=None):
    return {
        "o": opacity if opacity is not None else static(100),
        "r": rotation if rotation is not None else static(0),
        "p": static(position + [0]),
        "a": static([0, 0, 0]),
        "s": scale if scale is not None else static([100, 100, 100]),
    }


def stroke(hex_color, width, opacity=100):
    return {"ty": "st", "c": static(color(hex_color)), "o": static(opacity), "w": static(width), "lc": 2, "lj": 2, "ml": 4}


def fill(hex_color):
    return {"ty": "fl", "c": static(color(hex_color)), "o": static(100), "r": 1}


def path(vertices, in_tangents, out_tangents, closed=False):
    return {"ty": "sh", "ks": static({"i": in_tangents, "o": out_tangents, "v": vertices, "c": closed})}


def arc(radius, start_degrees, end_degrees):
    """Arc around (0, 0), angles counter clockwise from the right, y going down on screen (gauge drawn above)."""
    steps = max(1, math.ceil(abs(end_degrees - start_degrees) / 45))
    delta = math.radians(end_degrees - start_degrees) / steps
    handle = 4 / 3 * math.tan(delta / 4) * radius
    vertices, in_tangents, out_tangents = [], [], []
    for step in range(steps + 1):
        angle = math.radians(start_degrees) + delta * step
        x, y = radius * math.cos(angle), -radius * math.sin(angle)
        tangent_x, tangent_y = -math.sin(angle), -math.cos(angle)
        vertices.append([round(x, 3), round(y, 3)])
        in_tangents.append([round(-tangent_x * handle, 3), round(-tangent_y * handle, 3)] if step > 0 else [0, 0])
        out_tangents.append([round(tangent_x * handle, 3), round(tangent_y * handle, 3)] if step < steps else [0, 0])
    return path(vertices, in_tangents, out_tangents)


def ellipse(diameter):
    return {"ty": "el", "p": static([0, 0]), "s": static([diameter, diameter]), "d": 1}


def shape_layer(index, name, shapes, transform, end_frame):
    return {
        "ddd": 0, "ind": index, "ty": 4, "nm": name, "sr": 1, "ks": transform, "ao": 0,
        "shapes": [{"ty": "gr", "nm": name, "it": shapes + [group_transform()]}],
        "ip": 0, "op": end_frame, "st": 0, "bm": 0,
    }


def ease_in_out(progress):
    """cubic-bezier(0.42, 0, 0.58, 1), solved numerically."""
    low, high = 0.0, 1.0
    for _ in range(40):
        middle = (low + high) / 2
        x = 3 * (1 - middle) ** 2 * middle * 0.42 + 3 * (1 - middle) * middle ** 2 * 0.58 + middle ** 3
        if x < progress:
            low = middle
        else:
            high = middle
    t = (low + high) / 2
    return 3 * (1 - t) * t ** 2 + t ** 3


def loading():
    """Reading animation (screen 03, paywall offer): a gauge with its three levels and a needle that scans them."""
    end_frame = 120
    center = [100, 134]
    radius = 70
    swing = 72
    segments = [(MINT, 180, 126), (AMBER, 117, 63), (CORAL, 54, 0)]

    def needle_rotation(frame):
        half = end_frame / 2
        if frame <= half:
            return -swing + 2 * swing * ease_in_out(frame / half)
        return swing - 2 * swing * ease_in_out((frame - half) / half)

    layers = []
    hub = shape_layer(1, "hub", [ellipse(18), fill(CORAL)], layer_transform(center), end_frame)
    needle_shape = path([[0, 4], [0, -54]], [[0, 0], [0, 0]], [[0, 0], [0, 0]])
    needle = shape_layer(
        2, "needle", [needle_shape, stroke(CORAL, 7)],
        layer_transform(
            center,
            rotation=animated([(0, -swing), (end_frame / 2, swing), (end_frame, -swing)]),
        ),
        end_frame,
    )
    layers += [hub, needle]
    for index, (segment_color, start, end) in enumerate(segments):
        middle_rotation = 90 - (start + end) / 2
        keyframes = []
        for frame in range(0, end_frame + 1, 6):
            distance = abs(needle_rotation(frame) - middle_rotation)
            keyframes.append((frame, round(100 if distance < 20 else max(35, 100 - (distance - 20) * 2.2), 1)))
        layers.append(
            shape_layer(
                3 + index, f"level {index + 1}", [arc(radius, start, end), stroke(segment_color, 16)],
                layer_transform(center, opacity=animated(keyframes, ease=False)), end_frame,
            )
        )
    track = shape_layer(6, "track", [arc(radius, 180, 0), stroke(SAGE, 16, opacity=18)], layer_transform(center), end_frame)
    layers.append(track)
    return document("Jauge loading", end_frame, layers)


def success():
    """Success animation (purchase done, analysis saved): a mint disc pops, its check is drawn, a ring fades out."""
    end_frame = 72
    center = [100, 100]
    disc = shape_layer(
        2, "disc", [ellipse(112), fill(MINT)],
        layer_transform(center, scale=animated([(0, [0, 0, 100]), (16, [112, 112, 100]), (26, [100, 100, 100])])),
        end_frame,
    )
    check_path = path([[-26, 2], [-8, 20], [28, -18]], [[0, 0], [0, 0], [0, 0]], [[0, 0], [0, 0], [0, 0]])
    trim = {"ty": "tm", "s": static(0), "e": animated([(14, [0]), (36, [100])]), "o": static(0), "m": 1}
    check = shape_layer(1, "check", [check_path, trim, stroke(WHITE, 12)], layer_transform(center), end_frame)
    ring = shape_layer(
        3, "ring", [ellipse(112), stroke(MINT, 6)],
        layer_transform(
            center,
            scale=animated([(10, [100, 100, 100]), (48, [158, 158, 100])]),
            opacity=animated([(10, [0]), (16, [70]), (48, [0])]),
        ),
        end_frame,
    )
    return document("Jauge success", end_frame, [check, disc, ring])


def document(name, end_frame, layers):
    return {"v": "5.7.4", "fr": FRAME_RATE, "ip": 0, "op": end_frame, "w": SIZE, "h": SIZE, "nm": name, "ddd": 0,
            "assets": [], "layers": layers}


if __name__ == "__main__":
    os.makedirs(OUTPUT, exist_ok=True)
    for file_name, animation in (("loading.json", loading()), ("success.json", success())):
        with open(os.path.join(OUTPUT, file_name), "w") as output:
            json.dump(animation, output, separators=(",", ":"))
        print("wrote", os.path.join(OUTPUT, file_name))
