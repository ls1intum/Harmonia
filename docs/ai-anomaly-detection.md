# Anomaly Detection

## Purpose
Detects suspicious collaboration patterns in team commit history. **Does NOT affect CQI score** - only displays warning flags for instructor review.

## Anomaly Types

| Flag | Threshold | Meaning |
|------|-----------|---------|
| **LATE_DUMP** | >50% commits in last 20% of time | Cramming before deadline |
| **SOLO_DEVELOPMENT** | One person >70% of commits | Unbalanced workload |
| **INACTIVE_PERIOD** | Gap >50% of assignment period | Long period with no activity |
| **UNEVEN_DISTRIBUTION** | LLM-detected | Commits in short bursts |

## Detection Method
**AI-first with validation:**
1. **LLM** (GPT-4o-mini) - analyzes patterns, detects anomalies
2. **Rule-based validation** - verifies findings with exact math, corrects percentages
3. **Best of both** - LLM catches complex patterns, rules ensure accuracy

## Testing

**Endpoint:** `GET /api/ai/detect-anomalies?scenario={type}`

### Test Scenarios

**1. Late Dump + Solo Development (default)**
```
http://localhost:8080/api/ai/detect-anomalies
```
Expected: `[LATE_DUMP, SOLO_DEVELOPMENT]`
- Alice: 7/8 commits (87.5%)
- 650/800 lines in last 6 days (last 20% of 30-day period)

**2. Solo Development Only**
```
http://localhost:8080/api/ai/detect-anomalies?scenario=solo
```
Expected: `[SOLO_DEVELOPMENT]`
- Alice: 9/10 commits (90%)
- Work spread throughout period

**3. Inactive Period**
```
http://localhost:8080/api/ai/detect-anomalies?scenario=inactive
```
Expected: `[INACTIVE_PERIOD]`
- 16-day gap between Day 6 and Day 22 (53% of 30-day period)
- Balanced contributions otherwise

**4. Good Collaboration**
```
http://localhost:8080/api/ai/detect-anomalies?scenario=good
```
Expected: `[]` (no anomalies)
- Balanced: Alice 50%, Bob 50%
- Spread evenly over time

## Configuration

```yaml
harmonia:
  ai:
    enabled: true
    anomaly-detector:
      enabled: true
      confidence-threshold: 0.7
```

## Example Output

```json
{
  "flags": ["LATE_DUMP", "SOLO_DEVELOPMENT"],
  "confidence": 1.0,
  "reasons": [
    "Alice has 83.3% of commits (5/6)",
    "66.7% of commits (4/6) in last 2 days"
  ]
}
```
