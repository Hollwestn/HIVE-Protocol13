"""Regression checks for actual JavaFX captures, not a reference-similarity acceptance score."""
import argparse
import json
from pathlib import Path
from PIL import Image

parser = argparse.ArgumentParser()
parser.add_argument("captures", type=Path, nargs="+")
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
results = []
for directory in args.captures:
    fixture = json.loads((directory / "visual-fixture.json").read_text(encoding="utf-8-sig"))
    assert fixture.get("visualFixture") is True and fixture.get("realTcpClients") == 3 and fixture.get("fullMinuteTest") is False
    images = {}
    for name in ("GOLDEN_MAIN_A.png", "PANEL_INVENTORY.png", "PANEL_REGION.png", "PANEL_SYSTEMS.png", "PANEL_EVIDENCE.png"):
        with Image.open(directory / name) as source:
            image = source.convert("RGB")
            assert image.size in ((1536, 864), (1280, 720), (1600, 900), (1920, 1080), (2560, 1440), (3840, 2160)), image.size
            scale = image.width / 1536
            region = image.crop(tuple(round(v * scale) for v in (1309, 139, 1418, 405)))
            pixels = list(region.get_flattened_data())
            visible = sum(max(pixel) > 45 for pixel in pixels)
            red = sum(r > 35 and r > g * 1.3 and r > b * 1.3 for r, g, b in pixels)
            if fixture.get("anatomyExpectedClean"):
                assert red < 8, f"Infection baked into clean anatomy: {directory / name} / red={red}"
            assert visible > 1200 * scale * scale, f"Anatomical mesh disappeared: {directory / name}"
            images[name] = {"size": image.size, "visibleAnatomyPixels": visible, "redAnatomyPixels": red}
    baseline = images["GOLDEN_MAIN_A.png"]["visibleAnatomyPixels"]
    for name, item in images.items():
        assert .65 < item["visibleAnatomyPixels"] / baseline < 1.5, f"Unstable anatomy: {name}"
    results.append({"directory": str(directory), "fixture": fixture, "images": images})
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps({"passed": True, "referenceSimilarityAccepted": False, "captures": results}, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"PASS: {sum(len(r['images']) for r in results)} captures; anatomy persists; no reference-similarity claim")
