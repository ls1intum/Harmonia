# RequestResourceApi

All URIs are relative to _http://localhost:8080_

| Method                                                      | HTTP request                                           | Description |
| ----------------------------------------------------------- | ------------------------------------------------------ | ----------- |
| [**fetchAndCloneRepositories**](#fetchandclonerepositories) | **GET** /api/requestResource/fetchAndCloneRepositories |             |

# **fetchAndCloneRepositories**

> Array<TeamRepositoryDTO> fetchAndCloneRepositories()

### Example

```typescript
import { RequestResourceApi, Configuration } from './api';

const configuration = new Configuration();
const apiInstance = new RequestResourceApi(configuration);

const { status, data } = await apiInstance.fetchAndCloneRepositories();
```

### Parameters

This endpoint does not have any parameters.

### Return type

**Array<TeamRepositoryDTO>**

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
