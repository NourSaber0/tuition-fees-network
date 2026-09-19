import React from 'react'

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost'
type Size = 'sm' | 'md'

const variantClasses: Record<Variant, string> = {
  primary: 'bg-[#003087] text-white hover:bg-[#002060]',
  secondary: 'bg-white text-[var(--cib-text)] border border-[var(--cib-border)] hover:bg-[var(--cib-bg)]',
  danger: 'bg-red-600 text-white hover:bg-red-700',
  ghost: 'bg-transparent text-[var(--cib-text)] hover:bg-[var(--cib-bg)]',
}

const sizeClasses: Record<Size, string> = {
  sm: 'px-2.5 py-1.5 text-xs',
  md: 'px-4 py-2 text-sm',
}

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  loading?: boolean
}

export function Button({ variant = 'primary', size = 'md', loading = false, disabled, className = '', children, ...rest }: ButtonProps) {
  return (
    <button
      disabled={disabled || loading}
      className={`inline-flex items-center justify-center gap-2 rounded-md font-medium transition-colors disabled:opacity-50 disabled:cursor-not-allowed ${variantClasses[variant]} ${sizeClasses[size]} ${className}`}
      {...rest}
    >
      {loading && <span className="w-3.5 h-3.5 border-2 border-current border-t-transparent rounded-full animate-spin" />}
      {children}
    </button>
  )
}
