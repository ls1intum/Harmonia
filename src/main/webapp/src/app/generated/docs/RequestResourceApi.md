# RequestResourceApi

All URIs are relative to _http://localhost:8080_

| Method                      | HTTP request                           | Description |
| --------------------------- | -------------------------------------- | ----------- |
| [**fetchData**](#fetchdata) | **GET** /api/requestResource/fetchData |             |

# **fetchData**

> Array<ClientResponseDTO> fetchData()

### Example

```typescript
import { RequestResourceApi, Configuration } from './api';

const configuration = new Configuration();
const apiInstance = new RequestResourceApi(configuration);

let jwt: string; // (optional) (default to undefined)
let artemisServerUrl: string; // (optional) (default to undefined)
let artemisUsername: string; // (optional) (default to undefined)
let artemisPassword: string; // (optional) (default to undefined)

const { status, data } = await apiInstance.fetchData(jwt, artemisServerUrl, artemisUsername, artemisPassword);
```

### Parameters

| Name                 | Type         | Description | Notes                            |
| -------------------- | ------------ | ----------- | -------------------------------- |
| **jwt**              | [**string**] |             | (optional) defaults to undefined |
| **artemisServerUrl** | [**string**] |             | (optional) defaults to undefined |
| **artemisUsername**  | [**string**] |             | (optional) defaults to undefined |
| **artemisPassword**  | [**string**] |             | (optional) defaults to undefined |

### Return type

**Array<ClientResponseDTO>**

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
