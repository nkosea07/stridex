import React from 'react';
import { cn } from '@/lib/utils';
import { X, MapPin, Zap, Navigation, Timer } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import type { LiveTrackingData } from '@/types';

interface AeroPanelProps {
  participant: LiveTrackingData | null;
  onClose: () => void;
  className?: string;
}

export function AeroPanel({ participant, onClose, className }: AeroPanelProps) {
  if (!participant) return null;

  return (
    <div className={cn(
      "absolute bottom-24 left-1/2 -translate-x-1/2 z-40",
      "w-[340px] overflow-hidden",
      "bg-background/40 backdrop-blur-xl border border-white/10 shadow-2xl rounded-2xl",
      "animate-in fade-in slide-in-from-bottom-4 duration-300",
      className
    )}>
      {/* Decorative Gradient Header */}
      <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-blue-500 via-orange-500 to-blue-500 animate-gradient-x" />

      <div className="p-4">
        <div className="flex justify-between items-start mb-4">
          <div className="space-y-1">
            <div className="flex items-center gap-2">
              <Badge variant="secondary" className="bg-primary/20 text-primary border-none font-mono text-[10px] h-4 px-1.5">
                BIB {participant.bibNumber}
              </Badge>
              <div className="flex items-center gap-1">
                <div className="h-1.5 w-1.5 rounded-full bg-live animate-live-pulse" />
                <span className="text-[9px] font-black uppercase tracking-[0.1em] text-live">Live Telemetry</span>
              </div>
            </div>
            <h3 className="text-xl font-black italic uppercase tracking-tighter leading-none">
              {participant.participantName}
            </h3>
          </div>
          <Button 
            variant="ghost" 
            size="icon" 
            className="h-8 w-8 rounded-full hover:bg-white/10 text-muted-foreground hover:text-foreground"
            onClick={onClose}
          >
            <X className="h-4 w-4" />
          </Button>
        </div>

        <div className="grid grid-cols-2 gap-2 mb-4">
          <div className="bg-white/5 border border-white/5 rounded-xl p-3 backdrop-blur-sm">
            <div className="flex items-center gap-1.5 mb-1">
              <Zap className="h-3 w-3 text-orange-500" />
              <span className="text-[9px] font-bold text-muted-foreground uppercase tracking-wider">Avg Pace</span>
            </div>
            <p className="text-lg font-black font-mono">
              {participant.averagePace || Number(participant.speed || 0).toFixed(1)}
              <span className="text-[10px] ml-1 text-muted-foreground">{participant.averagePace ? 'MIN/KM' : 'KM/H'}</span>
            </p>
          </div>
          <div className="bg-white/5 border border-white/5 rounded-xl p-3 backdrop-blur-sm">
            <div className="flex items-center gap-1.5 mb-1">
              <Navigation className="h-3 w-3 text-blue-500" />
              <span className="text-[9px] font-bold text-muted-foreground uppercase tracking-wider">Last CP</span>
            </div>
            <p className="text-lg font-black font-mono truncate">
              {participant.lastCheckpoint || 'N/A'}
            </p>
          </div>
        </div>

        <div className="bg-primary/5 border border-primary/10 rounded-xl p-3 mb-4">
          <div className="flex items-center justify-between mb-3 border-b border-primary/10 pb-2">
            <div className="flex items-center gap-1.5">
              <Timer className="h-3 w-3 text-primary" />
              <span className="text-[9px] font-bold text-primary uppercase tracking-wider">Estimated Finish</span>
            </div>
            <span className="text-xs font-black font-mono text-primary">
              {participant.predictedFinishTime 
                ? new Date(participant.predictedFinishTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                : '---'}
            </span>
          </div>
          <div className="flex items-center justify-between mb-2">
            <div className="flex items-center gap-1.5">
              <MapPin className="h-3 w-3 text-primary" />
              <span className="text-[9px] font-bold text-primary uppercase tracking-wider">Coordinates</span>
            </div>
          </div>
          <div className="flex justify-between font-mono text-[11px] font-bold">
            <div className="flex flex-col">
              <span className="text-[8px] text-muted-foreground uppercase">Latitude</span>
              <span>{participant.latitude.toFixed(6)}</span>
            </div>
            <div className="flex flex-col text-right">
              <span className="text-[8px] text-muted-foreground uppercase">Longitude</span>
              <span>{participant.longitude.toFixed(6)}</span>
            </div>
          </div>
        </div>

        <Button className="w-full bg-primary hover:bg-primary/90 text-primary-foreground font-black uppercase text-[10px] tracking-[0.2em] h-10 rounded-xl shadow-lg shadow-primary/20">
          Focus on Runner
        </Button>
      </div>
    </div>
  );
}
