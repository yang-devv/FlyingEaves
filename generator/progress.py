"""Progress of the daemon's requests, for the game to show: run/bgen/out/<id>.progress.json
{"stage": "建筑", "pct": 45, "done": 37, "total": 97}. A thread rewrites the file of the request being built every second
(and writes 排队 for the requests still waiting behind it), so the game tells a long build (a big town, a 化神 sect, the
1:1 palace) from a daemon that is not running by whether the file keeps changing, not by how long it has waited.
The file is removed when the result is written. Stages are rough: 规划, 建筑 done/total, 地基, 写出."""
import json
import threading
import time

PCT = {"排队": 0, "规划": 5, "建筑": 10, "地基": 85, "写出": 92}


class Progress:
    def __init__(self, out, req_dir, every=1.0):
        self.out, self.req, self.every = out, req_dir, every
        self.lock = threading.Lock()
        self.id, self.state, self.wrote = None, None, 0.0
        self.stop = threading.Event()

    def path(self, rid):
        return self.out / f"{rid}.progress.json"

    def _write(self, rid, stage, done=None, total=None):
        pct = PCT[stage]
        if stage == "建筑" and total:
            pct = PCT["建筑"] + (PCT["地基"] - PCT["建筑"]) * done // total
        d = dict(stage=stage, pct=pct)
        if total:
            d.update(done=done, total=total)
        tmp = self.out / f"{rid}.progress.tmp"
        try:
            tmp.write_text(json.dumps(d, ensure_ascii=False), encoding="utf-8")
            tmp.replace(self.path(rid))
        except OSError:
            pass                                    # (the game is reading it: the next beat writes it again)

    def start(self, rid):
        with self.lock:
            self.id, self.state = rid, ("规划", None, None)
            self._write(rid, *self.state)
            self.wrote = time.time()

    def set(self, stage, done=None, total=None):
        with self.lock:
            if self.id is None:
                return
            new_stage = self.state[0] != stage
            self.state = (stage, done, total)
            if new_stage or time.time() - self.wrote > 0.5:   # (a town's few hundred rooms go by fast)
                self._write(self.id, *self.state)
                self.wrote = time.time()

    def finish(self, rid):
        """The result is written (or the request dropped): the game stops looking."""
        with self.lock:
            if self.id == rid:
                self.id, self.state = None, None
            self.path(rid).unlink(missing_ok=True)

    def beat(self):
        with self.lock:
            if self.id is not None:
                self._write(self.id, *self.state)
                self.wrote = time.time()
            for q in self.req.glob("*.json"):
                if q.stem != self.id:
                    self._write(q.stem, "排队")

    def run(self):
        while not self.stop.wait(self.every):
            try:
                self.beat()
            except Exception:
                pass

    def start_thread(self):
        threading.Thread(target=self.run, name="progress", daemon=True).start()
        return self
