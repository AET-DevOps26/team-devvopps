import asyncio
import os
import json
import time
import requests
import uvicorn
from collections import deque
from collections import defaultdict
from datetime import datetime, timezone
from typing import Any, List, Optional
from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException
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
from langchain_core.prompts import PromptTemplate
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

if GROQ_API_KEY:
    # Groq profile: free tier, llama-3.3-70b-versatile.
    API_URL = "https://api.groq.com/openai/v1/chat/completions"
    MODEL_NAME = "llama-3.3-70b-versatile"
    LLM_API_KEY = GROQ_API_KEY
elif LOGOS_API_KEY:
    # Logos profile: TUM-hosted gpt-oss-120b. Off-campus needs eduVPN.
    API_URL = "https://logos.aet.cit.tum.de/v1/chat/completions"
    MODEL_NAME = "openai/gpt-oss-120b"
    LLM_API_KEY = LOGOS_API_KEY
else:
    # LM Studio profile: local model on host. Defaults match compose.yml
    # so both `docker compose up` and `python main.py` work.
    API_URL = os.getenv("LLM_API_URL", "http://localhost:1234/v1/chat/completions")
    MODEL_NAME = os.getenv("LLM_MODEL", "gemma-4-e2b")
    # LM Studio doesn't require a key; CHAIR_API_KEY is left for back-compat.
    LLM_API_KEY = os.getenv("LLM_API_KEY") or os.getenv("CHAIR_API_KEY")

# URL of the course-service REST API used to fetch the course catalogue.
COURSE_SERVICE_URL = os.getenv("COURSE_SERVICE_URL", "http://course-service:8082/courses")

# Number of courses passed to the LLM after TF-IDF filtering.
TOP_K = int(os.getenv("TOP_K", "30"))

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

# One request uses approx. 1000 tokens. Setting max tokens per user to 30000 
# allows the user to generate approx. 30 roadmaps
MAX_TOKENS_PER_USER = 30000  # max cumulative tokens per user

_user_token_usage: dict = defaultdict(int)  # userId -> total tokens used

def check_user_limit(user_id: str) -> None:
    """Raises HTTPException if user has exceeded their token limit."""
    current = _user_token_usage[user_id]
    if current >= MAX_TOKENS_PER_USER:
        raise HTTPException(
            status_code=429,
            detail=f"Token limit exceeded. You have used {current}/{MAX_TOKENS_PER_USER} tokens."
        )

# ---------------------------------------------------------------------------
# App lifecycle
# ---------------------------------------------------------------------------
@asynccontextmanager
async def lifespan(app: FastAPI):
    _log("INFO", f"Starting up (model: {MODEL_NAME})")
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

    api_url: str = API_URL
    api_key: Optional[str] = LLM_API_KEY
    model_name: str = MODEL_NAME
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
        headers = {
            "Content-Type": "application/json",
        }

        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"

        # Build messages for chat completion
        messages = [
            {"role": "user", "content": prompt}
        ]

        payload = {
            "model": self.model_name,
            "messages": messages,
        }

        try:
            response = requests.post(
                self.api_url,
                headers=headers,
                json=payload,
                timeout=120
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


_PROMPT = """You are an expert academic advisor creating a personalised learning roadmap.

Student's learning goal: {goal}

Available courses in the catalogue:
{courses}

Instructions:
1. Select the most relevant courses from the list above to reach the student's goal.
2. Break the journey into clear milestones (e.g. "Complete foundational mathematics"). Also include external milestones that are not courses.
3. For each milestone, define concrete tasks the student should do. For course tasks, include the course code in brackets (e.g. "Enroll in [IN2064] Machine Learning").
4. Each milestone MUST contain at least 2–4 tasks. Tasks MUST belong to their milestone (nested structure)
5. Respond with ONLY valid JSON.

Required JSON format:

{{
  "milestones": [
    {{
      "title": "Milestone name",
      "description": "What this milestone achieves",
      "tasks": [
        {{
          "title": "Task description",
          "completed": false
        }}
      ]
    }}
  ]
}}

JSON response:
"""
llm = OpenAICompatibleLLM()

chain = PromptTemplate(
    input_variables=["goal", "courses"],
    template=_PROMPT,
) | llm

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
    return {"status": "healthy", "service": "LLM Roadmap Generation Service", "model": MODEL_NAME}


@app.post("/recommend", response_model=RoadmapResponse)
async def recommend(req: RoadmapRequest, user_id: str = "anonymous") -> RoadmapResponse:
    if not req.goal.strip():
        raise HTTPException(status_code=422, detail="goal cannot be empty")

    # Check user limit before calling llm, but because we do not know token usage of the llm call, 
    # user will have n+1 tries (will exceed usage by one request, next time request is denied)
    check_user_limit(user_id)

    t0 = time.time()
    _log("INFO", "Roadmap request received", goal=req.goal, model=MODEL_NAME)

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
    _log("INFO", f"Calling LLM ({MODEL_NAME})...", goal=req.goal)

    t_llm = time.time()
    try:
        raw = await chain.ainvoke({"goal": req.goal, "courses": courses_str})
        llm_ms = round((time.time() - t_llm) * 1000)
        _log("INFO", f"LLM responded in {llm_ms}ms", goal=req.goal, llm_ms=llm_ms)
    except Exception as e:
        # Catch everything (timeouts, connection errors, provider SDK errors),
        # not just RuntimeError — otherwise failures bypass the structured log.
        llm_ms = round((time.time() - t_llm) * 1000)
        _log("ERROR", f"LLM call failed after {llm_ms}ms: {e}", goal=req.goal, llm_ms=llm_ms)
        raise HTTPException(status_code=503, detail=str(e)) from e

    result = parse_llm_response(raw)

    total_tokens = llm.last_usage.get("total_tokens", 0)
    _user_token_usage[user_id] += total_tokens
    _log("INFO", f"User {user_id} used {total_tokens} tokens " f"({_user_token_usage[user_id]}/{MAX_TOKENS_PER_USER})"
)

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
