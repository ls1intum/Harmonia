# TeamRepositoryDTO

## Properties

| Name              | Type                                        | Description | Notes                             |
| ----------------- | ------------------------------------------- | ----------- | --------------------------------- |
| **commitCount**   | **number**                                  |             | [optional] [default to undefined] |
| **error**         | **string**                                  |             | [optional] [default to undefined] |
| **isCloned**      | **boolean**                                 |             | [optional] [default to undefined] |
| **localPath**     | **string**                                  |             | [optional] [default to undefined] |
| **participation** | [**ParticipationDTO**](ParticipationDTO.md) |             | [optional] [default to undefined] |

## Example

```typescript
import { TeamRepositoryDTO } from './api';

const instance: TeamRepositoryDTO = {
  commitCount,
  error,
  isCloned,
  localPath,
  participation,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
