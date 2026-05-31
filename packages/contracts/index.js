export const API_CONTRACT = {
  apiVersion: 1,
  apiPath: '/api',
  web: {
    apiBasePath: '/api/v1',
    wsTrackingPath: '/api/ws/tracking',
  },
  mobile: {
    defaultApiRoot: 'http://localhost:8081/api',
  },
  endpoints: {
    authLogin: '/auth/login',
    authRegister: '/auth/register',
    authRefresh: '/auth/refresh',
    authMe: '/auth/me',
    authProfile: '/auth/profile',
    racesList: '/races',
    raceById: '/races/:id',
    raceRegister: '/races/:id/participants/register',
    myRegistrations: '/participants/my-registrations',
    trackingLocation: '/tracking/:raceId/location',
    trackingCheckpoint: '/tracking/:raceId/checkpoint',
    myResults: '/results/my',
    notifications: '/notifications',
  },
};

export function getVersionedApiBase(apiRoot) {
  const root = (apiRoot || API_CONTRACT.mobile.defaultApiRoot).replace(/\/+$/, '');
  return `${root}/v${API_CONTRACT.apiVersion}`;
}

