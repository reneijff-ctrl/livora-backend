export interface TipMenuAction {
  amount: number;
  description: string;
}

export interface TipMenuCategory {
  title: string;
  actions: TipMenuAction[];
}

export interface TipMenuEvent {
  type: 'TIP_MENU';
  actions: TipMenuAction[];
  categories?: TipMenuCategory[];
  uncategorized?: TipMenuAction[];
}

export interface GoalStatusEvent {
  type: 'GOAL_PROGRESS' | 'GOAL_STATUS' | 'GOAL_COMPLETED';
  title: string;
  targetAmount: number;
  currentAmount: number;
  percentage: number;
  isCompleted?: boolean;
  active?: boolean;
  milestones?: { amount: number; label: string; reached: boolean }[];
}

export interface ActionTriggeredEvent {
  type: 'ACTION_TRIGGERED';
  amount: number;
  description: string;
  donor: string;
}
