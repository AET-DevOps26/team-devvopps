"""
Unit tests for the LLM Roadmap Generation Service (main.py).

Tests cover the critical GenAI logic:
- parse_llm_response: JSON parsing and fallback behaviour
- filter_courses: TF-IDF course retrieval
- build_index: course indexing from the course service
- /recommend endpoint: input validation and response structure
- /health endpoint: service health check
- /usage endpoint: per-user token usage tracking
- /logs endpoint: in-memory log store
- Token limit handling and monthly reset
- OpenAICompatibleLLM wrapper
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
import main

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
    """Tests for the LLM JSON output parser."""

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
        """JSON wrapped in ```json ...``` fences is parsed correctly."""
        raw = "```json\n{\"milestones\":[]}\n```"

        result = parse_llm_response(raw)

        assert isinstance(result, RoadmapResponse)
        assert result.milestones == []

    def test_strips_plain_code_fences(self):
        """JSON wrapped in plain ``` ... ``` fences is parsed correctly."""
        raw = "```\n{\"milestones\": []}\n```"

        result = parse_llm_response(raw)

        assert result.milestones == []

    def test_returns_empty_milestones_on_invalid_json(self):
        """Malformed JSON falls back to an empty milestones list rather than crashing."""
        result = parse_llm_response("this is not json at all")

        assert isinstance(result, RoadmapResponse)
        assert result.milestones == []

    def test_returns_empty_milestones_on_empty_string(self):
        """Empty string input falls back to empty milestones list."""
        result = parse_llm_response("")

        assert result.milestones == []

    def test_returns_empty_milestones_on_missing_milestones_key(self):
        """JSON without the milestones key falls back to empty milestones list."""
        result = parse_llm_response(json.dumps({"something_else": "value"}))

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
        """Builds a small in-memory index before each test in this class."""
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
        main._vectorizer = TfidfVectorizer(stop_words="english", ngram_range=(1, 2))
        main._matrix = main._vectorizer.fit_transform(documents)

        yield

        main._courses = []
        main._vectorizer = None
        main._matrix = None

    def test_returns_relevant_courses_for_goal(self):
        """Courses relevant to the goal appear in the output."""
        result = filter_courses("machine learning")

        assert "Machine Learning" in result or "Deep Learning" in result

    def test_returns_string(self):
        """filter_courses always returns a string."""
        assert isinstance(filter_courses("machine learning"), str)

    def test_returns_fallback_when_no_index(self):
        """Returns fallback message when the index has not been built."""
        import main
        main._vectorizer = None
        main._matrix = None
        main._courses = []

        assert filter_courses("machine learning") == "- No matching courses found"

    def test_returns_fallback_when_no_matching_courses(self):
        """Returns a string (fallback or empty) when no courses match — no crash."""
        result = filter_courses("quantum physics advanced theoretical")

        assert isinstance(result, str)

    def test_respects_top_k_limit(self):
        """Never returns more than k courses."""
        result = filter_courses("learning", k=1)

        lines = [l for l in result.splitlines() if l.startswith("-")]
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
            assert build_index() == 0

    def test_returns_course_count_on_success(self):
        """Returns the number of indexed courses when the service responds."""
        mock_courses = [
            {"tum_number": "IN2064", "title": "Machine Learning", "objective": "ML"},
            {"tum_number": "IN0015", "title": "Databases", "objective": "SQL"},
        ]
        mock_response = MagicMock()
        mock_response.json.return_value = mock_courses
        mock_response.raise_for_status = MagicMock()

        with patch("main.requests.get", return_value=mock_response):
            assert build_index() == 2

    def test_builds_vectorizer_and_matrix(self):
        """After a successful build, the vectorizer and matrix are populated."""
        import main
        mock_response = MagicMock()
        mock_response.json.return_value = [
            {"tum_number": "IN2064", "title": "Machine Learning", "objective": "ML"},
        ]
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
        assert client.get("/health").status_code == 200

    def test_returns_healthy_status(self):
        """Health endpoint reports healthy status."""
        assert client.get("/health").json()["status"] == "healthy"

    def test_returns_model_name(self):
        """Health endpoint includes the active model name."""
        assert "model" in client.get("/health").json()

    def test_returns_provider_name(self):
        """Health endpoint includes the active provider name."""
        assert "provider" in client.get("/health").json()

    def test_returns_courses_indexed(self):
        """Health endpoint reports how many courses are indexed."""
        assert "courses_indexed" in client.get("/health").json()


# ---------------------------------------------------------------------------
# /recommend endpoint
# ---------------------------------------------------------------------------

class TestRecommendEndpoint:
    """Tests for the /recommend endpoint."""

    def test_returns_422_when_goal_is_empty_string(self):
        """Whitespace-only goal is rejected with 422."""
        assert client.post("/recommend", json={"goal": "   "}).status_code == 422

    def test_returns_422_when_goal_is_missing(self):
        """Missing goal field is rejected with 422."""
        assert client.post("/recommend", json={}).status_code == 422

    def test_returns_200_with_valid_goal(self):
        """Valid goal returns 200 with a milestones list."""
        mock_output = json.dumps({
            "milestones": [
                {
                    "title": "Learn ML",
                    "description": "Start with basics",
                    "tasks": [{"title": "Study linear algebra", "completed": False}]
                }
            ]
        })

        with patch.object(
        main.OpenAICompatibleLLM,
        "ainvoke",
        new=AsyncMock(return_value=mock_output),
            ), \
            patch("main.feature_enabled", return_value=False):

            response = client.post("/recommend", json={"goal": "Learn machine learning"})

        assert response.status_code == 200
        data = response.json()
        assert "milestones" in data
        assert len(data["milestones"]) == 1

    def test_returns_empty_milestones_when_llm_returns_invalid_json(self):
        """Malformed LLM output falls back to empty milestones rather than 500."""
        with patch.object(
                main.OpenAICompatibleLLM,
                "ainvoke",
                new=AsyncMock(return_value="not valid json"),
            ), \
             patch("main.feature_enabled", return_value=False):
            response = client.post("/recommend", json={"goal": "Learn something"})

        assert response.status_code == 200
        assert response.json()["milestones"] == []

    def test_returns_503_when_llm_raises_exception(self):
        """Exception from the LLM returns 503 Service Unavailable."""
        with patch.object(
                main.OpenAICompatibleLLM,
                "ainvoke",
                new=AsyncMock(side_effect=RuntimeError("LLM unreachable")),
            ), \
            patch("main.feature_enabled", return_value=False):
            response = client.post("/recommend", json={"goal": "Learn something"})

        assert response.status_code == 503

    def test_milestone_structure(self):
        """Each milestone has title, description, and tasks."""
        mock_output = json.dumps({
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

        with patch.object(
            main.OpenAICompatibleLLM,
            "ainvoke",
            new=AsyncMock(return_value=mock_output),
        ), \
             patch("main.feature_enabled", return_value=False):
            response = client.post("/recommend", json={"goal": "Learn math"})

        milestone = response.json()["milestones"][0]
        assert "title" in milestone
        assert "description" in milestone
        assert "tasks" in milestone
        assert len(milestone["tasks"]) == 2

    def test_usage_increases_after_successful_request(self):
        """Token usage is recorded for the requesting user after a successful call."""
        import main

        main._user_token_usage.clear()

        mock_output = json.dumps({"milestones": []})

        async def mock_ainvoke(*args, **kwargs):
            main.llm.last_usage = {
                "prompt_tokens": 50,
                "completion_tokens": 73,
                "total_tokens": 123,
            }
            return mock_output

        with patch.object(
                main.OpenAICompatibleLLM,
                "ainvoke",
                new=mock_ainvoke,
            ), \
            patch("main.feature_enabled", side_effect=lambda name, **kw: name == "tokenQuota"):

            response = client.post(
                "/recommend",
                json={"goal": "Learn AI"},
                headers={"X-User-Id": "test-user"},
            )

        assert response.status_code == 200
        assert main._user_token_usage["test-user"] == 123


# ---------------------------------------------------------------------------
# /usage endpoint
# ---------------------------------------------------------------------------

class TestUsageEndpoint:
    """Tests for user token usage tracking endpoint."""

    def test_usage_returns_zero_for_new_user(self):
        """A new user has zero token usage."""
        import main
        main._user_token_usage.clear()

        response = client.get("/usage", headers={"X-User-Id": "new_user"})

        assert response.status_code == 200
        data = response.json()
        assert data["user_id"] == "new_user"
        assert data["used"] == 0
        assert data["remaining"] == data["limit"]

    def test_usage_returns_existing_usage(self):
        """Existing token usage is returned correctly."""
        import main
        main._user_token_usage["alice"] = 1234

        response = client.get("/usage", headers={"X-User-Id": "alice"})

        assert response.status_code == 200
        data = response.json()
        assert data["used"] == 1234
        assert data["remaining"] == data["limit"] - 1234

    def test_usage_requires_header(self):
        """Missing X-User-Id header is rejected with 422."""
        assert client.get("/usage").status_code == 422

    def test_usage_response_contains_period(self):
        """Usage response includes the current billing period."""
        response = client.get("/usage", headers={"X-User-Id": "alice"})

        assert "period" in response.json()


# ---------------------------------------------------------------------------
# Token limit handling
# ---------------------------------------------------------------------------

class TestTokenLimit:
    """Tests for monthly token quota enforcement."""

    def test_rejects_user_when_token_limit_exceeded(self):
        """Users exceeding token quota receive HTTP 429."""
        import main

        # Set usage to the full limit so the next request is rejected.
        limit = main.monthly_token_limit()
        main._user_token_usage["limited_user"] = limit

        with patch("main.feature_enabled", side_effect=lambda name, **kw: name == "tokenQuota"):
            response = client.post(
                "/recommend",
                json={"goal": "Learn AI"},
                headers={"X-User-Id": "limited_user"},
            )

        assert response.status_code == 429
        assert "token limit exceeded" in response.json()["detail"].lower()

    def test_allows_request_when_quota_flag_disabled(self):
        """Request succeeds even with zero remaining tokens when tokenQuota flag is off."""
        import main

        limit = main.monthly_token_limit()
        main._user_token_usage["user"] = limit

        mock_output = json.dumps({"milestones": []})

        with patch.object(
                main.OpenAICompatibleLLM,
                "ainvoke",
                new=AsyncMock(return_value=mock_output),
            ), \
             patch("main.feature_enabled", return_value=False):
            response = client.post("/recommend", json={"goal": "Learn AI"})

        assert response.status_code == 200


# ---------------------------------------------------------------------------
# Goal validation
# ---------------------------------------------------------------------------

class TestGoalValidation:
    """Tests for goal input validation."""

    def test_rejects_goal_longer_than_limit(self):
        """Goals longer than MAX_GOAL_CHARS are rejected with 422."""
        import main

        long_goal = "a" * (main.MAX_GOAL_CHARS + 1)
        response = client.post("/recommend", json={"goal": long_goal})

        assert response.status_code == 422
        assert "too long" in response.json()["detail"]

    def test_accepts_goal_at_exact_limit(self):
        """Goals exactly at MAX_GOAL_CHARS are accepted."""
        import main

        goal = "a" * main.MAX_GOAL_CHARS
        mock_output = json.dumps({"milestones": []})

        with patch.object(
                main.OpenAICompatibleLLM,
                "ainvoke",
                new=AsyncMock(return_value=mock_output),
            ), \
             patch("main.feature_enabled", return_value=False):
            response = client.post("/recommend", json={"goal": goal})

        assert response.status_code == 200


# ---------------------------------------------------------------------------
# /logs endpoint
# ---------------------------------------------------------------------------

class TestLogsEndpoint:
    """Tests for the in-memory log store endpoint."""

    def test_logs_returns_200(self):
        """Logs endpoint returns 200."""
        assert client.get("/logs").status_code == 200

    def test_logs_returns_list(self):
        """Logs endpoint returns a list under the 'logs' key."""
        import main
        main._log("INFO", "test message")

        data = client.get("/logs").json()
        assert "logs" in data
        assert isinstance(data["logs"], list)

    def test_logs_contain_required_fields(self):
        """Each log entry contains timestamp, level, and message."""
        import main
        main._log("INFO", "testing")

        log = client.get("/logs").json()["logs"][0]
        assert "timestamp" in log
        assert "level" in log
        assert "message" in log

    def test_logs_are_newest_first(self):
        """Log entries are returned newest first."""
        import main
        main._log("INFO", "first")
        main._log("INFO", "second")

        logs = client.get("/logs").json()["logs"]
        assert logs[0]["message"] == "second"
        assert logs[1]["message"] == "first"


# ---------------------------------------------------------------------------
# Root endpoint
# ---------------------------------------------------------------------------

class TestRootEndpoint:
    """Tests for the root endpoint."""

    def test_root_returns_service_information(self):
        """Root endpoint exposes service metadata."""
        response = client.get("/")

        assert response.status_code == 200
        data = response.json()
        assert data["service"] == "LLM Roadmap Service"
        assert "version" in data
        assert "endpoints" in data


# ---------------------------------------------------------------------------
# OpenAICompatibleLLM wrapper
# ---------------------------------------------------------------------------

class TestOpenAICompatibleLLM:
    """Tests for the custom LangChain LLM wrapper."""

    def test_extracts_token_usage(self):
        """LLM wrapper stores token usage returned by the provider."""
        from main import OpenAICompatibleLLM

        llm = OpenAICompatibleLLM()
        fake_response = MagicMock()
        fake_response.json.return_value = {
            "choices": [{"message": {"content": "hello"}}],
            "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30}
        }
        fake_response.raise_for_status = MagicMock()

        with patch("main.requests.post", return_value=fake_response):
            result = llm._call("test")

        assert result == "hello"
        assert llm.last_usage["total_tokens"] == 30

    def test_handles_invalid_provider_response(self):
        """Empty provider response raises an exception."""
        from main import OpenAICompatibleLLM

        llm = OpenAICompatibleLLM()
        fake_response = MagicMock()
        fake_response.json.return_value = {}
        fake_response.raise_for_status = MagicMock()

        with patch("main.requests.post", return_value=fake_response):
            with pytest.raises(Exception):
                llm._call("test")

    def test_raises_on_request_exception(self):
        """Network error from requests raises an exception."""
        from main import OpenAICompatibleLLM
        import requests as req

        llm = OpenAICompatibleLLM()

        with patch("main.requests.post", side_effect=req.RequestException("timeout")):
            with pytest.raises(Exception, match="API request failed"):
                llm._call("test")


# ---------------------------------------------------------------------------
# Monthly quota reset
# ---------------------------------------------------------------------------

class TestMonthlyReset:
    """Tests for the monthly token quota reset logic."""

    def test_resets_usage_when_month_changes(self):
        """All usage counters are cleared when the calendar month rolls over."""
        import main

        main._user_token_usage["alice"] = 500
        main._usage_month = "2000-01"

        main._reset_if_new_month()

        assert dict(main._user_token_usage) == {}
        assert main._usage_month != "2000-01"

    def test_does_not_reset_when_month_is_same(self):
        """Usage is preserved when still within the same calendar month."""
        import main
        from datetime import datetime, timezone

        main._usage_month = datetime.now(timezone.utc).strftime("%Y-%m")
        main._user_token_usage["alice"] = 500

        main._reset_if_new_month()

        assert main._user_token_usage["alice"] == 500


# ---------------------------------------------------------------------------
# monthly_token_limit helper
# ---------------------------------------------------------------------------

class TestMonthlyTokenLimit:
    """Tests for the monthly_token_limit() guard logic."""

    def test_returns_default_when_setting_is_not_integer(self):
        """Non-integer setting value falls back to DEFAULT_MONTHLY_LIMIT."""
        import main

        with patch("main.get_setting", return_value="not-a-number"):
            result = main.monthly_token_limit()

        assert result == main.DEFAULT_MONTHLY_LIMIT

    def test_returns_default_when_setting_is_too_small(self):
        """A limit <= MAX_TOKENS falls back to DEFAULT_MONTHLY_LIMIT."""
        import main

        with patch("main.get_setting", return_value=str(main.MAX_TOKENS)):
            result = main.monthly_token_limit()

        assert result == main.DEFAULT_MONTHLY_LIMIT

    def test_returns_configured_value_when_valid(self):
        """A valid limit larger than MAX_TOKENS is returned as-is."""
        import main

        valid = str(main.MAX_TOKENS + 1000)
        with patch("main.get_setting", return_value=valid):
            result = main.monthly_token_limit()

        assert result == main.MAX_TOKENS + 1000