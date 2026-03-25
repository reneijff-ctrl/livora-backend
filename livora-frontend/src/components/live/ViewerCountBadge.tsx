import React from "react"
import { safeRender } from "@/utils/safeRender"

interface ViewerCountBadgeProps {
  viewerCount: number
}

function ViewerCountBadge({ viewerCount }: ViewerCountBadgeProps) {
  return (
    <span className="viewer-pill">
      <span className="viewer-icon">👁</span>
      <span className="viewer-count">{safeRender(viewerCount)} Watching</span>
    </span>
  )
}

export default React.memo(ViewerCountBadge)
