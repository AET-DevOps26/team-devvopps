import os
import json
import requests
from typing import Dict, Any, List, Optional
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from langchain_core.prompts import PromptTemplate
from langchain_core.language_models.llms import LLM
from langchain_core.callbacks.manager import CallbackManagerForLLMRun

# Environment configuration: pick the upstream provider based on env.
#
# If LOGOS_API_KEY is set we point at the TUM Logos endpoint and default
# to the openai/gpt-oss-120b model. Otherwise we default to LM Studio
# running on the host (host.docker.internal:1234 from inside Docker on
# macOS/Windows) with gemma-4-e2b. The LM Studio path can still be
# overridden by setting LLM_API_URL / LLM_MODEL explicitly.
LOGOS_API_KEY = os.getenv("LOGOS_API_KEY")
if LOGOS_API_KEY:
    # Logos profile: TUM-hosted gpt-oss-120b. Off-campus needs eduVPN.
    # Hardcoded so a single LOGOS_API_KEY in .env is the only switch
    # students need to flip.
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

COURSE_SERVICE_URL = os.getenv("COURSE_SERVICE_URL", "http://course-service:8082/courses")

# Create FastAPI application instance
app = FastAPI(
    title="LLM Recommendation Service",
    description="Service that generates personalized food recommendations using an LLM",
    version="1.0.0"
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
    milestones: List[str] = Field(default=[], description="Learning milestones")
    tasks:      List[str] = Field(default=[], description="Concrete tasks to complete the milestones")


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
                timeout=30
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


# Initialize the LLM
KEYWORD_PROMPT = """
You are a keyword extraction engine for an academic course recommendation system.

Extract the most relevant search keywords from the student's learning goal.

Rules:
- Return ONLY valid JSON
- No markdown, no explanation
- Keywords should be short (1–3 words)
- Focus on technical concepts, skills, and domains
- Do NOT repeat stopwords or generic words like "learn", "study"

Student goal:
{goal}

Return format:
{
  "keywords": [
    "keyword 1",
    "keyword 2",
    "keyword 3"
  ]
}
"""


_PROMPT = """You are an expert academic advisor creating a personalised learning roadmap.
 
Student's learning goal: {goal}
 
Available courses in the catalogue:
{courses}
 
Instructions:
1. Select the most relevant courses from the list above to reach the student's goal.
2. Break the journey into clear milestones (e.g. "Complete foundational mathematics"). Also include external milestones that are not courses.
3. For each milestone, define concrete tasks the student should do (e.g. "Take course: Linear Algebra").
4. Each milestone MUST contain at least 2–4 tasks. Tasks MUST belong to their milestone (nested structure)
5. Respond with ONLY valid JSON.
 
Required JSON format:

{
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
}

JSON response:
"""

keyword_chain = PromptTemplate(
    input_variables=["goal"],
    template=KEYWORD_PROMPT,
) | OpenAICompatibleLLM()

chain = PromptTemplate(
    input_variables=["goal", "courses"],
    template=_PROMPT,
) | OpenAICompatibleLLM()

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
def parse_keywords(raw: str) -> List[str]:
    try:
        cleaned = (
            raw.strip()
            .removeprefix("```json")
            .removeprefix("```")
            .removesuffix("```")
            .strip()
        )

        data = json.loads(cleaned)
        keywords = data.get("keywords", [])

        if not isinstance(keywords, list):
            return []

        return [k.strip() for k in keywords if isinstance(k, str) and k.strip()]

    except Exception:
        return []
    
async def extract_keywords_llm(goal: str) -> List[str]:
    raw = await keyword_chain.ainvoke({"goal": goal})
    return parse_keywords(raw)

def search_course_by_keyword(keyword: str) -> List[Dict[str, Any]]:
    try:
        resp = requests.get(
            f"{COURSE_SERVICE_URL}/search",
            params={"title": keyword},
            timeout=10
        )
        resp.raise_for_status()
        data = resp.json()

        # backend returns single Course → wrap into list
        return [data] if isinstance(data, dict) else []

    except Exception as e:
        print(f"Warning: search failed for '{keyword}': {e}")
        return []
 
 
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
    return {"status": "healthy", "service": "LLM Roadmap Generation Service"}


@app.post("/generate", response_model=RoadmapResponse)
async def generate(req: RoadmapRequest) -> RoadmapResponse:
    if not req.goal.strip():
        raise HTTPException(status_code=422, detail="goal cannot be empty")

    # Extract keywords
    keywords = await extract_keywords_llm(req.goal)

    # Search courses from course-service
    all_courses = []
    for kw in keywords:
        results = search_course_by_keyword(kw)
        all_courses.extend(results)

    # Deduplicate by course_id
    unique_courses = {
        c["course_id"]: c for c in all_courses if "course_id" in c
    }.values()

    # Convert enriched course data into LLM input
    courses_str = "\n".join(
        f"- {c.get('title')} | {c.get('content','')[:200]}"
        for c in unique_courses
    )

    # fallback if nothing found
    if not courses_str.strip():
        courses_str = "- No matching courses found"

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
        "version": "1.0.0",
        "description": "Generates personalized roadmaps using LangChain against an OpenAI-compatible LLM endpoint (e.g. LM Studio).",
        "endpoints": {
            "health": "/health",
            "generate": "/generate",
        }
    }

# Entry point for direct execution
if __name__ == "__main__":
    port = int(os.getenv("PORT", 8004))
    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=True)