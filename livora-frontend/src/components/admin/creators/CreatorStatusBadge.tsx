import React from 'react';

type Status = 'DRAFT' | 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'BANNED' | string;

const STATUS_CONFIG: Record<string, { label: string; classes: string; dot: string }> = {
  DRAFT:     { label: 'Draft',     classes: 'bg-zinc-800 text-zinc-400 ring-zinc-700',     dot: 'bg-zinc-500' },
  PENDING:   { label: 'Pending',   classes: 'bg-amber-950/60 text-amber-400 ring-amber-800', dot: 'bg-amber-400' },
  ACTIVE:    { label: 'Active',    classes: 'bg-emerald-950/60 text-emerald-400 ring-emerald-800', dot: 'bg-emerald-400' },
  SUSPENDED: { label: 'Suspended', classes: 'bg-red-950/60 text-red-400 ring-red-800',     dot: 'bg-red-400' },
  BANNED:    { label: 'Banned',    classes: 'bg-rose-950/60 text-rose-300 ring-rose-800',  dot: 'bg-rose-400' },
};

interface Props { status: Status; }

const CreatorStatusBadge: React.FC<Props> = ({ status }) => {
  const cfg = STATUS_CONFIG[status] ?? { label: status, classes: 'bg-zinc-800 text-zinc-400 ring-zinc-700', dot: 'bg-zinc-500' };
  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold ring-1 ring-inset ${cfg.classes}`}>
      <span className={`w-1.5 h-1.5 rounded-full ${cfg.dot}`} />
      {cfg.label}
    </span>
  );
};

export default CreatorStatusBadge;
