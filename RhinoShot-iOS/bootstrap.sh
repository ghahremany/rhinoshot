#!/usr/bin/env bash
#
# RhinoShot for iOS — one-command project bootstrap.
#
#   ./bootstrap.sh          generate the Xcode project and open it
#   ./bootstrap.sh generate generate only, do not open Xcode
#
# Requirements: macOS with Xcode 15 or newer (Xcode 16 recommended) and
# Homebrew (https://brew.sh). XcodeGen is installed automatically if missing.

set -euo pipefail

cd "$(dirname "$0")"

MODE="${1:-open}"

if ! command -v xcodegen >/dev/null 2>&1; then
  echo "==> XcodeGen is not installed. Installing it with Homebrew..."
  if ! command -v brew >/dev/null 2>&1; then
    echo "!!! Homebrew was not found."
    echo "    Install it from https://brew.sh and run this script again."
    exit 1
  fi
  brew install xcodegen
fi

echo "==> Generating the Xcode project..."
xcodegen generate

echo "==> Checking the MediaPipe model..."
MODEL="RhinoShot/Resources/face_landmarker.task"
if [ ! -f "$MODEL" ]; then
  echo "!!! $MODEL is missing."
  echo "    Download it with:"
  echo "    curl -L -o $MODEL https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task"
  exit 1
fi
echo "    model ok ($(du -h "$MODEL" | cut -f1))"

if [ "$MODE" = "open" ]; then
  echo "==> Opening Xcode..."
  open RhinoShot.xcodeproj
  cat <<'EOF'

Next steps in Xcode:
  1. Select the "RhinoShot" target > "Signing & Capabilities".
  2. Choose your Team (a free Apple ID is enough for a 7-day install on your
     own iPhone) and set a unique Bundle Identifier, e.g. ir.rhinoshot.clinic.
  3. Connect the iPhone, select it as the run destination and press Run.

See docs/BUILD-AND-INSTALL.md for the full guide (free Apple ID, TestFlight,
App Store and troubleshooting).
EOF
else
  echo "==> Done."
fi
