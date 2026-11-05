"""
FastAPI 入口：站长流式对话 + 与现有前端兼容的占位接口。

启动：在仓库根执行 ./scripts/dev-up.sh（会创建 cat-agent/.venv 并 pip install）。
手动：cd cat-agent && .venv/bin/python -m uvicorn api:app --host 127.0.0.1 --port 8000
"""

from __future__ import annotations

import json
import os
import re
from typing import Any, AsyncIterator, Literal

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, StreamingResponse
from pydantic import BaseModel, Field

from ai_service import (
    get_nearby_shelters_impl,
    initial_state,
    station_master_graph,
)

app = FastAPI(title="Cat Rescue Station Master Agent", version="2.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ---------------------------------------------------------------------------
# 请求体
# ---------------------------------------------------------------------------
class ChatTurn(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(..., min_length=1)


class ChatStreamRequest(BaseModel):
    message: str = Field(..., min_length=1, description="用户当前一句话")
    lat: float | None = Field(None, description="可选纬度")
    lng: float | None = Field(None, description="可选经度")
    iot_snapshot: dict[str, Any] | None = Field(
        None, description="场景 C：设备报警/健康快照 JSON"
    )
    assessment_context: str | None = Field(
        None, description="本次照片评估摘要，多轮追问时持续带上"
    )
    history: list[ChatTurn] = Field(
        default_factory=list,
        description="本轮之前的对话（不含 message 本身）",
    )
    need_nearby: bool = Field(
        False,
        description="为 true 时即使文案未提及「附近」，也按场景 B 查机构（如前端按钮）",
    )
    output_language: str | None = Field(
        None,
        description="输出语言：zh 或 en（与 locale 二选一即可，优先 output_language）",
    )
    locale: str | None = Field(
        None,
        description="界面语言，与 output_language 对齐时传 zh / en",
    )
    cat_id: int | None = Field(
        None,
        description="当前聚焦的猫咪档案 ID（热力图/评估匹配），供其他节点使用",
    )
    assessment_id: str | None = Field(
        None,
        description="当前聚焦的评估 UUID（与页面最近一次评估一致），供档案详情类查询",
    )
    assessment_duplicate: dict[str, Any] | None = Field(
        None,
        description="上传后同猫识别结果（assessmentDuplicate JSON），供站长回答「之前见过吗」",
    )
    volunteer_user_id: int | None = Field(
        None,
        description="志愿者数字 ID（喂养打卡 POST /volunteers/me/feeding-checkins 使用）",
    )


class NearbyResourcesRequest(BaseModel):
    location: str = ""
    need_tnr: bool = True
    need_vet: bool = True
    lat: float | None = None
    lng: float | None = None


def _sse(payload: dict[str, Any]) -> str:
    """SSE 单行 data 帧。"""
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"


def _redact_secrets(text: str) -> str:
    """避免异常正文或上游回包把 API Key 泄漏到 SSE / 日志。"""
    if not text:
        return text
    t = text
    t = re.sub(r"sk-[a-zA-Z0-9_-]{8,}", "sk-***REDACTED***", t, flags=re.I)
    t = re.sub(r"Bearer\s+[a-zA-Z0-9._-]+", "Bearer ***REDACTED***", t, flags=re.I)
    return t


def _format_stream_error(exc: BaseException) -> str:
    """把 OpenAI 404 等错误展开成可读的排查提示。"""
    parts = [_redact_secrets(str(exc).strip() or exc.__class__.__name__)]
    body = getattr(exc, "body", None)
    if body is not None:
        try:
            raw = json.dumps(body, ensure_ascii=False) if isinstance(body, dict) else str(body)
            parts.append(_redact_secrets(raw))
        except Exception:  # noqa: BLE001
            parts.append(_redact_secrets(str(body)))
    response = getattr(exc, "response", None)
    if response is not None:
        try:
            parts.append(f"HTTP {getattr(response, 'status_code', '?')}")
        except Exception:  # noqa: BLE001
            pass
    hint = _redact_secrets(
        "若仍为 404：检查 LLM 网关 OPENAI_BASE_URL（需可访问 /v1/chat/completions）、"
        "OPENAI_MODEL 是否可用；勿在日志或截图中粘贴密钥。"
    )
    return " | ".join(p for p in parts if p) + " — " + hint


def _chunk_content(chunk: Any) -> str:
    """从 LangChain AIMessageChunk 取出文本增量。"""
    content = getattr(chunk, "content", None)
    if content is None:
        return ""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for block in content:
            if isinstance(block, str):
                parts.append(block)
            elif isinstance(block, dict) and block.get("type") == "text":
                parts.append(str(block.get("text", "")))
        return "".join(parts)
    return str(content)


def _yield_sse_token_chunks(text: str, step: int = 32):
    """将完整回复拆成多帧 SSE token（与 web_search 补发逻辑一致）。"""
    if not text:
        return
    for i in range(0, len(text), step):
        yield _sse({"type": "token", "content": text[i : i + step]})


# LangGraph astream_events(v2) 对图内节点往往不冒泡 on_chat_model_stream，只在 on_chain_end 给出 final_text
#（例如 db_lookup_reply 早退路径不调 LLM）。此处在链结束且本轮未流式输出时补发 token，避免前端 acc 为空显示「暂无内容」。
_FINAL_TEXT_CHAIN_NODES = frozenset(
    {"db_lookup_reply", "education_reply", "shelter_reply", "iot_audit", "multi_tool"},
)


async def _stream_graph_events(
    state: dict[str, Any],
) -> AsyncIterator[str]:
    """
    使用 LangGraph astream_events(v2) 捕获 OpenAI token，并以 SSE 推给前端。

    事件类型（data JSON）：
    - token: 打字机文本增量
    - route: 场景路由结果
    - cards: 附近机构卡片（场景 B）
    - done: 结束
    - error: 异常
    """
    route_emitted = False
    cards_emitted = False
    final_cards: list[dict[str, Any]] = []
    chat_model_stream_had_tokens = False

    try:
        async for event in station_master_graph.astream_events(
            state,
            version="v2",
        ):
            kind = event.get("event")
            name = event.get("name", "")

            # 分类完成后推送路由（便于前端展示不同 UI）
            if kind == "on_chain_end" and name == "classify" and not route_emitted:
                output = (event.get("data") or {}).get("output") or {}
                route = output.get("route")
                if route:
                    route_emitted = True
                    # routing_mode / tools show whether the rules or the LLM decided, and which tools it chose.
                    yield _sse({
                        "type": "route",
                        "route": route,
                        "routing_mode": output.get("routing_mode", "rule"),
                        "tools": output.get("tools_used") or [],
                    })

            # 机构数据拉取完成后缓存卡片
            if kind == "on_chain_end" and name == "shelter_fetch":
                output = (event.get("data") or {}).get("output") or {}
                cards = output.get("shelter_cards") or []
                if cards:
                    final_cards = cards
                    cards_emitted = True
                    yield _sse({"type": "cards", "items": cards})

            # 联网搜索节点不经过 LangChain stream，在此补发 token 供前端打字机展示
            if kind == "on_chain_end" and name == "web_search":
                output = (event.get("data") or {}).get("output") or {}
                text = (output.get("final_text") or "").strip()
                for frame in _yield_sse_token_chunks(text):
                    yield frame

            # 图内回复节点：若 v2 未冒泡 on_chat_model_stream，用链结束时的 final_text 补发（含 db_lookup 固定句 / 缺参提示）
            if kind == "on_chain_end" and name in _FINAL_TEXT_CHAIN_NODES:
                output = (event.get("data") or {}).get("output") or {}
                text = (output.get("final_text") or "").strip()
                if text and not chat_model_stream_had_tokens:
                    for frame in _yield_sse_token_chunks(text):
                        yield frame

            # LangChain / OpenAI 流式 token（若环境有冒泡则优先走增量；上面 on_chain_end 会因 flag 跳过重复）
            if kind == "on_chat_model_stream":
                chunk = (event.get("data") or {}).get("chunk")
                text = _chunk_content(chunk)
                if text:
                    chat_model_stream_had_tokens = True
                    yield _sse({"type": "token", "content": text})

        # 图跑完后补发卡片（若流式阶段未单独推送）
        if final_cards and not cards_emitted:
            yield _sse({"type": "cards", "items": final_cards})

        yield _sse({"type": "done"})
    except Exception as exc:  # noqa: BLE001
        yield _sse({"type": "error", "message": _format_stream_error(exc)})


@app.post("/api/ai/chat-stream")
async def chat_stream(body: ChatStreamRequest) -> StreamingResponse:
    """
    站长对话 SSE 流式接口（打字机效果）。

    前端示例：
      const es = new EventSource(...)  // 需 POST 时用 fetch + ReadableStream
      或 fetch(url, {method:'POST', body: JSON.stringify({message:'...'})})
        .then(r => r.body.getReader()) ...
    """
    history = [{"role": t.role, "content": t.content.strip()} for t in body.history if t.content.strip()]
    raw_lang = (body.output_language or body.locale or "zh").strip().lower()
    ol = "en" if raw_lang.startswith("en") else "zh"
    state = initial_state(
        body.message.strip(),
        lat=body.lat,
        lng=body.lng,
        volunteer_user_id=body.volunteer_user_id,
        iot_snapshot=body.iot_snapshot,
        assessment_context=(body.assessment_context or "").strip() or None,
        chat_history=history,
        output_language=ol,
        focus_cat_id=body.cat_id,
        focus_assessment_id=body.assessment_id,
        assessment_duplicate=body.assessment_duplicate,
    )
    if body.need_nearby and state.get("route") is None:
        # 覆盖路由：按钮「附近绝育」
        state["route"] = "shelter"  # type: ignore[typeddict-unknown-key]

    # 若 need_nearby，在 classify 前写入标记供 classify_route 使用
    if body.need_nearby:
        state["user_message"] = (state.get("user_message") or "") + " [nearby_shelters]"

    return StreamingResponse(
        _stream_graph_events(state),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


# ---------------------------------------------------------------------------
# 兼容旧版前端：附近资源文本（index.html pythonAgentFetchNearbyResourcesText）
# ---------------------------------------------------------------------------
@app.post("/v1/resources/nearby")
async def resources_nearby(body: NearbyResourcesRequest) -> JSONResponse:
    if body.lat is None or body.lng is None:
        return JSONResponse(
            {
                "text": "请先提供定位坐标（lat/lng）后再查询附近机构。",
                "cards": [],
            }
        )
    payload = await get_nearby_shelters_impl(
        body.lat,
        body.lng,
        need_tnr=body.need_tnr,
        need_vet=body.need_vet,
    )
    orgs = payload.get("organizations") or []
    lines = []
    for o in orgs[:3]:
        name = o.get("name") or "机构"
        dist = o.get("distanceKm")
        dist_s = f"{dist:.1f}km" if isinstance(dist, (int, float)) else "—"
        phone = o.get("phone") or "未提供"
        addr = o.get("address") or ""
        lines.append(f"· {name}（约 {dist_s}）\n  地址：{addr}\n  电话：{phone}")
    text = "\n\n".join(lines) if lines else "暂未查到附近机构，请稍后再试。"
    return JSONResponse({"text": text, "cards": payload.get("cards") or [], "raw": payload})


@app.get("/health")
async def health() -> dict[str, Any]:
    model = os.getenv("OPENAI_MODEL", "gpt-4o-mini")
    key = (os.getenv("OPENAI_API_KEY") or os.getenv("CAT_RESCUE_OPENAI_API_KEY") or "").strip()
    key_ok = bool(key) and key.startswith("sk-") and "replace" not in key.lower()
    return {
        "status": "ok" if key_ok else "degraded",
        "agent": "openai-langgraph",
        "model": model,
        "llmReady": key_ok,
        "hint": None
        if key_ok
        else "Configure the LLM credential in your environment (see .env.example), then restart uvicorn. Never expose API keys in logs or screenshots.",
    }


# 占位：深度分析 multipart 仍由后续迭代实现；避免旧前端 404
@app.post("/v1/analyze/complete")
async def analyze_complete_stub() -> JSONResponse:
    return JSONResponse(
        status_code=501,
        content={
            "detail": "Use POST /api/ai/chat-stream for the station-master agent, "
            "or keep the previous analyze pipeline until migrated."
        },
    )
