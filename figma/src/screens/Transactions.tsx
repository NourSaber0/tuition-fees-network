import { useState, useEffect } from 'react'
import {
  SearchIcon, DownloadIcon, EyeIcon, CheckIcon, XIcon,
  ChevronLeftIcon, ChevronRightIcon, CalendarIcon,
  CheckCircleIcon, ClockIcon, XCircleIcon, AlertIcon,
  UserIcon, CreditCardIcon, RefreshIcon, PlusIcon,
} from '../components/Icons'
import {
  calcPriority, calcPenalty, calcTotalDue, daysFromToday, dueDateLabel,
  formatDueDate, graceEnded, priorityBadge,
} from '../utils/deadline'
import type { Priority } from '../utils/deadline'

/* ── Types ── */
type TxStatus  = 'Successful' | 'Pending' | 'Failed'
type PayMethod = 'CIB Debit Card' | 'CIB Credit Card' | 'Cash' | 'EPP' | 'Bank Transfer'
type PayStep   = 'search' | 'fees' | 'not-found' | 'amount' | 'review' | 'processing' | 'success' | 'failed'

interface PartialData {
  originalAmount: number
  previouslyPaid: number
}

interface Transaction {
  id: string
  institution: string
  institutionType: 'School' | 'University'
  student: string
  fee: string
  amount: number
  method: PayMethod
  status: TxStatus
  bankRef: string
  settlementStatus: string
  reconStatus: string
  timestamp: string
  partial?: PartialData
  dueDate: string
}

interface CustomerFee {
  id: string
  name: string
  originalAmount: number
  paid: number
  remaining: number
  status: 'Unpaid' | 'Partial' | 'Paid'
  eligible: boolean
  dueDate: string
}

interface CustomerRecord {
  name: string
  nid: string
  institution: string
  institutionType: 'School' | 'University'
  grade: string
}

/* ── Transaction data ── */
const transactions: Transaction[] = [
  { id: 'TX-20260831-0001', institution: 'Cairo International School',  institutionType: 'School',     student: 'Ahmed Hassan',   fee: 'Tuition Q3',     amount: 18000, method: 'CIB Debit Card',  status: 'Successful', bankRef: 'BNK-CIB-84720', settlementStatus: 'Settled',     reconStatus: 'Matched',      timestamp: '31 Aug 2026 14:28:04', dueDate: '2026-10-15' },
  { id: 'TX-20260831-0002', institution: 'Cairo University',            institutionType: 'University', student: 'Karim Nour',     fee: 'Semester Fee',   amount: 8000,  method: 'Bank Transfer',   status: 'Pending',    bankRef: 'BNK-CIB-84721', settlementStatus: 'Pending',     reconStatus: 'Pending',      timestamp: '31 Aug 2026 14:25:11', dueDate: '2026-09-15', partial: { originalAmount: 12500, previouslyPaid: 0 } },
  { id: 'TX-20260831-0003', institution: 'Maadi British School',        institutionType: 'School',     student: 'Sara Mohamed',   fee: 'Activity Fee',   amount: 1200,  method: 'CIB Debit Card',  status: 'Pending',    bankRef: 'BNK-CIB-84722', settlementStatus: 'Pending',     reconStatus: 'Pending',      timestamp: '31 Aug 2026 14:22:11', dueDate: '2026-09-10', partial: { originalAmount: 2500, previouslyPaid: 0 } },
  { id: 'TX-20260831-0004', institution: 'Heliopolis Academy',          institutionType: 'School',     student: 'Omar Ali',       fee: 'Tuition Q3',     amount: 22000, method: 'CIB Credit Card', status: 'Failed',     bankRef: 'BNK-CIB-84723', settlementStatus: 'Not Settled', reconStatus: 'Not Required', timestamp: '31 Aug 2026 14:18:33', dueDate: '2026-09-05' },
  { id: 'TX-20260831-0005', institution: 'Ain Shams University',        institutionType: 'University', student: 'Nadia Saleh',    fee: 'Lab Fee',        amount: 3800,  method: 'CIB Credit Card', status: 'Successful', bankRef: 'BNK-CIB-84724', settlementStatus: 'Settled',     reconStatus: 'Matched',      timestamp: '31 Aug 2026 14:12:44', dueDate: '2026-10-01' },
  { id: 'TX-20260831-0006', institution: 'Nasr City Academy',           institutionType: 'School',     student: 'Fatma Khalil',   fee: 'Bus Fee',        amount: 3200,  method: 'CIB Debit Card',  status: 'Successful', bankRef: 'BNK-CIB-84725', settlementStatus: 'Settled',     reconStatus: 'Matched',      timestamp: '31 Aug 2026 14:15:50', dueDate: '2026-10-01' },
  { id: 'TX-20260831-0007', institution: 'Cairo International School',  institutionType: 'School',     student: 'Mostafa Ahmed',  fee: 'Tuition Q3',     amount: 18000, method: 'EPP',             status: 'Successful', bankRef: 'BNK-CIB-84726', settlementStatus: 'Settled',     reconStatus: 'Matched',      timestamp: '31 Aug 2026 14:09:22', dueDate: '2026-10-15' },
  { id: 'TX-20260831-0008', institution: 'American University in Cairo',institutionType: 'University', student: 'Hassan Fouad',   fee: 'Tuition Q3',     amount: 48000, method: 'CIB Credit Card', status: 'Successful', bankRef: 'BNK-CIB-84727', settlementStatus: 'Settled',     reconStatus: 'Matched',      timestamp: '31 Aug 2026 14:05:07', dueDate: '2026-10-01' },
  { id: 'TX-20260831-0009', institution: 'Alexandria International',    institutionType: 'School',     student: 'Layla Hassan',   fee: 'Registration',   amount: 5000,  method: 'Cash',            status: 'Successful', bankRef: 'BNK-CIB-84728', settlementStatus: 'Settled',     reconStatus: 'Matched',      timestamp: '31 Aug 2026 14:01:07', dueDate: '2026-10-01' },
  { id: 'TX-20260831-0010', institution: 'Heliopolis Academy',          institutionType: 'School',     student: 'Nabil Ibrahim',  fee: 'Lab Fee',        amount: 1800,  method: 'CIB Debit Card',  status: 'Successful', bankRef: 'BNK-CIB-84729', settlementStatus: 'Settled',     reconStatus: 'Matched',      timestamp: '31 Aug 2026 13:55:44', dueDate: '2026-10-01' },
  { id: 'TX-20260831-0011', institution: 'Nasr City Academy',           institutionType: 'School',     student: 'Rania Sherif',   fee: 'Tuition Q3',     amount: 15500, method: 'Bank Transfer',   status: 'Successful', bankRef: 'BNK-CIB-84730', settlementStatus: 'Settled',     reconStatus: 'Exception',    timestamp: '31 Aug 2026 13:48:16', dueDate: '2026-10-01' },
  { id: 'TX-20260831-0012', institution: 'Cairo University',            institutionType: 'University', student: 'Rania Mostafa',  fee: 'Enrollment Fee', amount: 8500,  method: 'CIB Credit Card', status: 'Failed',     bankRef: 'BNK-CIB-84731', settlementStatus: 'Not Settled', reconStatus: 'Not Required', timestamp: '31 Aug 2026 13:40:29', dueDate: '2026-08-28' },
]

/* ── Customer lookup data (US-42/43) ── */
const DEMO_CUSTOMERS: Record<string, { customer: CustomerRecord; fees: CustomerFee[] }> = {
  '29901011234567': {
    customer: { name: 'Ahmed Hassan', nid: '29901011234567', institution: 'Cairo International School', institutionType: 'School', grade: 'Grade 10' },
    fees: [
      { id: 'FEE-AH-001', name: 'Tuition — Term 1 2026/27', originalAmount: 18000, paid: 5000, remaining: 13000, status: 'Partial', eligible: true,  dueDate: '2026-09-12' },
      { id: 'FEE-AH-002', name: 'Activity Fee — Q1 2026',   originalAmount: 2500,  paid: 0,    remaining: 2500,  status: 'Unpaid',  eligible: true,  dueDate: '2026-09-20' },
      { id: 'FEE-AH-003', name: 'Transport — Term 1',       originalAmount: 3600,  paid: 3600, remaining: 0,     status: 'Paid',    eligible: false, dueDate: '2026-08-15' },
    ],
  },
  '30205054321098': {
    customer: { name: 'Sara Mohamed', nid: '30205054321098', institution: 'Maadi British School', institutionType: 'School', grade: 'Grade 7' },
    fees: [
      { id: 'FEE-SM-001', name: 'Tuition — Term 1 2026/27', originalAmount: 12400, paid: 0, remaining: 12400, status: 'Unpaid', eligible: true, dueDate: '2026-09-07' },
      { id: 'FEE-SM-002', name: 'Bus Fee — Term 1',         originalAmount: 3200,  paid: 0, remaining: 3200,  status: 'Unpaid', eligible: true, dueDate: '2026-09-30' },
    ],
  },
}

/* ── Style maps ── */
const statusStyle: Record<TxStatus, string> = {
  Successful: 'bg-green-50 text-green-700 border-green-200',
  Pending:    'bg-amber-50 text-amber-700 border-amber-200',
  Failed:     'bg-red-50   text-red-700   border-red-200',
}

const statusTabs: { label: string; count: number }[] = [
  { label: 'All',        count: 1284 },
  { label: 'Successful', count: 1201 },
  { label: 'Pending',    count: 35   },
  { label: 'Failed',     count: 48   },
]

/* ── Helpers ── */
function maskNID(nid: string): string {
  return nid.slice(0, 3) + '•••••••' + nid.slice(-4)
}

/* ── Shared components ── */
function StatusBadge({ status }: { status: string }) {
  const style = statusStyle[status as TxStatus] ?? 'bg-gray-100 text-gray-600 border-gray-200'
  return <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold border ${style}`}>{status}</span>
}

function PriorityBadge({ priority }: { priority: Priority }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold border ${priorityBadge[priority]}`}>
      {priority}
    </span>
  )
}

function txOutstanding(tx: Transaction): number {
  if (tx.status === 'Successful') return 0
  if (tx.partial) return tx.partial.originalAmount - tx.partial.previouslyPaid - tx.amount
  return tx.amount
}

function txPriority(tx: Transaction): Priority {
  return calcPriority(tx.dueDate, txOutstanding(tx))
}

function txPenalty(tx: Transaction): number {
  return calcPenalty(txOutstanding(tx), tx.dueDate)
}

/* ── Transaction Detail (US-40, US-41, US-49) ── */
function TransactionDetail({ tx, onBack }: { tx: Transaction; onBack: () => void }) {
  const displayStatus = tx.status

  const remaining = tx.partial
    ? tx.partial.originalAmount - tx.partial.previouslyPaid - tx.amount
    : null

  const outstanding = txOutstanding(tx)
  const priority    = calcPriority(tx.dueDate, outstanding)
  const penalty     = calcPenalty(outstanding, tx.dueDate)

  return (
    <div className="space-y-4">
      <button onClick={onBack} className="flex items-center gap-1 text-sm text-gray-400 hover:text-gray-700 transition-colors">
        <ChevronLeftIcon className="w-4 h-4" /> Back to Transactions
      </button>

      <div className="grid grid-cols-3 gap-4">
        {/* Main detail card */}
        <div className="col-span-2 bg-white rounded-xl border border-[#E8EDF5] p-6">
          <div className="flex items-start justify-between mb-5 pb-5 border-b border-gray-100">
            <div>
              <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1">Transaction Detail</div>
              <h2 className="text-lg font-bold text-[#1B2A4A] font-mono">{tx.id}</h2>
              <p className="text-xs text-gray-400 mt-0.5">{tx.timestamp}</p>
            </div>
            <StatusBadge status={displayStatus} />
          </div>

          <div className="grid grid-cols-2 gap-x-10 gap-y-5">
            {([
              ['Institution',           tx.institution],
              ['Institution Type',      tx.institutionType],
              ['Student',               tx.student],
              ['Fee Type',              tx.fee],
              ['Amount',                `EGP ${tx.amount.toLocaleString()}`],
              ['Payment Method',        tx.method],
              ['Bank Reference',        tx.bankRef],
              ['Settlement Status',     tx.settlementStatus],
              ['Reconciliation Status', tx.reconStatus],
            ] as [string, string][]).map(([k, v]) => (
              <div key={k}>
                <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1">{k}</div>
                <div className={`text-sm ${
                  k === 'Amount'          ? 'font-bold text-[#1B2A4A] font-mono text-base' :
                  k === 'Bank Reference'  ? 'font-mono text-[#003087]' : 'text-gray-800'
                }`}>{v}</div>
              </div>
            ))}
          </div>

          {/* US-40: Partial payment breakdown */}
          {tx.partial && (
            <div className="mt-6 pt-5 border-t border-gray-100">
              <div className="flex items-center gap-2 mb-3">
                <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">Payment Breakdown</div>
                <span className="text-[10px] font-semibold px-2 py-0.5 rounded border bg-amber-50 text-amber-700 border-amber-200">Partial Payment</span>
              </div>
              <div className="bg-[#F8FAFD] rounded-xl border border-[#E8EDF5] p-4 space-y-3">
                <div className="flex justify-between text-sm">
                  <span className="text-gray-500">Original Fee Amount</span>
                  <span className="font-mono font-semibold text-gray-700">EGP {tx.partial.originalAmount.toLocaleString()}</span>
                </div>
                {tx.partial.previouslyPaid > 0 && (
                  <div className="flex justify-between text-sm">
                    <span className="text-gray-500">Previously Paid</span>
                    <span className="font-mono font-semibold text-green-700">− EGP {tx.partial.previouslyPaid.toLocaleString()}</span>
                  </div>
                )}
                <div className="flex justify-between text-sm border-t border-gray-100 pt-3">
                  <span className="text-gray-500">This Payment</span>
                  <span className="font-mono font-semibold text-[#003087]">EGP {tx.amount.toLocaleString()}</span>
                </div>
                <div className="flex justify-between text-sm border-t border-gray-100 pt-3">
                  <span className="font-semibold text-gray-700">Remaining Balance</span>
                  <span className="font-mono font-bold text-amber-700">EGP {(remaining ?? 0).toLocaleString()}</span>
                </div>
                {/* Progress bar */}
                <div className="pt-1">
                  <div className="flex justify-between text-[10px] text-gray-400 mb-1.5">
                    <span>Paid</span>
                    <span>{Math.round(((tx.partial.previouslyPaid + tx.amount) / tx.partial.originalAmount) * 100)}%</span>
                  </div>
                  <div className="w-full h-2 bg-gray-100 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-[#003087] rounded-full transition-all"
                      style={{ width: `${Math.min(100, Math.round(((tx.partial.previouslyPaid + tx.amount) / tx.partial.originalAmount) * 100))}%` }}
                    />
                  </div>
                </div>
              </div>
            </div>
          )}
          {/* Payment Deadline section */}
          <div className="mt-6 pt-5 border-t border-gray-100">
            <div className="flex items-center gap-2 mb-3">
              <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">Payment Deadline</div>
              {priority === 'OVERDUE' && (
                <span className="text-[11px] font-bold text-red-700 bg-red-50 border border-red-200 rounded px-2 py-0.5">
                  ⚠ Overdue
                </span>
              )}
            </div>
            <div className={`rounded-xl border overflow-hidden ${priority === 'OVERDUE' ? 'border-red-200' : priority === 'URGENT' ? 'border-orange-200' : 'border-[#E8EDF5]'}`}>
              {/* Header row — due date + priority */}
              <div className={`flex items-center justify-between px-4 py-3 ${priority === 'OVERDUE' ? 'bg-red-50' : priority === 'URGENT' ? 'bg-orange-50' : 'bg-[#F8FAFD]'}`}>
                <div>
                  <div className="text-sm font-semibold text-[#1B2A4A]">Due: {formatDueDate(tx.dueDate)}</div>
                  <div className={`text-xs mt-0.5 ${priority === 'OVERDUE' ? 'text-red-600 font-semibold' : priority === 'URGENT' ? 'text-orange-600 font-semibold' : 'text-gray-400'}`}>
                    {dueDateLabel(tx.dueDate)}
                    {graceEnded(tx.dueDate) && <span className="ml-2">· Grace period ended</span>}
                  </div>
                </div>
                <PriorityBadge priority={priority} />
              </div>

              {/* Balance breakdown */}
              <div className="px-4 py-3 space-y-2.5 bg-white">
                <div className="flex justify-between text-xs">
                  <span className="text-gray-500">Original Outstanding</span>
                  <span className="font-mono font-semibold text-gray-700">
                    EGP {outstanding.toLocaleString()}
                  </span>
                </div>
                {penalty > 0 && (
                  <div className="flex justify-between text-xs">
                    <span className="text-red-600 font-semibold">Late Penalty (5%)</span>
                    <span className="font-mono font-bold text-red-600">+ EGP {penalty.toLocaleString()}</span>
                  </div>
                )}
                <div className={`flex justify-between text-sm border-t pt-2.5 ${penalty > 0 ? 'border-red-100' : 'border-gray-100'}`}>
                  <span className="font-semibold text-gray-700">Total Amount Due</span>
                  <span className={`font-mono font-bold ${penalty > 0 ? 'text-red-700' : outstanding === 0 ? 'text-green-700' : 'text-[#1B2A4A]'}`}>
                    EGP {calcTotalDue(outstanding, tx.dueDate).toLocaleString()}
                  </span>
                </div>
                {priority === 'OVERDUE' && (
                  <p className="text-[11px] text-red-600 bg-red-50 border border-red-100 rounded px-2.5 py-2 leading-relaxed">
                    Payment deadline exceeded. A 5% late penalty has been applied to the outstanding balance.
                  </p>
                )}
                {priority === 'PAID' && (
                  <p className="text-[11px] text-green-700 font-medium">Fully paid — no outstanding balance.</p>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* Payment timeline */}
        <div className="bg-white rounded-xl border border-[#E8EDF5] p-5">
          <h3 className="text-sm font-semibold text-[#1B2A4A] mb-4">Payment Timeline</h3>
          <div className="space-y-4">
            {[
              { label: 'Payment Initiated', time: '14:28:01', done: true },
              { label: 'Bank Authorisation', time: '14:28:03', done: true },
              { label: 'Payment Captured',  time: '14:28:04', done: displayStatus === 'Successful' },
              { label: 'Settlement',        time: tx.settlementStatus === 'Settled' ? '14:30:00' : '—', done: tx.settlementStatus === 'Settled' },
              { label: 'Reconciliation',    time: tx.reconStatus === 'Matched' ? '15:00:00' : '—',      done: tx.reconStatus === 'Matched' },
            ].map(step => (
              <div key={step.label} className="flex items-start gap-3">
                <div className={`w-5 h-5 rounded-full flex items-center justify-center shrink-0 mt-0.5 ${step.done ? 'bg-green-500' : 'bg-gray-200'}`}>
                  {step.done && <CheckCircleIcon className="w-3 h-3 text-white" />}
                </div>
                <div>
                  <div className="text-xs font-semibold text-gray-700">{step.label}</div>
                  <div className="text-[11px] text-gray-400 font-mono">{step.time}</div>
                </div>
              </div>
            ))}
          </div>
          <div className="mt-5 pt-4 border-t border-gray-100 space-y-2">
            <div className="text-[11px] text-gray-400"><strong className="text-gray-600">Channel:</strong> CIB Online Banking</div>
            <div className="text-[11px] text-gray-400">
              <strong className="text-gray-600">Idempotency Key:</strong>
              <span className="font-mono block text-[10px] mt-0.5">{tx.bankRef}-KEY</span>
            </div>
          </div>
        </div>
      </div>

    </div>
  )
}

/* ── Payment Workflow (US-42 – US-47) ── */
function WorkflowProgress({ step }: { step: PayStep }) {
  const steps = ['Search', 'Select Fees', 'Payment', 'Review', 'Done']
  const activeIdx =
    step === 'search'                    ? 0 :
    step === 'fees' || step === 'not-found' ? 1 :
    step === 'amount'                    ? 2 :
    step === 'review'                    ? 3 : 4

  return (
    <div className="flex items-center gap-0 mb-6">
      {steps.map((s, i) => (
        <div key={s} className="flex items-center flex-1 last:flex-none">
          <div className="flex items-center gap-1.5 shrink-0">
            <div className={`w-6 h-6 rounded-full flex items-center justify-center text-[11px] font-bold transition-all ${
              i < activeIdx  ? 'bg-[#003087] text-white' :
              i === activeIdx ? 'bg-[#003087] text-white ring-4 ring-[#003087]/20' :
                               'bg-gray-100 text-gray-400'
            }`}>
              {i < activeIdx ? <CheckIcon className="w-3 h-3" /> : i + 1}
            </div>
            <span className={`text-xs font-semibold whitespace-nowrap ${i === activeIdx ? 'text-[#003087]' : i < activeIdx ? 'text-gray-600' : 'text-gray-400'}`}>{s}</span>
          </div>
          {i < steps.length - 1 && (
            <div className={`flex-1 h-px mx-2 ${i < activeIdx ? 'bg-[#003087]' : 'bg-gray-200'}`} />
          )}
        </div>
      ))}
    </div>
  )
}

function PaymentWorkflow({ onDone }: { onDone: () => void }) {
  const [step,        setStep]        = useState<PayStep>('search')
  const [nid,         setNid]         = useState('')
  const [nidError,    setNidError]    = useState('')
  const [customer,    setCustomer]    = useState<CustomerRecord | null>(null)
  const [fees,        setFees]        = useState<CustomerFee[]>([])
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [payAmount,   setPayAmount]   = useState('')
  const [amtError,    setAmtError]    = useState('')
  const [payMethod,     setPayMethod]     = useState<PayMethod>('CIB Debit Card')
  const [creditPayType, setCreditPayType] = useState<'full' | 'epp' | null>(null)
  const [simulateFail,  setSimulateFail]  = useState(false)
  const [eppTenor,      setEppTenor]      = useState<3 | 6 | 12 | 18 | null>(null)
  const [receiptRef,  setReceiptRef]  = useState('')

  /* Auto-advance processing step */
  useEffect(() => {
    if (step !== 'processing') return
    const t = setTimeout(() => {
      if (simulateFail) {
        setStep('failed')
      } else {
        setReceiptRef(`TX-${Date.now().toString().slice(-10)}`)
        setStep('success')
      }
    }, 2200)
    return () => clearTimeout(t)
  }, [step, simulateFail])

  /* Derived values */
  const selectedFees    = fees.filter(f => selectedIds.has(f.id))
  const totalRemaining  = selectedFees.reduce((s, f) => s + f.remaining, 0)
  const payAmountNum    = parseFloat(payAmount) || 0
  const isPartial       = payAmountNum > 0 && payAmountNum < totalRemaining
  const afterPayment    = totalRemaining - payAmountNum

  const handleNidSearch = () => {
    const trimmed = nid.trim()
    if (!/^\d{14}$/.test(trimmed)) {
      setNidError('National ID must be exactly 14 digits.')
      return
    }
    setNidError('')
    const record = DEMO_CUSTOMERS[trimmed]
    if (record) {
      setCustomer(record.customer)
      setFees(record.fees)
      setSelectedIds(new Set(record.fees.filter(f => f.eligible).map(f => f.id)))
      setStep('fees')
    } else {
      setCustomer(null)
      setStep('not-found')
    }
  }

  const handleAmountContinue = () => {
    if (!payAmount || payAmountNum <= 0) { setAmtError('Enter a payment amount.'); return }
    if (payAmountNum > totalRemaining)   { setAmtError(`Amount cannot exceed the remaining balance of EGP ${totalRemaining.toLocaleString()}.`); return }
    setAmtError('')
    setStep('review')
  }

  const resetWorkflow = () => {
    setStep('search'); setNid(''); setNidError('')
    setCustomer(null); setFees([]); setSelectedIds(new Set())
    setPayAmount(''); setAmtError(''); setSimulateFail(false)
    setCreditPayType(null); setEppTenor(null)
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3">
        <button onClick={onDone} className="flex items-center gap-1 text-sm text-gray-400 hover:text-gray-700 transition-colors">
          <ChevronLeftIcon className="w-4 h-4" /> Back to Transactions
        </button>
        <span className="text-[#DDE4EE]">|</span>
        <span className="text-sm font-semibold text-[#003087]">Process Customer Payment</span>
      </div>

      <div className="bg-white rounded-xl border border-[#E8EDF5] p-6">
        {step !== 'processing' && step !== 'success' && step !== 'failed' && (
          <WorkflowProgress step={step} />
        )}

        {/* ── Step 1: NID Search (US-42) ── */}
        {step === 'search' && (
          <div className="max-w-md mx-auto py-4">
            <div className="text-center mb-6">
              <div className="w-12 h-12 rounded-xl bg-[#003087]/10 flex items-center justify-center mx-auto mb-3">
                <UserIcon className="w-6 h-6 text-[#003087]" />
              </div>
              <h3 className="text-base font-bold text-[#1B2A4A]">Customer Identification</h3>
              <p className="text-sm text-gray-400 mt-1">Enter the customer's National ID to retrieve their fee information.</p>
            </div>
            <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">National ID *</label>
            <div className="flex gap-2">
              <input
                type="text"
                maxLength={14}
                placeholder="14-digit National ID"
                value={nid}
                onChange={e => { setNid(e.target.value.replace(/\D/g, '')); setNidError('') }}
                onKeyDown={e => e.key === 'Enter' && handleNidSearch()}
                className={`flex-1 border rounded-lg px-3 py-2.5 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] tracking-widest ${nidError ? 'border-red-400 bg-red-50' : 'border-[#DDE3EF]'}`}
              />
              <button onClick={handleNidSearch} className="px-4 py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors flex items-center gap-1.5">
                <SearchIcon className="w-3.5 h-3.5" /> Search
              </button>
            </div>
            {nidError && <p className="text-xs text-red-500 mt-1.5">{nidError}</p>}
            <div className="mt-3 flex items-start gap-1.5 text-[11px] text-gray-400">
              <span className="shrink-0 mt-0.5">🔒</span>
              National ID is treated as sensitive information and will be masked after retrieval.
            </div>
            <div className="mt-4 bg-[#F4F6F9] rounded-lg px-4 py-3 text-xs text-gray-500">
              <span className="font-semibold text-gray-600">Demo NIDs: </span>
              <span className="font-mono">29901011234567</span> · <span className="font-mono">30205054321098</span>
            </div>
          </div>
        )}

        {/* ── Step 1b: Not found ── */}
        {step === 'not-found' && (
          <div className="max-w-md mx-auto py-4 text-center">
            <div className="w-12 h-12 rounded-xl bg-red-50 flex items-center justify-center mx-auto mb-3">
              <XCircleIcon className="w-6 h-6 text-red-500" />
            </div>
            <h3 className="text-base font-bold text-[#1B2A4A] mb-1">Customer Not Found</h3>
            <p className="text-sm text-gray-500 mb-1">No customer record was found for National ID</p>
            <p className="font-mono text-sm text-[#003087] mb-5">{maskNID(nid)}</p>
            <p className="text-xs text-gray-400 mb-5">Verify the National ID and try again, or contact your supervisor if the issue persists.</p>
            <button onClick={() => { setStep('search'); setNid('') }} className="px-5 py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors">
              Search Again
            </button>
          </div>
        )}

        {/* ── Step 2: Customer + Fee Selection (US-43, US-44) ── */}
        {step === 'fees' && customer && (
          <div className="space-y-4">
            {/* Customer card */}
            <div className="bg-[#F4F6F9] rounded-xl border border-[#E8EDF5] p-4 flex items-center gap-4">
              <div className="w-10 h-10 rounded-full bg-[#003087] text-white flex items-center justify-center font-bold text-sm shrink-0">
                {customer.name.split(' ').map(n => n[0]).join('').slice(0, 2)}
              </div>
              <div className="flex-1 min-w-0">
                <div className="font-semibold text-[#1B2A4A] text-sm">{customer.name}</div>
                <div className="text-xs text-gray-400">{customer.institution} · {customer.grade}</div>
              </div>
              <div className="text-right">
                <div className="text-[10px] text-gray-400 font-semibold uppercase tracking-wider">National ID</div>
                <div className="text-xs font-mono text-[#003087]">{maskNID(customer.nid)}</div>
              </div>
            </div>

            {/* Fee table */}
            <div>
              <div className="flex items-center justify-between mb-2">
                <h4 className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">Outstanding Fees</h4>
                <button onClick={() => {
                  const eligibleIds = fees.filter(f => f.eligible).map(f => f.id)
                  setSelectedIds(prev => prev.size === eligibleIds.length ? new Set() : new Set(eligibleIds))
                }} className="text-xs text-[#003087] font-semibold hover:underline">
                  {selectedIds.size === fees.filter(f => f.eligible).length ? 'Deselect All' : 'Select All'}
                </button>
              </div>
              <div className="rounded-xl border border-[#E8EDF5] overflow-hidden">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-[#F8FAFD]">
                      <th className="w-10 px-4 py-2.5" />
                      {['Fee', 'Original', 'Paid', 'Remaining', 'Status', 'Due Date', 'Priority'].map(h => (
                        <th key={h} className="text-left px-4 py-2.5 text-[11px] font-semibold text-gray-400 uppercase tracking-wider">{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-50">
                    {fees.map(fee => (
                      <tr key={fee.id} className={`transition-colors ${fee.eligible ? 'hover:bg-[#F8FAFD]' : 'opacity-50'} ${selectedIds.has(fee.id) ? 'bg-[#EBF1FB]' : ''}`}>
                        <td className="px-4 py-3 text-center">
                          {fee.eligible ? (
                            <input type="checkbox" checked={selectedIds.has(fee.id)}
                              onChange={() => setSelectedIds(prev => {
                                const next = new Set(prev)
                                next.has(fee.id) ? next.delete(fee.id) : next.add(fee.id)
                                return next
                              })}
                              className="w-4 h-4 rounded border-gray-300 text-[#003087] cursor-pointer accent-[#003087]"
                            />
                          ) : (
                            <span title="Already paid" className="text-gray-300 text-xs">✓</span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-xs text-gray-700 font-medium">{fee.name}</td>
                        <td className="px-4 py-3 text-xs font-mono text-gray-600">EGP {fee.originalAmount.toLocaleString()}</td>
                        <td className="px-4 py-3 text-xs font-mono text-green-700">{fee.paid > 0 ? `EGP ${fee.paid.toLocaleString()}` : '—'}</td>
                        <td className="px-4 py-3 text-xs font-mono font-semibold text-gray-800">
                          {fee.remaining > 0 ? `EGP ${fee.remaining.toLocaleString()}` : <span className="text-green-600">Settled</span>}
                        </td>
                        <td className="px-4 py-3">
                          <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${
                            fee.status === 'Paid'    ? 'bg-green-50 text-green-700 border-green-200' :
                            fee.status === 'Partial' ? 'bg-amber-50 text-amber-700 border-amber-200' :
                                                       'bg-gray-100 text-gray-500 border-gray-200'
                          }`}>{fee.status}</span>
                        </td>
                        <td className="px-4 py-3 whitespace-nowrap">
                          <div className="text-xs font-mono text-gray-600">{formatDueDate(fee.dueDate)}</div>
                          <div className={`text-[10px] mt-0.5 ${daysFromToday(fee.dueDate) < 0 ? 'text-red-500' : daysFromToday(fee.dueDate) === 0 ? 'text-orange-600 font-semibold' : 'text-gray-400'}`}>
                            {dueDateLabel(fee.dueDate)}
                          </div>
                        </td>
                        <td className="px-4 py-3 whitespace-nowrap">
                          <PriorityBadge priority={calcPriority(fee.dueDate, fee.remaining)} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {/* Summary + continue */}
            <div className="flex items-center justify-between bg-[#F4F6F9] rounded-xl border border-[#E8EDF5] px-5 py-3.5">
              <div className="text-sm">
                <span className="text-gray-500">{selectedIds.size} fee{selectedIds.size !== 1 ? 's' : ''} selected · </span>
                <span className="font-semibold text-[#1B2A4A]">Total: EGP {selectedFees.reduce((s, f) => s + f.remaining, 0).toLocaleString()}</span>
              </div>
              <button
                disabled={selectedIds.size === 0}
                onClick={() => { setPayAmount(String(totalRemaining)); setStep('amount') }}
                className="px-4 py-2 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors disabled:opacity-40"
              >
                Continue to Payment →
              </button>
            </div>
          </div>
        )}

        {/* ── Step 3: Payment Amount (US-46) ── */}
        {step === 'amount' && customer && (
          <div className="max-w-lg mx-auto space-y-5 py-2">
            <div>
              <h3 className="text-base font-bold text-[#1B2A4A] mb-1">Payment Amount</h3>
              <p className="text-sm text-gray-400">Confirm the amount to pay. Enter a lower amount to make a partial payment.</p>
            </div>

            {/* Selected fees summary */}
            <div className="rounded-xl border border-[#E8EDF5] overflow-hidden">
              {selectedFees.map((fee, i) => (
                <div key={fee.id} className={`flex items-center justify-between px-4 py-3 text-xs ${i > 0 ? 'border-t border-gray-50' : ''}`}>
                  <span className="text-gray-700 font-medium">{fee.name}</span>
                  <span className="font-mono font-semibold text-gray-800">EGP {fee.remaining.toLocaleString()}</span>
                </div>
              ))}
              <div className="flex items-center justify-between px-4 py-3 border-t border-[#E8EDF5] bg-[#F8FAFD]">
                <span className="text-xs font-semibold text-gray-600">Fee Balance</span>
                <span className="text-sm font-bold font-mono text-[#1B2A4A]">EGP {totalRemaining.toLocaleString()}</span>
              </div>
            </div>

            {/* Amount input */}
            <div>
              <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">Amount to Pay (EGP) *</label>
              <div className="flex gap-2">
                <input
                  type="number"
                  min="1"
                  max={totalRemaining}
                  value={payAmount}
                  onChange={e => { setPayAmount(e.target.value); setAmtError('') }}
                  className={`flex-1 border rounded-lg px-3 py-2.5 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] ${amtError ? 'border-red-400 bg-red-50' : 'border-[#DDE3EF]'}`}
                  placeholder="Enter amount…"
                />
                <button onClick={() => { setPayAmount(String(totalRemaining)); setAmtError('') }}
                  className="px-3 py-2.5 text-xs font-semibold border border-[#DDE3EF] rounded-lg hover:bg-gray-50 text-gray-600 transition-colors whitespace-nowrap">
                  Pay Full
                </button>
              </div>
              {amtError && <p className="text-xs text-red-500 mt-1.5">{amtError}</p>}
            </div>

            {/* Live breakdown */}
            {payAmountNum > 0 && (
              <div className="bg-[#F8FAFD] rounded-xl border border-[#E8EDF5] p-4 space-y-2.5">
                <div className="flex justify-between text-sm">
                  <span className="text-gray-500">Fee Balance</span>
                  <span className="font-mono text-gray-700">EGP {totalRemaining.toLocaleString()}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-gray-500">Amount Being Paid</span>
                  <span className="font-mono font-semibold text-[#003087]">EGP {payAmountNum.toLocaleString()}</span>
                </div>
                <div className={`flex justify-between text-sm border-t border-gray-100 pt-2.5 ${payAmountNum > totalRemaining ? 'text-red-600' : ''}`}>
                  <span className="font-semibold text-gray-700">Remaining After Payment</span>
                  <span className="font-mono font-bold">{payAmountNum > totalRemaining ? '—' : `EGP ${afterPayment.toLocaleString()}`}</span>
                </div>
                {isPartial && (
                  <div className="flex items-center gap-1.5 text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 mt-1">
                    <ClockIcon className="w-3.5 h-3.5 shrink-0" />
                    Partial payment — EGP {afterPayment.toLocaleString()} will remain as outstanding balance.
                  </div>
                )}
                {payAmountNum > totalRemaining && (
                  <div className="flex items-center gap-1.5 text-xs text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2 mt-1">
                    <AlertIcon className="w-3.5 h-3.5 shrink-0" />
                    Amount exceeds the remaining fee balance.
                  </div>
                )}
              </div>
            )}

            <div className="flex gap-3 pt-1">
              <button onClick={() => setStep('fees')} className="px-4 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">← Back</button>
              <button onClick={handleAmountContinue} className="flex-1 py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors">
                Continue to Review →
              </button>
            </div>
          </div>
        )}

        {/* ── Step 4: Review + Payment Method (US-45) ── */}
        {step === 'review' && customer && (
          <div className="max-w-lg mx-auto space-y-5 py-2">
            <div>
              <h3 className="text-base font-bold text-[#1B2A4A] mb-1">Review & Confirm</h3>
              <p className="text-sm text-gray-400">Verify the details before processing the payment.</p>
            </div>

            <div className="rounded-xl border border-[#E8EDF5] overflow-hidden divide-y divide-gray-50">
              <div className="px-5 py-3 bg-[#F8FAFD] flex items-center justify-between">
                <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Customer</span>
                <span className="text-sm font-semibold text-[#1B2A4A]">{customer.name}</span>
              </div>
              <div className="px-5 py-3 flex items-center justify-between">
                <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Institution</span>
                <span className="text-sm text-gray-700">{customer.institution}</span>
              </div>
              {selectedFees.map(fee => (
                <div key={fee.id} className="px-5 py-3 flex items-center justify-between">
                  <span className="text-xs text-gray-500">{fee.name}</span>
                  <span className="text-sm font-mono text-gray-700">EGP {fee.remaining.toLocaleString()}</span>
                </div>
              ))}
              <div className="px-5 py-3 flex items-center justify-between bg-[#F8FAFD]">
                <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider flex items-center gap-1.5">
                  Amount to Pay
                  {isPartial && <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-amber-100 text-amber-700 border border-amber-200">Partial</span>}
                </span>
                <span className="text-base font-bold font-mono text-[#003087]">EGP {payAmountNum.toLocaleString()}</span>
              </div>
            </div>

            {/* Payment method */}
            <div className="space-y-2">
              <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider block">Payment Method</label>
              <div className="grid grid-cols-3 gap-2">
                {(['CIB Debit Card', 'CIB Credit Card', 'Cash'] as PayMethod[]).map(m => (
                  <button key={m} onClick={() => { setPayMethod(m); setCreditPayType(null); setEppTenor(null) }}
                    className={`py-2.5 text-xs font-semibold rounded-lg border transition-all ${
                      payMethod === m ? 'border-[#003087] bg-[#EBF1FB] text-[#003087]' : 'border-[#DDE3EF] text-gray-500 hover:bg-gray-50'
                    }`}>
                    {m}
                  </button>
                ))}
              </div>

              {/* Credit Card sub-options: Full Payment vs EPP */}
              {payMethod === 'CIB Credit Card' && (
                <div className="rounded-xl border border-[#003087]/20 bg-[#F4F8FF] p-3 space-y-2.5">
                  <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">Payment Type</p>
                  <div className="grid grid-cols-2 gap-2">
                    {(['full', 'epp'] as const).map(t => (
                      <button key={t} onClick={() => { setCreditPayType(t); setEppTenor(null) }}
                        className={`py-2.5 text-xs font-semibold rounded-lg border transition-all ${
                          creditPayType === t ? 'border-[#003087] bg-[#EBF1FB] text-[#003087]' : 'border-[#DDE3EF] text-gray-500 bg-white hover:bg-gray-50'
                        }`}>
                        {t === 'full' ? 'Full Payment' : 'EPP / Installments'}
                      </button>
                    ))}
                  </div>

                  {/* EPP tenor picker */}
                  {creditPayType === 'epp' && (
                    <div className="space-y-2 pt-1">
                      <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">Installment Tenor</p>
                      <div className="grid grid-cols-4 gap-1.5">
                        {([3, 6, 12, 18] as const).map(t => (
                          <button key={t} onClick={() => setEppTenor(t)}
                            className={`py-2 text-xs font-semibold rounded-lg border transition-all ${
                              eppTenor === t ? 'border-[#003087] bg-[#EBF1FB] text-[#003087]' : 'border-[#DDE3EF] text-gray-500 bg-white hover:bg-gray-50'
                            }`}>
                            {t}m
                          </button>
                        ))}
                      </div>
                      {eppTenor ? (
                        <div className="flex items-center justify-between pt-0.5">
                          <span className="text-[11px] text-gray-500">Est. monthly installment</span>
                          <span className="text-xs font-bold font-mono text-[#003087]">
                            EGP {Math.ceil(payAmountNum / eppTenor).toLocaleString()} / month
                          </span>
                        </div>
                      ) : (
                        <p className="text-[11px] text-gray-400">Select a tenor above to continue.</p>
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* Demo toggle */}
            <label className="flex items-center gap-2 text-xs text-gray-400 cursor-pointer select-none">
              <input type="checkbox" checked={simulateFail} onChange={e => setSimulateFail(e.target.checked)}
                className="rounded border-gray-300 text-red-500 accent-red-500" />
              Demo: simulate payment failure
            </label>

            <div className="flex gap-3">
              <button onClick={() => setStep('amount')} className="px-4 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">← Back</button>
              <button onClick={() => setStep('processing')}
                disabled={payMethod === 'CIB Credit Card' && (!creditPayType || (creditPayType === 'epp' && !eppTenor))}
                className="flex-1 py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors flex items-center justify-center gap-2 disabled:opacity-40 disabled:cursor-not-allowed">
                <CreditCardIcon className="w-4 h-4" /> Process Payment
              </button>
            </div>
          </div>
        )}

        {/* ── Processing state (US-45) ── */}
        {step === 'processing' && (
          <div className="flex flex-col items-center justify-center py-16 space-y-4">
            <div className="w-14 h-14 rounded-full border-4 border-[#003087]/20 border-t-[#003087] animate-spin" />
            <p className="text-sm font-semibold text-[#1B2A4A]">Processing Payment…</p>
            <p className="text-xs text-gray-400">Please do not close this window.</p>
          </div>
        )}

        {/* ── Success + Receipt (US-47) ── */}
        {step === 'success' && customer && (
          <div className="max-w-md mx-auto">
            <div className="bg-[#003087] text-white px-6 py-5 rounded-t-xl">
              <div className="text-[10px] uppercase tracking-widest opacity-50 mb-1">Commercial International Bank Egypt</div>
              <div className="text-lg font-bold">Payment Receipt</div>
              <div className="text-xs opacity-60 mt-0.5">Official Transaction Record</div>
            </div>
            <div className="border border-t-0 border-[#E8EDF5] rounded-b-xl bg-white p-6 space-y-4">
              <div className="flex items-center gap-2 bg-green-50 border border-green-200 rounded-lg px-4 py-2.5">
                <CheckCircleIcon className="w-5 h-5 text-green-600 shrink-0" />
                <span className="text-sm font-semibold text-green-800">Payment Successful</span>
              </div>
              <div className="space-y-3">
                {([
                  ['Transaction Ref.',  receiptRef],
                  ['Date / Time',       '1 Sep 2026 · ' + new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })],
                  ['Customer',         customer.name],
                  ['National ID',      maskNID(customer.nid)],
                  ['Institution',      customer.institution],
                  ['Fees Paid',        selectedFees.map(f => f.name).join(', ')],
                  ['Payment Method',   payMethod === 'CIB Credit Card' && creditPayType === 'epp' ? `CIB Credit Card — EPP (${eppTenor}m)` : payMethod],
                  ['Payment Status',   'Successful'],
                ] as [string, string][]).map(([k, v]) => (
                  <div key={k} className="flex justify-between text-xs">
                    <span className="text-gray-400 font-semibold">{k}</span>
                    <span className={`font-semibold ${k === 'Transaction Ref.' ? 'font-mono text-[#003087]' : k === 'Payment Status' ? 'text-green-700' : 'text-gray-700'}`}>{v}</span>
                  </div>
                ))}
              </div>
              <div className="bg-[#F4F6F9] rounded-lg px-4 py-3 flex items-center justify-between border border-[#E8EDF5]">
                <span className="text-sm text-gray-500">Amount Paid</span>
                <span className="text-xl font-bold font-mono text-[#003087]">EGP {payAmountNum.toLocaleString()}</span>
              </div>
              {isPartial && (
                <div className="flex items-start gap-1.5 text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2.5">
                  <ClockIcon className="w-3.5 h-3.5 shrink-0 mt-0.5" />
                  Partial payment — remaining balance of EGP {afterPayment.toLocaleString()} carried forward.
                </div>
              )}
              <div className="flex gap-3 pt-2">
                <button className="flex-1 py-2.5 text-xs font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors text-gray-500">
                  🖨 Print Receipt
                </button>
                <button onClick={resetWorkflow} className="flex-1 py-2.5 text-xs font-semibold border border-[#003087]/30 rounded-lg text-[#003087] hover:bg-[#EBF1FB] transition-colors">
                  New Payment
                </button>
                <button onClick={onDone} className="flex-1 py-2.5 text-xs font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors">
                  Done
                </button>
              </div>
            </div>
          </div>
        )}

        {/* ── Failed state (US-45) ── */}
        {step === 'failed' && (
          <div className="max-w-md mx-auto py-4 text-center space-y-4">
            <div className="w-14 h-14 rounded-full bg-red-50 flex items-center justify-center mx-auto">
              <XCircleIcon className="w-7 h-7 text-red-500" />
            </div>
            <div>
              <h3 className="text-base font-bold text-[#1B2A4A] mb-1">Payment Failed</h3>
              <p className="text-sm text-gray-500">The transaction could not be processed. No amount has been charged.</p>
            </div>
            <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-left space-y-1.5 text-xs">
              <div className="flex justify-between"><span className="text-gray-400">Error Code</span><span className="font-mono text-red-700">ERR-4012 · Authorization declined</span></div>
              <div className="flex justify-between"><span className="text-gray-400">Customer</span><span className="font-semibold text-gray-700">{customer?.name}</span></div>
              <div className="flex justify-between"><span className="text-gray-400">Amount Attempted</span><span className="font-mono text-gray-700">EGP {payAmountNum.toLocaleString()}</span></div>
            </div>
            <div className="flex gap-3">
              <button onClick={() => { setSimulateFail(false); setStep('review') }}
                className="flex-1 py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors flex items-center justify-center gap-1.5">
                <RefreshIcon className="w-4 h-4" /> Retry Payment
              </button>
              <button onClick={onDone} className="flex-1 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
                Cancel
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

/* ── Transactions main screen ── */
export default function Transactions({ userRole: _userRole }: { userRole?: string }) {
  const [mainView,        setMainView]        = useState<'list' | 'detail' | 'workflow'>('list')
  const [activeTab,       setActiveTab]       = useState('All')
  const [search,          setSearch]          = useState('')
  const [institution,     setInstitution]     = useState('')
  const [instType,        setInstType]        = useState('')
  const [method,          setMethod]          = useState('')
  const [priorityFilter,  setPriorityFilter]  = useState('')
  const [selected,        setSelected]        = useState<Transaction | null>(null)

  if (mainView === 'workflow') return <PaymentWorkflow onDone={() => setMainView('list')} />
  if (mainView === 'detail' && selected) return <TransactionDetail tx={selected} onBack={() => setMainView('list')} />

  const filtered = transactions.filter(tx => {
    const matchTab      = activeTab === 'All' || tx.status === activeTab
    const matchSearch   = !search || tx.id.toLowerCase().includes(search.toLowerCase()) || tx.student.toLowerCase().includes(search.toLowerCase())
    const matchInst     = !institution || tx.institution.includes(institution)
    const matchType     = !instType || tx.institutionType === instType
    const matchMethod   = !method || tx.method === method
    const matchPriority = !priorityFilter || txPriority(tx) === priorityFilter
    return matchTab && matchSearch && matchInst && matchType && matchMethod && matchPriority
  })

  return (
    <div className="space-y-4">
      {/* Status tabs + actions */}
      <div className="flex items-center gap-1 bg-white rounded-xl border border-[#E8EDF5] p-1.5">
        {statusTabs.map(({ label, count }) => (
          <button key={label} onClick={() => setActiveTab(label)}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${activeTab === label ? 'bg-[#003087] text-white shadow-sm' : 'text-gray-500 hover:bg-gray-50'}`}>
            {label}
            <span className={`text-[10px] rounded-full px-1.5 py-0.5 font-bold ${activeTab === label ? 'bg-white/20 text-white' : 'bg-gray-100 text-gray-500'}`}>
              {count.toLocaleString()}
            </span>
          </button>
        ))}
        <div className="flex-1" />
        {/* US-42: Process Payment entry point */}
        <button onClick={() => setMainView('workflow')}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-white bg-[#F7941D] hover:bg-[#C96B10] transition-colors">
          <PlusIcon className="w-3.5 h-3.5" /> Process Payment
        </button>
        <button className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-gray-500 hover:bg-gray-50 border border-gray-200 transition-colors ml-1">
          <DownloadIcon className="w-3.5 h-3.5" /> Export
        </button>
      </div>

      {/* Filters (US-48) */}
      <div className="flex items-center gap-3 flex-wrap">
        <div className="relative flex-1 min-w-[180px] max-w-xs">
          <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-300" />
          <input type="text" placeholder="Transaction ID or student…" value={search} onChange={e => setSearch(e.target.value)}
            className="w-full pl-8 pr-4 py-2 border border-[#DDE3EF] rounded-lg text-sm placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] bg-white" />
        </div>
        <select value={instType} onChange={e => setInstType(e.target.value)}
          className="border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-200 bg-white">
          <option value="">All Types</option>
          <option value="School">School</option>
          <option value="University">University</option>
        </select>
        <select value={institution} onChange={e => setInstitution(e.target.value)}
          className="border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-200 bg-white">
          <option value="">All Institutions</option>
          <option>Cairo International School</option>
          <option>Maadi British School</option>
          <option>Heliopolis Academy</option>
          <option>Nasr City Academy</option>
          <option>Alexandria International</option>
          <option>Cairo University</option>
          <option>Ain Shams University</option>
          <option>American University in Cairo</option>
        </select>
        <select value={method} onChange={e => setMethod(e.target.value)}
          className="border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-200 bg-white">
          <option value="">All Methods</option>
          <option>CIB Debit Card</option>
          <option>CIB Credit Card</option>
          <option>Cash</option>
          <option>EPP</option>
          <option>Bank Transfer</option>
        </select>
        <select value={priorityFilter} onChange={e => setPriorityFilter(e.target.value)}
          className="border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-200 bg-white">
          <option value="">All Priorities</option>
          <option value="OVERDUE">Overdue</option>
          <option value="URGENT">Urgent</option>
          <option value="HIGH">High</option>
          <option value="MEDIUM">Medium</option>
          <option value="LOW">Low</option>
          <option value="PAID">Paid</option>
        </select>
        <div className="relative">
          <CalendarIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-300" />
          <input type="date" defaultValue="2026-08-31"
            className="pl-8 pr-3 py-2 border border-[#DDE3EF] rounded-lg text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-200 bg-white" />
        </div>
      </div>

      {/* Transaction table (US-38, US-39) */}
      <div className="bg-white rounded-xl border border-[#E8EDF5] overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-[#F8FAFD] border-b border-[#E8EDF5]">
                {['Transaction ID','Institution','Type','Student','Fee','Amount','Due Date','Priority','Penalty','Method','Recon','Status',''].map((h, i) => (
                  <th key={i} className="text-left px-5 py-3.5 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {filtered.map(tx => (
                <tr key={tx.id} className="hover:bg-[#F8FAFD] transition-colors">
                  <td className="px-5 py-4">
                    <div className="font-mono text-xs text-[#003087] font-semibold whitespace-nowrap">{tx.id}</div>
                    {tx.partial && (
                      <div className="text-[10px] text-amber-600 font-semibold mt-0.5">Partial</div>
                    )}
                  </td>
                  <td className="px-5 py-4 text-xs text-gray-700 max-w-[140px] truncate">{tx.institution}</td>
                  <td className="px-5 py-4 whitespace-nowrap">
                    <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${tx.institutionType === 'University' ? 'bg-[#FEF3E6] text-[#C96B10]' : 'bg-[#EBF1FB] text-[#003087]'}`}>{tx.institutionType}</span>
                  </td>
                  <td className="px-5 py-4 text-xs text-gray-600 whitespace-nowrap">{tx.student}</td>
                  <td className="px-5 py-4 text-xs text-gray-500 whitespace-nowrap">{tx.fee}</td>
                  <td className="px-5 py-4 text-xs font-mono font-bold text-gray-800 whitespace-nowrap">{tx.amount.toLocaleString()} EGP</td>
                  <td className="px-5 py-4 whitespace-nowrap">
                    <div className="text-xs font-mono text-gray-600">{formatDueDate(tx.dueDate)}</div>
                    <div className={`text-[10px] mt-0.5 ${daysFromToday(tx.dueDate) < 0 && txOutstanding(tx) > 0 ? 'text-red-500 font-semibold' : daysFromToday(tx.dueDate) === 0 && txOutstanding(tx) > 0 ? 'text-orange-600 font-semibold' : 'text-gray-400'}`}>
                      {txOutstanding(tx) > 0 ? dueDateLabel(tx.dueDate) : 'Settled'}
                    </div>
                  </td>
                  <td className="px-5 py-4 whitespace-nowrap">
                    <PriorityBadge priority={txPriority(tx)} />
                  </td>
                  <td className="px-5 py-4 whitespace-nowrap">
                    {txPenalty(tx) > 0 ? (
                      <div>
                        <span className="text-xs font-mono font-semibold text-red-600">+EGP {txPenalty(tx).toLocaleString()}</span>
                        <div className="text-[10px] text-red-400 mt-0.5">Total: EGP {calcTotalDue(txOutstanding(tx), tx.dueDate).toLocaleString()}</div>
                      </div>
                    ) : (
                      <span className="text-xs text-gray-300">—</span>
                    )}
                  </td>
                  <td className="px-5 py-4 text-xs text-gray-500 whitespace-nowrap">{tx.method}</td>
                  <td className="px-5 py-4 whitespace-nowrap">
                    <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${
                      tx.reconStatus === 'Matched'      ? 'bg-green-50  text-green-700 border-green-200' :
                      tx.reconStatus === 'Exception'    ? 'bg-red-50   text-red-700   border-red-200'   :
                      tx.reconStatus === 'Pending'      ? 'bg-amber-50 text-amber-700 border-amber-200' :
                                                          'bg-gray-100 text-gray-500  border-gray-200'
                    }`}>{tx.reconStatus}</span>
                  </td>
                  <td className="px-5 py-4 whitespace-nowrap"><StatusBadge status={tx.status} /></td>
                  <td className="px-5 py-4">
                    <button onClick={() => { setSelected(tx); setMainView('detail') }}
                      className="p-1.5 rounded hover:bg-[#003087]/10 text-[#003087] transition-colors">
                      <EyeIcon className="w-3.5 h-3.5" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="flex items-center justify-between px-5 py-3.5 border-t border-gray-100">
          <span className="text-xs text-gray-400">Showing {filtered.length} of 1,284 transactions</span>
          <div className="flex items-center gap-1">
            <button className="p-1.5 rounded border border-gray-200 hover:bg-gray-50 text-gray-400 transition-colors"><ChevronLeftIcon className="w-3.5 h-3.5" /></button>
            {[1, 2, 3].map(n => (
              <button key={n} className={`w-7 h-7 rounded text-xs font-semibold ${n === 1 ? 'bg-[#003087] text-white' : 'border border-gray-200 text-gray-500 hover:bg-gray-50'}`}>{n}</button>
            ))}
            <span className="text-gray-400 text-xs px-1">…</span>
            <button className="w-7 h-7 rounded border border-gray-200 text-xs text-gray-500 hover:bg-gray-50">107</button>
            <button className="p-1.5 rounded border border-gray-200 hover:bg-gray-50 text-gray-400 transition-colors"><ChevronRightIcon className="w-3.5 h-3.5" /></button>
          </div>
        </div>
      </div>
    </div>
  )
}
