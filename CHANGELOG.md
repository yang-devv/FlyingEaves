# Changelog

## 1.0.0 — pure Java (first public release)

- The generator is ported to Java (`com.flyingeaves.gen`) and runs inside the game on one worker thread: the O menu's
  87 kinds, 单体建造 and its preview, presets, 随机一栋 / 随机一院, 随机城镇, 随机宗门, 一句话, 随机宫城, 紫禁城 1:1.
  Output is byte-identical to the Python generator (oracle: `PORTING.md`).
- **No Python needed.** The default jar holds only the generator's data (materials, parts, presets, library); no
  `.py` files. The mod never starts, looks for, or writes requests for a Python process. The config has only
  `generatorDir`.
- A request too big for the free heap fails with a message (how much it needs, and to raise `-Xmx`) instead of
  crashing the game.
- Results come back in memory (no `.dvcr` files, no polling); a newer preview stops an older one mid-build.
- Start-up files (`kinds.json`, `presets/`, `library/`, `sites/`) are written by the Java side.
- The Python-backed build is still available: `gradlew build -PwithPython=true` packs the Python generator and adds
  config `backend` (`java` | `python`), `pythonPath`, `autoStart`, and the fallback from Java to Python.
- JUnit oracle tests (`gradlew test`, `slowTest`) check the Java generator against answers captured from the Python
  one; they need neither Python nor Minecraft. Dev pipe: `room`, `preset`, `town`, `sect`, `mem`, `hash`, `save`.
- Memory: the undo record keeps 6 bytes a cell (packed position + palette index, per chunk) instead of a
  `BlockPos`->`BlockState` map (紫禁城 1:1: 84 MB instead of ~1.2 GB); placing streams chunk by chunk (a chunk is lit
  once its neighbours are written and let go once they are lit: ~200-400 chunks held instead of all 3400); undo
  writes the chunks the same way instead of one `setBlock` a cell (紫禁城: 10-15 s instead of 1.5-3 min); the preview
  reads the runs directly instead of a map of every cell. The 紫禁城 1:1 runs at `-Xmx3G` (4G recommended).
- License: MIT.

## 0.1.0 — Python-backed (not published)

- First standalone version: keys H / K / O, the Python generator (`generator/`, Python 3.10+) started and stopped by
  the mod, file protocol in `<game dir>/flyingeaves/`.
