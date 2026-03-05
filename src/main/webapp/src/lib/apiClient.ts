import {
  Configuration,
  AnalysisResourceApi,
  RequestResourceApi,
  EmailMappingResourceApi,
  AuthResourceApi,
  PairProgrammingResourceApi,
  ExportResourceApi,
  CqiWeightResourceApi,
} from '@/app/generated';

export const apiConfig = new Configuration({
  basePath: window.location.origin,
  baseOptions: { withCredentials: true },
});

export const analysisApi = new AnalysisResourceApi(apiConfig);
export const requestApi = new RequestResourceApi(apiConfig);
export const emailMappingApi = new EmailMappingResourceApi(apiConfig);
export const authApi = new AuthResourceApi(apiConfig);
export const pairProgrammingApi = new PairProgrammingResourceApi(apiConfig);
export const exportApi = new ExportResourceApi(apiConfig);
export const cqiWeightsApi = new CqiWeightResourceApi(apiConfig);
