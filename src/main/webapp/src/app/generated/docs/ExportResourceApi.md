# ExportResourceApi

All URIs are relative to _http://localhost:8080_

| Method                        | HTTP request                     | Description |
| ----------------------------- | -------------------------------- | ----------- |
| [**exportData**](#exportdata) | **GET** /api/export/{exerciseId} |             |

# **exportData**

> string exportData()

### Example

```typescript
import { ExportResourceApi, Configuration } from './api';

const configuration = new Configuration();
const apiInstance = new ExportResourceApi(configuration);

let exerciseId: number; // (default to undefined)
let format: 'EXCEL' | 'JSON'; // (optional) (default to 'EXCEL')
let include: Array<string>; // (optional) (default to undefined)

const { status, data } = await apiInstance.exportData(exerciseId, format, include);
```

### Parameters

| Name           | Type                    | Description                                                         | Notes                            |
| -------------- | ----------------------- | ------------------------------------------------------------------- | -------------------------------- | ------------------------------ |
| **exerciseId** | [**number**]            |                                                                     | defaults to undefined            |
| **format**     | [\*\*&#39;EXCEL&#39;    | &#39;JSON&#39;**]**Array<&#39;EXCEL&#39; &#124; &#39;JSON&#39;>\*\* |                                  | (optional) defaults to 'EXCEL' |
| **include**    | **Array&lt;string&gt;** |                                                                     | (optional) defaults to undefined |

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
