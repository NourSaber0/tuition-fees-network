import { useState, useEffect } from 'react'
import {
  SearchIcon, EyeIcon, ChevronLeftIcon, ChevronRightIcon,
  CheckCircleIcon, ClockIcon, CalendarIcon, CreditCardIcon,
  PlusIcon, AlertIcon, XIcon, CheckIcon,
} from '../components/Icons'

type EPPStatus = 'Active' | 'Completed' | 'Defaulted' | 'Cancelled'

interface EPPPlan {
  id: string
  payRef: string
  institution: string
  institutionType: 'School' | 'University'
  student: string
  principal: number
  tenor: 3 | 6 | 12 | 18
  interestRate: number
  interest: number
  adminFee: number
  total: number
  monthly: number
  paidInstallments: number
  status: EPPStatus
  startDate: string
}

const epp: EPPPlan[] = [
  { id: 'EPP-2026-001', payRef: 'PAY-2026-00841', institution: 'Cairo International School', institutionType: 'School', student: 'Ahmed Hassan', principal: 24000, tenor: 12, interestRate: 14, interest: 3360, adminFee: 240, total: 27600, monthly: 2300, paidInstallments: 3, status: 'Active', startDate: '1 Jun 2026' },
  { id: 'EPP-2026-002', payRef: 'PAY-2026-00718', institution: 'Heliopolis Academy', institutionType: 'School', student: 'Sara Mohamed', principal: 22000, tenor: 6, interestRate: 12, interest: 1760, adminFee: 220, total: 23980, monthly: 3996.67, paidInstallments: 5, status: 'Active', startDate: '1 Apr 2026' },
  { id: 'EPP-2026-003', payRef: 'PAY-2026-00700', institution: 'Cairo University', institutionType: 'University', student: 'Karim Nour', principal: 35000, tenor: 12, interestRate: 14, interest: 4900, adminFee: 350, total: 40250, monthly: 3354.17, paidInstallments: 4, status: 'Active', startDate: '1 May 2026' },
  { id: 'EPP-2026-004', payRef: 'PAY-2026-00612', institution: 'Nasr City Academy', institutionType: 'School', student: 'Omar Nabil', principal: 15500, tenor: 3, interestRate: 10, interest: 775, adminFee: 155, total: 16430, monthly: 5476.67, paidInstallments: 3, status: 'Completed', startDate: '1 May 2026' },
  { id: 'EPP-2026-005', payRef: 'PAY-2026-00589', institution: 'Maadi British School', institutionType: 'School', student: 'Dina Fouad', principal: 18000, tenor: 12, interestRate: 14, interest: 2520, adminFee: 180, total: 20700, monthly: 1725, paidInstallments: 4, status: 'Active', startDate: '1 May 2026' },
  { id: 'EPP-2026-006', payRef: 'PAY-2026-00520', institution: 'American University in Cairo', institutionType: 'University', student: 'Hassan Fouad', principal: 80000, tenor: 18, interestRate: 16, interest: 20480, adminFee: 800, total: 101280, monthly: 5626.67, paidInstallments: 6, status: 'Active', startDate: '1 Feb 2026' },
  { id: 'EPP-2026-007', payRef: 'PAY-2026-00445', institution: 'Alexandria International', institutionType: 'School', student: 'Tamer Gamal', principal: 28000, tenor: 18, interestRate: 16, interest: 7168, adminFee: 280, total: 35448, monthly: 1969.33, paidInstallments: 6, status: 'Active', startDate: '1 Feb 2026' },
  { id: 'EPP-2026-008', payRef: 'PAY-2026-00301', institution: 'Cairo International School', institutionType: 'School', student: 'Nour Hassan', principal: 20000, tenor: 6, interestRate: 12, interest: 1600, adminFee: 200, total: 21800, monthly: 3633.33, paidInstallments: 2, status: 'Defaulted', startDate: '1 Mar 2026' },
  { id: 'EPP-2026-009', payRef: 'PAY-2026-00288', institution: 'Ain Shams University', institutionType: 'University', student: 'Nadia Saleh', principal: 42000, tenor: 18, interestRate: 16, interest: 10752, adminFee: 420, total: 53172, monthly: 2954, paidInstallments: 9, status: 'Active', startDate: '1 Jan 2026' },
  { id: 'EPP-2026-010', payRef: 'PAY-2026-00190', institution: 'Nasr City Academy', institutionType: 'School', student: 'Mona Khaled', principal: 12000, tenor: 3, interestRate: 10, interest: 600, adminFee: 120, total: 12720, monthly: 4240, paidInstallments: 3, status: 'Completed', startDate: '1 Mar 2026' },
]

const statusStyle: Record<EPPStatus, string> = {
  Active: 'bg-green-50 text-green-700 border-green-200',
  Completed: 'bg-blue-50 text-blue-700 border-blue-200',
  Defaulted: 'bg-red-50 text-red-700 border-red-200',
  Cancelled: 'bg-gray-100 text-gray-500 border-gray-200',
}

function EPPDetail({ plan, onBack }: { plan: EPPPlan; onBack: () => void }) {
  const schedule = Array.from({ length: plan.tenor }, (_, i) => ({
    installment: i + 1,
    dueDate: new Date(new Date(plan.startDate).setMonth(new Date(plan.startDate).getMonth() + i + 1)).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' }),
    principal: Math.round((plan.principal / plan.tenor) * 100) / 100,
    interest: Math.round((plan.interest / plan.tenor) * 100) / 100,
    amount: Math.round(plan.monthly * 100) / 100,
    status: i < plan.paidInstallments ? 'Paid' : i === plan.paidInstallments ? 'Due' : 'Upcoming',
  }))

  return (
    <div className="space-y-4">
      <button onClick={onBack} className="flex items-center gap-1 text-sm text-gray-400 hover:text-gray-700 transition-colors">
        <ChevronLeftIcon className="w-4 h-4" /> Back to EPP Plans
      </button>

      <div className="grid grid-cols-3 gap-4">
        {/* Plan overview */}
        <div className="col-span-2 bg-white rounded-xl border border-[#E8EDF5] p-6">
          <div className="flex items-start justify-between mb-5 pb-5 border-b border-gray-100">
            <div>
              <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1">EPP Plan</div>
              <h2 className="text-lg font-bold text-[#1B2A4A] font-mono">{plan.id}</h2>
              <div className="flex items-center gap-2 mt-0.5">
              <p className="text-xs text-gray-400">{plan.payRef} · {plan.institution}</p>
              <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${plan.institutionType === 'University' ? 'bg-[#FEF3E6] text-[#C96B10]' : 'bg-[#EBF1FB] text-[#003087]'}`}>{plan.institutionType}</span>
            </div>
            </div>
            <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${statusStyle[plan.status]}`}>
              {plan.status}
            </span>
          </div>

          <div className="grid grid-cols-3 gap-6">
            {[
              ['Student', plan.student],
              ['Institution', plan.institution],
              ['Start Date', plan.startDate],
            ].map(([k, v]) => (
              <div key={k}>
                <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1">{k}</div>
                <div className="text-sm text-gray-800">{v}</div>
              </div>
            ))}
          </div>

          {/* Pricing breakdown */}
          <div className="mt-6 bg-[#F8FAFD] rounded-xl p-5">
            <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-4">Pricing Breakdown</h3>
            <div className="space-y-2.5">
              {[
                { label: 'Principal', value: `${plan.principal.toLocaleString()} EGP`, highlight: false },
                { label: `Tenor`, value: `${plan.tenor} months`, highlight: false },
                { label: `Interest (${plan.interestRate}% per annum)`, value: `${plan.interest.toLocaleString()} EGP`, highlight: false },
                { label: 'Admin Fee', value: `${plan.adminFee.toLocaleString()} EGP`, highlight: false },
              ].map(({ label, value }) => (
                <div key={label} className="flex items-center justify-between text-sm">
                  <span className="text-gray-500">{label}</span>
                  <span className="font-mono font-semibold text-gray-700">{value}</span>
                </div>
              ))}
              <div className="border-t border-[#DDE3EF] pt-2.5 flex items-center justify-between">
                <span className="text-sm font-bold text-[#1B2A4A]">Total Payable</span>
                <span className="font-mono font-bold text-[#1B2A4A] text-base">{plan.total.toLocaleString()} EGP</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm text-gray-500">Monthly Installment</span>
                <span className="font-mono font-bold text-[#F7941D] text-lg">{plan.monthly.toLocaleString()} EGP</span>
              </div>
            </div>
          </div>

          {/* Progress — installment count */}
          <div className="mt-4 space-y-3">
            <div className="flex items-center gap-3">
              <div className="flex-1">
                <div className="flex items-center justify-between text-xs mb-1.5">
                  <span className="text-gray-500">{plan.paidInstallments} of {plan.tenor} installments paid</span>
                  <span className="font-semibold text-gray-700">{Math.round((plan.paidInstallments / plan.tenor) * 100)}%</span>
                </div>
                <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-[#003087] rounded-full transition-all"
                    style={{ width: `${(plan.paidInstallments / plan.tenor) * 100}%` }}
                  />
                </div>
              </div>
              <span className="text-xs text-gray-400 whitespace-nowrap">
                {plan.tenor - plan.paidInstallments} remaining
              </span>
            </div>
            {/* Progress — monetary (US-53) */}
            <div className="flex items-center gap-3">
              <div className="flex-1">
                <div className="flex items-center justify-between text-xs mb-1.5">
                  <span className="text-gray-500">EGP {(plan.paidInstallments * plan.monthly).toLocaleString()} paid of EGP {plan.total.toLocaleString()}</span>
                  <span className="font-semibold text-gray-700">{Math.round((plan.paidInstallments * plan.monthly / plan.total) * 100)}%</span>
                </div>
                <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-green-500 rounded-full transition-all"
                    style={{ width: `${(plan.paidInstallments * plan.monthly / plan.total) * 100}%` }}
                  />
                </div>
              </div>
              <span className="text-xs text-gray-400 whitespace-nowrap">
                EGP {((plan.tenor - plan.paidInstallments) * plan.monthly).toLocaleString()} left
              </span>
            </div>
          </div>
        </div>

        {/* Quick stats */}
        <div className="bg-white rounded-xl border border-[#E8EDF5] p-5">
          <h3 className="text-sm font-semibold text-[#1B2A4A] mb-4">Plan Summary</h3>
          <div className="space-y-4">
            {[
              { label: 'Total Paid', value: `${(plan.paidInstallments * plan.monthly).toLocaleString()} EGP`, color: 'text-green-600' },
              { label: 'Outstanding', value: `${((plan.tenor - plan.paidInstallments) * plan.monthly).toLocaleString()} EGP`, color: 'text-[#003087]' },
              { label: 'Next Due Date', value: schedule[plan.paidInstallments]?.dueDate || '—', color: 'text-gray-700' },
              { label: 'Payment Reference', value: plan.payRef, color: 'text-[#003087] font-mono text-[11px]' },
            ].map(({ label, value, color }) => (
              <div key={label} className="bg-gray-50 rounded-lg p-3">
                <div className="text-[11px] text-gray-400 uppercase tracking-wider font-semibold mb-1">{label}</div>
                <div className={`text-sm font-bold ${color}`}>{value}</div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Installment schedule */}
      <div className="bg-white rounded-xl border border-[#E8EDF5] overflow-hidden">
        <div className="px-5 py-4 border-b border-gray-100">
          <h3 className="text-sm font-semibold text-[#1B2A4A]">Installment Schedule</h3>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-[#F8FAFD]">
                {['#', 'Due Date', 'Principal (EGP)', 'Interest (EGP)', 'Amount (EGP)', 'Paid Amount', 'Status'].map(h => (
                  <th key={h} className="text-left px-5 py-3 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {schedule.map(row => (
                <tr key={row.installment} className={`transition-colors ${row.status === 'Due' ? 'bg-amber-50/50' : 'hover:bg-[#F8FAFD]'}`}>
                  <td className="px-5 py-3 text-xs font-mono font-semibold text-gray-500">{row.installment}</td>
                  <td className="px-5 py-3 text-xs text-gray-600">{row.dueDate}</td>
                  <td className="px-5 py-3 text-xs font-mono text-gray-700">{row.principal.toLocaleString()}</td>
                  <td className="px-5 py-3 text-xs font-mono text-gray-700">{row.interest.toLocaleString()}</td>
                  <td className="px-5 py-3 text-xs font-mono font-bold text-gray-800">{row.amount.toLocaleString()}</td>
                  <td className="px-5 py-3 text-xs font-mono">
                    {row.status === 'Paid'
                      ? <span className="text-green-700 font-semibold">{row.amount.toLocaleString()}</span>
                      : <span className="text-gray-300">—</span>}
                  </td>
                  <td className="px-5 py-3">
                    <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${
                      row.status === 'Paid' ? 'bg-green-50 text-green-700 border-green-200' :
                      row.status === 'Due' ? 'bg-amber-50 text-amber-700 border-amber-200' :
                      'bg-gray-50 text-gray-400 border-gray-200'
                    }`}>{row.status}</span>
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

/* ── Interest rates per tenor ── */
const TENOR_RATES: Record<number, number> = { 3: 10, 6: 12, 12: 14, 18: 16 }

type CardResult = 'valid-credit' | 'rejected-debit' | 'rejected-non-cib' | null

function classifyCard(raw: string): CardResult {
  const digits = raw.replace(/\s/g, '')
  if (digits.length < 16) return null
  if (digits.startsWith('4589') || digits.startsWith('5412')) return 'valid-credit'
  if (digits.startsWith('4123') || digits.startsWith('4000')) return 'rejected-debit'
  return 'rejected-non-cib'
}

type CreateStep = 'card' | 'details' | 'review' | 'processing' | 'success'

interface CreateForm {
  cardNumber: string
  cardHolder: string
  expiry: string
  studentNid: string
  studentName: string
  institution: string
  feeDesc: string
  principal: string
  tenor: 3 | 6 | 12 | 18
}

const emptyCreate: CreateForm = {
  cardNumber: '', cardHolder: '', expiry: '',
  studentNid: '', studentName: '', institution: '',
  feeDesc: 'Tuition — Term 1 2026/27', principal: '',
  tenor: 12,
}

function formatCardInput(val: string) {
  const digits = val.replace(/\D/g, '').slice(0, 16)
  return digits.replace(/(.{4})/g, '$1 ').trim()
}

function CreateEPPWizard({ onClose, onCreated }: { onClose: () => void; onCreated: (plan: EPPPlan) => void }) {
  const [step, setStep]         = useState<CreateStep>('card')
  const [form, setForm]         = useState<CreateForm>(emptyCreate)
  const [cardResult, setCardResult] = useState<CardResult>(null)
  const [cardChecked, setCardChecked] = useState(false)
  const [formError, setFormError] = useState('')
  const [newPlan, setNewPlan]   = useState<EPPPlan | null>(null)

  useEffect(() => {
    if (step !== 'processing') return
    const t = setTimeout(() => {
      const rate = TENOR_RATES[form.tenor]
      const principal = parseFloat(form.principal)
      const interest  = Math.round(principal * (rate / 100) * (form.tenor / 12))
      const adminFee  = Math.round(principal * 0.01)
      const total     = principal + interest + adminFee
      const monthly   = Math.round((total / form.tenor) * 100) / 100
      const plan: EPPPlan = {
        id:               `EPP-2026-0${String(epp.length + 11).padStart(2, '0')}`,
        payRef:           `PAY-2026-0${Date.now().toString().slice(-5)}`,
        institution:      form.institution,
        institutionType:  'School',
        student:          form.studentName,
        principal,
        tenor:            form.tenor,
        interestRate:     rate,
        interest,
        adminFee,
        total,
        monthly,
        paidInstallments: 0,
        status:           'Active',
        startDate:        '3 Sep 2026',
      }
      setNewPlan(plan)
      setStep('success')
    }, 2000)
    return () => clearTimeout(t)
  }, [step])

  const checkCard = () => {
    const result = classifyCard(form.cardNumber)
    setCardResult(result)
    setCardChecked(true)
  }

  const proceedToDetails = () => {
    if (cardResult !== 'valid-credit') return
    setStep('details')
    setFormError('')
  }

  const proceedToReview = () => {
    if (!form.studentName.trim()) { setFormError('Student name is required.'); return }
    if (!form.institution.trim()) { setFormError('Institution name is required.'); return }
    const p = parseFloat(form.principal)
    if (!p || p <= 0) { setFormError('Enter a valid principal amount.'); return }
    if (p > 500000) { setFormError('Principal cannot exceed EGP 500,000.'); return }
    setFormError('')
    setStep('review')
  }

  const rate      = TENOR_RATES[form.tenor]
  const principal = parseFloat(form.principal) || 0
  const interest  = Math.round(principal * (rate / 100) * (form.tenor / 12))
  const adminFee  = Math.round(principal * 0.01)
  const total     = principal + interest + adminFee
  const monthly   = form.tenor ? Math.round((total / form.tenor) * 100) / 100 : 0

  return (
    <div className="fixed inset-0 bg-black/40 flex items-end sm:items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-[#E8EDF5]">
          <div>
            <h2 className="text-base font-bold text-[#1B2A4A]">Create EPP Plan</h2>
            <p className="text-[11px] text-gray-400 mt-0.5">
              {step === 'card' && 'Step 1 of 3 — Card Validation'}
              {step === 'details' && 'Step 2 of 3 — Plan Details'}
              {(step === 'review' || step === 'processing') && 'Step 3 of 3 — Review & Confirm'}
              {step === 'success' && 'Plan Created'}
            </p>
          </div>
          <button onClick={onClose} className="p-1.5 rounded hover:bg-gray-100 text-gray-400 transition-colors">
            <XIcon className="w-4 h-4" />
          </button>
        </div>

        {/* Progress bar */}
        {step !== 'success' && step !== 'processing' && (
          <div className="flex gap-1 px-6 pt-4">
            {(['card', 'details', 'review'] as CreateStep[]).map((s, i) => (
              <div key={s} className={`flex-1 h-1 rounded-full transition-all ${
                step === s ? 'bg-[#F7941D]' :
                i < ['card','details','review'].indexOf(step) ? 'bg-[#003087]' : 'bg-gray-100'
              }`} />
            ))}
          </div>
        )}

        <div className="px-6 py-5 space-y-4">

          {/* ── Step 1: Card ── */}
          {step === 'card' && (
            <>
              <div>
                <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Card Number</label>
                <input
                  type="text"
                  value={form.cardNumber}
                  onChange={e => { setForm({ ...form, cardNumber: formatCardInput(e.target.value) }); setCardChecked(false); setCardResult(null) }}
                  placeholder="XXXX XXXX XXXX XXXX"
                  maxLength={19}
                  className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm font-mono text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
                />
                <p className="text-[11px] text-gray-400 mt-1">
                  Demo: <span className="font-mono text-[#003087]">4589 XXXX XXXX XXXX</span> (CIB Credit) ·{' '}
                  <span className="font-mono text-red-500">4123 XXXX XXXX XXXX</span> (CIB Debit — rejected) ·{' '}
                  <span className="font-mono text-gray-400">other = non-CIB</span>
                </p>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Cardholder Name</label>
                  <input type="text" value={form.cardHolder} onChange={e => setForm({ ...form, cardHolder: e.target.value })}
                    placeholder="As printed on card"
                    className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]" />
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Expiry (MM/YY)</label>
                  <input type="text" value={form.expiry} onChange={e => setForm({ ...form, expiry: e.target.value })}
                    placeholder="MM/YY" maxLength={5}
                    className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm font-mono text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]" />
                </div>
              </div>

              {/* Validation result */}
              {cardChecked && cardResult === 'valid-credit' && (
                <div className="flex items-center gap-2 px-4 py-3 bg-green-50 border border-green-200 rounded-xl text-sm text-green-700 font-medium">
                  <CheckCircleIcon className="w-4 h-4 shrink-0" /> CIB Credit Card verified — eligible for EPP
                </div>
              )}
              {cardChecked && cardResult === 'rejected-debit' && (
                <div className="flex items-start gap-2 px-4 py-3 bg-red-50 border border-red-200 rounded-xl">
                  <AlertIcon className="w-4 h-4 text-red-500 mt-0.5 shrink-0" />
                  <div>
                    <p className="text-sm font-semibold text-red-700">CIB Debit Card — Not eligible</p>
                    <p className="text-xs text-red-600 mt-0.5">EPP / Installment Plans require a CIB credit card. Debit cards are not accepted.</p>
                  </div>
                </div>
              )}
              {cardChecked && cardResult === 'rejected-non-cib' && (
                <div className="flex items-start gap-2 px-4 py-3 bg-red-50 border border-red-200 rounded-xl">
                  <AlertIcon className="w-4 h-4 text-red-500 mt-0.5 shrink-0" />
                  <div>
                    <p className="text-sm font-semibold text-red-700">Non-CIB Card — Not eligible</p>
                    <p className="text-xs text-red-600 mt-0.5">Only CIB-issued credit cards are accepted for EPP plans. Non-CIB cards cannot be used.</p>
                  </div>
                </div>
              )}

              <div className="flex gap-3 pt-2">
                <button onClick={onClose} className="px-4 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">Cancel</button>
                {!cardChecked && (
                  <button onClick={checkCard} disabled={form.cardNumber.replace(/\s/g,'').length < 16}
                    className="flex-1 py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors disabled:opacity-40">
                    Validate Card
                  </button>
                )}
                {cardChecked && cardResult === 'valid-credit' && (
                  <button onClick={proceedToDetails}
                    className="flex-1 py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors">
                    Continue →
                  </button>
                )}
                {cardChecked && cardResult !== 'valid-credit' && (
                  <button onClick={() => { setCardChecked(false); setCardResult(null); setForm({ ...form, cardNumber: '' }) }}
                    className="flex-1 py-2.5 text-sm font-semibold border border-[#003087] text-[#003087] rounded-lg hover:bg-[#EBF1FB] transition-colors">
                    Try Different Card
                  </button>
                )}
              </div>
            </>
          )}

          {/* ── Step 2: Details ── */}
          {step === 'details' && (
            <>
              {formError && (
                <div className="flex items-center gap-2 px-3 py-2 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700">
                  <AlertIcon className="w-3.5 h-3.5 shrink-0" />{formError}
                </div>
              )}
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Student Name <span className="text-red-400">*</span></label>
                  <input type="text" value={form.studentName} onChange={e => setForm({ ...form, studentName: e.target.value })}
                    placeholder="Full name"
                    className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]" />
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">National ID</label>
                  <input type="text" value={form.studentNid} onChange={e => setForm({ ...form, studentNid: e.target.value })}
                    placeholder="14-digit NID" maxLength={14}
                    className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm font-mono text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]" />
                </div>
                <div className="col-span-2">
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Institution <span className="text-red-400">*</span></label>
                  <input type="text" value={form.institution} onChange={e => setForm({ ...form, institution: e.target.value })}
                    placeholder="School or university name"
                    className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]" />
                </div>
                <div className="col-span-2">
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Fee Description</label>
                  <input type="text" value={form.feeDesc} onChange={e => setForm({ ...form, feeDesc: e.target.value })}
                    className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]" />
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Principal (EGP) <span className="text-red-400">*</span></label>
                  <input type="number" value={form.principal} onChange={e => setForm({ ...form, principal: e.target.value })}
                    placeholder="0.00" min="1000" max="500000"
                    className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm font-mono text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]" />
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Tenor</label>
                  <div className="grid grid-cols-4 gap-1">
                    {([3, 6, 12, 18] as const).map(t => (
                      <button key={t} onClick={() => setForm({ ...form, tenor: t })}
                        className={`py-2 text-xs font-semibold rounded-lg border transition-all ${
                          form.tenor === t ? 'border-[#003087] bg-[#EBF1FB] text-[#003087]' : 'border-[#DDE3EF] text-gray-500 hover:bg-gray-50'
                        }`}>
                        {t}m
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              {/* Live preview */}
              {principal > 0 && (
                <div className="bg-[#F8FAFD] rounded-xl p-4 space-y-2 text-xs">
                  <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-2">Plan Preview</p>
                  {[
                    ['Principal', `EGP ${principal.toLocaleString()}`],
                    [`Interest (${rate}% p.a.)`, `EGP ${interest.toLocaleString()}`],
                    [`Admin Fee (1%)`, `EGP ${adminFee.toLocaleString()}`],
                  ].map(([l, v]) => (
                    <div key={l} className="flex justify-between text-gray-500">
                      <span>{l}</span><span className="font-mono">{v}</span>
                    </div>
                  ))}
                  <div className="flex justify-between font-bold text-gray-800 border-t border-gray-100 pt-2">
                    <span>Total Payable</span><span className="font-mono">EGP {total.toLocaleString()}</span>
                  </div>
                  <div className="flex justify-between font-bold text-[#F7941D]">
                    <span>Monthly Installment</span><span className="font-mono">EGP {monthly.toLocaleString()}</span>
                  </div>
                </div>
              )}

              <div className="flex gap-3 pt-2">
                <button onClick={() => setStep('card')} className="px-4 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">← Back</button>
                <button onClick={proceedToReview}
                  className="flex-1 py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors">
                  Review Plan →
                </button>
              </div>
            </>
          )}

          {/* ── Step 3: Review ── */}
          {step === 'review' && (
            <>
              <div className="bg-[#F8FAFD] rounded-xl p-5 space-y-3">
                <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-3">Plan Summary</p>
                {[
                  ['Student', form.studentName],
                  ['Institution', form.institution],
                  ['Fee', form.feeDesc],
                  ['Card', `•••• •••• •••• ${form.cardNumber.replace(/\s/g,'').slice(-4)} (CIB Credit)`],
                  ['Principal', `EGP ${parseFloat(form.principal).toLocaleString()}`],
                  ['Tenor', `${form.tenor} months`],
                  [`Interest (${rate}% p.a.)`, `EGP ${interest.toLocaleString()}`],
                  ['Admin Fee', `EGP ${adminFee.toLocaleString()}`],
                ].map(([l, v]) => (
                  <div key={l} className="flex justify-between text-sm">
                    <span className="text-gray-500">{l}</span>
                    <span className="font-medium text-gray-800">{v}</span>
                  </div>
                ))}
                <div className="border-t border-[#DDE3EF] pt-3 flex justify-between font-bold text-base">
                  <span className="text-gray-800">Total Payable</span>
                  <span className="font-mono text-[#1B2A4A]">EGP {total.toLocaleString()}</span>
                </div>
                <div className="flex justify-between text-sm font-bold text-[#F7941D]">
                  <span>Monthly Installment</span>
                  <span className="font-mono">EGP {monthly.toLocaleString()}</span>
                </div>
              </div>
              <div className="flex gap-3 pt-2">
                <button onClick={() => setStep('details')} className="px-4 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">← Back</button>
                <button onClick={() => setStep('processing')}
                  className="flex-1 py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors flex items-center justify-center gap-2">
                  <CreditCardIcon className="w-4 h-4" /> Create EPP Plan
                </button>
              </div>
            </>
          )}

          {/* ── Processing ── */}
          {step === 'processing' && (
            <div className="py-10 text-center space-y-4">
              <div className="w-14 h-14 rounded-full border-4 border-[#003087]/20 border-t-[#003087] animate-spin mx-auto" />
              <div>
                <p className="font-semibold text-gray-800">Creating EPP Plan…</p>
                <p className="text-sm text-gray-400 mt-1">Please wait while we set up the installment plan.</p>
              </div>
            </div>
          )}

          {/* ── Success ── */}
          {step === 'success' && newPlan && (
            <div className="space-y-4">
              <div className="py-6 text-center space-y-3">
                <div className="w-14 h-14 rounded-full bg-green-100 flex items-center justify-center mx-auto">
                  <CheckCircleIcon className="w-7 h-7 text-green-600" />
                </div>
                <div>
                  <p className="font-bold text-gray-800 text-base">EPP Plan Created</p>
                  <p className="text-xs font-mono text-[#003087] mt-1">{newPlan.id}</p>
                </div>
              </div>
              <div className="bg-[#F8FAFD] rounded-xl p-4 space-y-2.5 text-sm">
                {[
                  ['Student',            newPlan.student],
                  ['Institution',        newPlan.institution],
                  ['Principal',          `EGP ${newPlan.principal.toLocaleString()}`],
                  ['Tenor',              `${newPlan.tenor} months`],
                  ['Monthly Installment',`EGP ${newPlan.monthly.toLocaleString()}`],
                  ['Total Payable',      `EGP ${newPlan.total.toLocaleString()}`],
                  ['Status',             'Active'],
                  ['First Payment',      'October 2026'],
                ].map(([l, v]) => (
                  <div key={l} className="flex justify-between">
                    <span className="text-gray-400">{l}</span>
                    <span className="font-medium text-gray-800 font-mono text-xs">{v}</span>
                  </div>
                ))}
              </div>
              <button onClick={() => { onCreated(newPlan); onClose() }}
                className="w-full py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors flex items-center justify-center gap-2">
                <CheckIcon className="w-4 h-4" /> Done — View EPP Plans
              </button>
            </div>
          )}

        </div>
      </div>
    </div>
  )
}

export default function EPP({ userRole }: { userRole?: string }) {
  const [selected, setSelected]   = useState<EPPPlan | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [eppList, setEppList]     = useState(epp)
  const canCreate = userRole === 'bank-admin' || userRole === 'bank-operations'
  const [search, setSearch] = useState('')
  const [filterStatus, setFilterStatus] = useState('All')
  const [filterTenor, setFilterTenor] = useState('All')

  if (selected) return <EPPDetail plan={selected} onBack={() => setSelected(null)} />

  const filtered = eppList.filter(p => {
    const matchSearch = !search || p.id.toLowerCase().includes(search.toLowerCase()) ||
      p.student.toLowerCase().includes(search.toLowerCase()) || p.payRef.toLowerCase().includes(search.toLowerCase())
    const matchStatus = filterStatus === 'All' || p.status === filterStatus
    const matchTenor = filterTenor === 'All' || p.tenor === Number(filterTenor)
    return matchSearch && matchStatus && matchTenor
  })

  const totalOutstanding = eppList.filter(p => p.status === 'Active').reduce((sum, p) => sum + (p.tenor - p.paidInstallments) * p.monthly, 0)

  return (
    <div className="space-y-4">
      {showCreate && (
        <CreateEPPWizard
          onClose={() => setShowCreate(false)}
          onCreated={plan => { setEppList(prev => [plan, ...prev]); setShowCreate(false) }}
        />
      )}

      {/* Summary bar */}
      <div className="grid grid-cols-4 gap-4">
        {[
          { label: 'Active Plans',      value: eppList.filter(p => p.status === 'Active').length,    icon: CreditCardIcon, color: 'bg-[#003087]/10 text-[#003087]' },
          { label: 'Completed',         value: eppList.filter(p => p.status === 'Completed').length, icon: CheckCircleIcon, color: 'bg-green-50 text-green-600' },
          { label: 'Defaulted',         value: eppList.filter(p => p.status === 'Defaulted').length, icon: ClockIcon, color: 'bg-red-50 text-red-500' },
          { label: 'Total Outstanding', value: `EGP ${(totalOutstanding / 1000).toFixed(0)}K`,       icon: CalendarIcon, color: 'bg-[#F7941D]/15 text-[#C96B10]' },
        ].map(({ label, value, icon: Icon, color }) => (
          <div key={label} className="bg-white rounded-xl border border-[#E8EDF5] p-5 flex items-center gap-4">
            <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${color} shrink-0`}>
              <Icon className="w-5 h-5" />
            </div>
            <div>
              <div className="text-xs font-semibold text-gray-400 uppercase tracking-wider">{label}</div>
              <div className="text-xl font-bold text-[#1B2A4A]">{value}</div>
            </div>
          </div>
        ))}
      </div>

      {/* Filters */}
      <div className="flex items-center gap-3">
        <div className="relative flex-1 max-w-xs">
          <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-300" />
          <input
            type="text"
            placeholder="Plan ID, student or reference…"
            value={search}
            onChange={e => setSearch(e.target.value)}
            className="w-full pl-8 pr-4 py-2 border border-[#DDE3EF] rounded-lg text-sm placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] bg-white"
          />
        </div>
        <select
          value={filterStatus}
          onChange={e => setFilterStatus(e.target.value)}
          className="border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-200 bg-white"
        >
          <option value="All">All Statuses</option>
          <option>Active</option>
          <option>Completed</option>
          <option>Defaulted</option>
          <option>Cancelled</option>
        </select>
        <select
          value={filterTenor}
          onChange={e => setFilterTenor(e.target.value)}
          className="border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-200 bg-white"
        >
          <option value="All">All Tenors</option>
          <option value="3">3 Months</option>
          <option value="6">6 Months</option>
          <option value="12">12 Months</option>
          <option value="18">18 Months</option>
        </select>
        {canCreate && (
          <button onClick={() => setShowCreate(true)}
            className="ml-auto flex items-center gap-2 px-4 py-2 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors">
            <PlusIcon className="w-3.5 h-3.5" /> Create EPP Plan
          </button>
        )}
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border border-[#E8EDF5] overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-[#F8FAFD] border-b border-[#E8EDF5]">
                {['Plan ID', 'Payment Ref', 'Institution', 'Type', 'Student', 'Principal', 'Tenor', 'Total Payable', 'Monthly', 'Progress', 'Status', ''].map(h => (
                  <th key={h + Math.random()} className="text-left px-4 py-3.5 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {filtered.map(plan => (
                <tr key={plan.id} className="hover:bg-[#F8FAFD] transition-colors">
                  <td className="px-4 py-3.5 font-mono text-xs font-bold text-[#003087] whitespace-nowrap">{plan.id}</td>
                  <td className="px-4 py-3.5 font-mono text-xs text-gray-500 whitespace-nowrap">{plan.payRef}</td>
                  <td className="px-4 py-3.5 text-xs text-gray-700 max-w-[120px] truncate">{plan.institution}</td>
                  <td className="px-4 py-3.5 whitespace-nowrap">
                    <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${plan.institutionType === 'University' ? 'bg-[#FEF3E6] text-[#C96B10]' : 'bg-[#EBF1FB] text-[#003087]'}`}>{plan.institutionType}</span>
                  </td>
                  <td className="px-4 py-3.5 text-xs text-gray-600 whitespace-nowrap">{plan.student}</td>
                  <td className="px-4 py-3.5 text-xs font-mono font-bold text-gray-800 whitespace-nowrap">
                    {plan.principal.toLocaleString()} EGP
                  </td>
                  <td className="px-4 py-3.5 text-xs text-center">
                    <span className="bg-[#003087]/10 text-[#003087] font-bold px-2 py-0.5 rounded text-[11px]">
                      {plan.tenor}m
                    </span>
                  </td>
                  <td className="px-4 py-3.5 text-xs font-mono font-semibold text-gray-700 whitespace-nowrap">
                    {plan.total.toLocaleString()} EGP
                  </td>
                  <td className="px-4 py-3.5 text-xs font-mono font-bold text-[#F7941D] whitespace-nowrap">
                    {plan.monthly.toLocaleString()} EGP
                  </td>
                  <td className="px-4 py-3.5 w-24">
                    <div className="flex items-center gap-1.5">
                      <div className="flex-1 h-1.5 bg-gray-100 rounded-full overflow-hidden">
                        <div
                          className="h-full bg-[#003087] rounded-full"
                          style={{ width: `${(plan.paidInstallments / plan.tenor) * 100}%` }}
                        />
                      </div>
                      <span className="text-[10px] text-gray-400 whitespace-nowrap font-mono">
                        {plan.paidInstallments}/{plan.tenor}
                      </span>
                    </div>
                  </td>
                  <td className="px-4 py-3.5">
                    <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${statusStyle[plan.status]}`}>
                      {plan.status}
                    </span>
                  </td>
                  <td className="px-4 py-3.5">
                    <button
                      onClick={() => setSelected(plan)}
                      className="p-1.5 rounded hover:bg-[#003087]/10 text-[#003087] transition-colors"
                    >
                      <EyeIcon className="w-3.5 h-3.5" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="flex items-center justify-between px-5 py-3.5 border-t border-gray-100">
          <span className="text-xs text-gray-400">Showing {filtered.length} of {epp.length} plans · Page 1 of 40</span>
          <div className="flex items-center gap-1">
            <button className="p-1.5 rounded border border-gray-200 hover:bg-gray-50 text-gray-400 transition-colors">
              <ChevronLeftIcon className="w-3.5 h-3.5" />
            </button>
            <button className="w-7 h-7 rounded bg-[#003087] text-white text-xs font-semibold">1</button>
            <button className="p-1.5 rounded border border-gray-200 hover:bg-gray-50 text-gray-400 transition-colors">
              <ChevronRightIcon className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
