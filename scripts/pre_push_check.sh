#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

echo "Checking pending diff formatting..."
git diff --check
git diff --cached --check

echo "Checking the Android test-compilation gate itself..."
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH="$ROOT/tools${PYTHONPATH:+:$PYTHONPATH}" \
    python3 -m unittest discover \
    -s tools \
    -p 'test_check_android_test_compilation.py' \
    -v

echo "Checking formal specification consistency..."
python3 tools/check_spec_requirements.py docs/specification

echo "Compiling Android test source sets..."
python3 tools/check_android_test_compilation.py --root "$ROOT" --mode auto

echo "Pre-push checks passed."
