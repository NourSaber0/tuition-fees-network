'use client'

import React from 'react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { LogoutIcon, BellIcon } from './Icons'

export interface PortalNavItem {
  id: string
  label: string
  href: string
  Icon: React.ComponentType<React.SVGProps<SVGSVGElement>>
  badge?: number
}

export interface PortalUser {
  name: string
  initials: string
  roleLabel: string
}

export interface PortalShellProps {
  brandLabel: string
  navItems: PortalNavItem[]
  user: PortalUser
  onLogout: () => void
  children: React.ReactNode
  /** Where the header bell links to - typically the notifications nav item's href. */
  notificationsHref?: string
  unreadNotifications?: number
}

/**
 * The CIB sidebar+header shell, ported from the Figma export's Portal.tsx
 * and shared by both portals - only the nav items, brand label, and user
 * differ. Navigation allow-listing happens before this component ever sees
 * navItems (callers filter by user.permissions), so this component itself
 * has no notion of roles.
 */
export function PortalShell({
  brandLabel,
  navItems,
  user,
  onLogout,
  children,
  notificationsHref,
  unreadNotifications = 0,
}: PortalShellProps) {
  const pathname = usePathname()
  const active = navItems.find((item) => pathname === item.href || pathname.startsWith(`${item.href}/`))
  const title = active?.label ?? brandLabel

  return (
    <div className="flex h-full overflow-hidden" style={{ background: 'var(--cib-bg)' }}>
      <aside className="w-60 flex flex-col shrink-0 h-full" style={{ background: 'var(--cib-blue)' }}>
        <div className="h-16 flex items-center px-5 shrink-0" style={{ borderBottom: '1px solid rgba(255,255,255,0.08)' }}>
          <div className="flex items-center gap-2 select-none">
            <span className="text-white font-bold text-base">CIB</span>
            <span
              className="text-[9px] uppercase tracking-widest font-semibold leading-tight"
              style={{ color: 'rgba(255,255,255,0.55)' }}
            >
              {brandLabel}
            </span>
          </div>
        </div>

        <nav className="flex-1 py-4 overflow-y-auto">
          <p
            className="text-[10px] uppercase tracking-widest font-semibold px-5 mb-2"
            style={{ color: 'rgba(255,255,255,0.3)' }}
          >
            Navigation
          </p>
          {navItems.map((item) => {
            const isActive = active?.id === item.id
            return (
              <Link
                key={item.id}
                href={item.href}
                className="w-full flex items-center gap-3 px-5 py-2.5 text-[13px] transition-all relative"
                style={{
                  background: isActive ? 'rgba(255,255,255,0.12)' : 'transparent',
                  color: isActive ? '#FFFFFF' : 'rgba(255,255,255,0.58)',
                  fontWeight: isActive ? 600 : 400,
                }}
              >
                {isActive && (
                  <span
                    className="absolute left-0 top-1 bottom-1 w-[3px] rounded-r-full"
                    style={{ background: 'var(--cib-orange)' }}
                  />
                )}
                <item.Icon className="w-4 h-4 shrink-0" />
                <span className="flex-1 text-left">{item.label}</span>
                {item.badge != null && item.badge > 0 && (
                  <span
                    className="text-[10px] font-bold rounded-full min-w-[18px] h-[18px] flex items-center justify-center px-1"
                    style={{ background: 'var(--cib-orange)', color: '#FFFFFF' }}
                  >
                    {item.badge}
                  </span>
                )}
              </Link>
            )
          })}
        </nav>

        <div className="p-4 shrink-0" style={{ borderTop: '1px solid rgba(255,255,255,0.08)' }}>
          <div className="flex items-center gap-3">
            <div
              className="w-8 h-8 rounded-full flex items-center justify-center text-white font-bold text-[11px] shrink-0"
              style={{ background: 'var(--cib-orange)' }}
            >
              {user.initials}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-white text-xs font-semibold truncate">{user.name}</div>
              <div className="text-[10px]" style={{ color: 'rgba(255,255,255,0.4)' }}>
                {user.roleLabel}
              </div>
            </div>
            <button
              onClick={onLogout}
              title="Sign out"
              className="p-1 rounded transition-colors"
              style={{ color: 'rgba(255,255,255,0.35)' }}
            >
              <LogoutIcon className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </aside>

      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        <header
          className="h-16 flex items-center px-6 justify-between shrink-0"
          style={{ background: 'var(--cib-card)', borderBottom: '1px solid var(--cib-border)' }}
        >
          <div className="flex items-center gap-3">
            <div className="w-5 h-5 rounded" style={{ background: 'var(--cib-orange)', opacity: 0.85 }} />
            <span className="font-semibold text-[15px]" style={{ color: 'var(--cib-blue)' }}>
              {title}
            </span>
          </div>
          <div className="flex items-center gap-4">
            <div className="text-right hidden sm:block">
              <div className="text-xs" style={{ color: 'var(--cib-text-muted)' }}>
                {new Date().toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
              </div>
              <div className="text-[10px] text-right" style={{ color: '#aab5c4' }}>
                Cairo, Egypt &middot; EGP
              </div>
            </div>
            <Link
              href={notificationsHref ?? '#'}
              className="relative p-2 rounded-lg transition-colors"
              style={{ color: 'var(--cib-text-muted)' }}
            >
              <BellIcon className="w-5 h-5" />
              {unreadNotifications > 0 && (
                <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full" style={{ background: 'var(--cib-orange)' }} />
              )}
            </Link>
            <div className="w-px h-8" style={{ background: 'var(--cib-border)' }} />
            <div className="flex items-center gap-2">
              <div
                className="w-8 h-8 rounded-full flex items-center justify-center text-white font-bold text-[11px]"
                style={{ background: 'var(--cib-blue)' }}
              >
                {user.initials}
              </div>
              <div className="hidden sm:block">
                <div className="text-xs font-semibold" style={{ color: 'var(--cib-text)' }}>
                  {user.name}
                </div>
                <div className="text-[10px]" style={{ color: 'var(--cib-text-muted)' }}>
                  {user.roleLabel}
                </div>
              </div>
            </div>
          </div>
        </header>

        <main className="flex-1 overflow-auto">
          <div className="p-6 min-h-full">{children}</div>
        </main>
      </div>
    </div>
  )
}
