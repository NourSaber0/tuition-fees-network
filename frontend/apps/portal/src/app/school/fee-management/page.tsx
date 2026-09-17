"use client";

import { useEffect, useState, useCallback } from "react";
import { useApiClient, FeeDto, FeeCategoryDto, getFees, getFeeCategories } from "@tuition/api-client";
import {
  Button,
  Badge,
  LoadingSpinner,
  formatIsoDate,
  SearchIcon,
  PlusIcon,
  Pagination,
} from "@tuition/ui";
import FeeDetailDrawer from "./FeeDetailDrawer";
import CreateFeeModal from "./CreateFeeModal";

export default function FeeManagementPage() {
  const apiClient = useApiClient();

  const [fees, setFees] = useState<FeeDto[]>([]);
  const [categories, setCategories] = useState<FeeCategoryDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [search, setSearch] = useState("");
  const [selectedCategory, setSelectedCategory] = useState("");
  const [selectedStatus, setSelectedStatus] = useState("");
  const [dueDateFrom, setDueDateFrom] = useState("");
  const [dueDateTo, setDueDateTo] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  
  const [selectedFeeId, setSelectedFeeId] = useState<string | null>(null);
  const [showCreateModal, setShowCreateModal] = useState(false);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [feesRes, catRes] = await Promise.all([
        getFees(apiClient, {
          search: search.trim() || undefined,
          category: selectedCategory || undefined,
          status: selectedStatus || undefined,
          dueDateFrom: dueDateFrom || undefined,
          dueDateTo: dueDateTo || undefined,
          page,
          pageSize: 25,
        }),
        getFeeCategories(apiClient),
      ]);
      setFees(feesRes.data);
      setTotalPages(Math.max(1, feesRes.totalPages ?? 1));
      setCategories(catRes);
    } catch {
      setError("Failed to load fees.");
    } finally {
      setLoading(false);
    }
  }, [apiClient, search, selectedCategory, selectedStatus, dueDateFrom, dueDateTo, page]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // Reset to page 0 when filters change
  const handleSearchChange = (val: string) => {
    setSearch(val);
    setPage(0);
  };
  const handleCategoryChange = (val: string) => {
    setSelectedCategory(val);
    setPage(0);
  };
  const handleStatusChange = (val: string) => {
    setSelectedStatus(val);
    setPage(0);
  };
  const handleDueDateFromChange = (val: string) => {
    setDueDateFrom(val);
    setPage(0);
  };
  const handleDueDateToChange = (val: string) => {
    setDueDateTo(val);
    setPage(0);
  };

  const handleCreated = () => {
    setShowCreateModal(false);
    fetchData();
  };

  const handleUpdated = () => {
    fetchData();
  };

  return (
    <>
      <div className="flex flex-col h-full bg-[#FAFBFD] p-6">
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold text-[#1B2A4A]">Fees</h1>
            <p className="text-gray-500 text-sm mt-1">Manage and assign student fees</p>
          </div>
          <Button onClick={() => setShowCreateModal(true)} className="gap-2">
            <PlusIcon className="w-4 h-4" /> Add Fee
          </Button>
        </div>

        <div className="bg-white rounded-xl shadow-sm border border-gray-100 flex-1 flex flex-col min-h-0 overflow-hidden">
          <div className="p-4 border-b border-gray-100 flex flex-wrap gap-3 items-center">
            <div className="relative w-64">
              <SearchIcon className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
              <input
                type="text"
                placeholder="Search by student or fee ID..."
                className="w-full pl-9 pr-4 py-2 border rounded-lg text-sm outline-none focus:border-blue-500"
                value={search}
                onChange={(e) => handleSearchChange(e.target.value)}
              />
            </div>
            
            <select
              value={selectedCategory}
              onChange={(e) => handleCategoryChange(e.target.value)}
              className="border rounded-lg px-3 py-2 text-sm outline-none focus:border-blue-500 bg-white"
            >
              <option value="">All Categories</option>
              {categories.map((c) => (
                <option key={c.code} value={c.code}>{c.displayName}</option>
              ))}
            </select>

            <select
              value={selectedStatus}
              onChange={(e) => handleStatusChange(e.target.value)}
              className="border rounded-lg px-3 py-2 text-sm outline-none focus:border-blue-500 bg-white"
            >
              <option value="">All Statuses</option>
              <option value="Active">Active</option>
              <option value="Paid">Paid</option>
              <option value="Partial">Partial</option>
              <option value="Overdue">Overdue</option>
            </select>

            <div className="flex items-center gap-1.5 text-xs text-gray-500">
              <span>Due:</span>
              <input
                type="date"
                value={dueDateFrom}
                onChange={(e) => handleDueDateFromChange(e.target.value)}
                className="border rounded-lg px-2 py-1.5 text-xs outline-none focus:border-blue-500 bg-white"
                title="Due Date From"
              />
              <span>to</span>
              <input
                type="date"
                value={dueDateTo}
                onChange={(e) => handleDueDateToChange(e.target.value)}
                className="border rounded-lg px-2 py-1.5 text-xs outline-none focus:border-blue-500 bg-white"
                title="Due Date To"
              />
            </div>

            {(search || selectedCategory || selectedStatus || dueDateFrom || dueDateTo) && (
              <button
                type="button"
                onClick={() => {
                  setSearch("");
                  setSelectedCategory("");
                  setSelectedStatus("");
                  setDueDateFrom("");
                  setDueDateTo("");
                  setPage(0);
                }}
                className="text-xs text-blue-600 hover:text-blue-800 underline ml-auto"
              >
                Clear filters
              </button>
            )}
          </div>

          <div className="flex-1 overflow-auto">
            {loading ? (
              <div className="h-full flex items-center justify-center">
                <LoadingSpinner />
              </div>
            ) : error ? (
              <div className="p-8 text-center text-red-500">{error}</div>
            ) : fees.length === 0 ? (
              <div className="p-12 text-center text-gray-500">No fees found matching filters.</div>
            ) : (
              <table className="w-full text-left text-xs">
                <thead className="bg-[#FAFBFD] border-b border-gray-200 text-gray-500 font-semibold uppercase tracking-wider text-[11px] sticky top-0 z-10">
                  <tr>
                    <th className="px-5 py-3">FEE ID / NAME</th>
                    <th className="px-5 py-3">STUDENT</th>
                    <th className="px-5 py-3">CATEGORY</th>
                    <th className="px-5 py-3 text-right">AMOUNT (EGP)</th>
                    <th className="px-5 py-3 text-right">REMAINING (EGP)</th>
                    <th className="px-5 py-3">DUE DATE</th>
                    <th className="px-5 py-3">STATUS</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {fees.map((fee) => (
                    <tr
                      key={fee.id}
                      onClick={() => setSelectedFeeId(fee.id)}
                      className="hover:bg-slate-50/70 transition-colors cursor-pointer"
                    >
                      <td className="px-5 py-3.5">
                        <div className="font-mono text-gray-500 text-[10px] mb-0.5">{fee.id}</div>
                        <div className="font-semibold text-gray-900 text-xs">{fee.name}</div>
                      </td>
                      <td className="px-5 py-3.5">
                        <div className="font-medium text-gray-900 text-xs">{fee.studentName || fee.studentId}</div>
                      </td>
                      <td className="px-5 py-3.5 text-gray-600">
                        {categories.find(c => c.code === fee.category)?.displayName || fee.category}
                      </td>
                      <td className="px-5 py-3.5 text-right font-mono text-gray-900">
                        {fee.totalDueEGP != null ? fee.totalDueEGP.toLocaleString() : fee.originalAmountEGP.toLocaleString()}
                        {fee.penaltyApplied && (
                          <div className="text-[10px] text-red-500">+ Penalty</div>
                        )}
                      </td>
                      <td className="px-5 py-3.5 text-right font-mono text-gray-900 font-semibold">
                        {fee.remainingEGP.toLocaleString()}
                      </td>
                      <td className="px-5 py-3.5">
                        <div className="text-gray-600 font-mono text-[11px]">{formatIsoDate(fee.dueDate)}</div>
                      </td>
                      <td className="px-5 py-3.5">
                        <Badge tone={fee.status === "Paid" ? "success" : fee.status === "Overdue" ? "danger" : fee.status === "Partial" ? "warning" : "neutral"}>
                          {fee.status}
                        </Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          {totalPages > 1 && (
            <div className="p-4 border-t border-gray-100 flex justify-center bg-white">
              <Pagination
                page={page + 1}
                totalPages={totalPages}
                onPageChange={(p) => setPage(p - 1)}
              />
            </div>
          )}
        </div>
      </div>

      {selectedFeeId && (
        <FeeDetailDrawer
          feeId={selectedFeeId}
          onClose={() => setSelectedFeeId(null)}
          onUpdated={handleUpdated}
        />
      )}

      {showCreateModal && (
        <CreateFeeModal
          onClose={() => setShowCreateModal(false)}
          onCreated={handleCreated}
          categories={categories}
        />
      )}
    </>
  );
}
