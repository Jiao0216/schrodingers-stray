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
| Does cropping to the cat help CLIP? | `same-cat … --no-crop` vs. default |

The number that matters most for ear tips is the **false "tipped" rate**: an intact cat called ear-tipped is
skipped for TNR. The `unclear` rate is the price paid for keeping that low — those photos go to a person.

For same-cat, the script prints the best CLIP distance threshold on your pairs; once volunteers have confirmed
and rejected enough real matches, the app also fits this automatically
(`GET /api/v1/admin/clip-calibration`).
