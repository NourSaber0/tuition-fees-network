import { useState } from 'react'
import {
  CheckCircleIcon, AlertIcon, ClockIcon, ReconcileIcon, EyeIcon,
  ChevronLeftIcon, RefreshIcon, DownloadIcon, ExclamationIcon, UserIcon, CheckIcon,
} from '../components/Icons'

/* ── Types ── */
type ExcStatus = 'Open' | 'Under Investigation' | 'Resolved' | 'Escalated'
type Priority  = 'High' | 'Medium' | 'Low'

interface ReconRow {
  id: string
  institution: string
  institutionType: 'School' | 'University'
  bankAmount: number
  systemAmount: number
  schoolAmount: number
  status: 'Matched' | 'Exception' | 'Pending'
  date: string
  txCount: number
}

interface Exception {
  id: string
  txId: string         // reconciliation row reference
  txRef: string        // individual transaction ID
  institution: string
  institutionType: 'School' | 'University'
  bankAmount: number
  systemAmount: number
  schoolAmount: number
  difference: number
  type: string
  date: string
  excStatus: ExcStatus
  assignedTo: string
  priority: Priority
  txStatus: string
  payMethod: string
  bankRef: string
  bankStatus: string
  settlementDate: string
  feeRef: string
  collectionDate: string
}

/* ── Static data ── */
const reconData: ReconRow[] = [
  { id: 'RC-20260831-001', institution: 'Cairo International School',   institutionType: 'School',     bankAmount: 285600,  systemAmount: 285600,  schoolAmount: 285600,  status: 'Matched',   date: '31 Aug 2026', txCount: 18  },
  { id: 'RC-20260831-002', institution: 'Nasr City Academy',            institutionType: 'School',     bankAmount: 198400,  systemAmount: 198400,  schoolAmount: 189000,  status: 'Exception', date: '31 Aug 2026', txCount: 14  },
  { id: 'RC-20260831-003', institution: 'Cairo University',             institutionType: 'University', bankAmount: 1840000, systemAmount: 1840000, schoolAmount: 1840000, status: 'Matched',   date: '31 Aug 2026', txCount: 142 },
  { id: 'RC-20260831-004', institution: 'Heliopolis Academy',           institutionType: 'School',     bankAmount: 330000,  systemAmount: 330000,  schoolAmount: 330000,  status: 'Matched',   date: '31 Aug 2026', txCount: 21  },
  { id: 'RC-20260831-005', institution: 'Maadi British School',         institutionType: 'School',     bankAmount: 142500,  systemAmount: 151000,  schoolAmount: 142500,  status: 'Exception', date: '31 Aug 2026', txCount: 11  },
  { id: 'RC-20260831-006', institution: 'Ain Shams University',         institutionType: 'University', bankAmount: 2210000, systemAmount: 2210000, schoolAmount: 2210000, status: 'Matched',   date: '31 Aug 2026', txCount: 198 },
  { id: 'RC-20260831-007', institution: 'Alexandria International',     institutionType: 'School',     bankAmount: 214800,  systemAmount: 214800,  schoolAmount: 214800,  status: 'Matched',   date: '31 Aug 2026', txCount: 16  },
  { id: 'RC-20260831-008', institution: 'October STEM School',          institutionType: 'School',     bankAmount: 0,       systemAmount: 0,       schoolAmount: 0,       status: 'Pending',   date: '31 Aug 2026', txCount: 0   },
]

const BASE_EXCEPTIONS: Exception[] = [
  {
    id: 'EXC-001', txId: 'RC-20260831-002', txRef: 'TX-20260831-0011',
    institution: 'Nasr City Academy', institutionType: 'School',
    bankAmount: 198400, systemAmount: 198400, schoolAmount: 189000, difference: 9400,
    type: 'Institution amount mismatch', date: '31 Aug 2026',
    excStatus: 'Under Investigation', assignedTo: 'Rania Mostafa', priority: 'High',
    txStatus: 'Successful', payMethod: 'Bank Transfer',
    bankRef: 'BNK-CIB-84730', bankStatus: 'Settled', settlementDate: '31 Aug 2026',
    feeRef: 'NC-FEE-2026-0831', collectionDate: '31 Aug 2026',
  },
  {
    id: 'EXC-002', txId: 'RC-20260831-005', txRef: 'TX-20260831-0003',
    institution: 'Maadi British School', institutionType: 'School',
    bankAmount: 142500, systemAmount: 151000, schoolAmount: 142500, difference: 8500,
    type: 'System over-reported', date: '31 Aug 2026',
    excStatus: 'Open', assignedTo: '', priority: 'High',
    txStatus: 'Pending', payMethod: 'Bank Transfer',
    bankRef: 'BNK-CIB-84722', bankStatus: 'Pending Settlement', settlementDate: '—',
    feeRef: 'MB-FEE-2026-0831', collectionDate: '31 Aug 2026',
  },
  {
    id: 'EXC-003', txId: 'RC-20260830-008', txRef: 'TX-20260830-0017',
    institution: 'Cairo International School', institutionType: 'School',
    bankAmount: 22000, systemAmount: 22000, schoolAmount: 20000, difference: 2000,
    type: 'Partial institution record', date: '30 Aug 2026',
    excStatus: 'Resolved', assignedTo: 'Hassan Fouad', priority: 'Low',
    txStatus: 'Successful', payMethod: 'Card',
    bankRef: 'BNK-CIB-84618', bankStatus: 'Settled', settlementDate: '30 Aug 2026',
    feeRef: 'CIS-FEE-2026-0830', collectionDate: '30 Aug 2026',
  },
]

const kpis = [
  { title: 'Total Transactions', value: '1,284', Icon: ReconcileIcon,   color: 'bg-[#003087]/10 text-[#003087]' },
  { title: 'Matched',            value: '1,278', Icon: CheckCircleIcon, color: 'bg-green-50 text-green-600'    },
  { title: 'Pending',            value: '3',     Icon: ClockIcon,       color: 'bg-amber-50 text-amber-600'   },
  { title: 'Exceptions',         value: '3',     Icon: AlertIcon,       color: 'bg-red-50 text-red-500'       },
]

const ASSIGNEES = ['Rania Mostafa', 'Hassan Fouad', 'Omar Nabil', 'Mona Khaled', 'Ahmed Hassan']

const resolutionStatuses: ExcStatus[] = ['Open', 'Under Investigation', 'Resolved', 'Escalated']

const excStatusStyle: Record<ExcStatus, string> = {
  'Open':                 'bg-red-50   text-red-700   border-red-200',
  'Under Investigation':  'bg-amber-50 text-amber-700 border-amber-200',
  'Resolved':             'bg-green-50 text-green-700 border-green-200',
  'Escalated':            'bg-purple-50 text-purple-700 border-purple-200',
}

const priorityStyle: Record<Priority, string> = {
  High:   'bg-red-100   text-red-700',
  Medium: 'bg-amber-100 text-amber-700',
  Low:    'bg-gray-100  text-gray-500',
}

/* ── B09 — Exception Resolution ── */
function ExceptionResolution({
  exc,
  onBack,
  onSave,
}: {
  exc: Exception
  onBack: () => void
  onSave: (id: string, status: ExcStatus, assignee: string) => void
}) {
  const [reason,     setReason]     = useState('')
  const [resolution, setResolution] = useState('')
  const [notes,      setNotes]      = useState('')
  const [reference,  setReference]  = useState('')
  const [resStatus,  setResStatus]  = useState<ExcStatus>(exc.excStatus)
  const [assignedTo, setAssignedTo] = useState(exc.assignedTo)
  const [saved,      setSaved]      = useState(false)

  const handleSave = () => {
    onSave(exc.id, resStatus, assignedTo)
    setSaved(true)
    setTimeout(() => setSaved(false), 3500)
  }

  /* Comparison table rows (US-59) */
  const rows: { field: string; payment: string; bank: string; school: string }[] = [
    {
      field:   'Reference',
      payment: exc.txRef,
      bank:    exc.bankRef,
      school:  exc.feeRef,
    },
    {
      field:   'Amount (EGP)',
      payment: exc.systemAmount.toLocaleString(),
      bank:    exc.bankAmount.toLocaleString(),
      school:  exc.schoolAmount.toLocaleString(),
    },
    {
      field:   'Status',
      payment: exc.txStatus,
      bank:    exc.bankStatus,
      school:  'Recorded',
    },
    {
      field:   'Date',
      payment: exc.date,
      bank:    exc.settlementDate,
      school:  exc.collectionDate,
    },
    {
      field:   'Method / Channel',
      payment: exc.payMethod,
      bank:    'Batch settlement',
      school:  'Term fees',
    },
  ]

  const amountMismatch = (val: string) => {
    const n = parseInt(val.replace(/,/g, ''))
    return !isNaN(n) && n !== exc.bankAmount
  }

  return (
    <div className="space-y-4">
      <button onClick={onBack} className="flex items-center gap-1 text-sm text-gray-400 hover:text-gray-700 transition-colors">
        <ChevronLeftIcon className="w-4 h-4" /> Back to Reconciliation
      </button>

      {saved && (
        <div className="flex items-center gap-2 bg-green-50 border border-green-200 rounded-xl px-5 py-3 text-green-800 text-sm font-semibold">
          <CheckCircleIcon className="w-4 h-4" /> Exception resolution saved. Status updated to <span className="ml-1">{resStatus}</span>.
        </div>
      )}

      <div className="grid grid-cols-3 gap-4">
        {/* Exception detail (col-span-2) */}
        <div className="col-span-2 space-y-4">

          {/* Header card */}
          <div className="bg-white rounded-xl border border-[#E8EDF5] p-6">
            <div className="flex items-start justify-between mb-4 pb-4 border-b border-gray-100">
              <div>
                <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1">Reconciliation Exception</div>
                <h2 className="text-lg font-bold text-[#1B2A4A] font-mono">{exc.id}</h2>
                <p className="text-xs text-gray-400 mt-0.5">
                  {exc.date} · {exc.institution}
                  <span className={`ml-1.5 text-[10px] font-semibold px-1.5 py-0.5 rounded ${exc.institutionType === 'University' ? 'bg-[#FEF3E6] text-[#C96B10]' : 'bg-[#EBF1FB] text-[#003087]'}`}>{exc.institutionType}</span>
                </p>
              </div>
              <div className="flex items-center gap-2">
                <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${priorityStyle[exc.priority]} border-transparent`}>{exc.priority} Priority</span>
                <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${excStatusStyle[resStatus]}`}>{resStatus}</span>
              </div>
            </div>

            <div className="flex items-start gap-3 bg-red-50 border border-red-100 rounded-lg px-4 py-3 mb-5">
              <ExclamationIcon className="w-4 h-4 text-red-500 shrink-0 mt-0.5" />
              <div>
                <div className="text-xs font-semibold text-red-700">{exc.type}</div>
                <div className="text-xs text-red-500 mt-0.5">Discrepancy of <strong className="font-mono">EGP {exc.difference.toLocaleString()}</strong> detected between records.</div>
              </div>
            </div>

            {/* US-59: 3-column record comparison table */}
            <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Record Comparison</h3>
            <div className="rounded-xl border border-[#E8EDF5] overflow-hidden">
              <table className="w-full text-xs">
                <thead>
                  <tr className="bg-[#F8FAFD]">
                    <th className="text-left px-4 py-2.5 text-[11px] font-semibold text-gray-400 uppercase tracking-wider w-28">Field</th>
                    <th className="text-left px-4 py-2.5 text-[11px] font-semibold text-[#003087] uppercase tracking-wider border-l border-[#E8EDF5]">Payment Record</th>
                    <th className="text-left px-4 py-2.5 text-[11px] font-semibold text-indigo-600 uppercase tracking-wider border-l border-[#E8EDF5]">Bank Transaction</th>
                    <th className="text-left px-4 py-2.5 text-[11px] font-semibold text-amber-600 uppercase tracking-wider border-l border-[#E8EDF5]">School Fee</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {rows.map(row => {
                    const payMismatch = row.field === 'Amount (EGP)' && amountMismatch(row.payment)
                    const sclMismatch = row.field === 'Amount (EGP)' && amountMismatch(row.school)
                    return (
                      <tr key={row.field} className={row.field === 'Amount (EGP)' ? 'bg-red-50/40' : ''}>
                        <td className="px-4 py-3 font-semibold text-gray-500 whitespace-nowrap">{row.field}</td>
                        <td className={`px-4 py-3 border-l border-[#E8EDF5] font-mono ${payMismatch ? 'text-red-600 font-bold' : 'text-gray-700'}`}>
                          {row.payment}
                          {payMismatch && <span className="ml-2 text-[10px] font-semibold bg-red-100 text-red-600 px-1.5 py-0.5 rounded">Δ {Math.abs(exc.systemAmount - exc.bankAmount).toLocaleString()}</span>}
                        </td>
                        <td className="px-4 py-3 border-l border-[#E8EDF5] font-mono text-gray-700">{row.bank}</td>
                        <td className={`px-4 py-3 border-l border-[#E8EDF5] font-mono ${sclMismatch ? 'text-red-600 font-bold' : 'text-gray-700'}`}>
                          {row.school}
                          {sclMismatch && <span className="ml-2 text-[10px] font-semibold bg-red-100 text-red-600 px-1.5 py-0.5 rounded">Δ {Math.abs(exc.schoolAmount - exc.bankAmount).toLocaleString()}</span>}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </div>

          {/* Resolution form */}
          <div className="bg-white rounded-xl border border-[#E8EDF5] p-6">
            <h3 className="text-sm font-semibold text-[#1B2A4A] mb-4">Resolution Details</h3>
            <div className="space-y-4">
              {/* US-60: Assign To */}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Assign To</label>
                  <div className="relative">
                    <UserIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-300" />
                    <select value={assignedTo} onChange={e => setAssignedTo(e.target.value)}
                      className="w-full pl-8 border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]">
                      <option value="">Unassigned</option>
                      {ASSIGNEES.map(a => <option key={a}>{a}</option>)}
                    </select>
                  </div>
                  {assignedTo && (
                    <p className="text-[11px] text-gray-400 mt-1">Currently assigned to <strong className="text-gray-600">{assignedTo}</strong></p>
                  )}
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Resolution Status</label>
                  <select value={resStatus} onChange={e => setResStatus(e.target.value as ExcStatus)}
                    className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]">
                    {resolutionStatuses.map(s => <option key={s}>{s}</option>)}
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Exception Reason</label>
                  <select value={reason} onChange={e => setReason(e.target.value)}
                    className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]">
                    <option value="">Select reason…</option>
                    <option>Duplicate entry by institution</option>
                    <option>System timing difference</option>
                    <option>Manual adjustment not synced</option>
                    <option>Fee reversal recorded late</option>
                    <option>Data entry error</option>
                    <option>Bank processing delay</option>
                  </select>
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Resolution Action</label>
                  <select value={resolution} onChange={e => setResolution(e.target.value)}
                    className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]">
                    <option value="">Select action…</option>
                    <option>Adjust institution record to match bank</option>
                    <option>Reverse duplicate transaction</option>
                    <option>Manual override — accept bank figure</option>
                    <option>Request reconfirmation from institution</option>
                    <option>Escalate to Operations Manager</option>
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Supporting Reference</label>
                <input type="text" value={reference} onChange={e => setReference(e.target.value)}
                  placeholder="Bank advice number, email thread ID, ticket…"
                  className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]" />
              </div>

              <div>
                <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Investigation Notes</label>
                <textarea value={notes} onChange={e => setNotes(e.target.value)}
                  placeholder="Add investigation notes or context…" rows={3}
                  className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] resize-none" />
              </div>

              <div className="flex items-center justify-end gap-3 pt-1">
                <button onClick={onBack} className="px-4 py-2 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
                  Cancel
                </button>
                <button onClick={handleSave}
                  className="px-5 py-2 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors flex items-center gap-1.5">
                  <CheckIcon className="w-3.5 h-3.5" /> Save Resolution
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Investigation workflow sidebar */}
        <div className="bg-white rounded-xl border border-[#E8EDF5] p-5 self-start">
          <h3 className="text-sm font-semibold text-[#1B2A4A] mb-4">Investigation Workflow</h3>
          <div className="space-y-3">
            {([
              { step: 'Exception Detected', done: true,                                  desc: `Auto-flagged ${exc.date}` },
              { step: 'Records Retrieved',  done: true,                                  desc: 'Payment, Bank, School data' },
              { step: 'Records Compared',   done: true,                                  desc: 'Discrepancy confirmed' },
              { step: 'Assigned',           done: !!assignedTo,                          desc: assignedTo || 'Awaiting assignment' },
              { step: 'Investigate',        done: resStatus === 'Under Investigation' || resStatus === 'Resolved' || resStatus === 'Escalated', desc: 'In progress' },
              { step: 'Resolve',            done: resStatus === 'Resolved',              desc: resStatus === 'Resolved' ? 'Completed' : 'Pending' },
            ] as { step: string; done: boolean; desc: string }[]).map((s, i) => (
              <div key={s.step} className="flex items-start gap-3">
                <div className={`w-5 h-5 rounded-full flex items-center justify-center shrink-0 mt-0.5 text-[10px] font-bold ${s.done ? 'bg-[#003087] text-white' : 'bg-gray-100 text-gray-400'}`}>
                  {s.done ? <CheckIcon className="w-2.5 h-2.5" /> : i + 1}
                </div>
                <div>
                  <div className={`text-xs font-semibold ${s.done ? 'text-gray-800' : 'text-gray-400'}`}>{s.step}</div>
                  <div className="text-[10px] text-gray-400">{s.desc}</div>
                </div>
              </div>
            ))}
          </div>

          <div className="mt-5 pt-4 border-t border-gray-100">
            <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-2">SLA Status</div>
            <div className="flex items-center gap-2">
              <div className="flex-1 h-1.5 bg-gray-100 rounded-full overflow-hidden">
                <div className="h-full bg-amber-400 rounded-full" style={{ width: '45%' }} />
              </div>
              <span className="text-[11px] font-semibold text-amber-600">6h 20m left</span>
            </div>
          </div>

          {assignedTo && (
            <div className="mt-4 pt-4 border-t border-gray-100">
              <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-2">Assigned To</div>
              <div className="flex items-center gap-2">
                <div className="w-6 h-6 rounded-full bg-[#003087] text-white flex items-center justify-center text-[10px] font-bold shrink-0">
                  {assignedTo.split(' ').map(n => n[0]).join('')}
                </div>
                <span className="text-xs font-semibold text-gray-700">{assignedTo}</span>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

/* ── B08 — Reconciliation Dashboard ── */
export default function Reconciliation() {
  const [selectedExc,        setSelectedExc]        = useState<Exception | null>(null)
  const [tab,                setTab]                = useState<'summary' | 'exceptions'>('summary')
  const [showResolved,       setShowResolved]       = useState(false)
  const [excStatusOverrides, setExcStatusOverrides] = useState<Record<string, ExcStatus>>({})
  const [excAssigneeOverrides, setExcAssigneeOverrides] = useState<Record<string, string>>({})

  const effectiveExceptions = BASE_EXCEPTIONS.map(e => ({
    ...e,
    excStatus:  excStatusOverrides[e.id]  ?? e.excStatus,
    assignedTo: excAssigneeOverrides[e.id] ?? e.assignedTo,
  }))

  const handleSave = (id: string, status: ExcStatus, assignee: string) => {
    setExcStatusOverrides(prev  => ({ ...prev,  [id]: status  }))
    setExcAssigneeOverrides(prev => ({ ...prev, [id]: assignee }))
  }

  if (selectedExc) {
    const live = effectiveExceptions.find(e => e.id === selectedExc.id) ?? selectedExc
    return <ExceptionResolution exc={live} onBack={() => setSelectedExc(null)} onSave={handleSave} />
  }

  const visibleExceptions = effectiveExceptions.filter(e =>
    showResolved || e.excStatus !== 'Resolved'
  )
  const resolvedCount = effectiveExceptions.filter(e => e.excStatus === 'Resolved').length

  return (
    <div className="space-y-4">
      {/* KPIs */}
      <div className="grid grid-cols-4 gap-4">
        {kpis.map(({ title, value, Icon, color }) => (
          <div key={title} className="bg-white rounded-xl border border-[#E8EDF5] p-5">
            <div className="flex items-start justify-between">
              <div>
                <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">{title}</p>
                <p className="text-2xl font-bold text-[#1B2A4A] mt-1">{value}</p>
              </div>
              <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${color}`}>
                <Icon className="w-5 h-5" />
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Tabs + actions */}
      <div className="flex items-center gap-1 bg-white rounded-xl border border-[#E8EDF5] p-1.5">
        <button onClick={() => setTab('summary')} className={`px-4 py-1.5 rounded-lg text-xs font-semibold transition-all ${tab === 'summary' ? 'bg-[#003087] text-white' : 'text-gray-500 hover:bg-gray-50'}`}>
          Reconciliation Summary
        </button>
        <button onClick={() => setTab('exceptions')} className={`px-4 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5 ${tab === 'exceptions' ? 'bg-[#003087] text-white' : 'text-gray-500 hover:bg-gray-50'}`}>
          Exception Queue
          <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded-full ${tab === 'exceptions' ? 'bg-white/20 text-white' : 'bg-red-100 text-red-600'}`}>
            {effectiveExceptions.filter(e => e.excStatus !== 'Resolved').length}
          </span>
        </button>
        <div className="flex-1" />
        <button className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-gray-500 hover:bg-gray-50 border border-gray-200 transition-colors">
          <RefreshIcon className="w-3.5 h-3.5" /> Refresh
        </button>
        <button className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-gray-500 hover:bg-gray-50 border border-gray-200 transition-colors">
          <DownloadIcon className="w-3.5 h-3.5" /> Export
        </button>
      </div>

      {/* B08: Reconciliation Summary (US-55, US-56) */}
      {tab === 'summary' && (
        <div className="bg-white rounded-xl border border-[#E8EDF5] overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[#F8FAFD] border-b border-[#E8EDF5]">
                  {['Reference', 'Institution', 'Type', 'Date', 'Tx Count', 'Bank Amount (EGP)', 'System Amount (EGP)', 'Inst. Amount (EGP)', 'Status', ''].map((h, i) => (
                    <th key={i} className="text-left px-5 py-3.5 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {reconData.map(row => (
                  <tr key={row.id} className={`transition-colors ${row.status === 'Exception' ? 'bg-red-50/20 hover:bg-red-50/40' : 'hover:bg-[#F8FAFD]'}`}>
                    <td className="px-5 py-4 font-mono text-xs text-[#003087] font-semibold whitespace-nowrap">{row.id}</td>
                    <td className="px-5 py-4 text-xs text-gray-700">{row.institution}</td>
                    <td className="px-5 py-4 whitespace-nowrap">
                      <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${row.institutionType === 'University' ? 'bg-[#FEF3E6] text-[#C96B10]' : 'bg-[#EBF1FB] text-[#003087]'}`}>{row.institutionType}</span>
                    </td>
                    <td className="px-5 py-4 text-xs text-gray-400 whitespace-nowrap">{row.date}</td>
                    <td className="px-5 py-4 text-xs font-mono font-semibold text-gray-700">{row.txCount}</td>
                    <td className="px-5 py-4 text-xs font-mono font-semibold text-gray-800">{row.bankAmount.toLocaleString()}</td>
                    <td className={`px-5 py-4 text-xs font-mono font-semibold ${row.systemAmount !== row.bankAmount ? 'text-red-600' : 'text-gray-800'}`}>
                      {row.systemAmount.toLocaleString()}
                      {row.systemAmount !== row.bankAmount && <span className="ml-1 text-[10px]">↑</span>}
                    </td>
                    <td className={`px-5 py-4 text-xs font-mono font-semibold ${row.schoolAmount !== row.bankAmount ? 'text-red-600' : 'text-gray-800'}`}>
                      {row.schoolAmount.toLocaleString()}
                      {row.schoolAmount !== row.bankAmount && <span className="ml-1 text-[10px]">↓</span>}
                    </td>
                    <td className="px-5 py-4">
                      <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${
                        row.status === 'Matched'   ? 'bg-green-50 text-green-700 border-green-200' :
                        row.status === 'Exception' ? 'bg-red-50 text-red-700 border-red-200' :
                                                     'bg-amber-50 text-amber-700 border-amber-200'
                      }`}>{row.status}</span>
                    </td>
                    <td className="px-5 py-4">
                      {row.status === 'Exception' && (
                        <button
                          onClick={() => {
                            const exc = effectiveExceptions.find(e => e.txId === row.id)
                            if (exc) { setSelectedExc(exc); setTab('exceptions') }
                          }}
                          className="text-[11px] text-red-600 font-semibold border border-red-200 rounded px-2 py-0.5 hover:bg-red-50 transition-colors"
                        >
                          Investigate
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* B08: Exception Queue (US-57, US-58) */}
      {tab === 'exceptions' && (
        <div className="space-y-3">
          {/* Queue header */}
          <div className="flex items-center justify-between">
            <p className="text-xs text-gray-400">
              {visibleExceptions.length} {showResolved ? 'total' : 'unresolved'} exception{visibleExceptions.length !== 1 ? 's' : ''}
              {!showResolved && resolvedCount > 0 && (
                <span className="ml-1">· {resolvedCount} resolved hidden</span>
              )}
            </p>
            {resolvedCount > 0 && (
              <button onClick={() => setShowResolved(v => !v)}
                className="text-xs font-semibold text-[#003087] hover:underline">
                {showResolved ? 'Hide resolved' : `Show ${resolvedCount} resolved`}
              </button>
            )}
          </div>

          {visibleExceptions.length === 0 && (
            <div className="bg-white rounded-xl border border-[#E8EDF5] p-10 text-center">
              <CheckCircleIcon className="w-10 h-10 text-green-400 mx-auto mb-3" />
              <p className="text-sm font-semibold text-gray-600">No unresolved exceptions</p>
              <p className="text-xs text-gray-400 mt-1">All reconciliation exceptions have been resolved.</p>
            </div>
          )}

          {visibleExceptions.map(exc => {
            const isResolved = exc.excStatus === 'Resolved'
            return (
              <div key={exc.id} className={`bg-white rounded-xl border p-5 ${isResolved ? 'border-gray-100 opacity-70' : 'border-red-100'}`}>
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-start gap-3 min-w-0">
                    <div className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 ${isResolved ? 'bg-green-50' : 'bg-red-50'}`}>
                      {isResolved
                        ? <CheckCircleIcon className="w-5 h-5 text-green-500" />
                        : <ExclamationIcon className="w-5 h-5 text-red-500" />}
                    </div>
                    <div className="min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-mono text-xs font-bold text-[#003087]">{exc.id}</span>
                        <span className="text-gray-200">·</span>
                        <span className="font-mono text-[11px] text-gray-400">{exc.txRef}</span>
                        <span className="text-gray-200">·</span>
                        <span className="text-xs text-gray-400">{exc.date}</span>
                        <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${priorityStyle[exc.priority]}`}>{exc.priority}</span>
                      </div>
                      <div className="flex items-center gap-2 mt-0.5 flex-wrap">
                        <span className="text-sm font-semibold text-gray-800">{exc.institution}</span>
                        <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${exc.institutionType === 'University' ? 'bg-[#FEF3E6] text-[#C96B10]' : 'bg-[#EBF1FB] text-[#003087]'}`}>{exc.institutionType}</span>
                      </div>
                      <div className="text-xs text-red-600 mt-0.5">{exc.type}</div>
                      {exc.assignedTo && (
                        <div className="flex items-center gap-1 mt-1.5 text-[11px] text-gray-400">
                          <UserIcon className="w-3 h-3" />
                          <span>Assigned to <strong className="text-gray-600">{exc.assignedTo}</strong></span>
                        </div>
                      )}
                    </div>
                  </div>

                  <div className="flex items-center gap-3 shrink-0">
                    <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${excStatusStyle[exc.excStatus]}`}>{exc.excStatus}</span>
                    <div className="text-right">
                      <div className="text-[10px] text-gray-400">Difference</div>
                      <div className={`text-base font-bold font-mono ${isResolved ? 'text-gray-400' : 'text-red-600'}`}>{exc.difference.toLocaleString()} EGP</div>
                    </div>
                    {!isResolved && (
                      <button onClick={() => setSelectedExc(exc)}
                        className="px-3 py-2 text-xs font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors flex items-center gap-1.5">
                        <EyeIcon className="w-3.5 h-3.5" /> Resolve
                      </button>
                    )}
                    {isResolved && (
                      <button onClick={() => setSelectedExc(exc)}
                        className="px-3 py-2 text-xs font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors text-gray-500 flex items-center gap-1.5">
                        <EyeIcon className="w-3.5 h-3.5" /> View
                      </button>
                    )}
                  </div>
                </div>

                {/* Amount summary row */}
                <div className="mt-3 pt-3 border-t border-gray-100 grid grid-cols-3 gap-4">
                  {[
                    { label: 'Bank',   value: exc.bankAmount   },
                    { label: 'System', value: exc.systemAmount },
                    { label: 'School', value: exc.schoolAmount },
                  ].map(({ label, value }) => (
                    <div key={label} className="text-xs">
                      <span className="text-gray-400">{label}: </span>
                      <span className={`font-mono font-semibold ${value !== exc.bankAmount ? 'text-red-600' : 'text-gray-700'}`}>
                        {value.toLocaleString()} EGP
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
