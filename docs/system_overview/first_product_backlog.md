## User Stories

| ID  | Title                  | Description                                                                                               | 
| --- | ---------------------- | --------------------------------------------------------------------------------------------------------- |
| US1 | Enter Goal                     | As a student, I want to input my specific career or academic goal in a text format.                       |
| US2 | Generate Roadmap       | As a student, I want to receive a structured personalized roadmap consisting of milestones, so that I can see the general steps required to achieve my goal. I want each roadmap milestone to include a small to-do list for that milestone, so that I can continue with exact and manageable tasks.      |
| US3 | View Roadmap       | As a student, I want to view my roadmap in a structured UI, so that I can navigate milestones.            | 
| US4 | Track Task Completion  | As a student, I want to mark tasks in the to-do list as completed, so that I can track my progress.                           | 
| US5 | Visual Progress        | As a student, I want completed milestones to be visually highlighted, so that I can see progress quickly. | 
| US6 | Progress Chart    | As a student, I want to see a progress chart with percentage to see how much of my goal I achieved. | 
| US7 | Course Recommendations | As a student, I want recommended TUM courses in my roadmap milestones when applicable, so that I can align my studies with my goal.             
| US8 | External Recommendations           | As a student, I want the roadmap to include steps beyond TUM (e.g., external certifications, skills to acquire), so that I have a holistic view of what it takes to reach my career goal besides studies.                               |
| US9 | Save Roadmap           | As a student, I want to be able to store my roadmap and view it again later.                            | 
| US10 | Choose Goal Type          | As a student, I want to be able to choose if I want to submit a "Career Goal" or "University Course Goal".                            | 

## System Stories

| ID   | Title                    | Description                                                          |
| ---- | ------------------------ | -------------------------------------------------------------------- |                                    
| SS1  | Backend Course Filtering Logic   | Implement keyword-based filtering of relevant TUM courses before GenAI invocation. (Potentially with embeddings later on)             
| SS2  | Combine Prompt with Context  | Combine user goal with retrieved course data before sending request to GenAI service. |
| SS3 | Restrict Editing   | Ensure that generated roadmaps cannot be modified by users in the initial version.|
| SS4 | Goal Input UI   | Provide a single input field and guide users to enter specific goals. (Potentially allow a chatbox interaction. The AI component asks back the user to specify their goal.)|


