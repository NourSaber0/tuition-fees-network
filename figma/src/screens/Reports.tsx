import { useState } from 'react'
import { DownloadIcon, CalendarIcon, FilterIcon, CheckCircleIcon, ReportIcon, XIcon, AlertIcon } from '../components/Icons'

/* ── Types ── */
interface ReportType {
  id: string
  title: string
  description: string
  category: string
  formats: string[]
  lastGenerated: string
  singleDate?: boolean
  contextFilters?: string[]
}

/* ── Report catalogue ── */
const reportTypes: ReportType[] = [
  { id: 'network-collections',     title: 'Network Collections',          description: 'Aggregate collection data across all participating institutions',       category: 'Collections',    formats: ['PDF', 'XLSX', 'CSV'], lastGenerated: '31 Aug 2026 08:00', contextFilters: ['feeType', 'paymentStatus'] },
  { id: 'collections-by-institution', title: 'Collections by Institution',  description: 'Per-institution breakdown with payment method and status trends',      category: 'Collections',    formats: ['PDF', 'XLSX'],        lastGenerated: '31 Aug 2026 08:00', contextFilters: ['feeType', 'paymentStatus'] },
  { id: 'daily-collections',       title: 'Daily Collections',            description: 'All collections for a selected date, broken down by institution',       category: 'Daily',          formats: ['PDF', 'XLSX', 'CSV'], lastGenerated: '31 Aug 2026 08:00', singleDate: true },
  { id: 'payments',                title: 'Payments Report',              description: 'Full payment register with status, method, and fee type',                category: 'Payments',       formats: ['PDF', 'XLSX', 'CSV'], lastGenerated: '30 Aug 2026 23:55', contextFilters: ['feeType', 'paymentStatus', 'paymentMethod'] },
  { id: 'failed-transactions',     title: 'Failed Transactions',          description: 'All failed transactions with decline reason codes and outstanding balances', category: 'Payments',       formats: ['XLSX', 'CSV'],        lastGenerated: '31 Aug 2026 06:00', contextFilters: ['paymentMethod'] },
  { id: 'epp-report',              title: 'EPP Portfolio Report',         description: 'All EPP plans — principal, tenor, repayment status, paid/outstanding',   category: 'EPP',            formats: ['PDF', 'XLSX'],        lastGenerated: '31 Aug 2026 07:30', contextFilters: ['eppTenor', 'eppStatus'] },
  { id: 'reconciliation',          title: 'Reconciliation Report',        description: 'Matched and exception reconciliation records across the network',         category: 'Reconciliation', formats: ['PDF', 'XLSX', 'CSV'], lastGenerated: '31 Aug 2026 09:00', contextFilters: ['reconStatus'] },
  { id: 'outstanding-balances',    title: 'Outstanding Balances',         description: 'Institutions and students with outstanding fee balances',                 category: 'Collections',    formats: ['PDF', 'XLSX'],        lastGenerated: '30 Aug 2026 23:55', contextFilters: ['feeType'] },
  { id: 'daily-report',            title: 'Daily Summary Report',         description: 'Auto-generated end-of-day operational summary with all activity',        category: 'Daily',          formats: ['PDF'],                lastGenerated: '30 Aug 2026 23:59', singleDate: true },
  { id: 'collections-by-type',    title: 'Collections by Institution Type', description: 'Schools vs Universities — side-by-side collection comparison',        category: 'Collections',    formats: ['PDF', 'XLSX', 'CSV'], lastGenerated: '31 Aug 2026 08:00' },
  { id: 'overdue-payments',       title: 'Overdue Payments Report',         description: 'All overdue fees with penalty amounts, days overdue, and grace period status', category: 'Deadlines', formats: ['PDF', 'XLSX', 'CSV'], lastGenerated: '7 Sep 2026 08:00', contextFilters: ['priority', 'paymentStatus'] },
  { id: 'deadline-summary',       title: 'Payment Deadline Summary',        description: 'Fee deadlines by priority tier — due today, this week, high, and medium', category: 'Deadlines', formats: ['PDF', 'XLSX'],        lastGenerated: '7 Sep 2026 08:00', contextFilters: ['priority', 'feeType'] },
]

const categoryColors: Record<string, string> = {
  Collections:    'bg-[#003087]/10 text-[#003087]',
  Payments:       'bg-green-50   text-green-700',
  EPP:            'bg-purple-50  text-purple-700',
  Reconciliation: 'bg-amber-50   text-amber-700',
  Daily:          'bg-gray-100   text-gray-600',
  Deadlines:      'bg-red-50     text-red-700',
}

/* ── Context filter options ── */
const FEE_TYPES      = ['Tuition', 'Activity Fee', 'Registration', 'Transport', 'Lab Fee', 'Bus Fee', 'Enrollment Fee']
const PAY_STATUSES   = ['Successful', 'Pending', 'Failed']
const PAY_METHODS    = ['Card', 'Bank Transfer', 'Cash', 'EPP', 'Wallet']
const EPP_TENORS     = ['3 Months', '6 Months', '12 Months', '18 Months']
const EPP_STATUSES   = ['Active', 'Completed', 'Defaulted', 'Cancelled']
const RECON_STATUSES = ['Matched', 'Exception', 'Pending']
const PRIORITIES     = ['Overdue', 'Urgent', 'High', 'Medium', 'Low', 'Paid']

/* ── Inline preview data ── */
interface PreviewCol { key: string; label: string; align?: 'right' | 'center' }
interface PreviewRow  { [k: string]: string | number }
interface Preview { cols: PreviewCol[]; rows: PreviewRow[]; total?: string; note?: string }

function buildPreview(id: string, dateFrom: string, dateTo: string, institution: string): Preview {
  const instLabel = institution || 'All Institutions'
  switch (id) {
    case 'network-collections':
    case 'collections-by-institution':
    case 'collections-by-type':
      return {
        cols: [
          { key: 'institution', label: 'Institution' },
          { key: 'type', label: 'Type', align: 'center' },
          { key: 'txCount', label: 'Transactions', align: 'right' },
          { key: 'amount', label: 'Collections (EGP)', align: 'right' },
          { key: 'status', label: 'Status', align: 'center' },
        ],
        rows: [
          { institution: 'Cairo International School', type: 'School',     txCount: 18,  amount: '285,600', status: 'Settled' },
          { institution: 'Nasr City Academy',          type: 'School',     txCount: 14,  amount: '189,000', status: 'Settled' },
          { institution: 'Heliopolis Academy',         type: 'School',     txCount: 21,  amount: '330,000', status: 'Settled' },
          { institution: 'Cairo University',           type: 'University', txCount: 142, amount: '1,840,000', status: 'Settled' },
          { institution: 'Ain Shams University',       type: 'University', txCount: 198, amount: '2,210,000', status: 'Settled' },
        ],
        total: 'EGP 4,854,600 across 393 transactions',
        note: `Network report · ${dateFrom} – ${dateTo} · ${instLabel}`,
      }
    case 'daily-collections':
      return {
        cols: [
          { key: 'institution', label: 'Institution' },
          { key: 'txCount', label: 'Transactions', align: 'right' },
          { key: 'amount', label: 'Amount (EGP)', align: 'right' },
          { key: 'settled', label: 'Settled', align: 'right' },
          { key: 'pending', label: 'Pending', align: 'right' },
        ],
        rows: [
          { institution: 'Cairo International School', txCount: 18, amount: '285,600', settled: '285,600', pending: '—' },
          { institution: 'Nasr City Academy',          txCount: 14, amount: '189,000', settled: '189,000', pending: '—' },
          { institution: 'Maadi British School',       txCount: 11, amount: '142,500', settled: '133,200', pending: '9,300' },
          { institution: 'Heliopolis Academy',         txCount: 21, amount: '330,000', settled: '330,000', pending: '—' },
          { institution: 'Alexandria International',   txCount: 16, amount: '214,800', settled: '214,800', pending: '—' },
        ],
        total: 'Daily total: EGP 1,162,100 · 80 transactions',
        note: `Daily collections · ${dateFrom}`,
      }
    case 'payments':
      return {
        cols: [
          { key: 'txId', label: 'Transaction ID' },
          { key: 'institution', label: 'Institution' },
          { key: 'fee', label: 'Fee Type' },
          { key: 'amount', label: 'Amount (EGP)', align: 'right' },
          { key: 'method', label: 'Method', align: 'center' },
          { key: 'status', label: 'Status', align: 'center' },
        ],
        rows: [
          { txId: 'TX-20260831-0001', institution: 'Cairo Int. School',  fee: 'Tuition Q3',    amount: '18,000', method: 'Card',          status: 'Successful' },
          { txId: 'TX-20260831-0005', institution: 'Ain Shams Univ.',    fee: 'Lab Fee',        amount: '3,800',  method: 'Card',          status: 'Successful' },
          { txId: 'TX-20260831-0006', institution: 'Nasr City Academy',  fee: 'Bus Fee',        amount: '3,200',  method: 'Card',          status: 'Successful' },
          { txId: 'TX-20260831-0002', institution: 'Cairo University',   fee: 'Semester Fee',   amount: '8,000',  method: 'Bank Transfer', status: 'Pending' },
          { txId: 'TX-20260831-0004', institution: 'Heliopolis Academy', fee: 'Tuition Q3',     amount: '22,000', method: 'Card',          status: 'Failed' },
        ],
        total: '1,284 total transactions in range',
        note: `Payments · ${dateFrom} – ${dateTo}`,
      }
    case 'failed-transactions':
      return {
        cols: [
          { key: 'txId', label: 'Transaction ID' },
          { key: 'institution', label: 'Institution' },
          { key: 'amount', label: 'Amount (EGP)', align: 'right' },
          { key: 'method', label: 'Method', align: 'center' },
          { key: 'reason', label: 'Failure Reason' },
        ],
        rows: [
          { txId: 'TX-20260831-0004', institution: 'Heliopolis Academy',   amount: '22,000', method: 'Card',   reason: 'Card declined' },
          { txId: 'TX-20260831-0012', institution: 'Cairo University',     amount: '8,500',  method: 'Wallet', reason: 'Insufficient wallet balance' },
          { txId: 'TX-20260830-0022', institution: 'Maadi British School', amount: '15,400', method: 'Card',   reason: 'Authorization timeout' },
        ],
        total: '48 failed transactions in range',
        note: `Failed transactions · ${dateFrom} – ${dateTo}`,
      }
    case 'epp-report':
      return {
        cols: [
          { key: 'planId', label: 'Plan ID' },
          { key: 'institution', label: 'Institution' },
          { key: 'principal', label: 'Principal (EGP)', align: 'right' },
          { key: 'tenor', label: 'Tenor', align: 'center' },
          { key: 'monthly', label: 'Monthly (EGP)', align: 'right' },
          { key: 'progress', label: 'Progress', align: 'center' },
          { key: 'status', label: 'Status', align: 'center' },
        ],
        rows: [
          { planId: 'EPP-2026-001', institution: 'Cairo Int. School',  principal: '24,000', tenor: '12m', monthly: '2,300',   progress: '3/12',  status: 'Active' },
          { planId: 'EPP-2026-002', institution: 'Heliopolis Academy', principal: '22,000', tenor: '6m',  monthly: '3,996.67', progress: '5/6',  status: 'Active' },
          { planId: 'EPP-2026-003', institution: 'Cairo University',   principal: '35,000', tenor: '12m', monthly: '3,354.17', progress: '4/12', status: 'Active' },
          { planId: 'EPP-2026-004', institution: 'Nasr City Academy',  principal: '15,500', tenor: '3m',  monthly: '5,476.67', progress: '3/3',  status: 'Completed' },
          { planId: 'EPP-2026-008', institution: 'Cairo Int. School',  principal: '20,000', tenor: '6m',  monthly: '3,633.33', progress: '2/6',  status: 'Defaulted' },
        ],
        total: '10 EPP plans · EGP 256,500 total principal',
        note: `EPP portfolio · ${dateFrom} – ${dateTo}`,
      }
    case 'reconciliation':
      return {
        cols: [
          { key: 'ref', label: 'Reference' },
          { key: 'institution', label: 'Institution' },
          { key: 'bank', label: 'Bank (EGP)', align: 'right' },
          { key: 'system', label: 'System (EGP)', align: 'right' },
          { key: 'school', label: 'School (EGP)', align: 'right' },
          { key: 'status', label: 'Status', align: 'center' },
        ],
        rows: [
          { ref: 'RC-20260831-001', institution: 'Cairo International School', bank: '285,600',   system: '285,600',   school: '285,600',   status: 'Matched' },
          { ref: 'RC-20260831-002', institution: 'Nasr City Academy',          bank: '198,400',   system: '198,400',   school: '189,000',   status: 'Exception' },
          { ref: 'RC-20260831-003', institution: 'Cairo University',           bank: '1,840,000', system: '1,840,000', school: '1,840,000', status: 'Matched' },
          { ref: 'RC-20260831-005', institution: 'Maadi British School',       bank: '142,500',   system: '151,000',   school: '142,500',   status: 'Exception' },
          { ref: 'RC-20260831-008', institution: 'October STEM School',        bank: '—',         system: '—',         school: '—',         status: 'Pending' },
        ],
        total: '1,278 matched · 3 exceptions · 3 pending',
        note: `Reconciliation report · ${dateFrom} – ${dateTo}`,
      }
    case 'outstanding-balances':
      return {
        cols: [
          { key: 'institution', label: 'Institution' },
          { key: 'student', label: 'Student' },
          { key: 'fee', label: 'Fee Type' },
          { key: 'outstanding', label: 'Outstanding (EGP)', align: 'right' },
          { key: 'dueDate', label: 'Due Date', align: 'center' },
        ],
        rows: [
          { institution: 'Cairo International School', student: 'Ahmed Hassan',  fee: 'Tuition Q3',     outstanding: '13,000', dueDate: '15 Sep 2026' },
          { institution: 'Maadi British School',       student: 'Sara Mohamed',  fee: 'Tuition Term 1', outstanding: '12,400', dueDate: '1 Sep 2026' },
          { institution: 'Cairo University',           student: 'Karim Nour',    fee: 'Semester Fee',   outstanding: '4,500',  dueDate: '30 Sep 2026' },
          { institution: 'Heliopolis Academy',         student: 'Reem Khalil',   fee: 'Activity Fee',   outstanding: '2,000',  dueDate: '1 Sep 2026' },
          { institution: 'Nasr City Academy',          student: 'Yasser Samir',  fee: 'Tuition Q3',     outstanding: '15,500', dueDate: '5 Sep 2026' },
        ],
        total: 'EGP 89,400 total outstanding across 47 accounts',
        note: `Outstanding balances · as of ${dateTo}`,
      }
    default:
      return {
        cols: [{ key: 'info', label: 'Report' }],
        rows: [{ info: 'Preview not available for this report type.' }],
        note: `${dateFrom} – ${dateTo}`,
      }
  }
}

/* ── Status cell styling ── */
function statusClass(val: string) {
  if (val === 'Matched' || val === 'Successful' || val === 'Settled' || val === 'Active' || val === 'Completed')
    return 'text-green-700 font-semibold'
  if (val === 'Exception' || val === 'Failed' || val === 'Defaulted')
    return 'text-red-600 font-semibold'
  if (val === 'Pending')
    return 'text-amber-600 font-semibold'
  return 'text-gray-600'
}

/* ── Reports screen (B12) ── */
export default function Reports() {
  const [selected,       setSelected]       = useState<string | null>(null)
  const [dateFrom,       setDateFrom]       = useState('2026-08-01')
  const [dateTo,         setDateTo]         = useState('2026-08-31')
  const [singleDate,     setSingleDate]     = useState('2026-08-31')
  const [school,         setSchool]         = useState('')
  const [feeType,        setFeeType]        = useState('')
  const [payStatus,      setPayStatus]      = useState('')
  const [payMethod,      setPayMethod]      = useState('')
  const [eppTenor,       setEppTenor]       = useState('')
  const [eppStatus,      setEppStatus]      = useState('')
  const [reconStatus,    setReconStatus]    = useState('')
  const [priority,       setPriority]       = useState('')
  const [format,         setFormat]         = useState('PDF')
  const [generating,     setGenerating]     = useState(false)
  const [generated,      setGenerated]      = useState<string | null>(null)
  const [filterCat,      setFilterCat]      = useState('All')

  const selectedReport = reportTypes.find(r => r.id === selected)
  const filtered       = reportTypes.filter(r => filterCat === 'All' || r.category === filterCat)
  const categories     = ['All', ...Array.from(new Set(reportTypes.map(r => r.category)))]

  const dateError = !selectedReport?.singleDate && dateFrom > dateTo

  const handleGenerate = async () => {
    if (!selected || dateError) return
    setGenerating(true)
    await new Promise(r => setTimeout(r, 1500))
    setGenerating(false)
    setGenerated(selected)
  }

  const handleSelect = (id: string) => {
    setSelected(id)
    setGenerated(null)
    const r = reportTypes.find(x => x.id === id)
    if (r?.formats && !r.formats.includes(format)) setFormat(r.formats[0])
  }

  const clearFilters = () => {
    setDateFrom('2026-08-01'); setDateTo('2026-08-31'); setSingleDate('2026-08-31')
    setSchool(''); setFeeType(''); setPayStatus(''); setPayMethod('')
    setEppTenor(''); setEppStatus(''); setReconStatus(''); setPriority('')
    setGenerated(null)
  }

  const activeFilterCount = [school, feeType, payStatus, payMethod, eppTenor, eppStatus, reconStatus, priority].filter(Boolean).length

  const ctxFilters = selectedReport?.contextFilters ?? []
  const effectiveDate = selectedReport?.singleDate ? singleDate : undefined

  const preview = generated === selected && selectedReport
    ? buildPreview(selected, selectedReport.singleDate ? singleDate : dateFrom, selectedReport.singleDate ? singleDate : dateTo, school)
    : null

  return (
    <div className="space-y-4">
      {/* Category filter tabs (US-68: Report type filter) */}
      <div className="flex items-center gap-2 flex-wrap">
        {categories.map(cat => (
          <button key={cat} onClick={() => setFilterCat(cat)}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${filterCat === cat ? 'bg-[#003087] text-white' : 'bg-white border border-[#DDE3EF] text-gray-500 hover:bg-gray-50'}`}>
            {cat}
            {cat !== 'All' && <span className="ml-1 opacity-60">({reportTypes.filter(r => r.category === cat).length})</span>}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-3 gap-4">
        {/* Report type list */}
        <div className="col-span-2 space-y-2">
          {filtered.map(report => (
            <div key={report.id} onClick={() => handleSelect(report.id)}
              className={`bg-white rounded-xl border p-4 cursor-pointer transition-all ${selected === report.id ? 'border-[#003087] ring-1 ring-blue-200' : 'border-[#E8EDF5] hover:border-[#003087]/30'}`}>
              <div className="flex items-start justify-between">
                <div className="flex items-start gap-3">
                  <div className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 ${categoryColors[report.category]}`}>
                    <ReportIcon className="w-4 h-4" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-semibold text-gray-800">{report.title}</span>
                      <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${categoryColors[report.category]}`}>{report.category}</span>
                      {report.singleDate && <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-gray-100 text-gray-400">Single Date</span>}
                    </div>
                    <p className="text-xs text-gray-400 mt-0.5">{report.description}</p>
                    <div className="flex items-center gap-2 mt-1.5">
                      {report.formats.map(f => (
                        <span key={f} className="text-[10px] font-semibold text-gray-400 border border-gray-200 rounded px-1.5 py-0.5">{f}</span>
                      ))}
                    </div>
                  </div>
                </div>
                <div className="text-right shrink-0 ml-3">
                  <div className="text-[10px] text-gray-400">Last generated</div>
                  <div className="text-[11px] font-semibold text-gray-600 whitespace-nowrap">{report.lastGenerated}</div>
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Generate + filter panel */}
        <div className="space-y-3">
          <div className="bg-white rounded-xl border border-[#E8EDF5] p-5">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-semibold text-[#1B2A4A]">Generate Report</h3>
              {/* US-68: Clear filters */}
              {(activeFilterCount > 0 || dateFrom !== '2026-08-01' || dateTo !== '2026-08-31') && (
                <button onClick={clearFilters} className="flex items-center gap-1 text-[11px] font-semibold text-gray-400 hover:text-red-500 transition-colors">
                  <XIcon className="w-3 h-3" /> Clear
                  {activeFilterCount > 0 && <span className="ml-0.5 bg-[#003087] text-white text-[10px] px-1.5 py-0.5 rounded-full">{activeFilterCount}</span>}
                </button>
              )}
            </div>

            {!selectedReport ? (
              <div className="text-center py-8 text-gray-300">
                <ReportIcon className="w-8 h-8 mx-auto mb-2 opacity-40" />
                <p className="text-xs">Select a report type to configure and generate</p>
              </div>
            ) : (
              <div className="space-y-4">
                <div className="bg-[#F8FAFD] rounded-lg p-3">
                  <div className="text-xs font-bold text-[#1B2A4A]">{selectedReport.title}</div>
                  <div className="text-[11px] text-gray-400 mt-0.5">{selectedReport.description}</div>
                </div>

                {/* US-65: Single date for daily reports */}
                {selectedReport.singleDate ? (
                  <div>
                    <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Collection Date</label>
                    <div className="relative">
                      <CalendarIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-300" />
                      <input type="date" value={singleDate} onChange={e => { setSingleDate(e.target.value); setGenerated(null) }}
                        className="w-full pl-8 border border-[#DDE3EF] rounded-lg px-3 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]" />
                    </div>
                  </div>
                ) : (
                  <div>
                    <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Date Range</label>
                    <div className="grid grid-cols-2 gap-2">
                      <input type="date" value={dateFrom} onChange={e => { setDateFrom(e.target.value); setGenerated(null) }}
                        className={`border rounded-lg px-2.5 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] ${dateError ? 'border-red-400 bg-red-50' : 'border-[#DDE3EF]'}`} />
                      <input type="date" value={dateTo} onChange={e => { setDateTo(e.target.value); setGenerated(null) }}
                        className={`border rounded-lg px-2.5 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] ${dateError ? 'border-red-400 bg-red-50' : 'border-[#DDE3EF]'}`} />
                    </div>
                    {/* US-68: Date validation */}
                    {dateError && (
                      <div className="flex items-center gap-1.5 mt-1.5 text-[11px] text-red-500">
                        <AlertIcon className="w-3 h-3" /> "From" date must be before "To" date.
                      </div>
                    )}
                  </div>
                )}

                {/* Institution filter */}
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Institution</label>
                  <select value={school} onChange={e => { setSchool(e.target.value); setGenerated(null) }}
                    className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]">
                    <option value="">All Institutions (Network)</option>
                    <option>Cairo International School</option>
                    <option>Maadi British School</option>
                    <option>Heliopolis Academy</option>
                    <option>Nasr City Academy</option>
                    <option>Alexandria International</option>
                    <option>Cairo University</option>
                    <option>Ain Shams University</option>
                    <option>American University in Cairo</option>
                  </select>
                </div>

                {/* US-68: Context-sensitive filters */}
                {ctxFilters.includes('feeType') && (
                  <div>
                    <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Fee Type</label>
                    <select value={feeType} onChange={e => { setFeeType(e.target.value); setGenerated(null) }}
                      className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]">
                      <option value="">All Fee Types</option>
                      {FEE_TYPES.map(f => <option key={f}>{f}</option>)}
                    </select>
                  </div>
                )}

                {ctxFilters.includes('paymentStatus') && (
                  <div>
                    <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Payment Status</label>
                    <select value={payStatus} onChange={e => { setPayStatus(e.target.value); setGenerated(null) }}
                      className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]">
                      <option value="">All Statuses</option>
                      {PAY_STATUSES.map(s => <option key={s}>{s}</option>)}
                    </select>
                  </div>
                )}

                {ctxFilters.includes('paymentMethod') && (
                  <div>
                    <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Payment Method</label>
                    <select value={payMethod} onChange={e => { setPayMethod(e.target.value); setGenerated(null) }}
                      className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]">
                      <option value="">All Methods</option>
                      {PAY_METHODS.map(m => <option key={m}>{m}</option>)}
                    </select>
                  </div>
                )}

                {/* US-66: EPP-specific filters */}
                {ctxFilters.includes('eppTenor') && (
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Tenor</label>
                      <select value={eppTenor} onChange={e => { setEppTenor(e.target.value); setGenerated(null) }}
                        className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]">
                        <option value="">All Tenors</option>
                        {EPP_TENORS.map(t => <option key={t}>{t}</option>)}
                      </select>
                    </div>
                    <div>
                      <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">EPP Status</label>
                      <select value={eppStatus} onChange={e => { setEppStatus(e.target.value); setGenerated(null) }}
                        className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]">
                        <option value="">All Statuses</option>
                        {EPP_STATUSES.map(s => <option key={s}>{s}</option>)}
                      </select>
                    </div>
                  </div>
                )}

                {/* US-67: Reconciliation status filter */}
                {ctxFilters.includes('reconStatus') && (
                  <div>
                    <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Reconciliation Status</label>
                    <select value={reconStatus} onChange={e => { setReconStatus(e.target.value); setGenerated(null) }}
                      className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]">
                      <option value="">All Statuses</option>
                      {RECON_STATUSES.map(s => <option key={s}>{s}</option>)}
                    </select>
                  </div>
                )}

                {/* Priority filter (deadline reports) */}
                {ctxFilters.includes('priority') && (
                  <div>
                    <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Payment Priority</label>
                    <select value={priority} onChange={e => { setPriority(e.target.value); setGenerated(null) }}
                      className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]">
                      <option value="">All Priorities</option>
                      {PRIORITIES.map(p => <option key={p}>{p}</option>)}
                    </select>
                  </div>
                )}

                {/* Format selector */}
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Export Format</label>
                  <div className="flex gap-2">
                    {selectedReport.formats.map(f => (
                      <button key={f} onClick={() => setFormat(f)}
                        className={`flex-1 py-2 text-xs font-bold rounded-lg border transition-all ${format === f ? 'bg-[#003087] text-white border-[#003087]' : 'border-[#DDE3EF] text-gray-500 hover:border-[#003087]/40'}`}>
                        {f}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Active filters summary */}
                {activeFilterCount > 0 && (
                  <div className="flex flex-wrap gap-1.5">
                    {school      && <span className="text-[10px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">{school}</span>}
                    {feeType     && <span className="text-[10px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">{feeType}</span>}
                    {payStatus   && <span className="text-[10px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">{payStatus}</span>}
                    {payMethod   && <span className="text-[10px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">{payMethod}</span>}
                    {eppTenor    && <span className="text-[10px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">{eppTenor}</span>}
                    {eppStatus   && <span className="text-[10px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">{eppStatus}</span>}
                    {reconStatus && <span className="text-[10px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">{reconStatus}</span>}
                  </div>
                )}

                {/* Generate / Ready state */}
                {generated === selected ? (
                  <div className="flex items-center gap-2 bg-green-50 border border-green-200 rounded-lg px-3 py-2.5">
                    <CheckCircleIcon className="w-4 h-4 text-green-600 shrink-0" />
                    <div className="flex-1 min-w-0">
                      <div className="text-xs font-semibold text-green-800">Report ready</div>
                      <div className="text-[11px] text-green-600 truncate">
                        report_{effectiveDate ?? `${dateFrom}_${dateTo}`}.{format.toLowerCase()}
                      </div>
                    </div>
                    <button className="p-1.5 bg-green-600 rounded text-white hover:bg-green-700 transition-colors shrink-0">
                      <DownloadIcon className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ) : (
                  <button onClick={handleGenerate} disabled={generating || dateError}
                    className="w-full bg-[#003087] hover:bg-[#002060] text-white font-semibold py-2.5 rounded-lg text-sm transition-colors flex items-center justify-center gap-2 disabled:opacity-50">
                    {generating
                      ? <><span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" /> Generating…</>
                      : <><DownloadIcon className="w-4 h-4" /> Generate &amp; Download</>}
                  </button>
                )}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* US-64–67: Inline preview after generation */}
      {preview && (
        <div className="bg-white rounded-xl border border-[#E8EDF5] overflow-hidden">
          <div className="flex items-center justify-between px-5 py-3.5 border-b border-gray-100">
            <div>
              <h3 className="text-sm font-semibold text-[#1B2A4A]">{selectedReport?.title} — Preview</h3>
              <p className="text-[11px] text-gray-400 mt-0.5">{preview.note} · Showing first {preview.rows.length} records</p>
            </div>
            <button className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-gray-500 hover:bg-gray-50 border border-gray-200 transition-colors">
              <DownloadIcon className="w-3.5 h-3.5" /> Download Full Report
            </button>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="bg-[#F8FAFD]">
                  {preview.cols.map(col => (
                    <th key={col.key} className={`px-5 py-3 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap ${col.align === 'right' ? 'text-right' : col.align === 'center' ? 'text-center' : 'text-left'}`}>
                      {col.label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {preview.rows.map((row, i) => (
                  <tr key={i} className="hover:bg-[#F8FAFD] transition-colors">
                    {preview.cols.map(col => {
                      const val = String(row[col.key] ?? '—')
                      const isStatus = col.key === 'status'
                      const isMismatch = (col.key === 'system' || col.key === 'school') &&
                        selectedReport?.id === 'reconciliation' &&
                        val !== String(preview.rows[i]['bank'] ?? '')
                      return (
                        <td key={col.key}
                          className={`px-5 py-3.5 whitespace-nowrap ${
                            col.align === 'right'  ? 'text-right font-mono' :
                            col.align === 'center' ? 'text-center' : ''
                          } ${isStatus ? statusClass(val) : isMismatch ? 'text-red-600 font-semibold font-mono' : 'text-gray-700'}`}>
                          {col.key === 'txId' || col.key === 'planId' || col.key === 'ref'
                            ? <span className="font-mono text-[#003087] font-semibold">{val}</span>
                            : val}
                        </td>
                      )
                    })}
                  </tr>
                ))}
              </tbody>
              {preview.total && (
                <tfoot>
                  <tr className="bg-[#F8FAFD] border-t border-[#E8EDF5]">
                    <td colSpan={preview.cols.length} className="px-5 py-3 text-[11px] font-semibold text-gray-500">
                      {preview.total}
                    </td>
                  </tr>
                </tfoot>
              )}
            </table>
          </div>
        </div>
      )}
    </div>
  )
}
