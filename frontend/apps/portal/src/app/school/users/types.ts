// ---------------------------------------------------------------------------
// SP-P10 – School Portal Users
// Endpoints:
//   GET    /users?search=&role=&status=&page=&pageSize=
//   POST   /users
//   GET    /users/{id}
//   PATCH  /users/{id}
//   POST   /users/{id}/deactivate
//   POST   /users/{id}/activate
//   GET    /roles
// ---------------------------------------------------------------------------

export interface SchoolUserSummaryDto {
  id: string;
  name: string;
  email: string;
  role: string;
  status: string;
  lastLogin: string | null;
  createdAt: string;
}

export interface CreateSchoolUserRequest {
  name: string;
  email: string;
  role: string;
  password?: string;
}

export interface UpdateSchoolUserRequest {
  name?: string;
  email?: string;
  role?: string;
}

export interface UserStatusResponse {
  status: string;
}

export interface RolePermissionsDto {
  role: string;
  permissions: string[];
}

export interface UserFilters {
  search: string;
  role: string;
  status: string;
}

