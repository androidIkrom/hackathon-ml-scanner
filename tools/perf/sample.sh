# Runs on the phone (pushed by tour.sh): every 3 s, the app's memory (PSS, kB), its CPU (% of one core) and the
# battery's heat (tenths of a degree), $1 times.
n="$1"
while [ "$n" -gt 0 ]; do
  n=$((n - 1))
  t=$(date "+%m-%d %H:%M:%S")
  p=$(pidof com.nungil)
  pss=$(dumpsys meminfo com.nungil | sed -n "s/.*TOTAL PSS: *\([0-9]*\).*/\1/p")
  [ -z "$pss" ] && pss=$(dumpsys meminfo com.nungil | sed -n "s/^ *TOTAL: *\([0-9]*\).*/\1/p")
  cpu=0
  [ -n "$p" ] && cpu=$(top -b -n 1 -p "$p" | tail -1 | awk '{print $9}')
  temp=$(dumpsys battery | sed -n "s/.*temperature: *\([0-9]*\).*/\1/p")
  echo "SAMPLE $t pss=${pss:-0} cpu=${cpu:-0} temp=${temp:-0}"
  sleep 3
done
