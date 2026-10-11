import Constants from 'expo-constants';
import * as Updates from 'expo-updates';

export type MobileRuntimeInfo = {
  appVersion: string | null;
  runtimeVersion: string | null;
  updateId: string | null;
  updateChannel: string | null;
  updatesEnabled: boolean;
  isEmbeddedLaunch: boolean;
};

export const getMobileRuntimeInfo = (): MobileRuntimeInfo => ({
  appVersion: Constants.expoConfig?.version ?? null,
  runtimeVersion: Updates.runtimeVersion,
  updateId: Updates.updateId,
  updateChannel: Updates.channel,
  updatesEnabled: Updates.isEnabled,
  isEmbeddedLaunch: Updates.isEmbeddedLaunch,
});
