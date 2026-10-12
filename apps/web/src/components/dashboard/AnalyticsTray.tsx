import { TrendingUp, Clock, CheckCircle2, Activity, Timer } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import type { LiveTrackingData, Checkpoint } from '@/types';
import type { TrackingMode } from './CommandBar';

interface AnalyticsTrayProps {
  selectedParticipant: LiveTrackingData | null;
  mode: TrackingMode;
  checkpoints: Checkpoint[];
}

export function AnalyticsTray({ selectedParticipant, checkpoints }: AnalyticsTrayProps) {
  if (!selectedParticipant) {
    return (
      <div className="flex flex-col items-center justify-center h-full p-8 text-center space-y-4 relative overflow-hidden bg-background/40 backdrop-blur-md">
        <div 
          className="absolute inset-0 pointer-events-none opacity-[0.03] mix-blend-overlay"
          style={{
            backgroundImage: `url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.65' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E")`
          }}
        />
        <div className="w-12 h-12 rounded-full bg-muted flex items-center justify-center relative z-10">
          <Activity className="h-6 w-6 text-muted-foreground" />
        </div>
        <div className="space-y-1 relative z-10">
          <p className="font-bold uppercase text-xs tracking-widest text-muted-foreground">No Selection</p>
          <p className="text-sm text-muted-foreground/60">Select a participant to view detailed telemetry</p>
        </div>
      </div>
    );
  }

  // Calculate checkpoint completion based on lastCheckpoint name
  const lastCpIndex = checkpoints.findIndex(cp => cp.name === selectedParticipant.lastCheckpoint);
  const processedCheckpoints = checkpoints.map((cp, index) => ({
    name: cp.name,
    completed: index <= lastCpIndex || (cp.checkpointType === 'START' && selectedParticipant.status !== 'REGISTERED')
  }));

  return (
    <div className="flex flex-col h-full relative overflow-hidden bg-background/40 backdrop-blur-md border-l border-white/5">
      {/* Grain Overlay */}
      <div 
        className="absolute inset-0 pointer-events-none opacity-[0.03] mix-blend-overlay"
        style={{
          backgroundImage: `url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.65' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E")`
        }}
      />
      
      <div className="flex flex-col h-full p-4 space-y-6 relative z-10">
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <h3 className="text-[10px] font-black text-muted-foreground uppercase tracking-[0.2em]">Telemetry</h3>
            <Badge variant="outline" className="font-mono text-[10px] h-5 rounded-sm bg-primary/5 text-primary border-primary/20 uppercase">
              {selectedParticipant.status}
            </Badge>
          </div>
          <p className="text-2xl font-black italic uppercase tracking-tighter leading-none">
            {selectedParticipant.participantName}
          </p>
          <div className="flex items-center gap-2">
            <Badge variant="secondary" className="font-mono rounded-sm px-1.5 py-0 text-[10px] h-5">
              BIB {selectedParticipant.bibNumber}
            </Badge>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <Card className="bg-muted/20 border-white/5 shadow-none rounded-md backdrop-blur-sm">
            <CardContent className="p-3">
              <div className="flex items-center gap-2 mb-1.5">
                <TrendingUp className="h-3.5 w-3.5 text-primary" />
                <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-wider">Avg Pace</span>
              </div>
              <p className="text-xl font-black">
                {selectedParticipant.averagePace || Number(selectedParticipant.speed || 0).toFixed(1)} 
                <span className="text-xs font-bold text-muted-foreground ml-1">
                  {selectedParticipant.averagePace ? 'min/km' : 'km/h'}
                </span>
              </p>
            </CardContent>
          </Card>
          <Card className="bg-muted/20 border-white/5 shadow-none rounded-md backdrop-blur-sm">
            <CardContent className="p-3">
              <div className="flex items-center gap-2 mb-1.5">
                <Clock className="h-3.5 w-3.5 text-primary" />
                <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-wider">Elapsed</span>
              </div>
              <p className="text-xl font-black tracking-tight">{selectedParticipant.elapsedTime || '00:00:00'}</p>
            </CardContent>
          </Card>
        </div>

        {/* Predictive Intelligence Grid */}
        <div className="grid grid-cols-1 gap-3">
          <Card className="bg-primary/5 border-primary/10 shadow-none rounded-md border-dashed">
            <CardContent className="p-3 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Timer className="h-4 w-4 text-primary" />
                <div>
                  <p className="text-[9px] font-bold text-muted-foreground uppercase tracking-wider">Est. Finish</p>
                  <p className="text-sm font-black font-mono">
                    {selectedParticipant.predictedFinishTime 
                      ? new Date(selectedParticipant.predictedFinishTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
                      : '---'}
                  </p>
                </div>
              </div>
              <div className="text-right">
                <p className="text-[9px] font-bold text-muted-foreground uppercase tracking-wider">Remaining</p>
                <p className="text-sm font-black text-primary">
                  {selectedParticipant.distanceRemaining ? `${selectedParticipant.distanceRemaining.toFixed(1)} km` : '---'}
                </p>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Race Progress Funnel */}
        <div className="space-y-4">
          <h4 className="text-[10px] font-black text-muted-foreground uppercase tracking-[0.2em] px-1">Race Progress Funnel</h4>
          <div className="relative px-2 py-4">
            {/* Connecting Line */}
            <div className="absolute top-[26px] left-4 right-4 h-0.5 bg-muted/30" />
            <div 
              className="absolute top-[26px] left-4 h-0.5 bg-primary transition-all duration-500 shadow-[0_0_8px_rgba(37,99,235,0.5)]" 
              style={{ width: `${processedCheckpoints.length > 1 ? (processedCheckpoints.filter(c => c.completed).length - 1) / (processedCheckpoints.length - 1) * 100 : 0}%` }}
            />
            
            <div className="relative flex justify-between items-center">
              {processedCheckpoints.map((cp, i) => (
                <div key={i} className="flex flex-col items-center gap-2">
                  <div className={cn(
                    "w-5 h-5 rounded-full border-2 z-10 bg-background transition-colors flex items-center justify-center shadow-lg",
                    cp.completed ? "border-primary bg-primary" : "border-muted bg-muted/50"
                  )}>
                    {cp.completed && <CheckCircle2 className="h-3 w-3 text-primary-foreground" />}
                  </div>
                  <span className={cn(
                    "text-[8px] font-bold uppercase tracking-tighter absolute -bottom-1",
                    cp.completed ? "text-primary" : "text-muted-foreground"
                  )}>
                    {cp.name}
                  </span>
                </div>
              ))}
              {processedCheckpoints.length === 0 && (
                <div className="flex-1 text-center py-2">
                  <span className="text-[10px] text-muted-foreground font-medium uppercase tracking-widest">No Checkpoints</span>
                </div>
              )}
            </div>
          </div>
        </div>

        <div className="space-y-2 mt-4">
          <h4 className="text-[10px] font-black text-muted-foreground uppercase tracking-[0.2em] px-1">Latest Checkpoint</h4>
          <div className="space-y-1.5">
            {selectedParticipant.lastCheckpoint ? (
              <div className="flex items-center justify-between p-2.5 rounded-md bg-muted/20 border border-white/5 text-xs backdrop-blur-sm">
                <div className="flex items-center gap-2">
                  <div className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
                  <span className="font-bold uppercase tracking-tighter">{selectedParticipant.lastCheckpoint}</span>
                </div>
                <span className="font-mono font-bold text-muted-foreground">{selectedParticipant.elapsedTime || '--'}</span>
              </div>
            ) : (
              <p className="text-[10px] text-muted-foreground italic px-1">No checkpoints recorded yet</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
