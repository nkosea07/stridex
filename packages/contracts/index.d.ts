export type EndpointKey =
  | 'authLogin'
  | 'authRegister'
  | 'authRegisterOrganizer'
  | 'authRefresh'
  | 'authMe'
  | 'authProfile'
  | 'authOAuthGoogle'
  | 'myDataExport'
  | 'myConsent'
  | 'myAccount'
  | 'myDeletionStatus'
  | 'myDeletionCancel'
  | 'racesList'
  | 'raceById'
  | 'raceCategories'
  | 'raceCategoryById'
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
  | 'raceEmergency'
  | 'raceRoute'
  | 'raceRouteGpx'
  | 'publicRaceRoute'
  | 'raceParticipantPayment'
  | 'raceParticipantPaymentCheckout'
  | 'paymentCallbackPesepay'
  | 'raceAnalyticsSummary'
  | 'raceAnalyticsDemographics'
  | 'raceAnalyticsRegistrations'
  | 'raceAnalyticsExport'
  | 'raceInsightsSummary'
  | 'raceInsightsAnomalies'
  | 'racePromoCodes'
  | 'racePromoCodeActive'
  | 'racePromoCodeDelete'
  | 'raceParticipantPaymentRefund'
  | 'raceParticipantPenalties'
  | 'raceParticipantPenaltyDelete'
  | 'checkpointRecordCorrect'
  | 'checkpointRecords'
  | 'raceCheckpointExport'
  | 'myRegistrations'
  | 'trackingLocation'
  | 'trackingCheckpoint'
  | 'myResults'
  | 'myResultsPage'
  | 'raceResultsPage'
  | 'notifications'
  | 'notificationDevices'
  | 'notificationPreferences'
  | 'notificationTemplates'
  | 'notificationTemplateById';

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
  legal: {
    privacyPolicyVersion: string;
    privacyPolicyPath: string;
  };
  endpoints: Record<EndpointKey, string>;
}

export const API_CONTRACT: ApiContract;
export function getVersionedApiBase(apiRoot?: string): string;
