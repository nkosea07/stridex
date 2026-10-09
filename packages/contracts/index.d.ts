export type EndpointKey =
  | 'authLogin'
  | 'authRegister'
  | 'authRegisterOrganizer'
  | 'authRefresh'
  | 'authMe'
  | 'authProfile'
  | 'racesList'
  | 'raceById'
  | 'raceRegister'
  | 'publicRaceHub'
  | 'publicRaceLive'
  | 'publicRaceLiveFeed'
  | 'publicRaceResults'
  | 'publicRaceSearch'
  | 'publicAthleteTracking'
  | 'publicAthleteTrack'
  | 'publicRaceHeatmap'
  | 'publicRaceRegistrationFields'
  | 'favorites'
  | 'favoriteDelete'
  | 'raceRegistrationFields'
  | 'raceTeams'
  | 'raceParticipantReview'
  | 'raceParticipantPayment'
  | 'myRegistrations'
  | 'trackingLocation'
  | 'trackingCheckpoint'
  | 'myResults'
  | 'myResultsPage'
  | 'raceResultsPage'
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
