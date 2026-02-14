# AnalyzedChunkDTO

## Properties

| Name                      | Type                                  | Description | Notes                             |
| ------------------------- | ------------------------------------- | ----------- | --------------------------------- |
| **authorEmail**           | **string**                            |             | [optional] [default to undefined] |
| **authorName**            | **string**                            |             | [optional] [default to undefined] |
| **chunkIndex**            | **number**                            |             | [optional] [default to undefined] |
| **classification**        | **string**                            |             | [optional] [default to undefined] |
| **commitMessages**        | **Array&lt;string&gt;**               |             | [optional] [default to undefined] |
| **commitShas**            | **Array&lt;string&gt;**               |             | [optional] [default to undefined] |
| **complexity**            | **number**                            |             | [optional] [default to undefined] |
| **confidence**            | **number**                            |             | [optional] [default to undefined] |
| **effortScore**           | **number**                            |             | [optional] [default to undefined] |
| **errorMessage**          | **string**                            |             | [optional] [default to undefined] |
| **id**                    | **string**                            |             | [optional] [default to undefined] |
| **isBundled**             | **boolean**                           |             | [optional] [default to undefined] |
| **isError**               | **boolean**                           |             | [optional] [default to undefined] |
| **isExternalContributor** | **boolean**                           |             | [optional] [default to undefined] |
| **linesChanged**          | **number**                            |             | [optional] [default to undefined] |
| **llmTokenUsage**         | [**LlmTokenUsage**](LlmTokenUsage.md) |             | [optional] [default to undefined] |
| **novelty**               | **number**                            |             | [optional] [default to undefined] |
| **reasoning**             | **string**                            |             | [optional] [default to undefined] |
| **timestamp**             | **string**                            |             | [optional] [default to undefined] |
| **totalChunks**           | **number**                            |             | [optional] [default to undefined] |

## Example

```typescript
import { AnalyzedChunkDTO } from './api';

const instance: AnalyzedChunkDTO = {
  authorEmail,
  authorName,
  chunkIndex,
  classification,
  commitMessages,
  commitShas,
  complexity,
  confidence,
  effortScore,
  errorMessage,
  id,
  isBundled,
  isError,
  isExternalContributor,
  linesChanged,
  llmTokenUsage,
  novelty,
  reasoning,
  timestamp,
  totalChunks,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
