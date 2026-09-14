import React from 'react'
import { LoadingSpinner } from './LoadingSpinner'
import { EmptyState } from './EmptyState'

export interface Column<T> {
  key: string
  header: string
  render?: (row: T) => React.ReactNode
  className?: string
}

export interface TableProps<T> {
  columns: Column<T>[]
  rows: T[]
  rowKey: (row: T) => string
  loading?: boolean
  emptyTitle?: string
  emptyDescription?: string
  onRowClick?: (row: T) => void
}

export function Table<T>({
  columns,
  rows,
  rowKey,
  loading = false,
  emptyTitle = 'No results',
  emptyDescription,
  onRowClick,
}: TableProps<T>) {
  if (loading) {
    return (
      <div className="flex justify-center py-12">
        <LoadingSpinner />
      </div>
    )
  }

  if (rows.length === 0) {
    return <EmptyState title={emptyTitle} description={emptyDescription} />
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead>
          <tr className="border-b border-[var(--cib-border)]">
            {columns.map((col) => (
              <th key={col.key} className={`px-3 py-2 text-xs font-semibold text-[var(--cib-text-muted)] ${col.className ?? ''}`}>
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={rowKey(row)}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              className={`border-b border-[var(--cib-border)] ${onRowClick ? 'cursor-pointer hover:bg-[var(--cib-bg)]' : ''}`}
            >
              {columns.map((col) => (
                <td key={col.key} className={`px-3 py-2.5 text-[var(--cib-text)] ${col.className ?? ''}`}>
                  {col.render ? col.render(row) : String((row as Record<string, unknown>)[col.key] ?? '')}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
