export type EndpointKey =
  | 'authLogin'
  | 'authRegister'
  | 'authRefresh'
  | 'authMe'
  | 'authProfile'
  | 'racesList'
  | 'raceById'
  | 'raceRegister'
  | 'publicRaceHub'
  | 'publicRaceLive'
  | 'publicRaceSearch'
  | 'publicAthleteTracking'
  | 'myRegistrations'
  | 'trackingLocation'
  | 'trackingCheckpoint'
  | 'myResults'
  | 'notifications';

export interface ApiContract {
  apiVersion: number;
  apiPath: string;
  web: {
    apiBasePath: string;
    wsTrackingPath: string;
  };
  mobile: {
    defaultApiRoot: string;
  };
  endpoints: Record<EndpointKey, string>;
}

export const API_CONTRACT: ApiContract;
export function getVersionedApiBase(apiRoot?: string): string;
