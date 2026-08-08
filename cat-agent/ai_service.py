"""
LangGraph 站长 Agent：OpenAI 底座 + 与 Demo 脚本对齐的四类能力路由。

- 工具调用（真实数据）→ Spring Boot REST，对应概念名如 get_cat_count_nearby / get_feeders_nearby 等
- 联网搜索 → OPENAI_HOSPITAL_SEARCH_MODEL（默认 gpt-4o-mini-search-preview）；可选后续接 Tavily
- 知识库 RAG → cat-agent/knowledge/*.md 轻量摘录注入「科普」节点
- 多模态照片分析 → 仍由 Java 评估管线完成；对话仅消费 assessment_context

环境变量：OPENAI_API_KEY（或 CAT_RESCUE_OPENAI_API_KEY）、OPENAI_MODEL、OPENAI_BASE_URL、
CAT_RESCUE_API_BASE、OPENAI_HOSPITAL_SEARCH_MODEL（联网搜索用）
"""

from __future__ import annotations

import copy
import json
import math
import os
import re
from pathlib import Path
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
            "Missing LLM API credential (set OPENAI_API_KEY or CAT_RESCUE_OPENAI_API_KEY). "
            "Use the same value as the Java backend; do not commit secrets."
        )
    lower = key.lower()
    if any(m in lower for m in _PLACEHOLDER_KEY_MARKERS) or key.startswith("replace_"):
        raise RuntimeError(
            "LLM API credential looks like a placeholder from .env.example. "
            "Edit .env.local with a real key, then: set -a && source .env.local && set +a && restart uvicorn."
        )
    if not key.startswith("sk-") and len(key) < 20:
        raise RuntimeError(
            "LLM API credential does not look valid for this OpenAI-compatible client. "
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

API_BASE = os.getenv("CAT_RESCUE_API_BASE", "http://127.0.0.1:8090").rstrip("/")

_HOSPITAL_SEARCH_MODEL = (
    os.getenv("OPENAI_HOSPITAL_SEARCH_MODEL", "gpt-4o-mini-search-preview").strip() or "gpt-4o-mini-search-preview"
)

_KNOWLEDGE_DIR = Path(__file__).resolve().parent / "knowledge"
_KNOWLEDGE_CHUNKS: list[tuple[str, str]] | None = None


def _load_knowledge_chunks() -> list[tuple[str, str]]:
    """加载 knowledge/*.md，用于科普路由的轻量 RAG（无向量库）。"""
    global _KNOWLEDGE_CHUNKS
    if _KNOWLEDGE_CHUNKS is not None:
        return _KNOWLEDGE_CHUNKS
    out: list[tuple[str, str]] = []
    if _KNOWLEDGE_DIR.is_dir():
        for p in sorted(_KNOWLEDGE_DIR.glob("*.md")):
            try:
                txt = p.read_text(encoding="utf-8").strip()
                if txt:
                    out.append((p.stem, txt))
            except OSError:
                pass
    _KNOWLEDGE_CHUNKS = out
    return out


def _rag_snippets_for_message(msg: str, *, max_chars: int = 2600) -> str:
    """按关键词与字符重叠从本地 md 中选最多 3 段摘录。"""
    m = (msg or "").strip().lower()
    if len(m) < 2:
        return ""
    chunks = _load_knowledge_chunks()
    if not chunks:
        return ""
    keywords = (
        "tnr",
        "绝育",
        "喂养",
        "喂食",
        "猫粮",
        "口炎",
        "猫瘟",
        "驱虫",
        "疫苗",
        "耳标",
        "剪耳",
        "放归",
        "诱捕",
    )
    scored: list[tuple[int, str, str]] = []
    msg_han = [c for c in m if "\u4e00" <= c <= "\u9fff"]
    for name, body in chunks:
        bl = body.lower()
        score = sum(3 for kw in keywords if kw in m and kw in bl)
        score += sum(1 for c in msg_han if c in bl)
        if score > 0:
            scored.append((score, name, body))
    scored.sort(key=lambda x: -x[0])
    parts: list[str] = []
    used = 0
    for _, name, body in scored[:3]:
        block = f"## [{name}]\n{body}\n"
        if used + len(block) > max_chars:
            block = block[: max(0, max_chars - used - 8)].rstrip() + "\n…\n"
        parts.append(block)
        used += len(block)
        if used >= max_chars:
            break
    return "\n".join(parts).strip()


_CONCEPT_TOOL_BY_INTENT: dict[str, str] = {
    "platform_stats": "get_platform_stats",
    "feeding_checkin": "checkin_feeding",
    "cats_nearby_count": "get_cat_count_nearby",
    "feeding_stations": "get_feeders_nearby",
    "tnr_directory": "find_rescue_center",
    "rescue_directory": "find_rescue_center",
    "assessments_list": "query_assessments_nearby",
    "assessment_ear": "get_assessment_detail",
    "assessment_reporter": "get_assessment_detail",
    "cat_heatmap_sightings": "get_cat_sightings_timeline",
    "assessment_duplicate_match": "get_same_cat_cluster_timeline",
    "report_cat": "report_cat",
    "unknown": "unknown",
}

# ---------------------------------------------------------------------------
# 站长 System Prompt（与 docs/agent_demo.md 四类能力对齐）
# ---------------------------------------------------------------------------
SYSTEM_PROMPT = """
你是一位充满爱心、务实且经验丰富的流浪猫救助站站长。你在通过小程序后台与社区志愿者对话；**唯一主交互是对话框**（地图由前端同步，你不必描述 UI 操作）。

【四类能力（路由已由系统分流，你只需按当前场景附录写回复）】
1. **工具 / 真实数据**：附近猫数量、喂食器、救助与 TNR 目录、打卡、平台统计等——**只使用本轮附带的 JSON**，禁止编造条数、距离、电话。
2. **联网搜索**：医院/收容所检索或开放域科普——基于检索结果回答，信息不足时说明不确定。
3. **知识库 RAG**：若系统附录中嵌入了「知识库摘录」，请自然融入回答，勿机械罗列文件名。
4. **多模态**：照片结论在「照片评估背景」里；你在此基础上给行动建议，勿重复填表式字段列表。

【铁律：严禁死板填表】
不要输出「是否受伤：无法判断 / 是否需要喂养：无法判断」等对用户无帮助的机械废话。

【语气：简洁、少客套】
短段落、直击问题；禁止每条末尾重复同一句感谢语（尤其「感谢您对流浪猫的关心与支持」类套话）。

【历史场景名称（附录里仍会写「场景 A/B/C」）】
- 场景 A：日常科普（可含知识库摘录）。
- 场景 B：附近机构目录 JSON（对应 Demo 中的 find_rescue_center / 机构卡片数据源）。
- 场景 C：IoT 设备与健康快照审计。
""".strip()

SCENE_A_APPEND = """
【当前路由：场景 A — 日常科普】
请只用温暖、有段落感的纯中文（或用户使用的语言）回答，不要输出 JSON、不要输出填表式字段列表。
若有「知识库摘录」，提炼要点融入叙述；篇幅尽量克制；结尾不要堆砌感谢语。
""".strip()

SCENE_B_APPEND = """
【当前路由：场景 B — 附近机构（概念工具 find_rescue_center / get_nearby_shelters）】
下方 JSON 来自 Java 目录接口合并结果（TNR + 救助）。请用站长口吻总结最近 3 家，写明距离与电话，语气务实亲切。
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
若背景中含【同猫识别】或【社区追踪】，用户问是否之前见过、是否同一只猫、有无历史记录时，**必须依据其中的时间线与档案 ID 回答**，勿说「系统暂无记录」。
若用户追问喂食、危险、绝育等，请直接回应其字面意思，不要重复无关的机构列表，除非本轮明确在问附近医院/TNR。
""".strip()

SCENE_FOLLOWUP_APPEND_EN = """
[Multi-turn follow-up]
Answer the volunteer's latest question first. Use the optional photo-assessment context and the conversation history; do not go off-topic.
If the context includes [Same-cat match] or [Community tracking], and they ask whether this cat was seen before or is the same individual, **answer from that timeline and archive IDs**—do not claim there is no record.
If they ask about feeding, safety, or neutering, respond directly—do not repeat unrelated shelter lists unless they clearly ask for nearby clinics/TNR.
""".strip()

SCENE_DB_LOOKUP_APPEND = """
【当前路由：平台数据库检索（概念工具名见 JSON 字段 concept_tool，与 Demo 脚本一致）】
下方 JSON 来自 Java 后端真实响应（字段 intent 标明本次分支：platform_stats | feeding_checkin | cats_nearby_count | feeding_stations | tnr_directory / rescue_directory / assessments_list / assessment_detail | cat_heatmap_sightings | assessment_duplicate_match | report_cat）。
- report_cat：无 REST 写接口；JSON 含 workflow 提示，请引导用户用「上传/分析」提交照片与定位完成上报。
- platform_stats：GET /api/v1/stats/platform 全区汇总；含 totalAssessments、sightingsLast30Days、distinctCatsWithSightings、assessmentsWithGeo、assessmentWithGeoRatio、todayScheduledStationDispenses。用户问「本周成果」时用这些数字做**汇总**，不要只复述单条猫咪档案。
- tnr_directory / rescue_directory：nearest 接口返回的机构列表。
- assessments_list：已按 GPS 筛选；data 为按时间最近的若干条（至多 3 条写入 data）。字段 records_total 为匹配总数；records_omitted 为未展开条数（0 表示已全部展示）。
- assessment_detail：GET /assessments/{id} 单条评估。绝育/耳标仅依据 modelLabels（如 earTipDetected、earTipConfidencePercent、likelyAlreadySterilized、captureAdvice）；禁止发明不存在的字段名。
- cats_nearby_count：GET /api/v1/cats/lookup 半径内匹配档案；`match_count` 为条数，`matches_sample` 为摘要样本（含 catId、距离、剪耳等）。
- feeding_stations：GET /api/v1/feeding-stations；若有 GPS 会附 `distanceMeters` 并按距离排序。
- feeding_checkin：POST /api/v1/volunteers/me/feeding-checkins 成功后的 JSON（需 volunteer_user_id）；失败见 error。
- cat_heatmap_sightings：`data` 为 GET /api/v1/cats/{catId}/heatmap/sightings（近 window_days 天、与热力图一致的加权目击）；`cat_history` 为 GET /api/v1/cats/{catId}/history?limit=50，含 `recentSightings` 每条的时间、坐标与 **addressText**（与档案页「出现记录时间线」同源的可读地址）。回答「几次、在哪些地方」时优先用 `cat_history.recentSightings` 复述地点与时间；`data` 用于补充热力图权重、去重状态、缩略图 imageUrl 等。若 `sightings_total` 与 `timeline_sightings_count` 不一致，说明热力图窗口内过滤更严，向用户简要说明即可。
- assessment_duplicate_match：`data` 为上传后页面传入的同猫识别簇（matchedAssessmentId、similarityScore、clusterSightings 时间线）。用户问「之前见过吗 / 同一只猫吗」时直接据此回答出现次数、时间与地点；可结合 trackingMatch 中的 suggestedCatId / dedupStatus。
- 若用户问志愿者/跟进人：公开 JSON 不含 reporterUserId；仅可复述 trackingMatch 内已有字段（如 sightingId、catId、dedupStatus）。若无可用信息，如实说明，禁止编造姓名或联系方式。
【回复格式】像站长口头说明一样用自然段落（可 1～3 段），不要用「第1/2/3条」「猫咪档案1、2、3」等编号列表。若需提及图片，只使用 JSON 里给出的完整 http(s) 链接（已是绝对地址），勿改回相对路径。
仅当 JSON 中 **records_omitted 存在且为大于 0 的整数** 时，结尾才用一句自然话说明另有若干条未展开；若该字段为 0、缺省或不存在，**禁止**写「另外还有」「未展示」等与条数相关的收尾。
若 JSON 中 empty 为 true，本轮已由系统固定回复，你不会收到本条（防重复）。
其他情况：温暖、务实；禁止编造 JSON 外的数据。若含 error / hint_zh，照实说明原因。
""".strip()

SCENE_DB_LOOKUP_APPEND_EN = """
[Route: platform DB lookup — see `concept_tool` in JSON for the demo script tool name]
The JSON is the real Java API response (see intent: platform_stats | feeding_checkin | cats_nearby_count | feeding_stations | tnr_directory / rescue_directory / assessments_list / assessment_detail | cat_heatmap_sightings | assessment_duplicate_match | report_cat).
- report_cat: no write API; JSON only has workflow hints—tell the user to use Upload / Analyze with photo + location.
- platform_stats: GET /stats/platform — totals: totalAssessments, sightingsLast30Days, distinctCatsWithSightings, assessmentsWithGeo, assessmentWithGeoRatio, todayScheduledStationDispenses. For “weekly summary / dashboard”, give an aggregate overview, not a single-cat story.
- feeding_checkin: POST /volunteers/me/feeding-checkins result or error (needs volunteer_user_id).
- cats_nearby_count: GET /cats/lookup summary; `match_count` and optional `matches_sample`.
- feeding_stations: GET /feeding-stations list; distances if GPS present.
- Directories: nearest organizations list.
- assessments_list: GPS-filtered; `data` holds up to the 3 most recent matches. `records_total` is the full match count; `records_omitted` is how many are not expanded (0 means all shown).
- assessment_detail: single assessment. Neuter/ear-tip only from modelLabels (earTipDetected, earTipConfidencePercent, likelyAlreadySterilized, captureAdvice); do not invent field names.
- cat_heatmap_sightings: `data` = GET /api/v1/cats/{catId}/heatmap/sightings (last `window_days`, same weighting as the UI heatmap). `cat_history` = GET /api/v1/cats/{catId}/history?limit=50 with `recentSightings` entries including **addressText** (human-readable locations like the profile timeline). For “how many times / where”, prefer summarizing `cat_history.recentSightings` (time + address); use `data` for weights, dedup flags, and `imageUrl`. If `sightings_total` differs from `timeline_sightings_count`, the heatmap window/filter is stricter—say that briefly.
- assessment_duplicate_match: `data` is the same-cat cluster from the upload page (matchedAssessmentId, similarityScore, clusterSightings timeline). For “seen before / same cat?” answer from this timeline; you may mention suggestedCatId / dedupStatus from trackingMatch in assessment context.
- Volunteer/follow-up: there is no reporterUserId in the public payload; only summarize trackingMatch fields if present; otherwise say it is not returned—do not invent identities.
[Reply format] Write as natural spoken prose (1–3 short paragraphs). Do not use numbered lists like "record 1/2/3" or "archive #1, #2". For images, only use the full http(s) URLs already present in JSON—never shorten to relative paths.
Only if `records_omitted` exists and is an integer **greater than 0**, add one short sentence that more records exist; if it is 0, missing, or absent, **do not** mention "additional records" / "N more rows".
If empty is true, a fixed reply was already sent upstream.
Otherwise summarize faithfully; do not invent data beyond JSON. Explain error/hint_en if present.
""".strip()

# 附近机构目录（Java nearest）— 收窄触发条件，避免与「猫瘟怎么治疗」等开放域问题抢路由
_SHELTER_INTENT = re.compile(
    r"(附近|就近|最近|周边|离我|离我近|closest|near me|nearby|where\s+(is|are).{0,16}(hospital|clinic|vet|shelter|tnr|rescue))"
    r".{0,48}(医院|诊所|收容|救助|绝育|tnr|兽医|vet|clinic|hospital|shelter)|"
    r"(医院|诊所|收容|救助).{0,16}(在哪|哪里|几家|一个|推荐|电话|地址|离我)|"
    r"\[nearby_shelters\]",
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


def _wants_web_search(msg: str) -> bool:
    """
    联网搜索：附近医院/收容所检索，或开放域流浪猫科普（与数据库工具互斥由 classify 顺序保证）。
    实现：OPENAI_HOSPITAL_SEARCH_MODEL（默认带联网能力的 search 类模型）；可后续换 Tavily。
    """
    m = (msg or "").strip()
    if not m:
        return False
    if _wants_hospital_web_search(m):
        return True
    if re.search(
        r"怎么(办|处理|治|养)|如何(治|处理|养)|是什么病|什么症状|会传染吗|要注意什么|科普一下|口炎|猫瘟|传腹|FIP|鼻支|驱虫药|疫苗.{0,6}打",
        m,
        re.I,
    ):
        return True
    if re.search(
        r"\b(how to|what is|symptoms?|deworm|vaccine schedule|FIP|URI|ringworm)\b",
        m,
        re.I,
    ):
        return True
    return False


def _web_search_is_local_facilities(msg: str) -> bool:
    """偏「查附近医院/收容所/电话地址」的联网子类型（提示模型优先列机构）。"""
    return bool(_wants_hospital_web_search(msg)) or bool(
        re.search(r"附近.{0,20}(动物医院|宠物医院|医院|诊所|收容|救助|tnr|绝育)", msg, re.I)
    )


def _state_has_assessment_duplicate(state: AgentState | None) -> bool:
    if not state:
        return False
    dup = state.get("assessment_duplicate")
    if not isinstance(dup, dict):
        return False
    if dup.get("matchedAssessmentId") is None:
        return False
    rows = dup.get("clusterSightings")
    return isinstance(rows, list) and len(rows) > 0


def _same_cat_history_question(msg: str) -> bool:
    m = (msg or "").strip()
    if not m:
        return False
    return bool(
        re.search(
            r"同一只猫|同只猫|疑似同猫|疑似.{0,8}同|之前出现过|之前有没有|曾经见过|有没有见过|"
            r"有.{0,8}记录|历史记录|见过吗|有记录吗|之前有记录|喂过吗|有档案吗|这只猫是谁的|"
            r"same\s+cat|seen\s+before|prior\s+record",
            m,
            re.I,
        )
    )


def _db_lookup_kind(msg: str, state: AgentState | None = None) -> str | None:
    """
    数据库检索意图（与 classify_route 中 db_lookup 分支一致）。
    返回：platform_stats | report_cat | feeding_checkin | cats_nearby_count | feeding_stations | …
    """
    m = (msg or "").strip()
    if not m:
        return None
    if re.search(
        r"本周成果|本周数据|本周汇总|本周的.{0,8}(成绩|数据|进展)|平台统计|全区汇总|总体数据|社区数据|运营数据|大盘|KPI",
        m,
    ) or re.search(
        r"\b(weekly|this week|platform)\b.{0,24}\b(stats|summary|numbers|results|progress)\b",
        m,
        re.I,
    ) or re.search(r"\b(platform stats|dashboard totals)\b", m, re.I):
        return "platform_stats"
    if re.search(
        r"上报.{0,12}(新猫|流浪猫)|登记.{0,8}新猫|新猫.{0,8}(档案|记录)|report\s+(a\s+)?(stray\s*)?cat",
        m,
        re.I,
    ):
        return "report_cat"
    if re.search(r"打卡|喂完了|喂养打卡|feeding\s*check|check\s*-?in", m, re.I):
        return "feeding_checkin"
    if re.search(
        r"附近.{0,14}(多少|几).{0,6}猫|多少只猫|几只猫|几只流浪|流浪猫.{0,10}多少|猫咪.{0,6}数量",
        m,
    ) or re.search(r"\bhow many\b.{0,24}\b(cat|cats|strays)\b", m, re.I):
        return "cats_nearby_count"
    if re.search(r"喂食器|喂养点|喂猫点|猫粮机|feeder|feeding\s*station", m, re.I):
        return "feeding_stations"
    if re.search(r"志愿者是谁|谁在跟进|谁在负责|跟进人|负责人是谁", m):
        return "assessment_reporter"
    # 同一只猫在热力图/目击记录中的出现次数与地点（需 focus_cat_id）
    if re.search(
        r"热力图|活动轨迹|出没地点|都在哪儿|都在哪里|在哪些位置|哪些地点",
        m,
    ):
        return "cat_heatmap_sightings"
    if re.search(r"(出现|露面|目击).{0,10}(几次|几回|多少次|多少回|多少遍|频次|频率)", m):
        return "cat_heatmap_sightings"
    if re.search(r"(几次|多少回|多少次).{0,10}(出现|露面|目击|来|看|见)", m):
        return "cat_heatmap_sightings"
    if re.search(r"(这只|该)猫.{0,16}(几次|多少回|多少次)", m) and re.search(
        r"出现|露面|目击|来|看|见|记录|最近|热力图|轨迹",
        m,
    ):
        return "cat_heatmap_sightings"
    if re.search(
        r"\b(how many times)\b.{0,40}\b(seen|appearance|spotted|sighting)\b",
        m,
        re.I,
    ) or re.search(r"\b(sighting|sightings|heatmap)\b.{0,40}\b(how many|count)\b", m, re.I):
        return "cat_heatmap_sightings"
    if re.search(
        r"TNR在哪|附近哪里可以绝育|附近哪.{0,4}绝育|哪(里|儿)可以绝育|哪做绝育|绝育机构|tnr机构",
        m,
        re.IGNORECASE,
    ):
        return "tnr_directory"
    if re.search(r"附近救助|救助机构|送养|哪(里|儿)可以送养|领养.{0,6}哪", m):
        return "rescue_directory"
    if _same_cat_history_question(m):
        if _state_has_assessment_duplicate(state):
            return "assessment_duplicate_match"
        cid = state.get("focus_cat_id") if state else None
        if cid is not None:
            return "cat_heatmap_sightings"
    if re.search(
        r"见过吗|有记录吗|之前有记录|喂过吗|有档案吗|这只猫是谁的|"
        r"之前出现过|之前有没有|曾经见过|有没有见过|同一只猫|疑似同猫",
        m,
    ):
        return "assessments_list"
    if re.search(
        r"绝育过吗|TNR了吗|耳尖|剪耳|tnr了|已经tnr|做过绝育",
        m,
        re.IGNORECASE,
    ):
        return "assessment_ear"
    return None


def _wants_db_lookup(msg: str, state: AgentState | None = None) -> bool:
    return _db_lookup_kind(msg, state) is not None


_UUID_RE = re.compile(
    r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b"
)


def _parse_assessment_uuid(text: str) -> str | None:
    m = _UUID_RE.search(text or "")
    return m.group(0) if m else None


def _parse_lat_lng_from_message(text: str) -> tuple[float | None, float | None]:
    """从用户话术中尽量解析经纬度（补充 state 中未传的坐标）。"""
    t = text or ""
    lat_m = re.search(r"(?:lat|纬度|latitude)\s*[:：=]?\s*(-?\d+(?:\.\d+)?)", t, re.I)
    lng_m = re.search(
        r"(?:lng|lon|经度|longitude)\s*[:：=]?\s*(-?\d+(?:\.\d+)?)",
        t,
        re.I,
    )
    if lat_m and lng_m:
        try:
            return float(lat_m.group(1)), float(lng_m.group(1))
        except ValueError:
            pass
    pair = re.search(
        r"(-?\d{1,2}\.\d{4,})\s*[,，]\s*(-?\d{1,3}\.\d{4,})",
        t,
    )
    if pair:
        try:
            return float(pair.group(1)), float(pair.group(2))
        except ValueError:
            pass
    return None, None


def _coords_for_db_lookup(state: AgentState, message: str) -> tuple[float | None, float | None]:
    la, ln = state.get("lat"), state.get("lng")
    if la is not None and ln is not None:
        try:
            return float(la), float(ln)
        except (TypeError, ValueError):
            pass
    return _parse_lat_lng_from_message(message)


def _haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlmb = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlmb / 2) ** 2
    return 2 * r * math.asin(min(1.0, math.sqrt(a)))


def _expand_assessment_relative_urls(value: Any, api_base: str) -> Any:
    """将 /api/v1/assessments/... 相对路径补成完整 URL（供回复中引用图片等）。"""
    root = api_base.rstrip("/")
    if isinstance(value, str):
        s = value.strip()
        if s.startswith("/api/v1/assessments/"):
            return f"{root}{s}"
        return value
    if isinstance(value, list):
        return [_expand_assessment_relative_urls(x, api_base) for x in value]
    if isinstance(value, dict):
        return {k: _expand_assessment_relative_urls(v, api_base) for k, v in value.items()}
    return value


def _prepare_db_lookup_llm_payload(payload: dict[str, Any]) -> dict[str, Any]:
    """
    供 LLM 摘要用：展开评估图片相对路径；assessments_list 仅保留最近 3 条并标注省略数量。
    """
    out = copy.deepcopy(payload)
    out = _expand_assessment_relative_urls(out, API_BASE)

    if out.get("intent") == "assessments_list" and isinstance(out.get("data"), list):
        rows = [r for r in out["data"] if isinstance(r, dict)]

        def _created_at_key(row: dict[str, Any]) -> str:
            return str(row.get("createdAt") or row.get("created_at") or "")

        rows_sorted = sorted(rows, key=_created_at_key, reverse=True)
        total = len(rows_sorted)
        out["records_total"] = total
        if total > 3:
            out["data"] = rows_sorted[:3]
            out["records_omitted"] = total - 3
        else:
            out["data"] = rows_sorted
            out["records_omitted"] = 0

    if out.get("intent") == "cat_heatmap_sightings" and isinstance(out.get("data"), list):
        root = API_BASE.rstrip("/")
        slim: list[dict[str, Any]] = []
        for row in out["data"]:
            if not isinstance(row, dict):
                continue
            cp = dict(row)
            iu = cp.get("imageUrl")
            if isinstance(iu, str) and iu.startswith("/api/"):
                cp["imageUrl"] = f"{root}{iu}"
            slim.append(cp)
        out["data"] = slim

    if out.get("intent") == "assessment_duplicate_match" and isinstance(out.get("data"), dict):
        out["data"] = _expand_assessment_relative_urls(out["data"], API_BASE)

    try:
        ro = out.get("records_omitted")
        if ro is not None and int(ro) == 0:
            out.pop("records_omitted", None)
    except (TypeError, ValueError):
        pass

    return out


def _filter_assessments_by_gps(
    rows: list[Any],
    lat: float,
    lng: float,
    *,
    radius_m: float = 150.0,
    limit: int = 8,
) -> list[dict[str, Any]]:
    scored: list[tuple[float, dict[str, Any]]] = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        rlat, rlng = row.get("latitude"), row.get("longitude")
        if rlat is None or rlng is None:
            continue
        try:
            d = _haversine_m(float(lat), float(lng), float(rlat), float(rlng))
        except (TypeError, ValueError):
            continue
        if d <= radius_m:
            row_copy = dict(row)
            row_copy["_distanceMetersApprox"] = round(d, 1)
            scored.append((d, row_copy))
    scored.sort(key=lambda x: x[0])
    return [x[1] for x in scored[:limit]]


def _resolve_assessment_uuid(state: AgentState, message: str) -> str | None:
    raw = (state.get("focus_assessment_id") or "").strip()
    if raw and _UUID_RE.fullmatch(raw):
        return raw
    return _parse_assessment_uuid(message)


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


RouteKind = Literal["education", "shelter", "iot", "web_search", "db_lookup"]


class AgentState(TypedDict, total=False):
    """LangGraph 共享状态。"""

    user_message: str
    lat: float | None
    lng: float | None
    volunteer_user_id: int | None
    iot_snapshot: dict[str, Any] | None
    assessment_context: str | None
    chat_history: list[dict[str, str]]
    output_language: Literal["zh", "en"]
    focus_cat_id: int | None
    focus_assessment_id: str | None
    assessment_duplicate: dict[str, Any] | None
    route: RouteKind
    shelter_payload: dict[str, Any] | None
    shelter_cards: list[dict[str, Any]]
    db_lookup_payload: dict[str, Any] | None
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
    url = f"{API_BASE}{path}"
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
    """概念名 find_rescue_center：查询 GPS 附近 TNR 与救助/医疗资源，返回 JSON 字符串。"""
    payload = await get_nearby_shelters_impl(lat, lng, need_tnr=need_tnr, need_vet=need_vet)
    return json.dumps(payload, ensure_ascii=False)


# ---------------------------------------------------------------------------
# 条件路由：决定走场景 A / B / C
# ---------------------------------------------------------------------------
def classify_route(state: AgentState) -> RouteKind:
    """
    路由优先级（对齐 docs/agent_demo.md）：
    1. 有 iot_snapshot → IoT 审计
    2. 平台工具 / 数据库 → db_lookup（get_cat_count_nearby、get_feeders_nearby 等）
    3. 联网搜索（医院检索 + 开放域科普）→ web_search
    4. 附近机构目录（Java nearest，概念名 find_rescue_center）→ shelter
    5. 其余 → education（可注入本地知识库 RAG 摘录）
    """
    snap = state.get("iot_snapshot")
    if snap and isinstance(snap, dict) and len(snap) > 0:
        return "iot"

    msg = (state.get("user_message") or "").replace("[nearby_shelters]", "").strip()

    if _wants_db_lookup(msg, state):
        return "db_lookup"

    if _wants_web_search(msg):
        return "web_search"

    if "[nearby_shelters]" in (state.get("user_message") or "") or _SHELTER_INTENT.search(msg):
        return "shelter"

    return "education"


def _route_edge(state: AgentState) -> str:
    """conditional_edges 的目标节点名。"""
    kind = state.get("route") or classify_route(state)
    return {
        "education": "education_reply",
        "shelter": "shelter_fetch",
        "iot": "iot_audit",
        "web_search": "web_search",
        "db_lookup": "db_lookup_fetch",
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


def _polish_assistant_text(text: str, *, lang: Literal["zh", "en"]) -> str:
    """Strip model boilerplate: 「另外还有0条…」、重复结尾感谢语等。"""
    t = (text or "").strip()
    if not t:
        return t
    lines_out: list[str] = []
    for line in t.splitlines():
        s = line.strip()
        if s and re.fullmatch(
            r"(另外还有|另有)\s*0\s*条[^。!！\n]*[。!！]?|另外还有0条记录[。!！]?",
            s,
        ):
            continue
        if s and len(s) < 100 and re.search(
            r"(另外还有|另有)\s*0\s*条",
            s,
        ):
            continue
        lines_out.append(line)
    t = "\n".join(lines_out).strip()
    t = re.sub(r"[，,]?\s*(另外还有|另有)\s*0\s*条[^。!！\n]*", "", t)
    t = t.strip()
    if lang == "en":
        t = re.sub(
            r"(?is)[\s\n]*(thank you for (your )?care and support (for )?(community |stray )?cats[!.\s…]*)+\s*$",
            "",
            t,
        ).strip()
    else:
        t = re.sub(
            r"(?s)[\s\n]*(感谢您对流浪猫的关心与支持|感谢您对流浪猫们的关心与支持)[!！。.…\s]*\s*$",
            "",
            t,
        ).strip()
    return t


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
    raw_msg = (state.get("user_message") or "").replace("[nearby_shelters]", "").strip()
    rag = _rag_snippets_for_message(raw_msg)
    scene = SCENE_A_APPEND
    if rag:
        if _norm_output_language(state) == "en":
            scene += "\n\n[Knowledge base excerpts — integrate naturally; do not read filenames aloud]\n" + rag
        else:
            scene += "\n\n【知识库摘录（融入回答，勿朗读文件名）】\n" + rag
    messages = _build_scene_messages(state, scene)
    text = await _stream_llm_to_text(messages, config)
    lang = _norm_output_language(state)
    return {"final_text": _polish_assistant_text(text, lang=lang), "shelter_cards": []}


async def web_search_node(state: AgentState) -> dict[str, Any]:
    """
    联网搜索：默认使用带联网能力的 OpenAI search 类模型（OPENAI_HOSPITAL_SEARCH_MODEL）。
    分支：附近医院/收容所 vs 开放域流浪猫科普（脚本 2. 联网搜索）。
    """
    from openai import AsyncOpenAI

    loc = _extract_user_location_hint(state)
    user_q = (state.get("user_message") or "").replace("[nearby_shelters]", "").strip()
    lang = _norm_output_language(state)
    local_facilities = _web_search_is_local_facilities(user_q)

    if local_facilities:
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
    else:
        if lang == "en":
            prompt = (
                "Use web search to answer the volunteer's question about stray/community cats "
                "(health, behavior, TNR basics, feeding safety, or disease red flags).\n"
                "Prefer reputable veterinary or major rescue sources; if uncertain, say so. "
                "Keep it practical and concise (bullet list OK).\n\n"
                f"Context location hint: {loc}\n"
                f"Question: {user_q}"
                f"{_system_language_tail(state)}"
            )
        else:
            prompt = (
                "请联网检索与下列问题相关的、对流浪猫救助者有用的信息（健康、行为、TNR 常识、喂养安全、伤病警示等）。\n"
                "优先动物医学与主流救助机构来源；不确定时请明确说明。回答务实用短段落或分条。\n\n"
                f"地域/上下文参考：{loc}\n"
                f"用户问题：{user_q}"
                f"{_system_language_tail(state)}"
            )

    client = AsyncOpenAI(api_key=_openai_api_key(), base_url=_normalize_openai_base_url())
    resp = await client.chat.completions.create(
        model=_HOSPITAL_SEARCH_MODEL,
        messages=[{"role": "user", "content": prompt}],
    )
    choice = resp.choices[0].message
    raw = (choice.content or "").strip()
    if lang == "en":
        text = raw if raw else "(No search results returned; retry later or verify model access.)"
    else:
        text = raw if raw else "（未返回有效搜索结果，请稍后重试或检查账号是否开通该模型权限。）"
    return {"final_text": _polish_assistant_text(text, lang=lang), "shelter_cards": []}


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
    lang = _norm_output_language(state)
    return {"final_text": _polish_assistant_text(text, lang=lang), "shelter_cards": []}


async def iot_audit_node(state: AgentState, config: RunnableConfig) -> dict[str, Any]:
    snap = state.get("iot_snapshot") or {}
    snap_text = json.dumps(snap, ensure_ascii=False, indent=2)
    messages = _build_scene_messages(state, SCENE_C_APPEND)
    if _norm_output_language(state) == "en":
        messages.append(HumanMessage(content=f"【Device/health snapshot JSON】\n{snap_text}"))
    else:
        messages.append(HumanMessage(content=f"【设备/健康快照 JSON】\n{snap_text}"))
    text = await _stream_llm_to_text(messages, config)
    lang = _norm_output_language(state)
    return {"final_text": _polish_assistant_text(text, lang=lang), "shelter_cards": []}


async def db_lookup_fetch_node(state: AgentState) -> dict[str, Any]:
    """按意图调用 Spring Boot：TNR/救助目录 nearest、assessments/list、assessments/{id}。"""
    msg = (state.get("user_message") or "").replace("[nearby_shelters]", "").strip()
    kind = _db_lookup_kind(msg, state) or "unknown"
    base = API_BASE
    lat, lng = _coords_for_db_lookup(state, msg)
    payload: dict[str, Any] = {
        "intent": kind,
        "concept_tool": _CONCEPT_TOOL_BY_INTENT.get(kind, kind),
    }

    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            if kind == "platform_stats":
                r = await client.get(f"{base}/api/v1/stats/platform")
                r.raise_for_status()
                body = r.json()
                payload["stats"] = body if isinstance(body, dict) else {}
                payload["empty"] = False

            elif kind == "report_cat":
                payload["empty"] = False
                payload["hint_zh"] = (
                    "新猫上报请在本页「上传/分析」提交清晰照片与定位；系统会走评估建档并与社区同猫库匹配。"
                    "仅在对话里文字描述无法在库里形成可追溯记录。"
                )
                payload["hint_en"] = (
                    "To report a new stray, use Upload / Analyze with a clear photo and location; "
                    "the pipeline creates an assessment and matches community cats. Text-only chat cannot create a traceable report."
                )

            elif kind == "tnr_directory":
                if lat is None or lng is None:
                    payload.update(
                        {
                            "error": "missing_coordinates",
                            "hint_zh": "查询附近 TNR 资源请先填写定位或经纬度。",
                            "hint_en": "Set latitude/longitude to query nearby TNR resources.",
                        }
                    )
                else:
                    r = await client.get(
                        f"{base}/api/v1/tnr-directory/nearest",
                        params={"lat": lat, "lng": lng, "limit": 5},
                    )
                    r.raise_for_status()
                    data = r.json()
                    rows = data if isinstance(data, list) else []
                    payload["data"] = rows
                    payload["empty"] = len(rows) == 0

            elif kind == "rescue_directory":
                if lat is None or lng is None:
                    payload.update(
                        {
                            "error": "missing_coordinates",
                            "hint_zh": "查询附近救助/送养机构请先填写定位或经纬度。",
                            "hint_en": "Set latitude/longitude to query nearby rescue/adoption orgs.",
                        }
                    )
                else:
                    r = await client.get(
                        f"{base}/api/v1/rescue-directory/nearest",
                        params={"lat": lat, "lng": lng, "limit": 5},
                    )
                    r.raise_for_status()
                    data = r.json()
                    rows = data if isinstance(data, list) else []
                    payload["data"] = rows
                    payload["empty"] = len(rows) == 0

            elif kind == "cats_nearby_count":
                if lat is None or lng is None:
                    payload.update(
                        {
                            "error": "missing_coordinates",
                            "hint_zh": "查询附近猫咪数量请先填写定位或经纬度。",
                            "hint_en": "Set latitude/longitude to count nearby cats.",
                        }
                    )
                else:
                    r = await client.get(
                        f"{base}/api/v1/cats/lookup",
                        params={"lat": lat, "lng": lng, "radius": 1000},
                    )
                    r.raise_for_status()
                    data = r.json()
                    matches = data.get("matches") if isinstance(data, dict) else None
                    if not isinstance(matches, list):
                        matches = []
                    payload["match_count"] = len(matches)
                    payload["matches_sample"] = matches[:15]
                    payload["radius_m"] = 1000
                    payload["empty"] = len(matches) == 0

            elif kind == "feeding_stations":
                r = await client.get(f"{base}/api/v1/feeding-stations")
                r.raise_for_status()
                raw_list = r.json()
                rows = raw_list if isinstance(raw_list, list) else []
                if lat is not None and lng is not None:
                    enriched: list[dict[str, Any]] = []
                    for row in rows:
                        if not isinstance(row, dict):
                            continue
                        cp = dict(row)
                        try:
                            sla = float(cp.get("latitude"))
                            slo = float(cp.get("longitude"))
                            d = _haversine_m(float(lat), float(lng), sla, slo)
                            cp["distanceMeters"] = round(d, 1)
                        except (TypeError, ValueError):
                            cp["distanceMeters"] = None
                        enriched.append(cp)
                    enriched.sort(
                        key=lambda x: float(x.get("distanceMeters") or 9e12),
                    )
                    rows = enriched
                payload["data"] = rows[:25]
                payload["empty"] = len(rows) == 0

            elif kind == "feeding_checkin":
                uid = state.get("volunteer_user_id")
                try:
                    uid_int = int(uid) if uid is not None else 0
                except (TypeError, ValueError):
                    uid_int = 0
                if uid_int <= 0:
                    payload.update(
                        {
                            "error": "missing_volunteer_user_id",
                            "hint_zh": "喂养打卡需要志愿者数字 userId（页面 trackingUserId / 登录后）；请先在志愿者中心登录或填写。",
                            "hint_en": "Feeding check-in needs a numeric volunteer userId (tracking field or volunteer login).",
                        }
                    )
                elif lat is None or lng is None:
                    payload.update(
                        {
                            "error": "missing_coordinates",
                            "hint_zh": "打卡需要当前位置的经纬度。",
                            "hint_en": "Latitude/longitude required for check-in.",
                        }
                    )
                else:
                    body = {
                        "latitude": float(lat),
                        "longitude": float(lng),
                        "note": "agent_checkin",
                    }
                    r = await client.post(
                        f"{base}/api/v1/volunteers/me/feeding-checkins",
                        params={"userId": uid_int},
                        json=body,
                    )
                    if r.status_code >= 400:
                        payload.update(
                            {
                                "error": "checkin_http_error",
                                "http_status": r.status_code,
                                "body": (r.text or "")[:500],
                            }
                        )
                    else:
                        payload["check_in"] = r.json()
                        payload["empty"] = False

            elif kind == "assessment_duplicate_match":
                dup = state.get("assessment_duplicate")
                if not isinstance(dup, dict) or dup.get("matchedAssessmentId") is None:
                    payload.update(
                        {
                            "error": "missing_duplicate_payload",
                            "hint_zh": "页面未传入同猫识别结果；请在上传分析完成后再问「之前见过吗」。",
                            "hint_en": "Same-cat match data was not passed from the upload page; ask after analysis completes.",
                        }
                    )
                else:
                    rows = dup.get("clusterSightings")
                    timeline = rows if isinstance(rows, list) else []
                    payload["data"] = dup
                    payload["cluster_size"] = len(timeline)
                    payload["empty"] = len(timeline) == 0

            elif kind == "cat_heatmap_sightings":
                cid_raw = state.get("focus_cat_id")
                if cid_raw is None:
                    payload.update(
                        {
                            "error": "missing_cat_id",
                            "hint_zh": "需要先匹配到猫咪档案数字 ID（上传后勾选「自动匹配同猫并居中热力图」成功，或在「猫咪轨迹热力图」里手动输入该猫的 ID），我才能按热力图数据回答出现了几次、在哪些地方。",
                            "hint_en": "Need a numeric cat archive ID (enable auto-match after upload, or enter the cat ID in the heatmap panel) to answer from the same sightings API as the heatmap.",
                        }
                    )
                else:
                    try:
                        cat_id = int(cid_raw)
                    except (TypeError, ValueError):
                        payload.update(
                            {
                                "error": "invalid_cat_id",
                                "hint_zh": "当前猫咪 ID 无效，请检查热力图输入框或重新上传并完成同猫匹配。",
                                "hint_en": "Invalid cat ID; check the heatmap field or re-run same-cat matching.",
                            }
                        )
                    else:
                        r = await client.get(f"{base}/api/v1/cats/{cat_id}/heatmap/sightings")
                        r.raise_for_status()
                        raw_list = r.json()
                        rows = raw_list if isinstance(raw_list, list) else []
                        total = len(rows)
                        max_rows = 40
                        payload["cat_id"] = cat_id
                        payload["window_days"] = 30
                        payload["sightings_total"] = total
                        payload["data"] = rows[:max_rows]
                        payload["records_omitted"] = max(0, total - max_rows)

                        cat_history: dict[str, Any] | None = None
                        try:
                            rh = await client.get(
                                f"{base}/api/v1/cats/{cat_id}/history",
                                params={"limit": 50},
                            )
                            if rh.status_code == 200:
                                hjson = rh.json()
                                cat_history = hjson if isinstance(hjson, dict) else None
                        except Exception:  # noqa: BLE001
                            cat_history = None
                        payload["cat_history"] = cat_history

                        hist_n = 0
                        if isinstance(cat_history, dict):
                            rs = cat_history.get("recentSightings")
                            if isinstance(rs, list):
                                hist_n = len(rs)
                        payload["timeline_sightings_count"] = hist_n

                        # 热力图窗口内可能无点，但 history 仍可有带地址的时间线
                        payload["empty"] = total == 0 and hist_n == 0

            elif kind == "assessments_list":
                if lat is None or lng is None:
                    payload.update(
                        {
                            "error": "missing_coordinates",
                            "hint_zh": "按位置查「是否见过」需 GPS；请填写定位或经纬度。",
                            "hint_en": "GPS is required to filter nearby assessment records.",
                        }
                    )
                else:
                    r = await client.get(f"{base}/api/v1/assessments/list")
                    r.raise_for_status()
                    raw_list = r.json()
                    rows = raw_list if isinstance(raw_list, list) else []
                    nearby = _filter_assessments_by_gps(rows, lat, lng, radius_m=150.0, limit=8)
                    payload["data"] = nearby
                    payload["list_total"] = len(rows)
                    payload["filter_radius_m"] = 150
                    payload["empty"] = len(nearby) == 0

            elif kind in ("assessment_ear", "assessment_reporter"):
                aid = _resolve_assessment_uuid(state, msg)
                if not aid:
                    payload.update(
                        {
                            "error": "missing_assessment_id",
                            "hint_zh": "需要一条评估档案 ID（上传评估后由页面自动带上），或在话里写出完整 UUID。",
                            "hint_en": "Need an assessment UUID from context or message text.",
                        }
                    )
                else:
                    r = await client.get(f"{base}/api/v1/assessments/{aid}")
                    if r.status_code == 404:
                        payload["empty"] = True
                        payload["assessment_id"] = aid
                    else:
                        r.raise_for_status()
                        payload["data"] = r.json()
                        payload["empty"] = False
                        payload["detail_focus"] = (
                            "reporter_tracking" if kind == "assessment_reporter" else "tnr_ear_model_labels"
                        )

            else:
                payload["error"] = "unknown_intent"
                payload["hint_zh"] = "内部路由异常，请换一种问法重试。"

    except Exception as exc:  # noqa: BLE001
        payload = {"intent": kind, "error": "request_failed", "message": str(exc)}

    return {"db_lookup_payload": payload, "shelter_cards": []}


async def db_lookup_reply_node(state: AgentState, config: RunnableConfig) -> dict[str, Any]:
    payload = state.get("db_lookup_payload") or {}
    lang = _norm_output_language(state)
    lang_en = lang == "en"

    def _done(t: str) -> dict[str, Any]:
        return {"final_text": _polish_assistant_text(t, lang=lang), "shelter_cards": []}

    if payload.get("intent") == "report_cat":
        if lang_en:
            text = payload.get("hint_en") or (
                "To report a new stray, use Upload / Analyze with a photo and location; text-only messages cannot create an archive."
            )
        else:
            text = payload.get("hint_zh") or (
                "新猫上报请在本页「上传/分析」提交照片与定位；系统会建档并做同猫匹配。仅在对话里发文字无法在库里形成可追溯上报。"
            )
        return _done(text)

    if payload.get("empty") is True:
        intent = payload.get("intent")
        if intent == "cat_heatmap_sightings":
            text = (
                "There are no sightings for this cat in the heatmap window and no timeline rows returned from history yet."
                if lang_en
                else "近 30 天热图窗口内暂无目击点，且档案历史接口也未返回时间线记录。"
            )
        elif intent == "cats_nearby_count":
            text = (
                "No cat archive matches within about 1km of your coordinates yet."
                if lang_en
                else "当前坐标约 1km 范围内暂无匹配的猫咪档案。"
            )
        elif intent == "feeding_stations":
            text = (
                "No feeding stations are configured in the system yet."
                if lang_en
                else "系统中暂无喂食器/喂养点记录。"
            )
        else:
            text = (
                "There is no matching record in the system yet. Would you like to help create an archive for this cat?"
                if lang_en
                else "系统暂无记录，要帮它建立档案吗？"
            )
        return _done(text)

    err = payload.get("error")
    if err == "missing_volunteer_user_id":
        if lang_en:
            text = payload.get("hint_en") or "Volunteer userId is required for feeding check-in."
        else:
            text = payload.get("hint_zh") or "喂养打卡需要先在页面填写或登录志愿者 userId。"
        return _done(text)
    if err == "checkin_http_error":
        body = str(payload.get("body") or "")
        status = payload.get("http_status")
        text = (
            f"打卡请求失败（HTTP {status}）：{body[:400]}"
            if not lang_en
            else f"Check-in request failed (HTTP {status}): {body[:400]}"
        )
        return _done(text)

    if err == "missing_cat_id":
        if lang_en:
            text = payload.get("hint_en") or (
                "I need a cat archive ID (same as the heatmap) to look up how many times this cat was sighted."
            )
        else:
            text = (
                payload.get("hint_zh")
                or "需要猫咪档案数字 ID（与热力图一致），才能查询这只猫出现了几次、在哪些地方。"
            )
        return _done(text)
    if err == "invalid_cat_id":
        if lang_en:
            text = payload.get("hint_en") or "The cat ID is invalid."
        else:
            text = payload.get("hint_zh") or "猫咪 ID 无效。"
        return _done(text)

    if err == "missing_coordinates":
        if lang_en:
            text = payload.get("hint_en") or (
                "Please enable location or enter latitude/longitude first, then ask again."
            )
        else:
            text = payload.get("hint_zh") or "要查询附近记录或机构，请先在表单中开启定位或填写经纬度后再试。"
        return _done(text)
    if err == "missing_assessment_id":
        if lang_en:
            text = payload.get("hint_en") or (
                "I need the assessment archive ID (or paste the full UUID) to answer that."
            )
        else:
            text = (
                payload.get("hint_zh")
                or "需要定位到一条评估档案（上传照片后由页面自动带上评估 ID），或您在话里写出完整评估 UUID，我才能查绝育状态或跟进信息。"
            )
        return _done(text)

    if err == "request_failed":
        msg_exc = str(payload.get("message") or "")
        text = (
            f"暂时无法连接救助平台数据：{msg_exc[:280]}"
            if not lang_en
            else f"Could not reach the rescue platform: {msg_exc[:280]}"
        )
        return _done(text)
    if err == "unknown_intent":
        text = (
            (payload.get("hint_zh") or "内部路由异常，请换一种问法重试。")
            if not lang_en
            else (payload.get("hint_en") or "Internal routing error; please try a different question.")
        )
        return _done(text)

    llm_payload = _prepare_db_lookup_llm_payload(payload)
    tool_json = json.dumps(llm_payload, ensure_ascii=False, indent=2)
    scene = SCENE_DB_LOOKUP_APPEND_EN if lang_en else SCENE_DB_LOOKUP_APPEND
    messages = _build_scene_messages(state, scene)
    if lang_en:
        tail = (
            "Summarize for the volunteer in natural English, following the scene format rules. "
            "Only if `records_omitted` exists and is an integer greater than 0, add one short sentence "
            "that additional records exist (use that count). If the field is absent or 0, do not mention "
            "extra rows or '0 more records'."
        )
    else:
        tail = (
            "请根据 JSON 用自然中文回复志愿者，并遵守场景中的格式要求：口语化段落、无编号列表。"
            "仅当 JSON 中 records_omitted 存在且为大于 0 的整数时，才用一句话说明另有若干条未展示；"
            "若为 0 或未出现该字段，不要写「另外还有」「若干条」等与条数相关的收尾。"
        )
    messages.append(HumanMessage(content=f"【后端查询 JSON】\n{tool_json}\n{tail}"))
    text = await _stream_llm_to_text(messages, config)
    return _done(text)


def build_station_master_graph():
    """编译站长 LangGraph（入口 → 分类 → 三场景之一 → END）。"""
    g = StateGraph(AgentState)
    g.add_node("classify", classify_node)
    g.add_node("education_reply", education_reply_node)
    g.add_node("shelter_fetch", shelter_fetch_node)
    g.add_node("shelter_reply", shelter_reply_node)
    g.add_node("iot_audit", iot_audit_node)
    g.add_node("web_search", web_search_node)
    g.add_node("db_lookup_fetch", db_lookup_fetch_node)
    g.add_node("db_lookup_reply", db_lookup_reply_node)

    g.add_edge(START, "classify")
    g.add_conditional_edges("classify", _route_edge)
    g.add_edge("education_reply", END)
    g.add_edge("shelter_fetch", "shelter_reply")
    g.add_edge("shelter_reply", END)
    g.add_edge("iot_audit", END)
    g.add_edge("web_search", END)
    g.add_edge("db_lookup_fetch", "db_lookup_reply")
    g.add_edge("db_lookup_reply", END)
    return g.compile()


# 单例图，供 FastAPI 复用
station_master_graph = build_station_master_graph()


def initial_state(
    message: str,
    *,
    lat: float | None = None,
    lng: float | None = None,
    volunteer_user_id: int | None = None,
    iot_snapshot: dict[str, Any] | None = None,
    assessment_context: str | None = None,
    chat_history: list[dict[str, str]] | None = None,
    output_language: str | None = None,
    focus_cat_id: int | None = None,
    focus_assessment_id: str | None = None,
    assessment_duplicate: dict[str, Any] | None = None,
) -> AgentState:
    ol = (output_language or "zh").strip().lower()
    lang: Literal["zh", "en"] = "en" if ol.startswith("en") else "zh"
    st: AgentState = {
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
    if focus_cat_id is not None:
        st["focus_cat_id"] = int(focus_cat_id)
    if focus_assessment_id:
        fa = str(focus_assessment_id).strip()
        if _UUID_RE.fullmatch(fa):
            st["focus_assessment_id"] = fa
    if isinstance(assessment_duplicate, dict) and assessment_duplicate.get("matchedAssessmentId") is not None:
        st["assessment_duplicate"] = assessment_duplicate
    if volunteer_user_id is not None:
        try:
            vu = int(volunteer_user_id)
            if vu > 0:
                st["volunteer_user_id"] = vu
        except (TypeError, ValueError):
            pass
    return st
