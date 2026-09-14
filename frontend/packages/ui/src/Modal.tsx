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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4 overflow-y-auto" role="dialog" aria-modal="true">
      <div className="fixed inset-0" onClick={onClose} />
      <div className="relative z-10 w-full max-w-lg rounded-2xl bg-white shadow-2xl border border-gray-100 overflow-hidden my-8">
        {title && (
          <div className="border-b border-gray-100 px-6 py-4 flex items-center justify-between bg-white">
            <h2 className="text-base font-bold text-[#1B2A4A]">{title}</h2>
            <button
              onClick={onClose}
              className="w-8 h-8 rounded-lg flex items-center justify-center text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors"
              aria-label="Close dialog"
            >
              ✕
            </button>
          </div>
        )}
        <div className="px-6 py-5 bg-white">{children}</div>
        {footer && <div className="border-t border-gray-100 bg-gray-50/70 px-6 py-3.5 flex justify-end gap-2">{footer}</div>}
      </div>
    </div>
  )
}
