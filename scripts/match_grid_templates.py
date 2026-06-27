#!/usr/bin/env python3
"""Match grid node numbers using cv2.matchTemplate against JavaFX-exported templates."""

from __future__ import annotations

import argparse
import csv
import time
from collections import defaultdict, deque
from dataclasses import dataclass, field
from pathlib import Path

import cv2
import numpy as np

import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from transcribe_grid_ocr import (  # noqa: E402
    calibrate_grid,
    detect_full_image,
    detect_nodes,
    estimate_spacing,
    ocr_node,
    snap_nodes,
)

# Graph grid dimensions (GraphVisualizer square grid for this embedding).
GRID_COLS = 22
GRID_ROWS = 14

MATCH_WEIGHT_FILTERED = 0.49
MATCH_WEIGHT_UNFILTERED = 0.21
MATCH_WEIGHT_OCR = 0.3
ASSIGN_THRESHOLDS = (0.85, 0.75, 0.65, 0.5, 0.0)
REVIEW_SCORE_CUTOFF = 0.5
REVIEW_SCORE_DIFFERENCE = 0.01
REVIEW_CROP_SCALE = 4
REVIEW_WINDOW = "GroupExplorer review node"


def draw_node_detections(
    img: np.ndarray,
    nodes: list[tuple[int, int, int]],
    *,
    circle_color: tuple[int, int, int] = (0, 255, 0),
    center_color: tuple[int, int, int] = (0, 0, 255),
    title: str | None = None,
) -> np.ndarray:
    """Draw detected circles and centers on a copy of the image."""
    out = img.copy()
    for x, y, r in nodes:
        cv2.circle(out, (x, y), r, circle_color, 2, cv2.LINE_AA)
        cv2.circle(out, (x, y), 3, center_color, -1, cv2.LINE_AA)
    if title:
        cv2.rectangle(out, (0, 0), (min(out.shape[1], 520), 36), (255, 255, 255), -1)
        cv2.putText(
            out,
            title,
            (8, 24),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.65,
            (0, 0, 0),
            2,
            cv2.LINE_AA,
        )
    return out


# Grayscale Hough param2: higher = stricter (fewer false positives). 24 → 176 nodes on OCR.png.
HOUGH_PARAM2 = 24

# Radial line removal: sweep at 90% radius, paint center→edge when darker than local avg.
LINE_SWEEP_RADIUS_FRAC = 0.9
LINE_START_RADIUS_FRAC = 0.05
LINE_END_RADIUS_FRAC = 0.9
LINE_PAINT_THICKNESS = 1  # ~0.8px effective width with LINE_AA
LINE_PAINT_OPACITY = 0.8
LINE_DARK_DELTA = 22  # gray levels darker than circle median at sweep point


def _sample_gray(gray: np.ndarray, x: float, y: float) -> float:
    h, w = gray.shape
    if x < 0 or y < 0 or x >= w - 1 or y >= h - 1:
        xi = int(round(x))
        yi = int(round(y))
        if 0 <= xi < w and 0 <= yi < h:
            return float(gray[yi, xi])
        return 255.0
    x0, y0 = int(x), int(y)
    fx, fy = x - x0, y - y0
    v = (
        gray[y0, x0] * (1 - fx) * (1 - fy)
        + gray[y0, x0 + 1] * fx * (1 - fy)
        + gray[y0 + 1, x0] * (1 - fx) * fy
        + gray[y0 + 1, x0 + 1] * fx * fy
    )
    return float(v)


def _circle_median_gray(
    gray: np.ndarray,
    cx: float,
    cy: float,
    radius: float,
    inner_frac: float = 0.08,
    outer_frac: float = 0.88,
) -> float:
    """Median intensity inside an annulus (background estimate, mostly fill color)."""
    h, w = gray.shape
    mask = np.zeros((h, w), np.uint8)
    cv2.circle(mask, (int(round(cx)), int(round(cy))), int(round(outer_frac * radius)), 255, -1)
    cv2.circle(mask, (int(round(cx)), int(round(cy))), int(round(inner_frac * radius)), 0, -1)
    vals = gray[mask > 0]
    if vals.size == 0:
        return float(np.median(gray))
    return float(np.median(vals))


def _merge_angles(angles: list[int], max_gap: int = 2) -> list[int]:
    """Merge nearby degree bins into one line per spoke."""
    if not angles:
        return []
    uniq = sorted(set(angles))
    merged: list[int] = []
    start = prev = uniq[0]
    for angle in uniq[1:]:
        if angle - prev <= max_gap:
            prev = angle
            continue
        merged.append((start + prev) // 2)
        start = prev = angle
    merged.append((start + prev) // 2)
    return merged


def remove_radial_lines_from_crop(
    gray: np.ndarray,
    pane_size: int,
    node_radius: float,
    dark_delta: float = LINE_DARK_DELTA,
) -> np.ndarray:
    """Remove edge lines that radiate from the circle center toward the rim.

    Sweeps 360° sampling at *LINE_SWEEP_RADIUS_FRAC* of the node radius. When the
    sample is darker than the circle median by *dark_delta*, blends a thin spoke
    from *LINE_START_RADIUS_FRAC* to *LINE_END_RADIUS_FRAC* toward the circle's
    median fill color at *LINE_PAINT_OPACITY*.
    """
    h, w = gray.shape
    cx = (w - 1) / 2.0
    cy = (h - 1) / 2.0
    cr = node_radius * min(h, w) / pane_size
    if cr < 4:
        return gray.copy()

    avg = _circle_median_gray(gray, cx, cy, cr)
    fill = avg

    hit_angles: list[int] = []
    sweep_r = LINE_SWEEP_RADIUS_FRAC * cr
    for angle_deg in range(360):
        rad = np.deg2rad(angle_deg)
        sx = cx + sweep_r * np.cos(rad)
        sy = cy + sweep_r * np.sin(rad)
        if _sample_gray(gray, sx, sy) < avg - dark_delta:
            hit_angles.append(angle_deg)

    paint_mask = np.zeros((h, w), np.float32)
    for angle_deg in _merge_angles(hit_angles):
        rad = np.deg2rad(angle_deg)
        x1 = int(round(cx + LINE_START_RADIUS_FRAC * cr * np.cos(rad)))
        y1 = int(round(cy + LINE_START_RADIUS_FRAC * cr * np.sin(rad)))
        x2 = int(round(cx + LINE_END_RADIUS_FRAC * cr * np.cos(rad)))
        y2 = int(round(cy + LINE_END_RADIUS_FRAC * cr * np.sin(rad)))
        stroke = np.zeros((h, w), np.uint8)
        cv2.line(stroke, (x1, y1), (x2, y2), 255, LINE_PAINT_THICKNESS, cv2.LINE_AA)
        paint_mask = np.maximum(paint_mask, stroke.astype(np.float32) / 255.0)

    if paint_mask.max() <= 0:
        return gray.copy()

    alpha = paint_mask * LINE_PAINT_OPACITY
    blended = gray.astype(np.float32) * (1.0 - alpha) + fill * alpha
    return np.clip(blended, 0, 255).astype(np.uint8)


def crop_node_pane_unfiltered(
    gray: np.ndarray,
    x: int,
    y: int,
    r: int,
    pane_size: int,
    node_radius: float,
) -> np.ndarray:
    """Native crop resized to template pane size, without line removal."""
    native = crop_node_native(gray, x, y, r, pane_size, node_radius)
    return cv2.resize(native, (pane_size, pane_size), interpolation=cv2.INTER_CUBIC)


def crop_node_pane_cleaned(
    gray: np.ndarray,
    x: int,
    y: int,
    r: int,
    pane_size: int,
    node_radius: float,
) -> np.ndarray:
    """Native crop with radial line removal, resized to template pane size."""
    native = crop_node_native(gray, x, y, r, pane_size, node_radius)
    cleaned = remove_radial_lines_from_crop(native, pane_size, node_radius)
    return cv2.resize(cleaned, (pane_size, pane_size), interpolation=cv2.INTER_CUBIC)


def crop_node_native(
    img: np.ndarray,
    x: int,
    y: int,
    r: int,
    pane_size: int,
    node_radius: float,
) -> np.ndarray:
    """Crop a native-resolution square pane centered on the node."""
    h, w = img.shape[:2]
    size = int(round(2 * r * pane_size / (2 * node_radius)))
    size = max(3, size | 1)
    half = size // 2
    x0 = x - half
    y0 = y - half
    src_x0 = max(0, x0)
    src_y0 = max(0, y0)
    src_x1 = min(w, x0 + size)
    src_y1 = min(h, y0 + size)
    crop = img[src_y0:src_y1, src_x0:src_x1]
    pl = src_x0 - x0
    pt = src_y0 - y0
    pr = size - crop.shape[1] - pl
    pb = size - crop.shape[0] - pt
    if pl or pt or pr or pb:
        crop = cv2.copyMakeBorder(crop, pt, pb, pl, pr, cv2.BORDER_REPLICATE)
    return crop


def _line_filter_panels(
    gray_orig: np.ndarray,
    x: int,
    y: int,
    r: int,
    pane_size: int,
    node_radius: float,
    scale: int = 4,
) -> tuple[np.ndarray, np.ndarray, np.ndarray, int]:
    """Return (before, after, removed-highlight) BGR tiles at native crop scale."""
    crop_before = crop_node_native(gray_orig, x, y, r, pane_size, node_radius)
    crop_after = remove_radial_lines_from_crop(crop_before, pane_size, node_radius)
    removed = np.abs(crop_before.astype(np.int16) - crop_after.astype(np.int16)) > 8
    removed_px = int(removed.sum())

    size = crop_before.shape[0] * scale

    def upscale(gray: np.ndarray) -> np.ndarray:
        return cv2.resize(gray, (size, size), interpolation=cv2.INTER_NEAREST)

    before = cv2.cvtColor(upscale(boost_contrast(crop_before)), cv2.COLOR_GRAY2BGR)
    after = cv2.cvtColor(upscale(boost_contrast(crop_after)), cv2.COLOR_GRAY2BGR)
    highlight = after.copy()
    highlight[upscale(removed.astype(np.uint8) * 255) > 0] = (0, 0, 255)
    return before, after, highlight, removed_px


def save_line_filter_preview(
    img: np.ndarray,
    output_path: Path,
    pane_size: int,
    node_radius: float,
    *,
    max_samples: int = 24,
    scale: int = 3,
) -> dict[str, object]:
    """Save a montage of boosted / filtered / removed crops for visual inspection."""
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    nodes_raw = detect_nodes(img, param2=HOUGH_PARAM2)
    spacing_guess = estimate_spacing(nodes_raw)
    x0, y0, spacing, ncols, nrows = calibrate_grid(
        nodes_raw, spacing_guess, ncols=GRID_COLS, nrows=GRID_ROWS
    )
    snapped = snap_nodes(nodes_raw, x0, y0, spacing, ncols, nrows)

    tiles: list[tuple[int, int, np.ndarray, int]] = []
    for ri, ci, x, y, r in snapped:
        before, after, highlight, removed_px = _line_filter_panels(
            gray, x, y, r, pane_size, node_radius, scale=scale
        )
        row = np.hstack([before, after, highlight])
        tile_h, tile_w = row.shape[:2]
        label = np.full((18, tile_w, 3), 255, np.uint8)
        cv2.putText(
            label,
            f"({ri},{ci}) removed={removed_px}px",
            (4, 13),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.4,
            (0, 0, 0),
            1,
            cv2.LINE_AA,
        )
        tiles.append((ri, ci, np.vstack([label, row]), removed_px))

    step = max(1, len(tiles) // max_samples)
    samples = tiles[::step][:max_samples]

    display = pane_size * scale
    panel_w = display * 3
    panel_h = display + 18

    def normalize_tile(tile: np.ndarray) -> np.ndarray:
        label_h = 18
        label = tile[:label_h]
        body = tile[label_h:]
        body = cv2.resize(body, (panel_w, display), interpolation=cv2.INTER_NEAREST)
        return np.vstack([label if label.shape[1] == panel_w else cv2.resize(label, (panel_w, label_h)), body])

    samples = [(ri, ci, normalize_tile(tile), px) for ri, ci, tile, px in samples]

    tile_h, tile_w = samples[0][2].shape[:2]
    cols = 4
    rows = int(np.ceil(len(samples) / cols))
    canvas_h = rows * tile_h + 28
    canvas_w = cols * tile_w
    canvas = np.full((canvas_h, canvas_w, 3), 255, np.uint8)
    cv2.putText(
        canvas,
        f"native crop | radial line-fill | removed (red)   "
        f"sweep={LINE_SWEEP_RADIUS_FRAC:.0%}r opacity={LINE_PAINT_OPACITY}",
        (8, 20),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.55,
        (0, 0, 0),
        1,
        cv2.LINE_AA,
    )

    for idx, (_, _, tile, _) in enumerate(samples):
        r = idx // cols
        c = idx % cols
        y0 = 28 + r * tile_h
        x0 = c * tile_w
        canvas[y0 : y0 + tile_h, x0 : x0 + tile_w] = tile

    output_path.parent.mkdir(parents=True, exist_ok=True)
    cv2.imwrite(str(output_path), canvas)

    removed_counts = [px for _, _, _, px in samples]
    return {
        "samples": len(samples),
        "nodes_total": len(snapped),
        "removed_px_mean": round(float(np.mean(removed_counts)), 1) if removed_counts else 0,
        "removed_px_max": max(removed_counts) if removed_counts else 0,
    }


def save_detection_plot(
    img: np.ndarray,
    output_path: Path,
    *,
    param2_strict: int = HOUGH_PARAM2,
    param2_lenient: int = 18,
) -> dict[str, int]:
    """Save a side-by-side plot of circle detections for visual inspection."""
    lenient_nodes = detect_nodes(img, param2=param2_lenient)
    strict_nodes = detect_nodes(img, param2=param2_strict)

    left = draw_node_detections(
        img,
        lenient_nodes,
        circle_color=(0, 180, 0),
        center_color=(0, 120, 255),
        title=f"grayscale Hough param2={param2_lenient} ({len(lenient_nodes)} circles)",
    )
    right = draw_node_detections(
        img,
        strict_nodes,
        circle_color=(0, 140, 255),
        center_color=(0, 0, 220),
        title=f"grayscale Hough param2={param2_strict} ({len(strict_nodes)} circles)",
    )

    canvas = np.hstack([left, right])
    output_path.parent.mkdir(parents=True, exist_ok=True)
    cv2.imwrite(str(output_path), canvas)
    return {
        f"param2_{param2_lenient}": len(lenient_nodes),
        f"param2_{param2_strict}": len(strict_nodes),
    }


def read_manifest(template_dir: Path) -> dict[str, float]:
    manifest = template_dir / "manifest.txt"
    values: dict[str, float] = {
        "pane_size": 42.0,
        "node_radius": 20.0,
    }
    if not manifest.exists():
        return values
    for line in manifest.read_text().splitlines():
        if "=" not in line:
            continue
        key, raw = line.split("=", 1)
        try:
            values[key] = float(raw)
        except ValueError:
            pass
    return values


def load_all_freq_templates(template_dir: Path) -> dict[int, list[np.ndarray]]:
    by_num: dict[int, list[np.ndarray]] = {}
    node_dir = template_dir / "node"
    for freq_dir in sorted(node_dir.glob("f*")):
        for path in sorted(freq_dir.glob("*.png")):
            num = int(path.stem)
            img = cv2.imread(str(path), cv2.IMREAD_UNCHANGED)
            if img is None:
                continue
            by_num.setdefault(num, []).append(img)
    return by_num


def to_gray(img: np.ndarray) -> np.ndarray:
    if img.ndim == 2:
        return img
    if img.shape[2] == 4:
        bgr = img[:, :, :3]
        alpha = img[:, :, 3]
        gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
        bg = np.full_like(gray, 240)
        a = alpha.astype(np.float32) / 255.0
        return (gray.astype(np.float32) * a + bg * (1.0 - a)).astype(np.uint8)
    return cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)


def crop_node_pane(
    img: np.ndarray,
    x: int,
    y: int,
    r: int,
    pane_size: int,
    node_radius: float,
) -> np.ndarray:
    """Crop a square pane with radial line removal, scaled to template size."""
    return crop_node_pane_cleaned(img, x, y, r, pane_size, node_radius)


def circle_mask(shape: tuple[int, int], pane_size: int, node_radius: float) -> np.ndarray:
    h, w = shape
    cx, cy = w // 2, h // 2
    r = max(1, int(round(node_radius * min(h, w) / pane_size)))
    mask = np.zeros((h, w), np.uint8)
    cv2.circle(mask, (cx, cy), r, 1, -1)
    return mask


def prepare_circle_templates(
    templates_by_num: dict[int, list[np.ndarray]],
    pane_size: int,
    node_radius: float,
    upscale: int = 2,
) -> tuple[dict[int, list[np.ndarray]], np.ndarray]:
    """Precompute contrast-boosted panes at fixed template size (2x upscaled)."""
    up = pane_size * upscale
    circle = circle_mask((up, up), pane_size, node_radius)
    result: dict[int, list[np.ndarray]] = {}
    for num, variants in templates_by_num.items():
        entries: list[np.ndarray] = []
        for template in variants:
            gray = boost_contrast(to_gray(template))
            if gray.shape[:2] != (pane_size, pane_size):
                gray = cv2.resize(gray, (pane_size, pane_size), interpolation=cv2.INTER_AREA)
            if upscale > 1:
                gray = cv2.resize(gray, (up, up), interpolation=cv2.INTER_CUBIC)
            entries.append(gray)
        if entries:
            result[num] = entries
    return result, circle


def boost_contrast(gray: np.ndarray) -> np.ndarray:
    """Aggressively boost contrast to reduce antialiasing fringe.

    Maps gray values through a steep sigmoid-like curve so that
    near-black becomes black and near-white becomes white.
    """
    normalized = gray.astype(np.float32) / 255.0
    # Steep sigmoid centered at 0.5
    boosted = 1.0 / (1.0 + np.exp(-12.0 * (normalized - 0.5)))
    return (boosted * 255).astype(np.uint8)


def masked_ncc(a: np.ndarray, b: np.ndarray, mask: np.ndarray) -> float:
    """Normalized cross-correlation over mask==1 pixels."""
    m = mask.astype(bool)
    if m.sum() < 8:
        return -1.0
    av = a[m].astype(np.float32)
    bv = b[m].astype(np.float32)
    av -= av.mean()
    bv -= bv.mean()
    an = float(np.linalg.norm(av))
    bn = float(np.linalg.norm(bv))
    if an < 1e-6 or bn < 1e-6:
        return -1.0
    return float(np.dot(av, bv) / (an * bn))


def best_overlay_ncc(
    crop_hi: np.ndarray,
    tmpl_hi: np.ndarray,
    circle: np.ndarray,
    max_shift: int = 1,
) -> float:
    """NCC for a fixed-size overlay, optionally jittering crop by ±max_shift px."""
    best = -1.0
    h, w = crop_hi.shape
    for dy in range(-max_shift, max_shift + 1):
        for dx in range(-max_shift, max_shift + 1):
            if dx == 0 and dy == 0:
                shifted = crop_hi
            else:
                m = np.full((h, w), 240, dtype=crop_hi.dtype)
                src_x0 = max(0, dx)
                src_y0 = max(0, dy)
                dst_x0 = max(0, -dx)
                dst_y0 = max(0, -dy)
                copy_w = w - abs(dx)
                copy_h = h - abs(dy)
                m[dst_y0 : dst_y0 + copy_h, dst_x0 : dst_x0 + copy_w] = crop_hi[
                    src_y0 : src_y0 + copy_h, src_x0 : src_x0 + copy_w
                ]
                shifted = m
            score = masked_ncc(shifted, tmpl_hi, circle)
            if score > best:
                best = score
    return best


def match_circle_fast(
    crop_pane: np.ndarray,
    circle_templates: dict[int, list[np.ndarray]],
    circle: np.ndarray,
    upscale: int = 2,
) -> list[tuple[float, int]]:
    """Rank templates by direct full-circle overlay at canonical pane size.

    No template sliding — only a small ±1 px shift search on the crop for
    sub-pixel alignment after 2x upscaling.

    Returns sorted [(score, num)] highest first.
    """
    crop_hi = boost_contrast(crop_pane)
    if upscale > 1:
        up = crop_pane.shape[0] * upscale
        crop_hi = cv2.resize(crop_hi, (up, up), interpolation=cv2.INTER_CUBIC)

    scores: list[tuple[float, int]] = []
    for num, variants in circle_templates.items():
        best_score = -1.0
        for tmpl_hi in variants:
            score = best_overlay_ncc(crop_hi, tmpl_hi, circle)
            if score > best_score:
                best_score = score
        if best_score > -1.0:
            scores.append((best_score, num))

    scores.sort(reverse=True)
    return scores


def normalize_three_weights(
    filtered: float, unfiltered: float, ocr: float
) -> tuple[float, float, float]:
    total = filtered + unfiltered + ocr
    if total <= 0:
        return 1 / 3, 1 / 3, 1 / 3
    return filtered / total, unfiltered / total, ocr / total


def combine_three_scores(
    filtered_scores: list[tuple[float, int]],
    unfiltered_scores: list[tuple[float, int]],
    ocr_scores: dict[int, float],
    weight_filtered: float,
    weight_unfiltered: float,
    weight_ocr: float,
) -> list[tuple[float, int]]:
    """Weighted average of filtered, unfiltered, and OCR scores per number."""
    wf, wu, wo = normalize_three_weights(weight_filtered, weight_unfiltered, weight_ocr)
    nums = {num for _, num in filtered_scores} | {num for _, num in unfiltered_scores} | set(ocr_scores)
    by_num: dict[int, float] = {num: 0.0 for num in nums}
    for score, num in filtered_scores:
        by_num[num] += wf * score
    for score, num in unfiltered_scores:
        by_num[num] += wu * score
    for num, score in ocr_scores.items():
        by_num[num] += wo * score
    return sorted(((s, n) for n, s in by_num.items()), reverse=True)


def combine_match_scores(
    filtered_scores: list[tuple[float, int]],
    unfiltered_scores: list[tuple[float, int]],
    weight_filtered: float,
    weight_unfiltered: float,
) -> list[tuple[float, int]]:
    """Weighted average of filtered and unfiltered crop scores (no OCR)."""
    return combine_three_scores(
        filtered_scores,
        unfiltered_scores,
        {},
        weight_filtered,
        weight_unfiltered,
        0.0,
    )


def cell_score_margin(scores: list[tuple[float, int]]) -> float:
    """Gap between the top two template scores for one cell."""
    if len(scores) < 2:
        return float("inf")
    return scores[0][0] - scores[1][0]


def needs_manual_review(
    score: float,
    margin: float,
    *,
    score_cutoff: float = REVIEW_SCORE_CUTOFF,
    margin_cutoff: float = REVIEW_SCORE_DIFFERENCE,
) -> bool:
    return score < score_cutoff or margin < margin_cutoff


@dataclass
class CellScoreData:
    scores_f: list[tuple[float, int]]
    scores_u: list[tuple[float, int]]
    ocr: dict[int, float]


@dataclass
class MatchingCache:
    snapped: list[tuple[int, int, int, int, int]]
    ncols: int
    nrows: int
    x0: float
    y0: float
    spacing: float
    pane: int
    per_cell: dict[tuple[int, int], CellScoreData]
    timing: dict[str, float] = field(default_factory=dict)


def collect_ocr_cell_scores(
    img: np.ndarray,
    snapped: list[tuple[int, int, int, int, int]],
    x0: float,
    y0: float,
    spacing: float,
    ncols: int,
    nrows: int,
    reader,
    api,
) -> dict[tuple[int, int], dict[int, float]]:
    cell_scores: dict[tuple[int, int], dict[int, float]] = defaultdict(dict)
    for ri, ci, x, y, _ in snapped:
        result = ocr_node(img, x, y, reader, api)
        if result:
            value, score = result
            cell_scores[(ri, ci)][value] = max(cell_scores[(ri, ci)].get(value, 0.0), score)
    for value, score, cx, cy in detect_full_image(img, reader):
        ci = int(round((cx - x0) / spacing))
        ri = int(round((cy - y0) / spacing))
        if 0 <= ci < ncols and 0 <= ri < nrows:
            cell_scores[(ri, ci)][value] = max(cell_scores[(ri, ci)].get(value, 0.0), score)
    return dict(cell_scores)


def prepare_matching_cache(
    img: np.ndarray,
    templates_by_num: dict[int, list[np.ndarray]],
    pane_size: float,
    node_radius: float,
    max_rows: int | None = None,
    *,
    use_ocr: bool = True,
) -> MatchingCache:
    t0 = time.perf_counter()
    nodes_raw = detect_nodes(img, param2=HOUGH_PARAM2)
    spacing_guess = estimate_spacing(nodes_raw)
    x0, y0, spacing, ncols, nrows = calibrate_grid(
        nodes_raw, spacing_guess, ncols=GRID_COLS, nrows=GRID_ROWS
    )
    snapped = snap_nodes(nodes_raw, x0, y0, spacing, ncols, nrows)
    if max_rows is not None:
        snapped = [(ri, ci, x, y, r) for ri, ci, x, y, r in snapped if ri < max_rows]
    t1 = time.perf_counter()

    pane = int(round(pane_size))
    upscale = 2
    circle_templates, circle = prepare_circle_templates(
        templates_by_num, pane, node_radius, upscale=upscale
    )
    t2 = time.perf_counter()

    gray_img = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    per_cell: dict[tuple[int, int], CellScoreData] = {}
    for ri, ci, x, y, r in snapped:
        crop_filtered = crop_node_pane_cleaned(gray_img, x, y, r, pane, node_radius)
        crop_unfiltered = crop_node_pane_unfiltered(gray_img, x, y, r, pane, node_radius)
        scores_f = match_circle_fast(crop_filtered, circle_templates, circle, upscale=upscale)
        scores_u = match_circle_fast(crop_unfiltered, circle_templates, circle, upscale=upscale)
        per_cell[(ri, ci)] = CellScoreData(scores_f=scores_f, scores_u=scores_u, ocr={})
    t3 = time.perf_counter()

    ocr_scores: dict[tuple[int, int], dict[int, float]] = {}
    if use_ocr:
        import easyocr
        import tesserocr

        reader = easyocr.Reader(["en"], gpu=False, verbose=False)
        with tesserocr.PyTessBaseAPI(
            psm=tesserocr.PSM.SINGLE_WORD, oem=tesserocr.OEM.LSTM_ONLY
        ) as api:
            api.SetVariable("tessedit_char_whitelist", "0123456789")
            api.SetVariable("user_defined_dpi", "300")
            ocr_scores = collect_ocr_cell_scores(
                img, snapped, x0, y0, spacing, ncols, nrows, reader, api
            )
        for key, scores in ocr_scores.items():
            if key in per_cell:
                per_cell[key].ocr = scores
    t4 = time.perf_counter()

    return MatchingCache(
        snapped=snapped,
        ncols=ncols,
        nrows=nrows,
        x0=x0,
        y0=y0,
        spacing=spacing,
        pane=pane,
        per_cell=per_cell,
        timing={
            "seconds_detect": round(t1 - t0, 2),
            "seconds_prepare": round(t2 - t1, 2),
            "seconds_match": round(t3 - t2, 2),
            "seconds_ocr": round(t4 - t3, 2),
            "seconds_total": round(t4 - t0, 2),
        },
    )


def assign_from_cache(
    cache: MatchingCache,
    *,
    weight_filtered: float,
    weight_unfiltered: float,
    weight_ocr: float,
    margin_cutoff: float = REVIEW_SCORE_DIFFERENCE,
    max_rows: int | None = None,
) -> tuple[
    list[list[str]],
    dict[str, object],
    dict[str, object],
    list[dict[str, object]],
]:
    wf, wu, wo = normalize_three_weights(weight_filtered, weight_unfiltered, weight_ocr)
    candidates: list[tuple[float, int, int, int]] = []
    cell_margins: dict[tuple[int, int], float] = {}
    cell_details: list[dict[str, object]] = []

    for ri, ci, x, y, r in cache.snapped:
        data = cache.per_cell[(ri, ci)]
        scores = combine_three_scores(
            data.scores_f, data.scores_u, data.ocr, wf, wu, wo
        )
        cell_margins[(ri, ci)] = cell_score_margin(scores)
        for score, num in scores:
            candidates.append((score, num, ri, ci))

        best_num = scores[0][1] if scores else None
        second_num = scores[1][1] if len(scores) > 1 else None
        sf = next((s for s, n in data.scores_f if n == best_num), 0.0) if best_num else 0.0
        su = next((s for s, n in data.scores_u if n == best_num), 0.0) if best_num else 0.0
        so = data.ocr.get(best_num, 0.0) if best_num else 0.0
        cell_details.append(
            {
                "row": ri,
                "col": ci,
                "x": x,
                "y": y,
                "pred": best_num,
                "combined": scores[0][0] if scores else 0.0,
                "score_filtered": sf,
                "score_unfiltered": su,
                "score_ocr": so,
                "margin": cell_margins[(ri, ci)],
                "second_num": second_num,
                "second_combined": scores[1][0] if len(scores) > 1 else 0.0,
                "top3": scores[:3],
                "ocr_readings": dict(data.ocr),
            }
        )

    assigned, assigned_scores, per_threshold = assign_iterative_threshold(candidates)
    grid = [["x"] * cache.ncols for _ in range(cache.nrows)]
    needs_review = 0
    low_score_review = 0
    low_margin_review = 0
    for (ri, ci), num in assigned.items():
        if max_rows is not None and ri >= max_rows:
            continue
        score = assigned_scores[(ri, ci)]
        margin = cell_margins.get((ri, ci), float("inf"))
        uncertain = needs_manual_review(score, margin, margin_cutoff=margin_cutoff)
        if uncertain:
            needs_review += 1
        if score < REVIEW_SCORE_CUTOFF:
            low_score_review += 1
        if margin < margin_cutoff:
            low_margin_review += 1
        grid[ri][ci] = format_cell(num, uncertain=uncertain)
        for detail in cell_details:
            if detail["row"] == ri and detail["col"] == ci:
                detail["assigned"] = num
                detail["assigned_score"] = score

    snapped_cells = [
        (ri, ci, x, y, r)
        for ri, ci, x, y, r in cache.snapped
        if max_rows is None or ri < max_rows
    ]
    meta = {
        "nodes_snapped": len(cache.snapped),
        "cells_scored": len(snapped_cells),
        "pairwise_scores": len(candidates),
        "assigned": sum(1 for ri, _ in assigned if max_rows is None or ri < max_rows),
        "needs_review": needs_review,
        "low_score_review": low_score_review,
        "low_margin_review": low_margin_review,
        "assigned_at_threshold": {str(k): v for k, v in per_threshold.items()},
        "x0": round(cache.x0, 1),
        "y0": round(cache.y0, 1),
        "spacing": round(cache.spacing, 1),
        "ncols": cache.ncols,
        "nrows": cache.nrows if max_rows is None else max_rows,
        "pane_size": cache.pane,
        "match_weight_filtered": round(wf, 4),
        "match_weight_unfiltered": round(wu, 4),
        "match_weight_ocr": round(wo, 4),
        "review_score_difference": margin_cutoff,
        **cache.timing,
    }
    review_info = {
        "snapped": snapped_cells,
        "assigned": assigned,
        "assigned_scores": assigned_scores,
        "cell_margins": cell_margins,
    }
    return grid, meta, review_info, cell_details


def load_grid_csv(path: Path) -> list[list[str]]:
    return [row for row in csv.reader(path.read_text().splitlines()) if row]


def compare_to_ground_truth(
    pred: list[list[str]], truth: list[list[str]]
) -> dict[str, object]:
    mismatches: list[dict[str, object]] = []
    correct = 0
    total = 0
    for ri, (pred_row, truth_row) in enumerate(zip(pred, truth)):
        for ci, (p, t) in enumerate(zip(pred_row, truth_row)):
            if t == "x":
                continue
            total += 1
            p_val = parse_cell(p)
            t_val = int(t)
            if p_val == t_val:
                correct += 1
            else:
                mismatches.append({"row": ri, "col": ci, "truth": t_val, "pred": p_val, "raw": p})
    return {
        "total": total,
        "correct": correct,
        "accuracy": correct / total if total else 0.0,
        "mismatches": mismatches,
    }


def write_score_dump(
    path: Path,
    cell_details: list[dict[str, object]],
    truth: list[list[str]] | None,
    comparison: dict[str, object] | None,
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(
            [
                "row",
                "col",
                "truth",
                "assigned",
                "correct",
                "combined",
                "score_filtered",
                "score_unfiltered",
                "score_ocr",
                "margin",
                "second_num",
                "second_combined",
                "top3",
                "ocr_readings",
            ]
        )
        mismatch_set = {
            (m["row"], m["col"]) for m in (comparison or {}).get("mismatches", [])
        }
        for d in sorted(cell_details, key=lambda x: (x["row"], x["col"])):
            ri, ci = int(d["row"]), int(d["col"])
            truth_val = truth[ri][ci] if truth and ri < len(truth) and ci < len(truth[ri]) else "x"
            assigned = d.get("assigned", d.get("pred"))
            correct = ""
            if truth_val != "x" and assigned is not None:
                correct = str(int(truth_val) == int(assigned))
            top3 = d.get("top3", [])
            top3_str = ";".join(f"{n}:{s:.4f}" for s, n in top3)
            ocr_readings = d.get("ocr_readings", {})
            ocr_str = ";".join(f"{n}:{s:.4f}" for n, s in sorted(ocr_readings.items()))
            writer.writerow(
                [
                    ri,
                    ci,
                    truth_val,
                    assigned if assigned is not None else "x",
                    correct,
                    f"{d.get('assigned_score', d.get('combined', 0.0)):.4f}",
                    f"{d['score_filtered']:.4f}",
                    f"{d['score_unfiltered']:.4f}",
                    f"{d['score_ocr']:.4f}",
                    f"{d['margin']:.4f}",
                    d.get("second_num", ""),
                    f"{d.get('second_combined', 0.0):.4f}",
                    top3_str,
                    ocr_str,
                ]
            )
        if comparison:
            writer.writerow([])
            writer.writerow(["accuracy", f"{comparison['accuracy']:.4f}"])
            writer.writerow(["correct", comparison["correct"]])
            writer.writerow(["total", comparison["total"]])
            writer.writerow(["mismatches", len(comparison["mismatches"])])
            writer.writerow([])
            writer.writerow(["mismatch_row", "mismatch_col", "truth", "pred"])
            for m in comparison["mismatches"]:
                writer.writerow([m["row"], m["col"], m["truth"], m["pred"]])


def tune_match_weights(
    cache: MatchingCache,
    truth: list[list[str]],
    *,
    max_rows: int | None = None,
    margin_cutoff: float = REVIEW_SCORE_DIFFERENCE,
    coarse_step: float = 0.1,
    fine_step: float = 0.05,
    fine_radius: float = 0.15,
) -> tuple[float, float, float, dict[str, object], list[list[str]], list[dict[str, object]]]:
    """Grid-search normalized weights to maximize ground-truth accuracy."""

    def evaluate(wf: float, wu: float, wo: float) -> tuple[float, list[list[str]], list[dict[str, object]], dict]:
        grid, _, _, details = assign_from_cache(
            cache,
            weight_filtered=wf,
            weight_unfiltered=wu,
            weight_ocr=wo,
            margin_cutoff=margin_cutoff,
            max_rows=max_rows,
        )
        cmp = compare_to_ground_truth(grid, truth)
        return float(cmp["accuracy"]), grid, details, cmp

    best_acc = -1.0
    best_weights = (MATCH_WEIGHT_FILTERED, MATCH_WEIGHT_UNFILTERED, MATCH_WEIGHT_OCR)
    best_grid: list[list[str]] = []
    best_details: list[dict[str, object]] = []
    best_cmp: dict[str, object] = {}

    def search(step: float, center: tuple[float, float, float] | None = None) -> None:
        nonlocal best_acc, best_weights, best_grid, best_details, best_cmp
        wo_values = np.arange(0.0, 0.55, step)
        for wo in wo_values:
            for wf in np.arange(0.0, 1.01 - wo, step):
                wu = 1.0 - wo - wf
                if wu < -1e-9:
                    continue
                if center is not None:
                    cf, cu, co = center
                    if (
                        abs(wf - cf) > fine_radius + 1e-9
                        or abs(wu - cu) > fine_radius + 1e-9
                        or abs(wo - co) > fine_radius + 1e-9
                    ):
                        continue
                acc, grid, details, cmp = evaluate(wf, wu, wo)
                if acc > best_acc + 1e-9 or (
                    abs(acc - best_acc) <= 1e-9
                    and len(cmp["mismatches"]) < len(best_cmp.get("mismatches", [None] * 999))
                ):
                    best_acc = acc
                    best_weights = (float(wf), float(wu), float(wo))
                    best_grid = grid
                    best_details = details
                    best_cmp = cmp

    search(coarse_step)
    center = best_weights
    search(fine_step, center=center)
    return (*best_weights, best_cmp, best_grid, best_details)


def build_grid(
    img: np.ndarray,
    templates_by_num: dict[int, list[np.ndarray]],
    pane_size: float,
    node_radius: float,
    max_rows: int | None = None,
    debug: bool = False,
    match_weight_filtered: float = MATCH_WEIGHT_FILTERED,
    match_weight_unfiltered: float = MATCH_WEIGHT_UNFILTERED,
    match_weight_ocr: float = MATCH_WEIGHT_OCR,
    margin_cutoff: float = REVIEW_SCORE_DIFFERENCE,
    cache: MatchingCache | None = None,
) -> tuple[list[list[str]], dict[str, object], dict[str, object], list[dict[str, object]]]:
    if cache is None:
        cache = prepare_matching_cache(
            img, templates_by_num, pane_size, node_radius, max_rows, use_ocr=match_weight_ocr > 0
        )
    grid, meta, review_info, cell_details = assign_from_cache(
        cache,
        weight_filtered=match_weight_filtered,
        weight_unfiltered=match_weight_unfiltered,
        weight_ocr=match_weight_ocr,
        margin_cutoff=margin_cutoff,
        max_rows=max_rows,
    )

    if debug:
        wf, wu, wo = normalize_three_weights(
            match_weight_filtered, match_weight_unfiltered, match_weight_ocr
        )
        for i, detail in enumerate(cell_details):
            ri, ci = int(detail["row"]), int(detail["col"])
            assigned = detail.get("assigned", detail.get("pred"))
            top3 = detail.get("top3", [])
            top_str = ", ".join(f"{n}({s:.3f})" for s, n in top3[:3])
            print(
                f"  [{i+1:3d}/{len(cell_details)}] grid=({ri},{ci}) "
                f"assigned={assigned} combined={detail.get('assigned_score', detail['combined']):.3f} "
                f"f={detail['score_filtered']:.3f} u={detail['score_unfiltered']:.3f} "
                f"o={detail['score_ocr']:.3f} margin={detail['margin']:.3f} "
                f"weights=({wf:.2f},{wu:.2f},{wo:.2f}) top3: {top_str}"
            )
        print("\n  Assignment by threshold:")
        for th, count in meta["assigned_at_threshold"].items():
            print(f"    >={th}: +{count}")

    return grid, meta, review_info, cell_details


def show_review_crop(display: np.ndarray, title: str) -> None:
    """Show the review crop in an OpenCV window (must call waitKey for it to appear)."""
    cv2.namedWindow(REVIEW_WINDOW, cv2.WINDOW_NORMAL)
    h, w = display.shape[:2]
    cv2.resizeWindow(REVIEW_WINDOW, w, h)
    cv2.imshow(REVIEW_WINDOW, display)
    cv2.setWindowTitle(REVIEW_WINDOW, title)
    cv2.moveWindow(REVIEW_WINDOW, 80, 80)
    cv2.waitKey(50)


def assign_iterative_threshold(
    candidates: list[tuple[float, int, int, int]],
    thresholds: tuple[float, ...] = ASSIGN_THRESHOLDS,
) -> tuple[dict[tuple[int, int], int], dict[tuple[int, int], float], dict[float, int]]:
    """Assign numbers to grid cells using progressively lower score cutoffs.

    *candidates* is [(score, num, row, col), ...] for all (cell, number) pairs.
    At each threshold, greedily assigns highest-scoring remaining pairs with
    score >= threshold. Each number and cell is used at most once.
    """
    assigned: dict[tuple[int, int], int] = {}
    assigned_scores: dict[tuple[int, int], float] = {}
    used_nums: set[int] = set()
    used_cells: set[tuple[int, int]] = set()
    per_threshold: dict[float, int] = {}

    ranked = sorted(candidates, reverse=True)
    for threshold in thresholds:
        n_before = len(assigned)
        for score, num, ri, ci in ranked:
            if score < threshold:
                continue
            if num in used_nums or (ri, ci) in used_cells:
                continue
            assigned[(ri, ci)] = num
            assigned_scores[(ri, ci)] = score
            used_nums.add(num)
            used_cells.add((ri, ci))
        per_threshold[threshold] = len(assigned) - n_before

    return assigned, assigned_scores, per_threshold


def format_cell(num: int, *, uncertain: bool) -> str:
    return f"?{num}" if uncertain else str(num)


def parse_cell(value: str) -> int | None:
    if value == "x":
        return None
    return int(value.lstrip("?"))


def find_cell_with_number(
    grid: list[list[str]], num: int, *, skip: tuple[int, int] | None = None
) -> tuple[int, int] | None:
    for r, row in enumerate(grid):
        for c, v in enumerate(row):
            if skip is not None and (r, c) == skip:
                continue
            if parse_cell(v) == num:
                return r, c
    return None


def review_low_confidence(
    img: np.ndarray,
    grid: list[list[str]],
    snapped: list[tuple[int, int, int, int, int]],
    assigned: dict[tuple[int, int], int],
    assigned_scores: dict[tuple[int, int], float],
    cell_margins: dict[tuple[int, int], float],
    *,
    pane_size: float,
    node_radius: float,
    score_cutoff: float = REVIEW_SCORE_CUTOFF,
    margin_cutoff: float = REVIEW_SCORE_DIFFERENCE,
) -> int:
    """Interactively confirm or correct uncertain assignments.

    Queues cells with assignment score below *score_cutoff* or where the top two
    template scores differ by less than *margin_cutoff*. Typed numbers are treated
    as authoritative; conflicts clear the other cell and queue it for review.
    """
    cell_coords = {(ri, ci): (x, y, r) for ri, ci, x, y, r in snapped}
    # (row, col, guess, score, margin, displaced)
    queue: deque[tuple[int, int, int | None, float, float, bool]] = deque(
        sorted(
            (
                (
                    ri,
                    ci,
                    assigned[(ri, ci)],
                    assigned_scores[(ri, ci)],
                    cell_margins.get((ri, ci), float("inf")),
                    False,
                )
                for (ri, ci) in assigned
                if needs_manual_review(
                    assigned_scores.get((ri, ci), 1.0),
                    cell_margins.get((ri, ci), float("inf")),
                    score_cutoff=score_cutoff,
                    margin_cutoff=margin_cutoff,
                )
            ),
            key=lambda t: (t[0], t[1]),
        )
    )
    if not queue:
        return 0

    queued_cells = {(ri, ci) for ri, ci, *_ in queue}
    used_nums = {parse_cell(v) for row in grid for v in row if v != "x"}
    used_nums.discard(None)

    print(
        f"\nReviewing {len(queue)} uncertain assignment(s) "
        f"(score < {score_cutoff} or top-two margin < {margin_cutoff})."
    )
    print(
        "  Enter 1-176 to assign (authoritative), Enter to keep guess, "
        "s to skip, q to stop review."
    )
    print("  Conflicts clear the other cell and queue it for review.")

    cv2.startWindowThread()
    changed = 0
    reviewed = 0
    try:
        while queue:
            ri, ci, guess, score, margin, displaced = queue.popleft()
            queued_cells.discard((ri, ci))
            reviewed += 1
            x, y, r = cell_coords[(ri, ci)]
            if img.ndim == 2:
                source = cv2.cvtColor(img, cv2.COLOR_GRAY2BGR)
            else:
                source = img
            native = crop_node_native(source, x, y, r, int(round(pane_size)), node_radius)
            display = native
            if REVIEW_CROP_SCALE > 1:
                display = cv2.resize(
                    display,
                    None,
                    fx=REVIEW_CROP_SCALE,
                    fy=REVIEW_CROP_SCALE,
                    interpolation=cv2.INTER_NEAREST,
                )
            if displaced:
                status = "displaced"
            else:
                parts = [f"score={score:.3f}"]
                if margin < margin_cutoff:
                    parts.append(f"margin={margin:.3f}")
                status = " ".join(parts)
            guess_str = str(guess) if guess is not None else "?"
            title = (
                f"grid ({ri},{ci}) guess={guess_str} {status} "
                f"[{reviewed}, {len(queue)} queued]"
            )
            show_review_crop(display, title)

            while True:
                prompt = (
                    f"[{reviewed}, {len(queue)} queued] row={ri} col={ci}  "
                    f"guess={guess_str}  {status}  > "
                )
                try:
                    raw = input(prompt).strip()
                except (EOFError, KeyboardInterrupt):
                    print("\nReview interrupted.")
                    return changed

                if raw.lower() == "q":
                    print("Stopping review early.")
                    return changed
                if raw.lower() == "s" or raw == "":
                    if displaced:
                        grid[ri][ci] = "x"
                    elif guess is not None:
                        grid[ri][ci] = format_cell(guess, uncertain=True)
                    break

                try:
                    num = int(raw)
                except ValueError:
                    print("  Enter a number 1-176, Enter, s, or q.")
                    continue
                if not 1 <= num <= 176:
                    print("  Number must be 1-176.")
                    continue

                owner = find_cell_with_number(grid, num, skip=(ri, ci))
                if owner is not None:
                    or_, oc = owner
                    old_num = parse_cell(grid[or_][oc])
                    grid[or_][oc] = "x"
                    if old_num is not None:
                        used_nums.discard(old_num)
                    if (or_, oc) not in queued_cells:
                        queue.append(
                            (
                                or_,
                                oc,
                                old_num,
                                assigned_scores.get((or_, oc), 0.0),
                                cell_margins.get((or_, oc), float("inf")),
                                True,
                            )
                        )
                        queued_cells.add((or_, oc))
                    print(
                        f"  Cleared ({or_},{oc}) (was {old_num}); "
                        f"queued for manual review."
                    )

                if guess is not None and guess != num:
                    used_nums.discard(guess)
                grid[ri][ci] = str(num)
                used_nums.add(num)
                if num != guess:
                    changed += 1
                break
    finally:
        cv2.destroyWindow(REVIEW_WINDOW)

    return changed


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image", type=Path)
    parser.add_argument("-t", "--templates", type=Path, default=Path("assets/number_templates"))
    parser.add_argument("-o", "--output", type=Path)
    parser.add_argument("--max-rows", type=int, help="Only match circles in the top N grid rows")
    parser.add_argument("--debug", action="store_true", help="Print per-circle matching details")
    parser.add_argument(
        "--plot-detections",
        type=Path,
        help="Save a side-by-side detection overlay image to this path",
    )
    parser.add_argument(
        "--preview-line-filter",
        type=Path,
        help="Save a montage showing line-filter before/after on sample node crops",
    )
    parser.add_argument(
        "--no-review",
        action="store_true",
        help=(
            f"Skip interactive review (score < {REVIEW_SCORE_CUTOFF} or "
            f"top-two margin < {REVIEW_SCORE_DIFFERENCE})"
        ),
    )
    parser.add_argument(
        "--review-score-difference",
        type=float,
        default=REVIEW_SCORE_DIFFERENCE,
        help="Queue manual review when top two scores differ by less than this (default: 0.02)",
    )
    parser.add_argument(
        "--match-weight-filtered",
        type=float,
        default=MATCH_WEIGHT_FILTERED,
        help="Weight for line-filtered crop scores (default: 0.5)",
    )
    parser.add_argument(
        "--match-weight-unfiltered",
        type=float,
        default=MATCH_WEIGHT_UNFILTERED,
        help="Weight for unfiltered crop scores (default: 0.21)",
    )
    parser.add_argument(
        "--match-weight-ocr",
        type=float,
        default=MATCH_WEIGHT_OCR,
        help="Weight for OCR scores (default: 0.3)",
    )
    parser.add_argument(
        "--ground-truth",
        type=Path,
        help="Compare results against a definitive grid CSV",
    )
    parser.add_argument(
        "--dump-scores",
        type=Path,
        help="Write per-cell scoring details to CSV",
    )
    parser.add_argument(
        "--tune-weights",
        action="store_true",
        help="Grid-search weights against --ground-truth to maximize accuracy",
    )
    args = parser.parse_args()

    if args.tune_weights and args.ground_truth is None:
        raise SystemExit("--tune-weights requires --ground-truth")

    img = cv2.imread(str(args.image))
    if img is None:
        raise SystemExit(f"Could not read image: {args.image}")

    manifest = read_manifest(args.templates)
    pane_size = int(round(manifest.get("pane_size", 42.0)))
    node_radius = manifest.get("node_radius", 20.0)

    if args.plot_detections:
        counts = save_detection_plot(img, args.plot_detections)
        print(f"Wrote detection plot to {args.plot_detections}")
        for key, value in counts.items():
            print(f"  {key}: {value}")

    if args.preview_line_filter:
        stats = save_line_filter_preview(
            img, args.preview_line_filter, pane_size, node_radius
        )
        print(f"Wrote line-filter preview to {args.preview_line_filter}")
        for key, value in stats.items():
            print(f"  {key}: {value}")

    if args.output is None:
        if not args.plot_detections and not args.preview_line_filter:
            raise SystemExit("Provide -o/--output, --plot-detections, or --preview-line-filter")
        return

    templates_by_num = load_all_freq_templates(args.templates)
    if not templates_by_num:
        raise SystemExit(f"No templates found in {args.templates}")

    pane_size_f = manifest.get("pane_size", 42.0)
    node_radius_f = manifest.get("node_radius", 20.0)

    truth = load_grid_csv(args.ground_truth) if args.ground_truth else None
    cache = prepare_matching_cache(
        img,
        templates_by_num,
        pane_size_f,
        node_radius_f,
        max_rows=args.max_rows,
        use_ocr=True,
    )

    if args.tune_weights:
        assert truth is not None
        print("Tuning match weights against ground truth...")
        wf, wu, wo, comparison, grid, cell_details = tune_match_weights(
            cache,
            truth,
            max_rows=args.max_rows,
            margin_cutoff=args.review_score_difference,
        )
        meta = {
            "tuned": True,
            "match_weight_filtered": round(wf, 4),
            "match_weight_unfiltered": round(wu, 4),
            "match_weight_ocr": round(wo, 4),
            **cache.timing,
        }
        review_info = {"snapped": [], "assigned": {}, "assigned_scores": {}, "cell_margins": {}}
        print(
            f"\nBest weights: filtered={wf:.3f} unfiltered={wu:.3f} ocr={wo:.3f} "
            f"accuracy={comparison['accuracy']:.1%} "
            f"({comparison['correct']}/{comparison['total']})"
        )
    else:
        grid, meta, review_info, cell_details = build_grid(
            img,
            templates_by_num,
            pane_size_f,
            node_radius_f,
            max_rows=args.max_rows,
            debug=args.debug,
            match_weight_filtered=args.match_weight_filtered,
            match_weight_unfiltered=args.match_weight_unfiltered,
            match_weight_ocr=args.match_weight_ocr,
            margin_cutoff=args.review_score_difference,
            cache=cache,
        )
        comparison = compare_to_ground_truth(grid, truth) if truth else None
        if comparison:
            print(
                f"\nGround truth: {comparison['correct']}/{comparison['total']} correct "
                f"({comparison['accuracy']:.1%}), {len(comparison['mismatches'])} mismatches"
            )

    if args.dump_scores:
        write_score_dump(args.dump_scores, cell_details, truth, comparison)
        print(f"Wrote score dump to {args.dump_scores}")

    if comparison and not args.tune_weights:
        for m in comparison["mismatches"][:30]:
            print(f"  mismatch ({m['row']},{m['col']}): truth={m['truth']} pred={m['pred']}")
        if len(comparison["mismatches"]) > 30:
            print(f"  ... and {len(comparison['mismatches']) - 30} more")
    elif args.tune_weights and comparison:
        for m in comparison["mismatches"][:30]:
            print(f"  mismatch ({m['row']},{m['col']}): truth={m['truth']} pred={m['pred']}")
        if len(comparison["mismatches"]) > 30:
            print(f"  ... and {len(comparison['mismatches']) - 30} more")

    manual_corrections = 0
    if args.tune_weights or args.no_review:
        if not args.tune_weights and meta.get("needs_review", 0):
            print(
                f"\nSkipping review of {meta['needs_review']} uncertain cell(s) "
                f"(--no-review)."
            )
    elif meta.get("needs_review", 0):
        manual_corrections = review_low_confidence(
            img,
            grid,
            review_info["snapped"],
            review_info["assigned"],
            review_info["assigned_scores"],
            review_info["cell_margins"],
            pane_size=pane_size_f,
            node_radius=node_radius_f,
            margin_cutoff=args.review_score_difference,
        )
        meta["manual_corrections"] = manual_corrections
    else:
        print(
            f"\nNo uncertain assignments "
            f"(score >= {REVIEW_SCORE_CUTOFF} and margin >= {args.review_score_difference}); "
            f"review skipped."
        )

    rows = grid if args.max_rows is None else grid[: args.max_rows]
    csv = "\n".join(",".join(row) for row in rows) + "\n"
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(csv)

    print(f"\nWrote {args.output}")
    for key, value in meta.items():
        print(f"  {key}: {value}")
    values = {parse_cell(v) for row in rows for v in row if v != "x"}
    values.discard(None)
    print(f"  unique numbers: {len(values)}")
    missing = set(range(1, 177)) - values
    if missing:
        print(f"  missing: {sorted(missing)[:20]}{'...' if len(missing) > 20 else ''}")


if __name__ == "__main__":
    main()
