import numpy as np
from typing import List

class NmsProcessor:

    def __init__(self,
        iou_thr: float = 0.5,
        beta : float = -1.0,
        alpha_all_classes : float = 0.5,
                ) -> None:

        self.iou_thr = iou_thr
        self.beta = beta
        self.alpha_all_classes = alpha_all_classes

    @staticmethod
    def _inter(a: np.ndarray | List[float], b: np.ndarray | List[float]) -> float:
        x1 = max(a[0], b[0]); y1 = max(a[1], b[1])
        x2 = min(a[2], b[2]); y2 = min(a[3], b[3])
        iw = max(0.0, x2 - x1); ih = max(0.0, y2 - y1)
        return iw * ih

    def _precompute(self, boxes_np: np.ndarray):
        """предрасчёт площадей и генератор функций IoU / contain_ratio_min по индексам"""
        wh = np.clip(boxes_np[:, 2:4] - boxes_np[:, 0:2], a_min=0, a_max=None)
        areas = wh[:, 0] * wh[:, 1]

        def iou_of(i: int, j: int) -> float:
            inter = self._inter(boxes_np[i], boxes_np[j])
            union = areas[i] + areas[j] - inter + 1e-9
            return inter / union

        def contain_ratio_min(i: int, j: int) -> float:
            inter = self._inter(boxes_np[i], boxes_np[j])
            smaller = min(areas[i], areas[j])
            return inter / (smaller + 1e-9)

        return areas, iou_of, contain_ratio_min

    def _stage1_classwise_indices(
        self,
        boxes_np: np.ndarray,
        scores_np: np.ndarray,
        labels_np: np.ndarray,
    ) -> List[int]:
        """Внутриклассовый NMS + (опц.) вложенность по боксам (beta)."""
        _, iou_of, contain_ratio_min = self._precompute(boxes_np)
        keep_stage1: List[int] = []

        for cls in np.unique(labels_np):
            idxs = np.where(labels_np == cls)[0]
            idxs = idxs[np.argsort(-scores_np[idxs])]  # по убыванию score
            kept_cls: List[int] = []

            while len(idxs) > 0:
                i = idxs[0]
                kept_cls.append(i)
                if len(idxs) == 1:
                    break
                rest = idxs[1:]

                # 1) IoU-подавление
                if self.iou_thr < 1.0:
                    keep_mask_iou = np.array([iou_of(i, j) <= self.iou_thr for j in rest], dtype=bool)
                else:
                    keep_mask_iou = np.ones(len(rest), dtype=bool)

                # 2) правило вложенности: inter / area(min_box) < beta — оставить
                if self.beta is not None and self.beta >= 0.0:
                    keep_mask_cont = np.array([contain_ratio_min(i, j) < self.beta for j in rest], dtype=bool)
                else:
                    keep_mask_cont = np.ones(len(rest), dtype=bool)

                keep_mask = np.logical_and(keep_mask_iou, keep_mask_cont)
                idxs = rest[keep_mask]

            keep_stage1.extend(kept_cls)

        return keep_stage1

    def _stage2_crossclass_indices(
        self,
        boxes_np: np.ndarray,
        scores_np: np.ndarray,
        kept_indices: List[int],
    ) -> List[int]:
        """Глобальный межклассовый NMS по IoU (alpha_all_classes)."""
        if self.alpha_all_classes is None or self.alpha_all_classes < 0.0 or self.alpha_all_classes >= 1.0:
            # выключено — просто вернуть, отсортировав по score
            return sorted(kept_indices, key=lambda k: -scores_np[k])

        areas, _, _ = self._precompute(boxes_np)

        def iou_pair(i: int, j: int) -> float:
            inter = self._inter(boxes_np[i], boxes_np[j])
            return inter / (areas[i] + areas[j] - inter + 1e-9)

        final_keep: List[int] = []
        idxs = np.array(sorted(kept_indices, key=lambda k: -scores_np[k]), dtype=int)

        while len(idxs) > 0:
            i = idxs[0]
            final_keep.append(i)
            if len(idxs) == 1:
                break
            rest = idxs[1:]
            keep_mask = np.array([iou_pair(i, j) <= self.alpha_all_classes for j in rest], dtype=bool)
            idxs = rest[keep_mask]

        return sorted(final_keep, key=lambda k: -scores_np[k])

    def nms_two_stage(
        self,
        boxes: List[List[float]],
        scores: List[float],
        labels: List[str],
    ) -> List[int]:
        """
        1) Class-wise NMS по IoU (+опц. правило вложенности beta)
        2) Global (межклассовый) NMS по IoU с порогом alpha_all_classes
        """
        boxes_np  = np.asarray(boxes,  dtype=np.float32)
        scores_np = np.asarray(scores, dtype=np.float32)
        labels_np = np.asarray(labels)

        keep_stage1 = self._stage1_classwise_indices(
            boxes_np, scores_np, labels_np
        )
        final_keep = self._stage2_crossclass_indices(
            boxes_np, scores_np, keep_stage1
        )
        return final_keep
