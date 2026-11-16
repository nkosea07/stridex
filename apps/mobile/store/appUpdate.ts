import { create } from 'zustand';
import { api, type AppConfig } from '@/lib/api';
import { isVersionBelow } from '@/lib/appVersion';

type AppUpdateState = {
  updateRequired: boolean;
  config: AppConfig | null;
  markUpdateRequired: () => void;
  check: (currentVersion: string | null) => Promise<void>;
};

export const useAppUpdateStore = create<AppUpdateState>((set, get) => ({
  updateRequired: false,
  config: null,
  markUpdateRequired: () => {
    set({ updateRequired: true });
    if (!get().config) {
      api.getAppConfig().then((config) => set({ config })).catch(() => undefined);
    }
  },
  check: async (currentVersion) => {
    try {
      const config = await api.getAppConfig();
      set({ config, updateRequired: get().updateRequired || isVersionBelow(currentVersion, config.minimumVersion) });
    } catch {
      return;
    }
  },
}));
