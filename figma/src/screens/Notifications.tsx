import { useState } from 'react'
import {
  BellIcon, AlertIcon, SchoolIcon, TransactionIcon, ReconcileIcon,
  CheckCircleIcon, XIcon,
} from '../components/Icons'
import type { Screen } from '../Portal'

type NotifType = 'failed_payment' | 'recon_exception' | 'institution_issue' | 'new_institution' | 'system_alert' | 'deadline_warning'

interface Notification {
  id: string
  type: NotifType
  title: string
  body: string
  meta?: string           // exception ref + status line (US-71)
  timestamp: string
  read: boolean
  severity: 'high' | 'medium' | 'low'
  actionLabel?: string    // CTA button text
  actionScreen?: Screen   // destination screen
}

const initialNotifs: Notification[] = [
  {
    id: 'N000a', type: 'deadline_warning', severity: 'high', read: false,
    title: 'Late Penalty Applied — 2 Overdue Transactions',
    body: 'TX-20260831-0004 (Omar Ali, Heliopolis Academy, EGP 22,000) is 2 days overdue — EGP 1,100 penalty applied. TX-20260831-0012 (Rania Mostafa, Cairo University, EGP 8,500) is 10 days overdue — EGP 425 penalty applied. Grace period has ended on the latter.',
    meta: 'Total penalties: EGP 1,525 · Rate: 5% of outstanding balance',
    timestamp: '09:00 today',
    actionLabel: 'View Transactions', actionScreen: 'transactions',
  },
  {
    id: 'N000b', type: 'deadline_warning', severity: 'high', read: false,
    title: 'Payment Deadline Due Today — 3 Fees',
    body: 'Sara Mohamed (Maadi British School) has Tuition Term 1 2026/27 of EGP 12,400 due today, 7 Sep 2026. Payment must be processed by end of business to avoid a 5% late penalty.',
    meta: 'Fee: FEE-SM-001 · Student NID: 302•••••321098',
    timestamp: '08:00 today',
    actionLabel: 'View Transactions', actionScreen: 'transactions',
  },
  {
    id: 'N000c', type: 'deadline_warning', severity: 'medium', read: false,
    title: 'Upcoming Deadlines — 8 Payments Due This Week',
    body: '8 fee payments are due between 7–13 Sep 2026. Of these, 3 are Urgent (due within 3 days), 2 are High priority (due in 5–8 days), and 3 are Medium priority. Total outstanding: EGP 33,625.',
    timestamp: '07:00 today',
    actionLabel: 'View Transactions', actionScreen: 'transactions',
  },
  {
    id: 'N001', type: 'recon_exception', severity: 'high', read: false,
    title: 'Reconciliation Exception Detected',
    body: 'Nasr City Academy (School): EGP 9,400 discrepancy between institution records and bank settlement for 31 Aug 2026.',
    meta: 'Exception: EXC-001 · Status: Under Investigation',
    timestamp: '14:32 today',
    actionLabel: 'Investigate Exception', actionScreen: 'reconciliation',
  },
  {
    id: 'N002', type: 'failed_payment', severity: 'high', read: false,
    title: 'Multiple Failed Payments — Heliopolis Academy',
    body: '3 card payments failed in the last hour. Total value: EGP 66,000. Common decline code: 51 (Insufficient Funds).',
    timestamp: '14:18 today',
    actionLabel: 'View Transactions', actionScreen: 'transactions',
  },
  {
    id: 'N003', type: 'new_institution', severity: 'medium', read: false,
    title: 'New University Application Submitted',
    body: 'Future University in Egypt (FUE) has submitted their registration application for review. Type: University (Private). Enrolled students: 5,200.',
    timestamp: '13:50 today',
    actionLabel: 'Review Application', actionScreen: 'schools',
  },
  {
    id: 'N004', type: 'new_institution', severity: 'medium', read: false,
    title: 'New School Application Submitted',
    body: 'Egyptian Language School (ELS) has submitted their registration application for review. Type: School (National). Principal: Mr. Hossam Ragab.',
    timestamp: '13:45 today',
    actionLabel: 'Review Application', actionScreen: 'schools',
  },
  {
    id: 'N005', type: 'recon_exception', severity: 'high', read: false,
    title: 'Reconciliation Exception — Maadi British School',
    body: 'EGP 8,500 difference detected between system amount (EGP 151,000) and bank amount (EGP 142,500) for 31 Aug 2026.',
    meta: 'Exception: EXC-002 · Status: Open · Ref: RC-20260831-005',
    timestamp: '12:55 today',
    actionLabel: 'Investigate Exception', actionScreen: 'reconciliation',
  },
  {
    id: 'N006', type: 'institution_issue', severity: 'medium', read: false,
    title: 'Upload Problem — New Cairo International',
    body: 'Fee upload file failed validation: 14 records with missing student IDs. Upload rejected. Institution notified.',
    timestamp: '12:10 today',
    actionLabel: 'View Institution', actionScreen: 'schools',
  },
  {
    id: 'N007', type: 'system_alert', severity: 'medium', read: true,
    title: 'Payment Gateway Latency Alert',
    body: 'Card payment processing latency exceeded 3 seconds for 15 minutes (11:30–11:45). Now resolved.',
    timestamp: '11:45 today',
  },
  {
    id: 'N008', type: 'failed_payment', severity: 'high', read: true,
    title: 'Failed Payment — High Value',
    body: 'TX-20260831-0004: EGP 22,000 tuition payment failed for Omar Ali at Heliopolis Academy. Reason: Card declined.',
    meta: 'Transaction: TX-20260831-0004 · Amount: EGP 22,000',
    timestamp: '11:15 today',
    actionLabel: 'View Transaction', actionScreen: 'transactions',
  },
  {
    id: 'N009', type: 'new_institution', severity: 'low', read: true,
    title: 'University Application Under Review',
    body: 'Mansoura University application moved to "Under Review" status. Assigned to Operations Team. Type: University (Public).',
    timestamp: '10:30 today',
    actionLabel: 'Review Application', actionScreen: 'schools',
  },
  {
    id: 'N010', type: 'system_alert', severity: 'low', read: true,
    title: 'Daily Reconciliation Run Completed',
    body: 'Automatic reconciliation for 30 Aug 2026 completed. 1,198 matched, 2 exceptions flagged across all institutions.',
    timestamp: '00:05 today',
    actionLabel: 'View Reconciliation', actionScreen: 'reconciliation',
  },
  {
    id: 'N011', type: 'institution_issue', severity: 'low', read: true,
    title: 'October STEM School — Application Rejected',
    body: 'October STEM School (School) application rejected due to incomplete documentation. Rejection notice sent.',
    timestamp: 'Yesterday 16:20',
    actionLabel: 'View Institution', actionScreen: 'schools',
  },
]

const typeConfig: Record<NotifType, { Icon: React.ComponentType<React.SVGProps<SVGSVGElement>>; label: string; bg: string; iconColor: string }> = {
  failed_payment:   { Icon: TransactionIcon, label: 'Failed Payment',    bg: 'bg-red-50',    iconColor: 'text-red-500'    },
  recon_exception:  { Icon: ReconcileIcon,   label: 'Reconciliation',    bg: 'bg-orange-50', iconColor: 'text-orange-500' },
  institution_issue:{ Icon: SchoolIcon,      label: 'Institution Issue', bg: 'bg-amber-50',  iconColor: 'text-amber-600'  },
  new_institution:  { Icon: SchoolIcon,      label: 'New Institution',   bg: 'bg-blue-50',   iconColor: 'text-blue-600'   },
  system_alert:     { Icon: AlertIcon,       label: 'System',            bg: 'bg-purple-50', iconColor: 'text-purple-600' },
  deadline_warning: { Icon: AlertIcon,       label: 'Deadline',          bg: 'bg-orange-50', iconColor: 'text-orange-600' },
}

const severityStyle: Record<string, string> = {
  high:   'bg-red-50   text-red-700   border-red-200',
  medium: 'bg-amber-50 text-amber-700 border-amber-200',
  low:    'bg-gray-100 text-gray-500  border-gray-200',
}

const typeLabelStyle: Record<NotifType, string> = {
  failed_payment:    'bg-red-50    text-red-600',
  recon_exception:   'bg-orange-50 text-orange-600',
  institution_issue: 'bg-amber-50  text-amber-600',
  new_institution:   'bg-blue-50   text-blue-600',
  system_alert:      'bg-gray-100  text-gray-500',
  deadline_warning:  'bg-orange-50 text-orange-700',
}

export default function Notifications({ onNavigate }: { onNavigate?: (screen: Screen) => void }) {
  const [notifs, setNotifs] = useState(initialNotifs)
  const [filter, setFilter] = useState('All')

  const markAllRead = () => setNotifs(n => n.map(x => ({ ...x, read: true })))
  const markRead    = (id: string) => setNotifs(n => n.map(x => x.id === id ? { ...x, read: true } : x))
  const dismiss     = (id: string) => setNotifs(n => n.filter(x => x.id !== id))

  const handleAction = (notif: Notification) => {
    markRead(notif.id)
    if (notif.actionScreen) onNavigate?.(notif.actionScreen)
  }

  const types = ['All', 'Deadline Warnings', 'Failed Payments', 'Reconciliation', 'Institution Issues', 'New Institutions', 'System Alerts']
  const typeMap: Record<string, NotifType | null> = {
    'All': null,
    'Deadline Warnings': 'deadline_warning',
    'Failed Payments':   'failed_payment',
    'Reconciliation':    'recon_exception',
    'Institution Issues':'institution_issue',
    'New Institutions':  'new_institution',
    'System Alerts':     'system_alert',
  }

  const filtered    = notifs.filter(n => filter === 'All' || n.type === typeMap[filter])
  const unreadCount = notifs.filter(n => !n.read).length

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <p className="text-sm text-gray-400">{filtered.length} notification{filtered.length !== 1 ? 's' : ''}</p>
          {unreadCount > 0 && (
            <span className="bg-red-100 text-red-700 text-[11px] font-bold px-2 py-0.5 rounded-full">
              {unreadCount} unread
            </span>
          )}
        </div>
        {unreadCount > 0 && (
          <button onClick={markAllRead} className="text-xs text-[#003087] font-semibold hover:underline flex items-center gap-1">
            <CheckCircleIcon className="w-3.5 h-3.5" /> Mark all as read
          </button>
        )}
      </div>

      {/* Filter tabs */}
      <div className="flex items-center gap-1 flex-wrap">
        {types.map(type => (
          <button key={type} onClick={() => setFilter(type)}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${filter === type ? 'bg-[#003087] text-white' : 'bg-white border border-[#DDE3EF] text-gray-500 hover:bg-gray-50'}`}>
            {type}
          </button>
        ))}
      </div>

      {/* Notification list */}
      <div className="space-y-2">
        {filtered.length === 0 ? (
          <div className="bg-white rounded-xl border border-[#E8EDF5] p-12 text-center">
            <BellIcon className="w-8 h-8 text-gray-200 mx-auto mb-3" />
            <p className="text-sm font-semibold text-gray-400">No notifications in this category</p>
            <p className="text-xs text-gray-300 mt-1">You"re all caught up.</p>
          </div>
        ) : (
          filtered.map(notif => {
            const cfg = typeConfig[notif.type]
            return (
              <div key={notif.id}
                className={`bg-white rounded-xl border p-4 transition-all ${!notif.read ? 'border-[#003087]/20 shadow-sm' : 'border-[#E8EDF5]'}`}>
                <div className="flex items-start gap-3">
                  {/* Icon */}
                  <div className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 ${cfg.bg}`}>
                    <cfg.Icon className={`w-4 h-4 ${cfg.iconColor}`} />
                  </div>

                  {/* Body */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex items-center gap-2 flex-wrap">
                        {!notif.read && <span className="w-2 h-2 bg-[#003087] rounded-full shrink-0" />}
                        <span className="text-sm font-semibold text-gray-800">{notif.title}</span>
                        <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded border ${severityStyle[notif.severity]}`}>
                          {notif.severity}
                        </span>
                      </div>
                      <div className="flex items-center gap-1 shrink-0">
                        {!notif.read && (
                          <button onClick={() => markRead(notif.id)}
                            className="text-[11px] text-[#003087] font-semibold hover:underline whitespace-nowrap">
                            Mark read
                          </button>
                        )}
                        <button onClick={() => dismiss(notif.id)}
                          className="p-1 rounded hover:bg-gray-100 text-gray-300 hover:text-gray-500 transition-colors">
                          <XIcon className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>

                    <p className="text-xs text-gray-500 mt-1 leading-relaxed">{notif.body}</p>

                    {/* US-71: Exception reference + status (for payment and recon exceptions) */}
                    {notif.meta && (
                      <p className="text-[11px] font-semibold text-gray-400 mt-1.5 font-mono">{notif.meta}</p>
                    )}

                    <div className="flex items-center justify-between gap-2 mt-2.5">
                      <div className="flex items-center gap-2">
                        <span className="text-[11px] text-gray-400">{notif.timestamp}</span>
                        <span className="text-gray-200">·</span>
                        <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${typeLabelStyle[notif.type]}`}>
                          {cfg.label}
                        </span>
                      </div>

                      {/* US-71: Action CTA — navigate to relevant screen */}
                      {notif.actionLabel && (
                        <button onClick={() => handleAction(notif)}
                          className="text-[11px] font-semibold text-[#003087] border border-[#003087]/25 rounded-lg px-2.5 py-1 hover:bg-[#EBF1FB] transition-colors whitespace-nowrap shrink-0">
                          {notif.actionLabel} →
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            )
          })
        )}
      </div>
    </div>
  )
}
