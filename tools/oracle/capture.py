"""Capture the JUnit fixtures for the Java generator from the frozen Python one (the oracle as checked-in hashes).

  python -I tools/oracle/capture.py [default|kinds|slow|all]

Writes src/test/resources/oracle/<set>.json.gz: a list of {"req": the request, "sha256": of the decompressed .dvcr
payload, "result": the daemon's result dict without "seconds"}, in the order they were run. The requests of a set run
in one Python process, in that order (module state carries over between requests, as in the daemon), so the Java test
runs them in the same order in a fresh JVM.

Sets:
  default  single rooms of every profile, multi-room buildings, the library (every saved design), 随机一院 plans
           (house, palace, free), the 宫城, a 随机城镇 and a 随机宗门 (筑基)
  kinds    the build menu (O / 建造菜单): every kind of bgen.K at its first and last tier, seed 1
  slow     the 紫禁城 1:1 (siteplan.city_real, as bgen_daemon.city_real_site: every room on one level)
  plans    the planners (designs back, no blocks): oracle.plan_requests "all" for seed 1 - 随机一院, the 宫城, the
           紫禁城 1:1 plan, towns, sects, sentences, the daemon's site / sentence branch; per case the sha256 of the
           design JSON text (oracle_dump.plan's <id>.design.json)
  startup  what the daemon writes into its run folder when it starts (bgen_daemon.write_kinds: kinds.json, presets/,
           library/, sites/), for an empty run folder and for one with the game's block list, a material file and a
           bay part of the player's: src/test/resources/oracle/startup.json.gz {scenario: {"inputs": {path: text},
           "files": {path: text}}} (sites/: siteplan.write_all, Java's Siteplan.writeAll).
"""
import gzip
import hashlib
import json
import os
import random
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
FIX = ROOT / "src" / "test" / "resources" / "oracle"
sys.path.insert(0, str(HERE))
import oracle  # noqa: E402

PROFILES = ["plain", "full", "roofs", "storeys", "sh", "podium", "podium_style", "platforms", "eaves", "double_eave", "open",
            "gallery", "round", "round_full", "specials", "board"]


def default_requests():
    reqs = []
    for p in PROFILES:
        reqs += oracle.random_rooms(3, seed=7, profile=p)
    for p in ("multi", "multi_full", "mixed"):
        reqs += oracle.random_rooms(3, seed=7, profile=p)
    reqs += oracle.library_requests()
    os.environ["FLYINGEAVES_RUN"] = tempfile.mkdtemp(prefix="fe_capture_")
    sys.path.insert(0, str(ROOT / "generator"))
    import siteplan
    import town
    import sect_plan
    for kind, jin, s in (("house", 1, 1), ("house", 3, 2), ("palace", 2, 1), ("palace", 3, 3)):
        reqs.append(oracle.design_request(siteplan.site(kind, jin, s), f"site_{kind}_{jin}_{s}"))
    for s in (1, 2):
        reqs.append(oracle.design_request(siteplan.randomize(siteplan.site_free(s), random.Random(s)), f"site_free_{s}"))
    reqs.append(oracle.design_request(siteplan.city(1), "site_city_1"))
    reqs.append(oracle.design_request(town.town(1), "town_1"))
    reqs.append(oracle.design_request(sect_plan.sect(1, tier="zhuji"), "sect_zhuji_1"))
    return [r for r in reqs if r]


def slow_requests():
    os.environ["FLYINGEAVES_RUN"] = tempfile.mkdtemp(prefix="fe_capture_")
    sys.path.insert(0, str(ROOT / "generator"))
    import siteplan
    d = siteplan.city_real(1)
    r = oracle.design_request(d, "city_real_1")
    for room in r["rooms"]:                     # (level: every room on the crosshair's ground, as the game lays it)
        room["y"] = 64
    return [r]


def capture(name, reqs):
    work = Path(tempfile.mkdtemp(prefix=f"fe_capture_{name}_"))
    rf = work / "requests.json"
    rf.write_text(json.dumps(reqs, ensure_ascii=False), encoding="utf-8")
    t = oracle.run_python(work / "out", [rf])
    cases = []
    for r in reqs:
        res = json.loads((work / "out" / f"{r['id']}.json").read_text(encoding="utf-8"))
        res.pop("seconds", None)
        dv = work / "out" / f"{r['id']}.dvcr"
        sha = hashlib.sha256(gzip.decompress(dv.read_bytes())).hexdigest() if dv.exists() else None
        cases.append(dict(req=r, sha256=sha, result=res))
    FIX.mkdir(parents=True, exist_ok=True)
    out = FIX / f"{name}.json.gz"
    with gzip.open(out, "wt", encoding="utf-8", compresslevel=9) as f:
        json.dump(cases, f, ensure_ascii=False, separators=(",", ":"))
    bad = [c["req"]["id"] for c in cases if not c["result"].get("ok")]
    print(f"{name}: {len(cases)} cases, python {t:.1f} s, {out.stat().st_size // 1024} KB" + (f", FAILED in python: {bad}" if bad else ""))


def capture_plans(name, reqs):
    work = Path(tempfile.mkdtemp(prefix=f"fe_capture_{name}_"))
    rf = work / "requests.json"
    rf.write_text(json.dumps(reqs, ensure_ascii=False), encoding="utf-8")
    t = oracle.run_python(work / "out", [rf])
    cases = []
    for r in reqs:
        text = (work / "out" / f"{r['id']}.design.json").read_text(encoding="utf-8")
        cases.append(dict(req=r, sha256=hashlib.sha256(text.encode("utf-8")).hexdigest(), ok=json.loads(text).get("ok")))
    FIX.mkdir(parents=True, exist_ok=True)
    out = FIX / f"{name}.json.gz"
    with gzip.open(out, "wt", encoding="utf-8", compresslevel=9) as f:
        json.dump(cases, f, ensure_ascii=False, separators=(",", ":"))
    print(f"{name}: {len(cases)} cases, python {t:.1f} s, {out.stat().st_size // 1024} KB")


STARTUP_SCENARIOS = {
    "empty": {},
    "player": {
        "registry_blocks.txt": None,                          # (filled in below: the game's list plus a modded block)
        "materials/custom.json": json.dumps({"walls": {"mywall": {"inherit": "palace", "cn": "我的墙", "swatch": "#123456",
                                                                  "fill": ["examplemod:jade_brick", "minecraft:calcite"]}},
                                             "roofs": {"myroof": {"inherit": "gold", "cn": "我的瓦", "swatch": "#654321"}}},
                                            ensure_ascii=False),
        "materials/bases_mine.json": json.dumps({"bases": {"jadebase": {"cn": "玉基", "blocks": ["examplemod:jade_brick"]},
                                                           "nobase": {"cn": "没有", "blocks": ["othermod:missing"]}}},
                                                ensure_ascii=False),
        "parts/bays_mine.json": json.dumps({"bays": {"mybay": {"cn": "我的开间", "role": "plain", "style": "我的样式", "rows": ["F|.", "F|."]},
                                                     "modbay": {"cn": "缺方块", "role": "plain", "style": "缺", "rows": ["X|."],
                                                                "key": {"X": "othermod:missing"}}}}, ensure_ascii=False),
    },
}


def startup_child(run):
    """(in its own process) the daemon's start-up writes into run."""
    os.environ["FLYINGEAVES_RUN"] = str(run)
    sys.path.insert(0, str(ROOT / "generator"))
    import contextlib
    import io
    import bgen_daemon
    with contextlib.redirect_stdout(io.StringIO()):
        bgen_daemon.write_kinds()


def capture_startup():
    reg = ROOT / "tools" / "oracle" / "registry_blocks.txt"
    out = {}
    for name, inputs in STARTUP_SCENARIOS.items():
        run = Path(tempfile.mkdtemp(prefix=f"fe_startup_{name}_"))
        ins = {}
        for rel, text in inputs.items():
            if rel == "registry_blocks.txt":
                text = reg.read_text(encoding="utf-8").strip() + "\nexamplemod:jade_brick\n"
            (run / rel).parent.mkdir(parents=True, exist_ok=True)
            (run / rel).write_text(text, encoding="utf-8")
            ins[rel] = text
        r = subprocess.run([sys.executable, "-I", __file__, "--startup-child", str(run)], capture_output=True, text=True,
                           encoding="utf-8", errors="replace")
        if r.returncode:
            raise SystemExit(r.stdout[-2000:] + r.stderr[-3000:])
        files = {}
        for p in sorted(run.rglob("*")):
            rel = p.relative_to(run).as_posix()
            if p.is_file() and rel not in ins and rel != "daemon.lock" and not rel.startswith(("requests/", "out/")):
                files[rel] = p.read_text(encoding="utf-8")
        out[name] = dict(inputs=ins, files=files)
        print(f"startup {name}: {len(files)} files")
    FIX.mkdir(parents=True, exist_ok=True)
    with gzip.open(FIX / "startup.json.gz", "wt", encoding="utf-8", compresslevel=9) as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    print(f"startup: {(FIX / 'startup.json.gz').stat().st_size // 1024} KB")


def main(argv):
    if argv and argv[0] == "--startup-child":
        startup_child(Path(argv[1]))
        return
    which = argv[0] if argv else "all"
    if which in ("startup", "all"):
        capture_startup()
    if which in ("default", "all"):
        capture("default", default_requests())
    if which in ("kinds", "all"):
        capture("kinds", oracle.kind_requests(seed=1, tiers="ends"))
    if which in ("slow", "all"):
        capture("slow", slow_requests())
    if which in ("plans", "all"):
        capture_plans("plans", oracle.plan_requests("all", 1))


if __name__ == "__main__":
    main(sys.argv[1:])
