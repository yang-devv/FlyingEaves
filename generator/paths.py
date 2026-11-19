"""Where the generator keeps its working files: the game's flyingeaves folder - requests and results (the file protocol
with the mod), the player's own materials and parts, the block list the game writes, caches.

bgen_daemon.py sets it from --run (the mod passes <game dir>/flyingeaves) through FLYINGEAVES_RUN before it imports the
rest; tools and tests run from this folder use generator/run.
"""
import os
from pathlib import Path

HERE = Path(__file__).resolve().parent
RUN = Path(os.environ.get("FLYINGEAVES_RUN") or (HERE / "run")).resolve()
