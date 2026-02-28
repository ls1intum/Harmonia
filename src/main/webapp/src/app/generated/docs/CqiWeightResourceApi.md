# CqiWeightResourceApi

All URIs are relative to *http://localhost:8080*

|Method | HTTP request | Description|
|------------- | ------------- | -------------|
|[**getWeights**](#getweights) | **GET** /api/exercises/{exerciseId}/cqi-weights | |
|[**resetWeights**](#resetweights) | **DELETE** /api/exercises/{exerciseId}/cqi-weights | |
|[**saveWeights**](#saveweights) | **PUT** /api/exercises/{exerciseId}/cqi-weights | |

# **getWeights**
> CqiWeightsDTO getWeights()


### Example

```typescript
import {
    CqiWeightResourceApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new CqiWeightResourceApi(configuration);

let exerciseId: number; // (default to undefined)

const { status, data } = await apiInstance.getWeights(
    exerciseId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **exerciseId** | [**number**] |  | defaults to undefined|


### Return type

**CqiWeightsDTO**

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

# **resetWeights**
> CqiWeightsDTO resetWeights()


### Example

```typescript
import {
    CqiWeightResourceApi,
    Configuration
} from './api';

const configuration = new Configuration();
const apiInstance = new CqiWeightResourceApi(configuration);

let exerciseId: number; // (default to undefined)

const { status, data } = await apiInstance.resetWeights(
    exerciseId
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **exerciseId** | [**number**] |  | defaults to undefined|


### Return type

**CqiWeightsDTO**

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

# **saveWeights**
> CqiWeightsDTO saveWeights(cqiWeightsDTO)


### Example

```typescript
import {
    CqiWeightResourceApi,
    Configuration,
    CqiWeightsDTO
} from './api';

const configuration = new Configuration();
const apiInstance = new CqiWeightResourceApi(configuration);

let exerciseId: number; // (default to undefined)
let cqiWeightsDTO: CqiWeightsDTO; //

const { status, data } = await apiInstance.saveWeights(
    exerciseId,
    cqiWeightsDTO
);
```

### Parameters

|Name | Type | Description  | Notes|
|------------- | ------------- | ------------- | -------------|
| **cqiWeightsDTO** | **CqiWeightsDTO**|  | |
| **exerciseId** | [**number**] |  | defaults to undefined|


### Return type

**CqiWeightsDTO**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: application/json


### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
|**200** | OK |  -  |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

