"use client";

import { useCallback, useEffect, useState } from "react";
import { useApiClient, useAuth, type PageResponse } from "@tuition/api-client";
import {
  Badge,
  Button,
  EmptyState,
  LoadingSpinner,
  Modal,
  Pagination,
  Table,
  UsersIcon,
  PlusIcon,
  ShieldIcon,
  SearchIcon,
  EditIcon,
  AlertIcon,
} from "@tuition/ui";
import type { Column } from "@tuition/ui";

import type {
  SchoolUserSummaryDto,
  CreateSchoolUserRequest,
  UpdateSchoolUserRequest,
  RolePermissionsDto,
  UserFilters,
} from "./types";

// ---------------------------------------------------------------------------
// Constants & Helpers
// ---------------------------------------------------------------------------

const PAGE_SIZE = 10;

const SCHOOL_ROLES = [
  { value: "School Admin", label: "School Admin" },
  { value: "School Finance", label: "School Finance" },
];

const DEFAULT_FILTERS: UserFilters = {
  search: "",
  role: "",
  status: "",
};

function formatDateTime(iso: string | null): string {
  if (!iso) return "Never";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return (
    d.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" }) +
    " " +
    d.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })
  );
}

function roleBadgeTone(role: string): "info" | "neutral" | "success" | "warning" | "danger" {
  if (role.toLowerCase().includes("admin")) return "info";
  return "neutral";
}

function statusBadgeTone(status: string): "success" | "danger" | "warning" | "info" | "neutral" {
  return status.toLowerCase() === "active" ? "success" : "danger";
}

function friendlyUserError(msg: string): string {
  const trimmed = msg.trim();
  if (trimmed === "email_exists" || trimmed.includes("email_exists")) {
    return "A user with this email address already exists.";
  }
  if (trimmed === "invalid_email" || trimmed.includes("invalid_email")) {
    return "Please enter a valid email address.";
  }
  if (trimmed === "invalid_role" || trimmed.includes("invalid_role")) {
    return "Invalid role specified. Allowed roles: School Admin, School Finance.";
  }
  return msg;
}

// ---------------------------------------------------------------------------
// Main Component
// ---------------------------------------------------------------------------

export default function SchoolUsersPage() {
  const apiClient = useApiClient();
  const { user } = useAuth();

  // Gate screen: only school-admin allowed
  const isSchoolAdmin =
    user?.role === "school-admin" ||
    user?.role === "School Admin" ||
    (user?.permissions ?? []).includes("users");

  // Users list state
  const [users, setUsers] = useState<SchoolUserSummaryDto[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [page, setPage] = useState(0); // 0-indexed
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Filters state
  const [filters, setFilters] = useState<UserFilters>(DEFAULT_FILTERS);
  const [pendingFilters, setPendingFilters] = useState<UserFilters>(DEFAULT_FILTERS);

  // Roles & Permissions matrix state
  const [rolesList, setRolesList] = useState<RolePermissionsDto[]>([]);
  const [showRolesMatrix, setShowRolesMatrix] = useState(false);

  // Create Modal state
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [createForm, setCreateForm] = useState<CreateSchoolUserRequest>({
    name: "",
    email: "",
    role: "School Finance",
  });
  const [createLoading, setCreateLoading] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  // Edit Modal state
  const [editingUser, setEditingUser] = useState<SchoolUserSummaryDto | null>(null);
  const [editForm, setEditForm] = useState<UpdateSchoolUserRequest>({});
  const [editLoading, setEditLoading] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  // Deactivate/Activate state
  const [deactivatingUser, setDeactivatingUser] = useState<SchoolUserSummaryDto | null>(null);
  const [statusLoading, setStatusLoading] = useState(false);

  // Refresh trigger
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  // -------------------------------------------------------------------------
  // Fetch Roles Matrix
  // -------------------------------------------------------------------------
  useEffect(() => {
    if (!isSchoolAdmin) return;
    apiClient
      .get<RolePermissionsDto[]>("/roles?type=school")
      .then((res) => {
        setRolesList(res ?? []);
      })
      .catch(() => {});
  }, [apiClient, isSchoolAdmin]);

  // -------------------------------------------------------------------------
  // Fetch Users List
  // -------------------------------------------------------------------------
  const fetchUsers = useCallback(() => {
    if (!isSchoolAdmin) return;
    setLoading(true);
    setError(null);

    const params = new URLSearchParams();
    if (filters.search) params.set("search", filters.search);
    if (filters.role) params.set("role", filters.role);
    if (filters.status) params.set("status", filters.status);
    params.set("page", String(page));
    params.set("pageSize", String(PAGE_SIZE));

    apiClient
      .get<PageResponse<SchoolUserSummaryDto>>(`/users?${params.toString()}`)
      .then((res) => {
        setUsers(res.data ?? []);
        setTotal(res.total ?? 0);
        setTotalPages(Math.max(1, res.totalPages ?? 1));
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : "Failed to load school users.");
      })
      .finally(() => {
        setLoading(false);
      });
  }, [apiClient, filters, page, isSchoolAdmin]);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers, refreshTrigger]);

  // -------------------------------------------------------------------------
  // Handlers for Create User
  // -------------------------------------------------------------------------
  const handleCreateUser = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!createForm.name.trim() || !createForm.email.trim()) {
      setCreateError("Please fill out all required fields.");
      return;
    }
    setCreateLoading(true);
    setCreateError(null);

    try {
      await apiClient.post("/users", createForm);
      setShowCreateModal(false);
      setCreateForm({ name: "", email: "", role: "School Finance" });
      setRefreshTrigger((p) => p + 1);
    } catch (err) {
      setCreateError(err instanceof Error ? friendlyUserError(err.message) : "Failed to create user.");
    } finally {
      setCreateLoading(false);
    }
  };

  // -------------------------------------------------------------------------
  // Handlers for Edit User
  // -------------------------------------------------------------------------
  const openEditModal = (u: SchoolUserSummaryDto) => {
    setEditingUser(u);
    setEditForm({ name: u.name, email: u.email, role: u.role });
    setEditError(null);
  };

  const handleUpdateUser = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingUser) return;
    setEditLoading(true);
    setEditError(null);

    try {
      await apiClient.patch(`/users/${editingUser.id}`, editForm);
      setEditingUser(null);
      setRefreshTrigger((p) => p + 1);
    } catch (err) {
      setEditError(err instanceof Error ? friendlyUserError(err.message) : "Failed to update user.");
    } finally {
      setEditLoading(false);
    }
  };

  // -------------------------------------------------------------------------
  // Handlers for Activate / Deactivate
  // -------------------------------------------------------------------------
  const handleDeactivate = async () => {
    if (!deactivatingUser) return;
    setStatusLoading(true);
    try {
      await apiClient.post(`/users/${deactivatingUser.id}/deactivate`, {});
      setDeactivatingUser(null);
      setRefreshTrigger((p) => p + 1);
    } catch (err) {
      alert(err instanceof Error ? err.message : "Deactivation failed.");
    } finally {
      setStatusLoading(false);
    }
  };

  const handleActivate = async (u: SchoolUserSummaryDto) => {
    setStatusLoading(true);
    try {
      await apiClient.post(`/users/${u.id}/activate`, {});
      setRefreshTrigger((p) => p + 1);
    } catch (err) {
      alert(err instanceof Error ? err.message : "Activation failed.");
    } finally {
      setStatusLoading(false);
    }
  };

  // -------------------------------------------------------------------------
  // Filter Handlers
  // -------------------------------------------------------------------------
  const applyFilters = () => {
    setFilters(pendingFilters);
    setPage(0);
  };

  const resetFilters = () => {
    setPendingFilters(DEFAULT_FILTERS);
    setFilters(DEFAULT_FILTERS);
    setPage(0);
  };

  // -------------------------------------------------------------------------
  // Access Gate Guard
  // -------------------------------------------------------------------------
  if (!isSchoolAdmin) {
    return (
      <div className="bg-white rounded-xl border border-red-200 p-12 text-center max-w-2xl mx-auto my-12 space-y-4">
        <div className="w-12 h-12 rounded-full bg-red-100 text-red-600 flex items-center justify-center mx-auto">
          <AlertIcon className="w-6 h-6" />
        </div>
        <h2 className="text-xl font-bold text-gray-900">Access Restricted</h2>
        <p className="text-sm text-gray-600">
          The School Users management module is restricted to logged-in users with the{" "}
          <span className="font-semibold text-gray-900">School Admin</span> role.
        </p>
      </div>
    );
  }

  // -------------------------------------------------------------------------
  // Table Columns
  // -------------------------------------------------------------------------
  const columns: Column<SchoolUserSummaryDto>[] = [
    {
      key: "name",
      header: "User Details",
      render: (row) => (
        <div>
          <p className="font-medium text-gray-900 text-sm">{row.name}</p>
          <p className="text-xs text-gray-500 font-mono">{row.email}</p>
        </div>
      ),
    },
    {
      key: "role",
      header: "Role",
      render: (row) => <Badge tone={roleBadgeTone(row.role)}>{row.role}</Badge>,
    },
    {
      key: "status",
      header: "Status",
      render: (row) => <Badge tone={statusBadgeTone(row.status)}>{row.status}</Badge>,
    },
    {
      key: "lastLogin",
      header: "Last Login",
      render: (row) => (
        <span className="text-xs text-gray-600">{formatDateTime(row.lastLogin)}</span>
      ),
    },
    {
      key: "actions",
      header: "Actions",
      render: (row) => (
        <div className="flex items-center gap-2">
          <button
            onClick={() => openEditModal(row)}
            className="p-1.5 rounded text-gray-500 hover:text-[#003087] hover:bg-gray-100 transition-colors"
            title="Edit User"
          >
            <EditIcon className="w-4 h-4" />
          </button>

          {row.status.toLowerCase() === "active" ? (
            <button
              onClick={() => setDeactivatingUser(row)}
              disabled={statusLoading}
              className="text-xs font-semibold text-red-600 hover:text-red-700 hover:underline px-2 py-1"
            >
              Deactivate
            </button>
          ) : (
            <button
              onClick={() => handleActivate(row)}
              disabled={statusLoading}
              className="text-xs font-semibold text-green-700 hover:text-green-800 hover:underline px-2 py-1"
            >
              Activate
            </button>
          )}
        </div>
      ),
    },
  ];

  // -------------------------------------------------------------------------
  // Render Main Screen
  // -------------------------------------------------------------------------
  return (
    <div className="space-y-6 pb-12">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">School Users</h1>
          <p className="mt-1 text-sm text-gray-500">
            Manage staff access accounts and roles for your school.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setShowRolesMatrix(!showRolesMatrix)}
            className="flex items-center gap-1.5"
          >
            <ShieldIcon className="w-4 h-4 text-gray-600" />
            {showRolesMatrix ? "Hide Permissions Matrix" : "View Role Permissions"}
          </Button>

          <Button
            variant="primary"
            size="sm"
            onClick={() => {
              setCreateForm({ name: "", email: "", role: "School Finance" });
              setCreateError(null);
              setShowCreateModal(true);
            }}
            className="flex items-center gap-1.5 bg-[#003087] text-white hover:bg-[#00256b]"
          >
            <PlusIcon className="w-4 h-4" />
            Add School User
          </Button>
        </div>
      </div>

      {/* Role & Permissions Matrix Collapsible */}
      {showRolesMatrix && (
        <div className="bg-white rounded-xl border border-blue-200 p-5 space-y-4">
          <div className="flex items-center gap-2">
            <ShieldIcon className="w-5 h-5 text-[#003087]" />
            <h3 className="text-sm font-bold text-gray-900">School Role Permission Matrix</h3>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {rolesList.map((r) => (
              <div key={r.role} className="bg-blue-50/50 rounded-lg border border-blue-100 p-4 space-y-2">
                <div className="flex items-center justify-between">
                  <span className="font-bold text-sm text-[#003087]">{r.role}</span>
                  <Badge tone={roleBadgeTone(r.role)}>{r.permissions.length} permissions</Badge>
                </div>
                <div className="flex flex-wrap gap-1.5 pt-1">
                  {r.permissions.map((perm) => (
                    <span
                      key={perm}
                      className="px-2 py-0.5 rounded text-[11px] font-medium bg-white text-gray-700 border border-blue-200"
                    >
                      {perm}
                    </span>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Filter Bar */}
      <div className="bg-white rounded-xl border border-gray-200 p-4 space-y-3">
        <div className="flex flex-wrap gap-3 items-center">
          {/* Search */}
          <div className="relative flex-1 min-w-[200px]">
            <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400 pointer-events-none" />
            <input
              type="text"
              placeholder="Search by name or email…"
              value={pendingFilters.search}
              onChange={(e) => setPendingFilters({ ...pendingFilters, search: e.target.value })}
              onKeyDown={(e) => e.key === "Enter" && applyFilters()}
              className="w-full pl-9 pr-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#003087]"
            />
          </div>

          {/* Role Filter */}
          <select
            value={pendingFilters.role}
            onChange={(e) => setPendingFilters({ ...pendingFilters, role: e.target.value })}
            className="px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#003087] bg-white"
          >
            <option value="">All Roles</option>
            {SCHOOL_ROLES.map((r) => (
              <option key={r.value} value={r.value}>
                {r.label}
              </option>
            ))}
          </select>

          {/* Status Filter */}
          <select
            value={pendingFilters.status}
            onChange={(e) => setPendingFilters({ ...pendingFilters, status: e.target.value })}
            className="px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#003087] bg-white"
          >
            <option value="">All Statuses</option>
            <option value="Active">Active</option>
            <option value="Inactive">Inactive</option>
          </select>

          <div className="flex gap-2 ml-auto">
            <Button variant="secondary" size="sm" onClick={resetFilters}>
              Reset
            </Button>
            <Button size="sm" onClick={applyFilters}>
              Apply Filters
            </Button>
          </div>
        </div>
      </div>

      {/* Summary Count */}
      {!loading && !error && (
        <p className="text-sm text-gray-500">
          Showing <span className="font-medium text-gray-800">{users.length}</span> of{" "}
          <span className="font-medium text-gray-800">{total}</span> school user{total !== 1 ? "s" : ""}
        </p>
      )}

      {/* Users Table */}
      {loading ? (
        <div className="flex justify-center py-16">
          <LoadingSpinner size={32} />
        </div>
      ) : error ? (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 text-sm text-red-700 flex items-center justify-between">
          <span>{error}</span>
          <Button variant="secondary" size="sm" onClick={fetchUsers}>
            Retry
          </Button>
        </div>
      ) : users.length === 0 ? (
        <EmptyState
          title="No school users found"
          description="Try clearing your search or filter options."
        />
      ) : (
        <>
          <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
            <Table<SchoolUserSummaryDto>
              columns={columns}
              rows={users}
              rowKey={(row) => row.id}
            />
          </div>

          {totalPages > 1 && (
            <div className="flex justify-center pt-2">
              <Pagination
                page={page + 1}
                totalPages={totalPages}
                onPageChange={(p) => setPage(p - 1)}
              />
            </div>
          )}
        </>
      )}

      {/* ------------------------------------------------------------------ */}
      {/* Create User Modal                                                   */}
      {/* ------------------------------------------------------------------ */}
      <Modal
        open={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        title="Add New School User"
        footer={
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setShowCreateModal(false)}>
              Cancel
            </Button>
            <Button variant="primary" onClick={handleCreateUser} disabled={createLoading} className="bg-[#003087] text-white hover:bg-[#00256b]">
              {createLoading ? "Creating…" : "Create User"}
            </Button>
          </div>
        }
      >
        <form onSubmit={handleCreateUser} className="space-y-4">
          {createError && (
            <div className="bg-red-50 border border-red-200 rounded-lg p-3 text-xs text-red-700">
              {createError}
            </div>
          )}

          <div>
            <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wide mb-1">
              Full Name *
            </label>
            <input
              type="text"
              required
              placeholder="e.g. Dina Fouad"
              value={createForm.name}
              onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })}
              className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#003087]"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wide mb-1">
              Email Address *
            </label>
            <input
              type="email"
              required
              placeholder="e.g. d.fouad@school.edu.eg"
              value={createForm.email}
              onChange={(e) => setCreateForm({ ...createForm, email: e.target.value })}
              className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#003087]"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wide mb-1">
              User Role *
            </label>
            <select
              value={createForm.role}
              onChange={(e) => setCreateForm({ ...createForm, role: e.target.value })}
              className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#003087] bg-white"
            >
              {SCHOOL_ROLES.map((r) => (
                <option key={r.value} value={r.value}>
                  {r.label}
                </option>
              ))}
            </select>
          </div>
        </form>
      </Modal>

      {/* ------------------------------------------------------------------ */}
      {/* Edit User Modal                                                     */}
      {/* ------------------------------------------------------------------ */}
      <Modal
        open={!!editingUser}
        onClose={() => setEditingUser(null)}
        title="Edit School User"
        footer={
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setEditingUser(null)}>
              Cancel
            </Button>
            <Button variant="primary" onClick={handleUpdateUser} disabled={editLoading}>
              {editLoading ? "Saving…" : "Save Changes"}
            </Button>
          </div>
        }
      >
        <form onSubmit={handleUpdateUser} className="space-y-4">
          {editError && (
            <div className="bg-red-50 border border-red-200 rounded-lg p-3 text-xs text-red-700">
              {editError}
            </div>
          )}

          <div>
            <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wide mb-1">
              Full Name
            </label>
            <input
              type="text"
              value={editForm.name ?? ""}
              onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
              className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#003087]"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wide mb-1">
              Email Address
            </label>
            <input
              type="email"
              value={editForm.email ?? ""}
              onChange={(e) => setEditForm({ ...editForm, email: e.target.value })}
              className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#003087]"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wide mb-1">
              Role
            </label>
            <select
              value={editForm.role ?? "School Finance"}
              onChange={(e) => setEditForm({ ...editForm, role: e.target.value })}
              className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#003087] bg-white"
            >
              {SCHOOL_ROLES.map((r) => (
                <option key={r.value} value={r.value}>
                  {r.label}
                </option>
              ))}
            </select>
          </div>
        </form>
      </Modal>

      {/* ------------------------------------------------------------------ */}
      {/* Deactivate User Confirmation Modal                                 */}
      {/* ------------------------------------------------------------------ */}
      <Modal
        open={!!deactivatingUser}
        onClose={() => setDeactivatingUser(null)}
        title="Confirm Deactivation"
        footer={
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => setDeactivatingUser(null)}>
              Cancel
            </Button>
            <Button variant="danger" onClick={handleDeactivate} disabled={statusLoading}>
              {statusLoading ? "Deactivating…" : "Deactivate User"}
            </Button>
          </div>
        }
      >
        <div className="space-y-3">
          <p className="text-sm text-gray-700">
            Are you sure you want to deactivate{" "}
            <span className="font-semibold text-gray-900">{deactivatingUser?.name}</span> (
            <span className="font-mono text-xs">{deactivatingUser?.email}</span>)?
          </p>
          <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 text-xs text-amber-800">
            Deactivating this user will revoke their portal access immediately. Historical activity
            logs will be retained.
          </div>
        </div>
      </Modal>
    </div>
  );
}

