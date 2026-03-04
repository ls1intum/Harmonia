# TeamSummaryDTO

## Properties

| Name                  | Type                                                       | Description | Notes                             |
| --------------------- | ---------------------------------------------------------- | ----------- | --------------------------------- |
| **analysisStatus**    | **string**                                                 |             | [optional] [default to undefined] |
| **cqi**               | **number**                                                 |             | [optional] [default to undefined] |
| **cqiDetails**        | [**CQIResultDTO**](CQIResultDTO.md)                        |             | [optional] [default to undefined] |
| **isFailed**          | **boolean**                                                |             | [optional] [default to undefined] |
| **isReviewed**        | **boolean**                                                |             | [optional] [default to undefined] |
| **isSuspicious**      | **boolean**                                                |             | [optional] [default to undefined] |
| **llmTokenTotals**    | [**LlmTokenTotalsDTO**](LlmTokenTotalsDTO.md)              |             | [optional] [default to undefined] |
| **orphanCommitCount** | **number**                                                 |             | [optional] [default to undefined] |
| **shortName**         | **string**                                                 |             | [optional] [default to undefined] |
| **students**          | [**Array&lt;StudentSummaryDTO&gt;**](StudentSummaryDTO.md) |             | [optional] [default to undefined] |
| **teamId**            | **number**                                                 |             | [optional] [default to undefined] |
| **teamName**          | **string**                                                 |             | [optional] [default to undefined] |
| **tutor**             | **string**                                                 |             | [optional] [default to undefined] |

## Example

```typescript
import { TeamSummaryDTO } from './api';

const instance: TeamSummaryDTO = {
  analysisStatus,
  cqi,
  cqiDetails,
  isFailed,
  isReviewed,
  isSuspicious,
  llmTokenTotals,
  orphanCommitCount,
  shortName,
  students,
  teamId,
  teamName,
  tutor,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
