import os
import pickle
from typing import List, Dict, Any

import faiss
from sentence_transformers import SentenceTransformer


class RagService:
    def __init__(self):
        self._base_dir = os.path.dirname(__file__)
        self._index_path = os.path.join(self._base_dir, "tree_diseases.index")
        self._docs_path = os.path.join(self._base_dir, "documents.pkl")
        self._index = None  # type: ignore
        self._documents: List[str] = []
        self._model = None  # lazy

    def _ensure_loaded(self) -> None:
        if self._index is None:
            if not os.path.exists(self._index_path):
                raise FileNotFoundError(
                    f"FAISS индекс не найден: {self._index_path}. Сначала запустите построение индекса (rag.py)."
                )
            self._index = faiss.read_index(self._index_path)

        if not self._documents:
            if not os.path.exists(self._docs_path):
                raise FileNotFoundError(
                    f"Файл документов не найден: {self._docs_path}. Сначала запустите построение индекса (rag.py)."
                )
            with open(self._docs_path, "rb") as f:
                self._documents = pickle.load(f)

        if self._model is None:
            self._model = SentenceTransformer('sentence-transformers/paraphrase-multilingual-mpnet-base-v2')

    def search(self, query_text: str, k: int = 3) -> List[Dict[str, Any]]:
        """
        Выполняет семантический поиск по индексу и возвращает top-k документов.
        Возвращаемая структура: [{"text": str, "distance": float, "rank": int}]
        """
        self._ensure_loaded()
        query_vector = self._model.encode([query_text]).astype('float32')
        distances, indices = self._index.search(query_vector, k)

        results: List[Dict[str, Any]] = []
        for rank, (dist, idx) in enumerate(zip(distances[0], indices[0]), start=1):
            if int(idx) < 0 or int(idx) >= len(self._documents):
                continue
            results.append({
                "text": self._documents[int(idx)],
                "distance": float(dist),
                "rank": rank
            })
        return results


_service_singleton: RagService = RagService()


def get_rag_service() -> RagService:
    return _service_singleton


