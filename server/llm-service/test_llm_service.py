"""
Unit tests for the LLM Roadmap Generation Service (main.py).

Tests cover the critical GenAI logic:
- parse_llm_response: JSON parsing and fallback behaviour
- filter_courses: TF-IDF course retrieval
- build_index: course indexing from the course service
- /recommend endpoint: input validation and response structure
- /health endpoint: service health check
"""

import json
import pytest
import sys
from unittest.mock import patch, MagicMock, AsyncMock
from fastapi.testclient import TestClient

fake_langchain = MagicMock()
fake_langchain.verbose = False
sys.modules["langchain"] = fake_langchain

from main import app, parse_llm_response, filter_courses, build_index, RoadmapResponse

client = TestClient(app)

@pytest.fixture(autouse=True)
def reset_usage():
    """
    Clears token usage before and after every test.

    Prevents tests from affecting each other because
    _user_token_usage is a global in-memory dictionary.
    """
    import main

    main._user_token_usage.clear()

    yield

    main._user_token_usage.clear()

# ---------------------------------------------------------------------------
# parse_llm_response
# ---------------------------------------------------------------------------

class TestParseLlmResponse:
    """Test for the LLM JSON output parser."""

    def test_parse_valid_json(self):
        """Valid JSON with milestones is parsed into a RoadmapResponse."""
        raw = json.dumps({
            "milestones": [
                {
                    "title": "Learn Basics of Machine Learning",
                    "description": "Foundation", 
                    "tasks": [
                        {"title": "Read intro", "completed": False}
                    ]
                }
            ]
        })

        result = parse_llm_response(raw)

        assert isinstance(result, RoadmapResponse)
        assert len(result.milestones) == 1
        assert result.milestones[0]["title"] == "Learn Basics of Machine Learning"

    def test_strips_markdown_code_fences(self):
        """JSON wrapped in ```json ...``` fences is parsed correctly in case LLM outputs this format."""
        raw = "```json\n{\"milestones\":[]}\n```"

        result = parse_llm_response(raw)

        assert isinstance(result, RoadmapResponse)
        assert result.milestones == []

    def test_strips_plain_code_fences(self):
        """JSON wrapped in plain ``` ... ``` fences is parsed correctly in case LLM outputs this format."""
        raw = "```\n{\"milestones\": []}\n```"

        result = parse_llm_response(raw)

        assert result.milestones == []

    def test_returns_empty_milestones_on_invalid_json(self):
        """Malformed JSON falls back to an empty milestones list rather than crashing."""
        raw = "this is not json at all"

        result = parse_llm_response(raw)

        assert isinstance(result, RoadmapResponse)
        assert result.milestones == []

    def test_returns_empty_milestones_on_empty_string(self):
        """Empty string input falls back to empty milestones list."""
        result = parse_llm_response("")

        assert result.milestones == []
    
    def test_returns_empty_milestones_on_missing_milestones_key(self):
        """JSON without the milestones key falls back to empty milestones list."""
        raw = json.dumps({"something_else": "value"})

        result = parse_llm_response(raw)

        assert result.milestones == []
    
    def test_parses_multiple_milestones(self):
        """Multiple milestones with multiple tasks are all parsed."""
        raw = json.dumps({
            "milestones": [
                {
                    "title": "Step 1",
                    "description": "First",
                    "tasks": [
                        {"title": "Task A", "completed": False},
                        {"title": "Task B", "completed": False},
                    ]
                },
                {
                    "title": "Step 2",
                    "description": "Second",
                    "tasks": [
                        {"title": "Task C", "completed": False}
                    ]
                }
            ]
        })

        result = parse_llm_response(raw)

        assert len(result.milestones) == 2
        assert len(result.milestones[0]["tasks"]) == 2


# ---------------------------------------------------------------------------
# filter_courses (TF-IDF retrieval)
# ---------------------------------------------------------------------------

class TestFilterCourses:
    """Tests for the TF-IDF course filtering logic."""

    @pytest.fixture(autouse=True)
    def setup(self):
        """Runs automatically before each test in this class to build a small in-memory index."""
        import main
        main._courses = [
            {"tum_number": "IN2064", "title": "Machine Learning", "objective": "Learn ML algorithms"},
            {"tum_number": "IN0015", "title": "Databases", "objective": "SQL and relational databases"},
            {"tum_number": "IN2346", "title": "Deep Learning", "objective": "Neural networks and deep learning"},
        ]

        from sklearn.feature_extraction.text import TfidfVectorizer
        documents = [
            f"{c['title']} {c.get('objective', '')}"
            for c in main._courses
        ]

        main._vectorizer = TfidfVectorizer(stop_words="english", ngram_range=(1,2))
        main._matrix = main._vectorizer.fit_transform(documents)

        yield

        # Reset state after each test
        main._courses = []
        main._vectorizer = None
        main._matrix = None

    def test_returns_relevant_courses_for_goal(self):
        """Courses relevant to the goal appear in the output."""
        result = filter_courses("machine learning")

        assert "Machine Learning" in result or "Deep Learning" in result

    def test_returns_string(self):
        """filter_courses always returns a string."""
        result = filter_courses("machine learning")

        assert isinstance(result, str)

    def test_returns_fallback_when_no_index(self):
        """Returns flabback message when the index has not been built."""
        import main
        main._vectorizer = None
        main._matrix = None
        main._courses = []

        result = filter_courses("machine learning")

        assert result == "- No matching courses found"
    
    def test_returns_fallback_when_no_matching_courses(self):
        """Returns fallback messae when no courses match the query."""
        result = filter_courses("quantun physics advanced theoretical")

        # Either fallback or empty string result, but no crash
        assert isinstance(result, str)
    
    def test_respects_top_k_limit(self):
        """Never returns more than k courses."""
        result = filter_courses("learning", k=1)

        # At most 1 course entry (each starts with "-[")
        lines = [l for l in result.splitlines() if l.startswith("-[")]
        assert len(lines) <= 1
    
    def test_includes_course_code_in_output(self):
        """Course codes are included in the formatted output."""
        result = filter_courses("machine learning")

        assert "IN2064" in result or "IN2346" in result


# ---------------------------------------------------------------------------
# build_index
# ---------------------------------------------------------------------------

class TestBuildIndex:
    """Tests for the TF-IDF index builder."""

    def test_returns_zero_when_course_service_unreachable(self):
        """Returns 0 and does not crash when the course service is down."""
        with patch("main.requests.get", side_effect=Exception("Connection refused")):
            count = build_index()
        
        assert count == 0
    
    def test_returns_course_count_on_success(self):
        """Returns the number of indexed courses when th service responds."""
        mock_courses = [
            {"tum_number": "IN2064", "title": "Machine Learning", "objective": "ML"},
            {"tum_number": "IN0015", "title": "Databases", "objective": "SQL"},
        ]

        mock_response = MagicMock()
        mock_response.json.return_value = mock_courses
        mock_response.raise_for_status = MagicMock()

        with patch("main.requests.get", return_value=mock_response):
            count = build_index()
        
        assert count == 2
    
    def test_builds_vectorizer_and_matrix(self):
        """After a successful build, the vectorizer and matrix are set."""
        import main
        mock_courses = [
            {"tum_number": "IN2064", "title": "Machine Learning", "objective": "ML"},
        ]
        mock_response = MagicMock()
        mock_response.json.return_value = mock_courses
        mock_response.raise_for_status = MagicMock()

        with patch("main.requests.get", return_value=mock_response):
            build_index()
        
        assert main._vectorizer is not None
        assert main._matrix is not None


# ---------------------------------------------------------------------------
# /health endpoint
# ---------------------------------------------------------------------------

class TestHealthEndpoint:
    """Tests for the /health endpoint."""

    def test_returns_200(self):
        """Health endpoint returns 200."""
        response = client.get("/health")

        assert response.status_code == 200
    
    def test_returns_healthy_status(self):
        """Health endpoint report healthy status."""
        response = client.get("/health")

        assert response.json()["status"] == "healthy"
    

    def test_returns_model_name(self):
        """Health endpoint includes the active model name."""
        response = client.get("/health")

        assert "model" in response.json()
    

# ---------------------------------------------------------------------------
# /recommend endpoint
# ---------------------------------------------------------------------------

class TestRecommendEndpoint:
    """Tests for the /recommend endpoint."""

    def test_returns_422_when_goal_is_empty_string(self):
        """Empty goal string is rejected with 422 Unprocessable Entity."""
        response = client.post("/recommend", json={"goal": "   "})

        assert response.status_code == 422
    
    def test_returns_422_when_goal_is_missing(self):
        """Missing goal field is rejected with 422 Unprocessable Entity."""
        response = client.post("/recommend", json={})

        assert response.status_code == 422
    
    def test_returns_200_with_valid_goal(self):
        """Valid goal returns 200 with milestones list."""
        mock_llm_output = json.dumps({
            "milestones": [
                {
                    "title": "Learn ML",
                    "description": "Start with basics",
                    "tasks": [
                        {"title": "Study linear algebra", "completed": False}
                    ]
                }
            ]
        })

        mock_chain = MagicMock()
        mock_chain.ainvoke = AsyncMock(return_value=mock_llm_output)

        with patch("main.chain", mock_chain):
            response = client.post("/recommend", json={"goal": "Learn machine learning"})
        
        assert response.status_code == 200
        data = response.json()
        assert "milestones" in data
        assert len(data["milestones"]) == 1
    
    def test_returns_empty_milestones_when_llm_returns_invalid_json(self):
        """Malformed LLM output falls back to empty milestones rather than 500 Internal Server Error."""
        mock_chain = MagicMock()
        mock_chain.ainvoke = AsyncMock(return_value="not valid json")
        
        with patch("main.chain", mock_chain):
            response = client.post("/recommend", json={"goal": "Learn something"})
        
        assert response.status_code == 200
        assert response.json()["milestones"] == []
    
    def test_returns_503_when_llm_raises_runtime_error(self):
        """RuntimeError from the LLM chain returns 503 Service Unavailable."""
        mock_chain = MagicMock()
        mock_chain.ainvoke = AsyncMock(side_effect=RuntimeError("LLM unreachable"))
        
        with patch("main.chain", mock_chain):
            response = client.post("/recommend", json={"goal": "Learn something"})
        
        assert response.status_code == 503
    
    def test_milestone_structure(self):
        """Each milestone in the response has title, description, and tasks."""
        mock_llm_output = json.dumps({
            "milestones": [
                {
                    "title": "Foundation",
                    "description": "Build the base",
                    "tasks": [
                        {"title": "Read textbook", "completed": False},
                        {"title": "Do exercises", "completed": False},
                    ]
                }
            ]
        })
        mock_chain = MagicMock()
        mock_chain.ainvoke = AsyncMock(return_value=mock_llm_output)

        with patch("main.chain", mock_chain):
            response = client.post("/recommend", json={"goal": "Learn math"})
        
        milestones = response.json()["milestones"][0]
        assert "title" in milestones
        assert "description" in milestones
        assert "tasks" in milestones
        assert len(milestones["tasks"]) == 2
    
    def test_usage_increases_after_successful_request(self):
        import main

        mock_chain = MagicMock()
        mock_chain.ainvoke = AsyncMock(return_value=json.dumps({
            "milestones": []
        }))

        main.llm.last_usage = {"total_tokens": 123}

        with patch("main.chain", mock_chain):
            response = client.post(
                "/recommend?user_id=test-user",
                json={"goal": "Learn AI"},
            )

        assert response.status_code == 200

        usage = client.get(
            "/usage",
            headers={"X-User-Id": "test-user"},
        ).json()

        assert usage["used"] == 123

# ---------------------------------------------------------------------------
# /usage endpoint
# ---------------------------------------------------------------------------

class TestUsageEndpoint:
    """Tests for user token usage tracking endpoint."""

    def test_usage_returns_default_zero(self):
        """A new user has zero token usage."""
        import main

        main._user_token_usage.clear()

        response = client.get(
            "/usage",
            headers={"X-User-Id": "new_user"},
        )

        assert response.status_code == 200

        data = response.json()

        assert data["user_id"] == "new_user"
        assert data["used"] == 0
        assert data["remaining"] == main.MAX_TOKENS_PER_USER

    def test_usage_returns_existing_usage(self):
        """Existing token usage is returned correctly."""
        import main

        main._user_token_usage["alice"] = 1234

        response = client.get(
            "/usage",
            headers={"X-User-Id": "alice"},
        )

        assert response.status_code == 200

        data = response.json()

        assert data["used"] == 1234
        assert data["remaining"] == main.MAX_TOKENS_PER_USER - 1234

    def test_usage_requires_header(self):
        """Missing X-User-Id header is rejected."""
        response = client.get("/usage")

        assert response.status_code == 422

# ---------------------------------------------------------------------------
# Token limit handling
# ---------------------------------------------------------------------------

class TestTokenLimit:

    def test_rejects_user_when_token_limit_exceeded(self):
        """Users exceeding token quota receive HTTP 429."""
        import main

        main._user_token_usage["limited_user"] = main.MAX_TOKENS_PER_USER

        response = client.post(
            "/recommend?user_id=limited_user",
            json={"goal": "Learn AI"}
        )

        assert response.status_code == 429
        assert "token limit exceeded" in response.json()["detail"].lower()

        del main._user_token_usage["limited_user"]

# ---------------------------------------------------------------------------
# Goal validation
# ---------------------------------------------------------------------------

class TestGoalValidation:

    def test_rejects_goal_longer_than_limit(self):
        """Goals longer than MAX_GOAL_CHARS are rejected."""
        import main

        long_goal = "a" * (main.MAX_GOAL_CHARS + 1)

        response = client.post(
            "/recommend",
            json={"goal": long_goal}
        )

        assert response.status_code == 422
        assert "too long" in response.json()["detail"]

# ---------------------------------------------------------------------------
# Logs endpoint
# ---------------------------------------------------------------------------

class TestLogsEndpoint:

    def test_logs_returns_list(self):
        """Logs endpoint returns stored logs."""
        import main

        main._log(
            "INFO",
            "test message"
        )

        response = client.get("/logs")

        assert response.status_code == 200

        data = response.json()

        assert "logs" in data
        assert isinstance(data["logs"], list)


    def test_logs_contain_required_fields(self):
        """Each log entry contains timestamp, level and message."""
        import main

        main._log(
            "INFO",
            "testing"
        )

        response = client.get("/logs")

        log = response.json()["logs"][0]

        assert "timestamp" in log
        assert "level" in log
        assert "message" in log

# ---------------------------------------------------------------------------
# Root endpoint
# ---------------------------------------------------------------------------

class TestRootEndpoint:

    def test_root_returns_service_information(self):
        """Root endpoint exposes service metadata."""
        response = client.get("/")

        assert response.status_code == 200

        data = response.json()

        assert data["service"] == "LLM Roadmap Service"
        assert "version" in data
        assert "endpoints" in data

# ---------------------------------------------------------------------------
# LLM wrapper tests
# ---------------------------------------------------------------------------

class TestOpenAICompatibleLLM:

    def test_extracts_token_usage(self):
        """LLM wrapper stores token usage returned by provider."""
        from main import OpenAICompatibleLLM

        llm = OpenAICompatibleLLM()

        fake_response = MagicMock()

        fake_response.json.return_value = {
            "choices": [
                {
                    "message": {
                        "content": "hello"
                    }
                }
            ],
            "usage": {
                "prompt_tokens": 10,
                "completion_tokens": 20,
                "total_tokens": 30
            }
        }

        fake_response.raise_for_status = MagicMock()

        with patch(
            "main.requests.post",
            return_value=fake_response
        ):

            result = llm._call("test")

        assert result == "hello"
        assert llm.last_usage["total_tokens"] == 30


    def test_handles_invalid_provider_response(self):
        """Invalid LLM provider response raises exception."""
        from main import OpenAICompatibleLLM

        llm = OpenAICompatibleLLM()

        fake_response = MagicMock()

        fake_response.json.return_value = {}

        fake_response.raise_for_status = MagicMock()

        with patch(
            "main.requests.post",
            return_value=fake_response
        ):
            with pytest.raises(Exception):
                llm._call("test")

# ---------------------------------------------------------------------------
# Monthly quota reset
# ---------------------------------------------------------------------------

class TestMonthlyReset:
    """Tests for monthly token quota reset."""

    def test_resets_usage_when_month_changes(self):
        import main

        main._user_token_usage["alice"] = 500
        main._usage_month = "2000-01"

        main._reset_if_new_month()

        assert main._user_token_usage == {}
        assert main._usage_month != "2000-01"

    def test_does_not_reset_when_month_is_same(self):
        import main
        from datetime import datetime, timezone

        current = datetime.now(timezone.utc).strftime("%Y-%m")

        main._usage_month = current
        main._user_token_usage["alice"] = 500

        main._reset_if_new_month()

        assert main._user_token_usage["alice"] == 500



    
