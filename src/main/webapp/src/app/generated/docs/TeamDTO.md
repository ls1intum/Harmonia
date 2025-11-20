# TeamDTO

## Properties

| Name          | Type                                                 | Description | Notes                             |
| ------------- | ---------------------------------------------------- | ----------- | --------------------------------- |
| **id**        | **number**                                           |             | [optional] [default to undefined] |
| **name**      | **string**                                           |             | [optional] [default to undefined] |
| **owner**     | [**ParticipantDTO**](ParticipantDTO.md)              |             | [optional] [default to undefined] |
| **shortName** | **string**                                           |             | [optional] [default to undefined] |
| **students**  | [**Array&lt;ParticipantDTO&gt;**](ParticipantDTO.md) |             | [optional] [default to undefined] |

## Example

```typescript
import { TeamDTO } from './api';

const instance: TeamDTO = {
  id,
  name,
  owner,
  shortName,
  students,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
