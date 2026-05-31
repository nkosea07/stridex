import { useEffect, useState } from 'react';
import { cn } from '@/lib/utils';

interface FreshnessIndicatorProps {
  lastSeen: number | undefined;
  now: number;
  className?: string;
}

export function FreshnessIndicator({ lastSeen, now, className }: FreshnessIndicatorProps) {
  if (!lastSeen) return <span className={cn("text-[10px] font-mono font-bold tracking-wider text-blue-500", className)}>SYNC: LIVE</span>;

  const secondsAgo = Math.max(0, Math.floor((now - lastSeen) / 1000));
  const isStale = secondsAgo > 60;

  return (
    <span className={cn(
      "text-[10px] font-mono font-bold tracking-wider",
      isStale ? "text-muted-foreground" : "text-blue-500",
      className
    )}>
      {isStale ? `STALE: ${secondsAgo}S AGO` : `UPDATED ${secondsAgo}S AGO`}
    </span>
  );
}
