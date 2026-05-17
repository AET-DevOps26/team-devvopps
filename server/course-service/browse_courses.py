#!/usr/bin/env python3
"""Browse courses in the coursedb database."""

import psycopg2

DB_HOST = "localhost"
DB_PORT = "5432"
DB_NAME = "coursedb"
DB_USER = "postgres"
DB_PASSWORD = "postgres"

def browse_courses():
    conn = psycopg2.connect(
        host=DB_HOST,
        port=DB_PORT,
        database=DB_NAME,
        user=DB_USER,
        password=DB_PASSWORD
    )
    cursor = conn.cursor()

    while True:
        print("\n" + "="*80)
        print(" Course Browser")
        print("="*80)
        print("1. View all courses (paginated)")
        print("2. Search by title")
        print("3. View course details")
        print("4. Statistics")
        print("5. Exit")
        print("="*80)

        choice = input("Choose option (1-5): ").strip()

        if choice == "1":
            # Paginated view
            cursor.execute("SELECT COUNT(*) FROM courses;")
            total = cursor.fetchone()[0]

            page = 1
            per_page = 10

            while True:
                offset = (page - 1) * per_page
                cursor.execute(
                    "SELECT course_id, title, offered_in FROM courses ORDER BY course_id LIMIT %s OFFSET %s",
                    (per_page, offset)
                )
                courses = cursor.fetchall()

                print(f"\n--- Page {page} ({offset+1}-{min(offset+per_page, total)} of {total}) ---")
                for i, (cid, title, offered_in) in enumerate(courses, 1):
                    print(f"{i}. [{cid}] {title[:70]} ({offered_in})")

                nav = input("\n[n]ext, [p]revious, [g]o to page, [b]ack: ").lower()
                if nav == "n" and offset + per_page < total:
                    page += 1
                elif nav == "p" and page > 1:
                    page -= 1
                elif nav == "g":
                    try:
                        page = int(input("Go to page: "))
                        if page < 1:
                            page = 1
                        elif page > (total + per_page - 1) // per_page:
                            page = (total + per_page - 1) // per_page
                    except:
                        pass
                elif nav == "b":
                    break

        elif choice == "2":
            # Search by title
            search = input("Search for course title (partial match): ").strip()
            if search:
                cursor.execute(
                    "SELECT course_id, title, offered_in FROM courses WHERE title ILIKE %s ORDER BY title LIMIT 50",
                    (f"%{search}%",)
                )
                courses = cursor.fetchall()

                if courses:
                    print(f"\nFound {len(courses)} course(s):")
                    for i, (cid, title, offered_in) in enumerate(courses, 1):
                        print(f"{i}. [{cid}] {title} ({offered_in})")

                    # Ask to view details
                    try:
                        idx = int(input("\nView details of course # (0 to skip): "))
                        if 0 < idx <= len(courses):
                            show_course_details(cursor, courses[idx-1][0])
                    except:
                        pass
                else:
                    print("No courses found.")

        elif choice == "3":
            # View course details
            try:
                cid = int(input("Enter course ID: "))
                show_course_details(cursor, cid)
            except:
                print("Invalid course ID.")

        elif choice == "4":
            # Statistics
            cursor.execute("SELECT COUNT(*) FROM courses;")
            total = cursor.fetchone()[0]

            cursor.execute("SELECT COUNT(DISTINCT title) FROM courses;")
            unique = cursor.fetchone()[0]

            cursor.execute("SELECT offered_in, COUNT(*) FROM courses GROUP BY offered_in;")
            by_semester = cursor.fetchall()

            print(f"\n Statistics:")
            print(f"  Total courses: {total}")
            print(f"  Unique titles: {unique}")
            print(f"\n  By semester:")
            for semester, count in by_semester:
                print(f"    {semester}: {count}")

        elif choice == "5":
            break

    cursor.close()
    conn.close()


def show_course_details(cursor, course_id):
    cursor.execute(
        """SELECT course_id, title, content, objective, previous_knowledge,
                  teaching_method, registration, offered_in, recommended_literature, tum_number FROM courses WHERE course_id = %s""",
        (course_id,)
    )
    course = cursor.fetchone()

    if not course:
        print("Course not found.")
        return

    cid, title, content, objective, prev_know, teach_method, registration, offered_in, rec_lit, tum_num = course

    print(f"\n{'='*80}")
    print(f" {title}")
    print(f"{'='*80}")
    print(f"\nID: {cid}")
    if tum_num:
        print(f"TUM Course Number: {tum_num}")
    print(f"Offered in: {offered_in}")

    if content:
        print(f"\n Content:")
        print("-" * 80)
        print(content)
        print("-" * 80)
    else:
        print(f"\n Content: [Not available]")

    if objective:
        print(f"\n Objective:")
        print("-" * 80)
        print(objective)
        print("-" * 80)
    else:
        print(f"\n Objective: [Not available]")

    if prev_know:
        print(f"\n Prerequisites:")
        print("-" * 80)
        print(prev_know)
        print("-" * 80)
    else:
        print(f"\n Prerequisites: [Not available]")

    if teach_method:
        print(f"\n Teaching Method:")
        print("-" * 80)
        print(teach_method)
        print("-" * 80)
    else:
        print(f"\n Teaching Method: [Not available]")

    if registration:
        print(f"\n Registration:")
        print("-" * 80)
        print(registration)
        print("-" * 80)
    else:
        print(f"\n Registration: [Not available]")

    if rec_lit:
        print(f"\n Recommended Literature:")
        print("-" * 80)
        print(rec_lit)
        print("-" * 80)
    else:
        print(f"\n Recommended Literature: [Not available]")

    print(f"\n{'='*80}")


if __name__ == "__main__":
    browse_courses()
