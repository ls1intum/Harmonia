# AttendanceResourceApi

All URIs are relative to _http://localhost:8080_

| Method                                    | HTTP request                    | Description |
| ----------------------------------------- | ------------------------------- | ----------- |
| [**uploadAttendance**](#uploadattendance) | **POST** /api/attendance/upload |             |

# **uploadAttendance**

> TeamsScheduleDTO uploadAttendance()

### Example

```typescript
import { AttendanceResourceApi, Configuration } from './api';

const configuration = new Configuration();
const apiInstance = new AttendanceResourceApi(configuration);

let courseId: number; // (default to undefined)
let exerciseId: number; // (default to undefined)
let file: File; // (default to undefined)
let jwt: string; // (optional) (default to undefined)
let artemisServerUrl: string; // (optional) (default to undefined)
let artemisUsername: string; // (optional) (default to undefined)
let artemisPassword: string; // (optional) (default to undefined)
let serverUrl: string; // (optional) (default to undefined)
let username: string; // (optional) (default to undefined)
let password: string; // (optional) (default to undefined)

const { status, data } = await apiInstance.uploadAttendance(
  courseId,
  exerciseId,
  file,
  jwt,
  artemisServerUrl,
  artemisUsername,
  artemisPassword,
  serverUrl,
  username,
  password,
);
```

### Parameters

| Name                 | Type         | Description | Notes                            |
| -------------------- | ------------ | ----------- | -------------------------------- |
| **courseId**         | [**number**] |             | defaults to undefined            |
| **exerciseId**       | [**number**] |             | defaults to undefined            |
| **file**             | [**File**]   |             | defaults to undefined            |
| **jwt**              | [**string**] |             | (optional) defaults to undefined |
| **artemisServerUrl** | [**string**] |             | (optional) defaults to undefined |
| **artemisUsername**  | [**string**] |             | (optional) defaults to undefined |
| **artemisPassword**  | [**string**] |             | (optional) defaults to undefined |
| **serverUrl**        | [**string**] |             | (optional) defaults to undefined |
| **username**         | [**string**] |             | (optional) defaults to undefined |
| **password**         | [**string**] |             | (optional) defaults to undefined |

### Return type

**TeamsScheduleDTO**

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: multipart/form-data
- **Accept**: application/json

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)
