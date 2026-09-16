"use client";

import { useEffect, useState, useCallback, useRef } from "react";
import { useRouter } from "next/navigation";
import { useApiClient, useAuth } from "@tuition/api-client";
import {
  FeeUploadListDto,
  getFeeUploadHistory,
  uploadFees,
  downloadFeeUploadTemplate,
} from "@tuition/api-client";
import {
  Button,
  Badge,
  LoadingSpinner,
  formatIsoDate,
  DownloadIcon,
  UploadCloudIcon,
  FileIcon,
} from "@tuition/ui";

function Uploader({ onUploadComplete }: { onUploadComplete: (id: string) => void }) {
  const apiClient = useApiClient();
  const [isDragging, setIsDragging] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setFile(e.target.files[0]);
      setError(null);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      const droppedFile = e.dataTransfer.files[0];
      if (
        droppedFile.type === "text/csv" ||
        droppedFile.name.endsWith(".csv") ||
        droppedFile.name.endsWith(".xlsx") ||
        droppedFile.name.endsWith(".xls")
      ) {
        setFile(droppedFile);
        setError(null);
      } else {
        setError("Only CSV and Excel files are supported.");
      }
    }
  };

  const handleUpload = async () => {
    if (!file) return;
    setUploading(true);
    setError(null);
    try {
      const res = await uploadFees(apiClient, file);
      if (res.uploadId) {
        onUploadComplete(res.uploadId);
      } else {
        throw new Error("Invalid response from server.");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to upload file.");
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-bold text-[#1B2A4A]">Upload Fees File</h2>
      </div>

      <div
        className={`border-2 border-dashed rounded-xl p-8 text-center transition-colors ${
          isDragging ? "border-blue-500 bg-blue-50" : "border-gray-300 hover:border-gray-400"
        }`}
        onDragOver={(e) => {
          e.preventDefault();
          setIsDragging(true);
        }}
        onDragLeave={() => setIsDragging(false)}
        onDrop={handleDrop}
      >
        {file ? (
          <div className="flex flex-col items-center">
            <div className="w-12 h-12 bg-blue-50 rounded-full flex items-center justify-center text-blue-600 mb-3">
              <FileIcon className="w-6 h-6" />
            </div>
            <p className="text-sm font-semibold text-gray-800">{file.name}</p>
            <p className="text-xs text-gray-500 mt-1">{(file.size / 1024).toFixed(1)} KB</p>
            
            <div className="flex items-center gap-3 mt-5">
              <Button variant="secondary" onClick={() => setFile(null)} disabled={uploading}>
                Remove
              </Button>
              <Button onClick={handleUpload} loading={uploading}>
                Upload File
              </Button>
            </div>
          </div>
        ) : (
          <div className="flex flex-col items-center">
            <div className="w-12 h-12 bg-gray-50 rounded-full flex items-center justify-center text-gray-400 mb-3">
              <UploadCloudIcon className="w-6 h-6" />
            </div>
            <p className="text-sm font-semibold text-gray-700">Drag & drop your CSV or Excel file here</p>
            <p className="text-xs text-gray-500 mt-1 mb-4">Maximum file size: 10MB</p>
            
            <input
              type="file"
              ref={fileInputRef}
              onChange={handleFileChange}
              accept=".csv, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, application/vnd.ms-excel"
              className="hidden"
            />
            <Button variant="secondary" onClick={() => fileInputRef.current?.click()}>
              Browse Files
            </Button>
          </div>
        )}
      </div>
      
      {error && (
        <div className="mt-4 p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">
          {error}
        </div>
      )}
    </div>
  );
}

export default function FeeUploadPage() {
  const router = useRouter();
  const apiClient = useApiClient();
  const [history, setHistory] = useState<FeeUploadListDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchHistory = useCallback(async () => {
    try {
      const res = await getFeeUploadHistory(apiClient);
      setHistory(res);
    } catch {
      setError("Failed to load upload history.");
    } finally {
      setLoading(false);
    }
  }, [apiClient]);

  useEffect(() => {
    fetchHistory();
  }, [fetchHistory]);

  const handleDownloadTemplate = async () => {
    try {
      await downloadFeeUploadTemplate(process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1");
    } catch (err) {
      alert(err instanceof Error ? err.message : "Failed to download template.");
    }
  };

  const getStatusTone = (status: string) => {
    switch (status) {
      case "Completed": return "success";
      case "Processing": return "info";
      case "Completed with Errors": return "warning";
      case "Failed": return "danger";
      default: return "neutral";
    }
  };

  return (
    <div className="flex flex-col min-h-full bg-[#FAFBFD] p-6 gap-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[#1B2A4A]">Fee Uploads</h1>
          <p className="text-gray-500 text-sm mt-1">Bulk upload and manage student fees via CSV/Excel.</p>
        </div>
        <Button variant="secondary" onClick={handleDownloadTemplate} className="gap-2">
          <DownloadIcon className="w-4 h-4" /> Download Template
        </Button>
      </div>

      <Uploader onUploadComplete={(id) => router.push(`/school/fee-upload/${id}`)} />

      <div className="bg-white rounded-xl shadow-sm border border-gray-100 flex-1 flex flex-col min-h-0 overflow-hidden">
        <div className="p-4 border-b border-gray-100">
          <h2 className="text-lg font-bold text-[#1B2A4A]">Upload History</h2>
        </div>

        <div className="flex-1 overflow-auto">
          {loading ? (
            <div className="flex justify-center p-10">
              <LoadingSpinner className="w-8 h-8 text-blue-500" />
            </div>
          ) : error ? (
            <div className="p-6 text-center text-red-500 text-sm">{error}</div>
          ) : history.length === 0 ? (
            <div className="p-12 text-center text-gray-500 text-sm">
              No fee uploads found.
            </div>
          ) : (
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-[#F8FAFC] border-b border-gray-100 text-[11px] uppercase tracking-wider text-gray-500">
                  <th className="px-5 py-3 font-semibold">Upload ID</th>
                  <th className="px-5 py-3 font-semibold">Date</th>
                  <th className="px-5 py-3 font-semibold">File Name</th>
                  <th className="px-5 py-3 font-semibold text-right">Rows</th>
                  <th className="px-5 py-3 font-semibold text-right">Accepted</th>
                  <th className="px-5 py-3 font-semibold text-right">Rejected</th>
                  <th className="px-5 py-3 font-semibold">Status</th>
                  <th className="px-5 py-3 font-semibold"></th>
                </tr>
              </thead>
              <tbody className="text-sm divide-y divide-gray-50">
                {history.map((h) => (
                  <tr key={h.uploadId} className="hover:bg-gray-50/50 transition-colors">
                    <td className="px-5 py-3.5 font-mono text-xs text-gray-600">{h.uploadId}</td>
                    <td className="px-5 py-3.5 text-gray-700">{formatIsoDate(h.uploadedAt)}</td>
                    <td className="px-5 py-3.5 text-[#1B2A4A] font-medium">{h.fileName}</td>
                    <td className="px-5 py-3.5 text-gray-600 text-right">{h.totalRows}</td>
                    <td className="px-5 py-3.5 text-emerald-600 font-semibold text-right">{h.acceptedRows}</td>
                    <td className="px-5 py-3.5 text-rose-600 font-semibold text-right">{h.rejectedRows}</td>
                    <td className="px-5 py-3.5">
                      <Badge tone={getStatusTone(h.status)}>{h.status}</Badge>
                    </td>
                    <td className="px-5 py-3.5 text-right">
                      <Button variant="secondary" size="sm" onClick={() => router.push(`/school/fee-upload/${h.uploadId}`)}>
                        View Details
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
}
