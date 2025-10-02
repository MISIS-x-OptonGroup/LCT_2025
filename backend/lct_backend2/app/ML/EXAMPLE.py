from ImageSegmentatator import ImageSegmentator

# <-------------------> 
# this is only for visualization !!!

import os, glob, random
from typing import List, Tuple, Dict, Optional, Sequence
import numpy as np
import torch
from PIL import Image
import cv2
import matplotlib.pyplot as plt

def color_for_label(label: str) -> Tuple[int,int,int]:
    seed = abs(hash(label)) % (2**32)
    rng = np.random.default_rng(seed)
    c = rng.integers(0, 255, size=3, dtype=np.uint8)
    return int(c[0]), int(c[1]), int(c[2])


def draw_boxes(
    image_rgb: np.ndarray,
    boxes: List[List[float]],
    labels: List[str],
    scores: Optional[List[float]] = None,
    thickness: int = 2,
) -> np.ndarray:
    vis = image_rgb.copy()
    for i, box in enumerate(boxes):
        x1, y1, x2, y2 = map(int, box)
        lab = labels[i] if i < len(labels) else "obj"
        col = color_for_label(lab)
        cv2.rectangle(vis, (x1,y1), (x2,y2), col, thickness)
        txt = lab if scores is None else f"{lab} {scores[i]:.2f}"
        (tw, th), bl = cv2.getTextSize(txt, cv2.FONT_HERSHEY_SIMPLEX, 0.5, 1)
        ytxt = max(0, y1-4)
        cv2.rectangle(vis, (x1, ytxt-th-4), (x1+tw+4, ytxt+2), col, -1)
        cv2.putText(vis, txt, (x1+2, ytxt-2), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0,0,0), 1, cv2.LINE_AA)
    return vis

def overlay_masks(
    image_rgb: np.ndarray,
    masks: List[np.ndarray],
    labels: Optional[Sequence[str]] = None,
    alpha: float = 0.5,
) -> np.ndarray:
    vis = image_rgb.copy()
    h, w = vis.shape[:2]
    colors = []
    if labels is not None and len(labels) == len(masks):
        colors = [color_for_label(l) for l in labels]
    else:
        rng = np.random.default_rng(123)
        colors = [tuple(map(int, c)) for c in rng.integers(0,255,size=(len(masks),3),dtype=np.uint8)]
    for i, m in enumerate(masks):
        mm = np.array(m)
        if mm.ndim == 3 and mm.shape[-1] == 1: mm = mm[...,0]
        if mm.dtype != np.uint8:
            mm = (mm > 0.5).astype(np.uint8) * 255
        if mm.shape[:2] != (h, w):
            mm = cv2.resize(mm, (w, h), interpolation=cv2.INTER_NEAREST)
        cm = np.zeros_like(vis, dtype=np.uint8); cm[mm>0] = colors[i % len(colors)]
        vis = cv2.addWeighted(vis, 1.0, cm, alpha, 0.0)
    return vis

def render_vis(image_rgb, masks, boxes, labels, scores, alpha=0.55):
    vis = overlay_masks(image_rgb, masks, labels=labels, alpha=alpha)
    vis = draw_boxes(vis, boxes, labels, scores)
    return vis


def show_inline(vis: np.ndarray, title: str = "", figsize=(10,10)):
    plt.figure(figsize=figsize)
    plt.imshow(vis)
    plt.axis("off")
    if title:
        plt.title(title)
    plt.show()

# <-------------------> 


# how it works

seg = ImageSegmentator(
    _box_thr=0.10080074890084499,
    _text_thr=0.2693266367707789,
    _iou_thr=0.8527497557418572,
    _beta=0.9457256509335716,
    _alpha_all_classes=0.7412089719658128,
)

img_path = "/Users/rougenn/projects/yolo_trees/trees/1.jpg"

img = Image.open(img_path).convert("RGB")
image_rgb = np.array(img, dtype=np.uint8)

# masks could be None if use_sam == False
masks, boxes, labels, scores = seg.predict_with_array(image_rgb)



# visualization
vis = render_vis(image_rgb, masks, boxes, labels, scores)
plt.figure()
plt.imshow(vis); plt.axis("off")
plt.title("SAM2+GroundingDINO")
plt.show()