from sam2.sam2_image_predictor import SAM2ImagePredictor
from functools import cached_property
import torch
from dataclasses import dataclass
import numpy as np
from typing import List


@dataclass
class MaskResult:
    masks: List[np.ndarray]
    indices: List[int]

class Sam2Model:
    def __init__(
            self,
            _DEVICE,
            SAM2_ID="facebook/sam2.1-hiera-large",
            load_now : bool = True,
            ) -> None:
        self.device = _DEVICE
        self.SAM2_ID = SAM2_ID

        if load_now:
             _ = self.sam2


    @cached_property
    def sam2(self):
        print('SAM2 weights are loading...')
        model = SAM2ImagePredictor.from_pretrained(self.SAM2_ID, device=self.device)
        if model is None:
            raise RuntimeError("SAM2 weights not loaded!")
        else:
            print("SAM2 weights are loaded!")
        return model

    # @torch.no_grad()
    def predict_mask(self, image_rgb: np.ndarray, 
                    boxes_xyxy: List[List[float]]
                    ) -> MaskResult:
        
        self.sam2.set_image(image_rgb)
        out_masks: List[np.ndarray] = []
        ok_index: List[int] = [] # индексы боксов, у которых получилось извлечь маску

        for bi, box in enumerate(boxes_xyxy):
            masks, scores, _ = self.sam2.predict(box=np.array(box, dtype=np.float32))
            if masks is None or len(masks) == 0:
                continue
            best = int(np.argmax(np.array(scores))) if scores is not None else 0
            out_masks.append(np.array(masks[best]))
            ok_index.append(bi)
        return MaskResult(masks=out_masks, indices=ok_index) 
