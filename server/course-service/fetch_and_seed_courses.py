#!/usr/bin/env python3
"""
Fetch TUM Master Informatik courses from public API and seed coursedb.
Courses are fetched for Winter 2024/25 (termid 203) and Summer 2025 (termid 204).
Fetches detailed course information including objectives, prerequisites, teaching methods.
"""

import asyncio
import aiohttp
import psycopg2
import os
import ssl
from typing import List, Dict, Optional
from dotenv import load_dotenv

load_dotenv()

# PostgreSQL connection
DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = os.getenv("DB_PORT", "5432")
DB_NAME = os.getenv("DB_NAME", "coursedb")
DB_USER = os.getenv("DB_USER", "postgres")
DB_PASSWORD = os.getenv("DB_PASSWORD", "postgres")

# TUM API base URL
TUM_API_BASE = "https://campus.tum.de/tumonline/ee/rest/slc.tm.cp/student/courses"

# Term IDs to fetch (203=Winter 2024/25, 204=Summer 2025)
TERM_IDS = [203, 204]

# Course types to include (VI/VO=Lecture, UE=Exercise, PR=Practical, SE=Seminar, WS=Workshop)
ALLOWED_COURSE_TYPES = ["VI", "VO", "UE", "PR", "SE", "WS"]


def get_ssl_context():
    """Create SSL context that skips verification (for macOS compatibility)."""
    ssl_context = ssl.create_default_context()
    ssl_context.check_hostname = False
    ssl_context.verify_mode = ssl.CERT_NONE
    return ssl_context


async def discover_curriculum_ids(session: aiohttp.ClientSession) -> List[str]:
    """
    Discover Master Informatik curriculum IDs.
    Returns list of curriculum version IDs for Master Informatik programs.
    Source: https://github.com/Vuenc/TUM-Master-Informatics-Offered-Lectures/blob/main/src/curriculums.py
    """
    return ["5217", "4731", "4594", "4271", "2612"]


async def fetch_courses_for_term(
    session: aiohttp.ClientSession,
    term_id: int,
    curriculum_ids: List[str],
    term_name: str
) -> List[Dict]:
    """Fetch all course IDs for a specific term and curriculum IDs."""

    courses = []
    seen_ids = set()
    headers = {"Accept": "application/json"}

    for curriculum_id in curriculum_ids:
        print(f"  Fetching curriculum {curriculum_id}...")

        # Fetch with pagination
        for skip in range(0, 1500, 100):
            filter_str = f"$filter=curriculumVersionId-eq={curriculum_id};termId-eq={term_id};&"
            url = f"{TUM_API_BASE}?{filter_str}$orderBy=title=ascnf&$skip={skip}&$top=100"

            try:
                async with session.get(url, headers=headers, timeout=aiohttp.ClientTimeout(total=10)) as resp:
                    if resp.status != 200:
                        print(f"    Warning: API returned status {resp.status}")
                        break

                    data = await resp.json()
                    page_courses = data.get("courses", [])

                    if not page_courses:
                        break

                    for course in page_courses:
                        # Get course type
                        course_type = course.get("courseTypeDto", {}).get("key", "")
                        course_id = course.get("id")

                        if course_type in ALLOWED_COURSE_TYPES and course_id not in seen_ids:
                            course["term_name"] = term_name
                            courses.append(course)
                            seen_ids.add(course_id)

                    # Stop if last page wasn't full
                    if len(page_courses) < 100:
                        break

            except asyncio.TimeoutError:
                print(f"    Timeout fetching page at skip={skip}")
                break
            except Exception as e:
                print(f"    Error fetching page: {e}")
                break

    return courses


async def fetch_course_details(session: aiohttp.ClientSession, course_id: int) -> Optional[Dict]:
    """Fetch detailed information for a single course."""
    url = f"{TUM_API_BASE}/{course_id}"
    headers = {"Accept": "application/json"}

    try:
        async with session.get(url, headers=headers, timeout=aiohttp.ClientTimeout(total=5)) as resp:
            if resp.status == 200:
                data = await resp.json()
                # Return the resource array first element if it exists
                if isinstance(data, dict) and "resource" in data:
                    return data["resource"][0] if data["resource"] else None
                return data
            return None
    except Exception as e:
        print(f"    Error fetching course {course_id}: {e}")
        return None


def extract_english_value(field_data: Dict) -> Optional[str]:
    """Extract English translation from a langdata field."""
    if not field_data:
        return None

    # Try to get from translations first
    if isinstance(field_data, dict) and "translations" in field_data:
        translations = field_data.get("translations", {}).get("translation", [])
        for trans in translations:
            if trans.get("lang") == "en" and trans.get("value"):
                return trans["value"]

    # Fallback to default value
    if isinstance(field_data, dict) and "value" in field_data:
        return field_data.get("value")

    return None


async def parse_courses_with_details(
    session: aiohttp.ClientSession,
    courses: List[Dict]
) -> List[tuple]:
    """
    Fetch detailed information for each course and parse into tuples for database insertion.
    Returns list of: (title, content, objective, previous_knowledge, teaching_method, registration, offered_in, recommended_literature, tum_number)
    """
    parsed = []

    for i, course in enumerate(courses):
        course_id = course.get("id")
        term_name = course.get("term_name", "")

        # Extract basic title from list response
        title = extract_english_value(course.get("courseTitle", {}))
        if not title:
            title = "Unknown Course"

        # Fetch detailed course information
        print(f"    [{i+1}/{len(courses)}] Fetching details for: {title[:50]}...")
        details = await fetch_course_details(session, course_id)

        # Initialize all fields
        content = None
        objective = None
        previous_knowledge = None
        teaching_method = None
        registration = None
        recommended_literature = None
        tum_number = None

        if details:
            # Extract from nested structure: content.cpCourseDetailDto.cpCourseDescriptionDto
            content_obj = details.get("content", {})
            detail_dto = content_obj.get("cpCourseDetailDto", {})
            desc_dto = detail_dto.get("cpCourseDescriptionDto", {})

            content = extract_english_value(desc_dto.get("courseContent"))
            objective = extract_english_value(desc_dto.get("courseObjective"))
            previous_knowledge = extract_english_value(desc_dto.get("previousKnowledge"))
            teaching_method = extract_english_value(desc_dto.get("teachingMethod"))

            # Also add cpTeachingMethodDto if available
            teaching_method_dto = detail_dto.get("cpTeachingMethodDto", {})
            method_name = extract_english_value(teaching_method_dto.get("name"))
            if method_name and teaching_method:
                teaching_method = f"{method_name}\n{teaching_method}"
            elif method_name:
                teaching_method = method_name

            # Registration info is in cpCourseDto
            course_dto = detail_dto.get("cpCourseDto", {})
            registration = extract_english_value(course_dto.get("registrationInfo"))

            # Extract TUM course number from cpCourseDto.courseNumber
            course_number_obj = course_dto.get("courseNumber", {})
            if isinstance(course_number_obj, dict):
                tum_number = course_number_obj.get("databaseValue") or course_number_obj.get("courseNumber")
            elif isinstance(course_number_obj, str):
                tum_number = course_number_obj

            # Recommended literature is in additionalInformation
            additional_info = desc_dto.get("additionalInformation", {})
            recommended_literature = extract_english_value(additional_info.get("recommendedLiterature"))

        parsed.append((
            title,
            content[:5000] if content else None,
            objective[:5000] if objective else None,
            previous_knowledge[:5000] if previous_knowledge else None,
            teaching_method[:5000] if teaching_method else None,
            registration[:5000] if registration else None,
            term_name,
            recommended_literature[:5000] if recommended_literature else None,
            tum_number
        ))

    return parsed


def insert_courses(courses: List[tuple]) -> int:
    """Insert courses into coursedb.courses table. Returns count of inserted courses."""

    try:
        conn = psycopg2.connect(
            host=DB_HOST,
            port=DB_PORT,
            database=DB_NAME,
            user=DB_USER,
            password=DB_PASSWORD
        )
        cursor = conn.cursor()

        # Insert courses, skipping duplicates based on title
        insert_query = """
            INSERT INTO courses (title, content, objective, previous_knowledge, teaching_method, registration, offered_in, recommended_literature, tum_number)
            SELECT %s, %s, %s, %s, %s, %s, %s, %s, %s
            WHERE NOT EXISTS (
                SELECT 1 FROM courses WHERE title = %s
            )
        """

        count = 0
        for course in courses:
            cursor.execute(insert_query, course + (course[0],))  # Append title again for WHERE clause
            count += cursor.rowcount

        conn.commit()
        cursor.close()
        conn.close()

        return count

    except psycopg2.Error as e:
        print(f"Database error: {e}")
        return 0


async def main():
    """Main orchestration function."""

    print("TUM Course Seeder - Master Informatik")
    print("=" * 50)

    # Discover curriculum IDs
    print("\nDiscovering Master Informatik curriculum IDs...")
    ssl_context = get_ssl_context()
    connector = aiohttp.TCPConnector(ssl=ssl_context)

    async with aiohttp.ClientSession(connector=connector) as session:
        curriculum_ids = await discover_curriculum_ids(session)
        print(f"   Found {len(curriculum_ids)} curriculum(s) to check")

        # Fetch course list for all terms
        all_courses = []
        for term_id in TERM_IDS:
            term_name = {203: "Winter 2024/25", 204: "Summer 2025"}.get(term_id, f"Term {term_id}")
            print(f"\nFetching course list for {term_name}...")

            term_courses = await fetch_courses_for_term(session, term_id, curriculum_ids, term_name)
            print(f"   Found {len(term_courses)} courses")
            all_courses.extend(term_courses)

        if not all_courses:
            print("\n No courses found! Check curriculum IDs and API connectivity.")
            return

        # Fetch details for all courses
        print(f"\n Fetching detailed information for {len(all_courses)} courses...")
        print("   This may take a moment...\n")
        parsed_courses = await parse_courses_with_details(session, all_courses)

        # Insert into database
        print(f"\nInserting into coursedb...")
        inserted = insert_courses(parsed_courses)

        print(f"\nSuccess! Inserted {inserted} new courses into coursedb")
        print("=" * 50)


if __name__ == "__main__":
    asyncio.run(main())
