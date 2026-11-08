#!/system/bin/sh
# HGSoft Overuse Controller optional ROOT ONLY CPUFreq helper.
# Controlled paths are hard-coded. One policy's original scaling_max_freq per line.
set -u
ACTION="$1"
BACKUP="$2"
ARG="$3"
HEARTBEAT="$4"
ROOT="/sys/devices/system/cpu/cpufreq"

valid_num() {
    case "$1" in *[!0-9]*|'') return 1;; *) return 0;; esac
}
restore_cpu() {
    [ -f "$BACKUP" ] || { echo "RESTORED"; return 0; }
    FAILED=0
    while read -r P ORIG; do
        case "$P" in "$ROOT"/policy[0-9]*) ;; *) FAILED=1; continue;; esac
        valid_num "$ORIG" || { FAILED=1; continue; }
        [ -f "$P/scaling_max_freq" ] || { FAILED=1; continue; }
        echo "$ORIG" > "$P/scaling_max_freq" 2>/dev/null || FAILED=1
    done < "$BACKUP"
    if [ "$FAILED" -eq 0 ]; then
        rm -f "$BACKUP"
        echo "RESTORED"
        return 0
    fi
    echo "ERROR_RESTORE_PARTIAL"
    return 9
}
case "$ACTION" in
    restore)
        restore_cpu
        ;;
    apply)
        PCT="$ARG"
        valid_num "$PCT" || exit 2
        [ "$PCT" -ge 70 ] && [ "$PCT" -le 100 ] || exit 3
        [ -d "$ROOT" ] || { echo "ERROR_NO_CPUFREQ"; exit 4; }
        if [ ! -s "$BACKUP" ]; then
            TEMP="$BACKUP.new"
            : > "$TEMP" || exit 5
            COUNT=0
            for P in "$ROOT"/policy[0-9]*; do
                [ -r "$P/scaling_max_freq" ] || continue
                [ -r "$P/scaling_min_freq" ] || continue
                ORIGINAL="$(cat "$P/scaling_max_freq" 2>/dev/null)"
                valid_num "$ORIGINAL" || continue
                echo "$P $ORIGINAL" >> "$TEMP"
                COUNT=$((COUNT+1))
            done
            if [ "$COUNT" -eq 0 ]; then
                rm -f "$TEMP"
                echo "ERROR_NO_WRITABLE_POLICY"
                exit 6
            fi
            mv "$TEMP" "$BACKUP"
            chmod 600 "$BACKUP"
        fi
        APPLIED=0
        FAILED=0
        while read -r P ORIGINAL; do
            case "$P" in "$ROOT"/policy[0-9]*) ;; *) FAILED=1; continue;; esac
            valid_num "$ORIGINAL" || { FAILED=1; continue; }
            [ -f "$P/scaling_max_freq" ] || { FAILED=1; continue; }
            MIN="$(cat "$P/scaling_min_freq" 2>/dev/null)"
            valid_num "$MIN" || { FAILED=1; continue; }
            TARGET=$((ORIGINAL*PCT/100))
            if [ "$TARGET" -lt "$MIN" ]; then TARGET="$MIN"; fi
            echo "$TARGET" > "$P/scaling_max_freq" 2>/dev/null && APPLIED=$((APPLIED+1)) || FAILED=1
        done < "$BACKUP"
        if [ "$APPLIED" -eq 0 ]; then
            restore_cpu >/dev/null 2>&1
            echo "ERROR_CPUFREQ_WRITE"
            exit 7
        fi
        if [ "$FAILED" -ne 0 ]; then
            echo "PARTIAL_CPU_LIMIT_$PCT"
        else
            echo "CPU_LIMIT_$PCT"
        fi
        ;;
    watch)
        # Fail-safe: if the foreground monitor stops sending heartbeat,
        # restore all the original CPU policy ceilings within 75 seconds.
        while [ -s "$BACKUP" ]; do
            NOW="$(date +%s)"
            LAST="$(cat "$HEARTBEAT" 2>/dev/null)"
            if ! valid_num "$LAST"; then
                restore_cpu
                exit 0
            fi
            DIFF=$((NOW-LAST))
            if [ "$DIFF" -gt 75 ] || [ "$DIFF" -lt -15 ]; then
                restore_cpu
                exit 0
            fi
            sleep 5
        done
        ;;
    *)
        echo "ERROR_UNKNOWN_MODE"
        exit 1
        ;;
esac
