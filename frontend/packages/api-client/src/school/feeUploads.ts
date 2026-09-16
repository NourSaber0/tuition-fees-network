import { ApiClient } from "../client";

export interface FeeUploadResultDto {
  uploadId: string;
  status: "Processing" | "Completed" | "Completed with Errors" | "Failed";
  totalRows: number;
  validRows: number;
  invalidRows: number;
  acceptedRows: number;
  rejectedRows: number;
  uploadedAt: string;
  fileName: string;
}

export interface FeeUploadRowDto {
  rowNumber: number;
  studentRef?: string;
  feeName?: string;
  category?: string;
  amountEGP?: number;
  dueDate?: string;
  status: "Valid" | "Invalid" | "Accepted" | "Rejected";
  errorReason?: string;
}

export interface FeeUploadListDto {
  uploadId: string;
  uploadedAt: string;
  fileName: string;
  status: "Processing" | "Completed" | "Completed with Errors" | "Failed";
  totalRows: number;
  acceptedRows: number;
  rejectedRows: number;
}

export async function uploadFees(client: ApiClient, file: File): Promise<{ uploadId: string; status: string }> {
  const formData = new FormData();
  formData.append("file", file);
  return client.post<{ uploadId: string; status: string }>("/fee-uploads", formData);
}

export async function getFeeUploadStatus(client: ApiClient, uploadId: string): Promise<FeeUploadResultDto> {
  return client.get<FeeUploadResultDto>(`/fee-uploads/${uploadId}`);
}

export async function getFeeUploadRows(client: ApiClient, uploadId: string, status?: string): Promise<FeeUploadRowDto[]> {
  const query = status ? `?status=${encodeURIComponent(status)}` : "";
  return client.get<FeeUploadRowDto[]>(`/fee-uploads/${uploadId}/rows${query}`);
}

export async function resubmitFeeUpload(client: ApiClient, uploadId: string, file: File): Promise<{ uploadId: string; status: string }> {
  const formData = new FormData();
  formData.append("file", file);
  return client.post<{ uploadId: string; status: string }>(`/fee-uploads/${uploadId}/resubmit`, formData);
}

export async function getFeeUploadHistory(client: ApiClient): Promise<FeeUploadListDto[]> {
  return client.get<FeeUploadListDto[]>("/fee-uploads");
}

export async function downloadFeeUploadTemplate(baseUrl: string): Promise<void> {
  const sessionStr = typeof window !== 'undefined' ? window.localStorage.getItem('tuition.auth.session') : null;
  const token = sessionStr ? (JSON.parse(sessionStr) as { accessToken: string }).accessToken : null;
  const headers: Record<string, string> = {};
  if (token) headers["Authorization"] = `Bearer ${token}`;

  // Use raw fetch for binary download
  const res = await fetch(`${baseUrl}/fee-uploads/template?format=csv`, {
    headers,
  });

  if (!res.ok) throw new Error("Failed to download template");
  
  const blob = await res.blob();
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = "fee_upload_template.csv";
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(url);
}

export async function downloadFeeUploadErrors(baseUrl: string, uploadId: string): Promise<void> {
  const sessionStr = typeof window !== 'undefined' ? window.localStorage.getItem('tuition.auth.session') : null;
  const token = sessionStr ? (JSON.parse(sessionStr) as { accessToken: string }).accessToken : null;
  const headers: Record<string, string> = {};
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const res = await fetch(`${baseUrl}/fee-uploads/${uploadId}/errors/export?format=csv`, {
    headers,
  });

  if (!res.ok) throw new Error("Failed to download error rows");
  
  const blob = await res.blob();
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `fee_upload_errors_${uploadId}.csv`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(url);
}
