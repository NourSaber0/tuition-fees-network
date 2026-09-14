import { useState } from 'react'
import type { Screen } from '../Portal'
import {
  SchoolIcon, UsersIcon, TransactionIcon, TrendUpIcon, TrendDownIcon,
  CheckCircleIcon, XCircleIcon, ClockIcon, ReconcileIcon, EPPIcon, ChevronRightIcon, AlertIcon,
} from '../components/Icons'
import { priorityBadge } from '../utils/deadline'
import type { Priority } from '../utils/deadline'

interface Props { onNavigate: (s: Screen) => void }

const kpis = [
  { title: 'Active Institutions', value: '61', sub: '35 schools · 26 universities', trend: 6.3, positive: true, Icon: SchoolIcon, color: 'bg-[#003087]/10 text-[#003087]' },
  { title: 'Total Students', value: '42,816', sub: 'Enrolled this year', trend: 4.1, positive: true, Icon: UsersIcon, color: 'bg-indigo-50 text-indigo-600' },
  { title: "Today's Transactions", value: '1,284', sub: 'As of 14:32 EET', trend: 11.2, positive: true, Icon: TransactionIcon, color: 'bg-sky-50 text-sky-600' },
  { title: "Today's Collection", value: 'EGP 2,418,600', sub: '↑ vs yesterday EGP 2.1M', trend: 15.2, positive: true, Icon: TrendUpIcon, color: 'bg-[#F7941D]/15 text-[#C96B10]' },
  { title: 'Successful Payments', value: '1,201', sub: '93.5% success rate', trend: 0.8, positive: true, Icon: CheckCircleIcon, color: 'bg-green-50 text-green-600' },
  { title: 'Failed Payments', value: '48', sub: '3.7% failure rate', trend: -2.1, positive: false, Icon: XCircleIcon, color: 'bg-red-50 text-red-500' },
  { title: 'Pending Payments', value: '35', sub: 'Awaiting confirmation', trend: -8.4, positive: true, Icon: ClockIcon, color: 'bg-amber-50 text-amber-600' },
  { title: 'Pending Reconciliation', value: '3', sub: 'Exceptions flagged', trend: 0, positive: true, Icon: ReconcileIcon, color: 'bg-orange-50 text-orange-600' },
  { title: 'Active EPP Plans', value: '312', sub: 'EGP 7.2M outstanding', trend: 9.5, positive: true, Icon: EPPIcon, color: 'bg-purple-50 text-purple-600' },
]

const recentTx = [
  { id: 'TX-20260831-001', institution: 'Cairo International School', institutionType: 'School', student: 'Ahmed Hassan', fee: 'Tuition Q1', amount: 18000, method: 'Card', status: 'Successful', time: '14:28' },
  { id: 'TX-20260831-002', institution: 'Cairo University', institutionType: 'University', student: 'Karim Nour', fee: 'Semester Fee', amount: 12500, method: 'Bank Transfer', status: 'Pending', time: '14:25' },
  { id: 'TX-20260831-003', institution: 'Maadi British School', institutionType: 'School', student: 'Sara Mohamed', fee: 'Activity Fee', amount: 2500, method: 'Bank Transfer', status: 'Pending', time: '14:22' },
  { id: 'TX-20260831-004', institution: 'Heliopolis Academy', institutionType: 'School', student: 'Omar Ali', fee: 'Tuition Q1', amount: 22000, method: 'Card', status: 'Failed', time: '14:18' },
  { id: 'TX-20260831-005', institution: 'Ain Shams University', institutionType: 'University', student: 'Nadia Saleh', fee: 'Lab Fee', amount: 3800, method: 'Card', status: 'Successful', time: '14:12' },
  { id: 'TX-20260831-006', institution: 'Cairo International School', institutionType: 'School', student: 'Mostafa Ahmed', fee: 'Tuition Q2', amount: 18000, method: 'EPP', status: 'Successful', time: '14:09' },
]

const weeklyData = [
  { day: 'Mon', amount: 1840000 },
  { day: 'Tue', amount: 2100000 },
  { day: 'Wed', amount: 1950000 },
  { day: 'Thu', amount: 2350000 },
  { day: 'Fri', amount: 1680000 },
  { day: 'Sat', amount: 890000 },
  { day: 'Sun', amount: 2418600 },
]

/* ── Chart geometry (declared after weeklyData so xOf can reference it safely) ── */
const VW = 520, VH = 168
const PL = 48, PR = 12, PT = 14
const CW = VW - PL - PR   // 460
const CH = VH - PT - 28   // 126
const Y_MAX = 2800000
const BOTTOM = PT + CH    // 140

function xOf(i: number) { return PL + (i / (weeklyData.length - 1)) * CW }
function yOf(v: number) { return PT + CH * (1 - v / Y_MAX) }

const deadlineKpis = [
  { title: 'Due Today',       value: '3',          sub: 'Payments due Sep 7',      color: 'bg-orange-50  text-orange-600',  dot: 'bg-orange-500'  },
  { title: 'Due This Week',   value: '8',          sub: 'Due by Sep 13',           color: 'bg-amber-50   text-amber-600',   dot: 'bg-amber-500'   },
  { title: 'Urgent',          value: '12',         sub: '1–6 days remaining',      color: 'bg-orange-50  text-orange-600',  dot: 'bg-orange-400'  },
  { title: 'Overdue',         value: '6',          sub: '2 grace periods ended',   color: 'bg-red-50     text-red-600',     dot: 'bg-red-500'     },
  { title: 'Penalties Applied', value: 'EGP 47,200', sub: '6 overdue fees',       color: 'bg-red-50     text-red-600',     dot: 'bg-red-600'     },
]

interface PriorityItem {
  id: string
  student: string
  institution: string
  fee: string
  amount: number
  dueDate: string
  label: string
  priority: Priority
  penalty: number
}

const priorityItems: PriorityItem[] = [
  { id: 'TX-20260831-0012', student: 'Rania Mostafa',  institution: 'Cairo University',      fee: 'Enrollment Fee', amount: 8500,  dueDate: '28 Aug 2026', label: '10 days overdue · Grace ended', priority: 'OVERDUE', penalty: 425  },
  { id: 'TX-20260831-0004', student: 'Omar Ali',        institution: 'Heliopolis Academy',    fee: 'Tuition Q3',     amount: 22000, dueDate: '5 Sep 2026',  label: '2 days overdue',                priority: 'OVERDUE', penalty: 1100 },
  { id: 'FEE-SM-001',       student: 'Sara Mohamed',    institution: 'Maadi British School',  fee: 'Tuition Term 1', amount: 12400, dueDate: '7 Sep 2026',  label: 'Due Today',                     priority: 'URGENT',  penalty: 0   },
  { id: 'TX-20260831-0003', student: 'Sara Mohamed',    institution: 'Maadi British School',  fee: 'Activity Fee',   amount: 1300,  dueDate: '10 Sep 2026', label: 'Due in 3 days',                 priority: 'URGENT',  penalty: 0   },
  { id: 'FEE-AH-001',       student: 'Ahmed Hassan',    institution: 'Cairo Intl. School',    fee: 'Tuition Term 1', amount: 13000, dueDate: '12 Sep 2026', label: 'Due in 5 days',                 priority: 'URGENT',  penalty: 0   },
  { id: 'TX-20260831-0002', student: 'Karim Nour',      institution: 'Cairo University',      fee: 'Semester Fee',   amount: 4500,  dueDate: '15 Sep 2026', label: 'Due in 8 days',                 priority: 'HIGH',    penalty: 0   },
]

const statusStyle: Record<string, string> = {
  Successful: 'bg-green-50 text-green-700 border-green-200',
  Pending: 'bg-amber-50 text-amber-700 border-amber-200',
  Failed: 'bg-red-50 text-red-700 border-red-200',
}

export default function Dashboard({ onNavigate }: Props) {
  const [hoveredDay, setHoveredDay] = useState<number | null>(null)

  /* Build chart path data */
  const pts = weeklyData.map((d, i) => ({ ...d, x: xOf(i), y: yOf(d.amount) }))

  const pathD = pts.reduce((acc, p, i) => {
    if (i === 0) return `M ${p.x.toFixed(1)} ${p.y.toFixed(1)}`
    const prev = pts[i - 1]
    const mx = ((prev.x + p.x) / 2).toFixed(1)
    return `${acc} C ${mx} ${prev.y.toFixed(1)} ${mx} ${p.y.toFixed(1)} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`
  }, '')

  const areaD = `${pathD} L ${pts[6].x.toFixed(1)} ${BOTTOM} L ${pts[0].x.toFixed(1)} ${BOTTOM} Z`

  const gridVals = [700000, 1400000, 2100000, 2800000]
  const hov = hoveredDay !== null ? pts[hoveredDay] : null

  /* Tooltip position + label — computed outside JSX to avoid IIFE in render */
  const tooltipTx  = hov ? Math.min(Math.max(hov.x - 46, PL), VW - PR - 92) : 0
  const tooltipTy  = hov ? Math.max(hov.y - 34, PT + 2) : 0
  const tooltipLbl = hov
    ? (hov.amount >= 1000000
        ? `EGP ${(hov.amount / 1000000).toFixed(3)}M`
        : `EGP ${(hov.amount / 1000).toFixed(0)}K`)
    : ''

  return (
    <div className="space-y-6">
      {/* Welcome bar */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-[17px] font-semibold text-[#1B2A4A]">Good afternoon, Mohamed</h1>
          <p className="text-sm text-gray-400 mt-0.5">Here's a network-wide summary for today, 31 Aug 2026</p>
        </div>
        <div className="flex items-center gap-2 rounded-lg px-3 py-1.5" style={{ background: '#FEF3E6', border: '1px solid #FDBA74' }}>
          <span className="w-2 h-2 rounded-full animate-pulse" style={{ background: '#F7941D' }} />
          <span className="text-xs font-semibold" style={{ color: '#C96B10' }}>3 reconciliation exceptions need attention</span>
          <button onClick={() => onNavigate('reconciliation')} style={{ color: '#C96B10' }} className="transition-colors hover:opacity-70">
            <ChevronRightIcon className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* KPI grid */}
      <div className="grid grid-cols-3 gap-4">
        {kpis.map(({ title, value, sub, trend, positive, Icon, color }) => (
          <div key={title} className="bg-white rounded-xl border border-[#E8EDF5] p-5 hover:shadow-md transition-shadow">
            <div className="flex items-start justify-between">
              <div className="flex-1 min-w-0">
                <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">{title}</p>
                <p className="text-[22px] font-bold text-[#1B2A4A] mt-1 leading-none">{value}</p>
                <p className="text-xs text-gray-400 mt-1.5">{sub}</p>
              </div>
              <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 ${color}`}>
                <Icon className="w-5 h-5" />
              </div>
            </div>
            {trend !== 0 && (
              <div className={`mt-3 flex items-center gap-1 text-[11px] font-medium ${positive ? 'text-green-600' : 'text-red-500'}`}>
                {positive ? <TrendUpIcon className="w-3 h-3" /> : <TrendDownIcon className="w-3 h-3" />}
                <span>{Math.abs(trend)}% vs yesterday</span>
              </div>
            )}
          </div>
        ))}
      </div>

      {/* Deadline KPIs */}
      <div className="grid grid-cols-5 gap-3">
        {deadlineKpis.map(kpi => (
          <div key={kpi.title} className="bg-white rounded-xl border border-[#E8EDF5] px-4 py-3.5 hover:shadow-md transition-shadow">
            <div className="flex items-center gap-1.5 mb-1.5">
              <span className={`w-2 h-2 rounded-full shrink-0 ${kpi.dot}`} />
              <span className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider leading-none">{kpi.title}</span>
            </div>
            <div className="text-xl font-bold text-[#1B2A4A] leading-none">{kpi.value}</div>
            <div className="text-[11px] text-gray-400 mt-1">{kpi.sub}</div>
          </div>
        ))}
      </div>

      {/* Payment Priority attention section */}
      <div className="bg-white rounded-xl border border-[#E8EDF5] overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-lg bg-red-50 flex items-center justify-center">
              <AlertIcon className="w-4 h-4 text-red-500" />
            </div>
            <h3 className="text-sm font-semibold text-[#1B2A4A]">Payment Priority — Action Required</h3>
            <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-red-100 text-red-700">6 items</span>
          </div>
          <button onClick={() => onNavigate('transactions')} className="text-xs font-medium flex items-center gap-1 hover:underline" style={{ color: '#003087' }}>
            View all <ChevronRightIcon className="w-3.5 h-3.5" />
          </button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-[#F8FAFD]">
                {['Ref / Student', 'Institution', 'Fee', 'Amount', 'Due Date', 'Status', 'Penalty'].map(h => (
                  <th key={h} className="text-left px-5 py-3 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {priorityItems.map(item => (
                <tr key={item.id} className={`hover:bg-[#F8FAFD] transition-colors ${item.priority === 'OVERDUE' ? 'bg-red-50/40' : item.priority === 'URGENT' && item.label === 'Due Today' ? 'bg-orange-50/40' : ''}`}>
                  <td className="px-5 py-3.5">
                    <div className="font-mono text-xs text-[#003087] font-semibold">{item.id}</div>
                    <div className="text-xs text-gray-600 mt-0.5">{item.student}</div>
                  </td>
                  <td className="px-5 py-3.5 text-xs text-gray-600 whitespace-nowrap">{item.institution}</td>
                  <td className="px-5 py-3.5 text-xs text-gray-500">{item.fee}</td>
                  <td className="px-5 py-3.5 text-xs font-mono font-bold text-gray-800 whitespace-nowrap">EGP {item.amount.toLocaleString()}</td>
                  <td className="px-5 py-3.5 whitespace-nowrap">
                    <div className="text-xs text-gray-600 font-mono">{item.dueDate}</div>
                    <div className={`text-[10px] mt-0.5 font-medium ${item.priority === 'OVERDUE' ? 'text-red-600' : item.priority === 'URGENT' ? 'text-orange-600' : 'text-gray-400'}`}>
                      {item.label}
                    </div>
                  </td>
                  <td className="px-5 py-3.5 whitespace-nowrap">
                    <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold border ${priorityBadge[item.priority]}`}>
                      {item.priority}
                    </span>
                  </td>
                  <td className="px-5 py-3.5 whitespace-nowrap">
                    {item.penalty > 0
                      ? <span className="text-xs font-mono font-semibold text-red-600">+EGP {item.penalty.toLocaleString()}</span>
                      : <span className="text-xs text-gray-300">—</span>
                    }
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-4">
        {/* Weekly chart */}
        <div className="col-span-2 bg-white rounded-xl border border-[#E8EDF5] p-5">
          <div className="flex items-center justify-between mb-3">
            <div>
              <h3 className="text-sm font-semibold text-[#1B2A4A]">Weekly Collections</h3>
              <p className="text-xs text-gray-400">Mon 25 – Sun 31 Aug 2026</p>
            </div>
            <span className="text-xs font-semibold px-2 py-1 rounded" style={{ background: '#EBF1FB', color: '#003087' }}>EGP</span>
          </div>

          {/* SVG line/area chart */}
          <div onMouseLeave={() => setHoveredDay(null)}>
            <svg
              viewBox={`0 0 ${VW} ${VH}`}
              className="w-full h-auto"
              style={{ minHeight: 130, display: 'block' }}
            >
              <defs>
                <linearGradient id="wc-area-grad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%"   stopColor="#003087" stopOpacity="0.18"/>
                  <stop offset="75%"  stopColor="#003087" stopOpacity="0.04"/>
                  <stop offset="100%" stopColor="#003087" stopOpacity="0"/>
                </linearGradient>
              </defs>

              {/* Horizontal gridlines */}
              {gridVals.map(v => {
                const gy = yOf(v)
                return (
                  <g key={v}>
                    <line x1={PL} y1={gy} x2={VW - PR} y2={gy} stroke="#EEF1F7" strokeWidth="1"/>
                    <text x={PL - 6} y={gy + 3.5} textAnchor="end" fontSize="9" fill="#C4CDDA">
                      {`${(v / 1000000).toFixed(1)}M`}
                    </text>
                  </g>
                )
              })}
              {/* Baseline */}
              <line x1={PL} y1={BOTTOM} x2={VW - PR} y2={BOTTOM} stroke="#E8EDF5" strokeWidth="1"/>
              <text x={PL - 6} y={BOTTOM + 3.5} textAnchor="end" fontSize="9" fill="#C4CDDA">0</text>

              {/* Area fill */}
              <path d={areaD} fill="url(#wc-area-grad)"/>

              {/* Line */}
              <path
                d={pathD}
                fill="none"
                stroke="#003087"
                strokeWidth="2.5"
                strokeLinejoin="round"
                strokeLinecap="round"
              />

              {/* Hovered vertical guide */}
              {hov && (
                <line
                  x1={hov.x} y1={PT}
                  x2={hov.x} y2={BOTTOM}
                  stroke="#003087" strokeWidth="1"
                  strokeDasharray="3 3" opacity="0.3"
                />
              )}

              {/* Hit areas + dots */}
              {pts.map((p, i) => {
                const isToday = i === 6
                const isHov   = hoveredDay === i
                const show    = isToday || isHov
                const fill    = isToday ? '#F7941D' : '#003087'
                return (
                  <g key={p.day}>
                    <rect
                      x={p.x - 32} y={PT} width={64} height={CH}
                      fill="transparent"
                      style={{ cursor: 'crosshair' }}
                      onMouseEnter={() => setHoveredDay(i)}
                    />
                    {show && (
                      <>
                        <circle cx={p.x} cy={p.y} r={isHov ? 9 : 7} fill={fill} fillOpacity="0.14"/>
                        <circle cx={p.x} cy={p.y} r={isHov ? 4.5 : 3.5} fill={fill} stroke="white" strokeWidth="1.5"/>
                      </>
                    )}
                  </g>
                )
              })}

              {/* Day labels */}
              {pts.map((p, i) => (
                <text
                  key={p.day + '-lbl'}
                  x={p.x} y={VH - 5}
                  textAnchor="middle"
                  fontSize="10"
                  fontWeight={i === 6 ? '700' : '500'}
                  fill={i === 6 ? '#F7941D' : '#9CA3AF'}
                >
                  {p.day}
                </text>
              ))}

              {/* Tooltip */}
              {hov && (
                <g>
                  <rect x={tooltipTx} y={tooltipTy} width={92} height={24} rx="4" fill="#1B2A4A" opacity="0.9"/>
                  <text x={tooltipTx + 46} y={tooltipTy + 15.5} textAnchor="middle" fontSize="10" fontWeight="600" fill="white">
                    {tooltipLbl}
                  </text>
                </g>
              )}
            </svg>
          </div>

          <div className="mt-1 pt-3 border-t border-gray-100 flex gap-6 text-xs text-gray-400">
            <span>Week total: <strong className="text-gray-700">EGP 13,228,600</strong></span>
            <span>Daily avg: <strong className="text-gray-700">EGP 1,889,800</strong></span>
          </div>
        </div>

        {/* Institution status snapshot */}
        <div className="bg-white rounded-xl border border-[#E8EDF5] p-5">
          <h3 className="text-sm font-semibold text-[#1B2A4A] mb-1">Institution Status</h3>
          <div className="flex items-center gap-3 mb-4">
            <span className="text-[11px] px-2 py-0.5 rounded-full font-semibold" style={{ background: '#EBF1FB', color: '#003087' }}>35 Schools</span>
            <span className="text-[11px] px-2 py-0.5 rounded-full font-semibold" style={{ background: '#FEF3E6', color: '#C96B10' }}>26 Universities</span>
          </div>
          <div className="space-y-3">
            {[
              { label: 'Active & Integrated', count: 51, color: 'bg-green-500', pct: 85 },
              { label: 'Active, Not Integrated', count: 10, color: 'bg-amber-400', pct: 16 },
              { label: 'Pending Approval', count: 5, color: 'bg-blue-400', pct: 8 },
              { label: 'Suspended', count: 2, color: 'bg-red-400', pct: 3 },
            ].map(item => (
              <div key={item.label}>
                <div className="flex items-center justify-between mb-1">
                  <span className="text-xs text-gray-500">{item.label}</span>
                  <span className="text-xs font-bold text-gray-700">{item.count}</span>
                </div>
                <div className="h-1.5 bg-gray-100 rounded-full overflow-hidden">
                  <div className={`h-full ${item.color} rounded-full`} style={{ width: `${item.pct}%` }} />
                </div>
              </div>
            ))}
          </div>
          <button
            onClick={() => onNavigate('schools')}
            className="mt-4 w-full text-xs font-semibold rounded-lg py-2 transition-colors" style={{ color: '#003087', border: '1px solid rgba(0,48,135,0.18)', background: 'transparent' }}
            onMouseEnter={e => (e.currentTarget.style.background = '#EBF1FB')}
            onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
          >
            View All Institutions
          </button>
        </div>
      </div>

      {/* Recent transactions */}
      <div className="bg-white rounded-xl border border-[#E8EDF5]">
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <h3 className="text-sm font-semibold text-[#1B2A4A]">Recent Transactions</h3>
          <button
            onClick={() => onNavigate('transactions')}
            className="text-xs font-medium flex items-center gap-1 hover:underline" style={{ color: '#003087' }}
          >
            View all <ChevronRightIcon className="w-3.5 h-3.5" />
          </button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-[#F8FAFD]">
                {['Transaction ID', 'Institution', 'Type', 'Student', 'Fee', 'Amount', 'Method', 'Time', 'Status'].map(h => (
                  <th key={h} className="text-left px-5 py-3 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {recentTx.map(tx => (
                <tr key={tx.id} className="hover:bg-[#F8FAFD] transition-colors">
                  <td className="px-5 py-3.5 font-mono text-xs text-[#003087] font-medium whitespace-nowrap">{tx.id}</td>
                  <td className="px-5 py-3.5 text-xs text-gray-700 whitespace-nowrap">{tx.institution}</td>
                  <td className="px-5 py-3.5 whitespace-nowrap">
                    <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${tx.institutionType === 'University' ? 'bg-[#FEF3E6] text-[#C96B10]' : 'bg-[#EBF1FB] text-[#003087]'}`}>{tx.institutionType}</span>
                  </td>
                  <td className="px-5 py-3.5 text-xs text-gray-600 whitespace-nowrap">{tx.student}</td>
                  <td className="px-5 py-3.5 text-xs text-gray-500 whitespace-nowrap">{tx.fee}</td>
                  <td className="px-5 py-3.5 text-xs font-semibold text-gray-800 whitespace-nowrap font-mono">
                    {tx.amount.toLocaleString()} EGP
                  </td>
                  <td className="px-5 py-3.5 text-xs text-gray-500 whitespace-nowrap">{tx.method}</td>
                  <td className="px-5 py-3.5 text-xs text-gray-400 whitespace-nowrap font-mono">{tx.time}</td>
                  <td className="px-5 py-3.5 whitespace-nowrap">
                    <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold border ${statusStyle[tx.status]}`}>
                      {tx.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
