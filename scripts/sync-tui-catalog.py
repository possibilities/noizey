#!/usr/bin/env python3
"""Extract the desktop catalog from Android; --check detects parity drift."""
import json
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parent.parent
source = root / "app/src/main/java/com/noizey/app/model"
sounds = [dict(id=i, name=n, description=d, category=c.title(), volume=float(v))
          for i, n, d, c, v in re.findall(
              r'SoundDefinition\("([^"]+)", "([^"]+)", "([^"]+)", SoundCategory\.(\w+), GeneratorKind\.\w+, ([\d.]+)f\)',
              (source / "SoundCatalog.kt").read_text())]
presets = [dict(id=i, name=n, note=d, layers={k: dict(volume=float(v), enabled=True)
           for k, v in re.findall(r'"([^"]+)" to ([\d.]+)f', layers)})
           for i, n, d, layers in re.findall(
               r'Preset\("([^"]+)", "([^"]+)", "([^"]+)", layers\(([^\n]+)\)\)',
               (source / "MixModels.kt").read_text())]
assert len(sounds) == 19 and len(presets) == 8, "Android catalog changed; inspect extraction"
result = json.dumps(dict(sounds=sounds, presets=presets), ensure_ascii=False, indent=2) + "\n"
target = root / "internal/model/catalog.json"
if "--check" in sys.argv:
    if target.read_text() != result:
        sys.exit("TUI catalog differs from Android. Run scripts/sync-tui-catalog.py")
else:
    target.write_text(result)
