"""Oracle for the Java port: run the Python generator (generator/) on requests and write what the daemon would write.

python -I tools/oracle/oracle_dump.py OUT_DIR REQ.json [REQ.json ...]
  For each request (a daemon request: {"id", "kind", ...} or {"type": "compose", "rooms": [...]}), writes
  OUT_DIR/<id>.dvcr (as the daemon's export) and OUT_DIR/<id>.json (the daemon's result, without "seconds").
  A request file may hold a list of requests.
  --trace: also OUT_DIR/<id>.calls.txt, the generator functions that ran (for scoping the port).

Runs with an empty private run folder (no registry_blocks.txt, no player materials/parts): only minecraft: blocks count
as present, as the Java port assumes by default.
"""
import json
import os
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
GEN = HERE.parent.parent / "generator"


def main(argv):
    trace = "--trace" in argv
    argv = [a for a in argv if a != "--trace"]
    out = Path(argv[0]).resolve()
    out.mkdir(parents=True, exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix="fe_oracle_run_"))
    os.environ["FLYINGEAVES_RUN"] = str(run)
    sys.path.insert(0, str(GEN))
    import bgen_daemon as D
    D.OUT = out
    reqs = []
    for p in argv[1:]:
        data = json.loads(Path(p).read_text(encoding="utf-8"))
        reqs += data if isinstance(data, list) else [data]
    for req in reqs:
        if req.get("type") == "plan":                  # a planner: the design back (JSON text compared as is)
            try:
                res = dict(ok=True, design=plan(req))
            except Exception as e:
                import traceback
                traceback.print_exc()
                res = dict(ok=False, error=f"{type(e).__name__}: {e}")
            (out / f"{req['id']}.design.json").write_text(json.dumps(res, ensure_ascii=False), encoding="utf-8")
            print(req["id"], res.get("ok"), flush=True)
            continue
        calls = {}
        if trace:
            def prof(frame, event, arg):
                if event == "call":
                    co = frame.f_code
                    fn = Path(co.co_filename).name
                    if Path(co.co_filename).parent == GEN:
                        k = f"{fn}:{co.co_firstlineno}:{co.co_name}"
                        calls[k] = calls.get(k, 0) + 1
            sys.setprofile(prof)
        try:
            res = D.build(json.loads(json.dumps(req)))
        except Exception as e:
            import traceback
            traceback.print_exc()
            res = dict(ok=False, error=f"{type(e).__name__}: {e}")
        finally:
            sys.setprofile(None)
        (out / f"{req['id']}.json").write_text(json.dumps(res, ensure_ascii=False, sort_keys=True), encoding="utf-8")
        if trace:
            (out / f"{req['id']}.calls.txt").write_text("\n".join(f"{v}\t{k}" for k, v in sorted(calls.items())), encoding="utf-8")
        print(req["id"], res.get("ok"), res.get("blocks"), flush=True)


def plan(req):
    """A planner request (oracle.py plans): fn site / city / city_real / free / town / sect / sentence / crowded, or
    daemon (the daemon's "site" / "sentence" branch with its random source seeded by rs)."""
    import random
    import siteplan
    fn = req["fn"]
    if fn == "site":
        return siteplan.site(req["kind"], int(req["jin"]), int(req["seed"]), river=req.get("river", True))
    if fn == "city":
        return siteplan.city(int(req["seed"]))
    if fn == "city_real":
        return siteplan.city_real(int(req["seed"]), moat=bool(req.get("moat", False)))
    if fn == "free":
        return siteplan.randomize(siteplan.site_free(int(req["seed"])), random.Random(int(req["seed"])))
    if fn == "site_free":
        return siteplan.site_free(int(req["seed"]))
    if fn == "crowded":
        d = plan(dict(req, fn=req["of"]))
        return [list(c) for c in siteplan.crowded(d["rooms"], int(req.get("strip", siteplan.WALK)))]
    if fn == "town":
        import town
        return town.town(int(req["seed"]), heights=req.get("heights"), size=req.get("size"), layout=req.get("layout"), box=req.get("box"))
    if fn == "sect":
        import sect_plan
        return sect_plan.sect(int(req["seed"]), tier=req.get("tier", "jindan"), heights=req.get("heights"))
    if fn == "sentence":
        import sentence
        d, said = sentence.parse(req["text"])
        return dict(design=d, said=said)
    if fn == "daemon":                                 # bgen_daemon.main's "site" / "sentence" branch, rs seeded
        import bgen_daemon as D
        q = req["req"]
        rs = random.Random(int(req["rs"]))
        if q["type"] == "site":
            if q.get("which") == "city":
                d = siteplan.city(rs.randrange(1, 1 << 30))
            elif q.get("which") == "city_real":
                d = D.city_real_site(rs.randrange(1, 1 << 30))
            elif q.get("which") == "town":
                import town
                d = town.town(rs.randrange(1, 1 << 62), heights=q.get("heights"), size=q.get("size") or None, box=q.get("box"))
            elif q.get("which") == "sect":
                import sect_plan
                d = sect_plan.sect(rs.randrange(1, 1 << 62), tier=str(q.get("tier") or "jindan"), heights=q.get("heights"))
            else:
                d = siteplan.randomize(siteplan.site_free(rs.randrange(1, 1 << 62)), rs)
            said = [d["name"]]
        else:
            import sentence
            d, said = sentence.parse(str(q.get("text", "")))
        return dict(ok=True, design=d, said=said, site=q["type"] == "site")
    raise ValueError("unknown plan fn " + fn)


if __name__ == "__main__":
    main(sys.argv[1:])
