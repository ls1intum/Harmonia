# CQIResultDTO

## Properties

| Name              | Type                                              | Description | Notes                             |
| ----------------- | ------------------------------------------------- | ----------- | --------------------------------- |
| **baseScore**     | **number**                                        |             | [optional] [default to undefined] |
| **components**    | [**ComponentScoresDTO**](ComponentScoresDTO.md)   |             | [optional] [default to undefined] |
| **cqi**           | **number**                                        |             | [optional] [default to undefined] |
| **filterSummary** | [**FilterSummaryDTO**](FilterSummaryDTO.md)       |             | [optional] [default to undefined] |
| **weights**       | [**ComponentWeightsDTO**](ComponentWeightsDTO.md) |             | [optional] [default to undefined] |

## Example

```typescript
import { CQIResultDTO } from './api';

const instance: CQIResultDTO = {
  baseScore,
  components,
  cqi,
  filterSummary,
  weights,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
