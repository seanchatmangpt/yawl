import subprocess
import json
import os

# Create output directory if it doesn't exist
os.makedirs("/tmp/jtbd/output", exist_ok=True)

# Try to run the jtbd_runner directly
try:
    # First let's try to see if we can run the jtbd_1 test directly
    cmd = [
        "escript", 
        "test/run_jtbd_standalone.erl"
    ]
    
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
    
    print("JTBD Test Runner Output:")
    print("=" * 50)
    print(result.stdout)
    if result.stderr:
        print("STDERR:")
        print(result.stderr)
    print(f"Exit code: {result.returncode}")
    
except Exception as e:
    print(f"Failed to run JTBD tests: {e}")
    
    # Let's try to manually check what files exist
    print("\nChecking test files:")
    print("Input files:")
    for f in ["pi-sprint-ocel.json", "pi-sprint-ocel-v2.json", "malformed.json"]:
        path = f"/tmp/jtbd/input/{f}"
        if os.path.exists(path):
            print(f"  ✓ {path}")
            # Show first few lines
            with open(path, 'r') as file:
                lines = file.readlines()[:3]
                print(f"    Content preview: {lines}")
        else:
            print(f"  ✗ {path}")
    
    print("\nOutput directory contents:")
    if os.path.exists("/tmp/jtbd/output"):
        for f in os.listdir("/tmp/jtbd/output"):
            print(f"  ✓ /tmp/jtbd/output/{f}")
    else:
        print("  ✗ /tmp/jtbd/output")
