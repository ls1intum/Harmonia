# Fix-Plan: Code-Analyse Issues #3-35

## Phase 1: Bug Fixes (hohes Risiko, kleine Änderungen)

### 1.1 EmailMappingService - saveAll Bug (#3)
- **Datei:** `src/main/java/.../analysis/service/EmailMappingService.java:148`
- **Änderung:** `saveAll(chunks)` → `saveAll(remappedChunks)`
- Nur geänderte Chunks persistieren, nicht alle

### 1.2 EmailMappingService - TypeReference statt @SuppressWarnings (#10)
- **Datei:** `src/main/java/.../analysis/service/EmailMappingService.java:502-508`
- **Änderung:** `OBJECT_MAPPER.readValue(json, List.class)` → `OBJECT_MAPPER.readValue(json, new TypeReference<List<String>>() {})`
- `@SuppressWarnings("unchecked")` entfernen

### 1.3 AnalysisStateService - Optional statt null (#11)
- **Datei:** `src/main/java/.../analysis/service/AnalysisStateService.java:106-148`
- **Änderung:** Return-Typ `AnalysisStatus` → `Optional<AnalysisStatus>`, null-Returns durch `Optional.empty()` ersetzen
- Caller in StreamingAnalysisPipelineService und AnalysisEvents anpassen

---

## Phase 2: Backend Code-Qualität

### 2.1 CqiRecalculationService - Magic Number -1L entfernen (#4)
- **Datei:** `src/main/java/.../analysis/service/cqi/CqiRecalculationService.java:82`
- **Änderung:** `getOrDefault(chunkEmail, -1L)` → Prüfung mit `containsKey()` und Chunks ohne Mapping überspringen oder mit `Optional` arbeiten

### 2.2 CQICalculatorService - Temporal Spread Duplikation entfernen (#5)
- **Datei:** `src/main/java/.../analysis/service/cqi/CQICalculatorService.java`
- **Änderung:** Gemeinsame Helper-Methode für die duplizierte Temporal-Spread-Logik. Neutral-Score `50.0` als Konstante `NEUTRAL_SCORE` extrahieren

### 2.3 PairProgrammingCalculator - Scoring angleichen (#6)
- **Datei:** `src/main/java/.../analysis/service/cqi/PairProgrammingCalculator.java`
- **Änderung:** `calculate()` bekommt dieselbe 0.5-Credit-Logik für "only one committed" wie `calculateFromChunks()`

### 2.4 ExerciseDataCleanupService - Pfade konfigurierbar (#7)
- **Datei:** `src/main/java/.../dataProcessing/service/ExerciseDataCleanupService.java:228-232`
- **Datei:** `src/main/java/.../core/config/HarmoniaProperties.java`
- **Änderung:** Neue Properties `harmonia.repos-dir` und `harmonia.projects-dir` in HarmoniaProperties. Default-Werte beibehalten (`~/.harmonia/repos`, `Projects`), aber konfigurierbar

### 2.5 CommitPreFilterService - Exception loggen (#8)
- **Datei:** `src/main/java/.../analysis/service/cqi/CommitPreFilterService.java:435-445`
- **Änderung:** `log.warn("Invalid regex pattern: {}", pattern, e)` im catch-Block

### 2.6 CorsConfig - Origins aus Properties lesen (#12)
- **Datei:** `src/main/java/.../core/config/CorsConfig.java:27-33`
- **Änderung:** Hardcoded Origins durch `harmoniaProperties.getCors().getAllowedOrigins()` ersetzen (Property existiert bereits in HarmoniaProperties)

### 2.7 AnalyzedChunkRepository - Duplikat entfernen (#15)
- **Datei:** `src/main/java/.../analysis/repository/AnalyzedChunkRepository.java`
- **Änderung:** `deleteAllByParticipation()` entfernen, nur `deleteByParticipation()` behalten. Caller-Referenzen prüfen und anpassen

### 2.8 DtoUtils - Magic Numbers als Konstanten (#25)
- **Datei:** `src/main/java/.../util/DtoUtils.java:33-35`
- **Änderung:** Konstanten extrahieren: `EFFORT_BASE_WEIGHT = 0.5`, `COMPLEXITY_WEIGHT = 0.3`, `NOVELTY_WEIGHT = 0.2`, `SCORE_NORMALIZER = 10.0`

### 2.9 PageUtil - Placeholder entfernen (#29)
- **Datei:** `src/main/java/.../util/PageUtil.java`
- **Änderung:** Placeholder-Werte ("name", "age", "address", "email") und zugehörigen Kommentar entfernen oder durch tatsächlich genutzte Spalten ersetzen

### 2.10 RequestResource - Exception loggen & 400 bei ungültigem Mode (#30, #31)
- **Datei:** `src/main/java/.../dataProcessing/web/RequestResource.java:133-137, 184-189`
- **Änderung 1:** Ungültiger AnalysisMode → `400 Bad Request` zurückgeben statt silent fallback
- **Änderung 2:** Empty catch → `log.debug("SSE emitter cleanup", e)`

---

## Phase 3: Frontend Code-Qualität

### 3.1 Shared Utilities extrahieren (#20-22)
- **Datei:** `src/main/webapp/src/lib/utils.ts`
- **Änderung:** Folgende Funktionen nach `lib/utils.ts` verschieben:
  - `getCQIColor(cqi: number): string`
  - `getCQIBgColor(cqi: number): string`
  - `isTeamFailed(team: TeamDTO): boolean`
- Duplikate in `TeamDetail.tsx` und `TeamsList.tsx` durch Imports ersetzen

### 3.2 Metric Status Konstanten (#24)
- **Datei:** `src/main/webapp/src/lib/utils.ts`
- **Änderung:** Zentrale Konstanten definieren:
  ```ts
  export const METRIC_STATUS = {
    PENDING: -1,
    NOT_FOUND: -2,
    WARNING: -3,
    NOT_AVAILABLE: -5,
  } as const;
  ```
- In `MetricCard.tsx` und `TeamDetail.tsx` die Magic Numbers durch Konstanten ersetzen

### 3.3 FileUpload - Unused Props entfernen (#23)
- **Datei:** `src/main/webapp/src/components/FileUpload.tsx:11-12`
- **Änderung:** `label` und `helperText` aus dem Interface und der Destructuring-Liste entfernen

### 3.4 TeamDetailPage - Stale State fixen (#18)
- **Datei:** `src/main/webapp/src/pages/TeamDetailPage.tsx`
- **Änderung:** `useEffect` hinzufügen der `setTeam(undefined)` aufruft wenn sich `resolvedTeamId` ändert

### 3.5 SSE Promise Timeout (#19)
- **Datei:** `src/main/webapp/src/pages/Teams.tsx`
- **Änderung:** `Promise.race` mit 5-Minuten-Timeout um die SSE-Promise wrappen

### 3.6 PairProgrammingBadge - Tooltip-Helper extrahieren (#34)
- **Datei:** `src/main/webapp/src/components/PairProgrammingBadge.tsx`
- **Änderung:** Gemeinsamen `StatusTooltip`-Helper innerhalb der Datei extrahieren, der Tooltip-Wrapper + Badge rendert. Duplizierung der 4 fast-identischen TooltipProvider-Blöcke eliminieren

### 3.7 devMode.ts - Error Logging (#35)
- **Datei:** `src/main/webapp/src/lib/devMode.ts`
- **Änderung:** `catch (e) {}` → `catch (e) { console.warn('localStorage access failed:', e); }`

---

## Bewusst NICHT gefixt

- **#9 Ownership Spread Workaround** - Erfordert Schema-Änderung (File-Liste persistieren). Design-Issue, kein Quick-Fix
- **#13 Auditor UUID** - Hängt von echtem Auth-System ab (Issue #1)
- **#14 ClientResponseDTO** - Großes Refactoring mit vielen Callern. Funktioniert korrekt, nur Design-Smell
- **#27 DTO Naming** - Rein kosmetisch, kein funktionaler Impact
- **#28 Validation Konsistenz** - Würde alle DTOs betreffen, separates Ticket
- **#32 StudentAnalysisDTO/StudentSummaryDTO** - StudentSummaryDTO hat `from()` Factory-Method, wird als View-Projection genutzt
- **#33 TeamRepositoryDTOBuilder** - Funktioniert, Entfernung würde alle Caller ändern
