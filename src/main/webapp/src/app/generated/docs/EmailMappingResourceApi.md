# EmailMappingResourceApi

All URIs are relative to _http://localhost:8080_

| Method                                              | HTTP request                                                          | Description |
| --------------------------------------------------- | --------------------------------------------------------------------- | ----------- |
| [**createMapping**](#createmapping)                 | **POST** /api/exercises/{exerciseId}/email-mappings                   |             |
| [**deleteMapping**](#deletemapping)                 | **DELETE** /api/exercises/{exerciseId}/email-mappings/{mappingId}     |             |
| [**deleteTemplateAuthors**](#deletetemplateauthors) | **DELETE** /api/exercises/{exerciseId}/email-mappings/template-author |             |
| [**getAllMappings**](#getallmappings)               | **GET** /api/exercises/{exerciseId}/email-mappings                    |             |
| [**getTemplateAuthors**](#gettemplateauthors)       | **GET** /api/exercises/{exerciseId}/email-mappings/template-author    |             |
| [**setTemplateAuthors**](#settemplateauthors)       | **PUT** /api/exercises/{exerciseId}/email-mappings/template-author    |             |

# **createMapping**

> ClientResponseDTO createMapping(createEmailMappingRequest)

### Example

```typescript
import { EmailMappingResourceApi, Configuration, CreateEmailMappingRequest } from './api';

const configuration = new Configuration();
const apiInstance = new EmailMappingResourceApi(configuration);

let exerciseId: number; // (default to undefined)
let createEmailMappingRequest: CreateEmailMappingRequest; //

const { status, data } = await apiInstance.createMapping(exerciseId, createEmailMappingRequest);
```

### Parameters

| Name                          | Type                          | Description | Notes                 |
| ----------------------------- | ----------------------------- | ----------- | --------------------- |
| **createEmailMappingRequest** | **CreateEmailMappingRequest** |             |                       |
| **exerciseId**                | [**number**]                  |             | defaults to undefined |

### Return type

**ClientResponseDTO**

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **deleteMapping**

> ClientResponseDTO deleteMapping()

### Example

```typescript
import { EmailMappingResourceApi, Configuration } from './api';

const configuration = new Configuration();
const apiInstance = new EmailMappingResourceApi(configuration);

let exerciseId: number; // (default to undefined)
let mappingId: string; // (default to undefined)

const { status, data } = await apiInstance.deleteMapping(exerciseId, mappingId);
```

### Parameters

| Name           | Type         | Description | Notes                 |
| -------------- | ------------ | ----------- | --------------------- |
| **exerciseId** | [**number**] |             | defaults to undefined |
| **mappingId**  | [**string**] |             | defaults to undefined |

### Return type

**ClientResponseDTO**

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

# **deleteTemplateAuthors**

> Array<ClientResponseDTO> deleteTemplateAuthors()

### Example

```typescript
import { EmailMappingResourceApi, Configuration } from './api';

const configuration = new Configuration();
const apiInstance = new EmailMappingResourceApi(configuration);

let exerciseId: number; // (default to undefined)

const { status, data } = await apiInstance.deleteTemplateAuthors(exerciseId);
```

### Parameters

| Name           | Type         | Description | Notes                 |
| -------------- | ------------ | ----------- | --------------------- |
| **exerciseId** | [**number**] |             | defaults to undefined |

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

# **getAllMappings**

> Array<EmailMappingDTO> getAllMappings()

### Example

```typescript
import { EmailMappingResourceApi, Configuration } from './api';

const configuration = new Configuration();
const apiInstance = new EmailMappingResourceApi(configuration);

let exerciseId: number; // (default to undefined)

const { status, data } = await apiInstance.getAllMappings(exerciseId);
```

### Parameters

| Name           | Type         | Description | Notes                 |
| -------------- | ------------ | ----------- | --------------------- |
| **exerciseId** | [**number**] |             | defaults to undefined |

### Return type

**Array<EmailMappingDTO>**

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

# **getTemplateAuthors**

> Array<TemplateAuthorDTO> getTemplateAuthors()

### Example

```typescript
import { EmailMappingResourceApi, Configuration } from './api';

const configuration = new Configuration();
const apiInstance = new EmailMappingResourceApi(configuration);

let exerciseId: number; // (default to undefined)

const { status, data } = await apiInstance.getTemplateAuthors(exerciseId);
```

### Parameters

| Name           | Type         | Description | Notes                 |
| -------------- | ------------ | ----------- | --------------------- |
| **exerciseId** | [**number**] |             | defaults to undefined |

### Return type

**Array<TemplateAuthorDTO>**

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

# **setTemplateAuthors**

> Array<ClientResponseDTO> setTemplateAuthors(templateAuthorDTO)

### Example

```typescript
import { EmailMappingResourceApi, Configuration } from './api';

const configuration = new Configuration();
const apiInstance = new EmailMappingResourceApi(configuration);

let exerciseId: number; // (default to undefined)
let templateAuthorDTO: Array<TemplateAuthorDTO>; //

const { status, data } = await apiInstance.setTemplateAuthors(exerciseId, templateAuthorDTO);
```

### Parameters

| Name                  | Type                         | Description | Notes                 |
| --------------------- | ---------------------------- | ----------- | --------------------- |
| **templateAuthorDTO** | **Array<TemplateAuthorDTO>** |             |                       |
| **exerciseId**        | [**number**]                 |             | defaults to undefined |

### Return type

**Array<ClientResponseDTO>**

### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: application/json
- **Accept**: application/json

### HTTP response details

| Status code | Description | Response headers |
| ----------- | ----------- | ---------------- |
| **200**     | OK          | -                |

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)
