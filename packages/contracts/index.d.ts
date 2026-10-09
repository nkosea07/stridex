export type EndpointKey =
  | 'authLogin'
  | 'authRegister'
  | 'authRegisterOrganizer'
  | 'authRefresh'
  | 'authMe'
  | 'authProfile'
  | 'racesList'
  | 'raceById'
  | 'raceClone'
  | 'raceArchive'
  | 'raceRestore'
  | 'raceRegister'
  | 'publicRaceHub'
  | 'publicRaceLive'
  | 'publicRaceLiveFeed'
  | 'publicRaceResults'
  | 'publicRaceResultCertificate'
  | 'raceResultsExport'
  | 'raceResultCertificate'
  | 'raceTeamRankings'
  | 'raceCountryRankings'
  | 'raceMedia'
  | 'raceMediaApprove'
  | 'raceMediaReject'
  | 'raceMediaDelete'
  | 'publicRaceMedia'
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
  | 'raceParticipantPaymentCheckout'
  | 'paymentCallbackPesepay'
  | 'raceAnalyticsSummary'
  | 'raceAnalyticsDemographics'
  | 'raceAnalyticsRegistrations'
  | 'racePromoCodes'
  | 'racePromoCodeActive'
  | 'racePromoCodeDelete'
  | 'raceParticipantPaymentRefund'
  | 'raceParticipantPenalties'
  | 'raceParticipantPenaltyDelete'
  | 'checkpointRecordCorrect'
  | 'myRegistrations'
  | 'trackingLocation'
  | 'trackingCheckpoint'
  | 'myResults'
  | 'myResultsPage'
  | 'raceResultsPage'
  | 'notifications'
  | 'notificationDevices';

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
