/**
 * Mirrors PageResponse.java's envelope: { data, page, size, total, totalPages, pageSize }.
 * `pageSize` duplicates `size` (a Jackson getter on the backend record) - both are read here
 * since which one a given endpoint uses hasn't been fully reconciled yet.
 */
export interface PageResponse<T> {
  data: T[]
  page: number
  size: number
  pageSize?: number
  total: number
  totalPages: number
}

/**
 * Backend error shape is inconsistent across controllers as of Phase 0:
 * AuthController and friends return the nested ApiErrorResponse shape
 * ({ error: { code, message, details } }), but several newer controllers
 * (Settings, Reports, InstitutionManagement, Payment, CsvIngestion,
 * InstitutionDues) return a flat Map ({ error: "<CODE>", message }).
 * ApiError normalizes both - see parseErrorBody in client.ts.
 */
export interface ApiError {
  status: number
  code: string
  message: string
  details?: unknown
}

/**
 * These are the values actually serialized into BankUserDto.role - the bank
 * ones come from BankRole.getRoleId() (see BankRole.java), the school ones
 * are hardcoded in BankAuthServiceImpl.toSchoolUserDto(). They are NOT the
 * same as the ROLE_* Spring Security authority constants (UserRole.java on
 * the backend) - those only ever appear inside the JWT, never in this DTO.
 */
export type BankRoleId = 'bank-admin' | 'bank-operations' | 'bank-finance' | 'bank-reconciliation'
export type SchoolRoleId = 'school-admin' | 'school-finance'
export type UserRole = BankRoleId | SchoolRoleId

export function isBankRole(role: string): role is BankRoleId {
  return role.startsWith('bank-')
}

export function isSchoolRole(role: string): role is SchoolRoleId {
  return role.startsWith('school-')
}

export interface AuthUser {
  id: string
  name: string
  initials?: string
  email: string
  role: UserRole | string
  permissions: string[]
  mustChangePassword: boolean
  lastLoginAt?: string
  schoolId?: string | null
  schoolName?: string | null
}

export interface AuthSession {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: AuthUser
}
