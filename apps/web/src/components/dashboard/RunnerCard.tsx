import { LineChart, Line, ResponsiveContainer } from 'recharts';
import { ChevronRight, Clock } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import type { LiveTrackingData } from '@/types';
import { useMemo } from 'react';

interface RunnerCardProps {
  participant: LiveTrackingData;
  isSelected: boolean;
  onClick: (participant: LiveTrackingData) => void;
}

export function RunnerCard({ participant, isSelected, onClick }: RunnerCardProps) {
  const sparklineData = useMemo(() => {
    // Generate 10 random data points for the pace trend
    return Array.from({ length: 10 }, () => ({
      value: participant.speed * (0.8 + Math.random() * 0.4),
    }));
  }, [participant.speed]);

  const statusColor = useMemo(() => {
    if (participant.speed > 12) return 'bg-green-500';
    if (participant.speed >= 5) return 'bg-yellow-500';
    return 'bg-red-500';
  }, [participant.speed]);

  return (
    <button
      className={cn(
        'w-full p-3 rounded-lg text-left transition-all hover:bg-muted/50 border border-transparent flex gap-3 relative overflow-hidden group',
        isSelected && 'bg-muted border-border/50 shadow-sm'
      )}
      onClick={() => onClick(participant)}
    >
      {/* Status Bar */}
      <div className={cn('absolute left-0 top-0 bottom-0 w-1 transition-colors', statusColor)} />

      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between mb-1">
          <div className="flex items-center gap-2">
            <div className="flex h-8 w-8 items-center justify-center rounded-sm bg-primary/10 text-[10px] font-mono font-bold text-primary border border-primary/20">
              {participant.bibNumber?.slice(-3)}
            </div>
            <div className="min-w-0">
              <p className="text-sm font-bold tracking-tight truncate">{participant.participantName}</p>
              <div className="flex items-center gap-2 text-[10px] text-muted-foreground font-medium">
                <span className="font-mono">{participant.bibNumber}</span>
                <span>•</span>
                <span className="text-foreground font-bold">{Number(participant.speed || 0).toFixed(1)} km/h</span>
              </div>
            </div>
          </div>
          <ChevronRight className={cn("h-4 w-4 text-muted-foreground shrink-0 transition-transform", isSelected && "translate-x-0.5")} />
        </div>

        <div className="flex items-center justify-between mt-2">
          <div className="flex items-center gap-2 text-[10px]">
            <Badge variant="outline" className="h-5 px-1.5 rounded-sm font-mono bg-background">
              {participant.lastCheckpoint || 'START'}
            </Badge>
            <span className="text-muted-foreground flex items-center gap-1 font-medium">
              <Clock className="h-3 w-3" />
              {participant.elapsedTime || '--:--:--'}
            </span>
          </div>

          <div className="h-6 w-16 opacity-70 group-hover:opacity-100 transition-opacity">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={sparklineData}>
                <Line
                  type="monotone"
                  dataKey="value"
                  stroke={participant.speed > 5 ? 'var(--color-primary)' : 'var(--color-destructive)'}
                  strokeWidth={2}
                  dot={false}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>
    </button>
  );
}
