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

export interface RequestOptions {
  headers?: Record<string, string>
}

export interface ApiClient {
  get<T>(path: string, options?: RequestOptions): Promise<T>
  post<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T>
  put<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T>
  patch<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T>
  delete<T>(path: string, options?: RequestOptions): Promise<T>
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
  async function request<T>(
    method: string,
    path: string,
    body?: unknown,
    options?: RequestOptions,
    isRetry = false
  ): Promise<T> {
    const token = hooks.getAccessToken()
    const headers: Record<string, string> = { 'Content-Type': 'application/json', ...(options?.headers ?? {}) }
    if (token && !headers.Authorization) headers.Authorization = `Bearer ${token}`

    const res = await fetch(`${baseUrl}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })

    if (res.status === 401 && !isRetry) {
      const newToken = await hooks.refreshAccessToken()
      if (newToken) {
        return request<T>(method, path, body, options, true)
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
    get: (path, options) => request('GET', path, undefined, options),
    post: (path, body, options) => request('POST', path, body, options),
    put: (path, body, options) => request('PUT', path, body, options),
    patch: (path, body, options) => request('PATCH', path, body, options),
    delete: (path, options) => request('DELETE', path, undefined, options),
  }
}
