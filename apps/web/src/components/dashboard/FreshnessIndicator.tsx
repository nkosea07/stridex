import { cn } from '@/lib/utils';
import { useTranslation } from 'react-i18next';

interface FreshnessIndicatorProps {
  lastSeen: number | undefined;
  now: number;
  className?: string;
}

export function FreshnessIndicator({ lastSeen, now, className }: FreshnessIndicatorProps) {
  const { t } = useTranslation();
  if (!lastSeen) return <span className={cn("text-[10px] font-mono font-bold tracking-wider text-blue-500", className)}>{t('liveTracking:freshness.live')}</span>;

  const secondsAgo = Math.max(0, Math.floor((now - lastSeen) / 1000));
  const isStale = secondsAgo > 60;

  return (
    <span className={cn(
      "text-[10px] font-mono font-bold tracking-wider",
      isStale ? "text-muted-foreground" : "text-blue-500",
      className
    )}>
      {isStale ? t('liveTracking:freshness.stale', { seconds: secondsAgo }) : t('liveTracking:freshness.updated', { seconds: secondsAgo })}
    </span>
  );
}
