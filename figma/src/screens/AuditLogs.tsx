import { useState, Fragment } from 'react'
import { SearchIcon, DownloadIcon, CalendarIcon, LockIcon, XIcon } from '../components/Icons'

interface AuditEntry {
  id: string
  user: string
  role: string
  action: string
  entity: string
  entityId: string
  prevValue: string
  newValue: string
  timestamp: string
  ipAddress: string
  severity: 'info' | 'warning' | 'critical'
}

const auditLog: AuditEntry[] = [
  { id: 'AUD-AUTH-01', user: 'BANK_ADMIN',       role: 'Bank Admin',      action: 'Successful authentication — MFA verified',           entity: 'Session · SES-20260831-001',          entityId: 'SES-001',          prevValue: '—', newValue: 'Login from 10.0.1.45 · Bank Admin',             timestamp: '31 Aug 2026 14:34:05', ipAddress: '10.0.1.45',    severity: 'info'     },
  { id: 'AUD-AUTH-02', user: 'BANK_ADMIN',       role: 'Bank Admin',      action: 'Failed MFA verification — incorrect OTP (1 attempt)', entity: 'Auth · MFA',                          entityId: 'MFA-FAIL-001',     prevValue: '—', newValue: 'OTP mismatch',                                  timestamp: '31 Aug 2026 14:33:51', ipAddress: '10.0.1.45',    severity: 'warning'  },
  { id: 'AUD-AUTH-03', user: 'UNKNOWN',           role: '—',               action: 'Failed login — invalid credentials (3 attempts)',     entity: 'Auth · Login',                        entityId: 'FAIL-EXT-001',     prevValue: '—', newValue: 'Account locked for 15 min',                     timestamp: '31 Aug 2026 14:33:22', ipAddress: '203.0.113.21', severity: 'critical' },
  { id: 'AUD-AUTH-04', user: 'OPS_SHERIF',        role: 'Operations',      action: 'Successful authentication — MFA verified',           entity: 'Session · SES-20260831-002',          entityId: 'SES-002',          prevValue: '—', newValue: 'Login from 10.0.1.67 · Operations',           timestamp: '31 Aug 2026 09:15:33', ipAddress: '10.0.1.67',    severity: 'info'     },
  { id: 'AUD-001',     user: 'BANK_ADMIN',       role: 'Bank Admin',      action: 'Updated institution status',                          entity: 'Institution · SCH-003',               entityId: 'SCH-003',          prevValue: 'Pending',                newValue: 'Approved',                            timestamp: '31 Aug 2026 14:32:05', ipAddress: '10.0.1.45',    severity: 'warning'  },
  { id: 'AUD-002',     user: 'OPS_SHERIF',        role: 'Operations',      action: 'Resolved reconciliation exception',                  entity: 'Exception · EXC-001',                 entityId: 'EXC-001',          prevValue: 'Open',                   newValue: 'Resolved',                            timestamp: '31 Aug 2026 14:20:18', ipAddress: '10.0.1.67',    severity: 'info'     },
  { id: 'AUD-003',     user: 'BANK_ADMIN',       role: 'Bank Admin',      action: 'Approved university application',                    entity: 'Institution · UNI-001 (Cairo University)', entityId: 'UNI-001',       prevValue: 'Under Review',           newValue: 'Approved',                            timestamp: '31 Aug 2026 14:05:12', ipAddress: '10.0.1.45',    severity: 'warning'  },
  { id: 'AUD-004',     user: 'BANK_ADMIN',       role: 'Bank Admin',      action: 'Added new bank user',                                entity: 'User · Amira Farouk',                  entityId: 'USR-012',          prevValue: '—',                      newValue: 'Role: Reconciliation Officer',         timestamp: '31 Aug 2026 13:55:44', ipAddress: '10.0.1.45',    severity: 'warning'  },
  { id: 'AUD-005',     user: 'RECON_AMIRA',       role: 'Reconciliation',  action: 'Downloaded reconciliation report',                  entity: 'Report · RECON-AUG-2026',             entityId: 'RPT-089',          prevValue: '—',                      newValue: 'PDF downloaded',                      timestamp: '31 Aug 2026 13:30:22', ipAddress: '10.0.1.89',    severity: 'info'     },
  { id: 'AUD-006',     user: 'BANK_ADMIN',       role: 'Bank Admin',      action: 'Deactivated institution account',                    entity: 'Institution · SCH-006',               entityId: 'SCH-006',          prevValue: 'Active',                 newValue: 'Inactive',                            timestamp: '31 Aug 2026 12:45:09', ipAddress: '10.0.1.45',    severity: 'critical' },
  { id: 'AUD-007',     user: 'OPS_HEBA',          role: 'Operations',      action: 'Rejected institution application',                  entity: 'Institution · SCH-006',               entityId: 'SCH-006',          prevValue: 'Under Review',           newValue: 'Rejected',                            timestamp: '31 Aug 2026 12:40:33', ipAddress: '10.0.1.78',    severity: 'warning'  },
  { id: 'AUD-008',     user: 'OPS_SHERIF',        role: 'Operations',      action: 'Updated EPP plan status',                           entity: 'EPP · EPP-2026-008',                  entityId: 'EPP-2026-008',     prevValue: 'Active',                 newValue: 'Defaulted',                           timestamp: '31 Aug 2026 11:22:15', ipAddress: '10.0.1.67',    severity: 'critical' },
  { id: 'AUD-009',     user: 'READONLY_MOSTAFA',  role: 'Read Only',       action: 'Viewed transaction details',                        entity: 'Transaction · TX-20260831-0001',       entityId: 'TX-20260831-0001', prevValue: '—',                      newValue: '—',                                   timestamp: '31 Aug 2026 11:05:41', ipAddress: '10.0.1.102',   severity: 'info'     },
  { id: 'AUD-010',     user: 'BANK_ADMIN',       role: 'Bank Admin',      action: 'Modified EPP configuration',                        entity: 'System Settings · EPP Config',         entityId: 'CFG-EPP',          prevValue: 'Max tenor: 12 months',   newValue: 'Max tenor: 18 months',                timestamp: '31 Aug 2026 10:14:28', ipAddress: '10.0.1.45',    severity: 'critical' },
  { id: 'AUD-011',     user: 'OPS_HEBA',          role: 'Operations',      action: 'Exported institution report',                       entity: 'Report · Cairo University Collections', entityId: 'RPT-088',         prevValue: '—',                      newValue: 'XLSX downloaded',                     timestamp: '31 Aug 2026 09:50:12', ipAddress: '10.0.1.78',    severity: 'info'     },
  { id: 'AUD-012',     user: 'BANK_ADMIN',       role: 'Bank Admin',      action: 'Reset user password',                               entity: 'User · OPS_SHERIF',                    entityId: 'USR-005',          prevValue: '—',                      newValue: 'Password reset email sent',           timestamp: '31 Aug 2026 09:30:07', ipAddress: '10.0.1.45',    severity: 'warning'  },
  { id: 'AUD-013',     user: 'SYSTEM',            role: 'System',          action: 'Reconciliation auto-run completed',                 entity: 'Reconciliation · 30 Aug 2026',         entityId: 'RECON-20260830',   prevValue: 'Running',                newValue: 'Completed: 1198 matched, 2 exceptions', timestamp: '31 Aug 2026 00:05:03', ipAddress: 'SYSTEM',       severity: 'info'     },
]

const severityStyle: Record<string, string> = {
  info:     'bg-blue-50  text-blue-700  border-blue-200',
  warning:  'bg-amber-50 text-amber-700 border-amber-200',
  critical: 'bg-red-50   text-red-700   border-red-200',
}
const severityDot: Record<string, string> = {
  info: 'bg-blue-400', warning: 'bg-amber-400', critical: 'bg-red-500',
}

/* Parse "31 Aug 2026 14:32:05" → Date for range filtering */
const MONTHS: Record<string, number> = { Jan:0,Feb:1,Mar:2,Apr:3,May:4,Jun:5,Jul:6,Aug:7,Sep:8,Oct:9,Nov:10,Dec:11 }
function parseAuditDate(ts: string): Date | null {
  const m = ts.match(/(\d+)\s+(\w+)\s+(\d+)\s+(\d+):(\d+):(\d+)/)
  if (!m) return null
  return new Date(+m[3], MONTHS[m[2]] ?? 0, +m[1], +m[4], +m[5], +m[6])
}

export default function AuditLogs() {
  const [search,     setSearch]     = useState('')
  const [filterRole, setFilterRole] = useState('All')
  const [filterSev,  setFilterSev]  = useState('All')
  const [dateFrom,   setDateFrom]   = useState('2026-08-01')   // US-74: controlled date range
  const [dateTo,     setDateTo]     = useState('2026-08-31')
  const [expanded,   setExpanded]   = useState<string | null>(null)

  const roles = ['All', ...Array.from(new Set(auditLog.map(e => e.role)))]

  /* US-74: Clear all filters */
  const clearFilters = () => {
    setSearch(''); setFilterRole('All'); setFilterSev('All')
    setDateFrom('2026-08-01'); setDateTo('2026-08-31')
    setExpanded(null)
  }
  const hasActiveFilters = search || filterRole !== 'All' || filterSev !== 'All' ||
    dateFrom !== '2026-08-01' || dateTo !== '2026-08-31'

  /* Filtering (US-74: date range now wired) */
  const filtered = auditLog.filter(e => {
    const matchSearch = !search ||
      e.user.toLowerCase().includes(search.toLowerCase()) ||
      e.action.toLowerCase().includes(search.toLowerCase()) ||
      e.entity.toLowerCase().includes(search.toLowerCase()) ||
      e.entityId.toLowerCase().includes(search.toLowerCase())
    const matchRole = filterRole === 'All' || e.role === filterRole
    const matchSev  = filterSev  === 'All' || e.severity === filterSev
    const entryDate = parseAuditDate(e.timestamp)
    const fromDate  = dateFrom ? new Date(dateFrom + 'T00:00:00') : null
    const toDate    = dateTo   ? new Date(dateTo   + 'T23:59:59') : null
    const matchDate = (!fromDate || !entryDate || entryDate >= fromDate) &&
                      (!toDate   || !entryDate || entryDate <= toDate)
    return matchSearch && matchRole && matchSev && matchDate
  })

  return (
    <div className="space-y-4">
      {/* Filter bar */}
      <div className="flex items-center gap-3 flex-wrap">
        {/* Search — user, action, entity, entity ID */}
        <div className="relative flex-1 min-w-[200px] max-w-xs">
          <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-300" />
          <input type="text" placeholder="User, action, entity or ID…" value={search}
            onChange={e => setSearch(e.target.value)}
            className="w-full pl-8 pr-4 py-2 border border-[#DDE3EF] rounded-lg text-sm placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] bg-white" />
        </div>

        {/* Role filter */}
        <select value={filterRole} onChange={e => setFilterRole(e.target.value)}
          className="border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-200 bg-white">
          {roles.map(r => <option key={r}>{r}</option>)}
        </select>

        {/* Severity filter */}
        <select value={filterSev} onChange={e => setFilterSev(e.target.value)}
          className="border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-200 bg-white">
          <option value="All">All Severity</option>
          <option value="critical">Critical</option>
          <option value="warning">Warning</option>
          <option value="info">Info</option>
        </select>

        {/* US-74: Date range — controlled and wired to filter */}
        <div className="flex items-center gap-1.5">
          <div className="relative">
            <CalendarIcon className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-300" />
            <input type="date" value={dateFrom} onChange={e => setDateFrom(e.target.value)}
              className="pl-8 pr-2 py-2 border border-[#DDE3EF] rounded-lg text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-200 bg-white w-[130px]" />
          </div>
          <span className="text-gray-300 text-xs">—</span>
          <input type="date" value={dateTo} onChange={e => setDateTo(e.target.value)}
            className="px-2 py-2 border border-[#DDE3EF] rounded-lg text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-200 bg-white w-[130px]" />
        </div>

        {/* US-74: Clear filters */}
        {hasActiveFilters && (
          <button onClick={clearFilters}
            className="flex items-center gap-1 text-xs font-semibold text-gray-400 hover:text-red-500 border border-gray-200 rounded-lg px-2.5 py-2 hover:bg-gray-50 transition-colors">
            <XIcon className="w-3 h-3" /> Clear
          </button>
        )}

        <button className="flex items-center gap-1.5 px-3 py-2 text-sm font-semibold border border-[#DDE3EF] rounded-lg bg-white text-gray-500 hover:bg-gray-50 transition-colors ml-auto">
          <DownloadIcon className="w-4 h-4" /> Export Log
        </button>
      </div>

      {/* US-74: Active filter chips */}
      {hasActiveFilters && (
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-[11px] text-gray-400 font-semibold">Filters:</span>
          {search      && <span className="text-[11px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">"{search}"</span>}
          {filterRole !== 'All' && <span className="text-[11px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">{filterRole}</span>}
          {filterSev  !== 'All' && <span className="text-[11px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold capitalize">{filterSev}</span>}
          {(dateFrom !== '2026-08-01' || dateTo !== '2026-08-31') && (
            <span className="text-[11px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">{dateFrom} – {dateTo}</span>
          )}
        </div>
      )}

      {/* Stat bar */}
      <div className="flex items-center gap-4 bg-white rounded-xl border border-[#E8EDF5] px-5 py-3">
        {(['critical', 'warning', 'info'] as const).map(sev => {
          const count = auditLog.filter(e => e.severity === sev).length
          return (
            <div key={sev} className="flex items-center gap-2">
              <span className={`w-2 h-2 rounded-full ${severityDot[sev]}`} />
              <span className="text-xs text-gray-400 capitalize">{sev}:</span>
              <span className="text-xs font-bold text-gray-700">{count}</span>
            </div>
          )
        })}
        <div className="h-4 w-px bg-gray-100 mx-1" />
        <span className="text-xs text-gray-400">Total: <strong className="text-gray-700">{auditLog.length}</strong></span>
        <span className="text-xs text-gray-400">Showing: <strong className="text-gray-700">{filtered.length}</strong></span>
      </div>

      {/* Log table */}
      <div className="bg-white rounded-xl border border-[#E8EDF5] overflow-hidden">
        {/* US-73: Read-only header notice */}
        <div className="flex items-center gap-2 px-5 py-2.5 bg-[#F8FAFD] border-b border-[#E8EDF5]">
          <LockIcon className="w-3.5 h-3.5 text-gray-400 shrink-0" />
          <span className="text-[11px] font-semibold text-gray-400">Read-only · Audit records are tamper-evident and cannot be edited or deleted</span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-[#F8FAFD] border-b border-[#E8EDF5]">
                {['Severity', 'User', 'Action', 'Entity', 'Previous Value', 'New Value', 'Timestamp', 'IP'].map(h => (
                  <th key={h} className="text-left px-4 py-3.5 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {filtered.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-5 py-10 text-center text-xs text-gray-400">
                    No audit entries match the current filters.
                  </td>
                </tr>
              ) : filtered.map(entry => (
                <Fragment key={entry.id}>
                  <tr
                    onClick={() => setExpanded(expanded === entry.id ? null : entry.id)}
                    className="hover:bg-[#F8FAFD] transition-colors cursor-pointer select-none">
                    <td className="px-4 py-3.5">
                      <span className={`inline-flex items-center gap-1 text-[11px] font-semibold px-2 py-0.5 rounded border ${severityStyle[entry.severity]}`}>
                        <span className={`w-1.5 h-1.5 rounded-full ${severityDot[entry.severity]}`} />
                        {entry.severity}
                      </span>
                    </td>
                    <td className="px-4 py-3.5">
                      <div className="font-mono text-xs font-bold text-[#003087] whitespace-nowrap">{entry.user}</div>
                      <div className="text-[10px] text-gray-400">{entry.role}</div>
                    </td>
                    <td className="px-4 py-3.5 text-xs text-gray-700 whitespace-nowrap">{entry.action}</td>
                    <td className="px-4 py-3.5 text-xs text-gray-600 max-w-[150px] truncate">{entry.entity}</td>
                    <td className="px-4 py-3.5">
                      {entry.prevValue === '—'
                        ? <span className="text-gray-300 text-xs">—</span>
                        : <span className="text-xs bg-red-50 text-red-700 px-2 py-0.5 rounded font-mono">{entry.prevValue}</span>}
                    </td>
                    <td className="px-4 py-3.5">
                      {entry.newValue === '—'
                        ? <span className="text-gray-300 text-xs">—</span>
                        : <span className="text-xs bg-green-50 text-green-700 px-2 py-0.5 rounded font-mono">{entry.newValue}</span>}
                    </td>
                    <td className="px-4 py-3.5 text-xs text-gray-400 whitespace-nowrap font-mono">{entry.timestamp}</td>
                    <td className="px-4 py-3.5 text-xs font-mono text-gray-400">{entry.ipAddress}</td>
                  </tr>

                  {/* US-73: Expandable detail row */}
                  {expanded === entry.id && (
                    <tr className="bg-[#F4F6F9]">
                      <td colSpan={8} className="px-5 py-3.5 border-t border-[#E8EDF5]">
                        <div className="grid grid-cols-4 gap-x-8 gap-y-2 text-xs">
                          <div>
                            <span className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider block mb-0.5">Entry ID</span>
                            <span className="font-mono text-gray-700">{entry.id}</span>
                          </div>
                          <div>
                            <span className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider block mb-0.5">Entity ID</span>
                            <span className="font-mono text-[#003087] font-semibold">{entry.entityId}</span>
                          </div>
                          <div>
                            <span className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider block mb-0.5">Full Timestamp</span>
                            <span className="font-mono text-gray-700">{entry.timestamp} EET</span>
                          </div>
                          <div>
                            <span className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider block mb-0.5">Session IP</span>
                            <span className="font-mono text-gray-700">{entry.ipAddress}</span>
                          </div>
                        </div>
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>

        <div className="flex items-center justify-between px-5 py-3.5 border-t border-gray-100">
          <div className="flex items-center gap-2">
            <LockIcon className="w-3 h-3 text-gray-300" />
            <span className="text-xs text-gray-400">Showing {filtered.length} of {auditLog.length} entries · Retention: 7 years</span>
          </div>
          <span className="text-xs text-gray-400">Click any row to expand details</span>
        </div>
      </div>
    </div>
  )
}
