import { Search, Users, Activity } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { ScrollArea } from '@/components/ui/scroll-area';
import { RunnerCard } from './RunnerCard';
import type { LiveTrackingData } from '@/types';

interface SidebarProps {
  participants: LiveTrackingData[];
  search: string;
  onSearchChange: (value: string) => void;
  selectedParticipant: LiveTrackingData | null;
  onParticipantClick: (participant: LiveTrackingData) => void;
}

export function Sidebar({
  participants,
  search,
  onSearchChange,
  selectedParticipant,
  onParticipantClick,
}: SidebarProps) {
  return (
    <div className="flex flex-col h-full bg-background">
      <div className="p-4 border-b border-border">
        <h2 className="text-lg font-bold tracking-tight mb-3 uppercase italic">Live Tracking</h2>
        <div className="relative">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Search bib or name..."
            className="pl-10 h-9 text-sm rounded-md"
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
          />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-2 p-4 border-b border-border bg-muted/20">
        <div className="p-3 rounded-md bg-background border border-border/50 shadow-sm">
          <div className="flex items-center gap-1.5 text-[10px] font-bold text-muted-foreground uppercase tracking-wider mb-1">
            <Users className="h-3 w-3" />
            On Course
          </div>
          <p className="text-xl font-black">{participants.length}</p>
        </div>
        <div className="p-3 rounded-md bg-background border border-border/50 shadow-sm">
          <div className="flex items-center gap-1.5 text-[10px] font-bold text-muted-foreground uppercase tracking-wider mb-1">
            <Activity className="h-3 w-3 text-green-500" />
            Updates
          </div>
          <p className="text-xl font-black uppercase">Live</p>
        </div>
      </div>

      <ScrollArea className="flex-1">
        <div className="p-2 space-y-1">
          {participants.map((participant) => (
            <RunnerCard
              key={participant.participantId}
              participant={participant}
              isSelected={selectedParticipant?.participantId === participant.participantId}
              onClick={onParticipantClick}
            />
          ))}
          {participants.length === 0 && (
            <div className="p-8 text-center">
              <p className="text-sm text-muted-foreground font-medium">No runners found</p>
            </div>
          )}
        </div>
      </ScrollArea>
    </div>
  );
}
