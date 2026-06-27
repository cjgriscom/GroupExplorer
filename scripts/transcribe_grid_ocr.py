#!/usr/bin/env python3
"""Transcribe numbered grid nodes from a screenshot into CSV."""

from __future__ import annotations

import argparse
import re
from collections import defaultdict
from pathlib import Path

import cv2
import easyocr
import numpy as np
from PIL import Image
import tesserocr


def detect_nodes(img: np.ndarray, *, param2: int = 18) -> list[tuple[int, int, int]]:
    h, w = img.shape[:2]
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    blur = cv2.medianBlur(gray, 5)
    circles = cv2.HoughCircles(
        blur,
        cv2.HOUGH_GRADIENT,
        dp=1.2,
        minDist=30,
        param1=50,
        param2=param2,
        minRadius=10,
        maxRadius=22,
    )
    if circles is None:
        return []
    return [
        (int(round(x)), int(round(y)), int(round(r)))
        for x, y, r in circles[0]
        if 20 < y < h - 20 and 20 < x < w - 20
    ]


def estimate_spacing(nodes: list[tuple[int, int, int]]) -> float:
    arr = np.array([(x, y) for x, y, _ in nodes], dtype=float)
    dists: list[float] = []
    for point in arr:
        delta = arr - point
        distance = np.sqrt((delta**2).sum(axis=1))
        distance = distance[distance > 5]
        if len(distance):
            dists.append(float(distance.min()))
    return float(np.median(dists)) if dists else 60.0


def _snap_hits(
    nodes: list[tuple[int, int, int]],
    x0: float,
    y0: float,
    spacing: float,
    ncols: int | None = None,
    nrows: int | None = None,
    tol: float = 0.35,
) -> int:
    hits = 0
    for x, y, _ in nodes:
        ci = round((x - x0) / spacing)
        ri = round((y - y0) / spacing)
        if ncols is not None and not (0 <= ci < ncols):
            continue
        if nrows is not None and not (0 <= ri < nrows):
            continue
        gx = x0 + ci * spacing
        gy = y0 + ri * spacing
        if abs(x - gx) <= spacing * tol and abs(y - gy) <= spacing * tol:
            hits += 1
    return hits


def calibrate_grid(
    nodes: list[tuple[int, int, int]],
    spacing: float,
    *,
    ncols: int | None = None,
    nrows: int | None = None,
) -> tuple[float, float, float, int, int]:
    xs = np.array([x for x, _, _ in nodes], dtype=float)
    ys = np.array([y for _, y, _ in nodes], dtype=float)

    if ncols is not None and nrows is not None:
        best: tuple[int, float, float, float] | None = None
        for s in np.linspace(max(58.0, spacing - 8.0), spacing + 12.0, 41):
            for x0 in np.linspace(xs.min() - 2 * s, xs.min() + s, 16):
                for y0 in np.linspace(ys.min() - 2 * s, ys.min() + s, 16):
                    hits = _snap_hits(nodes, x0, y0, s, ncols, nrows)
                    if best is None or hits > best[0]:
                        best = (hits, float(s), float(x0), float(y0))
        assert best is not None
        _, spacing, x0, y0 = best
        return x0, y0, spacing, ncols, nrows

    best = None
    for x0 in np.linspace(xs.min() - spacing, xs.min() + spacing, 20):
        for y0 in np.linspace(ys.min() - spacing, ys.min() + spacing, 20):
            hits = _snap_hits(nodes, x0, y0, spacing)
            if best is None or hits > best[0]:
                best = (hits, float(x0), float(y0))

    assert best is not None
    _, x0, y0 = best
    ncols = int(round((xs.max() - x0) / spacing)) + 1
    nrows = int(round((ys.max() - y0) / spacing)) + 1
    return x0, y0, spacing, ncols, nrows


def snap_nodes(
    nodes: list[tuple[int, int, int]],
    x0: float,
    y0: float,
    spacing: float,
    ncols: int,
    nrows: int,
) -> list[tuple[int, int, int, int, int]]:
    snapped: dict[tuple[int, int], tuple[int, int, int, int, int]] = {}
    for x, y, r in nodes:
        ci = int(round((x - x0) / spacing))
        ri = int(round((y - y0) / spacing))
        if not (0 <= ci < ncols and 0 <= ri < nrows):
            continue
        gx = x0 + ci * spacing
        gy = y0 + ri * spacing
        if abs(x - gx) > spacing * 0.35 or abs(y - gy) > spacing * 0.35:
            continue
        key = (ri, ci)
        prev = snapped.get(key)
        if prev is None or r > prev[4]:
            snapped[key] = (ri, ci, x, y, r)
    return list(snapped.values())


def preprocess_crop(crop: np.ndarray) -> np.ndarray:
    ch, cw = crop.shape[:2]
    cx, cy = cw // 2, ch // 2
    hsv = cv2.cvtColor(crop, cv2.COLOR_BGR2HSV)
    _, s, v = cv2.split(hsv)
    mask = np.zeros((ch, cw), np.uint8)
    cv2.circle(mask, (cx, cy), min(ch, cw) // 2 - 2, 255, -1)
    text = ((v < 125) | ((s < 75) & (v < 155))).astype(np.uint8) * 255
    text = cv2.bitwise_and(text, mask)
    text = cv2.morphologyEx(text, cv2.MORPH_OPEN, np.ones((2, 2), np.uint8))
    out = np.full((ch, cw), 255, np.uint8)
    out[text > 0] = 0
    return cv2.cvtColor(out, cv2.COLOR_GRAY2BGR)


def parse_number(text: str) -> tuple[int, float] | None:
    digits = re.sub(r"\D", "", text)
    if not digits:
        return None
    value = int(digits)
    if 1 <= value <= 176:
        return value, 1.0
    if 10 <= value <= 1760 and value % 10 == 0:
        value //= 10
        if 1 <= value <= 176:
            return value, 0.5
    return None


def ocr_easy(reader: easyocr.Reader, crop: np.ndarray) -> tuple[int, float] | None:
    best: tuple[int, float] | None = None
    for source in (crop, preprocess_crop(crop)):
        big = cv2.resize(source, None, fx=4, fy=4, interpolation=cv2.INTER_CUBIC)
        rgb = cv2.cvtColor(big, cv2.COLOR_BGR2RGB)
        for _, txt, conf in reader.readtext(rgb, detail=1, paragraph=False):
            parsed = parse_number(txt)
            if parsed is None:
                continue
            value, bonus = parsed
            score = float(conf) * bonus
            if best is None or score > best[1]:
                best = (value, score)
    return best


def ocr_tess(crop: np.ndarray, api: tesserocr.PyTessBaseAPI) -> tuple[int, float] | None:
    best: tuple[int, float] | None = None
    for source in (crop, preprocess_crop(crop)):
        rgb = cv2.cvtColor(source, cv2.COLOR_BGR2RGB)
        pil = Image.fromarray(rgb).resize((400, 400), Image.Resampling.LANCZOS)
        api.SetImage(pil)
        txt = api.GetUTF8Text().strip()
        conf = max(float(api.MeanTextConf()), 0.0) / 100.0
        match = re.search(r"\d{1,3}", txt)
        if not match:
            continue
        parsed = parse_number(match.group())
        if parsed is None:
            continue
        value, bonus = parsed
        score = conf * bonus
        if best is None or score > best[1]:
            best = (value, score)
    return best


def ocr_node(
    img: np.ndarray,
    x: int,
    y: int,
    reader: easyocr.Reader,
    api: tesserocr.PyTessBaseAPI,
    pad: int = 38,
) -> tuple[int, float] | None:
    h, w = img.shape[:2]
    crop = img[max(0, y - pad) : min(h, y + pad + 1), max(0, x - pad) : min(w, x + pad + 1)]
    easy = ocr_easy(reader, crop)
    tess = ocr_tess(crop, api)
    if easy and tess:
        return easy if easy[1] >= tess[1] else tess
    return easy or tess


def detect_full_image(
    img: np.ndarray,
    reader: easyocr.Reader,
    scale: float = 1.5,
) -> list[tuple[int, float, float, float]]:
    big = cv2.resize(img, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
    rgb = cv2.cvtColor(big, cv2.COLOR_BGR2RGB)
    detections: list[tuple[int, float, float, float]] = []
    for box, txt, conf in reader.readtext(rgb, detail=1, paragraph=False):
        parsed = parse_number(txt)
        if parsed is None:
            continue
        value, bonus = parsed
        score = float(conf) * bonus
        if score < 0.15:
            continue
        cx = sum(p[0] for p in box) / 4 / scale
        cy = sum(p[1] for p in box) / 4 / scale
        detections.append((value, score, cx, cy))
    return detections


def cell_center(x0: float, y0: float, spacing: float, ri: int, ci: int) -> tuple[float, float]:
    return x0 + ci * spacing, y0 + ri * spacing


def assign_readings(
    cells: list[tuple[int, int, int, int, int]],
    cell_scores: dict[tuple[int, int], dict[int, float]],
) -> dict[tuple[int, int], int]:
    # For each number, keep the cell with the highest score.
    by_num: dict[int, tuple[tuple[int, int], float]] = {}
    for (ri, ci), scores in cell_scores.items():
        for num, score in scores.items():
            prev = by_num.get(num)
            if prev is None or score > prev[1]:
                by_num[num] = ((ri, ci), score)

    assigned: dict[tuple[int, int], int] = {}
    used_cells: set[tuple[int, int]] = set()
    for num, ((ri, ci), _) in sorted(by_num.items(), key=lambda item: item[1][1], reverse=True):
        if (ri, ci) in used_cells:
            continue
        assigned[(ri, ci)] = num
        used_cells.add((ri, ci))

    # Fill any still-empty snapped cells from their top local candidate.
    for ri, ci, _, _, _ in cells:
        if (ri, ci) in assigned:
            continue
        scores = cell_scores.get((ri, ci), {})
        for num, _ in sorted(scores.items(), key=lambda item: item[1], reverse=True):
            if num not in assigned.values():
                assigned[(ri, ci)] = num
                break
    return assigned


def build_grid(
    img: np.ndarray,
    reader: easyocr.Reader,
    api: tesserocr.PyTessBaseAPI,
) -> tuple[list[list[str]], dict[str, object]]:
    nodes = detect_nodes(img)
    spacing = estimate_spacing(nodes)
    x0, y0, spacing, ncols, nrows = calibrate_grid(nodes, spacing)
    cells = snap_nodes(nodes, x0, y0, spacing, ncols, nrows)

    cell_scores: dict[tuple[int, int], dict[int, float]] = defaultdict(dict)

    for ri, ci, x, y, _ in cells:
        result = ocr_node(img, x, y, reader, api)
        if result:
            value, score = result
            cell_scores[(ri, ci)][value] = max(cell_scores[(ri, ci)].get(value, 0.0), score)

    for value, score, cx, cy in detect_full_image(img, reader):
        ci = int(round((cx - x0) / spacing))
        ri = int(round((cy - y0) / spacing))
        if 0 <= ci < ncols and 0 <= ri < nrows:
            cell_scores[(ri, ci)][value] = max(cell_scores[(ri, ci)].get(value, 0.0), score)

    assigned = assign_readings(cells, cell_scores)
    grid = [["x"] * ncols for _ in range(nrows)]
    for ri, ci, _, _, _ in cells:
        if (ri, ci) in assigned:
            grid[ri][ci] = str(assigned[(ri, ci)])

    meta = {
        "nodes_detected": len(nodes),
        "cells_snapped": len(cells),
        "assigned": len(assigned),
        "x0": round(x0, 1),
        "y0": round(y0, 1),
        "spacing": round(spacing, 1),
        "ncols": ncols,
        "nrows": nrows,
    }
    return grid, meta


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image", type=Path)
    parser.add_argument("-o", "--output", type=Path, required=True)
    args = parser.parse_args()

    img = cv2.imread(str(args.image))
    if img is None:
        raise SystemExit(f"Could not read image: {args.image}")

    reader = easyocr.Reader(["en"], gpu=False, verbose=False)
    with tesserocr.PyTessBaseAPI(psm=tesserocr.PSM.SINGLE_WORD, oem=tesserocr.OEM.LSTM_ONLY) as api:
        api.SetVariable("tessedit_char_whitelist", "0123456789")
        api.SetVariable("user_defined_dpi", "300")
        grid, meta = build_grid(img, reader, api)

    csv = "\n".join(",".join(row) for row in grid) + "\n"
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(csv)

    print(f"Wrote {args.output}")
    for key, value in meta.items():
        print(f"  {key}: {value}")
    values = {int(v) for row in grid for v in row if v != "x"}
    print(f"  unique numbers: {len(values)}")


if __name__ == "__main__":
    main()
