'use client'

import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { createApiClient, parseErrorBody, type ApiClient } from './client'
import type { AuthSession, AuthUser } from './types'

const STORAGE_KEY = 'tuition.auth.session'

interface LoginResponse {
  mfaRequired: boolean
  mfaToken: string
  otpChannel: string
  otpDestinationHint: string
  expiresInSeconds: number
  resendAvailableInSeconds: number
}

interface MfaVerifyResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: AuthUser
}

type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated'

interface AuthContextValue {
  status: AuthStatus
  user: AuthUser | null
  apiClient: ApiClient
  login: (username: string, password: string) => Promise<LoginResponse>
  verifyMfa: (mfaToken: string, code: string) => Promise<AuthUser>
  resendMfa: (mfaToken: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

function readStoredSession(): AuthSession | null {
  if (typeof window === 'undefined') return null
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as AuthSession) : null
  } catch {
    return null
  }
}

function writeStoredSession(session: AuthSession | null) {
  if (typeof window === 'undefined') return
  if (session) {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  } else {
    window.localStorage.removeItem(STORAGE_KEY)
  }
}

export interface AuthProviderProps {
  /** e.g. NEXT_PUBLIC_API_BASE_URL, without a trailing slash. */
  baseUrl: string
  children: React.ReactNode
}

// AuthController is mounted at {"/api/v1/auth", "/auth"} - every sub-route used
// below (mfa/verify, mfa/resend, refresh, logout, me) hangs off that /auth
// prefix, not off the api base directly.

/**
 * One AuthProvider for both portals (Bank Back-Office + School). The credentials
 * typed into the single /login page decide the role that comes back from
 * /mfa/verify; this provider just stores whatever session it's handed - it
 * doesn't know or care which portal a given user belongs to. Each (bank)/(school)
 * route group layout is responsible for checking `user.role` and redirecting.
 */
export function AuthProvider({ baseUrl, children }: AuthProviderProps) {
  const [session, setSession] = useState<AuthSession | null>(null)
  const [status, setStatus] = useState<AuthStatus>('loading')
  const sessionRef = useRef<AuthSession | null>(null)
  sessionRef.current = session

  const updateSession = useCallback((next: AuthSession | null) => {
    sessionRef.current = next
    setSession(next)
    writeStoredSession(next)
    setStatus(next ? 'authenticated' : 'unauthenticated')
  }, [])

  const refreshAccessToken = useCallback(async (): Promise<string | null> => {
    const current = sessionRef.current
    if (!current) return null

    try {
      const res = await fetch(`${baseUrl}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: current.refreshToken }),
      })
      if (!res.ok) return null

      const data = (await res.json()) as MfaVerifyResponse
      const next: AuthSession = {
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        tokenType: data.tokenType,
        expiresIn: data.expiresIn,
        user: data.user,
      }
      updateSession(next)
      return next.accessToken
    } catch {
      return null
    }
  }, [baseUrl, updateSession])

  const onSessionExpired = useCallback(() => {
    updateSession(null)
  }, [updateSession])

  const apiClient = useMemo(
    () =>
      createApiClient(baseUrl, {
        getAccessToken: () => sessionRef.current?.accessToken ?? null,
        refreshAccessToken,
        onSessionExpired,
      }),
    [baseUrl, refreshAccessToken, onSessionExpired]
  )

  useEffect(() => {
    const stored = readStoredSession()
    if (!stored) {
      setStatus('unauthenticated')
      return
    }

    sessionRef.current = stored
    setSession(stored)

    apiClient
      .get<AuthUser>('/auth/me')
      .then((user) => updateSession({ ...stored, user }))
      .catch(() => {
        // apiClient already attempted a refresh internally; if we're here, it failed.
        updateSession(null)
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const login = useCallback(
    async (username: string, password: string) => {
      const res = await fetch(`${baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      })
      if (!res.ok) {
        const err = await parseErrorBody(res)
        throw new Error(err.message)
      }
      return (await res.json()) as LoginResponse
    },
    [baseUrl]
  )

  const verifyMfa = useCallback(
    async (mfaToken: string, code: string) => {
      const res = await fetch(`${baseUrl}/auth/mfa/verify`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mfaToken, code }),
      })
      if (!res.ok) {
        const err = await parseErrorBody(res)
        throw new Error(err.message)
      }
      const data = (await res.json()) as MfaVerifyResponse
      updateSession({
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        tokenType: data.tokenType,
        expiresIn: data.expiresIn,
        user: data.user,
      })
      return data.user
    },
    [baseUrl, updateSession]
  )

  const resendMfa = useCallback(
    async (mfaToken: string) => {
      await fetch(`${baseUrl}/auth/mfa/resend`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ mfaToken }),
      })
    },
    [baseUrl]
  )

  const logout = useCallback(async () => {
    const current = sessionRef.current
    try {
      await fetch(`${baseUrl}/auth/logout`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(current ? { Authorization: `Bearer ${current.accessToken}` } : {}),
        },
        body: JSON.stringify({ refreshToken: current?.refreshToken }),
      })
    } catch {
      // best-effort - still clear the local session below
    }
    updateSession(null)
  }, [baseUrl, updateSession])

  const value: AuthContextValue = {
    status,
    user: session?.user ?? null,
    apiClient,
    login,
    verifyMfa,
    resendMfa,
    logout,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}

export function useApiClient(): ApiClient {
  return useAuth().apiClient
}
