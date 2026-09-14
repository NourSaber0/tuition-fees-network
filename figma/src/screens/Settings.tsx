import { useState } from 'react'
import { CheckIcon, PlusIcon, EditIcon, SettingsIcon } from '../components/Icons'

const tabs = ['Fee Types', 'Payment Statuses', 'EPP Configuration', 'Notification Settings', 'School Configuration']

function Toggle({ checked, onChange }: { checked: boolean; onChange: () => void }) {
  return (
    <button
      onClick={onChange}
      className={`relative w-9 h-5 rounded-full transition-colors ${checked ? 'bg-[#003087]' : 'bg-gray-200'}`}
    >
      <span className={`absolute top-0.5 w-4 h-4 bg-white rounded-full shadow transition-all ${checked ? 'left-4.5' : 'left-0.5'}`} />
    </button>
  )
}

function SaveButton({ onSave, saved }: { onSave: () => void; saved: boolean }) {
  return (
    <button
      onClick={onSave}
      className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-lg transition-all ${
        saved ? 'bg-green-50 text-green-700 border border-green-200' : 'bg-[#003087] text-white hover:bg-[#002060]'
      }`}
    >
      {saved ? <><CheckIcon className="w-4 h-4" /> Saved</> : 'Save Changes'}
    </button>
  )
}

export default function Settings() {
  const [tab, setTab] = useState(0)
  const [saved, setSaved] = useState(false)

  const [feeTypes] = useState([
    { id: 1, name: 'Tuition Fee', code: 'TUITION', taxable: false, active: true },
    { id: 2, name: 'Activity Fee', code: 'ACTIVITY', taxable: false, active: true },
    { id: 3, name: 'Bus Transportation', code: 'BUS', taxable: false, active: true },
    { id: 4, name: 'Uniform Fee', code: 'UNIFORM', taxable: false, active: true },
    { id: 5, name: 'Registration Fee', code: 'REGFEE', taxable: false, active: true },
    { id: 6, name: 'Book Fee', code: 'BOOKS', taxable: false, active: true },
    { id: 7, name: 'Laboratory Fee', code: 'LAB', taxable: false, active: false },
  ])

  const [eppConfig, setEppConfig] = useState({
    tenors: { 3: true, 6: true, 12: true, 18: true },
    minAmount: 5000,
    maxAmount: 100000,
    interest3: 10,
    interest6: 12,
    interest12: 14,
    interest18: 16,
    adminFeeRate: 1.0,
    requireApproval: false,
    maxPlansPerStudent: 2,
  })

  const [notifSettings, setNotifSettings] = useState({
    failedPayments: true,
    reconExceptions: true,
    schoolUploadErrors: true,
    newSchoolReg: true,
    systemAlerts: true,
    dailySummary: true,
    emailNotifs: true,
    smsAlerts: false,
    slackIntegration: false,
  })

  const [schoolConfig, setSchoolConfig] = useState({
    requireDualApproval: true,
    autoIntegration: false,
    maxStudentsPerUpload: 5000,
    uploadFormats: { xlsx: true, csv: true, xml: false },
    activationDelay: 24,
    requireMOECertificate: true,
  })

  const handleSave = () => {
    setSaved(true)
    setTimeout(() => setSaved(false), 2500)
  }

  return (
    <div className="space-y-4">
      {/* Tab nav */}
      <div className="flex border-b border-[#E8EDF5] bg-white rounded-t-xl overflow-hidden">
        {tabs.map((t, i) => (
          <button
            key={t}
            onClick={() => setTab(i)}
            className={`px-5 py-3.5 text-xs font-semibold transition-colors border-b-2 whitespace-nowrap ${
              tab === i ? 'text-[#003087] border-[#003087] bg-[#EBF1FB]' : 'text-gray-400 border-transparent hover:text-gray-700'
            }`}
          >
            {t}
          </button>
        ))}
      </div>

      {/* Fee Types */}
      {tab === 0 && (
        <div className="bg-white rounded-b-xl rounded-tr-xl border border-[#E8EDF5] border-t-0 p-6">
          <div className="flex items-center justify-between mb-5">
            <div>
              <h3 className="text-sm font-semibold text-[#1B2A4A]">Fee Types</h3>
              <p className="text-xs text-gray-400 mt-0.5">Configure the fee categories schools can submit</p>
            </div>
            <button className="flex items-center gap-2 px-3 py-2 text-xs font-semibold text-[#003087] border border-[#003087]/30 rounded-lg hover:bg-[#EBF1FB] transition-colors">
              <PlusIcon className="w-3.5 h-3.5" /> Add Fee Type
            </button>
          </div>
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-[#F8FAFD] border border-[#E8EDF5] rounded-lg">
                {['Fee Name', 'Code', 'Taxable', 'Active', 'Actions'].map(h => (
                  <th key={h} className="text-left px-4 py-3 text-[11px] font-semibold text-gray-400 uppercase tracking-wider">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {feeTypes.map(fee => (
                <tr key={fee.id} className="hover:bg-[#F8FAFD]">
                  <td className="px-4 py-3 text-sm font-medium text-gray-800">{fee.name}</td>
                  <td className="px-4 py-3 font-mono text-xs text-[#003087] font-semibold">{fee.code}</td>
                  <td className="px-4 py-3">
                    <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${fee.taxable ? 'bg-amber-50 text-amber-700 border-amber-200' : 'bg-gray-100 text-gray-500 border-gray-200'}`}>
                      {fee.taxable ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${fee.active ? 'bg-green-50 text-green-700 border-green-200' : 'bg-gray-100 text-gray-500 border-gray-200'}`}>
                      {fee.active ? 'Active' : 'Inactive'}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <button className="p-1.5 rounded hover:bg-[#003087]/10 text-[#003087] transition-colors">
                      <EditIcon className="w-3.5 h-3.5" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Payment Statuses */}
      {tab === 1 && (
        <div className="bg-white rounded-b-xl rounded-tr-xl border border-[#E8EDF5] border-t-0 p-6">
          <h3 className="text-sm font-semibold text-[#1B2A4A] mb-5">Payment Status Configuration</h3>
          <div className="grid grid-cols-2 gap-4">
            {[
              { status: 'Successful', desc: 'Payment authorized and captured', color: 'bg-green-50 text-green-700 border-green-200', terminal: true },
              { status: 'Pending', desc: 'Awaiting bank authorization or confirmation', color: 'bg-amber-50 text-amber-700 border-amber-200', terminal: false },
              { status: 'Failed', desc: 'Payment declined or errored', color: 'bg-red-50 text-red-700 border-red-200', terminal: true },
              { status: 'Voided', desc: 'Cancelled before capture', color: 'bg-gray-100 text-gray-600 border-gray-200', terminal: true },
            ].map(({ status, desc, color, terminal }) => (
              <div key={status} className="flex items-start gap-3 p-4 bg-gray-50 rounded-xl border border-gray-100">
                <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${color} shrink-0`}>{status}</span>
                <div className="flex-1">
                  <p className="text-xs text-gray-600">{desc}</p>
                  <p className="text-[11px] text-gray-400 mt-1">
                    <strong>Terminal:</strong> {terminal ? 'Yes — no further transitions' : 'No — may transition to another state'}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* EPP Configuration */}
      {tab === 2 && (
        <div className="bg-white rounded-b-xl rounded-tr-xl border border-[#E8EDF5] border-t-0 p-6 space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-sm font-semibold text-[#1B2A4A]">EPP Configuration</h3>
              <p className="text-xs text-gray-400 mt-0.5">Configure installment plan terms and pricing</p>
            </div>
            <SaveButton onSave={handleSave} saved={saved} />
          </div>

          <div>
            <h4 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Available Tenors</h4>
            <div className="flex gap-3">
              {([3, 6, 12, 18] as const).map(tenor => (
                <label key={tenor} className="flex items-center gap-2 cursor-pointer bg-gray-50 rounded-lg px-4 py-3 border border-gray-200 hover:border-[#003087]/30 transition-colors">
                  <input
                    type="checkbox"
                    checked={eppConfig.tenors[tenor]}
                    onChange={() => setEppConfig({ ...eppConfig, tenors: { ...eppConfig.tenors, [tenor]: !eppConfig.tenors[tenor] } })}
                    className="accent-[#003087]"
                  />
                  <span className="text-sm font-semibold text-gray-700">{tenor} months</span>
                </label>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-2 gap-6">
            <div>
              <h4 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Amount Limits (EGP)</h4>
              <div className="grid grid-cols-2 gap-3">
                {[
                  { label: 'Minimum Amount', key: 'minAmount', value: eppConfig.minAmount },
                  { label: 'Maximum Amount', key: 'maxAmount', value: eppConfig.maxAmount },
                ].map(({ label, key, value }) => (
                  <div key={key}>
                    <label className="block text-[11px] text-gray-400 font-semibold mb-1.5">{label}</label>
                    <input
                      type="number"
                      value={value}
                      onChange={e => setEppConfig({ ...eppConfig, [key]: Number(e.target.value) })}
                      className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm font-mono text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
                    />
                  </div>
                ))}
              </div>
            </div>

            <div>
              <h4 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Interest Rates (% per annum)</h4>
              <div className="grid grid-cols-2 gap-3">
                {[
                  { label: '3-month rate', key: 'interest3', value: eppConfig.interest3 },
                  { label: '6-month rate', key: 'interest6', value: eppConfig.interest6 },
                  { label: '12-month rate', key: 'interest12', value: eppConfig.interest12 },
                  { label: '18-month rate', key: 'interest18', value: eppConfig.interest18 },
                ].map(({ label, key, value }) => (
                  <div key={key}>
                    <label className="block text-[11px] text-gray-400 font-semibold mb-1.5">{label}</label>
                    <div className="relative">
                      <input
                        type="number"
                        value={value}
                        onChange={e => setEppConfig({ ...eppConfig, [key]: Number(e.target.value) })}
                        className="w-full border border-[#DDE3EF] rounded-lg px-3 pr-6 py-2 text-sm font-mono text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
                      />
                      <span className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-gray-400">%</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div>
            <h4 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Advanced Settings</h4>
            <div className="space-y-3">
              {[
                { label: 'Require manual approval for all EPP plans', key: 'requireApproval', value: eppConfig.requireApproval },
              ].map(({ label, key, value }) => (
                <div key={key} className="flex items-center justify-between bg-gray-50 rounded-lg px-4 py-3">
                  <span className="text-sm text-gray-700">{label}</span>
                  <Toggle checked={value} onChange={() => setEppConfig({ ...eppConfig, [key]: !value })} />
                </div>
              ))}
              <div className="flex items-center justify-between bg-gray-50 rounded-lg px-4 py-3">
                <span className="text-sm text-gray-700">Max active EPP plans per student</span>
                <input
                  type="number"
                  value={eppConfig.maxPlansPerStudent}
                  onChange={e => setEppConfig({ ...eppConfig, maxPlansPerStudent: Number(e.target.value) })}
                  className="w-16 border border-[#DDE3EF] rounded-lg px-2 py-1.5 text-sm text-center font-mono text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200"
                />
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Notification Settings */}
      {tab === 3 && (
        <div className="bg-white rounded-b-xl rounded-tr-xl border border-[#E8EDF5] border-t-0 p-6 space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-sm font-semibold text-[#1B2A4A]">Notification Settings</h3>
              <p className="text-xs text-gray-400 mt-0.5">Control which events trigger notifications</p>
            </div>
            <SaveButton onSave={handleSave} saved={saved} />
          </div>

          <div>
            <h4 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Event Triggers</h4>
            <div className="space-y-2">
              {[
                { label: 'Failed payment alerts', key: 'failedPayments' },
                { label: 'Reconciliation exceptions', key: 'reconExceptions' },
                { label: 'School upload errors', key: 'schoolUploadErrors' },
                { label: 'New school registrations', key: 'newSchoolReg' },
                { label: 'System alerts', key: 'systemAlerts' },
                { label: 'Daily summary digest', key: 'dailySummary' },
              ].map(({ label, key }) => (
                <div key={key} className="flex items-center justify-between bg-gray-50 rounded-lg px-4 py-3">
                  <span className="text-sm text-gray-700">{label}</span>
                  <Toggle
                    checked={notifSettings[key as keyof typeof notifSettings] as boolean}
                    onChange={() => setNotifSettings({ ...notifSettings, [key]: !notifSettings[key as keyof typeof notifSettings] })}
                  />
                </div>
              ))}
            </div>
          </div>

          <div>
            <h4 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Delivery Channels</h4>
            <div className="space-y-2">
              {[
                { label: 'In-app notifications', key: 'emailNotifs', alwaysOn: true },
                { label: 'Email notifications', key: 'emailNotifs', alwaysOn: false },
                { label: 'SMS alerts (high severity only)', key: 'smsAlerts', alwaysOn: false },
                { label: 'Slack integration', key: 'slackIntegration', alwaysOn: false },
              ].map(({ label, key, alwaysOn }, i) => (
                <div key={i} className="flex items-center justify-between bg-gray-50 rounded-lg px-4 py-3">
                  <span className={`text-sm ${alwaysOn ? 'text-gray-400' : 'text-gray-700'}`}>{label}</span>
                  <Toggle
                    checked={alwaysOn || notifSettings[key as keyof typeof notifSettings] as boolean}
                    onChange={() => !alwaysOn && setNotifSettings({ ...notifSettings, [key]: !notifSettings[key as keyof typeof notifSettings] })}
                  />
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* School Configuration */}
      {tab === 4 && (
        <div className="bg-white rounded-b-xl rounded-tr-xl border border-[#E8EDF5] border-t-0 p-6 space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-sm font-semibold text-[#1B2A4A]">School Configuration</h3>
              <p className="text-xs text-gray-400 mt-0.5">Global settings applied to all school onboarding and operations</p>
            </div>
            <SaveButton onSave={handleSave} saved={saved} />
          </div>

          <div className="space-y-2">
            {[
              { label: 'Require dual approval for school activation', key: 'requireDualApproval' },
              { label: 'Auto-integration after approval', key: 'autoIntegration' },
              { label: 'Require Ministry of Education certificate', key: 'requireMOECertificate' },
            ].map(({ label, key }) => (
              <div key={key} className="flex items-center justify-between bg-gray-50 rounded-lg px-4 py-3">
                <span className="text-sm text-gray-700">{label}</span>
                <Toggle
                  checked={schoolConfig[key as keyof typeof schoolConfig] as boolean}
                  onChange={() => setSchoolConfig({ ...schoolConfig, [key]: !schoolConfig[key as keyof typeof schoolConfig] })}
                />
              </div>
            ))}
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                Max students per upload
              </label>
              <input
                type="number"
                value={schoolConfig.maxStudentsPerUpload}
                onChange={e => setSchoolConfig({ ...schoolConfig, maxStudentsPerUpload: Number(e.target.value) })}
                className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm font-mono text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
              />
            </div>
            <div>
              <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                Post-approval activation delay (hours)
              </label>
              <input
                type="number"
                value={schoolConfig.activationDelay}
                onChange={e => setSchoolConfig({ ...schoolConfig, activationDelay: Number(e.target.value) })}
                className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm font-mono text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
              />
            </div>
          </div>

          <div>
            <h4 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Allowed Upload Formats</h4>
            <div className="flex gap-3">
              {(['xlsx', 'csv', 'xml'] as const).map(fmt => (
                <label key={fmt} className="flex items-center gap-2 cursor-pointer bg-gray-50 rounded-lg px-4 py-3 border border-gray-200 hover:border-[#003087]/30 transition-colors">
                  <input
                    type="checkbox"
                    checked={schoolConfig.uploadFormats[fmt]}
                    onChange={() => setSchoolConfig({ ...schoolConfig, uploadFormats: { ...schoolConfig.uploadFormats, [fmt]: !schoolConfig.uploadFormats[fmt] } })}
                    className="accent-[#003087]"
                  />
                  <span className="text-sm font-semibold text-gray-700 uppercase">{fmt}</span>
                </label>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
