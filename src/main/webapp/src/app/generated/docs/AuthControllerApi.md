# AuthControllerApi

All URIs are relative to _http://localhost:8080_

| Method              | HTTP request             | Description |
| ------------------- | ------------------------ | ----------- |
| [**login**](#login) | **POST** /api/auth/login |             |

# **login**

> login(loginRequestDTO)

### Example

```typescript
import { AuthControllerApi, Configuration, LoginRequestDTO } from './api';

const configuration = new Configuration();
const apiInstance = new AuthControllerApi(configuration);

let loginRequestDTO: LoginRequestDTO; //

const { status, data } = await apiInstance.login(loginRequestDTO);
```

### Parameters

| Name                | Type                | Description | Notes |
| ------------------- | ------------------- | ----------- | ----- |
| **loginRequestDTO** | **LoginRequestDTO** |             |       |

### Return type

void (empty response body)

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: Not defined

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)
