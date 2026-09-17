import { useState } from 'react'
import { PlusIcon, EditIcon, ShieldIcon, SearchIcon, CheckIcon, XIcon, CheckCircleIcon, AlertIcon } from '../components/Icons'
import type { UserRole as PortalRole } from '../Portal'

type BankRole = 'Bank Admin' | 'Operations' | 'Finance' | 'Reconciliation'
type UserStatus = 'Active' | 'Inactive'

interface BankUser {
  id: string
  name: string
  username: string
  email: string
  role: BankRole
  status: UserStatus
  lastLogin: string
  department: string
  createdAt: string
}

const INITIAL_USERS: BankUser[] = [
  { id: 'USR-001', name: 'Mohamed Ali',    username: 'ADMIN_MALI',    email: 'm.ali@cibeg.com',      role: 'Bank Admin',  status: 'Active',   lastLogin: '31 Aug 2026 14:00', department: 'IT & Operations', createdAt: '1 Jan 2024'  },
  { id: 'USR-002', name: 'Heba Mostafa',   username: 'OPS_HEBA',      email: 'h.mostafa@cibeg.com',  role: 'Operations',  status: 'Active',   lastLogin: '31 Aug 2026 09:15', department: 'Operations',     createdAt: '15 Jan 2024' },
  { id: 'USR-003', name: 'Sherif Nabil',   username: 'OPS_SHERIF',    email: 's.nabil@cibeg.com',    role: 'Operations',  status: 'Active',   lastLogin: '31 Aug 2026 11:00', department: 'Operations',     createdAt: '15 Jan 2024' },
  { id: 'USR-004', name: 'Amira Farouk',   username: 'FIN_AMIRA',     email: 'a.farouk@cibeg.com',   role: 'Finance',     status: 'Active',   lastLogin: '31 Aug 2026 13:30', department: 'Finance',        createdAt: '1 Jun 2024'  },
  { id: 'USR-005', name: 'Tarek Ibrahim',  username: 'FIN_TAREK',     email: 't.ibrahim@cibeg.com',  role: 'Finance',     status: 'Active',   lastLogin: '30 Aug 2026 17:45', department: 'Finance',        createdAt: '1 Jun 2024'  },
  { id: 'USR-006', name: 'Dina Hassan',    username: 'OPS_DINA',      email: 'd.hassan@cibeg.com',   role: 'Operations',  status: 'Active',   lastLogin: '31 Aug 2026 08:30', department: 'Collections',    createdAt: '1 Mar 2025'  },
  { id: 'USR-007', name: 'Mostafa Gamal',  username: 'FIN_MOSTAFA',   email: 'm.gamal@cibeg.com',    role: 'Finance',        status: 'Active',   lastLogin: '31 Aug 2026 11:05', department: 'Compliance',     createdAt: '15 Aug 2025' },
  { id: 'USR-008', name: 'Rania Khaled',   username: 'OPS_RANIA',     email: 'r.khaled@cibeg.com',   role: 'Operations',     status: 'Inactive', lastLogin: '15 Jul 2026 14:20', department: 'Operations',     createdAt: '1 Feb 2024'  },
  { id: 'USR-009', name: 'Tarek Ibrahim',  username: 'RECON_TAREK',   email: 't.ibrahim@cibeg.com',  role: 'Reconciliation', status: 'Active',   lastLogin: '31 Aug 2026 13:30', department: 'Finance',        createdAt: '1 Jun 2024'  },
]

const rolePermissions: Record<BankRole, string[]> = {
  'Bank Admin':     ['Manage bank users', 'Register & manage schools', 'Approve / reject schools', 'View all transactions', 'Process payments', 'Create & manage EPP plans', 'Full reconciliation access', 'Generate all reports', 'View audit logs', 'System settings'],
  'Operations':     ['Register & manage schools', 'View all transactions', 'Process payments', 'Create & manage EPP plans', 'Reconciliation (view & assign)', 'View notifications'],
  'Finance':        ['View transactions', 'Manage EPP plans', 'Full reconciliation access', 'Generate financial reports', 'View notifications'],
  'Reconciliation': ['View reconciliation status', 'View exception queue', 'View exception details', 'Assign exceptions', 'Investigate exceptions', 'Resolve exceptions', 'View notifications'],
}

const roleColors: Record<BankRole, string> = {
  'Bank Admin':     'bg-[#003087]/10 text-[#003087] border-[#003087]/20',
  'Operations':     'bg-indigo-50 text-indigo-700 border-indigo-200',
  'Finance':        'bg-amber-50 text-amber-700 border-amber-200',
  'Reconciliation': 'bg-teal-50 text-teal-700 border-teal-200',
}

const statusStyle: Record<UserStatus, string> = {
  Active:   'bg-green-50 text-green-700 border-green-200',
  Inactive: 'bg-gray-100 text-gray-500 border-gray-200',
}

interface UserFormData {
  name: string
  email: string
  username: string
  role: BankRole
  department: string
}

const emptyForm: UserFormData = { name: '', email: '', username: '', role: 'Operations', department: '' }

const ALL_PERMISSIONS = [
  'Manage bank users', 'Register & manage schools', 'Approve / reject schools',
  'View all transactions', 'Process payments', 'Create & manage EPP plans',
  'Full reconciliation access', 'Reconciliation (view & assign)',
  'View reconciliation status', 'View exception queue', 'View exception details',
  'Assign exceptions', 'Investigate exceptions', 'Resolve exceptions',
  'Generate all reports', 'Generate financial reports',
  'View audit logs', 'System settings', 'View notifications',
]

export default function Users({ userRole }: { userRole?: PortalRole }) {
  const isAdmin = userRole === 'bank-admin'

  const [userList, setUserList]       = useState(INITIAL_USERS)
  const [search, setSearch]           = useState('')
  const [showAddForm, setShowAddForm] = useState(false)
  const [editUser, setEditUser]       = useState<BankUser | null>(null)
  const [form, setForm]               = useState<UserFormData>(emptyForm)
  const [formError, setFormError]     = useState('')
  const [selectedPermRole, setSelectedPermRole] = useState<BankRole>('Bank Admin')
  const [confirmUser, setConfirmUser] = useState<BankUser | null>(null)
  const [successMsg, setSuccessMsg]   = useState('')

  const filtered = userList.filter(u =>
    !search || u.name.toLowerCase().includes(search.toLowerCase()) ||
    u.username.toLowerCase().includes(search.toLowerCase()) ||
    u.email.toLowerCase().includes(search.toLowerCase()) ||
    u.role.toLowerCase().includes(search.toLowerCase())
  )

  const showSuccess = (msg: string) => {
    setSuccessMsg(msg)
    setTimeout(() => setSuccessMsg(''), 3000)
  }

  const validateForm = (isEdit = false): boolean => {
    if (!form.name.trim())  { setFormError('Full name is required.'); return false }
    if (!form.email.trim()) { setFormError('Email address is required.'); return false }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) { setFormError('Enter a valid email address.'); return false }
    const emailExists = userList.some(u => u.email.toLowerCase() === form.email.toLowerCase() && (!isEdit || u.id !== editUser?.id))
    if (emailExists) { setFormError('A user with this email already exists.'); return false }
    const usernameExists = form.username && userList.some(u => u.username.toLowerCase() === form.username.toLowerCase() && (!isEdit || u.id !== editUser?.id))
    if (usernameExists) { setFormError('This username is already taken.'); return false }
    setFormError('')
    return true
  }

  const handleAddUser = () => {
    if (!validateForm()) return
    const username = form.username.trim() || form.name.replace(/\s+/g, '_').toUpperCase().slice(0, 12)
    const newUser: BankUser = {
      id:         `USR-${String(userList.length + 1).padStart(3, '0')}`,
      name:       form.name.trim(),
      username,
      email:      form.email.trim(),
      role:       form.role,
      status:     'Active',
      lastLogin:  '—',
      department: form.department,
      createdAt:  '3 Sep 2026',
    }
    setUserList([...userList, newUser])
    setShowAddForm(false)
    setForm(emptyForm)
    showSuccess(`${newUser.name} has been added successfully.`)
  }

  const handleSaveEdit = () => {
    if (!editUser || !validateForm(true)) return
    setUserList(userList.map(u => u.id === editUser.id
      ? { ...u, name: form.name.trim(), email: form.email.trim(), username: form.username.trim() || u.username, role: form.role, department: form.department }
      : u
    ))
    setEditUser(null)
    setForm(emptyForm)
    showSuccess('User updated successfully.')
  }

  const handleToggleStatus = () => {
    if (!confirmUser) return
    const newStatus: UserStatus = confirmUser.status === 'Active' ? 'Inactive' : 'Active'
    setUserList(userList.map(u => u.id === confirmUser.id ? { ...u, status: newStatus } : u))
    showSuccess(`${confirmUser.name} has been ${newStatus === 'Active' ? 'reactivated' : 'deactivated'}.`)
    setConfirmUser(null)
  }

  const openEdit = (user: BankUser) => {
    setEditUser(user)
    setForm({ name: user.name, email: user.email, username: user.username, role: user.role, department: user.department })
    setFormError('')
    setShowAddForm(false)
  }

  const roleCounts = (['Bank Admin', 'Operations', 'Finance', 'Reconciliation'] as BankRole[]).map(r => ({
    role: r, count: userList.filter(u => u.role === r && u.status === 'Active').length,
  }))

  return (
    <div className="space-y-4">

      {/* Success toast */}
      {successMsg && (
        <div className="flex items-center gap-2 px-4 py-3 bg-green-50 border border-green-200 rounded-xl text-sm text-green-700 font-medium">
          <CheckIcon className="w-4 h-4 shrink-0" />{successMsg}
        </div>
      )}

      {/* Role summary */}
      <div className="grid grid-cols-4 gap-3">
        {roleCounts.map(({ role, count }) => (
          <div key={role} className={`bg-white rounded-xl border p-4 flex items-center justify-between border-[#E8EDF5]`}>
            <div>
              <span className={`text-[11px] font-bold px-2 py-0.5 rounded border inline-block mb-1.5 ${roleColors[role]}`}>{role}</span>
              <div className="text-2xl font-bold text-[#1B2A4A]">{count}</div>
              <div className="text-[11px] text-gray-400">active users</div>
            </div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-3 gap-4">
        {/* Users table */}
        <div className="col-span-2 space-y-3">
          <div className="flex items-center gap-3">
            <div className="relative flex-1">
              <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-300" />
              <input type="text" placeholder="Search users…" value={search} onChange={e => setSearch(e.target.value)}
                className="w-full pl-8 pr-4 py-2 border border-[#DDE3EF] rounded-lg text-sm placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] bg-white" />
            </div>
            {isAdmin && (
              <button onClick={() => { setShowAddForm(!showAddForm); setEditUser(null); setForm(emptyForm); setFormError('') }}
                className="flex items-center gap-2 px-4 py-2 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors">
                <PlusIcon className="w-4 h-4" /> Add User
              </button>
            )}
          </div>

          {/* Add user form */}
          {showAddForm && isAdmin && (
            <div className="bg-white rounded-xl border border-[#003087]/20 p-5">
              <h3 className="text-sm font-semibold text-[#1B2A4A] mb-4">Add New Bank Employee</h3>
              <UserForm form={form} setForm={setForm} error={formError} />
              <div className="flex gap-3 mt-4">
                <button onClick={() => { setShowAddForm(false); setForm(emptyForm); setFormError('') }}
                  className="px-4 py-2 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">Cancel</button>
                <button onClick={handleAddUser} disabled={!form.name || !form.email}
                  className="flex items-center gap-2 px-4 py-2 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors disabled:opacity-40">
                  <PlusIcon className="w-3.5 h-3.5" /> Add Employee
                </button>
              </div>
            </div>
          )}

          {/* Edit user form */}
          {editUser && isAdmin && (
            <div className="bg-white rounded-xl border border-[#F7941D]/30 p-5">
              <div className="flex items-center gap-2 mb-4">
                <EditIcon className="w-4 h-4 text-[#F7941D]" />
                <h3 className="text-sm font-semibold text-[#1B2A4A]">Edit User — {editUser.name}</h3>
                <span className="text-[11px] text-gray-400 font-mono">{editUser.id}</span>
              </div>
              <UserForm form={form} setForm={setForm} error={formError} />
              <div className="flex gap-3 mt-4">
                <button onClick={() => { setEditUser(null); setForm(emptyForm); setFormError('') }}
                  className="px-4 py-2 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">Cancel</button>
                <button onClick={handleSaveEdit}
                  className="flex items-center gap-2 px-4 py-2 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors">
                  <CheckCircleIcon className="w-3.5 h-3.5" /> Save Changes
                </button>
              </div>
            </div>
          )}

          <div className="bg-white rounded-xl border border-[#E8EDF5] overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-[#F8FAFD] border-b border-[#E8EDF5]">
                    {['User', 'Username', 'Role', 'Department', 'Last Login', 'Status', isAdmin ? 'Actions' : ''].filter(Boolean).map(h => (
                      <th key={h} className="text-left px-4 py-3.5 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {filtered.map(user => (
                    <tr key={user.id} className={`transition-colors ${user.status === 'Inactive' ? 'opacity-60' : 'hover:bg-[#F8FAFD]'}`}>
                      <td className="px-4 py-3.5">
                        <div className="flex items-center gap-2">
                          <div className={`w-7 h-7 rounded-full flex items-center justify-center font-bold text-[10px] shrink-0 ${user.status === 'Inactive' ? 'bg-gray-100 text-gray-400' : 'bg-[#003087]/10 text-[#003087]'}`}>
                            {user.name.split(' ').map(n => n[0]).join('').slice(0, 2)}
                          </div>
                          <div>
                            <div className="text-xs font-semibold text-gray-800">{user.name}</div>
                            <div className="text-[10px] text-gray-400">{user.email}</div>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-3.5 font-mono text-xs text-[#003087] font-medium">{user.username}</td>
                      <td className="px-4 py-3.5">
                        <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${roleColors[user.role]}`}>{user.role}</span>
                      </td>
                      <td className="px-4 py-3.5 text-xs text-gray-500">{user.department}</td>
                      <td className="px-4 py-3.5 text-[11px] text-gray-400 font-mono whitespace-nowrap">{user.lastLogin}</td>
                      <td className="px-4 py-3.5">
                        <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${statusStyle[user.status]}`}>{user.status}</span>
                      </td>
                      {isAdmin && (
                        <td className="px-4 py-3.5">
                          <div className="flex items-center gap-1">
                            <button onClick={() => openEdit(user)}
                              className="p-1.5 rounded hover:bg-[#003087]/10 text-[#003087] transition-colors" title="Edit user">
                              <EditIcon className="w-3.5 h-3.5" />
                            </button>
                            {user.status === 'Active' ? (
                              <button onClick={() => setConfirmUser(user)}
                                className="px-2 py-1 text-[10px] font-semibold text-red-600 border border-red-200 rounded hover:bg-red-50 transition-colors">
                                Deactivate
                              </button>
                            ) : (
                              <button onClick={() => setConfirmUser(user)}
                                className="px-2 py-1 text-[10px] font-semibold text-green-600 border border-green-200 rounded hover:bg-green-50 transition-colors">
                                Reactivate
                              </button>
                            )}
                          </div>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {!isAdmin && (
            <div className="flex items-center gap-2 px-4 py-3 bg-[#F8FAFD] border border-[#DDE4EE] rounded-xl text-xs text-gray-400">
              <AlertIcon className="w-3.5 h-3.5 shrink-0" />
              User management actions (add, edit, deactivate) require Bank Admin access.
            </div>
          )}
        </div>

        {/* Permissions matrix */}
        <div className="bg-white rounded-xl border border-[#E8EDF5] p-5 h-fit">
          <div className="flex items-center gap-2 mb-4">
            <ShieldIcon className="w-4 h-4 text-[#003087]" />
            <h3 className="text-sm font-semibold text-[#1B2A4A]">Role Permissions</h3>
          </div>
          <div className="mb-3">
            <select value={selectedPermRole} onChange={e => setSelectedPermRole(e.target.value as BankRole)}
              className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]">
              <option>Bank Admin</option>
              <option>Operations</option>
              <option>Finance</option>
              <option>Reconciliation</option>
            </select>
          </div>
          <div className="space-y-1.5">
            {ALL_PERMISSIONS.map(perm => {
              const has = rolePermissions[selectedPermRole].includes(perm)
              return (
                <div key={perm} className={`flex items-center justify-between px-3 py-2 rounded-lg text-xs ${has ? 'bg-green-50' : 'bg-gray-50'}`}>
                  <span className={has ? 'text-gray-700 font-medium' : 'text-gray-400'}>{perm}</span>
                  {has
                    ? <CheckIcon className="w-3.5 h-3.5 text-green-600 shrink-0" />
                    : <XIcon className="w-3.5 h-3.5 text-gray-300 shrink-0" />}
                </div>
              )
            })}
          </div>
        </div>
      </div>

      {/* Deactivate / Reactivate confirm modal */}
      {confirmUser && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl shadow-2xl p-6 w-full max-w-sm mx-4">
            <h3 className="text-base font-semibold text-[#1B2A4A] mb-2">
              {confirmUser.status === 'Active' ? 'Deactivate User' : 'Reactivate User'}
            </h3>
            <p className="text-sm text-gray-500 mb-1">
              {confirmUser.status === 'Active'
                ? <>Are you sure you want to deactivate <strong>{confirmUser.name}</strong>? They will immediately lose access to the Bank Back Office.</>
                : <>Reactivate <strong>{confirmUser.name}</strong>? They will regain access according to their assigned role.</>}
            </p>
            {confirmUser.status === 'Active' && (
              <p className="text-xs text-amber-600 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 mt-3">
                Historical activity and audit records are retained.
              </p>
            )}
            <div className="flex gap-3 mt-5">
              <button onClick={() => setConfirmUser(null)}
                className="flex-1 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">Cancel</button>
              <button onClick={handleToggleStatus}
                className={`flex-1 py-2.5 text-sm font-semibold text-white rounded-lg transition-colors ${confirmUser.status === 'Active' ? 'bg-red-600 hover:bg-red-700' : 'bg-green-600 hover:bg-green-700'}`}>
                {confirmUser.status === 'Active' ? 'Deactivate' : 'Reactivate'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

/* ── Shared form fields ── */
function UserForm({ form, setForm, error }: {
  form: UserFormData
  setForm: (f: UserFormData) => void
  error: string
}) {
  return (
    <div className="space-y-4">
      {error && (
        <div className="flex items-center gap-2 px-3 py-2 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700">
          <AlertIcon className="w-3.5 h-3.5 shrink-0" />{error}
        </div>
      )}
      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Full Name <span className="text-red-400">*</span></label>
          <input type="text" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })}
            placeholder="e.g. Ahmed Mohamed"
            className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]" />
        </div>
        <div>
          <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Email <span className="text-red-400">*</span></label>
          <input type="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })}
            placeholder="email@cibeg.com"
            className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]" />
        </div>
        <div>
          <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Username</label>
          <input type="text" value={form.username} onChange={e => setForm({ ...form, username: e.target.value })}
            placeholder="Auto-generated if blank"
            className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]" />
        </div>
        <div>
          <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Role <span className="text-red-400">*</span></label>
          <select value={form.role} onChange={e => setForm({ ...form, role: e.target.value as BankRole })}
            className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]">
            <option value="Bank Admin">Bank Admin</option>
            <option value="Operations">Operations</option>
            <option value="Finance">Finance</option>
            <option value="Reconciliation">Reconciliation</option>
          </select>
        </div>
        <div>
          <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Department</label>
          <select value={form.department} onChange={e => setForm({ ...form, department: e.target.value })}
            className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]">
            <option value="">Select department</option>
            <option>IT & Operations</option>
            <option>Operations</option>
            <option>Finance</option>
            <option>Collections</option>
            <option>Compliance</option>
            <option>Risk</option>
          </select>
        </div>
      </div>
    </div>
  )
}

interface UserFormData {
  name: string
  email: string
  username: string
  role: BankRole
  department: string
}
