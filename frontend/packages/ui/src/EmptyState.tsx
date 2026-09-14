import React from 'react'

export interface EmptyStateProps {
  icon?: React.ReactNode
  title: string
  description?: string
  action?: React.ReactNode
  className?: string
}

export function EmptyState({ icon, title, description, action, className = '' }: EmptyStateProps) {
  return (
    <div className={`flex flex-col items-center justify-center text-center py-12 px-4 ${className}`}>
      {icon && <div className="w-10 h-10 mb-3 text-[var(--cib-text-muted)]">{icon}</div>}
      <p className="text-sm font-medium text-[var(--cib-text)]">{title}</p>
      {description && <p className="mt-1 text-xs text-[var(--cib-text-muted)] max-w-sm">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}
