# Accuracy evaluation

Measures ear-tip (TNR clip) detection and same-cat matching on your own labelled photos, so changes to prompts,
models or thresholds can be compared with numbers instead of impressions.

## 1. Collect photos

Put photos in `eval/photos/` (git-ignored — they stay on your machine). Aim for at least 30 ear-tip photos with a
mix of ear-tipped and intact cats, including hard cases (blurry, far away, ears partly hidden), and 20+ same-cat
pairs (half the same cat, half different but similar-looking cats).

## 2. Label them

`ear_tip_labels.csv` — one photo per row:

| image | label |
|---|---|
| photos/cat01.jpg | `yes` (ear-tipped) / `no` (both ear tips intact) / `unclear` (a person can't tell) |

`same_cat_pairs.csv` — one pair per row: `image_a,image_b,same` with `same` = `1` (same individual) or `0`.

Lines starting with `#` are comments.

## 3. Run

Backend on `:8090` (with a valid `OPENAI_API_KEY`) and embedding-service on `:8001`:

```bash
python3 eval/run_eval.py ear-tip eval/ear_tip_labels.csv
python3 eval/run_eval.py same-cat eval/same_cat_pairs.csv
```

The ear-tip run calls OpenAI (votes × photos requests); the same-cat run is local only. Per-photo results are
written to `eval/*_results.csv`.

## 4. Compare settings

| Question | Command |
|---|---|
| Does the head close-up help? | add `--no-head-crop` and compare |
| Is a stronger model worth it? | `--model gpt-4o` vs. the default |
| How many votes? | `--votes 1` / `--votes 3` / `--votes 5` |
| Old single-prompt behaviour | restart with `CAT_RESCUE_EAR_TIP_ENABLED=false`, then `--mode main` |
| Does cropping to the cat help? | `same-cat … --no-crop` vs. default |

The number that matters most for ear tips is the **false "tipped" rate**: an intact cat called ear-tipped is
skipped for TNR. The `unclear` rate is the price paid for keeping that low — those photos go to a person.

For same-cat, the script prints the best distance threshold for the running encoder on your pairs; once
volunteers have confirmed and rejected enough real matches, the app also fits this automatically
(`GET /api/v1/admin/clip-calibration`).

## 5. No-training baselines

Before training anything, `baseline.py` checks how far frozen, off-the-shelf models get. It runs locally in the
embedding-service virtualenv (no backend, OpenAI key or running service); weights download on first use.

| Backbone | What it is |
|---|---|
| `clip` | CLIP ViT-B/32, the app's previous encoder (`CAT_EMBEDDING_MODEL=clip`) |
| `dinov2` | DINOv2 base, self-supervised, sensitive to fine texture; the app's default encoder |
| `mega` | MegaDescriptor-T, trained for wildlife re-identification |
| `siglip` | AvitoTech SigLIP-Base, fine-tuned for pet re-identification on ~1.9M photos (PetFace, LCW, …) |

**Same cat** needs identity labels instead of pairs: `cats.csv` has one photo per row, `image,cat_id`, where photos
of the same individual share a `cat_id` (aim for 3+ photos per cat, taken on different days). It reports rank-1,
mAP, pair AUC and, at each backbone's best threshold, the **false-merge rate** (different cats matched).

**Ear tip** reuses `ear_tip_labels.csv` (optionally with a `cat_id` column so photos of one cat stay in the same
fold). It trains a logistic regression on the head-crop features with cross-validation and reports the false
"tipped" rate, plus the threshold that keeps it under `--max-false-yes` (default 5%) and how many tips are still
caught there. `unclear` rows are skipped.

```bash
embedding-service/.venv/bin/pip install -r eval/requirements-baseline.txt
cd eval
../embedding-service/.venv/bin/python baseline.py same-cat cats.csv
../embedding-service/.venv/bin/python baseline.py ear-tip ear_tip_labels.csv
```

Options: `--backbones clip,mega` to run a subset; `--crop none` (same cat) or `--crop cat` / `none` (ear tip) to
see what the detector crop adds. If one backbone clearly beats `clip` on your photos, switching the embedding
service to it (with a new `embedding_version` and a recalibrated threshold) is the cheapest accuracy gain before
any fine-tuning.

For the next step after these baselines, training a projection head on frozen DINOv2 vectors, see
[`reid/README.md`](reid/README.md).
