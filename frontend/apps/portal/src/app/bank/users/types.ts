export interface BankUserSummaryDto {
  id: string;
  name: string;
  username: string;
  email: string;
  role: string;
  status: string;
  lastLogin: string | null;
  department: string | null;
  createdAt: string;
}

export interface CreateBankUserRequest {
  name: string;
  email: string;
  username?: string;
  role: string;
  department?: string;
}

export interface UpdateBankUserRequest {
  name?: string;
  email?: string;
  username?: string;
  role?: string;
  department?: string;
}

export interface UserStatusResponse {
  status: string;
}

export interface RolePermissionsDto {
  role: string;
  permissions: string[];
}
