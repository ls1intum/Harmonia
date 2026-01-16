# ConfigResourceApi

All URIs are relative to *http://localhost:8080*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**getProjects**](#getprojects) | **GET** /api/config/projects | |

# **getProjects**
> Array<Project> getProjects()


### Example

```typescript
import {
    ConfigResourceApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new ConfigResourceApi(configuration);

const { status, data } = await apiInstance.getProjects();
```

### Parameters
This endpoint does not have any parameters.


### Return type

**Array<Project>**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

