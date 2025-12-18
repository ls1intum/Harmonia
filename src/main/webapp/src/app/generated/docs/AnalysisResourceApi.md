# AnalysisResourceApi

All URIs are relative to _http://localhost:8080_

| Method                      | HTTP request                                         | Description |
| --------------------------- | ---------------------------------------------------- | ----------- |
| [**recompute**](#recompute) | **POST** /api/analysis/{course}/{exercise}/recompute |             |

# **recompute**

> string recompute()

### Example

```typescript
import { AnalysisResourceApi, Configuration } from './api';

const configuration = new Configuration();
const apiInstance = new AnalysisResourceApi(configuration);

let course: string; // (default to undefined)
let exercise: string; // (default to undefined)

const { status, data } = await apiInstance.recompute(course, exercise);
```

### Parameters

| Name         | Type         | Description | Notes                 |
| ------------ | ------------ | ----------- | --------------------- |
| **course**   | [**string**] |             | defaults to undefined |
| **exercise** | [**string**] |             | defaults to undefined |

### Return type

**string**

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)
