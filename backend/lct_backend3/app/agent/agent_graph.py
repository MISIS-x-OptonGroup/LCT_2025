import os
import json
import logging
from typing import Any, Dict, List, TypedDict

import requests
from langgraph.graph import StateGraph, START, END

try:
    from .rag_service import get_rag_service  # запуск как пакет: python -m agent.main
except ImportError:
    from rag_service import get_rag_service  # запуск из папки agent: python main.py


logger = logging.getLogger("agent_graph")
logger.setLevel(logging.INFO)


class AgentState(TypedDict, total=False):
    image_input: str
    max_tokens: int
    temperature: float
    observation_text: str
    rag_results: List[Dict[str, Any]]
    final_json: str
    logs: List[str]


def _read_system_prompt() -> str:
    base_dir = os.path.dirname(__file__)
    prompt_path = os.path.join(base_dir, "promt.txt")
    with open(prompt_path, "r", encoding="utf-8") as f:
        return f.read()


def _append_log(state: AgentState, message: str) -> None:
    logs = state.get("logs") or []
    logs.append(message)
    state["logs"] = logs


def _call_nvidia(messages: List[Dict[str, Any]], *, max_tokens: int, temperature: float) -> str:
    api_key = os.getenv("NVIDIA_API_KEY", "")
    if not api_key:
        raise RuntimeError("NVIDIA_API_KEY не задан в окружении")

    invoke_url = "https://integrate.api.nvidia.com/v1/chat/completions"
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Accept": "application/json",
        "Content-Type": "application/json"
    }
    payload = {
        "model": "meta/llama-3.2-90b-vision-instruct",
        "messages": messages,
        "max_tokens": max_tokens,
        "temperature": temperature,
        "top_p": 1.0,
        "frequency_penalty": 0.0,
        "presence_penalty": 0.0,
        "stream": False
    }

    response = requests.post(invoke_url, headers=headers, json=payload)
    response.raise_for_status()
    result = response.json()
    if "choices" in result and len(result["choices"]) > 0:
        return result["choices"][0]["message"]["content"]
    raise RuntimeError("Пустой ответ от NVIDIA API")


def node_vision_observation(state: AgentState) -> AgentState:
    """
    Делает первый проход по изображению: краткие наблюдения (без финального JSON).
    """
    image_input = state["image_input"]
    max_tokens = state.get("max_tokens", 512)
    temperature = state.get("temperature", 0.7)

    messages: List[Dict[str, Any]] = [
        {
            "role": "user",
            "content": [
                {"type": "text", "text": (
                    "Сформулируй краткие наблюдения по признакам на изображении дерева/куста: "
                    "листья, кора, крона, ствол, видимые повреждения, грибные тела, вредители. "
                    "Короткие маркеры на русском, без выводов о породе и диагнозах, до 60 слов."
                )},
                {"type": "image_url", "image_url": {"url": image_input}}
            ]
        }
    ]

    text = _call_nvidia(messages, max_tokens=min(max_tokens, 256), temperature=temperature)
    _append_log(state, f"[vision_observation] observation=\n{text}")
    state["observation_text"] = text
    return state


def node_rag_search(state: AgentState) -> AgentState:
    """
    Ищет релевантные записи по наблюдениям.
    """
    query = state.get("observation_text", "")
    rag = get_rag_service()
    results = rag.search(query, k=3)
    state["rag_results"] = results
    pretty = "\n\n".join([f"#{r['rank']} (L2={r['distance']:.4f})\n{r['text']}" for r in results])
    _append_log(state, f"[rag_search] top_docs=\n{pretty}")
    return state


def _format_rag_context(results: List[Dict[str, Any]]) -> str:
    parts: List[str] = []
    for r in results:
        snippet = r.get("text", "")
        if len(snippet) > 1200:
            snippet = snippet[:1200] + "..."
        parts.append(f"[{r.get('rank')}] {snippet}")
    return "\n\n".join(parts)


def node_final_synthesis(state: AgentState) -> AgentState:
    """
    Генерирует финальный ответ строго в формате JSON по системной инструкции из promt.txt.
    Использует: изображение, наблюдения, контекст RAG.
    """
    system_prompt = _read_system_prompt()
    image_input = state["image_input"]
    max_tokens = state.get("max_tokens", 512)
    temperature = state.get("temperature", 0.2)
    observation = state.get("observation_text", "")
    rag_results = state.get("rag_results", [])
    rag_context = _format_rag_context(rag_results)

    messages: List[Dict[str, Any]] = [
        {"role": "system", "content": system_prompt},
        {
            "role": "user",
            "content": [
                {"type": "text", "text": (
                    "Следуй строго инструкции. Выведи ТОЛЬКО JSON по схеме.\n\n"
                    f"Наблюдения: {observation}\n\n"
                    f"Контекст RAG (болезни, симптомы, рекомендации):\n{rag_context}"
                )},
                {"type": "image_url", "image_url": {"url": image_input}}
            ]
        }
    ]

    raw = _call_nvidia(messages, max_tokens=max_tokens, temperature=temperature)
    _append_log(state, f"[final_synthesis] raw=\n{raw}")
    state["final_json"] = raw
    return state


def node_validate(state: AgentState) -> AgentState:
    """
    Валидирует, что ответ — валидный JSON. При необходимости пробует извлечь JSON из текста.
    """
    text = state.get("final_json", "").strip()
    fixed = text
    try:
        json.loads(text)
        _append_log(state, "[validate] JSON ok")
        return state
    except Exception:
        # Попытка извлечь блок JSON по крайним фигурным скобкам
        try:
            start = text.find("{")
            end = text.rfind("}")
            if start != -1 and end != -1 and end > start:
                candidate = text[start:end + 1]
                json.loads(candidate)
                fixed = candidate
                _append_log(state, "[validate] extracted JSON block")
        except Exception as e:  # noqa: BLE001
            _append_log(state, f"[validate] extraction failed: {e}")

    state["final_json"] = fixed
    return state


def build_graph() -> Any:
    graph = StateGraph(AgentState)
    graph.add_node("vision", node_vision_observation)
    graph.add_node("rag", node_rag_search)
    graph.add_node("synthesis", node_final_synthesis)
    graph.add_node("validate", node_validate)

    graph.add_edge(START, "vision")
    graph.add_edge("vision", "rag")
    graph.add_edge("rag", "synthesis")
    graph.add_edge("synthesis", "validate")
    graph.add_edge("validate", END)

    return graph.compile()


_compiled_graph = build_graph()


def run_agent(image_input: str, *, max_tokens: int = 768, temperature: float = 0.2) -> Dict[str, Any]:
    """
    Запускает граф агента. Возвращает словарь с полями: final_json, logs.
    image_input — это URL или data:URI (base64) изображения.
    """
    initial: AgentState = {
        "image_input": image_input,
        "max_tokens": max_tokens,
        "temperature": temperature,
        "logs": []
    }
    result: AgentState = _compiled_graph.invoke(initial)
    return {
        "final_json": result.get("final_json", ""),
        "logs": result.get("logs", [])
    }


