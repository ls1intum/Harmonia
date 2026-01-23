# ClientResponseDTO


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**analysisHistory** | [**Array&lt;AnalyzedChunkDTO&gt;**](AnalyzedChunkDTO.md) |  | [optional] [default to undefined]
**cqi** | **number** |  | [optional] [default to undefined]
**isSuspicious** | **boolean** |  | [optional] [default to undefined]
**orphanCommits** | [**Array&lt;OrphanCommitDTO&gt;**](OrphanCommitDTO.md) |  | [optional] [default to undefined]
**students** | [**Array&lt;StudentAnalysisDTO&gt;**](StudentAnalysisDTO.md) |  | [optional] [default to undefined]
**submissionCount** | **number** |  | [optional] [default to undefined]
**teamId** | **number** |  | [optional] [default to undefined]
**teamName** | **string** |  | [optional] [default to undefined]
**tutor** | **string** |  | [optional] [default to undefined]

## Example

```typescript
import { ClientResponseDTO } from './api';

const instance: ClientResponseDTO = {
    analysisHistory,
    cqi,
    isSuspicious,
    orphanCommits,
    students,
    submissionCount,
    teamId,
    teamName,
    tutor,
};
```

[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)
