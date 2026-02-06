# RequestResourceApi

All URIs are relative to *http://localhost:8080*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**fetchData**](#fetchdata) | **GET** /api/requestResource/fetchData | |
|[**getData**](#getdata) | **GET** /api/requestResource/{exerciseId}/getData | |
|[**getTeamsByExercise**](#getteamsbyexercise) | **GET** /api/requestResource/teams/{exerciseId} | |
|[**hasAnalyzedData**](#hasanalyzeddata) | **GET** /api/requestResource/hasData/{exerciseId} | |
|[**streamAnalysis**](#streamanalysis) | **GET** /api/requestResource/stream | |

# **fetchData**
> Array<ClientResponseDTO> fetchData()


### Example

```typescript
import {
    RequestResourceApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new RequestResourceApi(configuration);

let exerciseId: number; // (default to undefined)
let jwt: string; // (optional) (default to undefined)
let artemisServerUrl: string; // (optional) (default to undefined)
let artemisUsername: string; // (optional) (default to undefined)
let artemisPassword: string; // (optional) (default to undefined)

const { status, data } = await apiInstance.fetchData(
    exerciseId,
    jwt,
    artemisServerUrl,
    artemisUsername,
    artemisPassword
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **exerciseId** | [**number**] |  | defaults to undefined|
| **jwt** | [**string**] |  | (optional) defaults to undefined|
| **artemisServerUrl** | [**string**] |  | (optional) defaults to undefined|
| **artemisUsername** | [**string**] |  | (optional) defaults to undefined|
| **artemisPassword** | [**string**] |  | (optional) defaults to undefined|


### Return type

**Array<ClientResponseDTO>**

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

# **getData**
> Array<ClientResponseDTO> getData()


### Example

```typescript
import {
    RequestResourceApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new RequestResourceApi(configuration);

let exerciseId: number; // (default to undefined)

const { status, data } = await apiInstance.getData(
    exerciseId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **exerciseId** | [**number**] |  | defaults to undefined|


### Return type

**Array<ClientResponseDTO>**

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

# **getTeamsByExercise**
> Array<ClientResponseDTO> getTeamsByExercise()


### Example

```typescript
import {
    RequestResourceApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new RequestResourceApi(configuration);

let exerciseId: number; // (default to undefined)

const { status, data } = await apiInstance.getTeamsByExercise(
    exerciseId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **exerciseId** | [**number**] |  | defaults to undefined|


### Return type

**Array<ClientResponseDTO>**

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

# **hasAnalyzedData**
> boolean hasAnalyzedData()


### Example

```typescript
import {
    RequestResourceApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new RequestResourceApi(configuration);

let exerciseId: number; // (default to undefined)

const { status, data } = await apiInstance.hasAnalyzedData(
    exerciseId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **exerciseId** | [**number**] |  | defaults to undefined|


### Return type

**boolean**

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

# **streamAnalysis**
> SseEmitter streamAnalysis()


### Example

```typescript
import {
    RequestResourceApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new RequestResourceApi(configuration);

let exerciseId: number; // (default to undefined)
let jwt: string; // (optional) (default to undefined)
let artemisServerUrl: string; // (optional) (default to undefined)
let artemisUsername: string; // (optional) (default to undefined)
let artemisPassword: string; // (optional) (default to undefined)

const { status, data } = await apiInstance.streamAnalysis(
    exerciseId,
    jwt,
    artemisServerUrl,
    artemisUsername,
    artemisPassword
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **exerciseId** | [**number**] |  | defaults to undefined|
| **jwt** | [**string**] |  | (optional) defaults to undefined|
| **artemisServerUrl** | [**string**] |  | (optional) defaults to undefined|
| **artemisUsername** | [**string**] |  | (optional) defaults to undefined|
| **artemisPassword** | [**string**] |  | (optional) defaults to undefined|


### Return type

**SseEmitter**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: text/event-stream


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

