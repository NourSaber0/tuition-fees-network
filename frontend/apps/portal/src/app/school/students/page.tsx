"use client";

import { useEffect, useState, useCallback, useMemo, useId } from "react";
import { useApiClient, useAuth, type PageResponse } from "@tuition/api-client";
import {
  Pagination,
  LoadingSpinner,
  SearchIcon,
  PlusIcon,
  RefreshIcon,
  EyeIcon,
  EditIcon,
  AlertIcon,
  CheckCircleIcon,
  XCircleIcon,
} from "@tuition/ui";
import type {
  StudentSummary,
  StudentDetail,
  StudentFeesResponse,
  StudentPaymentsResponse,
  StudentGuardian,
  StudentStatementResponse,
  EnrollStudentRequest,
  UpdateStudentRequest,
} from "./types";

function money(amount?: number | null): string {
  if (amount == null || isNaN(amount)) return "0 EGP";
  return `${Math.round(amount).toLocaleString()} EGP`;
}

const GRADES = [
  "All Grades",
  "Grade 1",
  "Grade 2",
  "Grade 3",
  "Grade 4",
  "Grade 5",
  "Grade 6",
  "Grade 7",
  "Grade 8",
  "Grade 9",
  "Grade 10",
  "Grade 11",
  "Grade 12",
  "Kindergarten",
];

const DEACTIVATE_REASONS = [
  "Withdrawn by Guardian",
  "Transferred to Another School",
  "Graduated",
  "Non-Payment / Financial Discontinuation",
  "Expelled / Disciplinary",
  "Other",
];

const AVATAR_BG_COLORS = [
  "bg-[#16335C]", // CIB Navy (Ahmed Hassan)
  "bg-emerald-600", // Green (Sara Mohamed)
  "bg-purple-600", // Purple (Omar Ali)
  "bg-rose-600", // Crimson/Rose (Nadia Saleh)
  "bg-teal-600", // Teal
  "bg-amber-600", // Amber
];

function getInitials(name: string): string {
  if (!name) return "??";
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

function getAvatarColor(id: string): string {
  let hash = 0;
  for (let i = 0; i < id.length; i++) {
    hash = (hash << 5) - hash + id.charCodeAt(i);
  }
  return AVATAR_BG_COLORS[Math.abs(hash) % AVATAR_BG_COLORS.length];
}

export default function StudentsPage() {
  const apiClient = useApiClient();
  const { user } = useAuth();
  const isAdmin =
    user?.role === "school-admin" ||
    user?.role === "ROLE_SCHOOL_ADMIN" ||
    user?.role === "school-finance" ||
    user?.role === "ROLE_SCHOOL_FINANCE";

  // Tab state: "active" | "deactivated"
  const [activeTab, setActiveTab] = useState<"active" | "deactivated">("active");

  // Active roster query state
  const [activeSearch, setActiveSearch] = useState("");
  const [selectedGrade, setSelectedGrade] = useState("All Grades");
  const [activePage, setActivePage] = useState(1);
  const [activeData, setActiveData] = useState<PageResponse<StudentSummary> | null>(null);
  const [activeLoading, setActiveLoading] = useState(true);

  // Dynamic grade options that include default GRADES plus any grades in roster
  const availableGrades = useMemo(() => {
    const list = [...GRADES];
    if (activeData?.data) {
      activeData.data.forEach((s) => {
        if (s.grade && !list.includes(s.grade)) {
          list.push(s.grade);
        }
      });
    }
    return list;
  }, [activeData]);

  // Deactivated query state
  const [deactSearch, setDeactSearch] = useState("");
  const [deactFrom, setDeactFrom] = useState("");
  const [deactTo, setDeactTo] = useState("");
  const [deactPage, setDeactPage] = useState(1);
  const [deactData, setDeactData] = useState<PageResponse<StudentSummary> | null>(null);
  const [deactLoading, setDeactLoading] = useState(false);

  // Banner states
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successToast, setSuccessToast] = useState<string | null>(null);

  // Drawer / Detail View
  const [selectedStudentId, setSelectedStudentId] = useState<string | null>(null);
  const [studentDetail, setStudentDetail] = useState<StudentDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailTab, setDetailTab] = useState<"fees" | "payments" | "guardians" | "statement">("fees");

  // Detail sub-tab data
  const [feesData, setFeesData] = useState<StudentFeesResponse | null>(null);
  const [paymentsData, setPaymentsData] = useState<StudentPaymentsResponse | null>(null);
  const [guardiansData, setGuardiansData] = useState<StudentGuardian[] | null>(null);
  const [statementData, setStatementData] = useState<StudentStatementResponse | null>(null);
  const [subTabLoading, setSubTabLoading] = useState(false);

  // Modals state
  const [isEnrollModalOpen, setIsEnrollModalOpen] = useState(false);
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [isDeactivateModalOpen, setIsDeactivateModalOpen] = useState(false);
  const [isReactivateModalOpen, setIsReactivateModalOpen] = useState(false);
  const [isLinkGuardianModalOpen, setIsLinkGuardianModalOpen] = useState(false);
  const [guardianToUnlink, setGuardianToUnlink] = useState<StudentGuardian | null>(null);

  // Form states matching Screenshot 1 exactly
  const [enrollForm, setEnrollForm] = useState({
    name: "",
    grade: "",
    section: "",
    nationalId: "",
    parentName: "",
    parentPhone: "",
    parentEmail: "",
  });
  const [editForm, setEditForm] = useState<UpdateStudentRequest>({});
  const [deactivateReason, setDeactivateReason] = useState(DEACTIVATE_REASONS[0]);
  const [customDeactivateReason, setCustomDeactivateReason] = useState("");
  const [linkGuardianForm, setLinkGuardianForm] = useState({
    name: "",
    phone: "",
    email: "",
    relationship: "Father",
    primaryGuardian: false,
  });
  const [actionSubmitting, setActionSubmitting] = useState(false);

  // Form input IDs for label bindings
  const enrollNameId = useId();
  const enrollGradeId = useId();
  const enrollSectionId = useId();
  const enrollNidId = useId();
  const enrollParentNameId = useId();
  const enrollParentPhoneId = useId();
  const enrollParentEmailId = useId();

  const editStudentRefId = useId();
  const editNameId = useId();
  const editGradeId = useId();
  const editSectionId = useId();
  const editParentNameId = useId();
  const editParentPhoneId = useId();
  const editParentEmailId = useId();

  const deactReasonSelectId = useId();
  const deactCustomReasonId = useId();

  const triggerToast = (msg: string) => {
    setSuccessToast(msg);
    setTimeout(() => setSuccessToast(null), 4000);
  };

  // Fetch active roster
  const fetchActiveStudents = useCallback(async () => {
    setActiveLoading(true);
    setErrorMessage(null);
    try {
      const params = new URLSearchParams();
      params.set("page", String(activePage));
      params.set("pageSize", "10");
      if (activeSearch.trim()) params.set("search", activeSearch.trim());
      if (selectedGrade !== "All Grades") params.set("grade", selectedGrade);

      const res = await apiClient.get<PageResponse<StudentSummary>>(`/students?${params.toString()}`);
      setActiveData(res);
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : "Failed to load active students");
    } finally {
      setActiveLoading(false);
    }
  }, [apiClient, activePage, activeSearch, selectedGrade]);

  // Fetch deactivated students
  const fetchDeactivatedStudents = useCallback(async () => {
    setDeactLoading(true);
    setErrorMessage(null);
    try {
      const params = new URLSearchParams();
      params.set("page", String(deactPage));
      params.set("pageSize", "10");
      if (deactSearch.trim()) params.set("search", deactSearch.trim());
      if (deactFrom) params.set("deactivatedFrom", deactFrom);
      if (deactTo) params.set("deactivatedTo", deactTo);

      const res = await apiClient.get<PageResponse<StudentSummary>>(`/students/deactivated?${params.toString()}`);
      setDeactData(res);
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : "Failed to load deactivated students");
    } finally {
      setDeactLoading(false);
    }
  }, [apiClient, deactPage, deactSearch, deactFrom, deactTo]);

  useEffect(() => {
    if (activeTab === "active") {
      fetchActiveStudents();
    } else {
      fetchDeactivatedStudents();
    }
  }, [activeTab, fetchActiveStudents, fetchDeactivatedStudents]);

  // Fetch single student detail
  const loadStudentDetail = useCallback(async (studentId: string) => {
    setSelectedStudentId(studentId);
    setDetailLoading(true);
    setErrorMessage(null);
    try {
      const detail = await apiClient.get<StudentDetail>(`/students/${studentId}`);
      setStudentDetail(detail);
      setDetailTab("fees");
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : "Failed to load student details");
    } finally {
      setDetailLoading(false);
    }
  }, [apiClient]);

  // Load sub-tab detail data
  useEffect(() => {
    if (!selectedStudentId) return;

    setSubTabLoading(true);
    if (detailTab === "fees") {
      apiClient
        .get<StudentFeesResponse>(`/students/${selectedStudentId}/fees`)
        .then(setFeesData)
        .catch((err) => setErrorMessage(err instanceof Error ? err.message : "Failed to load fees"))
        .finally(() => setSubTabLoading(false));
    } else if (detailTab === "payments") {
      apiClient
        .get<StudentPaymentsResponse>(`/students/${selectedStudentId}/payments`)
        .then(setPaymentsData)
        .catch((err) => setErrorMessage(err instanceof Error ? err.message : "Failed to load payments"))
        .finally(() => setSubTabLoading(false));
    } else if (detailTab === "guardians") {
      apiClient
        .get<StudentGuardian[]>(`/students/${selectedStudentId}/guardians`)
        .then(setGuardiansData)
        .catch((err) => setErrorMessage(err instanceof Error ? err.message : "Failed to load guardians"))
        .finally(() => setSubTabLoading(false));
    } else if (detailTab === "statement") {
      apiClient
        .get<StudentStatementResponse>(`/students/${selectedStudentId}/statement`)
        .then(setStatementData)
        .catch((err) => setErrorMessage(err instanceof Error ? err.message : "Failed to load statement"))
        .finally(() => setSubTabLoading(false));
    }
  }, [selectedStudentId, detailTab, apiClient]);

  // Handle Add Student (matches Screenshot 1 fields)
  const handleEnrollStudent = async (e: React.FormEvent) => {
    e.preventDefault();
    setActionSubmitting(true);
    setErrorMessage(null);

    // Auto-generate unique student reference (e.g. STU-8421) if not provided
    const randomSuffix = Math.floor(1000 + Math.random() * 9000);
    const nextRef = `STU-${randomSuffix}`;

    // Normalize grade: if entered "12", format as "Grade 12"
    let formattedGrade = enrollForm.grade.trim() || "Grade 10";
    if (/^\d+$/.test(formattedGrade)) {
      formattedGrade = `Grade ${formattedGrade}`;
    }

    const payload: EnrollStudentRequest = {
      studentRef: nextRef,
      name: enrollForm.name.trim(),
      grade: formattedGrade,
      section: enrollForm.section.trim() || "A",
      nationalId: enrollForm.nationalId.trim() || undefined,
      parentName: enrollForm.parentName.trim() || undefined,
      parentPhone: enrollForm.parentPhone.trim() || undefined,
      parentEmail: enrollForm.parentEmail.trim() || undefined,
    };

    try {
      await apiClient.post("/students", payload);
      setIsEnrollModalOpen(false);
      setEnrollForm({
        name: "",
        grade: "",
        section: "",
        nationalId: "",
        parentName: "",
        parentPhone: "",
        parentEmail: "",
      });
      triggerToast(`Student "${payload.name}" (${nextRef}) enrolled successfully!`);
      setActivePage(1);
      fetchActiveStudents();
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : "Failed to enroll student");
    } finally {
      setActionSubmitting(false);
    }
  };

  // Handle Update Student
  const handleUpdateStudent = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedStudentId) return;
    setActionSubmitting(true);
    setErrorMessage(null);
    try {
      const updated = await apiClient.patch<StudentDetail>(`/students/${selectedStudentId}`, editForm);
      setStudentDetail(updated);
      setIsEditModalOpen(false);
      triggerToast("Student details updated successfully!");
      if (activeTab === "active") fetchActiveStudents();
      else fetchDeactivatedStudents();
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : "Failed to update student");
    } finally {
      setActionSubmitting(false);
    }
  };

  // Handle Deactivate Student
  const handleDeactivateStudent = async () => {
    if (!selectedStudentId) return;
    setActionSubmitting(true);
    setErrorMessage(null);
    const reason = deactivateReason === "Other" ? customDeactivateReason : deactivateReason;
    try {
      await apiClient.post(`/students/${selectedStudentId}/deactivate`, { reason });
      setIsDeactivateModalOpen(false);
      setSelectedStudentId(null);
      setStudentDetail(null);
      triggerToast("Student moved to Deactivated Archive.");
      fetchActiveStudents();
      if (activeTab === "deactivated") fetchDeactivatedStudents();
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : "Failed to deactivate student");
    } finally {
      setActionSubmitting(false);
    }
  };

  // Handle Reactivate Student
  const handleReactivateStudent = async () => {
    if (!selectedStudentId) return;
    setActionSubmitting(true);
    setErrorMessage(null);
    try {
      await apiClient.post(`/students/${selectedStudentId}/reactivate`);
      setIsReactivateModalOpen(false);
      setSelectedStudentId(null);
      setStudentDetail(null);
      triggerToast("Student reactivated successfully!");
      fetchDeactivatedStudents();
      fetchActiveStudents();
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : "Failed to reactivate student");
    } finally {
      setActionSubmitting(false);
    }
  };

  return (
    <div className="space-y-6 pb-12 max-w-7xl">
      {/* Toast Banner */}
      {successToast && (
        <div className="flex items-center gap-2 rounded-lg bg-emerald-50 border border-emerald-200 px-4 py-3 text-sm text-emerald-800 shadow-sm animate-in fade-in duration-200">
          <CheckCircleIcon className="w-4 h-4 text-emerald-600 flex-shrink-0" />
          <span>{successToast}</span>
        </div>
      )}

      {/* Error Banner */}
      {errorMessage && (
        <div className="flex items-center justify-between gap-3 rounded-lg bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-800 shadow-sm">
          <div className="flex items-center gap-2">
            <XCircleIcon className="w-4 h-4 text-red-600 flex-shrink-0" />
            <span>{errorMessage}</span>
          </div>
          <button
            onClick={() => setErrorMessage(null)}
            className="text-xs text-red-600 hover:text-red-900 font-semibold"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* Page Title (Screenshot 2 style: Orange rounded icon + "Students") */}
      <div className="flex items-center gap-2.5">
        <div className="w-7 h-7 rounded-lg bg-[#F7941D] flex items-center justify-center text-white shadow-xs">
          <svg className="w-4 h-4" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 3L1 9l11 6 9-4.91V17h2V9L12 3z M5 13.18v4L12 21l7-3.82v-4L12 17l-7-3.82z" />
          </svg>
        </div>
        <h1 className="text-xl font-bold text-[#F7941D] tracking-tight">Students</h1>
      </div>

      {/* Tab Pills (Screenshot 2: "Active Students [10]" / "Deactivated [1]") */}
      <div className="inline-flex items-center gap-1.5 p-1 rounded-xl bg-gray-100/80 border border-gray-200/50">
        <button
          type="button"
          onClick={() => {
            setActiveTab("active");
            setActivePage(1);
          }}
          className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all ${
            activeTab === "active"
              ? "bg-white text-[#1B2A4A] shadow-xs"
              : "text-gray-500 hover:text-gray-800"
          }`}
        >
          <span>Active Students</span>
          <span
            className={`px-2 py-0.5 rounded-full text-[11px] font-bold ${
              activeTab === "active" ? "bg-[#16335C] text-white" : "bg-gray-200 text-gray-700"
            }`}
          >
            {activeData?.total ?? 0}
          </span>
        </button>

        <button
          type="button"
          onClick={() => {
            setActiveTab("deactivated");
            setDeactPage(1);
          }}
          className={`flex items-center gap-2 px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all ${
            activeTab === "deactivated"
              ? "bg-white text-[#1B2A4A] shadow-xs"
              : "text-gray-500 hover:text-gray-800"
          }`}
        >
          <span>Deactivated</span>
          <span
            className={`px-2 py-0.5 rounded-full text-[11px] font-bold ${
              activeTab === "deactivated" ? "bg-[#16335C] text-white" : "bg-gray-200 text-gray-700"
            }`}
          >
            {deactData?.total ?? 0}
          </span>
        </button>
      </div>

      {/* Active Students View */}
      {activeTab === "active" && (
        <div className="space-y-4">
          {/* Filter Bar (Screenshot 2: Search input + Count + Grade Dropdown + "+ Add Student" Button) */}
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
            {/* Search input with icon */}
            <div className="relative flex-1 max-w-sm">
              <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
              <input
                type="text"
                placeholder="Search students..."
                value={activeSearch}
                onChange={(e) => {
                  setActiveSearch(e.target.value);
                  setActivePage(1);
                }}
                className="w-full pl-9 pr-3 py-2 text-xs rounded-lg border border-gray-200 bg-white text-gray-800 placeholder-gray-400 shadow-2xs focus:outline-none focus:border-[#16335C]"
              />
            </div>

            {/* Right Controls: Count + Grade dropdown + Blue "+ Add Student" button */}
            <div className="flex items-center gap-3 justify-end flex-wrap">
              <span className="text-xs text-gray-500 font-medium whitespace-nowrap">
                {activeData?.total ?? 0} students
              </span>

              <select
                aria-label="Filter by Grade"
                value={selectedGrade}
                onChange={(e) => {
                  setSelectedGrade(e.target.value);
                  setActivePage(1);
                }}
                className="py-2 px-3 text-xs rounded-lg border border-gray-200 bg-white text-gray-700 font-medium shadow-2xs focus:outline-none focus:border-[#16335C]"
              >
                {availableGrades.map((g) => (
                  <option key={g} value={g}>
                    {g}
                  </option>
                ))}
              </select>

              {/* Blue "+ Add Student" Button (Exact match from Screenshot 2) */}
              <button
                type="button"
                onClick={() => setIsEnrollModalOpen(true)}
                className="inline-flex items-center gap-1.5 bg-[#16335C] hover:bg-[#102544] text-white text-xs font-semibold px-4 py-2 rounded-lg shadow-sm transition-colors whitespace-nowrap"
              >
                <PlusIcon className="w-3.5 h-3.5" />
                <span>Add Student</span>
              </button>
            </div>
          </div>

          {/* Table (Screenshot 2: STUDENT ID, NAME, GRADE, TOTAL FEES, PAID, OUTSTANDING) */}
          <div className="rounded-xl border border-gray-200 bg-white shadow-xs overflow-hidden">
            {activeLoading ? (
              <div className="flex justify-center py-16">
                <LoadingSpinner />
              </div>
            ) : !activeData || activeData.data.length === 0 ? (
              <div className="py-16 text-center text-sm text-gray-500">
                No active students match your search criteria.
              </div>
            ) : (
              <table className="w-full text-left text-xs">
                <thead className="bg-[#FAFBFD] border-b border-gray-200 text-gray-500 font-semibold uppercase tracking-wider text-[11px]">
                  <tr>
                    <th className="px-5 py-3 font-semibold">STUDENT ID</th>
                    <th className="px-5 py-3 font-semibold">NAME</th>
                    <th className="px-5 py-3 font-semibold">GRADE</th>
                    <th className="px-5 py-3 font-semibold">TOTAL FEES</th>
                    <th className="px-5 py-3 font-semibold">PAID</th>
                    <th className="px-5 py-3 font-semibold">OUTSTANDING</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {activeData.data.map((row) => (
                    <tr
                      key={row.id}
                      onClick={() => loadStudentDetail(row.id)}
                      className="hover:bg-slate-50/70 transition-colors cursor-pointer"
                    >
                      {/* Student ID */}
                      <td className="px-5 py-3.5 font-mono text-gray-700 font-medium text-xs">
                        {row.studentRef || `STU-${row.id.substring(0, 4)}`}
                      </td>

                      {/* Name with circle avatar initials */}
                      <td className="px-5 py-3.5">
                        <div className="flex items-center gap-2.5">
                          <div
                            className={`w-7 h-7 rounded-full flex items-center justify-center text-white text-[11px] font-bold shrink-0 ${getAvatarColor(
                              row.id
                            )}`}
                          >
                            {getInitials(row.name)}
                          </div>
                          <div>
                            <div className="font-semibold text-gray-900 text-xs">{row.name}</div>
                            {row.section && (
                              <div className="text-[10px] text-gray-400">Section {row.section}</div>
                            )}
                          </div>
                        </div>
                      </td>

                      {/* Grade */}
                      <td className="px-5 py-3.5 text-gray-700 font-medium">
                        {row.grade?.startsWith("Grade ") ? (
                          <div className="leading-tight">
                            <span className="text-gray-500 text-xs">Grade</span>
                            <div className="font-medium text-gray-900 text-xs">{row.grade.replace("Grade ", "")}</div>
                          </div>
                        ) : (
                          row.grade
                        )}
                      </td>

                      {/* Total Fees */}
                      <td className="px-5 py-3.5 leading-tight">
                        <div className="font-bold text-gray-900 text-xs">
                          {Math.round(row.totalFeesEGP).toLocaleString()}
                        </div>
                        <div className="font-bold text-gray-900 text-xs">EGP</div>
                      </td>

                      {/* Paid (Green) */}
                      <td className="px-5 py-3.5 leading-tight">
                        <div className="font-bold text-emerald-600 text-xs">
                          {Math.round(row.paidEGP).toLocaleString()}
                        </div>
                        <div className="font-bold text-emerald-600 text-xs">EGP</div>
                      </td>

                      {/* Outstanding (Orange if > 0, Green if 0) */}
                      <td
                        className={`px-5 py-3.5 font-bold text-xs ${
                          row.outstandingEGP > 0 ? "text-amber-600" : "text-emerald-600"
                        }`}
                      >
                        {Math.round(row.outstandingEGP).toLocaleString()} EGP
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          {/* Pagination */}
          {activeData && activeData.totalPages > 1 && (
            <Pagination
              page={activeData.page}
              totalPages={activeData.totalPages}
              onPageChange={(p) => setActivePage(p)}
            />
          )}
        </div>
      )}

      {/* Deactivated Archive View */}
      {activeTab === "deactivated" && (
        <div className="space-y-4">
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
            <div className="relative flex-1 max-w-sm">
              <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
              <input
                type="text"
                placeholder="Search deactivated archive..."
                value={deactSearch}
                onChange={(e) => {
                  setDeactSearch(e.target.value);
                  setDeactPage(1);
                }}
                className="w-full pl-9 pr-3 py-2 text-xs rounded-lg border border-gray-200 bg-white text-gray-800 placeholder-gray-400 shadow-2xs focus:outline-none focus:border-[#0B2545]"
              />
            </div>
            <div className="flex items-center gap-2 text-xs text-gray-500">
              <span>Date:</span>
              <input
                type="date"
                value={deactFrom}
                onChange={(e) => setDeactFrom(e.target.value)}
                className="border border-gray-200 rounded px-2 py-1 bg-white"
              />
              <span>to</span>
              <input
                type="date"
                value={deactTo}
                onChange={(e) => setDeactTo(e.target.value)}
                className="border border-gray-200 rounded px-2 py-1 bg-white"
              />
            </div>
          </div>

          <div className="rounded-xl border border-gray-200 bg-white shadow-xs overflow-hidden">
            {deactLoading ? (
              <div className="flex justify-center py-16">
                <LoadingSpinner />
              </div>
            ) : !deactData || deactData.data.length === 0 ? (
              <div className="py-16 text-center text-sm text-gray-500">
                No deactivated students found in the archive.
              </div>
            ) : (
              <table className="w-full text-left text-xs">
                <thead className="bg-[#FAFBFD] border-b border-gray-200 text-gray-500 font-semibold uppercase tracking-wider text-[11px]">
                  <tr>
                    <th className="px-5 py-3 font-semibold">STUDENT ID</th>
                    <th className="px-5 py-3 font-semibold">NAME</th>
                    <th className="px-5 py-3 font-semibold">GRADE</th>
                    <th className="px-5 py-3 font-semibold">DEACTIVATED DATE</th>
                    <th className="px-5 py-3 font-semibold">REASON</th>
                    <th className="px-5 py-3 font-semibold text-right">ACTION</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {deactData.data.map((row) => (
                    <tr
                      key={row.id}
                      onClick={() => loadStudentDetail(row.id)}
                      className="hover:bg-slate-50/70 transition-colors cursor-pointer"
                    >
                      <td className="px-5 py-3.5 font-mono text-gray-500 font-medium">
                        {row.studentRef || `STU-${row.id.substring(0, 4)}`}
                      </td>
                      <td className="px-5 py-3.5 font-semibold text-gray-900">{row.name}</td>
                      <td className="px-5 py-3.5 text-gray-700">{row.grade}</td>
                      <td className="px-5 py-3.5 text-gray-500">{row.deactivatedDate ?? "—"}</td>
                      <td className="px-5 py-3.5 text-gray-500 italic">{row.deactivationReason ?? "Withdrawn"}</td>
                      <td className="px-5 py-3.5 text-right" onClick={(e) => e.stopPropagation()}>
                        {isAdmin && (
                          <button
                            type="button"
                            onClick={() => {
                              setSelectedStudentId(row.id);
                              setIsReactivateModalOpen(true);
                            }}
                            className="px-3 py-1 bg-[#0B2545] text-white rounded text-xs font-medium hover:bg-[#071a33]"
                          >
                            Reactivate
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>
      )}

      {/* =========================================================================
          MODAL 1: ADD STUDENT (Exact Match of Screenshot 1)
          Two-column grid: NAME, GRADE, SECTION, NATIONAL ID, PARENT NAME, PARENT PHONE, PARENT EMAIL
         ========================================================================= */}
      {isEnrollModalOpen && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white w-full max-w-lg rounded-2xl shadow-2xl border border-gray-100 p-6 space-y-5">
            {/* Modal Header: "Add Student" with close button */}
            <div className="flex items-center justify-between">
              <h2 className="text-base font-bold text-gray-900">Add Student</h2>
              <button
                type="button"
                onClick={() => setIsEnrollModalOpen(false)}
                className="text-gray-400 hover:text-gray-600 text-lg font-bold p-1 leading-none"
              >
                ✕
              </button>
            </div>

            {/* Modal Form: Exact Grid from Screenshot 1 */}
            <form onSubmit={handleEnrollStudent} className="space-y-4 text-xs">
              {/* Row 1: NAME & GRADE */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor={enrollNameId} className="block text-[11px] font-bold text-gray-600 tracking-wider uppercase mb-1.5">
                    NAME
                  </label>
                  <input
                    id={enrollNameId}
                    type="text"
                    required
                    placeholder="Full student name"
                    value={enrollForm.name}
                    onChange={(e) => setEnrollForm({ ...enrollForm, name: e.target.value })}
                    className="w-full px-3.5 py-2.5 text-xs rounded-lg border border-gray-200 bg-white text-gray-900 placeholder-gray-400 focus:outline-none focus:border-[#16335C] focus:ring-1 focus:ring-[#16335C]"
                  />
                </div>
                <div>
                  <label htmlFor={enrollGradeId} className="block text-[11px] font-bold text-gray-600 tracking-wider uppercase mb-1.5">
                    GRADE
                  </label>
                  <input
                    id={enrollGradeId}
                    type="text"
                    placeholder="e.g. Grade 10"
                    value={enrollForm.grade}
                    onChange={(e) => setEnrollForm({ ...enrollForm, grade: e.target.value })}
                    className="w-full px-3.5 py-2.5 text-xs rounded-lg border border-gray-200 bg-white text-gray-900 placeholder-gray-400 focus:outline-none focus:border-[#16335C] focus:ring-1 focus:ring-[#16335C]"
                  />
                </div>
              </div>

              {/* Row 2: SECTION & NATIONAL ID */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor={enrollSectionId} className="block text-[11px] font-bold text-gray-600 tracking-wider uppercase mb-1.5">
                    SECTION
                  </label>
                  <input
                    id={enrollSectionId}
                    type="text"
                    placeholder="e.g. A"
                    value={enrollForm.section}
                    onChange={(e) => setEnrollForm({ ...enrollForm, section: e.target.value })}
                    className="w-full px-3.5 py-2.5 text-xs rounded-lg border border-gray-200 bg-white text-gray-900 placeholder-gray-400 focus:outline-none focus:border-[#16335C] focus:ring-1 focus:ring-[#16335C]"
                  />
                </div>
                <div>
                  <label htmlFor={enrollNidId} className="block text-[11px] font-bold text-gray-600 tracking-wider uppercase mb-1.5">
                    NATIONAL ID
                  </label>
                  <input
                    id={enrollNidId}
                    type="text"
                    maxLength={14}
                    placeholder="14-digit National ID"
                    value={enrollForm.nationalId}
                    onChange={(e) => setEnrollForm({ ...enrollForm, nationalId: e.target.value })}
                    className="w-full px-3.5 py-2.5 text-xs rounded-lg border border-gray-200 bg-white text-gray-900 placeholder-gray-400 focus:outline-none focus:border-[#16335C] focus:ring-1 focus:ring-[#16335C] font-mono"
                  />
                </div>
              </div>

              {/* Row 3: PARENT NAME & PARENT PHONE */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor={enrollParentNameId} className="block text-[11px] font-bold text-gray-600 tracking-wider uppercase mb-1.5">
                    PARENT NAME
                  </label>
                  <input
                    id={enrollParentNameId}
                    type="text"
                    placeholder="Parent full name"
                    value={enrollForm.parentName}
                    onChange={(e) => setEnrollForm({ ...enrollForm, parentName: e.target.value })}
                    className="w-full px-3.5 py-2.5 text-xs rounded-lg border border-gray-200 bg-white text-gray-900 placeholder-gray-400 focus:outline-none focus:border-[#16335C] focus:ring-1 focus:ring-[#16335C]"
                  />
                </div>
                <div>
                  <label htmlFor={enrollParentPhoneId} className="block text-[11px] font-bold text-gray-600 tracking-wider uppercase mb-1.5">
                    PARENT PHONE
                  </label>
                  <input
                    id={enrollParentPhoneId}
                    type="tel"
                    placeholder="+20 1XX XXX XXXX"
                    value={enrollForm.parentPhone}
                    onChange={(e) => setEnrollForm({ ...enrollForm, parentPhone: e.target.value })}
                    className="w-full px-3.5 py-2.5 text-xs rounded-lg border border-gray-200 bg-white text-gray-900 placeholder-gray-400 focus:outline-none focus:border-[#16335C] focus:ring-1 focus:ring-[#16335C]"
                  />
                </div>
              </div>

              {/* Row 4: PARENT EMAIL (Full width) */}
              <div>
                <label htmlFor={enrollParentEmailId} className="block text-[11px] font-bold text-gray-600 tracking-wider uppercase mb-1.5">
                  PARENT EMAIL
                </label>
                <input
                  id={enrollParentEmailId}
                  type="email"
                  placeholder="parent@email.com"
                  value={enrollForm.parentEmail}
                  onChange={(e) => setEnrollForm({ ...enrollForm, parentEmail: e.target.value })}
                  className="w-full px-3.5 py-2.5 text-xs rounded-lg border border-gray-200 bg-white text-gray-900 placeholder-gray-400 focus:outline-none focus:border-[#16335C] focus:ring-1 focus:ring-[#16335C]"
                />
              </div>

              {/* Modal Footer: Cancel & Add Student buttons (matching Screenshot 1) */}
              <div className="flex items-center justify-end gap-3 pt-4">
                <button
                  type="button"
                  onClick={() => setIsEnrollModalOpen(false)}
                  disabled={actionSubmitting}
                  className="px-5 py-2.5 text-xs font-semibold text-gray-700 bg-white hover:bg-gray-50 border border-gray-200 rounded-lg transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={actionSubmitting}
                  className="px-5 py-2.5 text-xs font-semibold text-white bg-[#16335C] hover:bg-[#102544] rounded-lg shadow-sm transition-colors disabled:opacity-50"
                >
                  {actionSubmitting ? "Adding..." : "Add Student"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Student Profile & Financial Overview Drawer (Sub-tab view) */}
      {selectedStudentId && (
        <div className="fixed inset-0 bg-black/40 z-40 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white w-full max-w-2xl rounded-2xl shadow-2xl border border-gray-200 overflow-hidden max-h-[90vh] flex flex-col">
            {detailLoading || !studentDetail ? (
              <div className="flex justify-center py-16">
                <LoadingSpinner />
              </div>
            ) : (
              <>
                {/* Header */}
                <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between bg-[#FAFBFD]">
                  <div className="flex items-center gap-3">
                    <div
                      className={`w-9 h-9 rounded-full flex items-center justify-center text-white text-xs font-bold ${getAvatarColor(
                        studentDetail.id
                      )}`}
                    >
                      {getInitials(studentDetail.name)}
                    </div>
                    <div>
                      <div className="flex items-center gap-2">
                        <h3 className="text-sm font-bold text-gray-900">{studentDetail.name}</h3>
                        <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-green-100 text-green-800">
                          {studentDetail.status}
                        </span>
                      </div>
                      <div className="text-[11px] text-gray-500 mt-0.5 space-x-2">
                        <span>
                          Ref: <strong className="font-mono text-[#0B2545]">{studentDetail.studentRef}</strong>
                        </span>
                        <span>•</span>
                        <span>{studentDetail.grade} {studentDetail.section ? `(${studentDetail.section})` : ""}</span>
                        <span>•</span>
                        <span>NID: <strong className="font-mono">{studentDetail.nationalIdMasked ?? "••••••••••••••"}</strong></span>
                      </div>
                    </div>
                  </div>
                  <button
                    onClick={() => {
                      setSelectedStudentId(null);
                      setStudentDetail(null);
                    }}
                    className="text-gray-400 hover:text-gray-600 text-lg font-bold p-1"
                  >
                    ✕
                  </button>
                </div>

                {/* Body */}
                <div className="p-6 space-y-4 overflow-y-auto flex-1 text-xs">
                  {/* Guardian contact */}
                  <div className="grid grid-cols-3 gap-2 p-3 bg-gray-50 border border-gray-200 rounded-lg text-xs">
                    <div>
                      <span className="text-gray-400 block text-[10px]">Guardian</span>
                      <strong className="text-gray-800">{studentDetail.parentName || "—"}</strong>
                    </div>
                    <div>
                      <span className="text-gray-400 block text-[10px]">Phone</span>
                      <strong className="text-gray-800 font-mono">{studentDetail.parentPhone || "—"}</strong>
                    </div>
                    <div>
                      <span className="text-gray-400 block text-[10px]">Email</span>
                      <strong className="text-gray-800 truncate block">{studentDetail.parentEmail || "—"}</strong>
                    </div>
                  </div>

                  {/* Financial KPI Cards */}
                  {studentDetail.totals && (
                    <div className="grid grid-cols-3 gap-3">
                      <div className="p-3 rounded-lg border border-gray-200 bg-white">
                        <div className="text-[10px] uppercase font-bold text-gray-400">Total Invoiced</div>
                        <div className="text-sm font-bold text-gray-900 mt-0.5">
                          {money(studentDetail.totals.totalFeesEGP)}
                        </div>
                      </div>
                      <div className="p-3 rounded-lg border border-gray-200 bg-white">
                        <div className="text-[10px] uppercase font-bold text-emerald-600">Total Paid</div>
                        <div className="text-sm font-bold text-emerald-600 mt-0.5">
                          {money(studentDetail.totals.totalPaidEGP)}
                        </div>
                      </div>
                      <div className="p-3 rounded-lg border border-gray-200 bg-white">
                        <div className="text-[10px] uppercase font-bold text-amber-600">Outstanding Balance</div>
                        <div className="text-sm font-bold text-amber-600 mt-0.5">
                          {money(studentDetail.totals.totalOutstandingEGP)}
                        </div>
                      </div>
                    </div>
                  )}

                  {/* Sub-tab Navigation */}
                  <div className="flex items-center gap-3 border-b border-gray-200 pt-2 text-xs font-semibold">
                    <button
                      onClick={() => setDetailTab("fees")}
                      className={`pb-2 border-b-2 transition-colors ${
                        detailTab === "fees"
                          ? "border-[#0B2545] text-[#0B2545]"
                          : "border-transparent text-gray-400 hover:text-gray-700"
                      }`}
                    >
                      Fee Breakdown
                    </button>
                    <button
                      onClick={() => setDetailTab("payments")}
                      className={`pb-2 border-b-2 transition-colors ${
                        detailTab === "payments"
                          ? "border-[#0B2545] text-[#0B2545]"
                          : "border-transparent text-gray-400 hover:text-gray-700"
                      }`}
                    >
                      Payments Ledger
                    </button>
                    <button
                      onClick={() => setDetailTab("guardians")}
                      className={`pb-2 border-b-2 transition-colors ${
                        detailTab === "guardians"
                          ? "border-[#0B2545] text-[#0B2545]"
                          : "border-transparent text-gray-400 hover:text-gray-700"
                      }`}
                    >
                      Guardians
                    </button>
                    <button
                      onClick={() => setDetailTab("statement")}
                      className={`pb-2 border-b-2 transition-colors ${
                        detailTab === "statement"
                          ? "border-[#0B2545] text-[#0B2545]"
                          : "border-transparent text-gray-400 hover:text-gray-700"
                      }`}
                    >
                      Account Statement
                    </button>
                  </div>

                  {/* Tab 1: Fees */}
                  {detailTab === "fees" && (
                    <div>
                      {subTabLoading ? (
                        <div className="flex justify-center py-6">
                          <LoadingSpinner />
                        </div>
                      ) : !feesData || feesData.data.length === 0 ? (
                        <div className="py-6 text-center text-gray-400">No fee lines assigned.</div>
                      ) : (
                        <div className="max-h-56 overflow-y-auto rounded-lg border border-gray-200">
                          <table className="w-full text-left text-xs">
                            <thead className="bg-gray-50 border-b border-gray-200 sticky top-0 text-gray-500 font-semibold">
                              <tr>
                                <th className="px-3 py-2">Fee Item</th>
                                <th className="px-3 py-2">Due Date</th>
                                <th className="px-3 py-2">Original</th>
                                <th className="px-3 py-2">Remaining</th>
                                <th className="px-3 py-2">Status</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100 bg-white">
                              {feesData.data.map((f) => (
                                <tr key={f.feeId}>
                                  <td className="px-3 py-2 font-medium text-gray-800">
                                    {f.name}
                                    <span className="block text-[10px] text-gray-400">{f.category} • {f.term}</span>
                                  </td>
                                  <td className="px-3 py-2 text-gray-500">{f.dueDate ?? "—"}</td>
                                  <td className="px-3 py-2 text-gray-800">{money(f.originalAmountEGP)}</td>
                                  <td className="px-3 py-2 font-bold text-amber-600">{money(f.remainingEGP)}</td>
                                  <td className="px-3 py-2">
                                    <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-green-100 text-green-800">
                                      {f.status}
                                    </span>
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      )}
                    </div>
                  )}

                  {/* Tab 2: Payments */}
                  {detailTab === "payments" && (
                    <div>
                      {subTabLoading ? (
                        <div className="flex justify-center py-6">
                          <LoadingSpinner />
                        </div>
                      ) : !paymentsData || paymentsData.data.length === 0 ? (
                        <div className="py-6 text-center text-gray-400">No payments found for this student.</div>
                      ) : (
                        <div className="max-h-56 overflow-y-auto rounded-lg border border-gray-200">
                          <table className="w-full text-left text-xs">
                            <thead className="bg-gray-50 border-b border-gray-200 sticky top-0 text-gray-500 font-semibold">
                              <tr>
                                <th className="px-3 py-2">Transaction ID</th>
                                <th className="px-3 py-2">Fee</th>
                                <th className="px-3 py-2">Amount</th>
                                <th className="px-3 py-2">Date & Method</th>
                                <th className="px-3 py-2">Status</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100 bg-white">
                              {paymentsData.data.map((p) => (
                                <tr key={p.id}>
                                  <td className="px-3 py-2 font-mono font-bold text-[#0B2545]">{p.id}</td>
                                  <td className="px-3 py-2 text-gray-700">{p.feeName}</td>
                                  <td className="px-3 py-2 font-bold text-emerald-600">{money(p.amountEGP)}</td>
                                  <td className="px-3 py-2 text-gray-500">{p.date} • {p.method}</td>
                                  <td className="px-3 py-2">
                                    <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-green-100 text-green-800">
                                      {p.status}
                                    </span>
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      )}
                    </div>
                  )}

                  {/* Tab 3: Guardians */}
                  {detailTab === "guardians" && (
                    <div className="space-y-3">
                      {subTabLoading ? (
                        <div className="flex justify-center py-6">
                          <LoadingSpinner />
                        </div>
                      ) : !guardiansData || guardiansData.length === 0 ? (
                        <div className="py-6 text-center text-gray-400">No guardians linked.</div>
                      ) : (
                        <div className="space-y-2">
                          {guardiansData.map((g) => (
                            <div key={g.id} className="p-3 border border-gray-200 rounded-lg flex items-center justify-between">
                              <div>
                                <div className="flex items-center gap-2">
                                  <strong className="text-gray-800">{g.name}</strong>
                                  <span className="px-2 py-0.5 bg-blue-100 text-blue-800 rounded-full text-[10px] font-bold">
                                    {g.relationship}
                                  </span>
                                  {g.primaryGuardian && (
                                    <span className="px-2 py-0.5 bg-green-100 text-green-800 rounded-full text-[10px] font-bold">
                                      Primary Contact
                                    </span>
                                  )}
                                </div>
                                <div className="text-[11px] text-gray-400 mt-0.5 space-x-3">
                                  <span>Phone: {g.phone || "—"}</span>
                                  <span>•</span>
                                  <span>Email: {g.email || "—"}</span>
                                </div>
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}

                  {/* Tab 4: Statement */}
                  {detailTab === "statement" && (
                    <div className="space-y-3">
                      {subTabLoading ? (
                        <div className="flex justify-center py-6">
                          <LoadingSpinner />
                        </div>
                      ) : !statementData ? (
                        <div className="py-6 text-center text-gray-400">No statement records available.</div>
                      ) : (
                        <div className="max-h-56 overflow-y-auto rounded-lg border border-gray-200">
                          <table className="w-full text-left text-xs">
                            <thead className="bg-gray-50 border-b border-gray-200 sticky top-0 text-gray-500 font-semibold">
                              <tr>
                                <th className="px-3 py-2">Date</th>
                                <th className="px-3 py-2">Type</th>
                                <th className="px-3 py-2">Description</th>
                                <th className="px-3 py-2">Debit (+)</th>
                                <th className="px-3 py-2">Credit (-)</th>
                                <th className="px-3 py-2">Balance</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100 bg-white">
                              {statementData.ledger.map((tx, idx) => (
                                <tr key={idx}>
                                  <td className="px-3 py-2 text-gray-500">{tx.date}</td>
                                  <td className="px-3 py-2">
                                    <span className={`px-2 py-0.5 rounded text-[10px] font-bold ${
                                      tx.type === "PAYMENT" ? "bg-green-100 text-green-800" : "bg-amber-100 text-amber-800"
                                    }`}>
                                      {tx.type}
                                    </span>
                                  </td>
                                  <td className="px-3 py-2 font-medium text-gray-800">{tx.description}</td>
                                  <td className="px-3 py-2 text-red-600">{tx.debitEGP > 0 ? money(tx.debitEGP) : "—"}</td>
                                  <td className="px-3 py-2 text-emerald-600">{tx.creditEGP > 0 ? money(tx.creditEGP) : "—"}</td>
                                  <td className="px-3 py-2 font-bold">{money(tx.runningBalanceEGP)}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      )}
                    </div>
                  )}
                </div>

                {/* Footer */}
                <div className="px-6 py-3 border-t border-gray-100 bg-gray-50 flex justify-end gap-2">
                  <button
                    onClick={() => {
                      setSelectedStudentId(null);
                      setStudentDetail(null);
                    }}
                    className="px-4 py-1.5 rounded-lg border border-gray-200 text-xs font-semibold bg-white hover:bg-gray-100"
                  >
                    Close
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* Deactivate Modal */}
      {isDeactivateModalOpen && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white w-full max-w-md rounded-2xl shadow-2xl border border-gray-100 p-6 space-y-4 text-xs">
            <h3 className="text-sm font-bold text-gray-900">Deactivate Student</h3>
            <p className="text-gray-600">
              Are you sure you want to move this student to the Deactivated Archive?
            </p>
            <div>
              <label htmlFor={deactReasonSelectId} className="block font-bold text-gray-700 mb-1">
                Reason for Withdrawal
              </label>
              <select
                id={deactReasonSelectId}
                value={deactivateReason}
                onChange={(e) => setDeactivateReason(e.target.value)}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 bg-white"
              >
                {DEACTIVATE_REASONS.map((r) => (
                  <option key={r} value={r}>
                    {r}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setIsDeactivateModalOpen(false)}
                className="px-4 py-2 border border-gray-200 rounded-lg text-gray-700 font-medium hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleDeactivateStudent}
                disabled={actionSubmitting}
                className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg font-semibold"
              >
                Deactivate
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Reactivate Modal */}
      {isReactivateModalOpen && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4 backdrop-blur-xs">
          <div className="bg-white w-full max-w-md rounded-2xl shadow-2xl border border-gray-100 p-6 space-y-4 text-xs">
            <h3 className="text-sm font-bold text-gray-900">Reactivate Student</h3>
            <p className="text-gray-600">
              Are you sure you want to restore this student to the active roster?
            </p>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setIsReactivateModalOpen(false)}
                className="px-4 py-2 border border-gray-200 rounded-lg text-gray-700 font-medium hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleReactivateStudent}
                disabled={actionSubmitting}
                className="px-4 py-2 bg-[#0B2545] hover:bg-[#071a33] text-white rounded-lg font-semibold"
              >
                Reactivate
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
