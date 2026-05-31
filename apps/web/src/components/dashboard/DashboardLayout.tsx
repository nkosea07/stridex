import React from 'react';
import { cn } from '@/lib/utils';

interface DashboardLayoutProps {
  dataStrip: React.ReactNode;
  children: React.ReactNode;
  analyticsTray?: React.ReactNode;
  className?: string;
}

export function DashboardLayout({
  dataStrip,
  children,
  analyticsTray,
  className,
}: DashboardLayoutProps) {
  return (
    <div className={cn('grid h-screen w-full grid-cols-1 overflow-hidden bg-background md:grid-cols-[20rem_1fr_18rem]', className)}>
      {/* Left Sidebar: Data Strip */}
      <aside className="hidden flex-col border-r border-border bg-card md:flex">
        {dataStrip}
      </aside>

      {/* Main Content: Map / Mission Control */}
      <main className="relative flex h-full w-full flex-col overflow-hidden">
        {children}
      </main>

      {/* Right Sidebar: Analytics Tray */}
      <aside className="hidden flex-col border-l border-border bg-card lg:flex">
        {analyticsTray || (
          <div className="flex h-full items-center justify-center p-8 text-center text-muted-foreground">
            <p>Select a participant to view detailed analytics</p>
          </div>
        )}
      </aside>
    </div>
  );
}
