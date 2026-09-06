# IntelliConf

IntelliConf is a conference management system with a Spring Boot backend and a Vue 3 frontend. It covers conference setup, milestones, participant/member management, committee invitations, task workflows, mail configuration, and potential author discovery.

## Project Layout

```text
.
|-- intelliconf/        # Spring Boot backend, nested Git repository
|-- IntelliConfFront/   # Vue 3 + Vite frontend
`-- *.md               # Local notes
```

The backend is stored as a nested Git repository. Commit backend changes inside `intelliconf/` first, then commit the updated `intelliconf` pointer in the root repository.

## Backend

Tech stack:

- Java 17
- Spring Boot 3.0.7
- MyBatis-Plus
- MySQL
- Redis
- Maven

Important backend paths:

```text
intelliconf/src/main/java/com/jxl/ai/intelliconf
intelliconf/src/main/resources/application.yml
intelliconf/src/main/resources/database
intelliconf/src/main/resources/mapper
```

Potential author discovery code is isolated under:

```text
intelliconf/src/main/java/com/jxl/ai/intelliconf/author_discovery
```

Run backend:

```bash
cd intelliconf
mvn spring-boot:run
```

Run backend tests:

```bash
cd intelliconf
mvn test
```

Database SQL files are in:

```text
intelliconf/src/main/resources/database
```

Apply the required SQL files to the MySQL database before starting features that depend on new tables.

## Frontend

Tech stack:

- Vue 3
- Vite
- TypeScript
- Element Plus
- Axios

Run frontend:

```bash
cd IntelliConfFront
npm install
npm run dev
```

Type-check and build:

```bash
cd IntelliConfFront
npm run type-check
npm run build
```

## Potential Author Discovery

The potential author discovery feature can:

- Create a discovery job from conference topic keywords.
- Query OpenAlex and optionally Crossref.
- Extract public academic emails from allowed public pages and PDFs.
- Save only candidates with a trusted email.
- Keep discovered authors in pending review status.
- Approve a candidate into `conf_contact_pool` and `conf_member`.

Main APIs:

```text
POST /api/conferences/{conferenceId}/author-discovery/jobs
POST /api/author-discovery/jobs/{jobId}/run
GET  /api/author-discovery/jobs/{jobId}
GET  /api/conferences/{conferenceId}/potential-authors
POST /api/potential-authors/{authorId}/approve
POST /api/potential-authors/{authorId}/reject
```

Example:

```bash
curl -X POST "http://localhost:8080/api/conferences/1/author-discovery/jobs" \
  -H "Content-Type: application/json" \
  -d "{\"topicKeywords\":[\"Artificial Intelligence in Education\",\"Learning Analytics\"],\"yearFrom\":2021,\"yearTo\":2026,\"maxAuthors\":50,\"enableCrossref\":true,\"enableEmailExtraction\":true}"
```

Then run the returned job:

```bash
curl -X POST "http://localhost:8080/api/author-discovery/jobs/1/run" \
  -H "Content-Type: application/json" \
  -d "{\"enableCrossref\":true,\"enableEmailExtraction\":true}"
```

More tool documentation:

```text
docs/tools/potential-author-discovery.md
```

## Git Hygiene

Do not commit local caches, build output, uploaded runtime files, or temporary backend copies. These are ignored:

```text
intelliconf/.m2/
intelliconf/target/
intelliconf/uploads/
IntelliConfFront/node_modules/
IntelliConfFront/dist/
backend/
backend.zip
```

## Notes

- `application.yml` currently contains environment-specific database and Redis settings. For deployment, prefer overriding these with environment-specific configuration.
- The author discovery crawler respects robots.txt and does not bypass login pages, captchas, paywalls, or anti-crawling mechanisms.
