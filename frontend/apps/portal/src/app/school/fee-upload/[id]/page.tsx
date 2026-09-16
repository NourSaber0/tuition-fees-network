"use client";

import { useEffect, useState, useCallback, useRef } from "react";
import { useRouter, useParams } from "next/navigation";
import { useApiClient, useAuth } from "@tuition/api-client";
import {
  FeeUploadResultDto,
  FeeUploadRowDto,
  getFeeUploadStatus,
  getFeeUploadRows,
  downloadFeeUploadErrors,
  resubmitFeeUpload,
} from "@tuition/api-client";
import {
  Button,
  Badge,
  LoadingSpinner,
  formatIsoDate,
  DownloadIcon,
  UploadCloudIcon,
  ArrowLeftIcon,
  FileIcon,
} from "@tuition/ui";

function ResubmitUploader({ uploadId, onResubmitComplete }: { uploadId: string, onResubmitComplete: () => void }) {
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
      await resubmitFeeUpload(apiClient, uploadId, file);
      setFile(null);
      onResubmitComplete();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to resubmit file.");
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="bg-white rounded-xl shadow-sm border border-blue-100 p-6 mt-6">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-lg font-bold text-[#1B2A4A]">Resubmit Corrections</h2>
          <p className="text-sm text-gray-500 mt-1">Upload a corrected file containing only the fixed rejected rows.</p>
        </div>
      </div>

      <div
        className={`border-2 border-dashed rounded-xl p-6 text-center transition-colors ${
          isDragging ? "border-blue-500 bg-blue-50" : "border-blue-200 hover:border-blue-400 bg-blue-50/30"
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
            <div className="w-10 h-10 bg-blue-100 rounded-full flex items-center justify-center text-blue-600 mb-2">
              <FileIcon className="w-5 h-5" />
            </div>
            <p className="text-sm font-semibold text-gray-800">{file.name}</p>
            <p className="text-xs text-gray-500 mt-1">{(file.size / 1024).toFixed(1)} KB</p>
            
            <div className="flex items-center gap-3 mt-4">
              <Button variant="secondary" onClick={() => setFile(null)} disabled={uploading}>
                Cancel
              </Button>
              <Button onClick={handleUpload} loading={uploading}>
                Resubmit File
              </Button>
            </div>
          </div>
        ) : (
          <div className="flex flex-col items-center">
            <div className="w-10 h-10 bg-white rounded-full flex items-center justify-center text-blue-500 shadow-sm mb-3">
              <UploadCloudIcon className="w-5 h-5" />
            </div>
            <p className="text-sm font-semibold text-blue-900">Drag & drop your corrected file here</p>
            
            <input
              type="file"
              ref={fileInputRef}
              onChange={handleFileChange}
              accept=".csv, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet, application/vnd.ms-excel"
              className="hidden"
            />
            <Button variant="secondary" onClick={() => fileInputRef.current?.click()} className="mt-4">
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

export default function FeeUploadDetailsPage() {
  const router = useRouter();
  const params = useParams();
  const uploadId = params.id as string;
  
  const apiClient = useApiClient();
  
  const [summary, setSummary] = useState<FeeUploadResultDto | null>(null);
  const [errorRows, setErrorRows] = useState<FeeUploadRowDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [downloading, setDownloading] = useState(false);

  const fetchDetails = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getFeeUploadStatus(apiClient, uploadId);
      setSummary(res);
      
      if (res.rejectedRows > 0) {
        const rows = await getFeeUploadRows(apiClient, uploadId, "Rejected");
        setErrorRows(rows);
      } else {
        setErrorRows([]);
      }
    } catch {
      setError("Failed to load upload details.");
    } finally {
      setLoading(false);
    }
  }, [apiClient, uploadId]);

  useEffect(() => {
    fetchDetails();
  }, [fetchDetails]);

  const handleDownloadErrors = async () => {
    setDownloading(true);
    try {
      await downloadFeeUploadErrors(process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1", uploadId);
    } catch (err) {
      alert(err instanceof Error ? err.message : "Failed to download errors.");
    } finally {
      setDownloading(false);
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

  if (loading) {
    return (
      <div className="flex h-full items-center justify-center bg-[#FAFBFD]">
        <LoadingSpinner className="w-8 h-8 text-blue-500" />
      </div>
    );
  }

  if (error || !summary) {
    return (
      <div className="p-6">
        <div className="p-4 bg-red-50 text-red-700 rounded-lg">{error || "Upload not found."}</div>
        <Button variant="secondary" className="mt-4" onClick={() => router.push("/school/fee-upload")}>
          Back to History
        </Button>
      </div>
    );
  }

  return (
    <div className="flex flex-col min-h-full bg-[#FAFBFD] p-6 gap-6">
      <div className="flex items-center gap-4">
        <Button variant="secondary" onClick={() => router.push("/school/fee-upload")}>
          <ArrowLeftIcon className="w-4 h-4 mr-2" /> Back
        </Button>
        <div>
          <h1 className="text-2xl font-bold text-[#1B2A4A]">Upload Details</h1>
          <p className="text-gray-500 text-sm mt-1">{summary.fileName} • {formatIsoDate(summary.uploadedAt)}</p>
        </div>
        <div className="ml-auto">
          <Badge tone={getStatusTone(summary.status)} className="text-sm px-3 py-1">{summary.status}</Badge>
        </div>
      </div>

      <div className="grid grid-cols-4 gap-4">
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-5 flex flex-col justify-center items-center text-center">
          <p className="text-sm text-gray-500 mb-1">Total Rows</p>
          <p className="text-3xl font-bold text-[#1B2A4A]">{summary.totalRows}</p>
        </div>
        <div className="bg-white rounded-xl shadow-sm border border-emerald-100 p-5 flex flex-col justify-center items-center text-center bg-emerald-50/30">
          <p className="text-sm text-emerald-600 mb-1">Accepted</p>
          <p className="text-3xl font-bold text-emerald-600">{summary.acceptedRows}</p>
        </div>
        <div className="bg-white rounded-xl shadow-sm border border-rose-100 p-5 flex flex-col justify-center items-center text-center bg-rose-50/30">
          <p className="text-sm text-rose-600 mb-1">Rejected</p>
          <p className="text-3xl font-bold text-rose-600">{summary.rejectedRows}</p>
        </div>
        <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-5 flex flex-col justify-center items-center text-center">
          <p className="text-sm text-gray-500 mb-1">Status</p>
          <p className="text-xl font-bold text-[#1B2A4A]">{summary.status}</p>
        </div>
      </div>

      {summary.rejectedRows > 0 && (
        <div className="bg-white rounded-xl shadow-sm border border-rose-200 overflow-hidden flex flex-col flex-1 min-h-[300px]">
          <div className="p-4 border-b border-rose-100 bg-rose-50 flex items-center justify-between">
            <h2 className="text-lg font-bold text-rose-900">Rejected Rows ({summary.rejectedRows})</h2>
            <Button variant="secondary" onClick={handleDownloadErrors} disabled={downloading} className="gap-2 bg-white text-rose-700 hover:bg-rose-50 border-rose-200">
              <DownloadIcon className="w-4 h-4" /> Export Errors
            </Button>
          </div>
          <div className="flex-1 overflow-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-rose-50/50 border-b border-rose-100 text-[11px] uppercase tracking-wider text-rose-700/70">
                  <th className="px-4 py-3 font-semibold">Row</th>
                  <th className="px-4 py-3 font-semibold">Student Ref</th>
                  <th className="px-4 py-3 font-semibold">Fee Name</th>
                  <th className="px-4 py-3 font-semibold text-right">Amount (EGP)</th>
                  <th className="px-4 py-3 font-semibold text-red-600">Error Reason</th>
                </tr>
              </thead>
              <tbody className="text-sm divide-y divide-rose-50">
                {errorRows.map((row) => (
                  <tr key={row.rowNumber} className="hover:bg-rose-50/30">
                    <td className="px-4 py-3 text-gray-500">{row.rowNumber}</td>
                    <td className="px-4 py-3 font-medium text-gray-800">{row.studentRef || "-"}</td>
                    <td className="px-4 py-3 text-gray-700">{row.feeName || "-"}</td>
                    <td className="px-4 py-3 text-gray-700 text-right">{row.amountEGP?.toLocaleString() || "-"}</td>
                    <td className="px-4 py-3 text-rose-600 font-medium">{row.errorReason}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
      
      {summary.rejectedRows > 0 && (
        <ResubmitUploader uploadId={uploadId} onResubmitComplete={fetchDetails} />
      )}
    </div>
  );
}
