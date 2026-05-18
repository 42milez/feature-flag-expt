#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

"$SCRIPT_DIR/kind-load-image.sh"
"$SCRIPT_DIR/k8s-apply-dev.sh"
"$SCRIPT_DIR/k8s-wait-dev.sh"
"$SCRIPT_DIR/k8s-status-dev.sh"
