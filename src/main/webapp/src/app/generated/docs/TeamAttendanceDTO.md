# TeamAttendanceDTO

## Properties

| Name                        | Type                            | Description | Notes                             |
| --------------------------- | ------------------------------- | ----------- | --------------------------------- |
| **pairedMandatorySessions** | **boolean**                     |             | [optional] [default to undefined] |
| **pairedSessions**          | **Array&lt;string&gt;**         |             | [optional] [default to undefined] |
| **student1Attendance**      | **{ [key: string]: boolean; }** |             | [optional] [default to undefined] |
| **student2Attendance**      | **{ [key: string]: boolean; }** |             | [optional] [default to undefined] |

## Example

```typescript
import { TeamAttendanceDTO } from './api';

const instance: TeamAttendanceDTO = {
  pairedMandatorySessions,
  pairedSessions,
  student1Attendance,
  student2Attendance,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
