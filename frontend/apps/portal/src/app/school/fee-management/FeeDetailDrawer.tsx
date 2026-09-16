"use client";

import { useEffect, useState } from "react";
import { useApiClient, FeeDetailDto, getFeeDetail, updateFee } from "@tuition/api-client";
import { Button, Badge, LoadingSpinner, formatIsoDate, XIcon, EditIcon } from "@tuition/ui";

interface FeeDetailDrawerProps {
  feeId: string;
  onClose: () => void;
  onUpdated: () => void;
}

export default function FeeDetailDrawer({ feeId, onClose, onUpdated }: FeeDetailDrawerProps) {
  const apiClient = useApiClient();
  const [fee, setFee] = useState<FeeDetailDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [isEditing, setIsEditing] = useState(false);
  const [editAmount, setEditAmount] = useState<number | "">("");
  const [editDueDate, setEditDueDate] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");

  useEffect(() => {
    const fetchDetail = async () => {
      try {
        const data = await getFeeDetail(apiClient, feeId);
        setFee(data);
        setEditAmount(data.originalAmountEGP);
        setEditDueDate(data.dueDate);
      } catch (err) {
        setError("Failed to load fee details.");
      } finally {
        setLoading(false);
      }
    };
    fetchDetail();
  }, [apiClient, feeId]);

  const handleSave = async () => {
    if (!fee) return;
    setSaving(true);
    setSaveError("");
    try {
      await updateFee(apiClient, feeId, {
        amountEGP: editAmount ? Number(editAmount) : fee.originalAmountEGP,
        dueDate: editDueDate || fee.dueDate,
      });
      setIsEditing(false);
      onUpdated();
      // Refetch locally
      const data = await getFeeDetail(apiClient, feeId);
      setFee(data);
    } catch (err: unknown) {
      const e = err as { message?: string };
      if (e.message?.includes("409")) {
        setSaveError("This fee is locked and cannot be edited.");
      } else {
        setSaveError(e.message || "Failed to update fee.");
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <div className="fixed inset-0 z-40 bg-black/40 backdrop-blur-xs" onClick={onClose} />
      <div className="fixed inset-y-0 right-0 w-[540px] bg-white shadow-2xl z-50 flex flex-col transform transition-transform duration-300">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="text-lg font-bold text-[#1B2A4A]">Fee Details</h2>
          <button onClick={onClose} className="p-2 hover:bg-gray-100 rounded-full text-gray-500 transition-colors">
            <XIcon className="w-5 h-5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-6 space-y-6">
          {loading ? (
            <div className="flex items-center justify-center h-40">
              <LoadingSpinner />
            </div>
          ) : error ? (
            <div className="text-red-500 text-center">{error}</div>
          ) : !fee ? null : (
            <>
              {/* Header Info */}
              <div className="bg-[#FAFBFD] p-5 rounded-xl border border-gray-100 space-y-4">
                <div className="flex justify-between items-start">
                  <div>
                    <div className="font-mono text-xs text-gray-500 mb-1">{fee.id}</div>
                    <div className="text-lg font-bold text-[#1B2A4A]">{fee.name}</div>
                  </div>
                  <Badge tone={fee.status === "Paid" ? "success" : fee.status === "Overdue" ? "danger" : fee.status === "Partial" ? "warning" : "neutral"}>
                    {fee.status}
                  </Badge>
                </div>

                <div className="grid grid-cols-2 gap-4 text-sm pt-2 border-t border-gray-100">
                  <div>
                    <div className="text-gray-500 text-xs mb-0.5">Student ID</div>
                    <div className="font-mono text-gray-900">{fee.studentId}</div>
                  </div>
                  <div>
                    <div className="text-gray-500 text-xs mb-0.5">Category</div>
                    <div className="font-medium text-gray-900">{fee.category}</div>
                  </div>
                </div>
              </div>

              {/* Financials & Edit */}
              <div className="border border-gray-200 rounded-xl overflow-hidden">
                <div className="bg-gray-50 px-5 py-3 border-b border-gray-200 flex justify-between items-center">
                  <h3 className="font-bold text-gray-900 text-sm">Financials & Deadline</h3>
                  {!isEditing && fee.status !== "Paid" && fee.status !== "Partial" && (
                    <Button variant="secondary" size="sm" onClick={() => setIsEditing(true)} className="h-7 text-xs">
                      <EditIcon className="w-3 h-3 mr-1" /> Edit
                    </Button>
                  )}
                </div>
                
                <div className="p-5 space-y-4">
                  {saveError && (
                    <div className="bg-red-50 text-red-600 p-3 rounded-lg text-sm border border-red-100">
                      {saveError}
                    </div>
                  )}

                  <div className="grid grid-cols-2 gap-6">
                    <div>
                      <div className="text-gray-500 text-xs mb-1.5">Original Amount</div>
                      {isEditing ? (
                        <div className="relative">
                          <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400 text-xs font-mono">EGP</span>
                          <input
                            type="number"
                            value={editAmount}
                            onChange={(e) => setEditAmount(e.target.value ? Number(e.target.value) : "")}
                            className="w-full pl-10 pr-3 py-1.5 border rounded-lg text-sm font-mono outline-none focus:border-blue-500"
                          />
                        </div>
                      ) : (
                        <div className="font-mono text-gray-900 font-semibold text-base">EGP {fee.originalAmountEGP.toLocaleString()}</div>
                      )}
                    </div>
                    <div>
                      <div className="text-gray-500 text-xs mb-1.5">Due Date</div>
                      {isEditing ? (
                        <input
                          type="date"
                          value={editDueDate}
                          onChange={(e) => setEditDueDate(e.target.value)}
                          className="w-full px-3 py-1.5 border rounded-lg text-sm font-mono outline-none focus:border-blue-500"
                        />
                      ) : (
                        <div>
                          <div className="font-mono text-gray-900 font-semibold text-base">{formatIsoDate(fee.dueDate)}</div>
                          {fee.overdue && fee.status !== "Paid" && (
                            <div className={`text-xs mt-0.5 font-medium ${fee.overdue.isOverdue ? "text-red-500" : "text-gray-500"}`}>
                              {fee.overdue.isOverdue ? `${fee.overdue.daysOverdue} days overdue` : "Upcoming"}
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  </div>

                  {!isEditing && (
                    <>
                      <div className="grid grid-cols-2 gap-6 pt-4 border-t border-gray-100">
                        <div>
                          <div className="text-gray-500 text-xs mb-0.5">Paid</div>
                          <div className="font-mono text-green-600 font-semibold">EGP {fee.paidEGP.toLocaleString()}</div>
                        </div>
                        <div>
                          <div className="text-gray-500 text-xs mb-0.5">Remaining</div>
                          <div className="font-mono text-amber-600 font-semibold">EGP {fee.remainingEGP.toLocaleString()}</div>
                        </div>
                      </div>

                      {fee.penalty?.applied && (
                        <div className="bg-red-50 rounded-lg p-3 border border-red-100 mt-4">
                          <div className="flex justify-between items-center text-sm mb-1">
                            <span className="text-red-800 font-semibold">Penalty Applied</span>
                            <span className="font-mono text-red-600 font-bold">+ EGP {fee.penalty.penaltyAmountEGP?.toLocaleString()}</span>
                          </div>
                          <div className="text-xs text-red-600/80">
                            Applied on {fee.penalty.penaltyAppliedAt ? formatIsoDate(fee.penalty.penaltyAppliedAt) : "N/A"}
                          </div>
                          <div className="flex justify-between items-center text-sm mt-3 pt-3 border-t border-red-200">
                            <span className="font-semibold text-gray-900">Total Due Now</span>
                            <span className="font-mono text-gray-900 font-bold">EGP {fee.penalty.totalDueEGP.toLocaleString()}</span>
                          </div>
                        </div>
                      )}
                    </>
                  )}

                  {isEditing && (
                    <div className="flex justify-end gap-2 pt-4">
                      <Button variant="secondary" onClick={() => setIsEditing(false)} disabled={saving}>Cancel</Button>
                      <Button onClick={handleSave} loading={saving}>Save Changes</Button>
                    </div>
                  )}
                </div>
              </div>

              {/* Payment History */}
              <div className="space-y-3">
                <h3 className="font-bold text-gray-900 text-sm">Payment History</h3>
                {fee.paymentHistory.length === 0 ? (
                  <div className="text-sm text-gray-500 italic">No payments recorded.</div>
                ) : (
                  <div className="space-y-2">
                    {fee.paymentHistory.map((ph, idx) => (
                      <div key={idx} className="bg-white border rounded-lg p-3 flex justify-between items-center">
                        <div>
                          <div className="font-mono text-xs text-gray-500">{ph.paymentId}</div>
                          <div className="text-xs text-gray-400 mt-0.5">
                            {ph.dateEGP ? formatIsoDate(ph.dateEGP) : ph.timestamp ? formatIsoDate(ph.timestamp) : ""}
                          </div>
                        </div>
                        <div className="text-right">
                          <div className="font-mono text-sm font-bold text-gray-900">EGP {ph.amountEGP.toLocaleString()}</div>
                          <div className="text-[10px] uppercase font-bold text-green-600 tracking-wider mt-0.5">{ph.status}</div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      </div>
    </>
  );
}
