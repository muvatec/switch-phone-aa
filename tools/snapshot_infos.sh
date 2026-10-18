#!/bin/zsh
# Save a sanitized snapshot of the dongle's `infos` (no uuid, MAC, activation code or saved phones).
# Run it on a computer connected to the dongle WiFi after every firmware change, then commit the file.
#   tools/snapshot_infos.sh            -> docs/firmware/infos-<version>.sample.json
OUT_DIR="$(cd "$(dirname "$0")/.." && pwd)/docs/firmware"
mkdir -p "$OUT_DIR"
"$(dirname "$0")/dongle_api.sh" infos | python3 -c '
import sys,json
d=json.load(sys.stdin); b=d["BoxInfo"]
for k in ("uuid","mac","code"): b.pop(k,None)
b["bt"]="AutoKit_xxx"; b["wifi"]="AutoKit_xx"; d["DevList"]=[]; d.pop("LangList",None)
d["_note"]="Sanitized sample: uuid, mac, activation code and saved phones removed."
open(sys.argv[1]+"/infos-"+b["ver"]+".sample.json","w").write(json.dumps(d,indent=2,ensure_ascii=False))
print("saved infos-"+b["ver"]+".sample.json")' "$OUT_DIR"
