import React from 'react'

export type BadgeTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral'

const toneClasses: Record<BadgeTone, string> = {
  success: 'bg-green-100 text-green-800',
  warning: 'bg-[var(--cib-orange-light)] text-[var(--cib-orange-dark)]',
  danger: 'bg-red-100 text-red-700',
  info: 'bg-[var(--cib-blue-light)] text-[var(--cib-blue)]',
  neutral: 'bg-gray-100 text-gray-700',
}

export interface BadgeProps {
  tone?: BadgeTone
  children: React.ReactNode
  className?: string
}

export function Badge({ tone = 'neutral', children, className = '' }: BadgeProps) {
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${toneClasses[tone]} ${className}`}>
      {children}
    </span>
  )
}
