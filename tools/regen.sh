#!/usr/bin/env bash
# Regenerate the lifted C from the XBE: disasm -> func_id -> abi -> recomp.
#
#   tools/regen.sh
#
# Environment:
#   XBOXRECOMP_DIR  toolkit            (default: the vendored xboxrecomp/)
#   B3_XBE       default.xbe        (default: <repo>/roms/game/default.xbe)
#   B3_GEN_DIR   output directory   (default: <repo>/.work/gen)
#   PYTHON       interpreter with capstone (default: <repo>/.work/venv/bin/python
#                when present, else python3; pip install -r tools/requirements.txt)
#   LIFT_ONLY=1     skip disasm/func_id/abi (enough after editing
#                   recomp_manual.c; seeds need the full run)
#   TRACE_FUNCS     JSON list of addresses whose entries are logged
#                   ([TRACE] lines; a bring-up probe, regenerate without it)
#
# Seeds (config/seed_functions.json) are entry points the static pass cannot
# see -- thread start routines and indirect-call targets found at runtime.
# Grow them with:  python3 -m tools.seed_from_log run.log default.xbe \
#                    --functions tools/disasm/output/functions.json \
#                    --seeds <this repo>/config/seed_functions.json
# and review every addition: a seed inside a function splits it.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TK="${XBOXRECOMP_DIR:-$HERE/xboxrecomp}"
XBE="${B3_XBE:-$HERE/roms/game/default.xbe}"
GEN="${B3_GEN_DIR:-$HERE/.work/gen}"
if [ -z "${PYTHON:-}" ]; then
    if [ -x "$HERE/.work/venv/bin/python" ]; then PYTHON="$HERE/.work/venv/bin/python"; else PYTHON=python3; fi
fi

cd "$TK"
if [ "${LIFT_ONLY:-0}" != "1" ]; then
    # disasm reads the section layout from <xbe stem>_analysis.json beside it.
    ANALYSIS="${XBE%.xbe}_analysis.json"
    [ -f "$ANALYSIS" ] || "$PYTHON" -m tools.xbe_parser "$XBE" --json "$ANALYSIS" --quiet
    "$PYTHON" -m tools.disasm "$XBE" --seed-functions "$HERE/config/seed_functions.json"
    "$PYTHON" -m tools.func_id "$XBE"
    "$PYTHON" -m tools.abi_analysis "$XBE"
fi
mkdir -p "$GEN"
# recomp_types.h is written only when absent, so a stale copy would hide
# register-model changes in the toolkit. Nothing here edits it by hand.
rm -f "$GEN/recomp_types.h"
"$PYTHON" -m tools.recomp "$XBE" --game-name "Burnout 3" --all --split 250 \
    --gen-dir "$GEN" --exclude-manual "$HERE/src/recomp_manual.c" \
    --mmio-sections DSOUND,XPP,D3D,XGRPH \
    ${TRACE_FUNCS:+--trace-functions "$TRACE_FUNCS"}
