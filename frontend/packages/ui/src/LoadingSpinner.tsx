import React from 'react'

export interface LoadingSpinnerProps {
  size?: number
  className?: string
  label?: string
}

export function LoadingSpinner({ size = 24, className = '', label = 'Loading' }: LoadingSpinnerProps) {
  return (
    <div role="status" aria-label={label} className={`inline-flex items-center justify-center ${className}`}>
      <span
        style={{ width: size, height: size }}
        className="border-2 border-[var(--cib-border)] border-t-[var(--cib-blue)] rounded-full animate-spin"
      />
    </div>
  )
}
