# LlmTokenUsageDTO

## Properties

| Name                 | Type        | Description | Notes                             |
| -------------------- | ----------- | ----------- | --------------------------------- |
| **completionTokens** | **number**  |             | [optional] [default to undefined] |
| **model**            | **string**  |             | [optional] [default to undefined] |
| **promptTokens**     | **number**  |             | [optional] [default to undefined] |
| **totalTokens**      | **number**  |             | [optional] [default to undefined] |
| **usageAvailable**   | **boolean** |             | [optional] [default to undefined] |

## Example

```typescript
import { LlmTokenUsageDTO } from './api';

const instance: LlmTokenUsageDTO = {
  completionTokens,
  model,
  promptTokens,
  totalTokens,
  usageAvailable,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
