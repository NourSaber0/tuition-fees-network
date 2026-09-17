"use client";

import { useCallback, useEffect, useState } from "react";
import { useApiClient, useAuth, type PageResponse } from "@tuition/api-client";
import {
  Button,
  Modal,
  LoadingSpinner,
  Pagination,
  PlusIcon,
  EditIcon,
  ShieldIcon,
  SearchIcon,
  CheckIcon,
  XIcon,
  CheckCircleIcon,
  AlertIcon,
} from "@tuition/ui";
import type { BankUserSummaryDto, CreateBankUserRequest, RolePermissionsDto } from "./types";
import { roleColorClass, statusClass, ROLE_ID_TO_DISPLAY, segmentLabel, initials, formatDateTime } from "./badges";

const ROLE_OPTIONS = ["Bank Admin", "Operations", "Finance", "Reconciliation"];
const PERM_ROLE_IDS = ["bank-admin", "bank-operations", "bank-finance", "bank-reconciliation"];

interface UserFormData {
  name: string;
  email: string;
  username: string;
  role: string;
  department: string;
}

const emptyForm: UserFormData = { name: "", email: "", username: "", role: "Operations", department: "" };

function UserForm({
  form,
  setForm,
  error,
}: {
  form: UserFormData;
  setForm: (f: UserFormData) => void;
  error: string;
}) {
  return (
    <div className="space-y-4">
      {error && (
        <div className="flex items-center gap-2 px-3 py-2 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700">
          <AlertIcon className="w-3.5 h-3.5 shrink-0" />
          {error}
        </div>
      )}
      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
            Full Name <span className="text-red-400">*</span>
          </label>
          <input
            type="text"
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            placeholder="e.g. Ahmed Mohamed"
            className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
          />
        </div>
        <div>
          <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
            Email <span className="text-red-400">*</span>
          </label>
          <input
            type="email"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
            placeholder="email@cibeg.com"
            className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
          />
        </div>
        <div>
          <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Username</label>
          <input
            type="text"
            value={form.username}
            onChange={(e) => setForm({ ...form, username: e.target.value })}
            placeholder="Auto-generated if blank"
            className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
          />
        </div>
        <div>
          <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
            Role <span className="text-red-400">*</span>
          </label>
          <select
            value={form.role}
            onChange={(e) => setForm({ ...form, role: e.target.value })}
            className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
          >
            {ROLE_OPTIONS.map((r) => (
              <option key={r}>{r}</option>
            ))}
          </select>
        </div>
        <div className="col-span-2">
          <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Department</label>
          <input
            type="text"
            value={form.department}
            onChange={(e) => setForm({ ...form, department: e.target.value })}
            placeholder="e.g. Operations"
            className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
          />
        </div>
      </div>
    </div>
  );
}

export default function UsersPage() {
  const apiClient = useApiClient();
  const { user } = useAuth();
  const canManage = user?.role === "bank-admin";

  const [users, setUsers] = useState<BankUserSummaryDto[]>([]);
  const [summary, setSummary] = useState<Record<string, number>>({});
  const [roles, setRoles] = useState<RolePermissionsDto[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  const [showAddForm, setShowAddForm] = useState(false);
  const [editUser, setEditUser] = useState<BankUserSummaryDto | null>(null);
  const [form, setForm] = useState<UserFormData>(emptyForm);
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);

  const [confirmUser, setConfirmUser] = useState<BankUserSummaryDto | null>(null);
  const [confirmLoading, setConfirmLoading] = useState(false);

  const [successMsg, setSuccessMsg] = useState("");
  const [resetTargetId, setResetTargetId] = useState<string | null>(null);

  const [selectedPermRole, setSelectedPermRole] = useState("bank-admin");

  useEffect(() => {
    apiClient
      .get<RolePermissionsDto[]>("/roles")
      .then((res) => setRoles(res ?? []))
      .catch(() => {});
  }, [apiClient]);

  const loadAll = useCallback(() => {
    const params = new URLSearchParams();
    if (search.trim()) params.set("search", search.trim());
    params.set("page", String(page));
    params.set("pageSize", "10");

    Promise.all([
      apiClient.get<Record<string, number>>("/users/summary"),
      apiClient.get<PageResponse<BankUserSummaryDto>>(`/users?${params.toString()}`),
    ])
      .then(([summaryRes, usersRes]) => {
        setSummary(summaryRes ?? {});
        setUsers(usersRes.data ?? []);
        setTotal(usersRes.total ?? 0);
        setTotalPages(Math.max(1, usersRes.totalPages ?? 1));
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [apiClient, search, page]);

  useEffect(() => {
    loadAll();
  }, [loadAll, refreshTrigger]);

  const showSuccess = (msg: string) => {
    setSuccessMsg(msg);
    setTimeout(() => setSuccessMsg(""), 3000);
  };

  const validateForm = (): boolean => {
    if (!form.name.trim()) {
      setFormError("Full name is required.");
      return false;
    }
    if (!form.email.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
      setFormError("Enter a valid email address.");
      return false;
    }
    setFormError("");
    return true;
  };

  const handleAddUser = async () => {
    if (!validateForm()) return;
    setSaving(true);
    try {
      const payload: CreateBankUserRequest = {
        name: form.name.trim(),
        email: form.email.trim(),
        username: form.username.trim() || undefined,
        role: form.role,
        department: form.department.trim() || undefined,
      };
      const created = await apiClient.post<BankUserSummaryDto>("/users", payload);
      setShowAddForm(false);
      setForm(emptyForm);
      setFormError("");
      showSuccess(`${created.name} has been added successfully.`);
      setRefreshTrigger((p) => p + 1);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Failed to add user.");
    } finally {
      setSaving(false);
    }
  };

  const openEdit = (u: BankUserSummaryDto) => {
    setEditUser(u);
    setForm({ name: u.name, email: u.email, username: u.username, role: u.role, department: u.department ?? "" });
    setFormError("");
    setShowAddForm(false);
  };

  const handleSaveEdit = async () => {
    if (!editUser || !validateForm()) return;
    setSaving(true);
    try {
      await apiClient.patch(`/users/${editUser.id}`, {
        name: form.name.trim(),
        email: form.email.trim(),
        username: form.username.trim() || undefined,
        role: form.role,
        department: form.department.trim() || undefined,
      });
      setEditUser(null);
      setForm(emptyForm);
      setFormError("");
      showSuccess("User updated successfully.");
      setRefreshTrigger((p) => p + 1);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Failed to update user.");
    } finally {
      setSaving(false);
    }
  };

  const handleToggleStatus = async () => {
    if (!confirmUser) return;
    setConfirmLoading(true);
    const activating = confirmUser.status !== "Active";
    try {
      await apiClient.post(`/users/${confirmUser.id}/${activating ? "activate" : "deactivate"}`, {});
      showSuccess(`${confirmUser.name} has been ${activating ? "reactivated" : "deactivated"}.`);
      setConfirmUser(null);
      setRefreshTrigger((p) => p + 1);
    } catch (err) {
      showSuccess(err instanceof Error ? err.message : "Action failed.");
    } finally {
      setConfirmLoading(false);
    }
  };

  const handleResetPassword = async (u: BankUserSummaryDto) => {
    setResetTargetId(u.id);
    try {
      await apiClient.post(`/users/${u.id}/reset-password`, {});
      showSuccess(`Password reset email sent to ${u.email}.`);
    } catch (err) {
      showSuccess(err instanceof Error ? err.message : "Failed to trigger password reset.");
    } finally {
      setResetTargetId(null);
    }
  };

  const selectedPermissions = roles.find((r) => r.role === selectedPermRole)?.permissions ?? [];

  return (
    <div className="space-y-4">
      {successMsg && (
        <div className="flex items-center gap-2 px-4 py-3 bg-green-50 border border-green-200 rounded-xl text-sm text-green-700 font-medium">
          <CheckIcon className="w-4 h-4 shrink-0" />
          {successMsg}
        </div>
      )}

      <div className="grid grid-cols-4 gap-3">
        {ROLE_OPTIONS.map((role) => (
          <div key={role} className="bg-white rounded-xl border border-[#E8EDF5] p-4 flex items-center justify-between">
            <div>
              <span className={`text-[11px] font-bold px-2 py-0.5 rounded border inline-block mb-1.5 ${roleColorClass(role)}`}>{role}</span>
              <div className="text-2xl font-bold text-[#1B2A4A]">{summary[role] ?? 0}</div>
              <div className="text-[11px] text-gray-400">active users</div>
            </div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-3 gap-4">
        <div className="col-span-2 space-y-3">
          <div className="flex items-center gap-3">
            <div className="relative flex-1">
              <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-300" />
              <input
                type="text"
                placeholder="Search users…"
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  setPage(1);
                }}
                className="w-full pl-8 pr-4 py-2 border border-[#DDE3EF] rounded-lg text-sm placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] bg-white"
              />
            </div>
            {canManage && (
              <Button
                variant="primary"
                size="sm"
                onClick={() => {
                  setShowAddForm(!showAddForm);
                  setEditUser(null);
                  setForm(emptyForm);
                  setFormError("");
                }}
                className="flex items-center gap-2"
              >
                <PlusIcon className="w-4 h-4" /> Add User
              </Button>
            )}
          </div>

          {showAddForm && canManage && (
            <div className="bg-white rounded-xl border border-[#003087]/20 p-5">
              <h3 className="text-sm font-semibold text-[#1B2A4A] mb-4">Add New Bank Employee</h3>
              <UserForm form={form} setForm={setForm} error={formError} />
              <div className="flex gap-3 mt-4">
                <button
                  onClick={() => {
                    setShowAddForm(false);
                    setForm(emptyForm);
                    setFormError("");
                  }}
                  className="px-4 py-2 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  Cancel
                </button>
                <button
                  onClick={handleAddUser}
                  disabled={!form.name || !form.email || saving}
                  className="flex items-center gap-2 px-4 py-2 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors disabled:opacity-40"
                >
                  <PlusIcon className="w-3.5 h-3.5" /> {saving ? "Adding…" : "Add Employee"}
                </button>
              </div>
            </div>
          )}

          {editUser && canManage && (
            <div className="bg-white rounded-xl border border-[#F7941D]/30 p-5">
              <div className="flex items-center gap-2 mb-4">
                <EditIcon className="w-4 h-4 text-[#F7941D]" />
                <h3 className="text-sm font-semibold text-[#1B2A4A]">Edit User — {editUser.name}</h3>
              </div>
              <UserForm form={form} setForm={setForm} error={formError} />
              <div className="flex gap-3 mt-4">
                <button
                  onClick={() => {
                    setEditUser(null);
                    setForm(emptyForm);
                    setFormError("");
                  }}
                  className="px-4 py-2 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  Cancel
                </button>
                <button
                  onClick={handleSaveEdit}
                  disabled={saving}
                  className="flex items-center gap-2 px-4 py-2 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors disabled:opacity-50"
                >
                  <CheckCircleIcon className="w-3.5 h-3.5" /> {saving ? "Saving…" : "Save Changes"}
                </button>
              </div>
            </div>
          )}

          <div className="bg-white rounded-xl border border-[#E8EDF5] overflow-hidden">
            {loading ? (
              <div className="flex flex-col items-center justify-center py-16 space-y-3">
                <LoadingSpinner />
                <p className="text-sm text-gray-500">Loading users...</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-[#F8FAFD] border-b border-[#E8EDF5]">
                      {["User", "Username", "Role", "Department", "Last Login", "Status", canManage ? "Actions" : ""]
                        .filter(Boolean)
                        .map((h) => (
                          <th key={h} className="text-left px-4 py-3.5 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">
                            {h}
                          </th>
                        ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-50">
                    {users.length === 0 && (
                      <tr>
                        <td colSpan={7} className="px-5 py-10 text-center text-xs text-gray-400">
                          No users match this search.
                        </td>
                      </tr>
                    )}
                    {users.map((u) => (
                      <tr key={u.id} className={`transition-colors ${u.status === "Inactive" ? "opacity-60" : "hover:bg-[#F8FAFD]"}`}>
                        <td className="px-4 py-3.5">
                          <div className="flex items-center gap-2">
                            <div
                              className={`w-7 h-7 rounded-full flex items-center justify-center font-bold text-[10px] shrink-0 ${
                                u.status === "Inactive" ? "bg-gray-100 text-gray-400" : "bg-[#003087]/10 text-[#003087]"
                              }`}
                            >
                              {initials(u.name)}
                            </div>
                            <div>
                              <div className="text-xs font-semibold text-gray-800">{u.name}</div>
                              <div className="text-[10px] text-gray-400">{u.email}</div>
                            </div>
                          </div>
                        </td>
                        <td className="px-4 py-3.5 font-mono text-xs text-[#003087] font-medium">{u.username}</td>
                        <td className="px-4 py-3.5">
                          <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${roleColorClass(u.role)}`}>{u.role}</span>
                        </td>
                        <td className="px-4 py-3.5 text-xs text-gray-500">{u.department || "—"}</td>
                        <td className="px-4 py-3.5 text-[11px] text-gray-400 font-mono whitespace-nowrap">{formatDateTime(u.lastLogin)}</td>
                        <td className="px-4 py-3.5">
                          <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${statusClass(u.status)}`}>{u.status}</span>
                        </td>
                        {canManage && (
                          <td className="px-4 py-3.5">
                            <div className="flex items-center gap-1">
                              <button
                                onClick={() => openEdit(u)}
                                className="p-1.5 rounded hover:bg-[#003087]/10 text-[#003087] transition-colors"
                                title="Edit user"
                              >
                                <EditIcon className="w-3.5 h-3.5" />
                              </button>
                              <button
                                onClick={() => handleResetPassword(u)}
                                disabled={resetTargetId === u.id}
                                className="px-2 py-1 text-[10px] font-semibold text-[#003087] border border-[#003087]/25 rounded hover:bg-[#EBF1FB] transition-colors disabled:opacity-40"
                                title="Reset password"
                              >
                                {resetTargetId === u.id ? "…" : "Reset PW"}
                              </button>
                              {u.status === "Active" ? (
                                <button
                                  onClick={() => setConfirmUser(u)}
                                  className="px-2 py-1 text-[10px] font-semibold text-red-600 border border-red-200 rounded hover:bg-red-50 transition-colors"
                                >
                                  Deactivate
                                </button>
                              ) : (
                                <button
                                  onClick={() => setConfirmUser(u)}
                                  className="px-2 py-1 text-[10px] font-semibold text-green-600 border border-green-200 rounded hover:bg-green-50 transition-colors"
                                >
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
            )}
            <div className="flex items-center justify-between px-5 py-3.5 border-t border-gray-100">
              <span className="text-xs text-gray-400">
                Showing {users.length} of {total} users
              </span>
              <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
            </div>
          </div>

          {!canManage && (
            <div className="flex items-center gap-2 px-4 py-3 bg-[#F8FAFD] border border-[#DDE4EE] rounded-xl text-xs text-gray-400">
              <AlertIcon className="w-3.5 h-3.5 shrink-0" />
              User management actions (add, edit, deactivate) require Bank Admin access.
            </div>
          )}
        </div>

        <div className="bg-white rounded-xl border border-[#E8EDF5] p-5 h-fit">
          <div className="flex items-center gap-2 mb-4">
            <ShieldIcon className="w-4 h-4 text-[#003087]" />
            <h3 className="text-sm font-semibold text-[#1B2A4A]">Role Permissions</h3>
          </div>
          <div className="mb-3">
            <select
              value={selectedPermRole}
              onChange={(e) => setSelectedPermRole(e.target.value)}
              className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
            >
              {PERM_ROLE_IDS.map((id) => (
                <option key={id} value={id}>
                  {ROLE_ID_TO_DISPLAY[id]}
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-1.5">
            {[
              "dashboard",
              "schools",
              "transactions",
              "reconciliation",
              "epp",
              "reports",
              "notifications",
              "audit-logs",
              "users",
              "settings",
            ].map((segment) => {
              const has = selectedPermissions.includes(segment);
              return (
                <div key={segment} className={`flex items-center justify-between px-3 py-2 rounded-lg text-xs ${has ? "bg-green-50" : "bg-gray-50"}`}>
                  <span className={has ? "text-gray-700 font-medium" : "text-gray-400"}>{segmentLabel(segment)}</span>
                  {has ? <CheckIcon className="w-3.5 h-3.5 text-green-600 shrink-0" /> : <XIcon className="w-3.5 h-3.5 text-gray-300 shrink-0" />}
                </div>
              );
            })}
          </div>
        </div>
      </div>

      <Modal
        open={!!confirmUser}
        onClose={() => setConfirmUser(null)}
        title={confirmUser?.status === "Active" ? "Deactivate User" : "Reactivate User"}
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirmUser(null)} disabled={confirmLoading}>
              Cancel
            </Button>
            <Button
              variant={confirmUser?.status === "Active" ? "danger" : "primary"}
              onClick={handleToggleStatus}
              disabled={confirmLoading}
            >
              {confirmLoading ? "Working…" : confirmUser?.status === "Active" ? "Deactivate" : "Reactivate"}
            </Button>
          </>
        }
      >
        {confirmUser && (
          <>
            <p className="text-sm text-gray-500 mb-1">
              {confirmUser.status === "Active" ? (
                <>
                  Are you sure you want to deactivate <strong>{confirmUser.name}</strong>? They will immediately lose access to
                  the Bank Back Office.
                </>
              ) : (
                <>
                  Reactivate <strong>{confirmUser.name}</strong>? They will regain access according to their assigned role.
                </>
              )}
            </p>
            {confirmUser.status === "Active" && (
              <p className="text-xs text-amber-600 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 mt-3">
                Historical activity and audit records are retained.
              </p>
            )}
          </>
        )}
      </Modal>
    </div>
  );
}
