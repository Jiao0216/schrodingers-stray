"""LLM tool-calling router for the station-lead agent (hybrid with the regex rules in ai_service).

The model only *chooses* tools and arguments; ai_service executes them through the existing nodes. Tools that write
data (feeding check-in) are deliberately not offered here — they stay on the explicit rule path.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, field
from typing import Any

LLM_ROUTING_ENABLED = os.getenv("AGENT_LLM_ROUTING", "true").strip().lower() not in ("0", "false", "no", "off")
LLM_ROUTING_TIMEOUT_S = float(os.getenv("AGENT_LLM_ROUTING_TIMEOUT", "8"))
MAX_TOOL_CALLS = 3

# db_lookup kinds whose intent the regex rules recognise unambiguously; these skip the router entirely.
RULE_ONLY_DB_KINDS = frozenset({"feeding_checkin", "report_cat", "platform_stats"})


@dataclass(frozen=True)
class Step:
    """One planned tool call, mapped onto an existing ai_service route."""

    tool: str
    route: str  # "db_lookup" | "education" | "web_search"
    db_kind: str | None = None
    cat_id: int | None = None
    query: str | None = None


@dataclass
class Plan:
    steps: list[Step] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)


def _fn(name: str, description: str, properties: dict[str, Any] | None = None, required: list[str] | None = None) -> dict:
    return {
        "type": "function",
        "function": {
            "name": name,
            "description": description,
            "parameters": {
                "type": "object",
                "properties": properties or {},
                "required": required or [],
                "additionalProperties": False,
            },
        },
    }


TOOLS: list[dict] = [
    _fn("get_cat_count_nearby",
        "Count community cats recorded near the volunteer's current location. Use for 'how many cats are around here'."),
    _fn("get_feeders_nearby",
        "List registered feeding stations / feeding spots near the volunteer."),
    _fn("find_rescue_center",
        "Nearby organizations from the platform directory: kind='tnr' for spay/neuter (TNR) programs, "
        "kind='rescue' for rescue, intake, adoption or injured-animal help.",
        {"kind": {"type": "string", "enum": ["tnr", "rescue"]}}, ["kind"]),
    _fn("query_assessments_nearby",
        "Recent photo assessments recorded near the volunteer — use for 'has this cat been seen / recorded before'."),
    _fn("get_assessment_detail",
        "Details of the photo assessment the volunteer is currently looking at. focus='ear_tip' for TNR / ear-tip / "
        "sterilization questions, focus='reporter' for who reported or is following up. Only when an assessment is open.",
        {"focus": {"type": "string", "enum": ["ear_tip", "reporter"]}}, ["focus"]),
    _fn("get_cat_sightings_timeline",
        "Where and how often one cat archive has been sighted (heatmap / timeline). cat_id is the numeric cat archive id: "
        "use the id the volunteer mentions, otherwise omit it to use the cat currently in focus. Never guess an id.",
        {"cat_id": {"type": "integer"}}),
    _fn("get_same_cat_cluster_timeline",
        "History of the cat the latest assessment was matched to ('same cat as before?'). Only when a match exists."),
    _fn("search_knowledge_base",
        "Stray-cat care knowledge: feeding safety, health warning signs, TNR basics, handling and behaviour. "
        "Use for general how-to / advice questions.",
        {"query": {"type": "string", "description": "the knowledge question, in the volunteer's words"}}, ["query"]),
    _fn("web_search_facilities",
        "Live web search for real-world places outside the platform directory, e.g. animal hospitals, emergency vets "
        "or clinics near the volunteer, or recent information the knowledge base will not have.",
        {"query": {"type": "string"}}, ["query"]),
]

_DB_TOOLS = {
    "get_cat_count_nearby": "cats_nearby_count",
    "get_feeders_nearby": "feeding_stations",
    "query_assessments_nearby": "assessments_list",
    "get_same_cat_cluster_timeline": "assessment_duplicate_match",
}


def plan_from_tool_calls(tool_calls: list[dict[str, Any]], context: dict[str, Any]) -> Plan:
    """
    Turns OpenAI tool calls into executable steps, dropping calls whose preconditions are missing (no open
    assessment, no cat id, unknown tool, bad arguments). ``context`` carries focus_cat_id / has_focus_assessment /
    has_duplicate so the model cannot call tools on data that is not there.
    """
    plan = Plan()
    seen: set[tuple] = set()
    # Scan generously (invalid calls are dropped, so a valid one may come later); accepted steps are capped below.
    for call in tool_calls[:10]:
        fn = (call.get("function") or {}) if isinstance(call, dict) else {}
        name = fn.get("name") or ""
        try:
            args = json.loads(fn.get("arguments") or "{}")
            if not isinstance(args, dict):
                raise ValueError("arguments must be an object")
        except (ValueError, TypeError) as exc:
            plan.errors.append(f"{name}: bad arguments ({exc})")
            continue

        step: Step | None = None
        if name in _DB_TOOLS:
            if name == "get_same_cat_cluster_timeline" and not context.get("has_duplicate"):
                plan.errors.append(f"{name}: no same-cat match in context")
                continue
            step = Step(name, "db_lookup", _DB_TOOLS[name])
        elif name == "find_rescue_center":
            kind = args.get("kind")
            if kind not in ("tnr", "rescue"):
                plan.errors.append(f"{name}: kind must be tnr|rescue")
                continue
            step = Step(name, "db_lookup", "tnr_directory" if kind == "tnr" else "rescue_directory")
        elif name == "get_assessment_detail":
            focus = args.get("focus")
            if not context.get("has_focus_assessment"):
                plan.errors.append(f"{name}: no assessment open")
                continue
            if focus not in ("ear_tip", "reporter"):
                plan.errors.append(f"{name}: focus must be ear_tip|reporter")
                continue
            step = Step(name, "db_lookup", "assessment_ear" if focus == "ear_tip" else "assessment_reporter")
        elif name == "get_cat_sightings_timeline":
            raw = args.get("cat_id", context.get("focus_cat_id"))
            try:
                cat_id = int(raw) if raw is not None else None
            except (TypeError, ValueError):
                cat_id = None
            if cat_id is None or cat_id <= 0:
                plan.errors.append(f"{name}: no valid cat_id")
                continue
            step = Step(name, "db_lookup", "cat_heatmap_sightings", cat_id=cat_id)
        elif name in ("search_knowledge_base", "web_search_facilities"):
            q = str(args.get("query") or "").strip()
            if not q:
                plan.errors.append(f"{name}: empty query")
                continue
            step = Step(name, "education" if name == "search_knowledge_base" else "web_search", query=q)
        else:
            plan.errors.append(f"unknown tool {name!r}")
            continue

        key = (step.tool, step.db_kind, step.cat_id, step.query)
        if key in seen:
            continue
        seen.add(key)
        plan.steps.append(step)
        if len(plan.steps) >= MAX_TOOL_CALLS:
            break
    return plan


def router_messages(user_message: str, context: dict[str, Any], history: list[dict[str, str]] | None) -> list[dict]:
    """Compact router prompt: the model sees the question, recent turns and what context data exists."""
    system = (
        "You route messages from stray-cat rescue volunteers to tools. Call every tool needed to answer "
        f"(at most {MAX_TOOL_CALLS}); call several for questions with several parts. Do not answer in text. "
        "Prefer platform tools for platform data, search_knowledge_base for general care advice, and "
        "web_search_facilities only for real-world places or very recent information. "
        "Never invent ids: pass cat_id only if the volunteer states it or the context provides one."
    )
    ctx = {
        "has_location": bool(context.get("has_location")),
        "focus_cat_id": context.get("focus_cat_id"),
        "assessment_open": bool(context.get("has_focus_assessment")),
        "same_cat_match_available": bool(context.get("has_duplicate")),
        "reply_language": context.get("lang", "zh"),
    }
    msgs: list[dict] = [{"role": "system", "content": system}]
    for turn in (history or [])[-4:]:
        role = "assistant" if turn.get("role") == "assistant" else "user"
        content = str(turn.get("content") or "")[:600]
        if content:
            msgs.append({"role": role, "content": content})
    msgs.append({"role": "user", "content": f"[context] {json.dumps(ctx, ensure_ascii=False)}\n{user_message}"})
    return msgs
