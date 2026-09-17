import { useState } from 'react'
import {
  SearchIcon, EyeIcon, CheckIcon, XIcon, PlusIcon,
  ChevronLeftIcon, ChevronRightIcon, SchoolIcon,
  TransactionIcon, ReconcileIcon, ReportIcon, AlertIcon
} from '../components/Icons'

type RegStatus = 'Approved' | 'Pending' | 'Under Review' | 'Rejected'
type IntStatus = 'Integrated' | 'Pending' | 'Failed' | 'Not Integrated'
type AccountStatus = 'Active' | 'Inactive' | 'Suspended'
type InstitutionType = 'School' | 'University'

interface Institution {
  id: string
  name: string
  city: string
  institutionType: InstitutionType
  subType: string
  principal: string
  phone: string
  email: string
  regStatus: RegStatus
  regNumber: string
  students: number
  feesSubmitted: string
  collections: string
  integStatus: IntStatus
  accountStatus: AccountStatus
  since: string
}

interface FeeSubmission {
  id: string
  term: string
  date: string
  students: number
  amount: string
  status: 'Processed' | 'Pending' | 'Rejected'
}

interface SettlementRecord {
  id: string
  date: string
  gross: string
  fee: string
  net: string
  status: 'Completed' | 'Processing' | 'Pending' | 'Failed'
  txRef: string
  reconRef: string
}

/* ── Seed data ── */
const BASE_INSTITUTIONS: Institution[] = [
  { id: 'SCH-001', name: 'Cairo International School',  city: 'Cairo',      institutionType: 'School',     subType: 'International', principal: 'Dr. Ahmad Fawzy',        phone: '+20 2 2516 0000',  email: 'admin@cis.edu.eg',             regStatus: 'Approved',      regNumber: 'MOEDU-SCH-2024-0112', students: 850,   feesSubmitted: 'EGP 15,300,000',  collections: 'EGP 14,892,000',  integStatus: 'Integrated',     accountStatus: 'Active',   since: '12 Jan 2024' },
  { id: 'SCH-002', name: 'Maadi British School',         city: 'Cairo',      institutionType: 'School',     subType: 'International', principal: 'Ms. Sarah Williams',     phone: '+20 2 2358 4444',  email: 'bursar@mbs.edu.eg',            regStatus: 'Approved',      regNumber: 'MOEDU-SCH-2024-0205', students: 620,   feesSubmitted: 'EGP 12,400,000',  collections: 'EGP 12,100,000',  integStatus: 'Integrated',     accountStatus: 'Active',   since: '5 Feb 2024'  },
  { id: 'SCH-003', name: 'Egyptian Language School',     city: 'Giza',       institutionType: 'School',     subType: 'National',      principal: 'Mr. Hossam Ragab',       phone: '+20 2 3745 2222',  email: 'info@els.edu.eg',              regStatus: 'Pending',       regNumber: 'MOEDU-SCH-2026-0828', students: 340,   feesSubmitted: 'EGP 0',           collections: 'EGP 0',           integStatus: 'Not Integrated', accountStatus: 'Inactive', since: '28 Aug 2026' },
  { id: 'SCH-004', name: 'Heliopolis Academy',           city: 'Cairo',      institutionType: 'School',     subType: 'National',      principal: 'Dr. Nadia Salama',       phone: '+20 2 2418 7800',  email: 'admin@hac.edu.eg',             regStatus: 'Approved',      regNumber: 'MOEDU-SCH-2024-0318', students: 1200,  feesSubmitted: 'EGP 26,400,000',  collections: 'EGP 25,960,000',  integStatus: 'Integrated',     accountStatus: 'Active',   since: '18 Mar 2024' },
  { id: 'SCH-005', name: 'New Cairo International',      city: 'New Cairo',  institutionType: 'School',     subType: 'International', principal: 'Mr. Karim Mansour',      phone: '+20 2 2618 9900',  email: 'finance@nci.edu.eg',           regStatus: 'Under Review',  regNumber: 'MOEDU-SCH-2026-0829', students: 520,   feesSubmitted: 'EGP 0',           collections: 'EGP 0',           integStatus: 'Pending',        accountStatus: 'Inactive', since: '29 Aug 2026' },
  { id: 'SCH-006', name: 'October STEM School',          city: '6th October',institutionType: 'School',     subType: 'STEM',          principal: 'Dr. Amr Hassan',         phone: '+20 38 3621 1100', email: 'stem@october-school.edu.eg',   regStatus: 'Rejected',      regNumber: 'MOEDU-SCH-2026-0720', students: 280,   feesSubmitted: 'EGP 0',           collections: 'EGP 0',           integStatus: 'Failed',         accountStatus: 'Inactive', since: '20 Jul 2026' },
  { id: 'SCH-007', name: 'Alexandria International',     city: 'Alexandria', institutionType: 'School',     subType: 'International', principal: 'Ms. Rania Aziz',         phone: '+20 3 4285 6600',  email: 'registrar@ais.edu.eg',         regStatus: 'Approved',      regNumber: 'MOEDU-SCH-2024-0402', students: 730,   feesSubmitted: 'EGP 18,250,000',  collections: 'EGP 17,840,000',  integStatus: 'Integrated',     accountStatus: 'Active',   since: '2 Apr 2024'  },
  { id: 'SCH-008', name: 'Nasr City Academy',            city: 'Cairo',      institutionType: 'School',     subType: 'National',      principal: 'Dr. Tarek Badawi',       phone: '+20 2 2625 3300',  email: 'admin@nca.edu.eg',             regStatus: 'Approved',      regNumber: 'MOEDU-SCH-2024-0508', students: 910,   feesSubmitted: 'EGP 16,380,000',  collections: 'EGP 16,015,000',  integStatus: 'Integrated',     accountStatus: 'Active',   since: '8 May 2024'  },
  { id: 'UNI-001', name: 'Cairo University',             city: 'Giza',       institutionType: 'University', subType: 'Public',        principal: 'Prof. Mohamed El-Khatib',phone: '+20 2 3567 8000',  email: 'finance@cu.edu.eg',            regStatus: 'Approved',      regNumber: 'MOHE-UNI-2025-0101',  students: 18400, feesSubmitted: 'EGP 92,000,000',  collections: 'EGP 89,740,000',  integStatus: 'Integrated',     accountStatus: 'Active',   since: '1 Jan 2025'  },
  { id: 'UNI-002', name: 'Ain Shams University',         city: 'Cairo',      institutionType: 'University', subType: 'Public',        principal: 'Prof. Amr Adly',         phone: '+20 2 2682 4000',  email: 'accounts@asu.edu.eg',          regStatus: 'Approved',      regNumber: 'MOHE-UNI-2025-0115',  students: 21200, feesSubmitted: 'EGP 106,000,000', collections: 'EGP 103,200,000', integStatus: 'Integrated',     accountStatus: 'Active',   since: '15 Jan 2025' },
  { id: 'UNI-003', name: 'American University in Cairo', city: 'New Cairo',  institutionType: 'University', subType: 'Private',       principal: 'Dr. Ehab Abdelrahman',   phone: '+20 2 2615 1000',  email: 'studentfinance@aucegypt.edu',  regStatus: 'Approved',      regNumber: 'MOHE-UNI-2025-0203',  students: 6800,  feesSubmitted: 'EGP 204,000,000', collections: 'EGP 199,680,000', integStatus: 'Integrated',     accountStatus: 'Active',   since: '3 Feb 2025'  },
  { id: 'UNI-004', name: 'Mansoura University',          city: 'Mansoura',   institutionType: 'University', subType: 'Public',        principal: 'Prof. Sherif Sabri',     phone: '+20 50 2244 444',  email: 'finance@mans.edu.eg',          regStatus: 'Under Review',  regNumber: 'MOHE-UNI-2026-0825',  students: 14600, feesSubmitted: 'EGP 0',           collections: 'EGP 0',           integStatus: 'Pending',        accountStatus: 'Inactive', since: '25 Aug 2026' },
  { id: 'UNI-005', name: 'Future University in Egypt',   city: 'New Cairo',  institutionType: 'University', subType: 'Private',       principal: 'Dr. Tarek Fahmy',        phone: '+20 2 2618 8500',  email: 'fin@fue.edu.eg',               regStatus: 'Pending',       regNumber: 'MOHE-UNI-2026-0830',  students: 5200,  feesSubmitted: 'EGP 0',           collections: 'EGP 0',           integStatus: 'Not Integrated', accountStatus: 'Inactive', since: '30 Aug 2026' },
]

/* ── Data helpers (US-14, US-15) ── */
function parseEGP(s: string): number {
  return parseInt(s.replace(/[^0-9]/g, '')) || 0
}
function fmtMoney(n: number): string {
  return 'EGP ' + Math.round(n).toLocaleString()
}

function getFeeSubmissions(inst: Institution): FeeSubmission[] {
  if (inst.integStatus !== 'Integrated' || inst.regStatus !== 'Approved') return []
  const total = parseEGP(inst.feesSubmitted)
  const isUni = inst.institutionType === 'University'
  const terms  = isUni
    ? ['Fall Semester 2026/27',    'Summer 2026',     'Spring Semester 2025/26', 'Fall Semester 2025/26']
    : ['Term 1 2026/27',           'Summer Term 2026','Term 2 2025/26',          'Term 1 2025/26']
  const dates  = ['28 Aug 2026, 09:15','14 Jul 2026, 14:30','10 Jan 2026, 10:00','5 Sep 2025, 08:45']
  const amtP   = [0.28, 0.17, 0.32, 0.23]
  const stuP   = [0.28, 0.15, 0.30, 0.27]
  return amtP.map((p, i) => ({
    id: `FS-${inst.id}-00${i + 1}`,
    term: terms[i],
    date: dates[i],
    students: Math.round(inst.students * stuP[i]),
    amount: fmtMoney(Math.round(total * p)),
    status: 'Processed' as const,
  }))
}

function getSettlements(inst: Institution): SettlementRecord[] {
  if (inst.integStatus !== 'Integrated' || inst.regStatus !== 'Approved') return []
  const total = parseEGP(inst.collections)
  const dates    = ['30 Aug 2026','15 Jul 2026','12 Jan 2026','8 Sep 2025']
  const dateKeys = ['20260830',   '20260715',   '20260112',   '20250908']
  const amtP     = [0.28, 0.17, 0.32, 0.23]
  return amtP.map((p, i) => {
    const gross = Math.round(total * p)
    const fee   = Math.round(gross * 0.02)
    return {
      id: `SET-${inst.id}-00${i + 1}`,
      date: dates[i],
      gross: fmtMoney(gross),
      fee: fmtMoney(fee),
      net: fmtMoney(gross - fee),
      status: 'Completed' as const,
      txRef: `TX-${dateKeys[i]}-${String(i + 1).padStart(4, '0')}`,
      reconRef: `RECON-${dateKeys[i]}`,
    }
  })
}

/* ── Style maps ── */
const regStatusStyle: Record<RegStatus, string> = {
  Approved:       'bg-green-50 text-green-700 border-green-200',
  Pending:        'bg-amber-50 text-amber-700 border-amber-200',
  'Under Review': 'bg-blue-50  text-blue-700  border-blue-200',
  Rejected:       'bg-red-50   text-red-700   border-red-200',
}
const integStyle: Record<IntStatus, string> = {
  Integrated:       'bg-green-50 text-green-700 border-green-200',
  Pending:          'bg-amber-50 text-amber-700 border-amber-200',
  Failed:           'bg-red-50   text-red-700   border-red-200',
  'Not Integrated': 'bg-gray-100 text-gray-500  border-gray-200',
}
const accountStyle: Record<AccountStatus, string> = {
  Active:    'bg-green-50 text-green-700 border-green-200',
  Inactive:  'bg-gray-100 text-gray-500  border-gray-200',
  Suspended: 'bg-red-50   text-red-700   border-red-200',
}
const settlementStyle: Record<SettlementRecord['status'], string> = {
  Completed:  'bg-green-50 text-green-700 border-green-200',
  Processing: 'bg-blue-50  text-blue-700  border-blue-200',
  Pending:    'bg-amber-50 text-amber-700 border-amber-200',
  Failed:     'bg-red-50   text-red-700   border-red-200',
}

const DETAIL_TABS = ['Institution Information','Students','Integration Status','Fee Submissions','Settlement','Transactions','Reconciliation','Reports']

type View = 'list' | 'detail' | 'approval'

/* ── Shared presentational components ── */
function StatusBadge({ label, style }: { label: string; style: string }) {
  return <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold border ${style}`}>{label}</span>
}

function InstitutionTypePill({ type }: { type: InstitutionType }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold ${
      type === 'University' ? 'bg-[#FEF3E6] text-[#C96B10]' : 'bg-[#EBF1FB] text-[#003087]'
    }`}>{type}</span>
  )
}

/* ── Register Institution modal (US-06) ── */
const SCHOOL_SUB_TYPES = ['International','National','STEM','Language','Private']
const UNI_SUB_TYPES    = ['Public','Private','International']
const CITIES = ['Cairo','Giza','Alexandria','New Cairo','6th October','Heliopolis','Mansoura','Suez','Ismailia','Port Said','Aswan','Luxor','Other']

interface RegForm {
  institutionType: InstitutionType | ''
  name: string; regNumber: string; city: string; subType: string
  principal: string; phone: string; email: string; students: string
}
type RegErrors = Partial<Record<keyof RegForm, true>>

function RegisterModal({ onClose, onRegister, nextSchool, nextUni }: {
  onClose: () => void
  onRegister: (inst: Institution) => void
  nextSchool: number
  nextUni: number
}) {
  const [form, setForm] = useState<RegForm>({ institutionType:'',name:'',regNumber:'',city:'',subType:'',principal:'',phone:'',email:'',students:'' })
  const [errors, setErrors] = useState<RegErrors>({})
  const [newId, setNewId] = useState<string | null>(null)

  const set = (k: keyof RegForm, v: string) => {
    setForm(f => ({ ...f, [k]: v, ...(k === 'institutionType' ? { subType:'' } : {}) }))
    setErrors(e => { const n = { ...e }; delete n[k]; return n })
  }

  const subTypes = form.institutionType === 'School' ? SCHOOL_SUB_TYPES : form.institutionType === 'University' ? UNI_SUB_TYPES : []

  const submit = () => {
    const required: (keyof RegForm)[] = ['institutionType','name','regNumber','city','subType','principal','phone','email','students']
    const errs: RegErrors = {}
    required.forEach(k => { if (!form[k]) errs[k] = true })
    if (Object.keys(errs).length) { setErrors(errs); return }
    const id = form.institutionType === 'School'
      ? `SCH-${String(nextSchool).padStart(3,'0')}`
      : `UNI-${String(nextUni).padStart(3,'0')}`
    onRegister({
      id, name: form.name, city: form.city,
      institutionType: form.institutionType as InstitutionType,
      subType: form.subType, principal: form.principal,
      phone: form.phone, email: form.email,
      regStatus: 'Pending', regNumber: form.regNumber,
      students: parseInt(form.students) || 0,
      feesSubmitted: 'EGP 0', collections: 'EGP 0',
      integStatus: 'Not Integrated', accountStatus: 'Inactive', since: '1 Sep 2026',
    })
    setNewId(id)
  }

  const fc = (k: keyof RegForm) =>
    `w-full border rounded-lg px-3 py-2.5 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] ${errors[k] ? 'border-red-400 bg-red-50' : 'border-[#DDE3EF]'}`

  if (newId) return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
      <div className="bg-white rounded-2xl shadow-2xl p-8 w-full max-w-md mx-4 text-center">
        <div className="w-14 h-14 rounded-full bg-green-50 flex items-center justify-center mx-auto mb-4">
          <CheckIcon className="w-7 h-7 text-green-600" />
        </div>
        <h3 className="text-lg font-bold text-[#1B2A4A] mb-1">Registration Submitted</h3>
        <p className="text-sm text-gray-500 mb-4">
          Registered with ID <strong className="font-mono text-[#003087]">{newId}</strong> · Status set to <strong className="text-amber-700">Pending Review</strong>.
        </p>
        <div className="bg-[#F4F6F9] rounded-lg px-4 py-3 text-xs text-left mb-5 space-y-1.5">
          <div className="flex justify-between"><span className="text-gray-400 font-semibold">Institution ID</span><span className="font-mono text-[#003087]">{newId}</span></div>
          <div className="flex justify-between"><span className="text-gray-400 font-semibold">Initial Status</span><span className="text-amber-700 font-semibold">Pending Review</span></div>
          <div className="flex justify-between"><span className="text-gray-400 font-semibold">Submitted</span><span>1 Sep 2026</span></div>
        </div>
        <button onClick={onClose} className="w-full py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors">Close</button>
      </div>
    </div>
  )

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 overflow-y-auto py-6">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-xl mx-4">
        <div className="flex items-center justify-between px-6 pt-6 pb-4 border-b border-gray-100">
          <div>
            <h3 className="text-base font-bold text-[#1B2A4A]">Register New Institution</h3>
            <p className="text-xs text-gray-400 mt-0.5">All fields marked * are required</p>
          </div>
          <button onClick={onClose} className="p-1.5 rounded-lg hover:bg-gray-100 text-gray-400 transition-colors"><XIcon className="w-4 h-4" /></button>
        </div>
        <div className="p-6 space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">Institution Type *</label>
              <select value={form.institutionType} onChange={e => set('institutionType', e.target.value)} className={fc('institutionType')}>
                <option value="">Select type…</option>
                <option value="School">School</option>
                <option value="University">University</option>
              </select>
              {errors.institutionType && <p className="text-[11px] text-red-500 mt-0.5">Required</p>}
            </div>
            <div>
              <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">Sub-type *</label>
              <select value={form.subType} onChange={e => set('subType', e.target.value)} disabled={!form.institutionType} className={fc('subType') + (!form.institutionType ? ' opacity-50 cursor-not-allowed' : '')}>
                <option value="">Select sub-type…</option>
                {subTypes.map(s => <option key={s}>{s}</option>)}
              </select>
              {errors.subType && <p className="text-[11px] text-red-500 mt-0.5">Required</p>}
            </div>
          </div>
          <div>
            <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">Institution Name *</label>
            <input type="text" placeholder="Full legal institution name" value={form.name} onChange={e => set('name', e.target.value)} className={fc('name')} />
            {errors.name && <p className="text-[11px] text-red-500 mt-0.5">Required</p>}
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">Registration Number *</label>
              <input type="text" placeholder={form.institutionType === 'University' ? 'MOHE-UNI-YYYY-XXXX' : 'MOEDU-SCH-YYYY-XXXX'} value={form.regNumber} onChange={e => set('regNumber', e.target.value)} className={fc('regNumber')} />
              {errors.regNumber && <p className="text-[11px] text-red-500 mt-0.5">Required</p>}
            </div>
            <div>
              <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">City *</label>
              <select value={form.city} onChange={e => set('city', e.target.value)} className={fc('city')}>
                <option value="">Select city…</option>
                {CITIES.map(c => <option key={c}>{c}</option>)}
              </select>
              {errors.city && <p className="text-[11px] text-red-500 mt-0.5">Required</p>}
            </div>
          </div>
          <div>
            <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">{form.institutionType === 'University' ? 'Vice Chancellor / President *' : 'Principal Name *'}</label>
            <input type="text" placeholder="Full name and title" value={form.principal} onChange={e => set('principal', e.target.value)} className={fc('principal')} />
            {errors.principal && <p className="text-[11px] text-red-500 mt-0.5">Required</p>}
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">Phone *</label>
              <input type="tel" placeholder="+20 X XXXX XXXX" value={form.phone} onChange={e => set('phone', e.target.value)} className={fc('phone')} />
              {errors.phone && <p className="text-[11px] text-red-500 mt-0.5">Required</p>}
            </div>
            <div>
              <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">Email *</label>
              <input type="email" placeholder="finance@institution.edu.eg" value={form.email} onChange={e => set('email', e.target.value)} className={fc('email')} />
              {errors.email && <p className="text-[11px] text-red-500 mt-0.5">Required</p>}
            </div>
          </div>
          <div>
            <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">{form.institutionType === 'University' ? 'Enrolled Students *' : 'Number of Students *'}</label>
            <input type="number" min="1" placeholder="e.g. 500" value={form.students} onChange={e => set('students', e.target.value)} className={fc('students')} />
            {errors.students && <p className="text-[11px] text-red-500 mt-0.5">Required</p>}
          </div>
        </div>
        <div className="flex gap-3 px-6 pb-6">
          <button onClick={onClose} className="flex-1 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">Cancel</button>
          <button onClick={submit} className="flex-1 py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors">Register Institution</button>
        </div>
      </div>
    </div>
  )
}

/* ── Approval view (US-08, US-09, US-10) ── */
function ApprovalView({ institution, onBack, onApproved, onRejected }: {
  institution: Institution
  onBack: () => void
  onApproved: (id: string) => void
  onRejected: (id: string) => void
}) {
  const [approveModal, setApproveModal] = useState(false)
  const [rejectModal,  setRejectModal]  = useState(false)
  const [rejectReason, setRejectReason] = useState('')
  const [done, setDone] = useState<'approved' | 'rejected' | null>(null)

  const confirmApprove = () => { setApproveModal(false); setDone('approved'); onApproved(institution.id) }
  const confirmReject  = () => { setRejectModal(false);  setDone('rejected'); onRejected(institution.id) }

  return (
    <div className="space-y-4">
      <button onClick={onBack} className="flex items-center gap-1 text-sm text-gray-400 hover:text-gray-700 transition-colors">
        <ChevronLeftIcon className="w-4 h-4" /> Back to Institutions
      </button>

      {done && (
        <div className={`flex items-center gap-2 rounded-xl px-5 py-3.5 text-sm font-semibold border ${done === 'approved' ? 'bg-green-50 border-green-200 text-green-800' : 'bg-red-50 border-red-200 text-red-800'}`}>
          {done === 'approved' ? <CheckIcon className="w-4 h-4" /> : <XIcon className="w-4 h-4" />}
          Application {done === 'approved' ? 'approved successfully. The institution account is now Active.' : 'rejected successfully.'}
        </div>
      )}

      <div className="bg-white rounded-xl border border-[#E8EDF5] p-6">
        <div className="flex items-start justify-between mb-5 pb-5 border-b border-gray-100">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">Institution Application</div>
              <InstitutionTypePill type={institution.institutionType} />
            </div>
            <h2 className="text-xl font-bold text-[#1B2A4A]">{institution.name}</h2>
            <p className="text-sm text-gray-400 mt-0.5">{institution.id} · {institution.regNumber} · Submitted {institution.since}</p>
          </div>
          <StatusBadge label={institution.regStatus} style={regStatusStyle[institution.regStatus]} />
        </div>

        <div className="grid grid-cols-2 gap-6">
          {([
            ['Institution ID',    institution.id],
            ['Registration No.',  institution.regNumber],
            ['Institution Type',  institution.institutionType],
            [institution.institutionType === 'University' ? 'Vice Chancellor / President' : 'Principal', institution.principal],
            ['City',              institution.city],
            ['Sub-Type',          institution.subType],
            ['Phone',             institution.phone],
            ['Email',             institution.email],
            ['Students / Enrolled', institution.students.toLocaleString()],
          ] as [string,string][]).map(([k, v]) => (
            <div key={k}>
              <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1">{k}</div>
              <div className="text-sm text-gray-800">{v}</div>
            </div>
          ))}
        </div>

        <div className="mt-6 pt-5 border-t border-gray-100">
          <h4 className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-3">Required Documents</h4>
          <div className="grid grid-cols-3 gap-3">
            {['Commercial Registry','Tax Card','Educational License','Bank Account Details',
              institution.institutionType === 'University' ? 'Ministry of Higher Ed. Approval' : 'Principal NID',
              'MENA Certification'].map(doc => (
              <div key={doc} className="flex items-center gap-2 text-xs text-gray-600">
                <CheckIcon className="w-3.5 h-3.5 text-green-500 shrink-0" />{doc}
              </div>
            ))}
          </div>
        </div>

        {!done && (
          <div className="mt-6 pt-5 border-t border-gray-100 flex items-center justify-between">
            <div>
              <div className="text-xs text-gray-400">Reviewed by: <strong className="text-gray-600">Operations Team</strong></div>
              <div className="text-xs text-gray-400">Pending since: <strong className="text-gray-600">{institution.since}</strong></div>
            </div>
            <div className="flex gap-3">
              <button onClick={() => setRejectModal(true)} className="px-4 py-2 text-sm font-semibold text-red-600 border border-red-200 rounded-lg hover:bg-red-50 transition-colors">
                Reject Application
              </button>
              <button onClick={() => setApproveModal(true)} className="px-4 py-2 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors flex items-center gap-2">
                <CheckIcon className="w-4 h-4" /> Approve Application
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Approval confirmation (US-09) */}
      {approveModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl shadow-2xl p-6 w-full max-w-sm mx-4">
            <div className="w-12 h-12 rounded-full bg-green-50 flex items-center justify-center mx-auto mb-4">
              <CheckIcon className="w-6 h-6 text-green-600" />
            </div>
            <h3 className="text-base font-semibold text-[#1B2A4A] mb-1 text-center">Confirm Approval</h3>
            <p className="text-sm text-gray-500 text-center mb-1">You are about to approve <strong>{institution.name}</strong>.</p>
            <p className="text-xs text-gray-400 text-center mb-5">This activates the institution account and enables fee collection. The action is logged.</p>
            <div className="flex gap-3">
              <button onClick={() => setApproveModal(false)} className="flex-1 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">Cancel</button>
              <button onClick={confirmApprove} className="flex-1 py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors">Confirm Approval</button>
            </div>
          </div>
        </div>
      )}

      {/* Reject modal (US-10) */}
      {rejectModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl shadow-2xl p-6 w-full max-w-md mx-4">
            <h3 className="text-base font-semibold text-[#1B2A4A] mb-1">Reject Application</h3>
            <p className="text-sm text-gray-400 mb-4">Provide a reason for rejecting <strong>{institution.name}</strong>.</p>
            <select value={rejectReason} onChange={e => setRejectReason(e.target.value)}
              className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] mb-3">
              <option value="">Select reason…</option>
              <option>Incomplete documentation</option>
              <option>Invalid commercial registry</option>
              <option>License not recognized</option>
              <option>Duplicate registration</option>
              <option>Compliance issue</option>
              <option>Other</option>
            </select>
            <textarea placeholder="Additional notes (optional)…"
              className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] h-24 resize-none mb-4" />
            <div className="flex gap-3">
              <button onClick={() => setRejectModal(false)} className="flex-1 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">Cancel</button>
              <button onClick={confirmReject} disabled={!rejectReason}
                className="flex-1 py-2.5 text-sm font-semibold text-white bg-red-600 rounded-lg hover:bg-red-700 transition-colors disabled:opacity-40">
                Confirm Rejection
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

/* ── Integration Status tab content (US-13) ── */
function IntegrationTab({ inst }: { inst: Institution }) {
  const cfg = {
    Integrated: {
      dot: 'bg-green-500', bg: 'bg-green-50', border: 'border-green-200',
      title: 'Integration Active', titleColor: 'text-green-800',
      desc: 'This institution is fully integrated with the CIB Fee Collection platform. Data synchronises automatically every 6 hours.',
    },
    Pending: {
      dot: 'bg-amber-400', bg: 'bg-amber-50', border: 'border-amber-200',
      title: 'Setup In Progress', titleColor: 'text-amber-800',
      desc: 'Integration setup has been initiated. The institution is awaiting API configuration and end-to-end testing.',
    },
    Failed: {
      dot: 'bg-red-500', bg: 'bg-red-50', border: 'border-red-200',
      title: 'Integration Failed', titleColor: 'text-red-800',
      desc: 'The connection to this institution has failed. Immediate action is required to restore service.',
    },
    'Not Integrated': {
      dot: 'bg-gray-400', bg: 'bg-gray-50', border: 'border-gray-200',
      title: 'Not Connected', titleColor: 'text-gray-700',
      desc: 'This institution has not yet been enrolled in the CIB integration program.',
    },
  }[inst.integStatus]

  return (
    <div className="space-y-5">
      <div className={`rounded-xl border ${cfg.bg} ${cfg.border} p-5`}>
        <div className="flex items-center gap-3 mb-2">
          <span className={`w-3 h-3 rounded-full shrink-0 ${cfg.dot}`} />
          <h3 className={`text-base font-bold ${cfg.titleColor}`}>{cfg.title}</h3>
          <StatusBadge label={inst.integStatus} style={integStyle[inst.integStatus]} />
        </div>
        <p className="text-sm text-gray-600 ml-6">{cfg.desc}</p>
      </div>

      {inst.integStatus === 'Integrated' && (
        <>
          <div className="grid grid-cols-2 gap-x-10 gap-y-4">
            {([
              ['API Endpoint',         `api.cib-collect.eg/v2/inst/${inst.id.toLowerCase()}`],
              ['Protocol',             'REST / HTTPS · TLS 1.3'],
              ['Last Successful Sync', '1 Sep 2026, 00:05 EET'],
              ['Sync Frequency',       'Every 6 hours'],
              ['Integration Date',     inst.since],
              ['Connection Key',       `CIB-${inst.id}-API-••••`],
            ] as [string,string][]).map(([k, v]) => (
              <div key={k}>
                <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-0.5">{k}</div>
                <div className="text-sm text-gray-800 font-mono">{v}</div>
              </div>
            ))}
          </div>
          <div>
            <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-2">Recent Sync Events</div>
            <div className="rounded-lg border border-gray-100 overflow-hidden">
              {[
                { time: '1 Sep 2026, 00:05',  event: 'Scheduled sync completed', detail: '4 new submissions processed' },
                { time: '31 Aug 2026, 18:05', event: 'Scheduled sync completed', detail: '0 new submissions' },
                { time: '31 Aug 2026, 12:05', event: 'Scheduled sync completed', detail: '1 new submission processed' },
              ].map((row, i) => (
                <div key={i} className={`flex items-center gap-4 px-4 py-2.5 text-xs ${i > 0 ? 'border-t border-gray-50' : ''}`}>
                  <CheckIcon className="w-3.5 h-3.5 text-green-500 shrink-0" />
                  <span className="font-mono text-gray-400 shrink-0">{row.time}</span>
                  <span className="text-gray-700 font-medium">{row.event}</span>
                  <span className="text-gray-400 ml-auto">{row.detail}</span>
                </div>
              ))}
            </div>
          </div>
        </>
      )}

      {inst.integStatus === 'Pending' && (
        <div>
          <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-3">Setup Checklist</div>
          <div className="space-y-2.5">
            {[
              { label: 'Institution account created',  done: true  },
              { label: 'Documents verified',           done: true  },
              { label: 'API credentials generated',   done: false },
              { label: 'Integration testing',         done: false },
              { label: 'Go-live sign-off',             done: false },
            ].map((step, i) => (
              <div key={i} className="flex items-center gap-3 text-sm">
                <span className={`w-5 h-5 rounded-full flex items-center justify-center shrink-0 text-[10px] font-bold ${step.done ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-400'}`}>
                  {step.done ? <CheckIcon className="w-3 h-3" /> : i + 1}
                </span>
                <span className={step.done ? 'text-gray-700' : 'text-gray-400'}>{step.label}</span>
              </div>
            ))}
          </div>
          <p className="mt-4 text-xs text-gray-400">Estimated go-live: <strong className="text-amber-700">7–14 business days from application date</strong></p>
        </div>
      )}

      {inst.integStatus === 'Failed' && (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-x-10 gap-y-4">
            {([
              ['Last Attempt',               '20 Jul 2026, 11:30'],
              ['Error Code',                 'ERR-502 · TLS certificate validation failed'],
              ['Last Successful Connection', 'Never connected'],
              ['Consecutive Failures',       '3'],
            ] as [string,string][]).map(([k, v]) => (
              <div key={k}>
                <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-0.5">{k}</div>
                <div className="text-sm text-red-700 font-mono">{v}</div>
              </div>
            ))}
          </div>
          <div className="bg-red-50 border border-red-200 rounded-lg px-4 py-3 flex items-start gap-2">
            <AlertIcon className="w-4 h-4 text-red-500 shrink-0 mt-0.5" />
            <p className="text-xs text-red-700">
              <strong>Action required:</strong> Contact CIB IT Support to resolve this integration failure before the institution can go live.
              Email: <span className="font-mono">tech-support@cib.eg</span> · Ref: <span className="font-mono">{inst.id}-INT-FAIL</span>
            </p>
          </div>
        </div>
      )}

      {inst.integStatus === 'Not Integrated' && (
        <div className="space-y-3">
          <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">Steps to Initiate Integration</div>
          {[
            'Ensure institution registration is fully approved',
            'Submit an integration request to CIB Operations',
            'Institution provides API / technical contact details',
            'CIB configures API credentials and endpoints',
            'End-to-end testing and go-live sign-off',
          ].map((s, i) => (
            <div key={i} className="flex items-start gap-3 text-sm text-gray-500">
              <span className="w-5 h-5 rounded-full bg-gray-100 text-gray-400 text-[10px] font-bold flex items-center justify-center shrink-0 mt-0.5">{i + 1}</span>
              {s}
            </div>
          ))}
          <p className="text-xs text-gray-400 pt-1">Estimated setup time: <strong className="text-gray-600">5–10 business days</strong></p>
        </div>
      )}
    </div>
  )
}

/* ── Institution detail (US-12, US-13, US-14, US-15) ── */
function InstitutionDetail({ institution, onBack, onActivate, onDeactivate }: {
  institution: Institution
  onBack: () => void
  onActivate?: (id: string) => void
  onDeactivate?: (id: string) => void
}) {
  const [tab, setTab] = useState(0)
  const [activateModal,   setActivateModal]   = useState(false)
  const [deactivateModal, setDeactivateModal] = useState(false)

  const students = institution.institutionType === 'University'
    ? [
        { id:'ST-001', name:'Karim Nour',    grade:'Faculty of Engineering · Year 3', guardian:'—', balance:12500, status:'Paid'    },
        { id:'ST-002', name:'Nadia Saleh',   grade:'Faculty of Medicine · Year 2',    guardian:'—', balance:8000,  status:'Partial' },
        { id:'ST-003', name:'Hassan Fouad',  grade:'Faculty of Commerce · Year 4',    guardian:'—', balance:6500,  status:'Unpaid'  },
        { id:'ST-004', name:'Rania Mostafa', grade:'Faculty of Law · Year 1',         guardian:'—', balance:0,     status:'Paid'    },
      ]
    : [
        { id:'ST-001', name:'Ahmed Hassan', grade:'Grade 10', guardian:'Hassan Ahmed',    balance:18000, status:'Paid'    },
        { id:'ST-002', name:'Sara Mohamed', grade:'Grade 7',  guardian:'Mohamed Ibrahim', balance:2500,  status:'Partial' },
        { id:'ST-003', name:'Omar Ali',     grade:'Grade 11', guardian:'Ali Kamal',       balance:22000, status:'Unpaid'  },
        { id:'ST-004', name:'Nour Sherif',  grade:'Grade 5',  guardian:'Sherif Nabil',    balance:0,     status:'Paid'    },
      ]

  const infoFields: [string,string][] = institution.institutionType === 'University'
    ? [
        ['Institution ID',              institution.id],
        ['Registration Number',         institution.regNumber],
        ['Institution Name',            institution.name],
        ['Institution Type',            institution.institutionType],
        ['University Type',             institution.subType],
        ['City',                        institution.city],
        ['Vice Chancellor / President', institution.principal],
        ['Phone',                       institution.phone],
        ['Email',                       institution.email],
        ['Account Status',              institution.accountStatus],
        ['Registration Date',           institution.since],
        ['Enrolled Students',           institution.students.toLocaleString()],
      ]
    : [
        ['Institution ID',      institution.id],
        ['Registration Number', institution.regNumber],
        ['Institution Name',    institution.name],
        ['Institution Type',    institution.institutionType],
        ['School Type',         institution.subType],
        ['City',                institution.city],
        ['Principal',           institution.principal],
        ['Phone',               institution.phone],
        ['Email',               institution.email],
        ['Account Status',      institution.accountStatus],
        ['Registration Date',   institution.since],
        ['Total Students',      institution.students.toLocaleString()],
      ]

  const feeSubmissions  = getFeeSubmissions(institution)
  const settlementRecs  = getSettlements(institution)

  return (
    <div className="space-y-4">
      <button onClick={onBack} className="flex items-center gap-1 text-sm text-gray-400 hover:text-gray-700 transition-colors">
        <ChevronLeftIcon className="w-4 h-4" /> Back to Institutions
      </button>

      {/* Header card */}
      <div className="bg-white rounded-xl border border-[#E8EDF5] p-6">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-4">
            <div className="w-14 h-14 bg-[#003087]/10 rounded-xl flex items-center justify-center">
              <SchoolIcon className="w-7 h-7 text-[#003087]" />
            </div>
            <div>
              <div className="flex items-center gap-2 mb-0.5">
                <h2 className="text-xl font-bold text-[#1B2A4A]">{institution.name}</h2>
                <InstitutionTypePill type={institution.institutionType} />
              </div>
              <p className="text-sm text-gray-400">{institution.id} · {institution.city} · {institution.subType}</p>
            </div>
          </div>
          <div className="flex items-center gap-2 flex-wrap justify-end">
            <StatusBadge label={institution.regStatus}    style={regStatusStyle[institution.regStatus]} />
            <StatusBadge label={institution.accountStatus} style={accountStyle[institution.accountStatus]} />
            {/* US-11: status actions in detail */}
            {institution.accountStatus === 'Active' && onDeactivate && (
              <button onClick={() => setDeactivateModal(true)} className="px-2.5 py-1 text-[11px] font-semibold text-red-600 border border-red-200 rounded hover:bg-red-50 transition-colors">
                Deactivate
              </button>
            )}
            {institution.accountStatus !== 'Active' && institution.regStatus === 'Approved' && onActivate && (
              <button onClick={() => setActivateModal(true)} className="px-2.5 py-1 text-[11px] font-semibold text-green-700 border border-green-200 rounded hover:bg-green-50 transition-colors">
                Activate
              </button>
            )}
          </div>
        </div>

        <div className="grid grid-cols-4 gap-4 mt-5 pt-5 border-t border-gray-100">
          {[
            { label:'Students',       value: institution.students.toLocaleString() },
            { label:'Fees Submitted', value: institution.feesSubmitted },
            { label:'Collections',    value: institution.collections },
            { label:'Integration',    value: institution.integStatus },
          ].map(({ label, value }) => (
            <div key={label}>
              <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1">{label}</div>
              <div className="text-sm font-semibold text-gray-800">{value}</div>
            </div>
          ))}
        </div>
      </div>

      {/* Tabs */}
      <div className="bg-white rounded-xl border border-[#E8EDF5] overflow-hidden">
        <div className="flex border-b border-gray-100 overflow-x-auto">
          {DETAIL_TABS.map((t, i) => (
            <button key={t} onClick={() => setTab(i)}
              className={`px-5 py-3.5 text-xs font-semibold whitespace-nowrap transition-colors border-b-2 ${
                tab === i ? 'text-[#003087] border-[#003087] bg-[#EBF1FB]' : 'text-gray-400 border-transparent hover:text-gray-700'
              }`}>{t}</button>
          ))}
        </div>

        <div className="p-5">
          {/* 0: Institution Information */}
          {tab === 0 && (
            <div className="grid grid-cols-2 gap-x-10 gap-y-4">
              {infoFields.map(([k, v]) => (
                <div key={k}>
                  <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-0.5">{k}</div>
                  <div className="text-sm text-gray-800">{v}</div>
                </div>
              ))}
            </div>
          )}

          {/* 1: Students */}
          {tab === 1 && (
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[#F8FAFD]">
                  {['Student ID','Name', institution.institutionType === 'University' ? 'Faculty / Year' : 'Grade','Guardian','Balance (EGP)','Status'].map(h => (
                    <th key={h} className="text-left px-4 py-2.5 text-[11px] font-semibold text-gray-400 uppercase tracking-wider">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {students.map(s => (
                  <tr key={s.id} className="hover:bg-[#F8FAFD]">
                    <td className="px-4 py-3 font-mono text-xs text-[#003087]">{s.id}</td>
                    <td className="px-4 py-3 text-xs text-gray-800 font-medium">{s.name}</td>
                    <td className="px-4 py-3 text-xs text-gray-500">{s.grade}</td>
                    <td className="px-4 py-3 text-xs text-gray-500">{s.guardian}</td>
                    <td className="px-4 py-3 text-xs font-mono font-semibold text-gray-700">{s.balance.toLocaleString()}</td>
                    <td className="px-4 py-3">
                      <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${
                        s.status === 'Paid'    ? 'bg-green-50 text-green-700 border-green-200' :
                        s.status === 'Partial' ? 'bg-amber-50 text-amber-700 border-amber-200' :
                                                  'bg-red-50   text-red-700   border-red-200'
                      }`}>{s.status}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {/* 2: Integration Status (US-13) */}
          {tab === 2 && <IntegrationTab inst={institution} />}

          {/* 3: Fee Submissions (US-14) */}
          {tab === 3 && (
            feeSubmissions.length === 0 ? (
              <div className="text-center py-10 text-gray-400">
                <ReportIcon className="w-8 h-8 mx-auto mb-3 opacity-40" />
                <p className="text-sm font-medium mb-1">No fee submissions yet</p>
                <p className="text-xs">Fee submissions will appear here once the institution is integrated and active.</p>
              </div>
            ) : (
              <div className="space-y-4">
                <div className="grid grid-cols-3 gap-4">
                  {[
                    { label:'Total Submissions', value: feeSubmissions.length.toString() },
                    { label:'Total Amount',       value: institution.feesSubmitted },
                    { label:'Last Submission',    value: feeSubmissions[0].date.split(',')[0] },
                  ].map(({ label, value }) => (
                    <div key={label} className="bg-[#F8FAFD] rounded-lg px-4 py-3">
                      <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-0.5">{label}</div>
                      <div className="text-sm font-bold text-gray-800">{value}</div>
                    </div>
                  ))}
                </div>
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-[#F8FAFD]">
                      {['Submission ID','Term / Period','Date','Students','Amount','Status'].map(h => (
                        <th key={h} className="text-left px-4 py-2.5 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-50">
                    {feeSubmissions.map(fs => (
                      <tr key={fs.id} className="hover:bg-[#F8FAFD]">
                        <td className="px-4 py-3 font-mono text-xs text-[#003087]">{fs.id}</td>
                        <td className="px-4 py-3 text-xs text-gray-700 font-medium">{fs.term}</td>
                        <td className="px-4 py-3 text-xs text-gray-500 font-mono whitespace-nowrap">{fs.date}</td>
                        <td className="px-4 py-3 text-xs font-mono text-gray-600">{fs.students.toLocaleString()}</td>
                        <td className="px-4 py-3 text-xs font-mono font-semibold text-gray-800">{fs.amount}</td>
                        <td className="px-4 py-3">
                          <span className="text-[11px] font-semibold px-2 py-0.5 rounded border bg-green-50 text-green-700 border-green-200">{fs.status}</span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
          )}

          {/* 4: Settlement (US-15) */}
          {tab === 4 && (
            settlementRecs.length === 0 ? (
              <div className="text-center py-10 text-gray-400">
                <ReconcileIcon className="w-8 h-8 mx-auto mb-3 opacity-40" />
                <p className="text-sm font-medium mb-1">No settlement records</p>
                <p className="text-xs">Settlement records appear here once fee submissions have been processed and settled.</p>
              </div>
            ) : (
              <div className="space-y-4">
                <div className="grid grid-cols-3 gap-4">
                  {[
                    { label:'Total Settled',      value: institution.collections },
                    { label:'Settlement Records', value: settlementRecs.length.toString() },
                    { label:'Last Settlement',    value: settlementRecs[0].date },
                  ].map(({ label, value }) => (
                    <div key={label} className="bg-[#F8FAFD] rounded-lg px-4 py-3">
                      <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-0.5">{label}</div>
                      <div className="text-sm font-bold text-gray-800">{value}</div>
                    </div>
                  ))}
                </div>
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-[#F8FAFD]">
                      {['Settlement ID','Date','Gross Amount','CIB Fee (2%)','Net Amount','Status','References'].map(h => (
                        <th key={h} className="text-left px-4 py-2.5 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-50">
                    {settlementRecs.map(rec => (
                      <tr key={rec.id} className="hover:bg-[#F8FAFD]">
                        <td className="px-4 py-3 font-mono text-xs text-[#003087]">{rec.id}</td>
                        <td className="px-4 py-3 text-xs text-gray-500 font-mono whitespace-nowrap">{rec.date}</td>
                        <td className="px-4 py-3 text-xs font-mono text-gray-700">{rec.gross}</td>
                        <td className="px-4 py-3 text-xs font-mono text-red-600">{rec.fee}</td>
                        <td className="px-4 py-3 text-xs font-mono font-semibold text-gray-800">{rec.net}</td>
                        <td className="px-4 py-3"><span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${settlementStyle[rec.status]}`}>{rec.status}</span></td>
                        <td className="px-4 py-3">
                          <div className="flex flex-col gap-0.5">
                            <span className="flex items-center gap-1 text-[10px] text-gray-400">
                              <TransactionIcon className="w-2.5 h-2.5 shrink-0" />
                              <span className="font-mono text-[#003087]">{rec.txRef}</span>
                            </span>
                            <span className="flex items-center gap-1 text-[10px] text-gray-400">
                              <ReconcileIcon className="w-2.5 h-2.5 shrink-0" />
                              <span className="font-mono text-[#003087]">{rec.reconRef}</span>
                            </span>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
          )}

          {/* 5-7: Transactions, Reconciliation, Reports — placeholder */}
          {[5, 6, 7].includes(tab) && (
            <div className="text-center py-10 text-gray-400">
              <ReportIcon className="w-8 h-8 mx-auto mb-3 opacity-40" />
              <p className="text-sm">Data for <strong>{DETAIL_TABS[tab]}</strong> is loaded here.</p>
            </div>
          )}
        </div>
      </div>

      {/* Activate / Deactivate confirmation modals (US-11) */}
      {activateModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl shadow-2xl p-6 w-full max-w-sm mx-4">
            <h3 className="text-base font-semibold text-[#1B2A4A] mb-2">Confirm Activation</h3>
            <p className="text-sm text-gray-500 mb-5">Activate <strong>{institution.name}</strong>? This enables fee collection for this institution.</p>
            <div className="flex gap-3">
              <button onClick={() => setActivateModal(false)} className="flex-1 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">Cancel</button>
              <button onClick={() => { setActivateModal(false); onActivate?.(institution.id) }} className="flex-1 py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors">Confirm</button>
            </div>
          </div>
        </div>
      )}
      {deactivateModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl shadow-2xl p-6 w-full max-w-sm mx-4">
            <h3 className="text-base font-semibold text-[#1B2A4A] mb-2">Confirm Deactivation</h3>
            <p className="text-sm text-gray-500 mb-5">Deactivate <strong>{institution.name}</strong>? Fee collection will be suspended until the account is reactivated.</p>
            <div className="flex gap-3">
              <button onClick={() => setDeactivateModal(false)} className="flex-1 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">Cancel</button>
              <button onClick={() => { setDeactivateModal(false); onDeactivate?.(institution.id) }} className="flex-1 py-2.5 text-sm font-semibold text-white bg-red-600 rounded-lg hover:bg-red-700 transition-colors">Confirm</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

/* ── Schools main screen ── */
export default function Schools({ userRole: _userRole }: { userRole?: string }) {
  const [institutionList,      setInstitutionList]      = useState<Institution[]>(BASE_INSTITUTIONS)
  const [regStatusOverrides,   setRegStatusOverrides]   = useState<Record<string, RegStatus>>({})
  const [acctStatusOverrides,  setAcctStatusOverrides]  = useState<Record<string, AccountStatus>>({})
  const [view,         setView]         = useState<View>('list')
  const [selectedId,   setSelectedId]   = useState<string | null>(null)
  const [search,       setSearch]       = useState('')
  const [filterStatus, setFilterStatus] = useState('All')
  const [filterType,   setFilterType]   = useState('All')
  const [actionModal,  setActionModal]  = useState<{ institution: Institution; action: string } | null>(null)
  const [showRegModal, setShowRegModal] = useState(false)
  const [toast,        setToast]        = useState<string | null>(null)

  const showToast = (msg: string) => { setToast(msg); setTimeout(() => setToast(null), 4500) }

  /* Merge overrides into the list */
  const effectiveList = institutionList.map(inst => ({
    ...inst,
    regStatus:     (regStatusOverrides[inst.id]  as RegStatus)     ?? inst.regStatus,
    accountStatus: (acctStatusOverrides[inst.id] as AccountStatus) ?? inst.accountStatus,
  }))

  const selected = selectedId ? effectiveList.find(i => i.id === selectedId) ?? null : null

  const filtered = effectiveList.filter(s => {
    const q = search.toLowerCase()
    const matchSearch  = s.name.toLowerCase().includes(q) || s.id.toLowerCase().includes(q) || s.city.toLowerCase().includes(q)
    const matchStatus  = filterStatus === 'All' || s.regStatus === filterStatus || s.accountStatus === filterStatus
    const matchType    = filterType   === 'All' || s.institutionType === filterType
    return matchSearch && matchStatus && matchType
  })

  const handleApproved   = (id: string) => { setRegStatusOverrides(p => ({ ...p, [id]:'Approved' })); setAcctStatusOverrides(p => ({ ...p, [id]:'Active' })) }
  const handleRejected   = (id: string) => { setRegStatusOverrides(p => ({ ...p, [id]:'Rejected' })) }
  const handleActivate   = (id: string) => { setAcctStatusOverrides(p => ({ ...p, [id]:'Active' }));   showToast('Account activated successfully.') }
  const handleDeactivate = (id: string) => { setAcctStatusOverrides(p => ({ ...p, [id]:'Inactive' })); showToast('Account deactivated.') }
  const handleRegister   = (inst: Institution) => setInstitutionList(p => [...p, inst])

  const nextSchool = institutionList.filter(i => i.institutionType === 'School').length     + 1
  const nextUni    = institutionList.filter(i => i.institutionType === 'University').length + 1

  /* Sub-views */
  if (view === 'detail' && selected) return (
    <>
      <InstitutionDetail institution={selected} onBack={() => setView('list')} onActivate={handleActivate} onDeactivate={handleDeactivate} />
      {toast && (
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-50 bg-[#003087] text-white px-5 py-3 rounded-xl shadow-lg text-sm font-semibold flex items-center gap-2 whitespace-nowrap">
          <CheckIcon className="w-4 h-4" /> {toast}
        </div>
      )}
    </>
  )
  if (view === 'approval' && selected) return (
    <ApprovalView institution={selected} onBack={() => setView('list')} onApproved={handleApproved} onRejected={handleRejected} />
  )

  return (
    <div className="space-y-4">
      {/* Header row */}
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <p className="text-sm text-gray-400">
          {filtered.length} institution{filtered.length !== 1 ? 's' : ''} found
          {filterType !== 'All' && <span className="ml-1 text-[#003087] font-medium">· {filterType}s only</span>}
        </p>
        <div className="flex items-center gap-3 flex-wrap">
          <div className="relative">
            <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-300" />
            <input type="text" placeholder="Search institutions…" value={search} onChange={e => setSearch(e.target.value)}
              className="pl-8 pr-4 py-2 border border-[#DDE3EF] rounded-lg text-sm placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] w-52 bg-white" />
          </div>
          <select value={filterType} onChange={e => setFilterType(e.target.value)}
            className="border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-200 bg-white">
            <option value="All">All Types</option>
            <option value="School">School</option>
            <option value="University">University</option>
          </select>
          <select value={filterStatus} onChange={e => setFilterStatus(e.target.value)}
            className="border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-200 bg-white">
            <option value="All">All Statuses</option>
            <option>Approved</option><option>Pending</option><option>Under Review</option>
            <option>Rejected</option><option>Active</option><option>Inactive</option>
          </select>
          {/* US-06: Register new institution */}
          <button onClick={() => setShowRegModal(true)}
            className="flex items-center gap-1.5 px-3.5 py-2 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors">
            <PlusIcon className="w-3.5 h-3.5" /> Register Institution
          </button>
        </div>
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border border-[#E8EDF5] overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-[#F8FAFD] border-b border-[#E8EDF5]">
                {['Institution','Type','Reg Status','Students','Fees Submitted','Collections','Integration','Account','Actions'].map(h => (
                  <th key={h} className="text-left px-5 py-3.5 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {filtered.map(inst => (
                <tr key={inst.id} className="hover:bg-[#F8FAFD] transition-colors">
                  <td className="px-5 py-4">
                    <div className="font-medium text-gray-800 text-sm">{inst.name}</div>
                    <div className="text-xs text-gray-400 mt-0.5">{inst.id} · {inst.city}</div>
                  </td>
                  <td className="px-5 py-4"><InstitutionTypePill type={inst.institutionType} /></td>
                  <td className="px-5 py-4"><StatusBadge label={inst.regStatus}     style={regStatusStyle[inst.regStatus]} /></td>
                  <td className="px-5 py-4 font-mono text-sm font-semibold text-gray-700">{inst.students.toLocaleString()}</td>
                  <td className="px-5 py-4 text-xs text-gray-600 font-mono">{inst.feesSubmitted}</td>
                  <td className="px-5 py-4 text-xs text-gray-600 font-mono">{inst.collections}</td>
                  <td className="px-5 py-4"><StatusBadge label={inst.integStatus}   style={integStyle[inst.integStatus]} /></td>
                  <td className="px-5 py-4"><StatusBadge label={inst.accountStatus} style={accountStyle[inst.accountStatus]} /></td>
                  <td className="px-5 py-4">
                    <div className="flex items-center gap-1">
                      <button onClick={() => { setSelectedId(inst.id); setView('detail') }} title="View"
                        className="p-1.5 rounded hover:bg-[#003087]/10 text-[#003087] transition-colors">
                        <EyeIcon className="w-3.5 h-3.5" />
                      </button>
                      {(inst.regStatus === 'Pending' || inst.regStatus === 'Under Review') && (
                        <button onClick={() => { setSelectedId(inst.id); setView('approval') }} title="Review"
                          className="px-2 py-1 text-[10px] font-semibold text-white bg-[#003087] rounded hover:bg-[#002060] transition-colors">
                          Review
                        </button>
                      )}
                      {inst.accountStatus === 'Active' ? (
                        <button onClick={() => setActionModal({ institution: inst, action: 'Deactivate' })} title="Deactivate"
                          className="px-2 py-1 text-[10px] font-semibold text-red-600 border border-red-200 rounded hover:bg-red-50 transition-colors">
                          Deactivate
                        </button>
                      ) : inst.regStatus === 'Approved' ? (
                        <button onClick={() => setActionModal({ institution: inst, action: 'Activate' })} title="Activate"
                          className="px-2 py-1 text-[10px] font-semibold text-green-600 border border-green-200 rounded hover:bg-green-50 transition-colors">
                          Activate
                        </button>
                      ) : null}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        <div className="flex items-center justify-between px-5 py-3.5 border-t border-gray-100">
          <span className="text-xs text-gray-400">Showing 1–{filtered.length} of {effectiveList.length} institutions</span>
          <div className="flex items-center gap-1">
            <button className="p-1.5 rounded border border-gray-200 hover:bg-gray-50 text-gray-400 transition-colors"><ChevronLeftIcon className="w-3.5 h-3.5" /></button>
            <button className="w-7 h-7 rounded bg-[#003087] text-white text-xs font-semibold">1</button>
            <button className="p-1.5 rounded border border-gray-200 hover:bg-gray-50 text-gray-400 transition-colors"><ChevronRightIcon className="w-3.5 h-3.5" /></button>
          </div>
        </div>
      </div>

      {/* List-level activate / deactivate confirmation (US-11) */}
      {actionModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl shadow-2xl p-6 w-full max-w-sm mx-4">
            <h3 className="text-base font-semibold text-[#1B2A4A] mb-2">Confirm {actionModal.action}</h3>
            <p className="text-sm text-gray-500 mb-5">
              Are you sure you want to <strong>{actionModal.action.toLowerCase()}</strong> <strong>{actionModal.institution.name}</strong>?
              {actionModal.action === 'Deactivate' && ' This suspends fee collection until the account is reactivated.'}
            </p>
            <div className="flex gap-3">
              <button onClick={() => setActionModal(null)} className="flex-1 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">Cancel</button>
              <button
                onClick={() => {
                  if (actionModal.action === 'Activate') handleActivate(actionModal.institution.id)
                  else handleDeactivate(actionModal.institution.id)
                  setActionModal(null)
                }}
                className={`flex-1 py-2.5 text-sm font-semibold text-white rounded-lg transition-colors ${actionModal.action === 'Deactivate' ? 'bg-red-600 hover:bg-red-700' : 'bg-[#003087] hover:bg-[#002060]'}`}>
                Confirm
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Register Institution modal (US-06) */}
      {showRegModal && (
        <RegisterModal onClose={() => setShowRegModal(false)} onRegister={handleRegister} nextSchool={nextSchool} nextUni={nextUni} />
      )}

      {/* Toast (US-11 success feedback) */}
      {toast && (
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-50 bg-[#003087] text-white px-5 py-3 rounded-xl shadow-lg text-sm font-semibold flex items-center gap-2 whitespace-nowrap">
          <CheckIcon className="w-4 h-4" /> {toast}
        </div>
      )}
    </div>
  )
}
