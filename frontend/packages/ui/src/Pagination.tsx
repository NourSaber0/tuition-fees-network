import React from 'react'

export interface PaginationProps {
  page: number
  totalPages: number
  onPageChange: (page: number) => void
  className?: string
}

export function Pagination({ page, totalPages, onPageChange, className = '' }: PaginationProps) {
  if (totalPages <= 1) return null

  return (
    <div className={`flex items-center justify-between gap-3 ${className}`}>
      <button
        type="button"
        disabled={page <= 1}
        onClick={() => onPageChange(page - 1)}
        className="rounded-md border border-[var(--cib-border)] px-3 py-1.5 text-xs font-medium disabled:opacity-40 hover:bg-[var(--cib-bg)]"
      >
        Previous
      </button>
      <span className="text-xs text-[var(--cib-text-muted)]">
        Page {page} of {totalPages}
      </span>
      <button
        type="button"
        disabled={page >= totalPages}
        onClick={() => onPageChange(page + 1)}
        className="rounded-md border border-[var(--cib-border)] px-3 py-1.5 text-xs font-medium disabled:opacity-40 hover:bg-[var(--cib-bg)]"
      >
        Next
      </button>
    </div>
  )
}
