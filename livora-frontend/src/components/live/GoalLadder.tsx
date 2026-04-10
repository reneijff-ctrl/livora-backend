import React, { useState, useEffect, useRef, useCallback } from "react";
import { safeRender } from "@/utils/safeRender";
import { useWs } from "@/ws/WsContext";
import { normalizeLiveEvent } from "@/components/live/LiveEventsController";
import apiClient from "@/api/apiClient";

interface Milestone {
  title: string;
  targetAmount: number;
}

interface GoalData {
  title: string;
  currentAmount: number;
  targetAmount: number;
  milestones: Milestone[];
}

interface GoalLadderProps {
  creatorId: number | undefined;
  availability: "ONLINE" | "LIVE" | "OFFLINE" | null;
}

function GoalLadder({ creatorId, availability }: GoalLadderProps) {
  const { subscribe, connected } = useWs();
  const [goal, setGoal] = useState<GoalData | null>(null);
  const [goalCompletedFlash, setGoalCompletedFlash] = useState(false);
  const mountedRef = useRef(true);
  const timeoutsRef = useRef<Set<NodeJS.Timeout>>(new Set());

  // Fetch initial goal state from API
  useEffect(() => {
    if (!creatorId) return;

    // Try goal group first, fall back to standalone goal
    apiClient
      .get(`/creator/tip-goal-groups/public/${creatorId}`)
      .then((res) => {
        if (!mountedRef.current) return;
        const data = res.data;
        if (data && data.title) {
          setGoal({
            title: data.title,
            currentAmount: data.currentAmount ?? 0,
            targetAmount: data.targetAmount ?? 0,
            milestones: (data.milestones || []).map((m: any) => ({
              title: m.title,
              targetAmount: m.targetAmount,
            })),
          });
        } else {
          // No active group — try standalone goal
          return apiClient.get(`/creator/tip-goals/public/${creatorId}`);
        }
      })
      .then((res) => {
        if (!res || !mountedRef.current) return;
        const data = res.data;
        if (data && data.title) {
          setGoal({
            title: data.title,
            currentAmount: data.currentAmount ?? 0,
            targetAmount: data.targetAmount ?? 0,
            milestones: [],
          });
        }
      })
      .catch(() => {
        // No active goal — that's fine
      });

    return () => {
      mountedRef.current = false;
    };
  }, [creatorId]);

  // Subscribe to WebSocket goal events for realtime updates
  useEffect(() => {
    if (!creatorId || availability !== "LIVE") return;

    const unsub = subscribe(
      `/exchange/amq.topic/goals.${creatorId}`,
      (msg) => {
        try {
          const incoming = JSON.parse(msg.body);
          const message = normalizeLiveEvent(incoming);
          const eventType = message.type || incoming.type;
          const data = message.payload || message;

          if (
            [
              "GOAL_GROUP_PROGRESS",
              "GOAL_GROUP_COMPLETED",
              "MILESTONE_REACHED",
              "GOAL_PROGRESS",
              "GOAL_STATUS",
              "GOAL_COMPLETED",
              "GOAL_SWITCH",
            ].includes(eventType)
          ) {
            setGoal((prev) => {
              const milestones =
                data.milestones && data.milestones.length > 0
                  ? data.milestones.map((m: any) => ({
                      title: m.title,
                      targetAmount: m.targetAmount,
                    }))
                  : prev?.milestones || [];

              return {
                title: data.title || prev?.title || "",
                currentAmount: data.currentAmount ?? prev?.currentAmount ?? 0,
                targetAmount: data.targetAmount ?? prev?.targetAmount ?? 0,
                milestones,
              };
            });
          }
        } catch (e) {
          console.error("GoalLadder: Error processing goal event", e);
        }
      }
    );

    return () => {
      if (typeof unsub === "function") unsub();
    };
  }, [creatorId, availability, subscribe, connected]);

  const handleClose = useCallback(() => {
    setGoal(null);
  }, []);

  // Completion flash animation (matching GoalOverlay)
  useEffect(() => {
    if (goal && goal.targetAmount > 0 && goal.currentAmount >= goal.targetAmount) {
      setGoalCompletedFlash(true);
      const timeout = setTimeout(() => {
        setGoalCompletedFlash(false);
        timeoutsRef.current.delete(timeout);
      }, 3000);
      timeoutsRef.current.add(timeout);
    }
  }, [goal?.currentAmount, goal?.targetAmount]);

  useEffect(() => {
    return () => {
      timeoutsRef.current.forEach(clearTimeout);
      timeoutsRef.current.clear();
    };
  }, []);

  if (!goal || !goal.title || goal.targetAmount <= 0) return null;

  const percentage = Math.min(
    (goal.currentAmount / goal.targetAmount) * 100,
    100
  );
  const isAlmostComplete = percentage >= 90 && percentage < 100;
  const isCompleted = percentage >= 100;

  // Derive milestone status from currentAmount
  const resolvedMilestones = goal.milestones.map((m) => ({
    ...m,
    completed: goal.currentAmount >= m.targetAmount,
  }));
  const activeIndex = resolvedMilestones.findIndex((m) => !m.completed);

  return (
    <div
      className={`w-full relative bg-black/70 backdrop-blur-md rounded-xl px-4 py-3 border border-white/10 shadow-2xl overflow-hidden transition-all duration-500 ${
        isAlmostComplete ? "animate-goalGlow" : ""
      } ${goalCompletedFlash ? "animate-goalComplete" : ""}`}
    >
      {/* Background fill overlay (matching GoalOverlay) */}
      <div
        className="absolute inset-y-0 left-0 bg-indigo-500/10 transition-all duration-1000 ease-out"
        style={{ width: `${percentage}%` }}
      />
      <div className="relative max-w-3xl mx-auto">
        {/* Header */}
        <div className="flex items-center justify-between mb-2">
          <div className="flex items-center gap-2">
            <span className="text-[10px] font-black text-indigo-400 uppercase tracking-[0.2em]">
              Tip Goal: {safeRender(goal.title)}
            </span>
            {isCompleted && (
              <span className="text-[10px] font-black text-emerald-400 animate-bounce">
                GOAL REACHED! 🎉
              </span>
            )}
          </div>
          <div className="flex items-center gap-3">
            <span className="text-[10px] font-mono text-white/50">
              {percentage.toFixed(0)}%
            </span>
            <button
              onClick={handleClose}
              className="text-white/30 hover:text-white/60 text-xs transition"
              title="Hide goal ladder"
            >
              ✕
            </button>
          </div>
        </div>

        {/* Progress bar */}
        <div className="h-2 w-full bg-white/5 rounded-full overflow-hidden border border-white/5 mb-2">
          <div
            className={`h-full bg-gradient-to-r from-indigo-500 to-purple-500 transition-all duration-1000 ease-out rounded-full shadow-[0_0_10px_rgba(99,102,241,0.5)] ${
              isCompleted ? "animate-pulse" : ""
            }`}
            style={{ width: `${percentage}%` }}
          />
        </div>

        {/* Token count */}
        <div className="goal-text flex items-center justify-between mb-1">
          <p className="text-[11px] font-bold text-white">
            🪙{safeRender(goal.currentAmount.toLocaleString())}{" "}
            <span className="text-white/30 font-medium">
              / {safeRender(goal.targetAmount.toLocaleString())}
            </span>
          </p>
        </div>

        {/* Milestone ladder */}
        {resolvedMilestones.length > 0 && (
          <div className="flex flex-col gap-1 mt-2 border-t border-white/5 pt-2">
            {resolvedMilestones.map((m, i) => {
              const isActive = i === activeIndex;
              return (
                <div
                  key={i}
                  className={`flex items-center justify-between text-[11px] px-2 py-1 rounded-lg transition-all duration-300 ${
                    isActive
                      ? "bg-indigo-500/10 shadow-[0_0_8px_rgba(99,102,241,0.15)]"
                      : m.completed
                      ? "bg-emerald-500/5"
                      : ""
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <span className={isActive ? "animate-pulse" : ""}>
                      {m.completed ? "✓" : isActive ? "🔥" : "○"}
                    </span>
                    <span
                      className={
                        m.completed
                          ? "text-emerald-300/90 line-through decoration-emerald-500/30"
                          : isActive
                          ? "text-indigo-300 font-medium"
                          : "text-white/30"
                      }
                    >
                      {safeRender(m.title)}
                    </span>
                  </div>
                  <span
                    className={`font-mono text-[10px] ${
                      m.completed
                        ? "text-emerald-400/70"
                        : isActive
                        ? "text-indigo-400"
                        : "text-white/25"
                    }`}
                  >
                    — {safeRender(m.targetAmount.toLocaleString())} tokens
                  </span>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

export default React.memo(GoalLadder);
