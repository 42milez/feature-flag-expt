#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

"$SCRIPT_DIR/kind-delete.sh"
"$SCRIPT_DIR/kind-create.sh"
kubectl get nodes --show-labels
