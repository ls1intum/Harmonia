# Agent Guidelines for Harmonia

## 1. Project Overview

Harmonia is an instructor-facing web application that analyzes student team collaboration in programming courses. It clones Git repositories, processes commit history, and computes a **Collaboration Quality Index (CQI)** score (0–100) for each team. AI-powered commit analysis (effort rating, classification, anomaly detection) provides deeper insights.

- **Integration:** Artemis LMS (fetches teams, exercises, participation data)
- **Target users:** Instructors, teaching assistants
- **Analysis time:** ~15 minutes for ~200 teams

## 2. Tech Stack

| Layer | Technologies |
|-------|-------------|
| **Server** | Java 25, Spring Boot 4.0.1, Spring Data JPA, Spring Security, Spring Batch, Spring AI 2.0, JGit 7.5, PostgreSQL 18, Liquibase, Lombok |
| **Client** | React 19, TypeScript 5.9, Vite 7, Tailwind CSS 4, Radix UI / Shadcn, TanStack Query 5, React Router DOM 7, Axios |
| **Build** | Gradle 9.3.1, Node 22, OpenAPI Generator (typescript-axios) |
| **CI/CD** | GitHub Actions |

## 3. Architecture

### Server (Java/Spring)

Monolithic Spring Boot application with modular packages under `de.tum.cit.aet`:

```
src/main/java/de/tum/cit/aet/
├── core/                    # Config, security, exceptions, shared DTOs
├── usermanagement/          # User entity, auth, controllers
├── repositoryProcessing/    # Artemis integration, Git clone/fetch, team data
├── analysis/                # CQI calculation, analysis orchestration
│   └── service/cqi/         # CQI calculator, commit pre-filter, config
├── ai/                      # Spring AI services (effort rating, classification, anomaly detection)
├── dataProcessing/          # Batch processing, request orchestration
└── util/                    # Shared utilities
```

**Layered architecture:**
```
Web (*Resource controllers) → Service → Repository → Domain (JPA entities)
```

### Client (React/TypeScript)

```
src/main/webapp/src/
├── app/generated/           # OpenAPI-generated API client (DO NOT EDIT)
│   ├── apis/                # API service classes
│   └── models/              # TypeScript interfaces
├── components/              # React components
│   └── ui/                  # Shadcn/UI primitives (button, card, badge, etc.)
├── pages/                   # Page-level components
├── hooks/                   # Custom React hooks
├── data/                    # Data loaders, config
├── lib/                     # Utilities (cn(), devMode, etc.)
└── types/                   # TypeScript type definitions
```

**Data flow:**
```
Pages → Components → Custom Hooks → Generated API Client → Axios → Server
```

Vite dev server proxies `/api` to the server on port 8080.

## 4. Key Domain Concepts

### CQI (Collaboration Quality Index)

```
CQI = BASE_SCORE × PENALTY_MULTIPLIER
BASE_SCORE = 0.55 × EffortBalance + 0.25 × LoCBalance + 0.15 × OwnershipSpread + 0.05 × TemporalSpread
```

| Component | Weight | Measures |
|-----------|--------|----------|
| Effort Balance | 55% | LLM-weighted effort distribution (Gini coefficient) |
| LoC Balance | 25% | Lines of code distribution |
| Ownership Spread | 15% | Multiple authors per file |
| Temporal Spread | 5% | Work spread over project duration |

Weights are configurable in `application.yml` under `harmonia.cqi.weights`.

### Commit Pre-Filter Pipeline

```
Raw Commits → [Pre-Filter] → Productive Commits → [LLM Analysis] → [CQI Calculator] → Score
```

Filters out: empty, merge, revert, rename-only, format-only, mass-reformat, generated files, trivial message patterns.

### Anomaly Detection

Flags for instructor review (does NOT affect CQI): `LATE_DUMP`, `SOLO_DEVELOPMENT`, `INACTIVE_PERIOD`, `UNEVEN_DISTRIBUTION`.

See `docs/CQI_Score_Formula_Design.md` for full details.

### Key Entities

- `TeamParticipation` — team + exercise + CQI score + analysis status
- `TeamRepository` — cloned Git repo
- `Student` / `Tutor` — team members
- `AnalysisStatus` — progress tracking (IDLE → RUNNING → COMPLETED/FAILED/CANCELLED)
- `AnalyzedChunk` — AI-analyzed commit chunk with effort rating and classification
- `VCSLog` — individual commit entry

## 5. Code Style & Conventions

### Server (Java)

**Formatting:** Spotless (auto-fix: `./gradlew spotlessApply`, check: `./gradlew spotlessCheck`)

**Linting:** Checkstyle
- JavaDoc required on all public methods ≥4 lines (with `@return`, `@param` tags)
- Exceptions: `@Override`, `@Test`, `@BeforeEach`, `@AfterEach`
- Braces required for all `if`/`else`/`for`/`while`/`do` blocks
- Modifier order enforced

**Conventions:**
- Lombok: `@Getter`, `@Setter`, `@RequiredArgsConstructor`, `@Slf4j`
- Constructor injection only (never `@Autowired` on fields)
- `record` types for immutable DTOs
- UUIDs as primary keys (`@GeneratedValue(strategy = GenerationType.UUID)`)
- Controllers named `*Resource` (e.g., `AnalysisResource`)
- Services named `*Service`, Repositories named `*Repository`

### Client (TypeScript)

**Formatting:** Prettier
- Print width: 140, single quotes, 2-space indent, avoid arrow parens, end-of-line auto

**Linting:** ESLint
- TypeScript ESLint recommended rules
- React hooks rules
- Unused vars = error (except `_`-prefixed)
- Generated code (`**/generated/**`) is excluded

**Conventions:**
- Arrow functions for components: `const MyComponent = () => { ... }`
- Props via interface: `interface MyComponentProps { ... }`
- Path alias: `@/` maps to `src/` (e.g., `import { Button } from '@/components/ui/button'`)
- Class composition: `cn()` utility (clsx + tailwind-merge)
- Variants: `class-variance-authority` (CVA)
- Server state: TanStack Query (React Query)
- Icons: Lucide React
- Toasts: Sonner

## 6. API & Generated Code

### OpenAPI Workflow

When server endpoints or DTOs change:

```bash
# 1. Generate OpenAPI spec from Spring controllers
./gradlew generateApiDocs -x webapp

# 2. Generate TypeScript client from spec
./gradlew openApiGenerate
```

- **Generated files:** `src/main/webapp/src/app/generated/`
- **CRITICAL: Never manually edit generated files** — they get overwritten
- Generated files are committed to version control
- OpenAPI spec: `openapi/openapi.yaml`

### Using the Generated Client

```typescript
import { AnalysisResourceApi } from '@/app/generated';
import type { AnalysisStatusDTO } from '@/app/generated';
```

## 7. Development Commands

### Server

```bash
./gradlew bootRun                     # Run Spring Boot server (port 8080)
./gradlew test                        # Run unit tests (excludes integration/e2e)
./gradlew test --tests ClassName      # Run specific test class
./gradlew integrationTest             # Run integration tests (requires external services)
./gradlew spotlessApply               # Auto-format Java code
./gradlew spotlessCheck               # Check Java formatting
./gradlew checkstyleMain              # Check JavaDoc and code style
./gradlew checkstyleTest              # Check test code style
```

### Client

```bash
cd src/main/webapp
npm run dev                           # Start Vite dev server (port 5173)
npm run build                         # Production build
npm run lint                          # Run ESLint
npm run prettier:check                # Check Prettier formatting
npm run prettier:format               # Auto-format TypeScript
npm run compile:ts                    # TypeScript type-check (npx tsc --noEmit)
```

### OpenAPI

```bash
./gradlew generateApiDocs -x webapp   # Generate OpenAPI spec
./gradlew openApiGenerate             # Generate TypeScript client
```

## 8. CI/CD & PR Conventions

### PR Title Format

```
`Category`: Description starting with capital letter
```

Valid categories: `Usability`, `Performance`, `Development`, `General`

Examples:
- `` `Development`: Add commit pre-filter pipeline ``
- `` `General`: Fix analysis status race condition ``
- `` `Performance`: Optimize database queries for large teams ``
- `` `Usability`: Add loading spinner for better user feedback ``

Common mistakes:
- `docs: update readme` — wrong format, missing backticks and capital letter
- `Development: fix bug` — missing backticks around category, description must start with capital letter

### GitHub Actions Checks

| Job | What it checks | Must pass |
|-----|---------------|-----------|
| `validate-pr-title` | PR title format (see above) | Yes |
| `server-style` | `spotlessCheck` + `checkstyleMain` | Yes |
| `client-style` | `prettier:check` + `lint` | Yes |
| `client-compilation` | `tsc --noEmit` | Yes |
| `tests` | `./gradlew test` | Yes |

**All checks must pass before merging.**

### Before Committing Checklist

```bash
# Server changes
./gradlew spotlessApply
./gradlew checkstyleMain checkstyleTest

# Client changes
cd src/main/webapp
npm run prettier:format
npm run lint
npm run compile:ts
```

## 9. Common Tasks

### Add a New Server Endpoint

1. Create DTO(s) in the module's `dto/` package
2. Add business logic in `*Service`
3. Add REST endpoint in `*Resource` controller
4. Add JavaDoc to all public methods
5. Run `./gradlew spotlessApply && ./gradlew checkstyleMain`
6. Regenerate OpenAPI: `./gradlew generateApiDocs -x webapp && ./gradlew openApiGenerate`
7. Commit generated files alongside your changes

### Add a New Client Component

1. Create `MyComponent.tsx` in `src/main/webapp/src/components/`
2. Use Shadcn/UI primitives from `components/ui/`
3. Import with `@/` alias: `import { Card } from '@/components/ui/card'`
4. Use `cn()` for conditional Tailwind classes
5. For API data: use TanStack Query hooks
6. Run `npm run lint && npm run compile:ts`

### Modify CQI Weights

1. Edit `src/main/resources/config/application.yml` under `harmonia.cqi.weights`
2. Weights must sum to 1.0
3. Run tests: `./gradlew test`

### Add a Database Migration

1. Create Liquibase changelog in `src/main/resources/db/changelog/`
2. Reference it in `db.changelog-master.xml`
3. Use `validate` DDL strategy — schema changes must go through Liquibase

## 10. Common Style Issues

### Java (Checkstyle)
- Missing or incomplete JavaDoc comments (missing `@return`, `@param` tags)
- Single-line `if`/`else`/`for`/`while` without braces
- Wrong modifier order

### TypeScript (ESLint/Prettier)
- Unused imports or variables
- Inconsistent formatting (run `npm run prettier:format` to auto-fix)
- Type errors from stale generated code (regenerate with `./gradlew openApiGenerate`)

## 11. Do's and Don'ts

### Do

- Use constructor injection with `@RequiredArgsConstructor`
- Use Lombok annotations (`@Getter`, `@Setter`, `@Slf4j`)
- Use `record` types for immutable DTOs
- Name controllers `*Resource`, services `*Service`, repos `*Repository`
- Add JavaDoc for public methods ≥4 lines
- Use UUIDs for primary keys
- Use `cn()` for conditional Tailwind classes
- Use TanStack Query for API calls in React
- Use `@/` path aliases for imports
- Run style checks before committing
- Commit generated OpenAPI files
- Use braces for all control flow blocks

### Don't

- Never manually edit files in `src/main/webapp/src/app/generated/`
- Never use field injection (`@Autowired` on fields)
- Never skip JavaDoc on public methods ≥4 lines
- Never commit without running style checks
- Never hardcode config values — use `application.yml`
- Never use single-line `if`/`else` without braces
- Never modify generated TypeScript client manually
- Never blindly fix style violations — understand why the rule exists before fixing
