# 🌟 Harmonia – Evidence-Based Insights into Team Collaboration

**Harmonia** is an instructor-facing application designed to provide transparent, data-driven insights into how student
teams collaborate on programming assignments. It analyzes Git repositories to compute metrics, visualize evidence,
detect anomalies, and generate short written summaries — helping instructors understand team dynamics at a glance.

---

## 🎯 Project Idea

Harmonia serves as a **decision support system** by automatically analyzing Git repository activity for student teams
and computing a quantifiable **Collaboration Quality Index (CQI)**. The tool centralizes all collaboration evidence and
metrics into a single platform, enabling course staff to ensure fair and evidence-based assessment of teamwork.

### Key Features

* **On-Demand Analysis:** Instructors can trigger a new analysis job course-wide or per-team, with a high-performance
  target completion time of $\sim 15$ minutes for typical course sizes ($\sim 200$ teams).
* **Teams Overview Table:** A sortable and filterable dashboard of all teams, featuring key metrics:
    * **Course Averages** are prominently displayed to provide context for team-specific scores.
    * Quantitative data like **Total Commits**, **Lines Written**, and **Team Members**.
    * **Collaboration Quality Index (CQI)** (0-100 score).
* **Team Detail View:** An in-depth page providing comprehensive collaboration evidence, including:
    * The final **CQI** and all contributing sub-scores.
    * A concise, AI-generated **Short Narrative Report** summarizing activity and referencing key commits.

---

## 🧱 Architecture

Harmonia is built as a Java/Spring-based monolith, prioritizing stability and job orchestration performance.

## 💻 Technology Stack

| Component | Technology                   |
|:----------|:-----------------------------|
| Client    | React 19.2 + TypeScript      |
| Server    | Spring Boot (Jave)           |
| Database  | PostgreSQL 18 with Hibernate |
| AI        | Spring AI (OpenAI)           |

## UML Diagram

![Harmonia Architecture UML Diagram](docs/architecture/Harmonia_architecture.png)

---

## 🐳 Docker Command for PostgreSQL

Use this command to quickly set up a local instance of the PostgreSQL database required by the server.

   ```bash
    docker compose -f docker/docker-compose.yml up -d
   ```

## 🚀 How to Run the Server

The application uses Gradle for dependency management and building. Assuming you have **Java 25** installed, use the
following commands to build and run the server locally:

1. Make sure to run the Docker container.

2. **Build the Project:** Compile the server application.

   | OS                         | Command to Build Project        |
          |:---------------------------|:--------------------------------|
   | macOS                      | `./gradlew clean build -x test` |
   | Windows                    | `.\gradlew clean build -x test` |

3. **Run the Server:** Start the Spring Boot application.

   | OS                         | Command to Run Server |
         |:---------------------------|:----------------------|
   | macOS                      | `./gradlew bootRun`   |
   | Windows                    | `.\gradlew bootRun`   |

---

## Dependencies Justification

#### `lucide-react`

- Icon library required by **shadcn/ui**
- Lightweight, consistent, and integrates cleanly with the component system

#### `tailwind-merge`

- Merges Tailwind classes and resolves conflicts (e.g., `px-2` vs. `px-4`)
- Needed for shadcn/ui components that combine base & conditional styles
- **Example:**
    ```ts
    const classes = twMerge(
      "px-4 py-2 text-sm text-gray-500",  // base styles
      isActive && "text-blue-600 font-bold" // conditional styles
     );
    ```

#### `clsx`

- Utility for conditional className construction
- Cleaner and more readable than manual string concatenation