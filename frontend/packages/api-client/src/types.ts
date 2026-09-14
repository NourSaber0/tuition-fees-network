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

export type UserRole =
  | 'ROLE_BACK_OFFICE'
  | 'ROLE_INSTITUTION_ADMIN'
  | 'ROLE_SCHOOL_ADMIN'
  | 'ROLE_SCHOOL_FINANCE'
  | 'ROLE_GUARDIAN'

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
