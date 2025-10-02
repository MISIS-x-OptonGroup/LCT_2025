from transformers import GroundingDinoProcessor, GroundingDinoForObjectDetection
from functools import cached_property
import torch
import numpy as np
from typing import List
from PIL import Image


class GdinoModel:
    def __init__(
            self,
            _DEVICE,
            prompts, # promt for gdino
            GDINO_ID="IDEA-Research/grounding-dino-base",
            load_now : bool = True,
            box_threshold: float = 0.20, 
            text_threshold: float = 0.20,
            size = {"shortest_edge": 1200, "longest_edge": 1900}
            ) -> None:
        self.size = size
        self.device = _DEVICE
        self.GDINO_ID = GDINO_ID
        self.prompts = prompts
        self.box_threshold = box_threshold
        self.text_threshold = text_threshold

        if load_now:
             _, _ = self.GDINO, self.GPROC

    def preprocess_caption(self, text: str) -> str:
        t = text.lower().strip()
        return t if t.endswith(".") else t + "."
    
    @cached_property
    def GPROC(self):
        print('GDINO processor is loading...')
        proc = GroundingDinoProcessor.from_pretrained(self.GDINO_ID)
        proc.image_processor.size = self.size
        if proc is None:
            raise RuntimeError("GDINO processor is not loaded!")
        else:
            print("GDINO processor is loaded!")
        return proc
    
    @cached_property
    def GDINO(self):
        print('GDINO weights are loading...')
        model = GroundingDinoForObjectDetection.from_pretrained(self.GDINO_ID).to(self.device).eval()
        if model is None:
            raise RuntimeError("GDINO weights not loaded!")
        else:
            print("GDINO weights are loaded!")
        return model

    
    def _limits_from_size(self):
        """
        Возвращает (target_short, target_long_or_max) из self.size.
        """
        if isinstance(self.size, dict):
            target_short = self.size.get("shortest_edge", None)
            target_long = self.size.get("longest_edge", None)
            return target_short, target_long
        elif isinstance(self.size, int):
            # если задано просто число — трактуем как ограничение по короткой стороне
            return int(self.size), None
        else:
            raise ValueError("Unsupported type for `size`. Use int or dict with 'shortest_edge'/'longest_edge'.")

    @torch.no_grad()
    def detect_boxes_hf(self, image_rgb):
        """
        Возвращает: boxes_xyxy (List[List[float]]), labels (List[str]), scores (List[float])
        Логика ресайза:
        - если обе стороны изображения <= рамок (shortest_edge/longest_edge), то do_resize=False (оставляем родное разрешение);
        - иначе ресайзим до self.size.
        """
        H, W = image_rgb.shape[:2]
        short, long_ = (H, W) if H < W else (W, H)

        target_short, target_long = self._limits_from_size()

        # признак «картинка уже в рамках»
        within_short = (target_short is None) or (short <= target_short)
        within_long = (target_long is None) or (long_ <= target_long)
        within_limits = within_short and within_long

        boxes_all, labels_all, scores_all = [], [], []

        pil_img = Image.fromarray(image_rgb)

        for label, phrases in self.prompts.items():
            text = " ".join(self.preprocess_caption(p) for p in phrases)

            if within_limits:
                # не ресайзим вовсе
                inputs = self.GPROC(
                    images=pil_img,
                    text=text,
                    return_tensors="pt",
                    do_resize=False,          # ключевой флаг
                ).to(self.device)
            else:
                # ресайзим вниз до заданных рамок
                # поддержка обоих вариантов ключей
                size_arg = {}
                if target_short is not None:
                    size_arg["shortest_edge"] = target_short
                if target_long is not None:
                    # одновременно передавать оба безопасно: лишний игнорируется
                    size_arg["longest_edge"] = target_long

                inputs = self.GPROC(
                    images=pil_img,
                    text=text,
                    return_tensors="pt",
                    do_resize=True,
                    size=size_arg if size_arg else None,
                ).to(self.device)

            outputs = self.GDINO(**inputs)

            # ВАЖНО: target_sizes должны быть исходными (H, W),
            # чтобы боксы вернулись в пиксели оригинала
            results = self.GPROC.post_process_grounded_object_detection(
                outputs,
                threshold=self.box_threshold,
                target_sizes=[(H, W)],
                text_threshold=self.text_threshold,
            )[0]

            for b, s in zip(results["boxes"], results["scores"]):
                boxes_all.append(b.cpu().numpy().tolist())
                labels_all.append(label)
                scores_all.append(float(s))

        return boxes_all, labels_all, scores_all