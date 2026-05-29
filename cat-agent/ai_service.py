"""
LangGraph 站长 Agent：OpenAI 底座 + 意图路由（科普 / 附近机构 / IoT 审计）。

运行前请设置环境变量：OPENAI_API_KEY（或 CAT_RESCUE_OPENAI_API_KEY）
可选：OPENAI_MODEL（默认 gpt-4o-mini）、OPENAI_BASE_URL（默认 https://api.openai.com）
可选：CAT_RESCUE_API_BASE（默认 http://127.0.0.1:8090）用于 get_nearby_shelters 拉 Java 目录 API。
可选：OPENAI_HOSPITAL_SEARCH_MODEL（默认 gpt-4o-search-preview）用于医院/收容所联网搜索节点。
"""

from __future__ import annotations

import json
import os
import re
from typing import Any, Literal, TypedDict

import httpx
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langchain_core.runnables import RunnableConfig
from langchain_core.tools import tool
from langchain_openai import ChatOpenAI
from langgraph.graph import END, START, StateGraph

# ---------------------------------------------------------------------------
# OpenAI LLM（与 Spring cat-rescue.openai.* 环境变量命名一致）
# ---------------------------------------------------------------------------
_PLACEHOLDER_KEY_MARKERS = (
    "replace_with",
    "your_openai",
    "sk-placeholder",
    "changeme",
    "xxx",
)


def _openai_api_key() -> str:
    key = (os.getenv("OPENAI_API_KEY") or os.getenv("CAT_RESCUE_OPENAI_API_KEY") or "").strip()
    if not key:
        raise RuntimeError(
            "Missing OPENAI_API_KEY (or CAT_RESCUE_OPENAI_API_KEY). "
            "Export the same key you use for the Java backend."
        )
    lower = key.lower()
    if any(m in lower for m in _PLACEHOLDER_KEY_MARKERS) or key.startswith("replace_"):
        raise RuntimeError(
            "OPENAI_API_KEY looks like a placeholder from .env.example "
            "(e.g. replace_with_openai_key). Edit .env.local with a real sk-... key, "
            "then: set -a && source .env.local && set +a && restart uvicorn."
        )
    if not key.startswith("sk-") and len(key) < 20:
        raise RuntimeError(
            "OPENAI_API_KEY does not look valid (expected sk-... from OpenAI). "
            "Check .env.local and restart uvicorn after sourcing it."
        )
    return key


_OPENAI_MODEL = os.getenv("OPENAI_MODEL", "gpt-4o-mini").strip() or "gpt-4o-mini"


def _normalize_openai_base_url() -> str:
    """
    LangChain OpenAI 客户端期望 base_url 含 /v1（如 https://api.openai.com/v1）。
    Java WebClient 使用根域名 + 路径 /v1/chat/completions；此处与 OpenAI Python SDK 对齐。
    """
    raw = (os.getenv("OPENAI_BASE_URL") or "https://api.openai.com").strip().rstrip("/")
    if raw.endswith("/v1"):
        return raw
    return raw + "/v1"


llm = ChatOpenAI(
    model=_OPENAI_MODEL,
    temperature=0.2,
    api_key=_openai_api_key(),
    base_url=_normalize_openai_base_url(),
)

JAVA_API_BASE = os.getenv("CAT_RESCUE_API_BASE", "http://127.0.0.1:8090").rstrip("/")

_HOSPITAL_SEARCH_MODEL = (
    os.getenv("OPENAI_HOSPITAL_SEARCH_MODEL", "gpt-4o-search-preview").strip() or "gpt-4o-search-preview"
)

# ---------------------------------------------------------------------------
# 站长 System Prompt（场景 A/B/C 铁律）
# ---------------------------------------------------------------------------
SYSTEM_PROMPT = """
你是一位充满爱心、务实且经验丰富的流浪猫救助站站长。你在通过小程序后台与社区志愿者对话。

【铁律：严禁死板填表】
绝对不要无差别套用相同的报告模板！严禁输出类似“是否受伤：无法判断 / 是否需要喂养：无法判断”这种对用户毫无帮助的机械废话。

【请根据用户的输入意图，严格分流处理】

1. 场景 A（日常科普咨询）：
   - 触发条件：用户询问通用科普，如“喂食需要注意什么”、“猫粮怎么选”、“多大可以绝育”。
   - 行为：直接进入“科普专家”角色，用通顺、温暖、有段落感的纯文本直接回答用户的疑问（例如提醒定时定点、注意防水、提供干净饮用水等）。
   - 格式：直接输出纯文本，禁止夹带任何结构化标签。

2. 场景 B（绝育与医疗位置求助）：
   - 触发条件：上下文或当前输入中包含 GPS 经纬度（如 lat, lng），或者用户点击了“附近哪里可以绝育”。
   - 行为：立即触发 `get_nearby_shelters` 工具去查询数据库。
   - 格式：拿到工具返回的 JSON 后，利用高情商将数据包装成温暖的语言（如：“帮您查到附近XX救助站，距离1.2km，电话是...建议您提前预约”），并顺带引导前端渲染推荐卡片。

3. 场景 C（真正的 IoT 异常分析）：
   - 触发条件：系统明确传入了最新的设备报警快照或健康数据。
   - 行为：此时才允许给出专业的【流浪猫行为与健康审计报告】。
""".strip()

SCENE_A_APPEND = """
【当前路由：场景 A — 日常科普】
请只用温暖、有段落感的纯中文（或用户使用的语言）回答，不要输出 JSON、不要输出填表式字段列表。
""".strip()

SCENE_B_APPEND = """
【当前路由：场景 B — 附近绝育/医疗】
下方是 `get_nearby_shelters` 工具返回的 JSON。请用站长口吻总结最近 3 家机构，写明距离与电话，语气务实亲切。
文末可加一句「您也可以点击下方卡片查看详情」。不要输出「无法判断」类填表内容。
""".strip()

SCENE_C_APPEND = """
【当前路由：场景 C — IoT 设备异常】
请根据附带的设备/健康快照，输出一份简洁的【流浪猫行为与健康审计报告】：
- 用 2～4 段说明现状与风险
- 给出 3 条以内可执行建议
- 仅在数据确实不足时说明缺什么传感器读数，禁止编造「无法判断」的空白表格
""".strip()

SCENE_FOLLOWUP_APPEND = """
【当前为多轮追问】
务必优先回答「本轮志愿者输入」里的具体问题；结合「本次照片评估背景」与上方对话历史，不要答非所问。
若用户追问喂食、危险、绝育等，请直接回应其字面意思，不要重复无关的机构列表，除非本轮明确在问附近医院/TNR。
""".strip()

SCENE_FOLLOWUP_APPEND_EN = """
[Multi-turn follow-up]
Answer the volunteer's latest question first. Use the optional photo-assessment context and the conversation history; do not go off-topic.
If they ask about feeding, safety, or neutering, respond directly—do not repeat unrelated shelter lists unless they clearly ask for nearby clinics/TNR.
""".strip()

# 附近机构意图（中英文关键词）
_SHELTER_INTENT = re.compile(
    r"绝育|tnr|治疗|医院|诊所|兽医|机构|附近|手术|疫苗|急诊|救治|"
    r"\bneuter|spay|tnr|vet|clinic|hospital|shelter|rescue|treatment|nearby\b",
    re.IGNORECASE,
)


def _wants_hospital_web_search(msg: str) -> bool:
    """医院/诊所/收容所/「哪里可以…」等 → OpenAI 联网搜索节点（优先于目录型 shelter）。"""
    m = msg.strip()
    if not m:
        return False
    if re.search(r"动物医院|宠物医院|医院|诊所|收容所|救助站", m):
        return True
    if "哪里可以" in m and re.search(r"医|收容|绝育|诊所|tnr|治疗|手术|动物|猫|宠物", m, re.I):
        return True
    if re.search(r"附近.*(医院|诊所|收容|救助)", m):
        return True
    if re.search(r"\b(hospital|clinic|shelter)\b", m, re.I) and re.search(
        r"\b(near|nearby|where|find|local)\b", m, re.I
    ):
        return True
    return False


def _extract_user_location_hint(state: AgentState) -> str:
    """从评估摘要里的「位置」或经纬度拼出检索用地域描述。"""
    ctx = (state.get("assessment_context") or "").strip()
    if ctx:
        for sep in ("位置：", "Location:", "位置:", "Location："):
            if sep in ctx:
                try:
                    idx = ctx.index(sep)
                except ValueError:
                    continue
                line = ctx[idx : idx + 280].split("\n", 1)[0]
                loc = line.split("：", 1)[-1].split(":", 1)[-1].strip()
                if loc:
                    return loc
    lat, lng = state.get("lat"), state.get("lng")
    if lat is not None and lng is not None:
        try:
            if _norm_output_language(state) == "en":
                return f"near lat {float(lat):.5f}, lng {float(lng):.5f}"
            return f"纬度{float(lat):.5f}、经度{float(lng):.5f} 一带"
        except (TypeError, ValueError):
            pass
    if _norm_output_language(state) == "en":
        return "No street address on file; infer city/region from the volunteer question or coordinates."
    return "用户未提供详细文字地址（请根据问题中的地名或常识推断所在城市/区域）"


RouteKind = Literal["education", "shelter", "iot", "hospital_search"]


class AgentState(TypedDict, total=False):
    """LangGraph 共享状态。"""

    user_message: str
    lat: float | None
    lng: float | None
    iot_snapshot: dict[str, Any] | None
    assessment_context: str | None
    chat_history: list[dict[str, str]]
    output_language: Literal["zh", "en"]
    route: RouteKind
    shelter_payload: dict[str, Any] | None
    shelter_cards: list[dict[str, Any]]
    final_text: str
    messages: list


# ---------------------------------------------------------------------------
# 工具：查询附近救助站 / TNR（优先调 Java API，失败时用内置演示数据）
# ---------------------------------------------------------------------------
_FALLBACK_SHELTERS = [
    {
        "id": "sj-acs",
        "name": "San Jose Animal Care Services",
        "address": "2750 Monterey Rd, San Jose, CA 95111",
        "phone": "+1-408-794-7297",
        "distanceKm": None,
        "kind": "rescue",
    },
    {
        "id": "hssv",
        "name": "Humane Society of Silicon Valley",
        "address": "901 Ames Ave, Milpitas, CA 95035",
        "phone": "+1-408-262-2133",
        "distanceKm": None,
        "kind": "rescue",
    },
]


async def _fetch_directory(path: str, lat: float, lng: float, limit: int = 5) -> list[dict[str, Any]]:
    url = f"{JAVA_API_BASE}{path}"
    async with httpx.AsyncClient(timeout=8.0) as client:
        r = await client.get(url, params={"lat": lat, "lng": lng, "limit": limit})
        r.raise_for_status()
        rows = r.json()
    if not isinstance(rows, list):
        return []
    out: list[dict[str, Any]] = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        out.append(
            {
                "id": row.get("id"),
                "name": row.get("name") or row.get("title"),
                "address": row.get("address"),
                "phone": row.get("phone"),
                "distanceKm": row.get("distanceKm") or row.get("distance_km"),
                "website": row.get("website"),
            }
        )
    return out


async def get_nearby_shelters_impl(
    lat: float,
    lng: float,
    *,
    need_tnr: bool = True,
    need_vet: bool = True,
    limit: int = 5,
) -> dict[str, Any]:
    """供 LangGraph 节点与 @tool 共用的实现。"""
    merged: list[dict[str, Any]] = []
    errors: list[str] = []
    if need_tnr:
        try:
            for row in await _fetch_directory("/api/v1/tnr-directory/nearest", lat, lng, limit):
                row["kind"] = "tnr"
                merged.append(row)
        except Exception as exc:  # noqa: BLE001
            errors.append(f"tnr-directory: {exc}")
    if need_vet:
        try:
            for row in await _fetch_directory("/api/v1/rescue-directory/nearest", lat, lng, limit):
                row["kind"] = "rescue"
                merged.append(row)
        except Exception as exc:  # noqa: BLE001
            errors.append(f"rescue-directory: {exc}")

    # 去重 + 按距离排序
    seen: set[str] = set()
    unique: list[dict[str, Any]] = []
    for row in merged:
        key = str(row.get("id") or row.get("name") or "")
        if key in seen:
            continue
        seen.add(key)
        unique.append(row)

    unique.sort(key=lambda x: float(x.get("distanceKm") or 9999.0))
    if not unique:
        unique = [dict(x) for x in _FALLBACK_SHELTERS]
        for row in unique:
            row["note"] = "demo_fallback"

    cards = [
        {
            "id": row.get("id"),
            "title": row.get("name"),
            "subtitle": row.get("address"),
            "phone": row.get("phone"),
            "distanceKm": row.get("distanceKm"),
            "kind": row.get("kind", "shelter"),
        }
        for row in unique[:limit]
    ]
    return {
        "lat": lat,
        "lng": lng,
        "need_tnr": need_tnr,
        "need_vet": need_vet,
        "organizations": unique[:limit],
        "cards": cards,
        "errors": errors,
    }


@tool
async def get_nearby_shelters(
    lat: float,
    lng: float,
    need_tnr: bool = True,
    need_vet: bool = True,
) -> str:
    """查询用户 GPS 附近的绝育（TNR）与救助/医疗资源，返回 JSON 字符串。"""
    payload = await get_nearby_shelters_impl(lat, lng, need_tnr=need_tnr, need_vet=need_vet)
    return json.dumps(payload, ensure_ascii=False)


# ---------------------------------------------------------------------------
# 条件路由：决定走场景 A / B / C
# ---------------------------------------------------------------------------
def classify_route(state: AgentState) -> RouteKind:
    """
    路由优先级：
    1. 有 iot_snapshot → 场景 C
    2. 医院/诊所/收容所/「哪里可以…」等 → hospital_search（OpenAI 联网搜索）
    3. 附近机构目录（Java）→ 场景 B
    4. 其余 → 场景 A
    """
    snap = state.get("iot_snapshot")
    if snap and isinstance(snap, dict) and len(snap) > 0:
        return "iot"

    msg = (state.get("user_message") or "").replace("[nearby_shelters]", "").strip()

    if _wants_hospital_web_search(msg):
        return "hospital_search"

    if "[nearby_shelters]" in (state.get("user_message") or "") or _SHELTER_INTENT.search(msg):
        # 有求助意图或前端按钮标记；无坐标时由 shelter_fetch 提示补定位
        return "shelter"

    return "education"


def _route_edge(state: AgentState) -> str:
    """conditional_edges 的目标节点名。"""
    kind = state.get("route") or classify_route(state)
    return {
        "education": "education_reply",
        "shelter": "shelter_fetch",
        "iot": "iot_audit",
        "hospital_search": "hospital_search",
    }[kind]


async def classify_node(state: AgentState) -> dict[str, Any]:
    route = classify_route(state)
    return {"route": route}


def _chunk_content(chunk: Any) -> str:
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


async def _stream_llm_to_text(messages: list, config: RunnableConfig) -> str:
    """使用 astream，便于上层 graph.astream_events 捕获 on_chat_model_stream。"""
    parts: list[str] = []
    async for chunk in llm.astream(messages, config=config):
        parts.append(_chunk_content(chunk))
    return "".join(parts)


def _history_to_lc_messages(history: list[dict[str, str]] | None) -> list:
    """将前端传来的多轮对话转为 LangChain 消息（仅保留最近若干轮）。"""
    out: list = []
    if not history:
        return out
    for turn in history[-10:]:
        role = (turn.get("role") or "").strip()
        content = (turn.get("content") or "").strip()
        if not content:
            continue
        if role == "user":
            out.append(HumanMessage(content=content))
        elif role == "assistant":
            out.append(AIMessage(content=content))
    return out


def _norm_output_language(state: AgentState) -> Literal["zh", "en"]:
    raw = (state.get("output_language") or "zh").strip().lower()
    if raw.startswith("en"):
        return "en"
    return "zh"


def _system_language_tail(state: AgentState) -> str:
    """按界面语言约束模型输出语言。"""
    if _norm_output_language(state) == "en":
        return "\n\nReply in English only."
    return "\n\n用中文回复。"


def _compose_human_prompt(state: AgentState) -> str:
    """把本轮输入、评估背景拼成一条 Human 消息，避免模型丢失上下文。"""
    raw = (state.get("user_message") or "").replace("[nearby_shelters]", "").strip()
    ctx = (state.get("assessment_context") or "").strip()
    if _norm_output_language(state) == "en":
        blocks = [f"[Volunteer message — this turn]\n{raw}"]
        if ctx:
            blocks.append(f"[Photo assessment context — optional]\n{ctx}")
        return "\n\n".join(blocks)
    blocks = [f"【本轮志愿者输入】\n{raw}"]
    if ctx:
        blocks.append(f"【本次照片评估背景】\n{ctx}")
    return "\n\n".join(blocks)


def _build_scene_messages(state: AgentState, scene_append: str) -> list:
    follow = bool(state.get("chat_history"))
    system = SYSTEM_PROMPT + "\n\n" + scene_append + _system_language_tail(state)
    if follow:
        system += "\n\n" + (
            SCENE_FOLLOWUP_APPEND_EN if _norm_output_language(state) == "en" else SCENE_FOLLOWUP_APPEND
        )
    messages: list = [SystemMessage(content=system)]
    messages.extend(_history_to_lc_messages(state.get("chat_history")))
    messages.append(HumanMessage(content=_compose_human_prompt(state)))
    return messages


async def education_reply_node(state: AgentState, config: RunnableConfig) -> dict[str, Any]:
    messages = _build_scene_messages(state, SCENE_A_APPEND)
    text = await _stream_llm_to_text(messages, config)
    return {"final_text": text, "shelter_cards": []}


async def hospital_search_node(state: AgentState) -> dict[str, Any]:
    """
    使用 OpenAI Responses 风格的联网搜索模型（gpt-4o-search-preview），
    结合用户地域描述检索附近动物医院 / 诊所 / 收容所。
    """
    from openai import AsyncOpenAI

    loc = _extract_user_location_hint(state)
    user_q = (state.get("user_message") or "").replace("[nearby_shelters]", "").strip()
    lang = _norm_output_language(state)
    if lang == "en":
        prompt = (
            f"Use web search to find cat-friendly animal hospitals, licensed pet clinics/vets, "
            f"and animal shelters or rescues near: {loc}.\n"
            "Output: a bullet list; each item must include — name, full address, phone "
            '(or "phone not published" if unavailable).\n'
            "Aim for 3–6 helpful results for a community cat rescuer. If results are thin, explain briefly and suggest how to search further.\n\n"
            f"Volunteer question: {user_q}"
            f"{_system_language_tail(state)}"
        )
    else:
        prompt = (
            f"请联网搜索【{loc}】附近的猫咪动物医院、正规宠物医院或诊所、以及动物收容所或救助机构。\n"
            "输出要求：按条目列出，每条写清楚——名称、详细地址、联系电话（若公开渠道查不到则写「电话未公开」）。\n"
            "尽量给出 3～6 条对救助志愿者有用的结果；若检索结果不足，请说明原因并给出如何继续查找的简要建议。\n\n"
            f"用户原话：{user_q}"
            f"{_system_language_tail(state)}"
        )
    client = AsyncOpenAI(api_key=_openai_api_key(), base_url=_normalize_openai_base_url())
    resp = await client.chat.completions.create(
        model=_HOSPITAL_SEARCH_MODEL,
        messages=[{"role": "user", "content": prompt}],
    )
    choice = resp.choices[0].message
    raw = (choice.content or "").strip()
    if _norm_output_language(state) == "en":
        text = raw if raw else "(No search results returned; retry later or verify model access.)"
    else:
        text = raw if raw else "（未返回有效搜索结果，请稍后重试或检查账号是否开通该模型权限。）"
    return {"final_text": text, "shelter_cards": []}


async def shelter_fetch_node(state: AgentState) -> dict[str, Any]:
    lat, lng = state.get("lat"), state.get("lng")
    if lat is None or lng is None:
        payload = {
            "error": "missing_coordinates",
            "hint": "请先在表单中开启定位或填写地址，以便查询附近机构。",
            "organizations": [],
            "cards": [],
        }
        return {"shelter_payload": payload, "shelter_cards": []}

    payload = await get_nearby_shelters_impl(float(lat), float(lng))
    return {
        "shelter_payload": payload,
        "shelter_cards": payload.get("cards") or [],
    }


async def shelter_reply_node(state: AgentState, config: RunnableConfig) -> dict[str, Any]:
    tool_json = json.dumps(state.get("shelter_payload") or {}, ensure_ascii=False, indent=2)
    messages = _build_scene_messages(state, SCENE_B_APPEND)
    if _norm_output_language(state) == "en":
        tool_human = (
            f"【Nearby directory JSON】\n{tool_json}\n"
            "Summarize for the volunteer in English using the data above (distances, phones, addresses)."
        )
    else:
        tool_human = (
            f"【附近机构工具 JSON】\n{tool_json}\n请据此回答本轮志愿者关于附近绝育/治疗/机构的问题。"
        )
    messages.append(HumanMessage(content=tool_human))
    text = await _stream_llm_to_text(messages, config)
    return {"final_text": text}


async def iot_audit_node(state: AgentState, config: RunnableConfig) -> dict[str, Any]:
    snap = state.get("iot_snapshot") or {}
    snap_text = json.dumps(snap, ensure_ascii=False, indent=2)
    messages = _build_scene_messages(state, SCENE_C_APPEND)
    if _norm_output_language(state) == "en":
        messages.append(HumanMessage(content=f"【Device/health snapshot JSON】\n{snap_text}"))
    else:
        messages.append(HumanMessage(content=f"【设备/健康快照 JSON】\n{snap_text}"))
    text = await _stream_llm_to_text(messages, config)
    return {"final_text": text, "shelter_cards": []}


def build_station_master_graph():
    """编译站长 LangGraph（入口 → 分类 → 三场景之一 → END）。"""
    g = StateGraph(AgentState)
    g.add_node("classify", classify_node)
    g.add_node("education_reply", education_reply_node)
    g.add_node("shelter_fetch", shelter_fetch_node)
    g.add_node("shelter_reply", shelter_reply_node)
    g.add_node("iot_audit", iot_audit_node)
    g.add_node("hospital_search", hospital_search_node)

    g.add_edge(START, "classify")
    g.add_conditional_edges("classify", _route_edge)
    g.add_edge("education_reply", END)
    g.add_edge("shelter_fetch", "shelter_reply")
    g.add_edge("shelter_reply", END)
    g.add_edge("iot_audit", END)
    g.add_edge("hospital_search", END)
    return g.compile()


# 单例图，供 FastAPI 复用
station_master_graph = build_station_master_graph()


def initial_state(
    message: str,
    *,
    lat: float | None = None,
    lng: float | None = None,
    iot_snapshot: dict[str, Any] | None = None,
    assessment_context: str | None = None,
    chat_history: list[dict[str, str]] | None = None,
    output_language: str | None = None,
) -> AgentState:
    ol = (output_language or "zh").strip().lower()
    lang: Literal["zh", "en"] = "en" if ol.startswith("en") else "zh"
    return {
        "user_message": message,
        "lat": lat,
        "lng": lng,
        "iot_snapshot": iot_snapshot,
        "assessment_context": assessment_context,
        "chat_history": list(chat_history or []),
        "output_language": lang,
        "messages": [],
        "shelter_cards": [],
    }
