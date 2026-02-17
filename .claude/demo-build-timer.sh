#!/bin/bash
set -euo pipefail

# Demo script to test build-timer.sh functionality
# Creates a mock Maven command that simulates a successful build

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "====================================="
echo "  Build Timer Demo"
echo "====================================="
echo ""
echo "This demonstrates the build-timer.sh"
echo "functionality with simulated data."
echo ""

cd "$PROJECT_ROOT"

export PATH="/tmp:$PATH"

cat > /tmp/mvn <<'EOF'
#!/bin/bash
echo "[INFO] Scanning for projects..."
echo "[INFO] ------------------------------------------------------------------------"
echo "[INFO] Reactor Build Order:"
echo "[INFO] "
echo "[INFO] YAWL Parent                                                        [pom]"
echo "[INFO] YAWL Utilities                                                     [jar]"
echo "[INFO] YAWL Elements                                                      [jar]"
echo "[INFO] YAWL Engine                                                        [jar]"
echo "[INFO] YAWL Stateless Engine                                              [jar]"
echo "[INFO] YAWL Resourcing                                                    [jar]"
echo "[INFO] YAWL Worklet Service                                               [jar]"
echo "[INFO] YAWL Scheduling Service                                            [jar]"
echo "[INFO] YAWL Integration                                                   [jar]"
echo "[INFO] YAWL Monitoring                                                    [jar]"
echo "[INFO] YAWL Control Panel                                                 [jar]"
echo "[INFO] "
echo "[INFO] Using the MultiThreadedBuilder implementation with a thread count of 4"
echo "[INFO] "
sleep 0.5
echo "[INFO] ------------------------------------------------------------------------"
echo "[INFO] Reactor Summary for YAWL Parent 5.2:"
echo "[INFO] "
echo "[INFO] YAWL Parent ........................................ SUCCESS [  0.004 s]"
echo "[INFO] YAWL Utilities ..................................... SUCCESS [  3.234 s]"
echo "[INFO] YAWL Elements ...................................... SUCCESS [  4.127 s]"
echo "[INFO] YAWL Engine ........................................ SUCCESS [  8.543 s]"
echo "[INFO] YAWL Stateless Engine .............................. SUCCESS [  4.182 s]"
echo "[INFO] YAWL Resourcing .................................... SUCCESS [  6.321 s]"
echo "[INFO] YAWL Worklet Service ............................... SUCCESS [  3.876 s]"
echo "[INFO] YAWL Scheduling Service ............................ SUCCESS [  3.654 s]"
echo "[INFO] YAWL Integration ................................... SUCCESS [  5.832 s]"
echo "[INFO] YAWL Monitoring .................................... SUCCESS [  4.921 s]"
echo "[INFO] YAWL Control Panel ................................. SUCCESS [  4.732 s]"
echo "[INFO] ------------------------------------------------------------------------"
echo "[INFO] BUILD SUCCESS"
echo "[INFO] ------------------------------------------------------------------------"
echo "[INFO] Total time:  54.234 s"
echo "[INFO] Finished at: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "[INFO] ------------------------------------------------------------------------"
exit 0
EOF

chmod +x /tmp/mvn

"$SCRIPT_DIR/build-timer.sh" compile -T 1C

echo ""
echo "====================================="
echo "  Performance Data Generated"
echo "====================================="
echo ""

if [[ -f "$PROJECT_ROOT/build-performance.json" ]]; then
    echo "Content of build-performance.json:"
    echo ""
    cat "$PROJECT_ROOT/build-performance.json"
    echo ""
    echo ""
    echo "====================================="
    echo "  Latest Build Summary"
    echo "====================================="
    echo ""
    if command -v jq &>/dev/null; then
        cat "$PROJECT_ROOT/build-performance.json" | jq -r '.[-1] |
            "Timestamp: \(.timestamp)\n" +
            "Command: \(.build_command)\n" +
            "Total Time: \(.total_time_seconds)s\n" +
            "Threads: \(.parallel_threads)\n" +
            "Cache Hit: \(.cache_hit)\n" +
            "\nModule Breakdown:\n" +
            (.modules | to_entries | map("  \(.key): \(.value)s") | join("\n"))'
    else
        echo "Install jq for better JSON display"
        echo "Showing raw data:"
        cat "$PROJECT_ROOT/build-performance.json"
    fi
else
    echo "Error: build-performance.json not created"
fi

rm -f /tmp/mvn

echo ""
echo "====================================="
echo "Demo complete!"
echo "====================================="
