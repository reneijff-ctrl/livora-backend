import React, { useState, useEffect, useRef } from "react"
import { safeRender } from '@/utils/safeRender'

interface Milestone {
  title: string
  targetAmount: number
  completed: boolean
}

interface GoalLadderOverlayProps {
  title: string
  currentAmount: number
  targetAmount: number
  milestones: Milestone[]
  onClose?: () => void
  onMilestoneReached?: (milestone: Milestone, remainingTokens: number) => void
}

function GoalLadderOverlay({ title, currentAmount, targetAmount, milestones, onClose, onMilestoneReached }: GoalLadderOverlayProps) {
  const [goalCompletedFlash, setGoalCompletedFlash] = useState(false);
  const [showEffect, setShowEffect] = useState(false);
  const lastAmountRef = useRef<number>(currentAmount);
  const completedMilestonesRef = useRef<Set<number>>(new Set());
  const timeoutsRef = useRef<Set<NodeJS.Timeout>>(new Set());

  const percentage = targetAmount > 0 ? Math.min((currentAmount / targetAmount) * 100, 100) : 0;
  const isHot = percentage >= 80;
  const isCompleted = percentage >= 100;
  const tokensToGo = targetAmount - currentAmount;

  // Initialize completed milestones on first render
  useEffect(() => {
    const alreadyCompleted = new Set<number>();
    for (const m of milestones) {
      if (m.completed || currentAmount >= m.targetAmount) {
        alreadyCompleted.add(m.targetAmount);
      }
    }
    completedMilestonesRef.current = alreadyCompleted;
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  function triggerMilestoneEffect() {
    setShowEffect(true);

    // Play sound
    const audio = new Audio('/sounds/milestone.mp3');
    audio.play().catch(() => {});

    const timeout = setTimeout(() => {
      setShowEffect(false);
      timeoutsRef.current.delete(timeout);
    }, 1200);
    timeoutsRef.current.add(timeout);
  }

  // Detect milestone changes
  useEffect(() => {
    if (!milestones || milestones.length === 0) return;
    const prevAmount = lastAmountRef.current;

    if (currentAmount > prevAmount) {
      // Find milestones that were just crossed
      const sortedMilestones = [...milestones].sort((a, b) => a.targetAmount - b.targetAmount);

      for (const m of sortedMilestones) {
        if (
          currentAmount >= m.targetAmount &&
          prevAmount < m.targetAmount &&
          !completedMilestonesRef.current.has(m.targetAmount)
        ) {
          completedMilestonesRef.current.add(m.targetAmount);
          triggerMilestoneEffect();

          // Find next uncompleted milestone for "tokens to go" message
          const nextMilestone = sortedMilestones.find(
            nm => nm.targetAmount > m.targetAmount && currentAmount < nm.targetAmount
          );
          const remaining = nextMilestone
            ? nextMilestone.targetAmount - currentAmount
            : targetAmount - currentAmount;

          onMilestoneReached?.(m, Math.max(0, remaining));
        }
      }
    }

    lastAmountRef.current = currentAmount;
  }, [currentAmount]); // eslint-disable-line react-hooks/exhaustive-deps

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
    <div className={`absolute bottom-4 left-4 right-4 z-30 bg-gradient-to-r from-black/70 via-black/60 to-black/70 backdrop-blur-lg rounded-2xl px-4 py-3 border border-white/10 shadow-[0_0_20px_rgba(168,85,247,0.3)] overflow-hidden transition-all duration-500 ease-in-out ${isHot ? "animate-pulse shadow-[0_0_25px_rgba(236,72,153,0.6)]" : ""} ${goalCompletedFlash ? "animate-goalComplete" : ""}`}>

      {/* Milestone flash overlay */}
      {showEffect && (
        <div className="absolute inset-0 z-40 bg-gradient-to-r from-purple-500/30 via-pink-500/30 to-indigo-500/30 animate-pulse pointer-events-none" />
      )}

      {onClose && (
        <button
          onClick={onClose}
          className="absolute top-2 right-2 text-white/60 hover:text-white text-xs z-50 transition-all duration-200"
        >
          ✕
        </button>
      )}

      <div className="relative flex flex-col gap-1.5">
        {/* Title + Tokens */}
        <div className="flex justify-between items-center mb-1">
          <span className="text-xs text-purple-300 font-semibold tracking-wide">
            🔥 {safeRender(title)?.toString().toUpperCase()}
          </span>
          <span className="text-xs text-white/80 font-medium">
            {safeRender(currentAmount)} / {safeRender(targetAmount)}
          </span>
        </div>

        {/* Progress bar with milestone markers */}
        {(() => {
          const markers = (milestones || []).map((m, i, arr) => ({
            ...m,
            percentage: targetAmount > 0 ? (m.targetAmount / targetAmount) * 100 : 0,
            isCompleted: currentAmount >= m.targetAmount,
            isActive:
              currentAmount < m.targetAmount &&
              (i === 0 || currentAmount >= arr[i - 1]?.targetAmount),
          }));

          return (
            <>
              <div className="relative h-3 w-full mt-10">
                {/* Bar background + fill */}
                <div className="absolute inset-0 bg-white/10 rounded-full overflow-hidden">
                  <div
                    className={`h-full bg-gradient-to-r from-purple-500 via-pink-500 to-indigo-500 transition-all duration-500 ease-out ${showEffect ? 'scale-105' : ''}`}
                    style={{ width: `${percentage}%`, transformOrigin: 'left center' }}
                  />
                  {/* Glow layer */}
                  <div className="absolute inset-0 bg-gradient-to-r from-purple-500/40 via-pink-500/40 to-indigo-500/40 blur-md opacity-70" />
                </div>

                {/* Milestone markers */}
                {(() => {
                  const activeIndex = markers.findIndex(m => m.isActive);
                  return markers.map((m, i) => {
                    // Show labels for completed, active, and next milestone
                    const isVisible = m.isCompleted || m.isActive || i === activeIndex + 1;
                    return (
                      <div
                        key={i}
                        className="absolute top-0 h-full flex items-center"
                        style={{ left: `${m.percentage}%`, transform: 'translateX(-50%)' }}
                      >
                        {/* Title label above marker */}
                        {isVisible && (
                          <div
                            className={`absolute -top-12 text-[10px] max-w-[140px] text-center whitespace-normal break-words leading-tight hover:opacity-100 opacity-70 transition-opacity ${
                              m.percentage < 10 ? 'left-0' : m.percentage > 90 ? 'right-0' : 'left-1/2 -translate-x-1/2'
                            } ${m.isActive ? 'bg-pink-500/20 text-pink-300 border border-pink-400/40 font-semibold' : 'text-white/90'} px-2 py-[2px] rounded-md bg-black/60 backdrop-blur-sm`}
                          >
                            {m.title}
                            {/* Connector line */}
                            <div className="absolute top-full left-1/2 -translate-x-1/2 w-[1px] h-3 bg-white/20" />
                          </div>
                        )}
                        <div
                          className={`w-[2px] h-full ${m.isCompleted ? 'bg-green-400' : m.isActive ? 'bg-pink-400' : 'bg-white/30'}`}
                        />
                        <div
                          className={`absolute -top-1 left-1/2 -translate-x-1/2 w-2 h-2 rounded-full ${m.isCompleted ? 'bg-green-400' : m.isActive ? 'bg-pink-400 animate-pulse' : 'bg-white/40'}`}
                        />
                      </div>
                    );
                  });
                })()}
              </div>

              {/* Milestone labels */}
              {markers.length > 0 && (
                <div className="relative w-full h-3">
                  {markers.map((m, i) => (
                    <span
                      key={i}
                      className="absolute text-[9px] text-white/50"
                      style={{ left: `${m.percentage}%`, transform: 'translateX(-50%)' }}
                    >
                      {safeRender(m.targetAmount)}
                    </span>
                  ))}
                </div>
              )}
            </>
          );
        })()}

        {/* Micro text */}
        <div className="flex justify-between items-center">
          <div className="text-[10px] text-white/50 mt-1">
            {tokensToGo > 0 ? `${tokensToGo} tokens to go` : ''}
          </div>
          {isCompleted && (
            <span className="text-[10px] font-black text-emerald-400 animate-bounce">GOAL REACHED! 🎉</span>
          )}
        </div>
      </div>
    </div>
  );
}

export default React.memo(GoalLadderOverlay);
