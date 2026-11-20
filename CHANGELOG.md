# Changelog

## Unreleased (1.2.0)

- 单体建造: rooms without interior.
  - **用途「空房」** (`empty`, the 74th use, last in the list: one notch down from 堂屋 with the wheel): that floor gets
    no furniture and no lamps; walls, stairs and ladders as ever (a single use has no partitions).
  - **内饰：有 / 无** for the whole building: a row in the room table (under 每层：用途、开敞) and a wheel tool **内饰**
    next to 用途 (works without aiming at a room; the tool column shows 「内饰：无」 while it is off). 无: every room is
    built without furniture and lamps; walls, partitions, doorways, stairs and ladders stay, so every floor is reachable.
    The rest of the building is the same as with 有 (the furniture is drawn and thrown away, so the random draws match).
    Kept for the session like 境界; a loaded design sets it (older saves: 有).
  - Rooms without lamps are dark: mobs can spawn inside.
- 随机一栋 never picks 空房.
- Saves and requests: `"interior": false` only when 内饰 is 无, `"uses": [..., "empty", ...]`; older saves and the
  presets are unchanged and build as before (byte for byte). `kinds.json` lists the new use.
- Python generator (`-PwithPython=true`): the same (`bgen.EMPTY_ROOM`, `storey_interior(bare=)`, compose's
  `"interior"`). Oracle: `oracle.py interior`, a new JUnit set `interior` (24 cases: bare and 空房 floors on rooms of
  several profiles and two saved designs); the other sets unchanged, the start-up fixture only for the new use.

## 1.1.0 — 2026-11-19

- 单体建造: every setting of the room table now has a wheel tool in the world (36 tools, 31 on the wheel). New:
  **形状** (16 shapes), **平座**, **台基形状** (跟随 / 方 / 圆), **台阶** (45° / 27° / 18° / 14°), **边距** (自动, 1–4, 6, 8,
  12, 16, 20) and **每层开敞** (the floor under the crosshair); **用途** now also turns with the wheel (right-click still
  lists them); **层高** scrolls back to 自动 below 4. The tool column has a 台基 group of its own.
- Tools that do nothing for the room under the crosshair are dimmed in the tool column, and the hint box says why
  (平座: round towers of 2+ storeys; 门洞: 城台; 台阶: a podium that is not a 城台, or a 台地; 台基形状 / 边距: a
  podium; 用途 / 每层开敞: not for gates, walls and the other special shapes; 每层开敞: not round towers); scrolling
  them changes nothing.
- 用途 and 每层开敞 pick the floor by the room's frame, the same floor the aim info highlights (before, the list and
  the highlight could disagree by the podium's height).
- Saves, undo/redo and the generator's request are unchanged (same JSON fields).
- Dev pipe: `wheel <TOOL> <room> <d> [ctrl] [floor]`, `preview`.

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
