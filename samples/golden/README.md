# Golden outputs and metric baselines

Populated in Phase 3. For each corpus book (`samples/corpus/manifest.yaml` id):

- `baselines/<id>.json`: committed metric baseline (text coverage, reading-order accuracy,
  equations verified, tables reconstructed, figures with captions matched, cross-references resolved,
  EPUBCheck pass, seconds per page).
- `<id>/output/`: local conversion output (gitignored).

CI fails when a metric drops beyond its configured tolerance versus the baseline.
