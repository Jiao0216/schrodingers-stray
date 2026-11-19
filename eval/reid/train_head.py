#!/usr/bin/env python3
"""Train a small projection head on frozen DINOv2 vectors and compare it with plain DINOv2 on the eval set.

    embedding-service/.venv/bin/python eval/reid/train_head.py eval/reid/data/train.npz eval/reid/data/eval.npz

The head (768 -> 512 -> 256, L2-normalised) is trained with a supervised contrastive loss on P cats x K photos
per batch, pulling photos of the same cat together and pushing other cats apart. Only DINOv2's output vectors
are used, so training takes minutes on a CPU.

Honest evaluation:
  * training cats are split again by cat into train / validation; early stopping and the match threshold are
    chosen on validation only, never on the evaluation set;
  * training photos that are near-duplicates of an evaluation photo are dropped before training;
  * the evaluation set (eval/cats.csv) is scored once at the end, for plain DINOv2 and for the head.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
import torch
from torch import nn

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
from baseline import reid_metrics  # noqa: E402

DEFAULT_OUT = Path(__file__).resolve().parent / "data" / "head.pt"
DUPLICATE_DISTANCE = 0.02  # cosine distance below which two photos are treated as the same picture


class Head(nn.Module):
    def __init__(self, in_dim: int, hidden: int, dim: int):
        super().__init__()
        self.net = nn.Sequential(nn.Linear(in_dim, hidden), nn.GELU(), nn.Dropout(0.1), nn.Linear(hidden, dim))

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return nn.functional.normalize(self.net(x), dim=1)


def supcon_loss(z: torch.Tensor, labels: torch.Tensor, temperature: float) -> torch.Tensor:
    """Supervised contrastive loss (Khosla et al. 2020) over one batch of L2-normalised embeddings."""
    sim = z @ z.T / temperature
    self_mask = torch.eye(len(z), dtype=torch.bool, device=z.device)
    sim = sim.masked_fill(self_mask, -1e9)
    positives = (labels[:, None] == labels[None, :]) & ~self_mask
    log_prob = sim - torch.logsumexp(sim, dim=1, keepdim=True)
    has_pos = positives.any(dim=1)
    per_anchor = -(log_prob * positives).sum(dim=1)[has_pos] / positives.sum(dim=1)[has_pos]
    return per_anchor.mean()


def pk_batches(ids: np.ndarray, p: int, k: int, rng: np.random.Generator):
    """One epoch of batches with P cats x K photos each (sampling with replacement for small cats)."""
    by_cat = {c: np.flatnonzero(ids == c) for c in np.unique(ids)}
    cats = rng.permutation(list(by_cat))
    for i in range(0, len(cats) - p + 1, p):
        idx = [rng.choice(by_cat[c], size=k, replace=len(by_cat[c]) < k) for c in cats[i:i + p]]
        yield np.concatenate(idx)


def pair_distances(emb: np.ndarray, ids: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    iu = np.triu_indices(len(ids), 1)
    return (1.0 - emb @ emb.T)[iu], (ids[:, None] == ids[None, :])[iu]


def threshold_for_recall(emb: np.ndarray, ids: np.ndarray, recall: float) -> float:
    """Smallest distance threshold that matches at least `recall` of the same-cat pairs."""
    dist, same = pair_distances(emb, ids)
    pos = np.sort(dist[same])
    return float(pos[min(len(pos) - 1, int(np.ceil(recall * len(pos))) - 1)]) + 1e-6


def at_threshold(emb: np.ndarray, ids: np.ndarray, t: float) -> tuple[float, int, int]:
    dist, same = pair_distances(emb, ids)
    return float((dist[same] <= t).mean()), int((dist[~same] <= t).sum()), int((~same).sum())


@torch.no_grad()
def project(head: Head, x: np.ndarray) -> np.ndarray:
    head.eval()
    return head(torch.from_numpy(x)).numpy()


def load(path: Path) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    d = np.load(path)
    return d["feats"].astype(np.float32), d["cat_ids"].astype(str), d["images"].astype(str)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("train", type=Path)
    ap.add_argument("eval", type=Path)
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    ap.add_argument("--dim", type=int, default=256)
    ap.add_argument("--hidden", type=int, default=512)
    ap.add_argument("--epochs", type=int, default=60)
    ap.add_argument("--p", type=int, default=32, help="cats per batch")
    ap.add_argument("--k", type=int, default=4, help="photos per cat per batch")
    ap.add_argument("--lr", type=float, default=1e-3)
    ap.add_argument("--temperature", type=float, default=0.1)
    ap.add_argument("--val-frac", type=float, default=0.15, help="share of training cats held out for validation")
    ap.add_argument("--target-recall", type=float, default=0.8, help="recall the match threshold is set for")
    ap.add_argument("--patience", type=int, default=10)
    args = ap.parse_args()
    torch.manual_seed(0)
    rng = np.random.default_rng(0)

    x, ids, images = load(args.train)
    x_eval, ids_eval, _ = load(args.eval)

    # Drop training photos that are (near-)copies of an evaluation photo.
    nearest = (1.0 - x @ x_eval.T).min(axis=1)
    dup = nearest < DUPLICATE_DISTANCE
    if dup.any():
        print(f"dropping {int(dup.sum())} training photos that duplicate evaluation photos, e.g. {images[dup][:3].tolist()}")
    x, ids = x[~dup], ids[~dup]
    cats, counts = np.unique(ids, return_counts=True)
    multi = set(cats[counts >= 2])
    keep = np.array([c in multi for c in ids])
    x, ids = x[keep], ids[keep]

    val_cats = set(rng.permutation(sorted(multi))[: max(2, int(len(multi) * args.val_frac))])
    is_val = np.array([c in val_cats for c in ids])
    x_tr, ids_tr, x_val, ids_val = x[~is_val], ids[~is_val], x[is_val], ids[is_val]
    n_tr_cats = len(set(ids_tr))
    p = min(args.p, n_tr_cats)
    if n_tr_cats < 4 or len(val_cats) < 2:
        raise SystemExit(f"need more training cats (have {n_tr_cats} train / {len(val_cats)} validation with 2+ photos)")
    print(f"train {n_tr_cats} cats / {len(ids_tr)} photos, validation {len(val_cats)} cats / {len(ids_val)} photos, "
          f"eval {len(set(ids_eval))} cats / {len(ids_eval)} photos")

    head = Head(x.shape[1], args.hidden, args.dim)
    opt = torch.optim.AdamW(head.parameters(), lr=args.lr, weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.CosineAnnealingLR(opt, T_max=args.epochs)
    xt, lt = torch.from_numpy(x_tr), torch.from_numpy(np.unique(ids_tr, return_inverse=True)[1])

    raw_val_map = reid_metrics(x_val, ids_val)["mAP"]
    best_map, best_state, best_epoch, stale = -1.0, None, 0, 0
    for epoch in range(1, args.epochs + 1):
        head.train()
        losses = []
        for idx in pk_batches(ids_tr, p, args.k, rng):
            loss = supcon_loss(head(xt[idx]), lt[idx], args.temperature)
            opt.zero_grad()
            loss.backward()
            opt.step()
            losses.append(loss.item())
        sched.step()
        val_map = reid_metrics(project(head, x_val), ids_val)["mAP"]
        if val_map > best_map:
            best_map, best_epoch, stale = val_map, epoch, 0
            best_state = {k: v.clone() for k, v in head.state_dict().items()}
        else:
            stale += 1
        if epoch == 1 or epoch % 5 == 0 or stale == 0:
            print(f"  epoch {epoch:>3}  loss {np.mean(losses):.3f}  validation mAP {val_map:.3f} (plain DINOv2 {raw_val_map:.3f})")
        if stale >= args.patience:
            break
    head.load_state_dict(best_state)
    print(f"best epoch {best_epoch} (validation mAP {best_map:.3f})\n")

    rows = []
    for name, val_emb, eval_emb in (("dinov2", x_val, x_eval), ("dinov2+head", project(head, x_val), project(head, x_eval))):
        t = threshold_for_recall(val_emb, ids_val, args.target_recall)
        m = reid_metrics(eval_emb, ids_eval)
        rec, fm, neg = at_threshold(eval_emb, ids_eval, t)
        rows.append((name, m, t, rec, fm, neg))
    print(f"Evaluation set ({args.eval.name}); threshold set on validation for {args.target_recall:.0%} recall")
    print(f"  {'model':<12} {'rank-1':>7} {'mAP':>6} {'AUC':>6}   {'threshold':>9} {'recall':>7} {'false merges':>13}")
    for name, m, t, rec, fm, neg in rows:
        print(f"  {name:<12} {m['rank1']:>7.0%} {m['mAP']:>6.2f} {m['pair_auc']:>6.2f}   {t:>9.3f} {rec:>7.0%} {fm:>6}/{neg:<6}")

    args.out.parent.mkdir(parents=True, exist_ok=True)
    torch.save({"state_dict": head.state_dict(), "in_dim": x.shape[1], "hidden": args.hidden, "dim": args.dim,
                "input": "dinov2-base CLS, cat crop, square pad, L2-normalised", "threshold": rows[1][2],
                "best_epoch": best_epoch}, args.out)
    print(f"\nsaved {args.out} (use only if dinov2+head beats dinov2 above)")


if __name__ == "__main__":
    main()
