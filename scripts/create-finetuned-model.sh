#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DEFAULT_OLLAMA_BIN="$(command -v ollama || true)"
OLLAMA_BIN="${OLLAMA_BIN:-$DEFAULT_OLLAMA_BIN}"

if [ ! -x "$OLLAMA_BIN" ]; then
  echo "Cannot find Ollama."
  echo "Install Ollama or set OLLAMA_BIN to the ollama executable path."
  exit 1
fi

"$OLLAMA_BIN" create mindbridge-qwen2.5-7b-ft:latest -f "$ROOT_DIR/models/mindbridge-qwen2.5-7b-ft/Modelfile"

echo "Created mindbridge-qwen2.5-7b-ft:latest"
echo "Run MindMate with: ./scripts/run-dev.sh"
