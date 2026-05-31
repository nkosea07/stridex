import React from 'react';
import { cn } from '@/lib/utils';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Settings, Users, Shield, Filter } from 'lucide-react';

export type TrackingMode = 'spectator' | 'organizer';

interface CommandBarProps {
  raceTitle?: string;
  mode: TrackingMode;
  onModeChange: (mode: TrackingMode) => void;
  className?: string;
}

export function CommandBar({
  raceTitle = 'Live Race Tracking',
  mode,
  onModeChange,
  className,
}: CommandBarProps) {
  return (
    <div className={cn(
      "absolute top-6 left-1/2 -translate-x-1/2 z-50",
      "flex items-center gap-4 px-4 py-2 rounded-full",
      "bg-background/80 backdrop-blur-md border border-white/20 shadow-2xl",
      "w-fit min-w-[400px] justify-between",
      className
    )}>
      {/* Race Title & Status */}
      <div className="flex items-center gap-3">
        <div className="flex h-8 w-8 items-center justify-center rounded-full bg-live/10 text-live">
          <div className="h-2 w-2 rounded-full bg-live animate-live-pulse" />
        </div>
        <div>
          <h1 className="text-sm font-bold tracking-tight uppercase">{raceTitle}</h1>
          <Badge variant="outline" className="text-[10px] h-4 py-0 border-live/50 text-live bg-live/5">
            LIVE
          </Badge>
        </div>
      </div>

      {/* Center Actions / Filters Placeholder */}
      <div className="flex items-center gap-1 border-x border-border/50 px-4">
        <Button variant="ghost" size="sm" className="h-8 w-8 p-0">
          <Filter className="h-4 w-4" />
        </Button>
        <Button variant="ghost" size="sm" className="h-8 w-8 p-0">
          <Settings className="h-4 w-4" />
        </Button>
      </div>

      {/* Mode Toggle */}
      <div className="flex items-center bg-muted/50 rounded-full p-1">
        <Button
          variant={mode === 'spectator' ? 'secondary' : 'ghost'}
          size="sm"
          className={cn(
            "h-7 px-3 rounded-full text-xs gap-1.5 transition-all",
            mode === 'spectator' && "bg-background shadow-sm"
          )}
          onClick={() => onModeChange('spectator')}
        >
          <Users className="h-3.5 w-3.5" />
          Spectator
        </Button>
        <Button
          variant={mode === 'organizer' ? 'secondary' : 'ghost'}
          size="sm"
          className={cn(
            "h-7 px-3 rounded-full text-xs gap-1.5 transition-all",
            mode === 'organizer' && "bg-background shadow-sm"
          )}
          onClick={() => onModeChange('organizer')}
        >
          <Shield className="h-3.5 w-3.5" />
          Organizer
        </Button>
      </div>
    </div>
  );
}
