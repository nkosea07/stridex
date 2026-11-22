import { useEffect, useState } from 'react';
import { AlertTriangle } from 'lucide-react';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { useTranslation } from 'react-i18next';

export function SecurityAuditor() {
  const { t } = useTranslation();
  const [warnings, setWarnings] = useState<string[]>([]);
  const isDev = import.meta.env.DEV;

  useEffect(() => {
    const issues: string[] = [];
    
    if (!import.meta.env.VITE_MAPBOX_TOKEN) {
      issues.push(t('liveTracking:config.mapboxMissing'));
    }
    
    if (!isDev && import.meta.env.VITE_API_URL?.includes('localhost')) {
      issues.push(t('liveTracking:config.localhostApi'));
    }

    setWarnings(issues);
  }, [isDev]);

  if (warnings.length === 0) return null;

  return (
    <div className="fixed bottom-24 right-6 z-[100] max-w-md animate-in fade-in slide-in-from-right-4">
      <Alert variant="destructive" className="bg-destructive/90 text-destructive-foreground border-none backdrop-blur-md shadow-2xl">
        <AlertTriangle className="h-4 w-4" />
        <AlertTitle className="font-black uppercase text-[10px] tracking-widest">{t('liveTracking:config.title')}</AlertTitle>
        <AlertDescription className="text-xs font-medium">
          <ul className="list-disc list-inside mt-1">
            {warnings.map((w, i) => <li key={i}>{w}</li>)}
          </ul>
        </AlertDescription>
      </Alert>
    </div>
  );
}
