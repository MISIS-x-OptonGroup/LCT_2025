import os, glob, random
from typing import List, Tuple, Dict, Optional
import numpy as np
import torch
from PIL import Image
import cv2
from .classes.Sam2Model import Sam2Model
from .classes.GDinoModel import GdinoModel
from .classes.NmsProcessor import NmsProcessor

# base consts
# _BETA = 0.9
# _box_thr=0.20
# _text_thr=0.20
# _iou_thr = 0.40
# _alpha_all_classes = 0.8

class ImageSegmentator:
    
    def pick_device(self) -> str:
        if torch.cuda.is_available(): return "cuda"
        if hasattr(torch.backends, "mps") and torch.backends.mps.is_available(): return "mps"
        return "cpu"
 
    def __init__(self,
                _DEVICE = None, # какой девайс используется при работе модели
                _prompts : Optional[List[str]] = None, # промпты для гдино, лучше оставить дефолтные
                _GDINO_ID="IDEA-Research/grounding-dino-base", # модель GDINO
                _gdino_load_now : bool = True, # если True, то GDINO подгрузит все сразу, а не при первом использовании
                _SAM2_ID="facebook/sam2.1-hiera-large", # модель SAM2
                _sam2_load_now : bool = True, # если True, то SAM подгрузит все сразу, а не при первом использовании
                use_sam : bool = True, # используется ли SAM2. Если нет, то маски будут None 
                _iou_thr: float = 0.40, # порог для нмс 
                _beta : float = 0.9, # порог для обработки после нмс
                _alpha_all_classes : float = 0.8, # порог для нмс всех классов
                _box_thr : float = 0.20, # боксовый порог для гдино
                _text_thr : float = 0.20, # классовый порог для гдино 
                size= {"shortest_edge": 1200, "longest_edge": 1900},
            ) -> None:
        
        """
        Инициализация сегментатора изображений.

        Args:
            _DEVICE (str, optional): 
                Какой девайс использовать ("cuda", "mps", "cpu"). 
                Если None — выбирается автоматически.
            _prompts (dict, optional): 
                Промпты для GroundingDINO. Лучше оставить дефолтные.
            _GDINO_ID (str): 
                HuggingFace ID модели GroundingDINO.
            _gdino_load_now (bool): 
                Если True — GDINO загружается сразу, иначе при первом использовании.
            _SAM2_ID (str): 
                HuggingFace ID модели SAM2.
            _sam2_load_now (bool): 
                Если True — SAM2 загружается сразу, иначе при первом использовании.
            use_sam (bool): 
                Использовать ли SAM2. Если False — маски будут None.
            _iou_thr (float): 
                Порог IoU для NMS (внутри класса).
            _beta (float): 
                Порог вложенности боксов после NMS.
            _alpha_all_classes (float): 
                Порог межклассового NMS.
            _box_thr (float): 
                Порог по уверенности для боксов (GDINO).
            _text_thr (float): 
                Порог по уверенности для текстовых классов (GDINO).
        """
        

        self.use_sam = use_sam
        self.device = _DEVICE if _DEVICE is not None else self.pick_device()

        self.nms = NmsProcessor(
            iou_thr = _iou_thr,
            beta = _beta,
            alpha_all_classes = _alpha_all_classes,
        )

        self.sam2 = Sam2Model(
            _DEVICE=self.device,
            SAM2_ID=_SAM2_ID,
            load_now=_sam2_load_now,
        ) if use_sam else None
        basePrompts = {
                    "tree":        ["single tree", "dead tree", "one tree"],
                    "bush":        ["bush"],
                }
        self.prompts = _prompts if _prompts is not None else basePrompts
        
        self.gdino = GdinoModel(
            _DEVICE = self.device,
            prompts=self.prompts,
            GDINO_ID=_GDINO_ID,
            load_now=_gdino_load_now,
            box_threshold=_box_thr,
            text_threshold=_text_thr,
            size=size,
        )
    
    def predict_with_array(
        self,
        image_rgb: np.ndarray
    ):
        # image_rgb = np.array(Image.open(image_path).convert("RGB"))
        with torch.inference_mode():    
            boxes, labels, scores = self.gdino.detect_boxes_hf(image_rgb)
        if not boxes:
            return [], [], [], []

        keep = self.nms.nms_two_stage(boxes, scores, labels)
        boxes_k = [boxes[i] for i in keep]
        labels_k= [labels[i] for i in keep]
        scores_k= [scores[i] for i in keep]

        if self.use_sam and self.sam2 is not None:
            with torch.inference_mode():
                out = self.sam2.predict_mask(image_rgb, boxes_k)
            masks, ok_idx = out.masks, out.indices
            # синхронизируем метаданные с реально полученными масками
            boxes_final  = [boxes_k[i]  for i in ok_idx]
            labels_final = [labels_k[i] for i in ok_idx]
            scores_final = [scores_k[i] for i in ok_idx]
        else:
            masks = []
            boxes_final, labels_final, scores_final = boxes_k, labels_k, scores_k

        return masks, boxes_final, labels_final, scores_final
    
    def predict_with_text(
        self,
        image_path: str
    ):
        img = Image.open(image_path).convert("RGB")
        image_rgb = np.array(img, dtype=np.uint8)
        return self.predict_with_array(image_rgb)

