#!/usr/bin/env bash
set -euo pipefail

MODE="${AI_EVAL_MODE:-local}"
RUN_ID="${AI_EVAL_RUN_ID:-latest}"
OUT_DIR="${AI_EVAL_OUT_DIR:-.generated/evaluations/${RUN_ID}}"
MANIFEST="${AI_EVAL_MANIFEST:-evaluations/golden-datasets/parsing/ingestion-v1/manifest.yaml}"
COVERAGE="${AI_EVAL_COVERAGE:-evaluations/rubrics/ingestion-coverage-v1.yaml}"
BASELINE="${AI_EVAL_BASELINE:-evaluations/baselines/production-ingestion-v1.json}"
POLICY="${AI_EVAL_POLICY:-evaluations/baselines/ingestion-release-gates-v1.yaml}"

mkdir -p "${OUT_DIR}"

python3 evaluations/runners/validate_ingestion_dataset.py \
  --manifest "${MANIFEST}" \
  --coverage "${COVERAGE}"

set +e
python3 evaluations/runners/run_ingestion_eval.py \
  --manifest "${MANIFEST}" \
  --coverage "${COVERAGE}" \
  --mode "${MODE}" \
  --out-dir "${OUT_DIR}"
RUNNER_STATUS=$?
set -e

set +e
python3 evaluations/runners/compare_release.py \
  --baseline "${BASELINE}" \
  --candidate "${OUT_DIR}/ingestion-evaluation-report.json" \
  --policy "${POLICY}" \
  --out "${OUT_DIR}/ingestion-release-comparison.json"
COMPARE_STATUS=$?
set -e

printf 'AI evaluation artifacts written to %s\n' "${OUT_DIR}"

if [[ "${RUNNER_STATUS}" -ne 0 ]]; then
  exit "${RUNNER_STATUS}"
fi

exit "${COMPARE_STATUS}"
