"use client";

import { useState } from "react";
import { useApiClient, createFee, FeeCategoryDto } from "@tuition/api-client";
import { Button, XIcon, SearchIcon } from "@tuition/ui";

export interface StudentSearchDto {
  id: string;
  name: string;
  studentRef?: string;
  grade?: string;
}

interface CreateFeeModalProps {
  onClose: () => void;
  onCreated: () => void;
  categories: FeeCategoryDto[];
}

export default function CreateFeeModal({ onClose, onCreated, categories }: CreateFeeModalProps) {
  const apiClient = useApiClient();

  const [search, setSearch] = useState("");
  const [searchResults, setSearchResults] = useState<StudentSearchDto[]>([]);
  const [searching, setSearching] = useState(false);
  const [selectedStudent, setSelectedStudent] = useState<StudentSearchDto | null>(null);

  const [name, setName] = useState("");
  const [category, setCategory] = useState("");
  const [amountEGP, setAmountEGP] = useState<number | "">("");
  const [term, setTerm] = useState("");
  const [dueDate, setDueDate] = useState("");

  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const handleSearch = async (q: string) => {
    setSearch(q);
    if (!q || q.trim().length < 2) {
      setSearchResults([]);
      return;
    }
    setSearching(true);
    try {
      // The API contract mentions GET /students/search?q=
      const res = await apiClient.get<StudentSearchDto[]>(`/students/search?q=${encodeURIComponent(q)}`);
      setSearchResults(res || []);
    } catch (err) {
      console.error("Student search failed", err);
    } finally {
      setSearching(false);
    }
  };

  const handleCreate = async () => {
    if (!selectedStudent || !name || !category || !amountEGP || !dueDate) {
      setError("Please fill in all required fields.");
      return;
    }
    setSaving(true);
    setError("");
    try {
      await createFee(apiClient, {
        studentId: selectedStudent.id,
        name,
        category,
        amountEGP: Number(amountEGP),
        term,
        dueDate,
      });
      onCreated();
    } catch (err: unknown) {
      setError((err as Error).message || "Failed to create fee.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/40 backdrop-blur-xs" onClick={onClose} />
      <div className="relative bg-white rounded-xl shadow-2xl w-full max-w-lg overflow-hidden flex flex-col max-h-[90vh]">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 bg-[#FAFBFD]">
          <h2 className="text-lg font-bold text-[#1B2A4A]">Add New Fee</h2>
          <button onClick={onClose} className="p-2 hover:bg-gray-200 rounded-full text-gray-500 transition-colors">
            <XIcon className="w-5 h-5" />
          </button>
        </div>

        <div className="p-6 overflow-y-auto">
          {error && (
            <div className="mb-4 p-3 bg-red-50 text-red-600 rounded-lg text-sm border border-red-100">
              {error}
            </div>
          )}

          <div className="space-y-5">
            {/* Student Picker */}
            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5">
                Assign to Student <span className="text-red-500">*</span>
              </label>
              {selectedStudent ? (
                <div className="flex items-center justify-between border border-blue-200 bg-blue-50 px-4 py-2.5 rounded-lg">
                  <div>
                    <div className="font-semibold text-blue-900 text-sm">{selectedStudent.name}</div>
                    <div className="text-xs font-mono text-blue-600/80">{selectedStudent.studentRef || `STU-${selectedStudent.id.substring(0, 4)}`}</div>
                  </div>
                  <button onClick={() => setSelectedStudent(null)} className="text-blue-500 hover:text-blue-700 text-sm font-medium">
                    Change
                  </button>
                </div>
              ) : (
                <div className="relative">
                  <div className="relative">
                    <SearchIcon className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
                    <input
                      type="text"
                      placeholder="Search by student name or ID..."
                      className="w-full pl-9 pr-4 py-2 border rounded-lg text-sm outline-none focus:border-blue-500"
                      value={search}
                      onChange={(e) => handleSearch(e.target.value)}
                    />
                  </div>
                  {(searching || searchResults.length > 0) && search.length >= 2 && (
                    <div className="absolute left-0 right-0 top-full mt-1 bg-white border shadow-lg rounded-lg max-h-48 overflow-y-auto z-10">
                      {searching ? (
                        <div className="p-4 text-center text-gray-400 text-xs">Searching...</div>
                      ) : (
                        searchResults.map((stu) => (
                          <div
                            key={stu.id}
                            className="p-3 hover:bg-gray-50 cursor-pointer border-b last:border-0"
                            onClick={() => {
                              setSelectedStudent(stu);
                              setSearch("");
                              setSearchResults([]);
                            }}
                          >
                            <div className="font-semibold text-gray-900 text-sm">{stu.name}</div>
                            <div className="text-xs font-mono text-gray-500">{stu.studentRef || `STU-${stu.id.substring(0, 4)}`} • {stu.grade || 'No Grade'}</div>
                          </div>
                        ))
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-1.5">
                  Fee Category <span className="text-red-500">*</span>
                </label>
                <select
                  value={category}
                  onChange={(e) => setCategory(e.target.value)}
                  className="w-full border rounded-lg px-3 py-2 text-sm outline-none focus:border-blue-500 bg-white"
                >
                  <option value="" disabled>Select category...</option>
                  {categories.map((c) => (
                    <option key={c.code} value={c.code}>{c.displayName}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-1.5">
                  Amount (EGP) <span className="text-red-500">*</span>
                </label>
                <div className="relative">
                  <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-xs font-mono">EGP</span>
                  <input
                    type="number"
                    value={amountEGP}
                    onChange={(e) => setAmountEGP(e.target.value ? Number(e.target.value) : "")}
                    className="w-full pl-10 pr-3 py-2 border rounded-lg text-sm font-mono outline-none focus:border-blue-500"
                    placeholder="0.00"
                  />
                </div>
              </div>
            </div>

            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5">
                Fee Name / Description <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="w-full px-3 py-2 border rounded-lg text-sm outline-none focus:border-blue-500"
                placeholder="e.g. Tuition - Term 1 2026/27"
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-1.5">
                  Due Date <span className="text-red-500">*</span>
                </label>
                <input
                  type="date"
                  value={dueDate}
                  onChange={(e) => setDueDate(e.target.value)}
                  className="w-full px-3 py-2 border rounded-lg text-sm font-mono outline-none focus:border-blue-500"
                />
              </div>
              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-1.5">
                  Academic Term <span className="text-gray-400 font-normal ml-1">(Optional)</span>
                </label>
                <input
                  type="text"
                  value={term}
                  onChange={(e) => setTerm(e.target.value)}
                  className="w-full px-3 py-2 border rounded-lg text-sm outline-none focus:border-blue-500"
                  placeholder="e.g. Term 1"
                />
              </div>
            </div>
          </div>
        </div>

        <div className="p-6 border-t border-gray-100 bg-gray-50 flex justify-end gap-3">
          <Button variant="secondary" onClick={onClose} disabled={saving}>Cancel</Button>
          <Button onClick={handleCreate} loading={saving}>Create Fee</Button>
        </div>
      </div>
    </div>
  );
}
