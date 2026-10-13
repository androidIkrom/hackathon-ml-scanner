"""Turns the phone's raw benchmark output into the article's numbers (see prepare.py for the whole run).

    python bench/analyze.py [data-dir]        (default D:/nungil-bench)

Reads <data-dir>/out (pulled from the phone) and, when present, bench/field-trials.csv.
Prints Markdown tables and writes <data-dir>/results.json. Every interval is 95%.
"""
import contextlib
import csv
import io
import json
import math
import random
import statistics
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np

SEED = 2026
WARM_UP = 20            # first frames of each run, left out of the latency numbers
IOU = 0.5
FACE_THRESHOLD, FACE_MARGIN, FACE_TOP = 0.5, 0.08, 3   # FaceMatcher.kt
GALLERIES, ENROLLED, PROBES, TRIALS = (1, 3, 5, 10, 20), 5, 5, 300   # open-set face protocol: saved people, samples each


def wilson(k: int, n: int) -> tuple[float, float]:
    if n == 0:
        return (0.0, 0.0)
    z, p = 1.96, k / n
    centre, half = p + z * z / (2 * n), z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n))
    return ((centre - half) / (1 + z * z / n), (centre + half) / (1 + z * z / n))


def fisher(a: int, b: int, c: int, d: int) -> float:
    """Two-sided Fisher exact test for the table [[a, b], [c, d]]."""
    row, col, n = a + b, a + c, a + b + c + d
    prob = lambda x: math.comb(col, x) * math.comb(n - col, row - x) / math.comb(n, row)
    seen = prob(a)
    return min(1.0, sum(p for x in range(max(0, row + col - n), min(row, col) + 1) if (p := prob(x)) <= seen * (1 + 1e-9)))


def bootstrap(counts: np.ndarray, ratio) -> tuple[float, float]:
    """Interval for a ratio of summed columns, resampling whole images (or trials)."""
    rng = np.random.default_rng(SEED)
    picks = rng.integers(0, len(counts), size=(2000, len(counts)))
    values = [ratio(counts[pick].sum(axis=0)) for pick in picks]
    return (float(np.percentile(values, 2.5)), float(np.percentile(values, 97.5)))


def pct(value: float, interval: tuple[float, float] | None = None) -> str:
    return f"{100 * value:.1f}%" + (f" [{100 * interval[0]:.1f}, {100 * interval[1]:.1f}]" if interval else "")


def iou(a, b) -> float:
    w = min(a[0] + a[2], b[0] + b[2]) - max(a[0], b[0])
    h = min(a[1] + a[3], b[1] + b[3]) - max(a[1], b[1])
    if w <= 0 or h <= 0:
        return 0.0
    return w * h / (a[2] * a[3] + b[2] * b[3] - w * h)


def covered(box, crowd) -> float:
    w = min(box[0] + box[2], crowd[0] + crowd[2]) - max(box[0], crowd[0])
    h = min(box[1] + box[3], crowd[1] + crowd[3]) - max(box[1], crowd[1])
    return max(0.0, w) * max(0.0, h) / max(1e-9, box[2] * box[3])


def size_of(area: float) -> str:
    return "small" if area < 32 * 32 else "medium" if area < 96 * 96 else "large"


def detection(data: Path, results: dict) -> None:
    files = sorted((data / "out").glob("det-*.jsonl"))
    if not files:
        return
    truth = json.loads((data / "instances_val2017.json").read_text())
    names = {c["id"]: c["name"] for c in truth["categories"]}
    ids = {name: cid for cid, name in names.items()}
    by_image = defaultdict(list)
    for a in truth["annotations"]:
        by_image[a["image_id"]].append(a)
    print("\n## Object detection, COCO val2017 sample, measured on the phone\n")
    print("| Model | Delegate | AP | AP50 | Precision | Recall | Recall, large | Mean confidence (TP / FP) | Latency ms: mean / median / p95 | FPS |")
    print("|---|---|---|---|---|---|---|---|---|---|")
    for file in files:
        lines = [json.loads(line) for line in file.read_text(encoding="utf-8").splitlines()]
        head, rows = lines[0], lines[1:]
        counts, by_size, tp_scores, fp_scores, coco_rows, unknown = [], defaultdict(lambda: [0, 0]), [], [], [], set()
        for row in rows:
            image = int(row["image"])
            real = [g for g in by_image[image] if not g["iscrowd"]]
            crowds = [g for g in by_image[image] if g["iscrowd"]]
            used, tp, fp = set(), 0, 0
            for det in sorted(row["dets"], key=lambda d: -d["score"]):
                if det["label"] not in ids:
                    unknown.add(det["label"])
                    continue
                coco_rows.append({"image_id": image, "category_id": ids[det["label"]], "bbox": det["box"], "score": det["score"]})
                if not det["kept"]:
                    continue
                best, best_iou = None, IOU
                for i, g in enumerate(real):
                    if i not in used and names[g["category_id"]] == det["label"] and (v := iou(det["box"], g["bbox"])) >= best_iou:
                        best, best_iou = i, v
                if best is not None:
                    used.add(best)
                    tp += 1
                    tp_scores.append(det["score"])
                elif not any(names[c["category_id"]] == det["label"] and covered(det["box"], c["bbox"]) >= 0.5 for c in crowds):
                    fp += 1
                    fp_scores.append(det["score"])
            for i, g in enumerate(real):
                by_size[size_of(g["area"])][0] += i in used
                by_size[size_of(g["area"])][1] += 1
            counts.append((tp, fp, len(real) - tp))
        counts = np.array(counts)
        tp, fp, fn = counts.sum(axis=0)
        precision, recall = (lambda s: s[0] / max(1, s[0] + s[1])), (lambda s: s[0] / max(1, s[0] + s[2]))
        ap = coco_ap(truth, coco_rows, [int(row["image"]) for row in rows])
        times = [row["ms"] for row in rows[WARM_UP:]]
        entry = {
            "model": head["model"], "delegate": head["ran"], "device": head["device"], "soc": head["soc"], "images": len(rows),
            "ap": ap[0], "ap50": ap[1], "tp": int(tp), "fp": int(fp), "fn": int(fn),
            "precision": precision((tp, fp, fn)), "precision_ci": bootstrap(counts, precision),
            "recall": recall((tp, fp, fn)), "recall_ci": bootstrap(counts, recall),
            "recall_by_size": {size: hit / max(1, total) for size, (hit, total) in by_size.items()},
            "confidence_tp": statistics.fmean(tp_scores) if tp_scores else None,
            "confidence_fp": statistics.fmean(fp_scores) if fp_scores else None,
            "ms_mean": statistics.fmean(times), "ms_median": statistics.median(times), "ms_p95": float(np.percentile(times, 95)),
            "labels_not_in_coco": sorted(unknown),
        }
        results.setdefault("detection", []).append(entry)
        confidence = " / ".join("-" if v is None else f"{v:.2f}" for v in (entry["confidence_tp"], entry["confidence_fp"]))
        print(f"| {head['model']} | {head['ran']} | {100 * ap[0]:.1f} | {100 * ap[1]:.1f} | {pct(entry['precision'], entry['precision_ci'])} "
              f"| {pct(entry['recall'], entry['recall_ci'])} | {pct(entry['recall_by_size'].get('large', 0))} | {confidence} "
              f"| {entry['ms_mean']:.0f} / {entry['ms_median']:.0f} / {entry['ms_p95']:.0f} | {1000 / entry['ms_mean']:.1f} |")
    first = results["detection"][0]
    print(f"\n{first['device']} ({first['soc']}), {first['images']} images. AP is COCO AP@[.5:.95] at the deployed settings "
          f"(detector score 0.3, 10 results). Precision and recall are at the app's defaults (0.7, people 0.5), IoU {IOU}, "
          f"intervals by bootstrap over images. Latency leaves out the first {WARM_UP} frames.")


def coco_ap(truth: dict, rows: list, images: list) -> tuple[float, float]:
    if not rows:
        return (0.0, 0.0)
    from pycocotools.coco import COCO
    from pycocotools.cocoeval import COCOeval
    with contextlib.redirect_stdout(io.StringIO()):
        gt = COCO()
        gt.dataset = truth
        gt.createIndex()
        run = COCOeval(gt, gt.loadRes(rows), "bbox")
        run.params.imgIds = images
        run.evaluate()
        run.accumulate()
        run.summarize()
    return (float(run.stats[0]), float(run.stats[1]))


def best_match(vector: np.ndarray, known: dict, threshold=FACE_THRESHOLD, margin=FACE_MARGIN, top=FACE_TOP) -> str | None:
    """FaceMatcher.bestMatch: mean of the three best samples, a threshold, and a margin over the runner-up."""
    ranked = sorted(((float(np.sort(samples @ vector)[::-1][:top].mean()), who) for who, samples in known.items()), reverse=True)
    if not ranked or ranked[0][0] < threshold:
        return None
    if len(ranked) > 1 and ranked[0][0] - ranked[1][0] < margin:
        return None
    return ranked[0][1]


def faces(data: Path, results: dict) -> None:
    meta = data / "out" / "faces.jsonl"
    if not meta.exists():
        return
    lines = [json.loads(line) for line in meta.read_text(encoding="utf-8").splitlines()]
    head, rows = lines[0], lines[1:]
    embedded = [row for row in rows if "row" in row]
    vectors = np.fromfile(data / "out" / "faces.f32", dtype="<f4").reshape(len(embedded), -1)
    of = {(row["person"], row["file"]): vectors[row["row"]] for row in embedded}
    entry = {
        "device": head["device"], "images": len(rows),
        "face_found": sum(row["faces"] > 0 for row in rows) / len(rows),
        "usable": sum(bool(row.get("usable")) for row in rows) / len(rows),
        "embedded": len(embedded) / len(rows),
        "find_ms_median": statistics.median(row["findMs"] for row in rows[WARM_UP:]),
        "embed_ms_median": statistics.median(row["embedMs"] for row in embedded[WARM_UP:]),
    }

    # LFW verification, the standard 6000 pairs. A face the app would skip counts as "not the same person".
    pairs, text = [], (data / "pairs.txt").read_text().splitlines()[1:]
    for index, line in enumerate(text):
        p = line.split()
        a, b = ((p[0], int(p[1])), (p[0], int(p[2]))) if len(p) == 3 else ((p[0], int(p[1])), (p[2], int(p[3])))
        va, vb = (of.get((who, f"{who}_{n:04d}.jpg")) for who, n in (a, b))
        pairs.append((index // 600, len(p) == 3, float(va @ vb) if va is not None and vb is not None else -1.0))
    same = [score for _, is_same, score in pairs if is_same]
    other = [score for _, is_same, score in pairs if not is_same]
    accepted, false = sum(s >= FACE_THRESHOLD for s in same), sum(s >= FACE_THRESHOLD for s in other)
    folds = []
    for fold in range(10):
        train = [(s, y) for f, y, s in pairs if f != fold]
        best = max(np.arange(0, 1, 0.005), key=lambda t: sum((s >= t) == y for s, y in train))
        folds.append(statistics.fmean((s >= best) == y for f, y, s in pairs if f == fold))
    entry["verification"] = {
        "accuracy_at_0.5": (accepted + len(other) - false) / len(pairs),
        "true_accept": accepted / len(same), "true_accept_ci": wilson(accepted, len(same)),
        "false_accept": false / len(other), "false_accept_ci": wilson(false, len(other)),
        "ten_fold_accuracy": statistics.fmean(folds), "ten_fold_sd": statistics.stdev(folds),
    }

    # Open-set identification the way the app is used: a few saved people, strangers walking past.
    by_person = defaultdict(list)
    for row in embedded:
        by_person[row["person"]].append(vectors[row["row"]])
    eligible = sorted(who for who, v in by_person.items() if len(v) >= ENROLLED + 3)
    everyone, rates = sorted(by_person), {"named_right": (0, 2), "named_wrong": (1, 2), "stranger_named": (3, 4)}
    entry["identification"] = {"people_eligible": len(eligible), "by_saved_people": {}}
    for gallery in GALLERIES:
        rng, counts = random.Random(SEED), []
        for _ in range(TRIALS):
            saved = rng.sample(eligible, gallery)
            known, genuine = {}, []
            for who in saved:
                order = rng.sample(range(len(by_person[who])), len(by_person[who]))
                known[who] = np.stack([by_person[who][i] for i in order[:ENROLLED]])
                genuine += [(who, by_person[who][i]) for i in order[ENROLLED:ENROLLED + PROBES]]
            strangers = []
            while len(strangers) < len(genuine):
                who = rng.choice(everyone)
                if who not in known:
                    strangers.append(rng.choice(by_person[who]))
            said = [best_match(vector, known) for _, vector in genuine]
            right = sum(name == who for name, (who, _) in zip(said, genuine))
            wrong = sum(name is not None and name != who for name, (who, _) in zip(said, genuine))
            counts.append((right, wrong, len(genuine), sum(best_match(v, known) is not None for v in strangers), len(strangers)))
        counts = np.array(counts)
        total = counts.sum(axis=0)
        row = {"genuine_probes": int(total[2]), "stranger_probes": int(total[4])}
        for key, (k, n) in rates.items():
            row[key] = total[k] / total[n]
            row[key + "_ci"] = bootstrap(counts, lambda s, k=k, n=n: s[k] / s[n])
        entry["identification"]["by_saved_people"][gallery] = row
    results["faces"] = entry

    v = entry["verification"]
    print(f"\n## Face recognition, LFW ({entry['images']} images), measured on the phone\n")
    print("| Measure | Value |\n|---|---|")
    print(f"| Face found / passes the quality gate / embedded | {pct(entry['face_found'])} / {pct(entry['usable'])} / {pct(entry['embedded'])} |")
    print(f"| Verification, 6000 pairs, accuracy at the app's threshold {FACE_THRESHOLD} | {pct(v['accuracy_at_0.5'])} |")
    print(f"| True accept / false accept at {FACE_THRESHOLD} | {pct(v['true_accept'], v['true_accept_ci'])} / {pct(v['false_accept'], v['false_accept_ci'])} |")
    print(f"| Verification, standard 10-fold protocol | {100 * v['ten_fold_accuracy']:.2f}% ± {100 * v['ten_fold_sd']:.2f} |")
    print(f"| Time per face: find + embed, median | {entry['find_ms_median']:.0f} + {entry['embed_ms_median']:.0f} ms |")
    print(f"\nOpen-set identification with the app's rule ({ENROLLED} samples per saved person, {TRIALS} random trials per row):\n")
    print("| Saved people | Saved person named correctly | Saved person given another's name | Stranger given a saved name |\n|---|---|---|---|")
    for gallery, row in entry["identification"]["by_saved_people"].items():
        print(f"| {gallery} | {pct(row['named_right'], row['named_right_ci'])} | {pct(row['named_wrong'], row['named_wrong_ci'])} | {pct(row['stranger_named'], row['stranger_named_ci'])} |")

def identify(by_person: dict, gallery: int, enrolled: int, **rule) -> dict:
    """The open-set protocol of faces() for one gallery size, enrolment size and matching rule."""
    eligible = sorted(who for who, v in by_person.items() if len(v) >= enrolled + 3)
    everyone, rng, counts = sorted(by_person), random.Random(SEED), []
    for _ in range(TRIALS):
        known, genuine = {}, []
        for who in rng.sample(eligible, gallery):
            order = rng.sample(range(len(by_person[who])), len(by_person[who]))
            known[who] = np.stack([by_person[who][i] for i in order[:enrolled]])
            genuine += [(who, by_person[who][i]) for i in order[enrolled:enrolled + PROBES]]
        strangers = []
        while len(strangers) < len(genuine):
            who = rng.choice(everyone)
            if who not in known:
                strangers.append(rng.choice(by_person[who]))
        said = [best_match(vector, known, **rule) for _, vector in genuine]
        counts.append((sum(name == who for name, (who, _) in zip(said, genuine)),
                       sum(name is not None and name != who for name, (who, _) in zip(said, genuine)), len(genuine),
                       sum(best_match(v, known, **rule) is not None for v in strangers), len(strangers)))
    counts = np.array(counts)
    total = counts.sum(axis=0)
    row = {"people_eligible": len(eligible)}
    for key, (k, n) in {"named_right": (0, 2), "named_wrong": (1, 2), "stranger_named": (3, 4)}.items():
        row[key] = total[k] / total[n]
        row[key + "_ci"] = bootstrap(counts, lambda s, k=k, n=n: s[k] / s[n])
    return row


def extras(data: Path, results: dict) -> None:
    """Threshold sweeps, per-class results and the ablation of the face rule: all recomputed from the phone's raw output."""
    out = results.setdefault("extras", {})
    default = data / "out" / "det-efficientdet-lite2-GPU.jsonl"
    if default.exists():
        truth = json.loads((data / "instances_val2017.json").read_text())
        names = {c["id"]: c["name"] for c in truth["categories"]}
        by_image = defaultdict(list)
        for a in truth["annotations"]:
            by_image[a["image_id"]].append(a)
        rows = [json.loads(line) for line in default.read_text(encoding="utf-8").splitlines()][1:]

        def score(threshold: float):
            per_class, counts = defaultdict(lambda: [0, 0, 0]), []
            for row in rows:
                real = [g for g in by_image[int(row["image"])] if not g["iscrowd"]]
                crowds = [g for g in by_image[int(row["image"])] if g["iscrowd"]]
                used, tp, fp = set(), 0, 0
                for det in sorted(row["dets"], key=lambda d: -d["score"]):
                    needs = max(0.3, round(threshold - 0.2, 1)) if det["label"] == "person" else threshold   # DetectionFilter.minScoreFor
                    if det["score"] < needs:
                        continue
                    best, best_iou = None, IOU
                    for i, g in enumerate(real):
                        if i not in used and names[g["category_id"]] == det["label"] and (v := iou(det["box"], g["bbox"])) >= best_iou:
                            best, best_iou = i, v
                    if best is not None:
                        used.add(best)
                        tp += 1
                        per_class[det["label"]][0] += 1
                    elif not any(names[c["category_id"]] == det["label"] and covered(det["box"], c["bbox"]) >= 0.5 for c in crowds):
                        fp += 1
                        per_class[det["label"]][1] += 1
                for i, g in enumerate(real):
                    per_class[names[g["category_id"]]][2] += i not in used
                counts.append((tp, fp, len(real) - tp))
            return np.array(counts), per_class

        out["detection_threshold_sweep"] = []
        for threshold in (0.3, 0.4, 0.5, 0.6, 0.7):   # the range of the app's setting
            counts, per_class = score(threshold)
            tp, fp, fn = counts.sum(axis=0)
            out["detection_threshold_sweep"].append({"threshold": threshold, "tp": int(tp), "fp": int(fp), "fn": int(fn),
                                                     "precision": tp / max(1, tp + fp), "recall": tp / max(1, tp + fn)})
            if threshold == 0.7:
                top = sorted(per_class.items(), key=lambda item: -(item[1][0] + item[1][2]))[:15]
                out["detection_per_class"] = [{"label": label, "objects": tp + fn, "precision": tp / (tp + fp) if tp + fp else None, "recall": tp / max(1, tp + fn),
                                               "tp": tp, "fp": fp, "fn": fn} for label, (tp, fp, fn) in top]
        print("\n## Detection, EfficientDet-Lite2 GPU: announcement threshold\n\n| Threshold (people 0.2 lower) | Precision | Recall |\n|---|---|---|")
        for row in out["detection_threshold_sweep"]:
            print(f"| {row['threshold']} | {pct(row['precision'])} | {pct(row['recall'])} |")
        print("\n| Class (15 most frequent) | Objects | Precision | Recall |\n|---|---|---|---|")
        for row in out["detection_per_class"]:
            print(f"| {row['label']} | {row['objects']} | {'none announced' if row['precision'] is None else pct(row['precision'])} | {pct(row['recall'])} |")

    meta = data / "out" / "faces.jsonl"
    if meta.exists():
        embedded = [row for row in (json.loads(line) for line in meta.read_text(encoding="utf-8").splitlines()[1:]) if "row" in row]
        vectors = np.fromfile(data / "out" / "faces.f32", dtype="<f4").reshape(len(embedded), -1)
        by_person = defaultdict(list)
        for row in embedded:
            by_person[row["person"]].append(vectors[row["row"]])
        rules = [
            ("Best single sample, cosine ≥ 0.3 (initial rule)", dict(threshold=0.3, margin=0.0, top=1)),
            ("Best single sample, cosine ≥ 0.5", dict(threshold=0.5, margin=0.0, top=1)),
            ("Mean of 3 best samples ≥ 0.5", dict(threshold=0.5, margin=0.0, top=3)),
            ("Mean of 3 best ≥ 0.5 and margin ≥ 0.08 (deployed)", dict(threshold=0.5, margin=0.08, top=3)),
        ]
        of = {(row["person"], row["file"]): vectors[row["row"]] for row in embedded}
        edges = [round(-0.3 + 0.025 * i, 3) for i in range(53)]          # cosine from -0.3 to 1.0
        scores = {True: [], False: []}
        for line in (data / "pairs.txt").read_text().splitlines()[1:]:
            w = line.split()
            a, b = ((w[0], int(w[1])), (w[0], int(w[2]))) if len(w) == 3 else ((w[0], int(w[1])), (w[2], int(w[3])))
            va, vb = (of.get((who, f"{who}_{n:04d}.jpg")) for who, n in (a, b))
            if va is not None and vb is not None:
                scores[len(w) == 3].append(float(va @ vb))
        out["face_pair_scores"] = {"edges": edges, "same": np.histogram(scores[True], bins=edges)[0].tolist(),
                                   "different": np.histogram(scores[False], bins=edges)[0].tolist(),
                                   "same_median": float(np.median(scores[True])), "different_median": float(np.median(scores[False])),
                                   "pairs_without_embedding": 6000 - len(scores[True]) - len(scores[False])}
        out["face_rule_ablation"] = [{"rule": name, **identify(by_person, 10, ENROLLED, **rule)} for name, rule in rules]
        out["face_enrolment"] = [{"enrolled": n, **identify(by_person, 10, n)} for n in (1, 3, 5, 10)]
        out["face_threshold"] = [{"threshold": t, **identify(by_person, 10, ENROLLED, threshold=t)} for t in (0.4, 0.5, 0.6, 0.7)]
        for title, key, first in (("Face matching rule, 10 saved people, 5 samples each", "face_rule_ablation", "rule"),
                                  ("Samples enrolled per person, 10 saved people, deployed rule", "face_enrolment", "enrolled"),
                                  ("Threshold of the deployed rule, 10 saved people, 5 samples each", "face_threshold", "threshold")):
            print(f"\n## {title}\n\n| {first.capitalize()} | Named correctly | Given another's name | Stranger given a saved name |\n|---|---|---|---|")
            for row in out[key]:
                print(f"| {row[first]} | {pct(row['named_right'], row['named_right_ci'])} | {pct(row['named_wrong'], row['named_wrong_ci'])} | {pct(row['stranger_named'], row['stranger_named_ci'])} |")


def field(sheet: Path, results: dict) -> None:
    """Rows: app, task, trial, outcome (1 or 0), seconds (optional), note. Lines without an outcome are skipped."""
    if not sheet.exists():
        return
    cells = defaultdict(lambda: {"ok": 0, "n": 0, "seconds": []})
    with open(sheet, newline="", encoding="utf-8") as file:
        for row in csv.DictReader(file):
            if row["outcome"].strip() not in ("0", "1"):
                continue
            cell = cells[(row["task"].strip(), row["app"].strip())]
            cell["ok"] += int(row["outcome"])
            cell["n"] += 1
            if row.get("seconds", "").strip():
                cell["seconds"].append(float(row["seconds"]))
    if not cells:
        return
    print("\n## Field trials, same phone, same scenes\n")
    print("| Task | App | Success | Median time s | p vs Nungil (Fisher) |\n|---|---|---|---|---|")
    for (task, app), cell in sorted(cells.items()):
        ours = cells.get((task, "Nungil"))
        p = "" if app == "Nungil" or not ours else f"{fisher(ours['ok'], ours['n'] - ours['ok'], cell['ok'], cell['n'] - cell['ok']):.3f}"
        seconds = f"{statistics.median(cell['seconds']):.1f}" if cell["seconds"] else ""
        print(f"| {task} | {app} | {cell['ok']}/{cell['n']} = {pct(cell['ok'] / cell['n'], wilson(cell['ok'], cell['n']))} | {seconds} | {p} |")
        results.setdefault("field", []).append({"task": task, "app": app, "ok": cell["ok"], "n": cell["n"], "seconds": cell["seconds"]})


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    data = Path(sys.argv[1] if len(sys.argv) > 1 else "D:/nungil-bench")
    results: dict = {}
    detection(data, results)
    faces(data, results)
    extras(data, results)
    field(Path(__file__).with_name("field-trials.csv"), results)
    (data / "results.json").write_text(json.dumps(results, indent=1, default=float))
    if not results:
        print("Nothing to analyse yet: pull the phone's bench/out folder first (see prepare.py).")
