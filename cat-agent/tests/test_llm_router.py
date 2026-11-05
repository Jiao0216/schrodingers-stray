"""Hybrid routing: regex fast path, LLM tool choice, and fallback. The router model is always a fake."""

import asyncio
import json

import pytest

import ai_service
import llm_router


def call(name, **args):
    return {"function": {"name": name, "arguments": json.dumps(args)}}


def state(msg, **extra):
    st = ai_service.initial_state(msg, lat=37.3382, lng=-121.8863, output_language="zh")
    st.update(extra)
    return st


@pytest.fixture
def router(monkeypatch):
    """Installs a fake router; returns the list of calls it received."""
    seen = []

    def install(result=None, exc=None, delay=0.0):
        async def fake(messages, tools):
            seen.append(messages)
            if delay:
                await asyncio.sleep(delay)
            if exc:
                raise exc
            return result or []

        monkeypatch.setattr(ai_service, "ROUTER_CALL", fake)
        return seen

    return install


# --- plan_from_tool_calls (pure) -------------------------------------------------------------------------------

def test_plan_maps_tools_to_existing_routes():
    plan = llm_router.plan_from_tool_calls(
        [call("find_rescue_center", kind="tnr"), call("search_knowledge_base", query="能喂牛奶吗")], {}
    )
    assert [(s.route, s.db_kind) for s in plan.steps] == [("db_lookup", "tnr_directory"), ("education", None)]
    assert plan.steps[1].query == "能喂牛奶吗"


def test_plan_drops_calls_without_preconditions():
    plan = llm_router.plan_from_tool_calls(
        [
            call("get_assessment_detail", focus="ear_tip"),       # no assessment open
            call("get_cat_sightings_timeline"),                   # no cat id anywhere
            call("get_same_cat_cluster_timeline"),                # no same-cat match
            call("find_rescue_center", kind="spa"),               # bad enum
            {"function": {"name": "get_feeders_nearby", "arguments": "{not json"}},
            call("checkin_feeding"),                              # write tool is never offered
        ],
        {},
    )
    assert plan.steps == []
    assert len(plan.errors) == 6


def test_plan_uses_focus_cat_and_caps_at_three():
    plan = llm_router.plan_from_tool_calls(
        [call("get_cat_sightings_timeline"), call("get_feeders_nearby"), call("get_feeders_nearby"),
         call("get_cat_count_nearby"), call("query_assessments_nearby")],
        {"focus_cat_id": 7},
    )
    assert [s.tool for s in plan.steps] == ["get_cat_sightings_timeline", "get_feeders_nearby", "get_cat_count_nearby"]
    assert plan.steps[0].cat_id == 7


def test_write_tool_is_not_offered_to_the_model():
    names = {t["function"]["name"] for t in llm_router.TOOLS}
    assert "checkin_feeding" not in names and "report_cat" not in names


# --- classify_node ---------------------------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_rule_fast_path_skips_router(router):
    seen = router([call("get_feeders_nearby")])
    out = await ai_service.classify_node(state("我刚喂完了，帮我打卡"))
    assert out["routing_mode"] == "rule"
    assert out["route"] == "db_lookup"
    assert seen == []  # the LLM was never asked


@pytest.mark.asyncio
async def test_single_tool_goes_through_existing_node(router):
    router([call("find_rescue_center", kind="rescue")])
    # Paraphrase the regex rules do not recognise as a directory question.
    out = await ai_service.classify_node(state("我捡到一只受伤的小猫，这边有没有能收留它的组织？"))
    assert out == {
        "route": "db_lookup", "routing_mode": "llm",
        "tools_used": ["find_rescue_center"], "db_kind_override": "rescue_directory",
    }
    # The override wins over the regex intent when the node runs.
    assert ai_service._db_lookup_kind("随便说点什么", {**state("x"), **out}) == "rescue_directory"


@pytest.mark.asyncio
async def test_compound_question_becomes_multi(router):
    router([call("get_cat_sightings_timeline", cat_id=3), call("find_rescue_center", kind="tnr")])
    out = await ai_service.classify_node(state("3 号猫最近在哪出现？附近有能做 TNR 的地方吗？"))
    assert out["route"] == "multi"
    assert [s["tool"] for s in out["plan_steps"]] == ["get_cat_sightings_timeline", "find_rescue_center"]
    assert out["plan_steps"][0]["cat_id"] == 3


@pytest.mark.asyncio
@pytest.mark.parametrize("kwargs", [
    {"exc": RuntimeError("boom")},
    {"result": []},
    {"result": [call("get_assessment_detail", focus="ear_tip")]},  # invalid without an open assessment
])
async def test_router_problems_fall_back_to_rules(router, kwargs):
    router(**kwargs)
    msg = "流浪猫可以喝牛奶吗？"
    out = await ai_service.classify_node(state(msg))
    assert out["routing_mode"] == "fallback"
    assert out["route"] == ai_service.classify_route(state(msg))


@pytest.mark.asyncio
async def test_router_timeout_falls_back(router, monkeypatch):
    monkeypatch.setattr(llm_router, "LLM_ROUTING_TIMEOUT_S", 0.05)
    router([call("get_feeders_nearby")], delay=1.0)
    out = await ai_service.classify_node(state("附近哪里有喂猫的地方"))
    assert out["routing_mode"] == "fallback"


@pytest.mark.asyncio
async def test_switch_off_restores_rule_behaviour(router, monkeypatch):
    seen = router([call("get_feeders_nearby")])
    monkeypatch.setattr(llm_router, "LLM_ROUTING_ENABLED", False)
    msg = "我捡到一只受伤的小猫，这边有没有能收留它的组织？"
    out = await ai_service.classify_node(state(msg))
    assert out == {"route": ai_service.classify_route(state(msg)), "routing_mode": "rule", "tools_used": []}
    assert seen == []


# --- multi_tool_node -------------------------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_multi_tool_runs_every_step_and_composes_one_reply(monkeypatch):
    fetched = []

    async def fake_fetch(st):
        fetched.append((st.get("db_kind_override"), st.get("focus_cat_id")))
        return {"db_lookup_payload": {"intent": st["db_kind_override"], "data": [{"ok": True}]}}

    captured = {}

    async def fake_llm(messages, config):
        captured["prompt"] = messages[-1].content
        return "综合回答"

    monkeypatch.setattr(ai_service, "db_lookup_fetch_node", fake_fetch)
    monkeypatch.setattr(ai_service, "_stream_llm_to_text", fake_llm)
    st = state("3 号猫在哪？附近哪里能绝育？牛奶能喂吗？", plan_steps=[
        {"tool": "get_cat_sightings_timeline", "route": "db_lookup", "db_kind": "cat_heatmap_sightings", "cat_id": 3, "query": None},
        {"tool": "find_rescue_center", "route": "db_lookup", "db_kind": "tnr_directory", "cat_id": None, "query": None},
        {"tool": "search_knowledge_base", "route": "education", "db_kind": None, "cat_id": None, "query": "牛奶"},
    ])
    out = await ai_service.multi_tool_node(st, {})
    assert out["final_text"] == "综合回答"
    assert ("cat_heatmap_sightings", 3) in fetched and ("tnr_directory", None) in fetched
    for name in ("get_cat_sightings_timeline", "find_rescue_center", "search_knowledge_base"):
        assert name in captured["prompt"]
