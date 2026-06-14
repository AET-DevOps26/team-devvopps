import os
import json
import requests
import uvicorn
from typing import Any, List, Optional
from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from langchain_core.prompts import PromptTemplate
from langchain_core.language_models.llms import LLM
from langchain_core.callbacks.manager import CallbackManagerForLLMRun
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
import numpy as np

# Environment configuration: pick the upstream provider based on env.
#
# If LOGOS_API_KEY is set we point at the TUM Logos endpoint and default
# to the openai/gpt-oss-120b model. Otherwise we default to LM Studio
# running on the host (host.docker.internal:1234 from inside Docker on
# macOS/Windows) with gemma-4-e2b. The LM Studio path can still be
# overridden by setting LLM_API_URL / LLM_MODEL explicitly.
LOGOS_API_KEY = os.getenv("LOGOS_API_KEY")
GROQ_API_KEY  = os.getenv("GROQ_API_KEY")

if LOGOS_API_KEY:
    # Logos profile: TUM-hosted gpt-oss-120b. Off-campus needs eduVPN.
    # Hardcoded so a single LOGOS_API_KEY in .env is the only switch
    # students need to flip.
    API_URL = "https://logos.aet.cit.tum.de/v1/chat/completions"
    MODEL_NAME = "openai/gpt-oss-120b"
    LLM_API_KEY = LOGOS_API_KEY
elif GROQ_API_KEY:
    # Groq profile: free tier, llama-3.3-70b-versatile.
    API_URL = "https://api.groq.com/openai/v1/chat/completions"
    MODEL_NAME = "llama-3.3-70b-versatile"
    LLM_API_KEY = GROQ_API_KEY
else:
    # LM Studio profile: local model on host. Defaults match compose.yml
    # so both `docker compose up` and `python main.py` work.
    API_URL = os.getenv("LLM_API_URL", "http://localhost:1234/v1/chat/completions")
    MODEL_NAME = os.getenv("LLM_MODEL", "gemma-4-e2b")
    # LM Studio doesn't require a key; CHAIR_API_KEY is left for back-compat.
    LLM_API_KEY = os.getenv("LLM_API_KEY") or os.getenv("CHAIR_API_KEY")

COURSE_SERVICE_URL = os.getenv("COURSE_SERVICE_URL", "http://course-service:8082/courses")
TOP_K = int(os.getenv("TOP_K", "30"))

# ---------------------------------------------------------------------------
# TF-IDF index: built once at startup, held in memory.
# Replaces keyword search — finds the TOP_K most relevant courses
# for a given goal without burning LLM tokens on all 929 courses.
# ---------------------------------------------------------------------------
_courses: List[dict] = []
_vectorizer: Optional[TfidfVectorizer] = None
_matrix = None


def build_index() -> int:
    global _courses, _vectorizer, _matrix
    try:
        resp = requests.get(COURSE_SERVICE_URL, timeout=15)
        resp.raise_for_status()
        _courses = resp.json()
    except Exception as e:
        print(f"[RAG] Could not fetch courses: {e}")
        return 0

    documents = []
    for c in _courses:
        title = c.get("title", "")
        objective = (c.get("objective") or c.get("content") or "")[:300]
        documents.append(f"{title} {objective}")

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
# App lifecycle
# ---------------------------------------------------------------------------
@asynccontextmanager
async def lifespan(app: FastAPI):
    print(f"[RAG] Building TF-IDF index... (model: {MODEL_NAME})")
    count = build_index()
    print(f"[RAG] Ready — {count} courses indexed.")
    yield


# Create FastAPI application instance
app = FastAPI(
    title="LLM Recommendation Service",
    description="Service that generates personalized learning roadmaps using an LLM",
    version="2.0.0",
    lifespan=lifespan,
)

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

chain = PromptTemplate(
    input_variables=["goal", "courses"],
    template=_PROMPT,
) | OpenAICompatibleLLM()

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

    except Exception:
        return RoadmapResponse(milestones=[])

# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {"status": "healthy", "service": "LLM Roadmap Generation Service", "model": MODEL_NAME}


@app.post("/recommend", response_model=RoadmapResponse)
async def recommend(req: RoadmapRequest) -> RoadmapResponse:
    if not req.goal.strip():
        raise HTTPException(status_code=422, detail="goal cannot be empty")

    # Use TF-IDF to find the most relevant courses (replaces keyword search)
    courses_str = filter_courses(req.goal)

    # Call LLM
    try:
        raw = await chain.ainvoke({
            "goal": req.goal,
            "courses": courses_str
        })
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e)) from e

    return parse_llm_response(raw)


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
