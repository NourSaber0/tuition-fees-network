import { useState } from 'react'
import {
  DashboardIcon, SchoolIcon, TransactionIcon, ReconcileIcon,
  EPPIcon, ReportIcon, BellIcon, AuditIcon, UsersIcon, SettingsIcon,
  LogoutIcon, ShieldIcon,
} from './components/Icons'
import Dashboard from './screens/Dashboard'
import Schools from './screens/Schools'
import Transactions from './screens/Transactions'
import Reconciliation from './screens/Reconciliation'
import EPP from './screens/EPP'
import Reports from './screens/Reports'
import Notifications from './screens/Notifications'
import AuditLogs from './screens/AuditLogs'
import Users from './screens/Users'
import Settings from './screens/Settings'

export type Screen =
  | 'dashboard' | 'schools' | 'transactions' | 'reconciliation'
  | 'epp' | 'reports' | 'notifications' | 'audit-logs' | 'users' | 'settings'

export type UserRole =
  | 'bank-admin' | 'bank-operations' | 'bank-finance' | 'bank-reconciliation'

/* ── Role-based permissions ── */
const rolePermissions: Record<UserRole, Screen[]> = {
  'bank-admin': [
    'dashboard', 'schools', 'transactions', 'reconciliation',
    'epp', 'reports', 'notifications', 'audit-logs', 'users', 'settings',
  ],
  'bank-operations': [
    'dashboard', 'schools', 'transactions', 'reconciliation',
    'epp', 'notifications',
  ],
  'bank-finance': [
    'dashboard', 'transactions', 'epp', 'reconciliation',
    'reports', 'notifications',
  ],
  'bank-reconciliation': [
    'dashboard', 'reconciliation', 'notifications',
  ],
}

const roleLabels: Record<UserRole, string> = {
  'bank-admin':          'Bank Admin',
  'bank-operations':     'Operations',
  'bank-finance':        'Finance',
  'bank-reconciliation': 'Reconciliation',
}

const roleProfiles: Record<UserRole, { name: string; initials: string }> = {
  'bank-admin':          { name: 'Mohamed Ali',   initials: 'MA' },
  'bank-operations':     { name: 'Sherif Hassan', initials: 'SH' },
  'bank-finance':        { name: 'Amira Farouk',  initials: 'AF' },
  'bank-reconciliation': { name: 'Tarek Ibrahim',  initials: 'TI' },
}

/* ── Nav items ── */
const navItems: {
  id: Screen
  label: string
  Icon: React.ComponentType<React.SVGProps<SVGSVGElement>>
  badge?: number
}[] = [
  { id: 'dashboard',      label: 'Dashboard',              Icon: DashboardIcon   },
  { id: 'schools',        label: 'Institution Management', Icon: SchoolIcon      },
  { id: 'transactions',   label: 'Transactions',           Icon: TransactionIcon },
  { id: 'reconciliation', label: 'Reconciliation',         Icon: ReconcileIcon, badge: 3 },
  { id: 'epp',            label: 'EPP Plans',              Icon: EPPIcon         },
  { id: 'reports',        label: 'Reports',                Icon: ReportIcon      },
  { id: 'notifications',  label: 'Notifications',          Icon: BellIcon,      badge: 5 },
  { id: 'audit-logs',     label: 'Audit Logs',             Icon: AuditIcon       },
  { id: 'users',          label: 'Users & Roles',          Icon: UsersIcon       },
  { id: 'settings',       label: 'System Settings',        Icon: SettingsIcon    },
]

const screenTitles: Record<Screen, string> = {
  dashboard:      'Dashboard',
  schools:        'Institution Management',
  transactions:   'Transaction Monitoring',
  reconciliation: 'Reconciliation',
  epp:            'EPP Plans',
  reports:        'Reports',
  notifications:  'Notifications',
  'audit-logs':   'Audit Logs',
  users:          'Users & Roles',
  settings:       'System Settings',
}

/* ── CIB Logo ── */
function CIBLogoFull() {
  return (
    <div className="flex items-center gap-3 select-none">
      <img src="/src/imports/cib.png" alt="CIB" className="h-9 w-auto object-contain" style={{ position: 'static' }} />
      <div style={{ color: 'rgba(255,255,255,0.55)' }} className="text-[9px] uppercase tracking-widest font-semibold leading-tight">
        Back Office
      </div>
    </div>
  )
}

/* ── Access-denied state ── */
function AccessDenied({ role, screenName }: { role: UserRole; screenName: string }) {
  return (
    <div className="flex items-center justify-center" style={{ minHeight: 400 }}>
      <div className="text-center max-w-sm px-4">
        <div className="w-16 h-16 rounded-2xl flex items-center justify-center mx-auto mb-5" style={{ background: '#FEF2F2' }}>
          <ShieldIcon className="w-8 h-8" style={{ color: '#EF4444' }} />
        </div>
        <h2 className="text-lg font-bold mb-2" style={{ color: '#1B2A4A' }}>Access Restricted</h2>
        <p className="text-sm mb-4" style={{ color: '#6B7A8D' }}>
          Your role (<strong>{roleLabels[role]}</strong>) does not have permission to access{' '}
          <strong>{screenName}</strong>. Please contact your Bank Administrator to request access.
        </p>
        <div className="rounded-lg px-4 py-2.5 text-xs font-medium" style={{ background: '#FEF2F2', color: '#B91C1C', border: '1px solid #FCA5A5' }}>
          Unauthorized access attempts are logged and monitored.
        </div>
      </div>
    </div>
  )
}

/* ── Portal ── */
export default function Portal({ onLogout, userRole }: { onLogout: () => void; userRole: UserRole }) {
  const [screen, setScreen] = useState<Screen>('dashboard')

  const allowed = rolePermissions[userRole]
  const profile = roleProfiles[userRole]
  const visibleNav = navItems.filter(n => allowed.includes(n.id))
  const canView = allowed.includes(screen)

  /* safe navigation — falls back to dashboard for restricted targets */
  const navigate = (s: Screen) => setScreen(allowed.includes(s) ? s : 'dashboard')

  return (
    <div className="flex h-full overflow-hidden" style={{ background: '#F4F6F9' }}>

      {/* ── Sidebar ── */}
      <aside className="w-60 flex flex-col shrink-0 h-full" style={{ background: '#003087' }}>
        {/* Logo */}
        <div className="h-16 flex items-center px-5 shrink-0" style={{ borderBottom: '1px solid rgba(255,255,255,0.08)' }}>
          <CIBLogoFull />
        </div>

        {/* Nav */}
        <nav className="flex-1 py-4 overflow-y-auto">
          <p className="text-[10px] uppercase tracking-widest font-semibold px-5 mb-2" style={{ color: 'rgba(255,255,255,0.3)' }}>
            Navigation
          </p>
          {visibleNav.map(({ id, label, Icon, badge }) => {
            const active = screen === id
            return (
              <button key={id} onClick={() => setScreen(id)}
                className="w-full flex items-center gap-3 px-5 py-2.5 text-[13px] transition-all relative"
                style={{
                  background: active ? 'rgba(255,255,255,0.12)' : 'transparent',
                  color: active ? '#FFFFFF' : 'rgba(255,255,255,0.58)',
                  fontWeight: active ? 600 : 400,
                }}
                onMouseEnter={e => { if (!active) (e.currentTarget as HTMLButtonElement).style.background = 'rgba(255,255,255,0.07)' }}
                onMouseLeave={e => { if (!active) (e.currentTarget as HTMLButtonElement).style.background = 'transparent' }}
              >
                {active && <span className="absolute left-0 top-1 bottom-1 w-[3px] rounded-r-full" style={{ background: '#F7941D' }} />}
                <Icon className="w-4 h-4 shrink-0" />
                <span className="flex-1 text-left">{label}</span>
                {badge != null && badge > 0 && (
                  <span className="text-[10px] font-bold rounded-full min-w-[18px] h-[18px] flex items-center justify-center px-1"
                    style={{ background: '#F7941D', color: '#FFFFFF' }}>
                    {badge}
                  </span>
                )}
              </button>
            )
          })}
        </nav>

        {/* User strip */}
        <div className="p-4 shrink-0" style={{ borderTop: '1px solid rgba(255,255,255,0.08)' }}>
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-full flex items-center justify-center text-white font-bold text-[11px] shrink-0" style={{ background: '#F7941D' }}>
              {profile.initials}
            </div>
            <div className="flex-1 min-w-0">
              <div className="text-white text-xs font-semibold truncate">{profile.name}</div>
              <div className="text-[10px]" style={{ color: 'rgba(255,255,255,0.4)' }}>{roleLabels[userRole]}</div>
            </div>
            <button onClick={onLogout} title="Sign out"
              className="p-1 rounded transition-colors" style={{ color: 'rgba(255,255,255,0.35)' }}
              onMouseEnter={e => (e.currentTarget.style.color = 'rgba(255,255,255,0.75)')}
              onMouseLeave={e => (e.currentTarget.style.color = 'rgba(255,255,255,0.35)')}
            >
              <LogoutIcon className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </aside>

      {/* ── Main content ── */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">

        {/* Header */}
        <header className="h-16 flex items-center px-6 justify-between shrink-0" style={{
          background: '#FFFFFF', borderBottom: '1px solid #DDE4EE',
          boxShadow: '0 1px 4px rgba(0,48,135,0.06)',
        }}>
          <div className="flex items-center gap-3">
            <div className="w-5 h-5 rounded" style={{ background: '#F7941D', opacity: 0.85 }} />
            <span className="font-semibold text-[15px]" style={{ color: '#003087' }}>{screenTitles[screen]}</span>
          </div>
          <div className="flex items-center gap-4">
            <div className="text-right hidden sm:block">
              <div className="text-xs" style={{ color: '#6B7A8D' }}>
                {new Date().toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
              </div>
              <div className="text-[10px] text-right" style={{ color: '#aab5c4' }}>Cairo, Egypt · EGP</div>
            </div>
            <button onClick={() => setScreen('notifications')}
              className="relative p-2 rounded-lg transition-colors" style={{ color: '#6B7A8D' }}
              onMouseEnter={e => (e.currentTarget.style.background = '#F4F6F9')}
              onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
            >
              <BellIcon className="w-5 h-5" />
              <span className="absolute top-1.5 right-1.5 w-2 h-2 rounded-full" style={{ background: '#F7941D' }} />
            </button>
            <div className="w-px h-8" style={{ background: '#DDE4EE' }} />
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 rounded-full flex items-center justify-center text-white font-bold text-[11px]" style={{ background: '#003087' }}>
                {profile.initials}
              </div>
              <div className="hidden sm:block">
                <div className="text-xs font-semibold" style={{ color: '#1B2A4A' }}>{profile.name}</div>
                <div className="text-[10px]" style={{ color: '#6B7A8D' }}>{roleLabels[userRole]}</div>
              </div>
            </div>
          </div>
        </header>

        {/* Screen content */}
        <main className="flex-1 overflow-auto">
          <div className="p-6 min-h-full">
            {canView ? (
              <>
                {screen === 'dashboard'      && <Dashboard onNavigate={navigate} />}
                {screen === 'schools'        && <Schools userRole={userRole} />}
                {screen === 'transactions'   && <Transactions userRole={userRole} />}
                {screen === 'reconciliation' && <Reconciliation />}
                {screen === 'epp'            && <EPP userRole={userRole} />}
                {screen === 'reports'        && <Reports />}
                {screen === 'notifications'  && <Notifications onNavigate={navigate} />}
                {screen === 'audit-logs'     && <AuditLogs />}
                {screen === 'users'          && <Users userRole={userRole} />}
                {screen === 'settings'       && <Settings />}
              </>
            ) : (
              <AccessDenied role={userRole} screenName={screenTitles[screen]} />
            )}
          </div>
        </main>
      </div>
    </div>
  )
}
