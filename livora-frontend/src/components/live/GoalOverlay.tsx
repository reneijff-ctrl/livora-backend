import React, { useState, useEffect, useRef } from "react"
import { safeRender } from '@/utils/safeRender'

interface MilestoneStatus {
  title: string
  targetAmount: number
  reached: boolean
}

interface GoalOverlayProps {
  goal: number
  progress: number
  title?: string
  onClose?: () => void
  milestones?: MilestoneStatus[]
}

function GoalOverlay({ goal, progress, title = "Tip Goal", onClose, milestones }: GoalOverlayProps) {
  const [goalCompletedFlash, setGoalCompletedFlash] = useState(false);
  const timeoutsRef = useRef<Set<NodeJS.Timeout>>(new Set());

  const percentage = goal > 0 ? Math.min((progress / goal) * 100, 100) : 0;
  const isAlmostComplete = percentage >= 90 && percentage < 100;
  const isCompleted = percentage >= 100;
  const hasMilestones = milestones && milestones.length > 0;

  useEffect(() => {
    if (isCompleted) {
      setGoalCompletedFlash(true);
      const timeout = setTimeout(() => {
        setGoalCompletedFlash(false);
        timeoutsRef.current.delete(timeout);
      }, 3000);
      timeoutsRef.current.add(timeout);
    }
  }, [isCompleted]);

  useEffect(() => {
    return () => {
      timeoutsRef.current.forEach(clearTimeout);
      timeoutsRef.current.clear();
    };
  }, []);

  return (
    <div className={`goal-overlay relative bg-black/70 backdrop-blur-md rounded-xl px-4 py-3 border border-white/10 shadow-2xl overflow-hidden group/goal transition-all duration-500 ease-in-out ${isAlmostComplete ? "animate-goalGlow" : ""} ${goalCompletedFlash ? "animate-goalComplete" : ""}`}>
      {onClose && (
        <button
          onClick={onClose}
          className="absolute top-2 right-2 text-white/60 hover:text-white text-xs z-50 transition-all duration-200"
        >
          ✕
        </button>
      )}

      {hasMilestones ? (
        /* Milestone ladder view */
        <div className="relative flex flex-col gap-2">
          <div className="flex justify-between items-end">
            <span className="text-[10px] font-black text-indigo-400 uppercase tracking-[0.2em]">🎯 {safeRender(title)}</span>
            <span className="text-[10px] font-mono text-white/50">{percentage.toFixed(0)}%</span>
          </div>

          <div className="flex flex-col gap-1 mt-1">
            {milestones.map((m, i) => {
              const isNext = !m.reached && (i === 0 || milestones[i - 1].reached);
              return (
                <div key={i} className={`flex items-center justify-between text-[11px] px-2 py-1 rounded-lg transition-all duration-300 ${isNext ? 'bg-indigo-500/10 shadow-[0_0_8px_rgba(99,102,241,0.15)]' : m.reached ? 'bg-emerald-500/5' : ''}`}>
                  <div className="flex items-center gap-2">
                    <span className={isNext ? 'animate-pulse' : ''}>{m.reached ? '✓' : (isNext ? '🔥' : '○')}</span>
                    <span className={m.reached ? 'text-emerald-300/90 line-through decoration-emerald-500/30' : (isNext ? 'text-indigo-300 font-medium' : 'text-white/30')}>{safeRender(m.title)}</span>
                  </div>
                  <span className={`font-mono text-[10px] ${m.reached ? 'text-emerald-400/70' : (isNext ? 'text-indigo-400' : 'text-white/25')}`}>— {safeRender(m.targetAmount.toLocaleString())} tokens</span>
                </div>
              );
            })}
          </div>

          <div className="goal-bar h-2 w-full bg-white/5 rounded-full overflow-hidden border border-white/5 mt-1">
            <div
              className={`goal-progress h-full bg-gradient-to-r from-indigo-500 to-purple-500 transition-all duration-1000 ease-out rounded-full shadow-[0_0_10px_rgba(99,102,241,0.5)] ${isCompleted ? 'animate-pulse' : ''}`}
              style={{ width: `${percentage}%` }}
            />
          </div>

          <div className="goal-text flex justify-between items-center">
            <p className="text-[11px] font-bold text-white">
              🪙{safeRender(progress.toLocaleString())} <span className="text-white/30 font-medium">/ {safeRender(goal.toLocaleString())}</span>
            </p>
            {isCompleted && (
              <span className="text-[10px] font-black text-emerald-400 animate-bounce">GOAL REACHED! 🎉</span>
            )}
          </div>
        </div>
      ) : (
        /* Single goal view (fallback) */
        <>
          <div 
            className="absolute inset-y-0 left-0 bg-indigo-500/10 transition-all duration-1000 ease-out" 
            style={{ width: `${percentage}%` }}
          />
          
          <div className="relative flex flex-col gap-2">
            <div className="flex justify-between items-end">
              <span className="text-[10px] font-black text-indigo-400 uppercase tracking-[0.2em]">Tip Goal: {safeRender(title)}</span>
              <span className="text-[10px] font-mono text-white/50">{percentage.toFixed(0)}%</span>
            </div>

            <div className="goal-bar h-2 w-full bg-white/5 rounded-full overflow-hidden border border-white/5">
              <div
                className={`goal-progress h-full bg-gradient-to-r from-indigo-500 to-purple-500 transition-all duration-1000 ease-out rounded-full shadow-[0_0_10px_rgba(99,102,241,0.5)] ${isCompleted ? 'animate-pulse' : ''}`}
                style={{ width: `${percentage}%` }}
              />
            </div>

            <div className="goal-text flex justify-between items-center mt-1">
              <p className="text-[11px] font-bold text-white">
                🪙{safeRender(progress.toLocaleString())} <span className="text-white/30 font-medium">/ {safeRender(goal.toLocaleString())}</span>
              </p>
              {isCompleted && (
                <span className="text-[10px] font-black text-emerald-400 animate-bounce">GOAL REACHED! 🎉</span>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  )
}

export default React.memo(GoalOverlay)
