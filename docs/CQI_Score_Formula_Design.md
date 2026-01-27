# Final CQI Score Formula Design for Harmonia

**Version:** 2.0 (Simplified)  
**Date:** January 22, 2026  
**Author:** Technical Architecture Team

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Commit Filter Module](#2-commit-filter-module) ⬅️ **NEW: Pre-Processing**
3. [CQI Components](#3-cqi-components)
4. [Edge Cases](#4-edge-cases)
5. [Validation Scenarios](#5-validation-scenarios)
6. [Implementation Guide](#6-implementation-guide)

---

## 1. Executive Summary

### 1.1 CQI Formula

$$
\boxed{\text{CQI} = \text{BASE\_SCORE} \times \text{PENALTY\_MULTIPLIER}}
$$

Where:
$$
\text{BASE\_SCORE} = 0.40 \cdot S_{\text{effort}} + 0.25 \cdot S_{\text{loc}} + 0.20 \cdot S_{\text{temporal}} + 0.15 \cdot S_{\text{ownership}}
$$

| Component | Weight | What it measures |
|-----------|--------|------------------|
| **Effort Balance** | 40% | LLM-weighted effort distribution (quality-aware) |
| **LoC Balance** | 25% | Raw lines of code distribution |
| **Temporal Spread** | 20% | Work spread over project duration |
| **Ownership Spread** | 15% | Files shared among team members |

### 1.2 Penalty Multipliers

| Condition | Threshold | Multiplier |
|-----------|-----------|------------|
| Solo Development | >85% effort | ×0.25 |
| Severe Imbalance | >70% effort | ×0.70 |
| High Trivial Ratio | >50% trivial | ×0.85 |
| Low LLM Confidence | >40% low conf | ×0.90 |
| Late Work | >50% in final 20% | ×0.85 |

### 1.3 Key Insight: Filter First, Then Score

Instead of complex penalty multipliers, we **filter out non-productive commits BEFORE scoring**:

```
Raw Commits → [Commit Filter] → Productive Commits → [CQI Calculator] → Score
```

This keeps the CQI formula simple while still handling:
- Trivial commits (typos, formatting)
- Auto-generated code
- Copy-pasted content
- Empty/merge commits

### 1.4 Quick Examples

| Team | Description | CQI |
|------|-------------|-----|
| Perfect | 50/50 split, consistent work | **95-100** |
| Good | 60/40 split, some late work | **75-85** |
| Acceptable | 70/30 split | **60-70** |
| Poor | 85/15 split | **30-45** |
| Solo | 95/5 split | **10-20** |

---

## 2. Commit Pre-Filter Module

### 2.1 Purpose

**Pre-filter commits BEFORE LLM analysis** to:
- Save LLM API calls (10-30% cost reduction)
- Remove obvious non-productive commits
- Improve CQI accuracy

### 2.2 Pipeline

```
Raw Commits → [Pre-Filter] → Productive Commits → [LLM Analysis] → [CQI Calculator] → Score
                   ↓
           Filtered (not analyzed, not counted)
```

### 2.3 Filter Rules

| Category | Detection | Action |
|----------|-----------|--------|
| **EMPTY** | 0 lines changed | Skip LLM, exclude |
| **MERGE** | Message pattern (`^Merge...`) | Skip LLM, exclude |
| **REVERT** | Message pattern (`^Revert...`) | Skip LLM, exclude |
| **RENAME_ONLY** | git -M/-C flag + ≤2 LoC | Skip LLM, exclude |
| **FORMAT_ONLY** | git diff -w shows no changes | Skip LLM, exclude |
| **MASS_REFORMAT** | ≥10 files, <5 avg LoC, format message | Skip LLM, exclude |
| **GENERATED_FILES** | Only lock files or build outputs | Skip LLM, exclude |
| **TRIVIAL_MESSAGE** | Linting/formatting patterns | Skip LLM, exclude |

### 2.4 Trivial Message Patterns

The following commit messages are filtered:

```
# Linting & Formatting
- "lint", "linting", "fix lint"
- "format", "formatting", "code formatting"  
- "run prettier", "apply eslint", "checkstyle", "spotless"
- "fix whitespace", "fix indentation"

# Style-only changes
- "style: ..."

# Typos
- "fix typo", "typos"

# WIP & Temp
- "wip", "temp", "test", "testing"
- ".", "..", "..."
- "oops", "stuff", "changes"

# Auto-generated
- "auto-format", "update dependencies"
- "[bot] ..."

# Initial commits  
- "initial commit", "first commit", "init"
```

### 2.5 Generated File Patterns

```java
// Lock files (exact match)
"package-lock.json", "yarn.lock", "pnpm-lock.yaml",
"Cargo.lock", "Gemfile.lock", "poetry.lock", "composer.lock"

// Path patterns
"node_modules/*", "build/*", "dist/*", "target/*"
"*.min.js", "*.min.css", "*.generated.*"
```

### 2.6 No Post-Filter Needed

After LLM analysis, **no additional filtering is applied**:
- TRIVIAL commits get low `effortScore` → naturally less weight
- Copy-paste gets low `novelty` → naturally less weight
- The LLM ratings are used directly in CQI calculation

**Benefits:**
- ✅ Simpler architecture
- ✅ Full transparency (all commits visible with scores)
- ✅ No "why was this filtered?" questions
- ✅ Instructors see the full picture

---

## 3. CQI Components

### 3.1 Effort Balance Score ($S_{\text{effort}}$) — Weight: 40%

**Input:** Filtered commits with LLM effort ratings

**Formula:**

$$
S_{\text{effort}} = 100 \times (1 - \text{Gini}(E))
$$

Where $E = [e_1, e_2, ..., e_n]$ = weighted effort per author.

**Gini Coefficient:**

$$
\text{Gini}(E) = \frac{\sum_i \sum_j |e_i - e_j|}{2n \sum_i e_i}
$$

| Distribution | Gini | Score |
|--------------|------|-------|
| 50/50 | 0.00 | 100 |
| 60/40 | 0.10 | 90 |
| 70/30 | 0.20 | 80 |
| 80/20 | 0.30 | 70 |
| 90/10 | 0.40 | 60 |
| 100/0 | 0.50 | 50 |

**Implementation:**

```java
public double calculateEffortBalance(Map<Long, Double> effortByAuthor) {
    if (effortByAuthor.size() <= 1) return 0.0;
    
    double[] efforts = effortByAuthor.values().stream()
        .mapToDouble(Double::doubleValue).toArray();
    
    return 100.0 * (1.0 - gini(efforts));
}

private double gini(double[] values) {
    int n = values.length;
    double sum = Arrays.stream(values).sum();
    if (sum == 0) return 1.0;
    
    double diffSum = 0;
    for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
            diffSum += Math.abs(values[i] - values[j]);
        }
    }
    return diffSum / (2.0 * n * sum);
}
```

---

### 3.2 LoC Balance Score ($S_{\text{loc}}$) — Weight: 25%

**Input:** Lines added + deleted per author (from filtered commits)

**Formula:**

$$
S_{\text{loc}} = 100 \times (1 - \text{Gini}(L))
$$

Where $L = [l_1, l_2, ..., l_n]$ = lines changed per author.

**Why keep LoC?**
- Provides fallback when LLM fails
- Simple, objective metric
- Catches cases where LLM over/underestimates

**Implementation:**

```java
public double calculateLocBalance(Map<Long, Integer> locByAuthor) {
    if (locByAuthor.size() <= 1) return 0.0;
    
    double[] locs = locByAuthor.values().stream()
        .mapToDouble(Integer::doubleValue).toArray();
    
    return 100.0 * (1.0 - gini(locs));
}
```

---

### 3.3 Temporal Spread Score ($S_{\text{temporal}}$) — Weight: 20%

**Input:** Commit timestamps from filtered chunks

**Purpose:** Rewards work spread evenly over project duration, penalizes cramming.

**Formula:**

$$
S_{\text{temporal}} = 100 \times (1 - \frac{\text{CV}_{\text{weekly}}}{2})
$$

Where CV = coefficient of variation of weekly effort distribution.

**Implementation:**

```java
private double calculateTemporalSpread(List<FilteredChunkDTO> chunks,
                                       LocalDateTime projectStart,
                                       LocalDateTime projectEnd) {
    // Divide project into weeks
    int numWeeks = Math.max(1, (int) Math.ceil(totalDays / 7.0));
    double[] weeklyEffort = new double[numWeeks];
    
    for (FilteredChunkDTO chunk : chunks) {
        int weekIndex = (int) (daysSinceStart / 7);
        weeklyEffort[weekIndex] += chunk.effectiveEffort();
    }
    
    // Calculate coefficient of variation
    double cv = stdev / mean;
    double normalizedCV = Math.min(cv / 2.0, 1.0);
    
    return 100.0 * (1.0 - normalizedCV);
}
```

---

### 3.4 Ownership Spread Score ($S_{\text{ownership}}$) — Weight: 15%

**Input:** File paths from filtered chunks

**Purpose:** Rewards multiple authors working on the same files.

**Formula:**

$$
S_{\text{ownership}} = 100 \times \frac{\sum_f \min(\text{authors}_f, 4)}{|\text{Files}| \times \min(n, 4)}
$$

**Implementation:**

```java
private double calculateOwnershipSpread(List<FilteredChunkDTO> chunks, int teamSize) {
    // Map: filename -> set of author IDs
    Map<String, Set<Long>> fileAuthors = new HashMap<>();
    
    for (FilteredChunkDTO chunk : chunks) {
        for (String file : chunk.files()) {
            fileAuthors.computeIfAbsent(file, k -> new HashSet<>())
                       .add(chunk.authorId());
        }
    }
    
    // Filter to significant files (>= 3 commits)
    // Calculate average author count per file
    int effectiveTeamSize = Math.min(teamSize, 4);
    double totalAuthors = significantFiles.stream()
        .mapToDouble(f -> Math.min(fileAuthors.get(f).size(), effectiveTeamSize))
        .sum();
    
    return 100.0 * totalAuthors / (significantFiles.size() * effectiveTeamSize);
}
```

## 4. Edge Cases

| Case | Handling |
|------|----------|
| **Single contributor** | CQI = 0 |
| **All commits filtered** | CQI = 0, flag for review |
| **LLM fails completely** | Use 100% LoC weight |
| **No commits** | CQI = 0 |
| **2-person team** | Formula works naturally |

---

## 5. Validation Scenarios

### 5.1 Perfect Team (CQI: 100)

- 2 members, effort: 50%/50%, LoC: 50%/50%
- $S_E = 100$, $S_L = 100$
- **CQI = 0.6×100 + 0.4×100 = 100**

### 5.2 Good Team (CQI: 88)

- 2 members, effort: 55%/45%, LoC: 60%/40%
- $S_E = 95$, $S_L = 80$
- **CQI = 0.6×95 + 0.4×80 = 89**

### 5.3 Solo Development (CQI: 15)

- 2 members, effort: 95%/5%, LoC: 90%/10%
- $S_E = 10$, $S_L = 20$
- **CQI = 0.6×10 + 0.4×20 = 14**

### 5.4 Balanced but Lots of Trivial (CQI: 85)

- 2 members, 50/50 split
- But 40% commits were filtered as trivial
- Post-filter: still 50/50
- **CQI = 100** (filtering worked!)
- Dashboard shows: "40% trivial commits filtered"

### 5.5 Copy-Paste Heavy (CQI: ~60)

- Member A: 1000 LoC (but 800 copy-pasted → weight reduced to 80 effective)
- Member B: 200 LoC (all original)
- Effective ratio: ~30%/70% after copy-paste weight reduction
- **CQI ≈ 60**

---

## 6. Implementation Guide

### 6.1 File Structure

All CQI-related code is organized as follows:

```
src/main/java/de/tum/cit/aet/analysis/
├── service/cqi/
│   ├── CQICalculatorService.java    # Main calculation service
│   ├── CommitPreFilterService.java  # Pre-filter BEFORE LLM
│   └── CQIConfig.java               # Configuration properties
└── dto/cqi/
    ├── CQIInputDTO.java             # Input data (optional)
    ├── CQIResultDTO.java            # Result with breakdown
    ├── ComponentScoresDTO.java      # Component scores
    ├── CQIPenaltyDTO.java           # Penalty details
    └── FilterSummaryDTO.java        # Pre-filter statistics
```

**Benefits of this structure:**
- DTOs centralized in `/dto` folder (standard Java convention)
- CQI-specific DTOs in `/dto/cqi` subfolder
- Services in `/service/cqi` for easy discovery
- Clear separation of concerns

### 6.2 CQICalculatorService

See: [CQICalculatorService.java](src/main/java/de/tum/cit/aet/analysis/service/cqi/CQICalculatorService.java)

Main entry points:
- `calculate(CQIInputDTO)` - Calculate from pre-aggregated data
- `calculateFromRaw(List<RatedChunk>, ...)` - Full pipeline with filtering

### 6.3 CommitPreFilterService

See: [CommitPreFilterService.java](src/main/java/de/tum/cit/aet/analysis/service/cqi/CommitPreFilterService.java)

Pre-filters commits **BEFORE** LLM analysis:
- Empty commits (0 LoC)
- Merge commits
- Revert commits
- Rename-only commits (git -M/-C)
- Format-only commits (git diff -w)
- Mass reformat commits (many files, uniform changes)
- Lock files & build outputs only
- Trivial message patterns (lint, format, typo, wip, etc.)

### 6.4 Configuration

See: [CQIConfig.java](src/main/java/de/tum/cit/aet/analysis/service/cqi/CQIConfig.java)

All weights, thresholds, and penalties are configurable via `application.yml`:

```yaml
harmonia:
  cqi:
    weights:
      effort: 0.40
      loc: 0.25
      temporal: 0.20
      ownership: 0.15
    thresholds:
      solo-development: 0.85
      severe-imbalance: 0.70
      high-trivial: 0.50
    penalties:
      solo-development: 0.25
      severe-imbalance: 0.70
      high-trivial: 0.85
```

### 6.5 Integration in RequestService

Replace current calculation:

```java
// OLD:
cqi = balanceCalculator.calculate(commitCounts);

// NEW:
@Autowired
private CQICalculatorService cqiCalculatorService;

// In saveSingleResult:
CQIResultDTO result = cqiCalculatorService.calculateFromRaw(
    ratedChunks, 
    students.size(),
    projectStart,
    projectEnd,
    avgConfidence,
    lowConfCount
);
cqi = result.cqi();
```

---

## Summary: What's Implemented

| Component | Status | File |
|-----------|--------|------|
| CQI Calculator | ✅ Implemented | `CQICalculatorService.java` |
| Commit Filter | ✅ Implemented | `CommitFilterService.java` |
| Configuration | ✅ Implemented | `CQIConfig.java` |
| DTOs | ✅ Implemented | `dto/*.java` |
| RequestService Integration | 🔜 TODO | Connect to existing pipeline |

**Components:**
- 4 components: Effort (40%), LoC (25%), Temporal (20%), Ownership (15%)
- 5 penalties: Solo, Imbalance, Trivial, Confidence, Late Work
- Pre-filtering: Empty, Merge, Auto-gen, Copy-paste, Trivial

---

*End of Document*
