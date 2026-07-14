import asyncio
import os
import json
import time
import requests
import uvicorn
import threading

from collections import deque
from collections import defaultdict
from datetime import datetime, timezone
from typing import Any, List, Optional
from contextlib import asynccontextmanager
from fastapi import FastAPI, Header, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

# ---------------------------------------------------------------------------
# In-memory log store (last 200 entries, newest first)
# ---------------------------------------------------------------------------
_logs: deque = deque(maxlen=200)

def _log(level: str, message: str, **extra):
    entry = {"timestamp": datetime.now(timezone.utc).isoformat(), "level": level, "message": message, **extra}
    _logs.appendleft(entry)
    print(f"[{level}] {message}", flush=True)
from langchain_core.language_models.llms import LLM
from langchain_core.callbacks.manager import CallbackManagerForLLMRun
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
import numpy as np


# ---------------------------------------------------------------------------
# LLM provider selection
# ---------------------------------------------------------------------------
# The service supports three LLM backends, selected by environment variables:
#   1. Logos (TUM-hosted GPT): set LOGOS_API_KEY. Requires eduVPN off-campus.
#   2. Groq (free cloud API):  set GROQ_API_KEY. Uses llama-3.3-70b.
#   3. LM Studio (local):      set neither. Defaults to localhost:1234.
#      Override with LLM_API_URL and LLM_MODEL for a different local model.
# ---------------------------------------------------------------------------
LOGOS_API_KEY = os.getenv("LOGOS_API_KEY")
GROQ_API_KEY  = os.getenv("GROQ_API_KEY")

# Provider profiles. Which one is used is decided PER REQUEST by
# current_provider() below, so admins can switch Logos <-> Groq at runtime
# via the "llmUseLogos" feature flag — no redeploy needed.
PROVIDER_LOGOS = {
    # TUM-hosted gpt-oss-120b. Off-campus needs eduVPN.
    "name": "logos",
    "url": "https://logos.aet.cit.tum.de/v1/chat/completions",
    "model": "openai/gpt-oss-120b",
    "key": LOGOS_API_KEY,
}
PROVIDER_GROQ = {
    # Free tier, llama-3.3-70b-versatile — much faster than Logos.
    "name": "groq",
    "url": "https://api.groq.com/openai/v1/chat/completions",
    "model": "llama-3.3-70b-versatile",
    "key": GROQ_API_KEY,
}
PROVIDER_LMSTUDIO = {
    # Local model on host. Defaults match compose.yml so both
    # `docker compose up` and `python main.py` work.
    "name": "lm-studio",
    "url": os.getenv("LLM_API_URL", "http://localhost:1234/v1/chat/completions"),
    "model": os.getenv("LLM_MODEL", "gemma-4-e2b"),
    # LM Studio doesn't require a key; CHAIR_API_KEY is left for back-compat.
    "key": os.getenv("LLM_API_KEY") or os.getenv("CHAIR_API_KEY"),
}

# URL of the course-service REST API used to fetch the course catalogue.
COURSE_SERVICE_URL = os.getenv("COURSE_SERVICE_URL", "http://course-service:8082/courses")

# user-service hosts the runtime feature flags (admin-panel toggles).
USER_SERVICE_URL = os.getenv("USER_SERVICE_URL", "http://user-service:8081")

# ---------------------------------------------------------------------------
# Feature flags: fetched from user-service, cached for 30s so toggling in the
# admin panel takes effect quickly without a request to user-service per call.
# On fetch failure the last known state (or the default) is used.
# ---------------------------------------------------------------------------
_flags_cache: dict = {}
_flags_fetched: float = 0.0


def feature_enabled(name: str, default: bool = True) -> bool:
    global _flags_cache, _flags_fetched
    if time.time() - _flags_fetched > 30:
        _flags_fetched = time.time()  # set first so failures don't retry-storm
        try:
            resp = requests.get(f"{USER_SERVICE_URL}/features", timeout=3)
            resp.raise_for_status()
            _flags_cache = {f["name"]: f["enabled"] for f in resp.json()}
        except Exception as e:
            _log("WARN", f"Could not fetch feature flags ({e}) — using cached/default values")
    return _flags_cache.get(name, default)


# ---------------------------------------------------------------------------
# Runtime settings (prompt sections, monthly token limit): fetched from
# user-service like the flags, cached for 30s. On failure the last known
# values (or the built-in defaults) are used.
# ---------------------------------------------------------------------------
_settings_cache: dict = {}
_settings_fetched: float = 0.0


def get_setting(name: str, default: str) -> str:
    global _settings_cache, _settings_fetched
    if time.time() - _settings_fetched > 30:
        _settings_fetched = time.time()  # set first so failures don't retry-storm
        try:
            resp = requests.get(f"{USER_SERVICE_URL}/settings", timeout=3)
            resp.raise_for_status()
            _settings_cache = {s["name"]: s["value"] for s in resp.json()}
        except Exception as e:
            _log("WARN", f"Could not fetch settings ({e}) — using cached/default values")
    value = _settings_cache.get(name, "")
    return value if value.strip() else default


def current_provider() -> dict:
    """
    Picks the LLM provider for THIS request. The "llmUseLogos" flag lets an
    admin switch between Logos and Groq at runtime; each provider is only
    eligible when its API key is actually configured, so toggling can never
    select a broken profile.
    """
    if LOGOS_API_KEY and feature_enabled("llmUseLogos", default=True):
        return PROVIDER_LOGOS
    if GROQ_API_KEY:
        return PROVIDER_GROQ
    if LOGOS_API_KEY:
        return PROVIDER_LOGOS  # flag is off but Logos is the only key present
    return PROVIDER_LMSTUDIO

# Number of courses passed to the LLM after TF-IDF filtering.
TOP_K = int(os.getenv("TOP_K", "30"))

# Hard cap on LLM output length. Generation time scales with output tokens,
# so this bounds both latency and cost. The prompt asks for a compact
# roadmap (max 5 milestones) so valid JSON fits comfortably under the cap.
# For gpt-oss, hidden reasoning tokens also count toward max_tokens: with a
# 1500 budget, long reasoning left no room for the JSON and truncated it
# (parse error -> 0 milestones -> 502). 4000 leaves headroom for both.
MAX_TOKENS = int(os.getenv("LLM_MAX_TOKENS", "4000"))

# Max characters accepted for the goal (mirrored by maxLength in the UI).
MAX_GOAL_CHARS = int(os.getenv("MAX_GOAL_CHARS", "200"))

# ---------------------------------------------------------------------------
# TF-IDF index: built once at startup, held in memory.
# Replaces keyword search — finds the TOP_K most relevant courses
# for a given goal without burning LLM tokens on all 929 courses.
# ---------------------------------------------------------------------------
_courses: List[dict] = []
_vectorizer: Optional[TfidfVectorizer] = None
_matrix = None
_last_index_attempt: float = 0.0  # epoch seconds of last failed build attempt


def build_index() -> int:
    global _courses, _vectorizer, _matrix
    try:
        resp = requests.get(COURSE_SERVICE_URL, timeout=15)
        resp.raise_for_status()
        _courses = resp.json()
    except Exception as e:
        print(f"[RAG] Could not fetch courses: {e}")
        return 0

    # Build a document per course combining title and objective for better matching.
    documents = []
    for c in _courses:
        title = c.get("title", "")
        objective = (c.get("objective") or c.get("content") or "")[:300]
        documents.append(f"{title} {objective}")

    if not documents:
        print("[RAG] No documents to index (empty course list).")
        return 0

    _vectorizer = TfidfVectorizer(stop_words="english", ngram_range=(1, 2))
    _matrix = _vectorizer.fit_transform(documents)
    print(f"[RAG] Indexed {len(_courses)} courses with TF-IDF.")
    return len(_courses)


def filter_courses(goal: str, k: int = TOP_K) -> str:
    """Return the top-k most relevant courses for the given goal as a formatted string."""
    if _vectorizer is None or _matrix is None or not _courses:
        return "- No matching courses found"

    query_vec = _vectorizer.transform([goal])
    scores = cosine_similarity(query_vec, _matrix).flatten()
    top_idx = np.argsort(scores)[::-1][:k]

    lines = []
    for i in top_idx:
        if scores[i] > 0:
            c = _courses[i]
            code = c.get("tum_number", "")
            title = c.get("title", "")
            objective = (c.get("objective") or c.get("content") or "")[:200]
            lines.append(f"- [{code}] {title} | {objective}")

    return "\n".join(lines) if lines else "- No matching courses found"

# ---------------------------------------------------------------------------
# Per-user token usage tracking
# ---------------------------------------------------------------------------

# The limit is based on actual LLM-reported token usage (prompt + completion)
# and renews at the start of each calendar month (UTC).
# NOTE: must stay comfortably ABOVE MAX_TOKENS — the pre-request reservation
# checks `current + MAX_TOKENS >= limit`, so a limit equal to MAX_TOKENS
# would reject every request before the first roadmap is generated.
DEFAULT_MONTHLY_LIMIT = int(os.getenv("MONTHLY_TOKEN_LIMIT", "50000"))  # per user per month


def monthly_token_limit() -> int:
    """
    The admin-editable monthly quota (setting "monthlyTokenLimit").
    Guarded: must parse as an int and exceed the per-request reservation
    (MAX_TOKENS), otherwise the pre-request check `current + MAX_TOKENS >=
    limit` would reject every request — fall back to the default instead.
    """
    raw = get_setting("monthlyTokenLimit", str(DEFAULT_MONTHLY_LIMIT))
    try:
        value = int(raw)
        if value > MAX_TOKENS:
            return value
        _log("WARN", f"monthlyTokenLimit {value} <= per-request cap {MAX_TOKENS} — using default {DEFAULT_MONTHLY_LIMIT}")
    except ValueError:
        _log("WARN", f"monthlyTokenLimit '{raw}' is not an integer — using default {DEFAULT_MONTHLY_LIMIT}")
    return DEFAULT_MONTHLY_LIMIT

_user_token_usage: dict = defaultdict(int)  # userId -> tokens used this month
_user_locks: dict = defaultdict(threading.Lock)
_usage_month: str = datetime.now(timezone.utc).strftime("%Y-%m")
_month_lock = threading.Lock()


def _reset_if_new_month() -> None:
    """Clears all usage counters when the calendar month (UTC) rolls over."""
    global _usage_month
    now_month = datetime.now(timezone.utc).strftime("%Y-%m")
    if now_month != _usage_month:
        with _month_lock:
            if now_month != _usage_month:
                _user_token_usage.clear()
                _usage_month = now_month
                _log("INFO", f"Monthly token quotas reset for {now_month}")


def check_user_limit(user_id: str) -> None:
    """Raises HTTPException if user has exceeded their monthly token limit."""
    _reset_if_new_month()
    current = _user_token_usage[user_id]
    limit = monthly_token_limit()
    if current + MAX_TOKENS >= limit:
        raise HTTPException(
            status_code=429,
            detail=f"Monthly token limit exceeded. You have used {current}/{limit} tokens this month — the quota resets at the start of next month."
        )

# ---------------------------------------------------------------------------
# App lifecycle
# ---------------------------------------------------------------------------
@asynccontextmanager
async def lifespan(app: FastAPI):
    _log("INFO", f"Starting up (provider: {current_provider()['name']}, model: {current_provider()['model']})")
    for attempt in range(1, 6):
        count = build_index()
        if count > 0:
            _log("INFO", f"Index ready — {count} courses indexed")
            break
        _log("WARN", f"Course index empty (attempt {attempt}/5), retrying in 10s...")
        await asyncio.sleep(10)
    else:
        _log("ERROR", "Could not build course index after 5 attempts — proceeding without courses")
    yield


# Create FastAPI application instance
app = FastAPI(
    title="LLM Recommendation Service",
    description="Service that generates personalized learning roadmaps using an LLM",
    version="2.0.0",
    lifespan=lifespan,
)

# Allow all origins so the frontend and API gateway can call this service freely.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class RoadmapRequest(BaseModel):
    """
    Request schema for generate endpoint.
    """
    goal: str = Field(..., description="The user's learning goal in natural language")


class RoadmapResponse(BaseModel):
    """
    Response schema for generate endpoint.
    """
    milestones: List[Any] = Field(default=[], description="Learning milestones")


class OpenAICompatibleLLM(LLM):
    """
    LangChain LLM wrapper for any OpenAI-compatible /v1/chat/completions
    endpoint (LM Studio, Ollama in OpenAI mode, OpenAI itself, etc.).
    """

    last_usage: dict = {}

    @property
    def _llm_type(self) -> str:
        return "openai_compatible"

    def _call(
        self,
        prompt: str,
        stop: Optional[List[str]] = None,
        run_manager: Optional[CallbackManagerForLLMRun] = None,
        **kwargs: Any,
    ) -> str:
        # Resolved per request (not at startup) so the admin-panel
        # "llmUseLogos" toggle takes effect without a restart.
        provider = current_provider()

        headers = {
            "Content-Type": "application/json",
        }

        if provider["key"]:
            headers["Authorization"] = f"Bearer {provider['key']}"

        # Build messages for chat completion
        messages = [
            {"role": "user", "content": prompt}
        ]

        payload = {
            "model": provider["model"],
            "messages": messages,
            "max_tokens": MAX_TOKENS,
        }
        if "gpt-oss" in provider["model"]:
            # Reasoning model: cap the hidden thinking phase. Roadmap JSON
            # generation doesn't need deep reasoning, and shorter reasoning
            # both speeds up responses and keeps the JSON under max_tokens.
            payload["reasoning_effort"] = os.getenv("LLM_REASONING_EFFORT", "low")

        try:
            response = requests.post(
                provider["url"],
                headers=headers,
                json=payload,
                # gpt-oss-120b on Logos routinely takes 130-160s per roadmap;
                # keep well above that so slow responses aren't cut off.
                timeout=300
            )
            response.raise_for_status()

            result = response.json()

            # ── Token usage tracking ───────────────────────────────────
            usage = result.get("usage", {})
            if usage:
                _log("INFO", "Token usage",
                     prompt_tokens=usage.get("prompt_tokens"),
                     completion_tokens=usage.get("completion_tokens"),
                     total_tokens=usage.get("total_tokens"),
                )
                self.last_usage = usage

            # Extract the response content
            if "choices" in result and len(result["choices"]) > 0:
                content = result["choices"][0]["message"]["content"]
                return content.strip()
            else:
                raise ValueError("Unexpected response format from API")

        except requests.RequestException as e:
            raise Exception(f"API request failed: {str(e)}")
        except (KeyError, IndexError, ValueError) as e:
            raise Exception(f"Failed to parse API response: {str(e)}")


# ---------------------------------------------------------------------------
# Prompt assembly. The prompt has a FIXED skeleton (the data block with the
# goal and the course list, plus section headers) and three admin-editable
# sections stored in user-service: role, instructions and response format.
# The defaults below are the fallback when a section is missing/blank, and
# they mirror the values SettingBootstrap seeds in user-service.
# ---------------------------------------------------------------------------
DEFAULT_ROLE = "You are an expert academic advisor creating a personalised learning roadmap."

DEFAULT_INSTRUCTIONS = """1. Select the most relevant courses from the list above to reach the student's goal.
2. Break the journey into clear milestones (e.g. "Complete foundational mathematics"). Also include external milestones that are not courses.
3. For each milestone, define concrete tasks the student should do. For course tasks, include the course code in brackets (e.g. "Enroll in [IN2064] Machine Learning").
4. Each milestone MUST contain at least 2–4 tasks. Tasks MUST belong to their milestone (nested structure)
5. Create at most 5 milestones. Keep titles and descriptions short (one sentence) — the response must stay compact.
6. Respond with ONLY valid JSON."""

DEFAULT_RESPONSE_FORMAT = """{
  "milestones": [
    {
      "title": "Milestone name",
      "description": "What this milestone achieves",
      "tasks": [
        {
          "title": "Task description",
          "completed": false
        }
      ]
    }
  ]
}"""


def build_prompt(goal: str, courses: str) -> str:
    """Assembles the prompt from the fixed skeleton + admin-editable sections."""
    return f"""{get_setting("promptRole", DEFAULT_ROLE)}

Student's learning goal: {goal}

Available courses in the catalogue:
{courses}

Instructions:
{get_setting("promptInstructions", DEFAULT_INSTRUCTIONS)}

Required JSON format:

{get_setting("promptResponseFormat", DEFAULT_RESPONSE_FORMAT)}

JSON response:
"""


llm = OpenAICompatibleLLM()

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def parse_llm_response(raw: str) -> RoadmapResponse:
    """
    Parses the LLM JSON output into a RoadmapResponse.
    Falls back to empty lists if the JSON is malformed.
    """
    try:
        cleaned = (
            raw.strip()
            .removeprefix("```json")
            .removeprefix("```")
            .removesuffix("```")
            .strip()
        )

        data = json.loads(cleaned)

        return RoadmapResponse.model_validate(data)

    except Exception as e:
        _log("ERROR", f"Failed to parse LLM response: {e}", raw_snippet=raw[:300])
        return RoadmapResponse(milestones=[])

# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@app.get("/health")
async def health_check():
    """Health check endpoint."""
    p = current_provider()
    return {
        "status": "healthy",
        "service": "LLM Roadmap Generation Service",
        "provider": p["name"],
        "model": p["model"],
        "api_key_configured": bool(p["key"]),
        "courses_indexed": len(_courses),
    }


@app.get("/usage")
async def get_usage(x_user_id: str = Header(...)):
    """
    Returns the caller's cumulative token usage and remaining quota.

    The user id comes from the gateway-injected, JWT-verified X-User-Id header,
    so the client cannot query another user's usage.
    """
    _reset_if_new_month()
    used = _user_token_usage[x_user_id]
    return {
        "user_id": x_user_id,
        "used": used,
        "limit": monthly_token_limit(),
        "remaining": max(0, monthly_token_limit() - used),
        "period": _usage_month,
    }


@app.post("/recommend", response_model=RoadmapResponse)
async def recommend(req: RoadmapRequest, user_id: str = "anonymous") -> RoadmapResponse:
    if not req.goal.strip():
        raise HTTPException(status_code=422, detail="goal cannot be empty")

    if len(req.goal) > MAX_GOAL_CHARS:
        raise HTTPException(
            status_code=422,
            detail=f"goal is too long ({len(req.goal)} chars) — maximum is {MAX_GOAL_CHARS}",
        )
    reserved = False
    total_tokens = 0
    t0 = time.time()      
    t_llm = time.time()

    try:
        # Check user limit before calling llm (skipped entirely when the
        # "tokenQuota" feature flag is disabled from the admin panel).
        # Add MAX_TOKENS (max output) so as an assumption of tokens used during request so that user cannot generate i+1 roadmaps
        if feature_enabled("tokenQuota"):
            with _user_locks[user_id]:
                check_user_limit(user_id)
                _user_token_usage[user_id] += MAX_TOKENS
                reserved = True

        t0 = time.time()
        _log("INFO", "Roadmap request received", goal=req.goal, model=current_provider()["model"])

        if not _courses:
            global _last_index_attempt
            if time.time() - _last_index_attempt > 60:
                _last_index_attempt = time.time()
                _log("WARN", "Course index empty — retrying fetch from course-service")
                count = build_index()
                if count > 0:
                    _log("INFO", f"Course index rebuilt: {count} courses")
                else:
                    _log("WARN", "Course index still empty — proceeding without courses")

        courses_str = filter_courses(req.goal)
        course_count = len([l for l in courses_str.splitlines() if l.strip().startswith("-")])
        _log("INFO", f"TF-IDF filtered {course_count} relevant courses", goal=req.goal)
        _log("INFO", f"Calling LLM ({current_provider()['model']})...", goal=req.goal)

        t_llm = time.time()
    
        raw = await llm.ainvoke(build_prompt(req.goal, courses_str))

        
        usage = dict(llm.last_usage)
        total_tokens = usage.get("total_tokens", 0)

        llm_ms = round((time.time() - t_llm) * 1000)
        _log("INFO", f"LLM responded in {llm_ms}ms", goal=req.goal, llm_ms=llm_ms)

        result = parse_llm_response(raw)

    except HTTPException:
        raise
    except Exception as e:
        # Catch everything (timeouts, connection errors, provider SDK errors),
        # not just RuntimeError — otherwise failures bypass the structured log.
        llm_ms = round((time.time() - t_llm) * 1000)
        _log("ERROR", f"LLM call failed after {llm_ms}ms: {e}", goal=req.goal, llm_ms=llm_ms)
        raise HTTPException(status_code=503, detail=str(e)) from e
    
    finally: 
        if reserved:
            with _user_locks[user_id]:
                _user_token_usage[user_id] -= MAX_TOKENS
                _user_token_usage[user_id] += total_tokens
            _log("INFO", f"User {user_id} used {total_tokens} tokens " f"({_user_token_usage[user_id]}/{monthly_token_limit()})")

    total_ms = round((time.time() - t0) * 1000)
    _log("INFO", f"Request done in {total_ms}ms — {len(result.milestones)} milestones", goal=req.goal, total_ms=total_ms)

    return result


@app.get("/logs")
async def get_logs():
    return {"logs": list(_logs)}


@app.get("/")
async def root():
    """Root endpoint with service information."""
    return {
        "service": "LLM Roadmap Service",
        "version": "2.0.0",
        "description": "Generates personalized roadmaps using TF-IDF filtering + LLM.",
        "endpoints": {
            "health": "/health",
            "recommend": "/recommend",
        }
    }

# Entry point for direct execution
if __name__ == "__main__":
    port = int(os.getenv("PORT", 8004))
    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=True)