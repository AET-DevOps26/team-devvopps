# Problem Statement : TUMgoal


## Who are the intended users?

TUM Computer Science students who face a wide range of academic choices, including selecting courses, specializations, and keeping track of the required steps. However, existing resources such as TUMOnline and To-do list apps can often be overwhelming, since they lack the intersection. The TUMOnline app does not offer integrated options to track exact steps, and To-do list apps are not specialized enough for TUM students. We want to offer a solution by combining both. Our intended users are students who struggle to translate long-term, specific career goals beyond the broad title of software engineering, or simple tasks such as applying to a TUM practical course, into clear steps.


## What is the main functionality?

Before submitting a goal, the user must select between two input modes using the simple selection button labeled “Career Goal” or “University Course Goal”. The student prompts their academic goal to the app in text form. The input can be submitted only once to generate the roadmap. Above the input textbox, there will be a prompt asking the user to be as specific as possible. 

The application transforms these goals into structured and personalized roadmaps. The roadmaps should break down long-term goals or smaller goals, such as a practical course the user wants to participate in, into smaller milestones. Each milestone node contains a small to-do list of tasks, such as selecting relevant courses and completing prerequisites. The aim is to break down the goal into manageable, smaller tasks. Individual tasks in the to-do lists can be marked as completed to track progress. When all tasks under a milestone are marked completed, the milestone node will change colors. When all the todos and milestones are completed, and the roadmap is done, a pop-up screen saying “Congrats!” will appear. 

The milestones of the roadmaps can consist of recommended TUM courses aligned with the student's academic goal. The roadmap can also include non-TUM-related steps, such as external certification or side projects, so that the student receives a full view of what it takes to reach the career goal. The user can also store the roadmaps and generate other ones. 

**Distinction between “University Course Goal” and “Career Goal”:**

- If the user selects University Course Goal, the system assumes that the input refers to participation in a specific TUM course. In this case, the system verifies whether the course exists in the internal TUM course database. If the course cannot be found, the roadmap generation is stopped, and the user is informed that the requested course does not exist. 
- If the user selects Career Goal, even if no direct TUM course matches the goal, the system can still generate a valid roadmap by combining available TUM courses with external recommendations such as certifications, projects, and networking activities.


## How will you integrate GenAI meaningfully?

The application should integrate course data and combine it with GenAI to sort available resources from TUMonline and generate content for the roadmap and to-do lists. To achieve the user’s goal, the app will recommend which courses to take at TUM and list the exact steps needed, e.g., applications. The roadmap's content is not limited to TUM; GenAI can also recommend additional steps to reach the goal. 

After a student submits their goal, the system first uses the language model to extract relevant keywords from the prompt. These keywords are then used to search the TUM course database, including course titles and descriptions, to retrieve courses that are relevant to the student’s career path or academic goal. The retrieved course information is provided back to the language model as input. By adding verified TUM-specific data, the system reduces hallucinations and ensures the generated roadmap contains accurate, relevant recommendations. Based on this context, GenAI generates a structured roadmap that includes milestones, to-do lists, recommended courses, and also additional non-TUM-related steps and suggestions. 





## Describe some scenarios how your app will function?

### Scenario 1
Lukas is a second-semester Computer Science Bachelor’s student who is interested in data science but is unsure how to turn this into a concrete career. He enters the goal, “I want to become a Machine Learning Engineer in the automotive industry. I am in my second semester of my Bachelor’s”. 

**Milestones:**


- **Register for “Introduction to Deep Learning”**
    - Prerequisites
    - Search for the course in TUMOnline
    - Add the course to the semester plan
    - Register before the deadline
    - Download lecture materials and organize study notes 

- **Complete a Python refresher course**
    - Search for beginner/intermediate Python courses
    - Compare external course recommendations
    - Register for an online Python course
    - Complete weekly exercises
    - Practice Python using small machine learning examples

- **Strengthen linear algebra and statistics skills**
    - Review matrix operations and vectors
    - Practice probability and statistics exercises
    - Create a weekly revision schedule
    - Solve past exercise sheets
    - Watch recommended supplementary tutorials

- **Apply for the “Autonomous Driving” practical course**
    - Course prerequisites
    - Read the course description and requirements
    - Prepare required application documents
    - Track the application deadline
    - Submit the application through matching platform

- **Attend the TUM IKOM career fair**
    - Register for the event
    - Research automotive companies attending the fair
    - Prepare questions for recruiters
    - Update CV before the event
    - Connect with company representatives on LinkedIn afterward

- **Start a small machine learning side project**
    - Choose a beginner machine learning project idea
    - Create a GitHub repository
    - Collect and preprocess a small dataset
    - Train and evaluate a simple model
    - Upload the finished project to a portfolio

- **Choose AI-related electives**
    - Explore available AI electives in TUMOnline
    - Compare course contents and prerequisites
    - Select electives matching career interests

- **Apply for internships in the automotive industry**
    - Prepare a professional CV
    - Write a motivation letter
    - Create or update LinkedIn profile
    - Search for internship openings
    - Submit internship applications
    - Prepare for technical interviews

- **Select a thesis topic related to autonomous systems**
    - Research current topics in autonomous systems
    - Explore available research groups at TUM
    - Contact professors or supervisors
    - Read related research papers
    - Define possible thesis ideas and interests


### Scenario 2

Sarah is a fourth-semester Computer Science Bachelor’s student who wants to secure a cybersecurity internship for the following summer. She enters her goal “I want to do a cybersecurity internship. I am in my fourth semester of my Bachelor’s.” into the application, and the system generates a roadmap focused on both academic preparation and practical experience. 

**Milestones:**

- **Take the “IT Security” course**
    - Course prerequisites
    - Register for the course in TUMOnline
    - Organize lecture and exercise schedules
    - Complete weekly assignments
    - Prepare for the final exam

- **Review networking fundamentals**
    - Study TCP/IP and network protocols
    - Review common network architectures
    - Practice subnetting exercises
    - Watch networking tutorial videos
    - Summarize key concepts in study notes

- **Learn Linux basics**
    - Install a Linux distribution in a virtual machine
    - Learn essential terminal commands
    - Practice file system navigation
    - Understand Linux permissions and processes
    - Complete beginner Linux exercises

- **Build a small penetration-testing lab**
    - Set up a virtual machine environment
    - Install cybersecurity tools
    - Configure a practice target machine
    - Follow beginner penetration-testing tutorials
    - Document findings and learned techniques

- **Participate in a Capture-the-Flag competition**
    - Search for beginner-friendly CTF events
    - Register for an online competition
    - Practice common cybersecurity challenges
    - Join or form a student team
    - Reflect on solved challenges afterward

- **Create a cybersecurity portfolio project**
    - Choose a small security-related project
    - Create a GitHub repository
    - Document the project clearly
    - Demonstrate practical cybersecurity skills
    - Add the project to CV and LinkedIn profile

- **Improve CV and LinkedIn profile**
    - Update CV with relevant technical skills and university projects
    - Add cybersecurity tools, programming languages, and certifications
    - Create or improve LinkedIn profile information
    - Upload portfolio projects or GitHub links
    - Ask peers or mentors for feedback on the CV

- **Attend cybersecurity networking events**
    - Search for cybersecurity meetups, workshops, or university events
    - Register for networking events or conferences
    - Prepare a short self-introduction
    - Connect with professionals and recruiters during the event
    - Follow up with new contacts on LinkedIn afterward

- **Apply for internship positions**
    - Search for cybersecurity internship openings
    - Prepare tailored application documents for each company
    - Submit internship applications before deadlines
    - Track submitted applications and responses
    - Prepare for technical and HR interviews
