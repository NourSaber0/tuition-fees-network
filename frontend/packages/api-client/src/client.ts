import type { ApiError } from './types'

export class ApiClientError extends Error implements ApiError {
  status: number
  code: string
  details?: unknown

  constructor(error: ApiError) {
    super(error.message)
    this.status = error.status
    this.code = error.code
    this.details = error.details
  }
}

export interface ApiClientHooks {
  getAccessToken: () => string | null
  /** Called once on a 401. Resolve with the new access token to retry, or null to give up. */
  refreshAccessToken: () => Promise<string | null>
  /** Called when refresh itself fails - clear the session and send the user to /login. */
  onSessionExpired: () => void
}

export interface ApiClient {
  get<T>(path: string): Promise<T>
  post<T>(path: string, body?: unknown): Promise<T>
  put<T>(path: string, body?: unknown): Promise<T>
  patch<T>(path: string, body?: unknown): Promise<T>
  delete<T>(path: string): Promise<T>
}

/** Normalizes the two error shapes currently in use across backend controllers (see types.ts). */
export async function parseErrorBody(res: Response): Promise<ApiError> {
  let body: unknown = null
  try {
    body = await res.json()
  } catch {
    // no JSON body
  }

  const b = (body ?? {}) as Record<string, unknown>
  const nestedError = b.error as Record<string, unknown> | string | undefined

  if (nestedError && typeof nestedError === 'object') {
    return {
      status: res.status,
      code: String(nestedError.code ?? 'UNKNOWN_ERROR'),
      message: String(nestedError.message ?? res.statusText),
      details: nestedError.details,
    }
  }

  return {
    status: res.status,
    code: typeof nestedError === 'string' ? nestedError : 'UNKNOWN_ERROR',
    message: typeof b.message === 'string' ? b.message : res.statusText,
    details: b.details,
  }
}

export function createApiClient(baseUrl: string, hooks: ApiClientHooks): ApiClient {
  async function request<T>(method: string, path: string, body?: unknown, isRetry = false): Promise<T> {
    const token = hooks.getAccessToken()
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (token) headers.Authorization = `Bearer ${token}`

    const res = await fetch(`${baseUrl}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })

    if (res.status === 401 && !isRetry) {
      const newToken = await hooks.refreshAccessToken()
      if (newToken) {
        return request<T>(method, path, body, true)
      }
      hooks.onSessionExpired()
      throw new ApiClientError({ status: 401, code: 'SESSION_EXPIRED', message: 'Session expired' })
    }

    if (!res.ok) {
      throw new ApiClientError(await parseErrorBody(res))
    }

    if (res.status === 204) return undefined as T
    return (await res.json()) as T
  }

  return {
    get: (path) => request('GET', path),
    post: (path, body) => request('POST', path, body),
    put: (path, body) => request('PUT', path, body),
    patch: (path, body) => request('PATCH', path, body),
    delete: (path) => request('DELETE', path),
  }
}
