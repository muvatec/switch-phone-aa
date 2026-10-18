#!/bin/zsh
# Talk to the dongle's web API from a computer that is on the dongle's WiFi.
#   tools/dongle_api.sh infos
#   tools/dongle_api.sh set <item> <value>
#   tools/dongle_api.sh restart
# macOS (uses `md5`). On Linux replace the md5 call with `md5sum | cut -d' ' -f1`.
SALT='HweL*@M@JEYUnvPw9G36MVB9X6u@2qxK'   # constant from the dongle's own web UI
HOST="${DONGLE_HOST:-192.168.50.2}"
ts=$(python3 -c 'import time;print(int(time.time()*1000))')
if [ "$1" = set ]; then
  sign=$(printf '%s' "cmd=set&item=$2&ts=$ts&val=$3$SALT" | md5)
  curl -s -m8 -d "cmd=set&item=$2&val=$3&ts=$ts&sign=$sign" "http://$HOST/cgi-bin/server.cgi"
else
  sign=$(printf '%s' "cmd=$1&ts=$ts$SALT" | md5)
  curl -s -m8 -d "cmd=$1&ts=$ts&sign=$sign" "http://$HOST/cgi-bin/server.cgi"
fi
