#!/usr/bin/env bash
#
# Pure banner renderer for harness/logs/status.jsonl.
#
#   render_banner <status_file> <alive:0|1> [now_epoch]  ->  exactly 4 lines on stdout
#
# No ANSI, no terminal queries, no side effects. Everything the banner decides — which run
# is current, which phase is running, staleness, the fix badge, the terminal outcome — is
# decided here, which is why this is the feature's only test seam. watch.sh does terminal
# management and nothing else.
#
# Malformed lines are dropped (fromjson? // empty), so a torn append degrades to a dropped
# frame rather than a crash.

# jq program. $alive and $now are injected; the input is the slurped array of valid events.
# The phase-label helper is `chip_name`, NOT `label`: `label` is a reserved jq keyword
# (`label $out | ...`) and defining it is a compile error.
# shellcheck disable=SC2016
_BANNER_JQ='
def chip_name($p):
  if   $p=="PICK"      then "pick"
  elif $p=="IMPL"      then "impl"
  elif $p=="FAST_GATE" then "fast"
  elif $p=="IT_GATE"   then "IT"
  elif $p=="REVIEW"    then "rev"
  elif $p=="PR"        then "pr"
  elif $p=="CI_WAIT"   then "ci"
  elif $p=="MERGE"     then "merge"
  else $p end;

def sym($s):
  if   $s=="ok"    then "✓"
  elif $s=="red"   then "✗"
  elif $s=="skip"  then "–"
  elif $s=="start" then "▶"
  else "·" end;

def elapsed($secs):
  (if $secs < 0 then 0 else $secs end) as $t
  | if $t < 60 then "\($t)s"
    else "\(($t / 60) | floor)m\($t % 60)s" end;

if length == 0 then
  "no run yet", "", "", "(waiting for the first phase event)"
else
  .[-1]                                                  as $last
  | [ .[] | select(.run == $last.run) ]                  as $ev
  | (reduce $ev[] as $e ({}; .[$e.phase] = $e))          as $st
  | ([ $ev[] | select(.phase == "FIX" and .state == "start") ] | length) as $fixes
  | ($st["DONE"])                                        as $done
  | ([ $ev[] | select(.state == "start") ] | last)       as $lastStart
  | (if $lastStart != null and $st[$lastStart.phase].state == "start"
     then $lastStart else null end)                      as $cur

  | def chip($p):
      ($st[$p].state // "none") as $s
      | sym($s) + " " + chip_name($p)
        + (if $cur != null and $cur.phase == $p
           then " " + elapsed($now - $cur.ts)
           else "" end);

    ( "US-\($last.issue) · iter \($last.iter) · pass \($last.pass) · budget \($last.budget)" ),

    ( ([ "PICK", "IMPL", "FAST_GATE", "IT_GATE" ] | map(chip(.)) | join("  "))
      + (if $fixes > 0 then "  ↺ fix \($fixes)" else "" end) ),

    ( [ "REVIEW", "PR", "CI_WAIT", "MERGE" ] | map(chip(.)) | join("  ") ),

    ( if $done != null then
        "DONE " + ($done.detail // "")
      elif $alive == 0 then
        "STALE (loop died in " + chip_name((($cur // $last).phase)) + ")"
      else
        "RUNNING (pid \($last.pid))"
      end )
end
'

# render_banner STATUS_FILE ALIVE [NOW]
render_banner() {
  local file="$1" alive="${2:-1}" now="${3:-}"
  [[ -n "$now" ]] || now="$(date +%s)"

  if [[ ! -f "$file" ]]; then
    printf 'no run yet\n\n\n(waiting for %s)\n' "$file"
    return 0
  fi

  # tail bounds the read on a long-lived run; 500 lines is many multiples of one iteration.
  # The first jq drops malformed lines; the second slurps the survivors and renders.
  tail -n 500 "$file" \
    | jq -R 'fromjson? // empty' \
    | jq -s -r --argjson alive "$alive" --argjson now "$now" "$_BANNER_JQ"
}
