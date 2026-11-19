# Porting the generator to Java

The Python generator (`generator/`) is ported to pure Java 17 in `com.flyingeaves.gen` (no Minecraft classes; block
states are strings). The Python version is the oracle: for the same request the Java port must write the same `.dvcr`
— byte for byte, but for the gzip time stamp — and the same result JSON.

## Harness

```
python -I tools/oracle/oracle.py rooms N --profile P [--seed S] [--tier T] [--set k=v,...] [--out DIR] [--quiet]
python -I tools/oracle/oracle.py library [--out DIR]          # every design in generator/data/library
python -I tools/oracle/oracle.py interior [N] [--seed S]      # 内饰：无 / 空房 floors on rooms of several profiles + 2 designs
python -I tools/oracle/oracle.py kinds [--seed S] [--only a,b] [--tiers all|ends]   # the build menu: every kind x tier
python -I tools/oracle/oracle.py run REQ.json [...]           # given daemon requests (a file: one request or a list)
python -I tools/oracle/oracle.py plans GROUP [N] [--only S]   # the planners: sites | towns | sects | sentences | daemon | all
python -I tools/oracle/oracle.py compare DIR_A DIR_B
python -I tools/oracle/gen_tables.py                          # regenerate Tables.java from the Python modules
```

- `oracle_dump.py` runs the Python daemon's `build()` per request with an empty run folder (no registry: only
  `minecraft:` blocks present); `--trace` writes which generator functions ran (to scope a port).
- The Java side is compiled with plain `javac` into `build/gen-classes` and run as `com.flyingeaves.gen.tools.Dump`.
- Per request: `same` (bytes equal but mtime), `payload` (same decompressed data, other gzip bytes), or `DIFF` with the
  first differing cell, a 5-high column and the 3x3 around it from both sides; `MISSING` when one side failed (Java
  throws `Platforms.NotPorted` for what is not ported yet).
- Profiles (random 随机一栋-style rooms, as `Compose.randomizeAll`): `plain` (1 storey, no podium, 硬山), `full` (every
  setting random), `roofs`, `storeys`, `sh`, `podium`, `podium_style`, `platforms`, `eaves`, `double_eave`, `open`,
  `gallery`, `round`, `round_full`, `multi` / `multi_full` (2-4 rooms touching or stacked).
- Requests in one batch run in one process on both sides, in order (module state carries over as in the daemon).
- `plans`: planner requests ({"type": "plan", "fn": site | city | city_real | free | site_free | crowded | town | sect |
  sentence | daemon, ...}; `oracle_dump.plan` / `Planners.plan`) write `<id>.design.json` on both sides; the JSON
  *text* is compared (key order, int vs float, float repr), the first differing path shown. `fn: daemon` runs
  bgen_daemon.main's "site" / "sentence" branch with its random source seeded (`rs`) - town / sect with sampled heights,
  sizes, boxes. An error counts as the same when both sides give the same text.

## Status

| Python | Java | status |
|---|---|---|
| CPython emulation | `py/` PyRandom (+ `ofString` seeding), PySet, PyHash, Py, UcrtMath, IntPair... | ours (see `THIRD_PARTY_NOTICES.md` for the algorithms and tables they reproduce) |
| arch: B / parse / rotate / mirror, stair / slab | `Blocks` | identical (all samples) |
| arch.Canvas, Frame, export (.dvcr), fix_supports | `Canvas`, `Frame`, `Fr` | identical, bytes too |
| materials, parts (pieces, bays, lights, ornaments) | `Materials`, `Parts`, `Env` | identical |
| arch roofs (zhewu, roof_surface, raster_roof), ridges, ornaments, lamps, xumizuo, balustrade, stairs_down, flights | `Arch` | identical |
| bgen roof_for (all 9 forms), stair_plan, bays, bsp, storey_interior, light_room, pick_light | `Bgen` | identical |
| bgen.house, podium (平台 / 须弥座 / 三层), widen_podium, draw_walls, pick_bay, eaves, horsehead, veranda, decorate (door lamps, plaque) | `House` | identical |
| 城台 (chengtai, tunnels, chengtai_well, stair_well, _walks_in), round 坛, square_platform | `Platforms` | identical |
| bgen.round_room, compose tower_cells / _tower_doors | `RoundRoom` | identical |
| furniture.furnish, furniture_big | `Furniture` | identical (code piece: only stele; the rest are templates) |
| joinparts | `Joinparts` | identical |
| walkout, _openings, clear_doorways, settle_loose, connect_panes | `Finish` | identical |
| compose.build_compose (rooms, odd widths, floors, doorways, stacks, ladders) | `Compose` | identical for houses and round rooms |
| bgen_daemon.build ground work (plinth, footing, clear_box, ground_grid) | `Generator` | identical |
| bgen.special_shape, thick_wall, town_ornament; arch.paifang | `SpecialShapes` | identical |
| compose: compound_ground, _wall_walks, wall odd widths | `Compose` | identical (compound_ground: see below) |
| compose: terrain_ground, build_bridges, plant_groves, 地块 (patches); selfcheck floating (only=) | `Terrain` | identical (6 towns, 4 sects) |
| compose: walls_override / roof_override (material board) | `Compose.override` | identical |
| bgen kinds (the build menu): bgen.build, ARCHETYPES (house, tower, stilt, cave, round, pavilion, compound, hall, gate, paifang, star, floating, street) | `Kinds` | identical (every kind x every tier it lists, 3 seeds) |
| arch.hall, pavilion, gate_tower, wall_ring, beams_and_brackets, columns, walls, pine, round_tree | `ArchBuildings` | identical |
| decor.py (ALL, BY_TIER) + bgen d_* / DECOR / DECOR_ALIAS; furniture p_well / p_small_furnace / p_plant / p_herb_rack / p_crates / p_chest as code | `Decor` | identical |
| sites.py (cloud_port, spirit_mine, beast_hill, trial_ground, forbidden, tomb; crag, _noise) | `Sites` | identical |
| xianxia.py: star_altar, pier, cloud_ship, formation_pillar, stele, spirit_field | `Xianxia` | identical |
| bgen.drop_floating (selfcheck's "floating" list, pieces popped from a PySet) | `Kinds.dropFloating` | identical |
| arch.tower / pagoda / corridor_ring / courtyard_unit / courtyard_variant / fill_zone(2); xianxia cliff_cave / plank_walk / dan_furnace / forge_hall / giant_sword; reach | — | not ported: no kind reaches them (bgen's tower_kind draws its own storeys; the sect planner's old paths) |
| siteplan: site (住宅 / 宫院), city (宫城), city_real (紫禁城 1:1, moat too), site_free + randomize (随机一院 全参数随机), crowded, _steps / _podium_out / _reach / _joined / _abut / _madao / _fit_frame / _part_ears / _close_walls / _clear_steps | `Siteplan` | identical |
| town (随机城镇: village / town / big, line / T / cross / Y / bank / grid, boxes, given heights), compose.group_ground | `Town` | identical |
| sect_plan (随机宗门 练气-化神, satellites, given heights; footprint() builds each building with compose, memo + the daemon's cache file) | `SectPlan` | identical |
| sentence (一句话) + pipeline.build / measure (the ridge, for 高 N 格) | `Sentence` | identical |
| bgen_daemon main's "site" / "sentence" branch, city_real_site | `Planners.daemon` (JavaBackend: types site / sentence) | identical |
| pipeline.py fit / score (a dev tool: targets -> designs, out/pipeline) | — | not ported (no game path reaches it) |

Samples identical (2026-11-19): plain 60/60, full 200/200 (+40), roofs 30/30, storeys 30/30, sh 30/30, podium 30/30,
podium_style 40/40, platforms 60/60, eaves 30/30, double_eave 30/30, open 30/30, gallery 30/30, round_full 40/40,
multi 40/40, multi_full 40/40, specials 80/80, board 60/60, mixed 54/60 (the 6: the Python nondeterminism below),
library 127/127 (every saved design), sites 43/43 (随机一院 house/palace 1-3 courts x 6 seeds, free plans x 6, the 宫城),
towns 10/10 (随机城镇 seeds 1-6, 随机宗门 筑基 / 金丹 / 元婴 / 化神). Every 单体建造 request the game can send (compose) is
ported; so is every kind of the build menu (bgen.build): `kinds` 483/483 (161 kind x tier requests - every kind at
every tier it lists, sites.py's six included - x seeds 1, 2, 3). The planners (designs back, `plans`, design JSON
text equal): sites 229/229 (随机一院 house / palace 1-3 courts x 20 seeds, 全参数随机 x 20, the 宫城 x 3, the
紫禁城 1:1 x 3 (one with the moat), crowded on all of them), towns 135/135 (seeds 1-15: by the seed, each size, each size
x a street plan, a box, given heights), sects 40/40 (seeds 1-8 x 筑基 / 金丹 / 元婴 / 化神, + given heights), sentences
50/50, the daemon's site / sentence branch 36/36 (rs 1-6: free, city, city_real, town with size / box / heights, sect
with heights, a sentence); and composed (the Java designs through both composes) 60/60. siteplan.write_all (the
start-up's sites/) is Java's (`Siteplan.writeAll`, `RunFilesTest`): with backend = java Python is not needed at all.

## How the pieces fit

- **Kinds** (`Kinds`, `ArchBuildings`, `Decor`, `Sites`, `Xianxia`): `Generator.build` calls `Kinds.build` for a
  request with a "kind" (the O menu). Oracle: `oracle.py kinds`; JUnit `OracleKindsTest` (125 cases).
- **Planners** (`Siteplan`, `Town`, `SectPlan`, `Sentence`, `Planners`): a "plan" request writes `<id>.design.json`
  (`tools/Dump.java`; oracle.py / oracle_dump.py `plans`). `JavaBackend` handles the types `site` and `sentence`
  (`Planners.daemon` with a SecureRandom-seeded PyRandom, as the daemon's `secrets.randbits(62)`). compose.USES /
  ROOFS ids are a list in `Siteplan`: keep them in step with compose.py (the oracle catches drift).
  `RunFiles.write(run)` writes the start-up files (kinds.json, presets/, library/, sites/ via `Siteplan.writeAll`).
  JUnit: `OraclePlansTest` (86 cases, the design text's sha256).
- **Game adapter** (`backend/JavaBackend`, `tool/GenRequest`): runs `Generator.build` on one worker thread (the
  generator's state is static: calls are serialised), hands the canvas to Preview / Stamp as runs (no `.dvcr` file),
  reads the data from the jar. In a `-PwithPython=true` build, config `backend = java|python` chooses the generator
  and Java can fall back to the Python daemon.
- **JUnit** (`gradlew test`): 193 oracle cases + 125 kinds + 24 with the data read from a jar + `RunFilesTest`
  (kinds.json, presets, library, sites: exact); `gradlew slowTest`: the 1:1 紫禁城. Fixtures (checked-in expected
  hashes from the frozen Python) come from `tools/oracle/capture.py`; add a kind's cases to `default_requests` there.
  A Java change is checked without Python.

With Python at hand, run `python -I tools/oracle/oracle.py suite 20` after a generator change (a few minutes);
`library`, `sites 6`, `towns 6 4` for the big ones.

## The two builds

`gradlew build` makes the pure-Java jar: `generator/data/**` only (no `*.py`, no `data/targets`), and
`com/flyingeaves/build.properties` says `python=false` (`com.flyingeaves.Build.PYTHON`). Then the config has only
`generatorDir`, `JavaBackend.enabled()` is always true, `GeneratorProcess` is a no-op (never looks for Python), and
what would have gone to Python (NotPorted, too big for the free heap, out of memory) fails with a message instead.
`gradlew build -PwithPython=true` builds the Python-backed variant: the Python generator in the jar (unpacked into
`<game dir>/flyingeaves/generator` and started by the mod), config `backend` / `pythonPath` / `autoStart`, and the
fallback from Java to Python. The oracle tooling (`tools/oracle`) runs `generator/` from the repo and does not care
either way.

## Known differences / risks

- compose.compound_ground used to pick the courtyard paving from a set (Python-hash-order dependent); it is now
  `paved = max(walls, key=walls.count)`, and Java's first-in-list-order pick is the same rule.
- The kinds use `math.sin` / `math.cos` (floating 浮空阁's rock, waterwheel, star_altar's rings, the sites' circles):
  StrictMath (fdlibm) against the MSVC CRT, as atan2 below; float seeds (`random.Random(rng.random())`) hash as CPython
  (`Kinds.floatHash`); `math.hypot` of floats (sites.crag) correctly rounded (`Sites.hypot`).
- `math.atan2` (round rooms' post / window angles) uses StrictMath (fdlibm) against the MSVC CRT; a last-ulp difference
  could flip a cell at a tolerance boundary. Not seen in 80 round towers.
- `math.hypot` of ints is `sqrt(u*u + v*v)` (UcrtMath.hypotInt).
- The planners' ground (town._synth, sect_plan._synth, street curves, rivers) uses `math.sin` / `math.cos`: StrictMath,
  which differs from the MSVC CRT in the last ulp for ~2% of arguments (measured on 300k). The values only feed
  `round()` and comparisons with slack, so a flip needs a value within an ulp of a boundary: not seen in any plan.
  `math.exp` and `x ** y` there go through UcrtMath (bit-exact).
- sect_plan.footprint is cached by a building's settings without its seed (the first one built decides): Java keeps
  the same memo for the JVM's life and reads / writes the daemon's `run/cache/sect_fp_cache.json` (same keys), so the
  two backends agree in game; in the oracle both start empty and see requests in the same order.
- Global state (current roof theme, palette, ornament choices, WINDOWS) is static, as in Python: one generation at a
  time per JVM. The in-game backend must serialise calls (or this becomes a context object later).
- Sets of strings are not order-sensitive (the generator is PYTHONHASHSEED-independent); sets of int tuples that are
  iterated use PySet, dicts LinkedHashMap.
