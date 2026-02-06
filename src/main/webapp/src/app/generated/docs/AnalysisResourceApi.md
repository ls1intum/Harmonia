# AnalysisResourceApi

All URIs are relative to *http://localhost:8080*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**cancelAnalysis**](#cancelanalysis) | **POST** /api/analysis/{exerciseId}/cancel | |
|[**clearData**](#cleardata) | **DELETE** /api/analysis/{exerciseId}/clear | |
|[**getStatus**](#getstatus) | **GET** /api/analysis/{exerciseId}/status | |
|[**recompute**](#recompute) | **POST** /api/analysis/{course}/{exercise}/recompute | |

# **cancelAnalysis**
> AnalysisStatusDTO cancelAnalysis()


### Example

```typescript
import {
    AnalysisResourceApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new AnalysisResourceApi(configuration);

let exerciseId: number; // (default to undefined)

const { status, data } = await apiInstance.cancelAnalysis(
    exerciseId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **exerciseId** | [**number**] |  | defaults to undefined|


### Return type

**AnalysisStatusDTO**

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

# **clearData**
> string clearData()


### Example

```typescript
import {
    AnalysisResourceApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new AnalysisResourceApi(configuration);

let exerciseId: number; // (default to undefined)
let type: string; // (optional) (default to 'both')

const { status, data } = await apiInstance.clearData(
    exerciseId,
    type
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **exerciseId** | [**number**] |  | defaults to undefined|
| **type** | [**string**] |  | (optional) defaults to 'both'|


### Return type

**string**

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

# **getStatus**
> AnalysisStatusDTO getStatus()


### Example

```typescript
import {
    AnalysisResourceApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new AnalysisResourceApi(configuration);

let exerciseId: number; // (default to undefined)

const { status, data } = await apiInstance.getStatus(
    exerciseId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **exerciseId** | [**number**] |  | defaults to undefined|


### Return type

**AnalysisStatusDTO**

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

# **recompute**
> string recompute()


### Example

```typescript
import {
    AnalysisResourceApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new AnalysisResourceApi(configuration);

let course: string; // (default to undefined)
let exercise: string; // (default to undefined)

const { status, data } = await apiInstance.recompute(
    course,
    exercise
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **course** | [**string**] |  | defaults to undefined|
| **exercise** | [**string**] |  | defaults to undefined|


### Return type

**string**

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

