import React from 'react'

export interface ModalProps {
  open: boolean
  onClose: () => void
  title?: string
  children: React.ReactNode
  footer?: React.ReactNode
}

export function Modal({ open, onClose, title, children, footer }: ModalProps) {
  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" role="dialog" aria-modal="true">
      <div className="absolute inset-0" onClick={onClose} />
      <div className="relative z-10 w-full max-w-lg rounded-lg bg-[var(--cib-card)] shadow-xl">
        {title && (
          <div className="border-b border-[var(--cib-border)] px-5 py-3">
            <h2 className="text-sm font-semibold text-[var(--cib-text)]">{title}</h2>
          </div>
        )}
        <div className="px-5 py-4">{children}</div>
        {footer && <div className="border-t border-[var(--cib-border)] px-5 py-3 flex justify-end gap-2">{footer}</div>}
      </div>
    </div>
  )
}
