"use client";

import { useEffect, useState } from "react";
import { useApiClient } from "@tuition/api-client";
import { LoadingSpinner, CheckIcon, PlusIcon, EditIcon, AlertIcon } from "@tuition/ui";
import type {
  FeeTypeSettingDto,
  CreateFeeTypeRequest,
  UpdateFeeTypeRequest,
  PaymentStatusInfo,
  EppSettingsDto,
  NotificationSettingsDto,
  InstitutionSettingsDto,
} from "./types";

const TABS = ["Fee Types", "Payment Statuses", "EPP Configuration", "Notification Settings", "School Configuration"];
const TENORS = [3, 6, 12, 18];

function Toggle({ checked, onChange, disabled }: { checked: boolean; onChange: () => void; disabled?: boolean }) {
  return (
    <button
      type="button"
      onClick={onChange}
      disabled={disabled}
      className={`relative w-9 h-5 rounded-full transition-colors disabled:opacity-40 ${checked ? "bg-[#003087]" : "bg-gray-200"}`}
    >
      <span
        className={`absolute top-0.5 w-4 h-4 bg-white rounded-full shadow transition-all ${checked ? "left-[18px]" : "left-0.5"}`}
      />
    </button>
  );
}

function SaveButton({ onSave, saving, saved }: { onSave: () => void; saving: boolean; saved: boolean }) {
  return (
    <button
      onClick={onSave}
      disabled={saving}
      className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-lg transition-all disabled:opacity-60 ${
        saved ? "bg-green-50 text-green-700 border border-green-200" : "bg-[#003087] text-white hover:bg-[#002060]"
      }`}
    >
      {saving ? "Saving…" : saved ? (
        <>
          <CheckIcon className="w-4 h-4" /> Saved
        </>
      ) : (
        "Save Changes"
      )}
    </button>
  );
}

function ErrorBanner({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <div className="flex items-center gap-2 px-3 py-2 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700">
      <AlertIcon className="w-3.5 h-3.5 shrink-0" />
      {message}
    </div>
  );
}

interface FeeTypeFormData {
  name: string;
  code: string;
  taxable: boolean;
  active: boolean;
}
const emptyFeeForm: FeeTypeFormData = { name: "", code: "", taxable: false, active: true };

function FeeTypesTab() {
  const apiClient = useApiClient();
  const [feeTypes, setFeeTypes] = useState<FeeTypeSettingDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [showAddForm, setShowAddForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<FeeTypeFormData>(emptyFeeForm);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    apiClient
      .get<FeeTypeSettingDto[]>("/settings/fee-types")
      .then((res) => setFeeTypes(res ?? []))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [apiClient]);

  const openAdd = () => {
    setShowAddForm(true);
    setEditingId(null);
    setForm(emptyFeeForm);
    setError(null);
  };

  const openEdit = (f: FeeTypeSettingDto) => {
    setEditingId(f.id);
    setShowAddForm(false);
    setForm({ name: f.name, code: f.code, taxable: f.taxable, active: f.active });
    setError(null);
  };

  const cancel = () => {
    setShowAddForm(false);
    setEditingId(null);
    setForm(emptyFeeForm);
    setError(null);
  };

  const handleAdd = async () => {
    if (!form.name.trim() || !form.code.trim()) {
      setError("Name and code are required.");
      return;
    }
    setSaving(true);
    try {
      const payload: CreateFeeTypeRequest = {
        name: form.name.trim(),
        code: form.code.trim(),
        taxable: form.taxable,
        active: form.active,
      };
      const created = await apiClient.post<FeeTypeSettingDto>("/settings/fee-types", payload);
      setFeeTypes((prev) => [...prev, created].sort((a, b) => a.name.localeCompare(b.name)));
      cancel();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to add fee type.");
    } finally {
      setSaving(false);
    }
  };

  const handleSaveEdit = async () => {
    if (!editingId) return;
    if (!form.name.trim() || !form.code.trim()) {
      setError("Name and code are required.");
      return;
    }
    setSaving(true);
    try {
      const payload: UpdateFeeTypeRequest = {
        name: form.name.trim(),
        code: form.code.trim(),
        taxable: form.taxable,
        active: form.active,
      };
      const updated = await apiClient.patch<FeeTypeSettingDto>(`/settings/fee-types/${editingId}`, payload);
      setFeeTypes((prev) => prev.map((f) => (f.id === editingId ? updated : f)));
      cancel();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to update fee type.");
    } finally {
      setSaving(false);
    }
  };

  const handleQuickToggle = async (f: FeeTypeSettingDto, field: "taxable" | "active") => {
    try {
      const updated = await apiClient.patch<FeeTypeSettingDto>(`/settings/fee-types/${f.id}`, {
        [field]: !f[field],
      } as UpdateFeeTypeRequest);
      setFeeTypes((prev) => prev.map((x) => (x.id === f.id ? updated : x)));
    } catch {
      // ignore - row keeps its previous value on failure
    }
  };

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center py-16 space-y-3">
        <LoadingSpinner />
        <p className="text-sm text-gray-500">Loading fee types...</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-sm font-semibold text-[#1B2A4A]">Fee Types</h3>
          <p className="text-xs text-gray-400 mt-0.5">Configure the fee categories schools can submit</p>
        </div>
        <button
          onClick={openAdd}
          className="flex items-center gap-2 px-3 py-2 text-xs font-semibold text-[#003087] border border-[#003087]/30 rounded-lg hover:bg-[#EBF1FB] transition-colors"
        >
          <PlusIcon className="w-3.5 h-3.5" /> Add Fee Type
        </button>
      </div>

      {(showAddForm || editingId) && (
        <div className="bg-[#F8FAFD] rounded-xl border border-[#003087]/20 p-4 space-y-3">
          <ErrorBanner message={error} />
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1">Name</label>
              <input
                type="text"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="e.g. Uniform Fee"
                className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
              />
            </div>
            <div>
              <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1">Code</label>
              <input
                type="text"
                value={form.code}
                onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase() })}
                placeholder="e.g. UNIFORM"
                className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm font-mono text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
              />
            </div>
          </div>
          <div className="flex items-center gap-6">
            <label className="flex items-center gap-2 text-sm text-gray-700">
              <input type="checkbox" checked={form.taxable} onChange={(e) => setForm({ ...form, taxable: e.target.checked })} className="accent-[#003087]" />
              Taxable
            </label>
            <label className="flex items-center gap-2 text-sm text-gray-700">
              <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} className="accent-[#003087]" />
              Active
            </label>
          </div>
          <div className="flex gap-3">
            <button onClick={cancel} className="px-4 py-2 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
              Cancel
            </button>
            <button
              onClick={editingId ? handleSaveEdit : handleAdd}
              disabled={saving}
              className="px-4 py-2 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors disabled:opacity-50"
            >
              {saving ? "Saving…" : editingId ? "Save Changes" : "Add Fee Type"}
            </button>
          </div>
        </div>
      )}

      <table className="w-full text-sm">
        <thead>
          <tr className="bg-[#F8FAFD] border border-[#E8EDF5] rounded-lg">
            {["Fee Name", "Code", "Taxable", "Active", "Actions"].map((h) => (
              <th key={h} className="text-left px-4 py-3 text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-50">
          {feeTypes.map((fee) => (
            <tr key={fee.id} className="hover:bg-[#F8FAFD]">
              <td className="px-4 py-3 text-sm font-medium text-gray-800">{fee.name}</td>
              <td className="px-4 py-3 font-mono text-xs text-[#003087] font-semibold">{fee.code}</td>
              <td className="px-4 py-3">
                <button
                  onClick={() => handleQuickToggle(fee, "taxable")}
                  className={`text-[11px] font-semibold px-2 py-0.5 rounded border transition-colors ${
                    fee.taxable ? "bg-amber-50 text-amber-700 border-amber-200" : "bg-gray-100 text-gray-500 border-gray-200"
                  }`}
                >
                  {fee.taxable ? "Yes" : "No"}
                </button>
              </td>
              <td className="px-4 py-3">
                <button
                  onClick={() => handleQuickToggle(fee, "active")}
                  className={`text-[11px] font-semibold px-2 py-0.5 rounded border transition-colors ${
                    fee.active ? "bg-green-50 text-green-700 border-green-200" : "bg-gray-100 text-gray-500 border-gray-200"
                  }`}
                >
                  {fee.active ? "Active" : "Inactive"}
                </button>
              </td>
              <td className="px-4 py-3">
                <button onClick={() => openEdit(fee)} className="p-1.5 rounded hover:bg-[#003087]/10 text-[#003087] transition-colors">
                  <EditIcon className="w-3.5 h-3.5" />
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function PaymentStatusesTab() {
  const apiClient = useApiClient();
  const [statuses, setStatuses] = useState<PaymentStatusInfo[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    apiClient
      .get<PaymentStatusInfo[]>("/settings/payment-statuses")
      .then((res) => setStatuses(res ?? []))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [apiClient]);

  const colorFor = (status: string) => {
    const s = status.toLowerCase();
    if (s === "successful") return "bg-green-50 text-green-700 border-green-200";
    if (s === "pending") return "bg-amber-50 text-amber-700 border-amber-200";
    if (s === "failed" || s === "reversed") return "bg-red-50 text-red-700 border-red-200";
    return "bg-gray-100 text-gray-600 border-gray-200";
  };

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center py-16 space-y-3">
        <LoadingSpinner />
      </div>
    );
  }

  return (
    <div>
      <h3 className="text-sm font-semibold text-[#1B2A4A] mb-5">Payment Status Configuration</h3>
      <div className="grid grid-cols-2 gap-4">
        {statuses.map(({ status, description, terminal }) => (
          <div key={status} className="flex items-start gap-3 p-4 bg-gray-50 rounded-xl border border-gray-100">
            <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border shrink-0 ${colorFor(status)}`}>{status}</span>
            <div className="flex-1">
              <p className="text-xs text-gray-600">{description}</p>
              <p className="text-[11px] text-gray-400 mt-1">
                <strong>Terminal:</strong> {terminal ? "Yes — no further transitions" : "No — may transition to another state"}
              </p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function EppConfigTab() {
  const apiClient = useApiClient();
  const [settings, setSettings] = useState<EppSettingsDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiClient
      .get<EppSettingsDto>("/settings/epp")
      .then(setSettings)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [apiClient]);

  const handleSave = async () => {
    if (!settings) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await apiClient.put<EppSettingsDto>("/settings/epp", settings);
      setSettings(updated);
      setSaved(true);
      setTimeout(() => setSaved(false), 2500);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save EPP settings.");
    } finally {
      setSaving(false);
    }
  };

  if (loading || !settings) {
    return (
      <div className="flex flex-col items-center justify-center py-16 space-y-3">
        <LoadingSpinner />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-sm font-semibold text-[#1B2A4A]">EPP Configuration</h3>
          <p className="text-xs text-gray-400 mt-0.5">Configure installment plan terms and pricing</p>
        </div>
        <SaveButton onSave={handleSave} saving={saving} saved={saved} />
      </div>

      <ErrorBanner message={error} />

      <div>
        <h4 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Available Tenors</h4>
        <div className="flex gap-3">
          {TENORS.map((tenor) => (
            <label
              key={tenor}
              className="flex items-center gap-2 cursor-pointer bg-gray-50 rounded-lg px-4 py-3 border border-gray-200 hover:border-[#003087]/30 transition-colors"
            >
              <input
                type="checkbox"
                checked={!!settings.tenors[String(tenor)]}
                onChange={() =>
                  setSettings({
                    ...settings,
                    tenors: { ...settings.tenors, [String(tenor)]: !settings.tenors[String(tenor)] },
                  })
                }
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
            <div>
              <label className="block text-[11px] text-gray-400 font-semibold mb-1.5">Minimum Amount</label>
              <input
                type="number"
                value={settings.minAmountEGP}
                onChange={(e) => setSettings({ ...settings, minAmountEGP: Number(e.target.value) })}
                className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm font-mono text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
              />
            </div>
            <div>
              <label className="block text-[11px] text-gray-400 font-semibold mb-1.5">Maximum Amount</label>
              <input
                type="number"
                value={settings.maxAmountEGP}
                onChange={(e) => setSettings({ ...settings, maxAmountEGP: Number(e.target.value) })}
                className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm font-mono text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
              />
            </div>
          </div>
        </div>

        <div>
          <h4 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Interest Rates (% per annum)</h4>
          <div className="grid grid-cols-2 gap-3">
            {TENORS.map((tenor) => (
              <div key={tenor}>
                <label className="block text-[11px] text-gray-400 font-semibold mb-1.5">{tenor}-month rate</label>
                <div className="relative">
                  <input
                    type="number"
                    value={settings.interestRatePct[String(tenor)] ?? 0}
                    onChange={(e) =>
                      setSettings({
                        ...settings,
                        interestRatePct: { ...settings.interestRatePct, [String(tenor)]: Number(e.target.value) },
                      })
                    }
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
        <h4 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Admin Fee</h4>
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-[11px] text-gray-400 font-semibold mb-1.5">Rate (%)</label>
            <input
              type="number"
              value={settings.adminFeeRatePct}
              onChange={(e) => setSettings({ ...settings, adminFeeRatePct: Number(e.target.value) })}
              className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm font-mono text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
            />
          </div>
          <div>
            <label className="block text-[11px] text-gray-400 font-semibold mb-1.5">Cap (EGP)</label>
            <input
              type="number"
              value={settings.adminFeeCapEGP}
              onChange={(e) => setSettings({ ...settings, adminFeeCapEGP: Number(e.target.value) })}
              className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm font-mono text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
            />
          </div>
        </div>
      </div>

      <div>
        <h4 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Advanced Settings</h4>
        <div className="space-y-3">
          <div className="flex items-center justify-between bg-gray-50 rounded-lg px-4 py-3">
            <span className="text-sm text-gray-700">Require manual approval for all EPP plans</span>
            <Toggle checked={settings.requireApproval} onChange={() => setSettings({ ...settings, requireApproval: !settings.requireApproval })} />
          </div>
          <div className="flex items-center justify-between bg-gray-50 rounded-lg px-4 py-3">
            <span className="text-sm text-gray-700">Max active EPP plans per student</span>
            <input
              type="number"
              value={settings.maxPlansPerStudent}
              onChange={(e) => setSettings({ ...settings, maxPlansPerStudent: Number(e.target.value) })}
              className="w-16 border border-[#DDE3EF] rounded-lg px-2 py-1.5 text-sm text-center font-mono text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200"
            />
          </div>
        </div>
      </div>
    </div>
  );
}

function NotificationSettingsTab() {
  const apiClient = useApiClient();
  const [settings, setSettings] = useState<NotificationSettingsDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiClient
      .get<NotificationSettingsDto>("/settings/notifications")
      .then(setSettings)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [apiClient]);

  const handleSave = async () => {
    if (!settings) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await apiClient.put<NotificationSettingsDto>("/settings/notifications", settings);
      setSettings(updated);
      setSaved(true);
      setTimeout(() => setSaved(false), 2500);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save notification settings.");
    } finally {
      setSaving(false);
    }
  };

  if (loading || !settings) {
    return (
      <div className="flex flex-col items-center justify-center py-16 space-y-3">
        <LoadingSpinner />
      </div>
    );
  }

  const eventRows: { label: string; key: keyof NotificationSettingsDto["events"] }[] = [
    { label: "Failed payment alerts", key: "failedPayments" },
    { label: "Reconciliation exceptions", key: "reconExceptions" },
    { label: "School upload errors", key: "schoolUploadErrors" },
    { label: "New school registrations", key: "newSchoolReg" },
    { label: "System alerts", key: "systemAlerts" },
    { label: "Daily summary digest", key: "dailySummary" },
  ];

  const channelRows: { label: string; key: keyof NotificationSettingsDto["channels"] }[] = [
    { label: "In-app notifications", key: "inApp" },
    { label: "Email notifications", key: "email" },
    { label: "SMS alerts (high severity only)", key: "sms" },
    { label: "Slack integration", key: "slack" },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-sm font-semibold text-[#1B2A4A]">Notification Settings</h3>
          <p className="text-xs text-gray-400 mt-0.5">Control which events trigger notifications</p>
        </div>
        <SaveButton onSave={handleSave} saving={saving} saved={saved} />
      </div>

      <ErrorBanner message={error} />

      <div>
        <h4 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Event Triggers</h4>
        <div className="space-y-2">
          {eventRows.map(({ label, key }) => (
            <div key={key} className="flex items-center justify-between bg-gray-50 rounded-lg px-4 py-3">
              <span className="text-sm text-gray-700">{label}</span>
              <Toggle
                checked={settings.events[key]}
                onChange={() => setSettings({ ...settings, events: { ...settings.events, [key]: !settings.events[key] } })}
              />
            </div>
          ))}
        </div>
      </div>

      <div>
        <h4 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Delivery Channels</h4>
        <div className="space-y-2">
          {channelRows.map(({ label, key }) => (
            <div key={key} className="flex items-center justify-between bg-gray-50 rounded-lg px-4 py-3">
              <span className="text-sm text-gray-700">{label}</span>
              <Toggle
                checked={settings.channels[key]}
                onChange={() => setSettings({ ...settings, channels: { ...settings.channels, [key]: !settings.channels[key] } })}
              />
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function SchoolConfigTab() {
  const apiClient = useApiClient();
  const [settings, setSettings] = useState<InstitutionSettingsDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiClient
      .get<InstitutionSettingsDto>("/settings/institutions")
      .then(setSettings)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [apiClient]);

  const handleSave = async () => {
    if (!settings) return;
    setSaving(true);
    setError(null);
    try {
      const updated = await apiClient.put<InstitutionSettingsDto>("/settings/institutions", settings);
      setSettings(updated);
      setSaved(true);
      setTimeout(() => setSaved(false), 2500);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to save school configuration.");
    } finally {
      setSaving(false);
    }
  };

  if (loading || !settings) {
    return (
      <div className="flex flex-col items-center justify-center py-16 space-y-3">
        <LoadingSpinner />
      </div>
    );
  }

  const toggleRows: { label: string; key: "requireDualApproval" | "autoIntegrationAfterApproval" | "requireMOECertificate" }[] = [
    { label: "Require dual approval for school activation", key: "requireDualApproval" },
    { label: "Auto-integration after approval", key: "autoIntegrationAfterApproval" },
    { label: "Require Ministry of Education certificate", key: "requireMOECertificate" },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-sm font-semibold text-[#1B2A4A]">School Configuration</h3>
          <p className="text-xs text-gray-400 mt-0.5">Global settings applied to all school onboarding and operations</p>
        </div>
        <SaveButton onSave={handleSave} saving={saving} saved={saved} />
      </div>

      <ErrorBanner message={error} />

      <div className="space-y-2">
        {toggleRows.map(({ label, key }) => (
          <div key={key} className="flex items-center justify-between bg-gray-50 rounded-lg px-4 py-3">
            <span className="text-sm text-gray-700">{label}</span>
            <Toggle checked={settings[key]} onChange={() => setSettings({ ...settings, [key]: !settings[key] })} />
          </div>
        ))}
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Max students per upload</label>
          <input
            type="number"
            value={settings.maxStudentsPerUpload}
            onChange={(e) => setSettings({ ...settings, maxStudentsPerUpload: Number(e.target.value) })}
            className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm font-mono text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
          />
        </div>
        <div>
          <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Post-approval activation delay (hours)</label>
          <input
            type="number"
            value={settings.postApprovalActivationDelayHours}
            onChange={(e) => setSettings({ ...settings, postApprovalActivationDelayHours: Number(e.target.value) })}
            className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm font-mono text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
          />
        </div>
      </div>

      <div>
        <h4 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Allowed Upload Formats</h4>
        <div className="flex gap-3">
          {(["xlsx", "csv", "xml"] as const).map((fmt) => (
            <label
              key={fmt}
              className="flex items-center gap-2 cursor-pointer bg-gray-50 rounded-lg px-4 py-3 border border-gray-200 hover:border-[#003087]/30 transition-colors"
            >
              <input
                type="checkbox"
                checked={settings.allowedUploadFormats[fmt]}
                onChange={() =>
                  setSettings({
                    ...settings,
                    allowedUploadFormats: { ...settings.allowedUploadFormats, [fmt]: !settings.allowedUploadFormats[fmt] },
                  })
                }
                className="accent-[#003087]"
              />
              <span className="text-sm font-semibold text-gray-700 uppercase">{fmt}</span>
            </label>
          ))}
        </div>
      </div>
    </div>
  );
}

export default function SettingsPage() {
  const [tab, setTab] = useState(0);

  return (
    <div className="space-y-4">
      <div className="flex border-b border-[#E8EDF5] bg-white rounded-t-xl overflow-hidden">
        {TABS.map((t, i) => (
          <button
            key={t}
            onClick={() => setTab(i)}
            className={`px-5 py-3.5 text-xs font-semibold transition-colors border-b-2 whitespace-nowrap ${
              tab === i ? "text-[#003087] border-[#003087] bg-[#EBF1FB]" : "text-gray-400 border-transparent hover:text-gray-700"
            }`}
          >
            {t}
          </button>
        ))}
      </div>

      <div className="bg-white rounded-b-xl rounded-tr-xl border border-[#E8EDF5] border-t-0 p-6">
        {tab === 0 && <FeeTypesTab />}
        {tab === 1 && <PaymentStatusesTab />}
        {tab === 2 && <EppConfigTab />}
        {tab === 3 && <NotificationSettingsTab />}
        {tab === 4 && <SchoolConfigTab />}
      </div>
    </div>
  );
}
