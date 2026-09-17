import { useState, useRef, useEffect } from 'react'
import {
  LockIcon, MailIcon, EyeIcon, XCircleIcon, CheckCircleIcon,
  KeyIcon, ChevronLeftIcon, CheckIcon,
} from '../components/Icons'
import type { UserRole } from '../Portal'

type LoginState = 'credentials' | 'mfa' | 'forgot' | 'sent' | 'reset' | 'reset-success'
interface Props { onAuthenticated: (role: UserRole) => void }

/* ── Demo accounts — role is determined server-side in production ── */
const DEMO_ACCOUNTS: Record<string, { password: string; role: UserRole }> = {
  admin:   { password: 'CIB@2026', role: 'bank-admin'          },
  ops:     { password: 'CIB@2026', role: 'bank-operations'     },
  finance: { password: 'CIB@2026', role: 'bank-finance'        },
  recon:   { password: 'CIB@2026', role: 'bank-reconciliation' },
}

/* ── Password requirements ── */
const PASS_REQS = [
  { label: 'At least 8 characters',  test: (p: string) => p.length >= 8 },
  { label: 'Uppercase letter (A–Z)', test: (p: string) => /[A-Z]/.test(p) },
  { label: 'Lowercase letter (a–z)', test: (p: string) => /[a-z]/.test(p) },
  { label: 'Number (0–9)',            test: (p: string) => /[0-9]/.test(p) },
  { label: 'Special character',      test: (p: string) => /[^A-Za-z0-9]/.test(p) },
]
const STRENGTH_LABEL = ['', 'Weak', 'Weak', 'Fair', 'Good', 'Strong']
const STRENGTH_COLOR = ['', '#EF4444', '#EF4444', '#F59E0B', '#22C55E', '#003087']

/* ── Shared sub-components at module scope (prevents remount on re-render) ── */
const S_BASE  = { borderColor: '#DDE4EE', background: '#FAFBFD' }
const S_FOCUS = { borderColor: '#003087', background: '#FAFBFD', boxShadow: '0 0 0 3px rgba(0,48,135,0.10)' }

interface IFProps {
  type?: string; value: string; onChange: (v: string) => void
  onKeyDown?: (e: React.KeyboardEvent) => void; placeholder: string
  icon?: React.ComponentType<React.SVGProps<SVGSVGElement>>; right?: React.ReactNode
}
function InputField({ type = 'text', value, onChange, onKeyDown, placeholder, icon: Icon, right }: IFProps) {
  const [focused, setFocused] = useState(false)
  return (
    <div className="relative">
      {Icon && <Icon className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-300 pointer-events-none" />}
      <input
        type={type} value={value} placeholder={placeholder}
        onChange={e => onChange(e.target.value)}
        onKeyDown={onKeyDown}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        className={`w-full border rounded-lg text-sm text-gray-700 placeholder-gray-300 focus:outline-none transition-colors py-2.5 ${Icon ? 'pl-9' : 'px-3'} ${right ? 'pr-10' : 'pr-4'}`}
        style={focused ? S_FOCUS : S_BASE}
      />
      {right && <div className="absolute right-3 top-1/2 -translate-y-1/2">{right}</div>}
    </div>
  )
}

function PrimaryBtn({ label, onClick, loading, disabled }: { label: string; onClick: () => void; loading?: boolean; disabled?: boolean }) {
  const off = loading || !!disabled
  return (
    <button onClick={onClick} disabled={off}
      className="w-full font-semibold py-2.5 rounded-lg text-sm text-white transition-all flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
      style={{ background: '#003087' }}
      onMouseEnter={e => { if (!off) (e.currentTarget as HTMLButtonElement).style.background = '#002060' }}
      onMouseLeave={e => { if (!off) (e.currentTarget as HTMLButtonElement).style.background = '#003087' }}
    >
      {loading
        ? <><span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" /><span>{label}</span></>
        : label}
    </button>
  )
}

function ErrorBanner({ msg }: { msg: string }) {
  return (
    <div className="flex items-center gap-2 rounded-lg px-3 py-2.5 mb-4 text-sm" style={{ background: '#FEF2F2', border: '1px solid #FCA5A5', color: '#B91C1C' }}>
      <XCircleIcon className="w-4 h-4 shrink-0" />{msg}
    </div>
  )
}

function SuccessBanner({ msg }: { msg: string }) {
  return (
    <div className="flex items-center gap-2 rounded-lg px-3 py-2.5 mb-4 text-sm" style={{ background: '#ECFDF5', border: '1px solid #6EE7B7', color: '#065F46' }}>
      <CheckCircleIcon className="w-4 h-4 shrink-0" />{msg}
    </div>
  )
}

function AuthCard({ children }: { children: React.ReactNode }) {
  return (
    <div className="rounded-2xl p-8" style={{ background: '#FFFFFF', border: '1px solid #DDE4EE', boxShadow: '0 4px 24px rgba(0,48,135,0.07)' }}>
      {children}
    </div>
  )
}

function FieldLabel({ children }: { children: string }) {
  return (
    <label className="block text-[11px] font-semibold uppercase tracking-wider mb-1.5" style={{ color: '#6B7A8D' }}>
      {children}
    </label>
  )
}

function BackButton({ onClick, label = 'Back to sign in' }: { onClick: () => void; label?: string }) {
  return (
    <button onClick={onClick}
      className="flex items-center gap-1 text-xs mb-6 transition-colors" style={{ color: '#aab5c4' }}
      onMouseEnter={e => (e.currentTarget.style.color = '#6B7A8D')}
      onMouseLeave={e => (e.currentTarget.style.color = '#aab5c4')}
    >
      <ChevronLeftIcon className="w-3.5 h-3.5" />{label}
    </button>
  )
}

/* ── Main component ── */
export default function Login({ onAuthenticated }: Props) {
  const [state, setState]               = useState<LoginState>('credentials')
  const [username, setUsername]         = useState('')
  const [password, setPassword]         = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [forgotEmail, setForgotEmail]   = useState('')
  const [newPassword, setNewPassword]   = useState('')
  const [confirmPass, setConfirmPass]   = useState('')
  const [showNewPw, setShowNewPw]       = useState(false)
  const [showConfPw, setShowConfPw]     = useState(false)
  const [otp, setOtp]                   = useState(['', '', '', '', '', ''])
  const [error, setError]               = useState('')
  const [loading, setLoading]           = useState(false)
  const [countdown, setCountdown]       = useState(60)
  const [otpExpired, setOtpExpired]     = useState(false)
  const [resendMsg, setResendMsg]       = useState('')
  const [loggedInRole, setLoggedInRole] = useState<UserRole>('bank-admin')
  const otpRefs  = useRef<(HTMLInputElement | null)[]>([])
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  /* ── Countdown ── */
  const startCountdown = () => {
    if (timerRef.current) clearInterval(timerRef.current)
    setCountdown(60); setOtpExpired(false)
    timerRef.current = setInterval(() => {
      setCountdown(prev => {
        if (prev <= 1) { clearInterval(timerRef.current!); setOtpExpired(true); return 0 }
        return prev - 1
      })
    }, 1000)
  }
  useEffect(() => {
    if (state === 'mfa') { setResendMsg(''); startCountdown() }
    return () => { if (timerRef.current) clearInterval(timerRef.current) }
  }, [state])
  const fmt = (s: number) => `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`

  /* ── Password strength ── */
  const reqResults = PASS_REQS.map(r => r.test(newPassword))
  const strength   = reqResults.filter(Boolean).length

  /* ── Handlers ── */
  const handleLogin = async () => {
    if (!username || !password) { setError('Please enter your username and password.'); return }
    const account = DEMO_ACCOUNTS[username.toLowerCase()]
    if (!account || account.password !== password) {
      setError('Incorrect username or password. Please try again.')
      return
    }
    setError(''); setLoggedInRole(account.role); setLoading(true)
    await new Promise(r => setTimeout(r, 900))
    setLoading(false); setState('mfa')
  }

  const handleOtpChange = (i: number, v: string) => {
    if (!/^\d*$/.test(v)) return
    const next = [...otp]; next[i] = v.slice(-1); setOtp(next); setError('')
    if (v && i < 5) otpRefs.current[i + 1]?.focus()
  }
  const handleOtpKeyDown = (i: number, e: React.KeyboardEvent) => {
    if (e.key === 'Backspace' && !otp[i] && i > 0) otpRefs.current[i - 1]?.focus()
  }
  const handleOtpPaste = (e: React.ClipboardEvent, startIdx: number) => {
    e.preventDefault()
    const digits = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, 6)
    if (!digits) return
    const next = [...otp]
    for (let j = 0; j < digits.length; j++) { if (startIdx + j < 6) next[startIdx + j] = digits[j] }
    setOtp(next); otpRefs.current[Math.min(startIdx + digits.length, 5)]?.focus(); setError('')
  }
  const handleVerifyMFA = async () => {
    const code = otp.join('')
    if (code.length < 6) { setError('Please enter the complete 6-digit code.'); return }
    setError(''); setLoading(true)
    await new Promise(r => setTimeout(r, 700))
    setLoading(false)
    if (code !== '123456') {
      setError('Invalid verification code. Please try again.')
      setOtp(['', '', '', '', '', '']); otpRefs.current[0]?.focus(); return
    }
    onAuthenticated(loggedInRole)
  }
  const handleResend = async () => {
    setOtp(['', '', '', '', '', '']); setError(''); setResendMsg(''); setLoading(true)
    await new Promise(r => setTimeout(r, 600))
    setLoading(false)
    setResendMsg('A new verification code has been sent to your registered device.')
    startCountdown(); otpRefs.current[0]?.focus()
  }
  const handleForgot = async () => {
    if (!forgotEmail) { setError('Please enter your email address.'); return }
    setError(''); setLoading(true)
    await new Promise(r => setTimeout(r, 800))
    setLoading(false); setState('sent')
  }
  const handleReset = async () => {
    if (!newPassword) { setError('Please enter a new password.'); return }
    if (strength < 3) { setError('Password is too weak. Please satisfy at least 3 requirements.'); return }
    if (newPassword !== confirmPass) { setError('Passwords do not match. Please re-enter.'); return }
    setError(''); setLoading(true)
    await new Promise(r => setTimeout(r, 900))
    setLoading(false); setState('reset-success')
  }

  const otpComplete = otp.every(d => d !== '')

  return (
    <div className="min-h-full flex" style={{ background: '#F4F6F9' }}>

      {/* ── Left branding panel ── */}
      <div className="hidden lg:flex w-[44%] flex-col relative overflow-hidden" style={{ background: '#003087' }}>
        <div className="absolute inset-0 opacity-[0.035]" style={{
          backgroundImage: 'repeating-linear-gradient(45deg,#F7941D 0,#F7941D 1px,transparent 0,transparent 50%)',
          backgroundSize: '28px 28px',
        }} />
        <div className="absolute bottom-0 left-0 right-0 h-2/5" style={{ background: 'linear-gradient(to top, #002060, transparent)' }} />
        <div className="relative z-10 flex flex-col h-full p-12">
          <div className="mb-auto" style={{ width: 'fit-content' }}>
            <img src="/src/imports/cib.png" alt="CIB" style={{ height: 48, width: 'auto', objectFit: 'contain', position: 'static' }} />
          </div>
          <div className="mb-auto mt-12">
            <h1 className="text-[2.1rem] font-light text-white leading-tight mb-4">
              School Payments<br />
              <span className="font-bold" style={{ color: '#F7941D' }}>Back Office Portal</span>
            </h1>
            <p className="text-sm leading-relaxed max-w-xs" style={{ color: 'rgba(255,255,255,0.52)' }}>
              Manage school registrations, monitor payments, reconcile transactions, and oversee installment plans across your network.
            </p>
          </div>
          <div className="grid grid-cols-3 gap-3">
            {[
              { label: 'Active Schools',   value: '48'       },
              { label: 'Daily Collection', value: 'EGP 2.4M' },
              { label: 'Success Rate',     value: '98.2%'    },
            ].map(s => (
              <div key={s.label} className="rounded-xl p-4" style={{ background: 'rgba(255,255,255,0.07)', border: '1px solid rgba(255,255,255,0.10)' }}>
                <div className="font-bold text-xl" style={{ color: '#F7941D' }}>{s.value}</div>
                <div className="text-xs mt-1" style={{ color: 'rgba(255,255,255,0.42)' }}>{s.label}</div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* ── Right form panel ── */}
      <div className="flex-1 flex items-center justify-center p-8">
        <div className="w-full max-w-sm">

          {/* Mobile logo */}
          <div className="flex lg:hidden items-center gap-2 justify-center mb-8">
            <img src="/src/imports/cib.png" alt="CIB" style={{ height: 36, width: 'auto', objectFit: 'contain', position: 'static' }} />
            <span className="font-bold text-base" style={{ color: '#003087' }}>CIB Back Office</span>
          </div>

          {/* ── Credentials ── */}
          {state === 'credentials' && (
            <AuthCard>
              <div className="mb-7">
                <h2 className="text-xl font-bold" style={{ color: '#003087' }}>Sign in to your account</h2>
                <p className="text-sm mt-1" style={{ color: '#6B7A8D' }}>Authorized CIB personnel only</p>
              </div>
              {error && <ErrorBanner msg={error} />}
              <div className="space-y-4">
                <div>
                  <FieldLabel>Username or Email</FieldLabel>
                  <InputField type="text" value={username} onChange={v => { setUsername(v); setError('') }}
                    onKeyDown={e => e.key === 'Enter' && handleLogin()}
                    placeholder="you@cibeg.com" icon={MailIcon} />
                </div>
                <div>
                  <FieldLabel>Password</FieldLabel>
                  <InputField
                    type={showPassword ? 'text' : 'password'}
                    value={password} onChange={v => { setPassword(v); setError('') }}
                    onKeyDown={e => e.key === 'Enter' && handleLogin()}
                    placeholder="••••••••" icon={LockIcon}
                    right={
                      <button type="button" onClick={() => setShowPassword(!showPassword)} style={{ color: '#aab5c4' }}
                        onMouseEnter={e => (e.currentTarget.style.color = '#6B7A8D')}
                        onMouseLeave={e => (e.currentTarget.style.color = '#aab5c4')}
                      ><EyeIcon className="w-4 h-4" /></button>
                    }
                  />
                </div>
                <div className="flex items-center justify-between">
                  <label className="flex items-center gap-2 cursor-pointer">
                    <input type="checkbox" className="w-3.5 h-3.5" style={{ accentColor: '#003087' }} />
                    <span className="text-xs" style={{ color: '#6B7A8D' }}>Remember me</span>
                  </label>
                  <button onClick={() => { setState('forgot'); setError('') }} className="text-xs font-semibold transition-colors" style={{ color: '#003087' }}
                    onMouseEnter={e => (e.currentTarget.style.color = '#F7941D')}
                    onMouseLeave={e => (e.currentTarget.style.color = '#003087')}
                  >Forgot password?</button>
                </div>
                <PrimaryBtn label={loading ? 'Signing in…' : 'Sign In'} onClick={handleLogin} loading={loading} />
              </div>
              <p className="text-center text-[10px] mt-5" style={{ color: '#C4CDDA' }}>
                Demo: <span className="font-mono">admin · ops · finance · recon</span> / <span className="font-mono">CIB@2026</span>
              </p>
            </AuthCard>
          )}

          {/* ── MFA / Two-factor ── */}
          {state === 'mfa' && (
            <AuthCard>
              <BackButton onClick={() => { setState('credentials'); setOtp(['','','','','','']); setError(''); setResendMsg('') }} label="Back" />
              <div className="mb-7">
                <div className="w-12 h-12 rounded-xl flex items-center justify-center mb-4" style={{ background: '#EBF1FB' }}>
                  <KeyIcon className="w-6 h-6" style={{ color: '#003087' }} />
                </div>
                <h2 className="text-xl font-bold" style={{ color: '#003087' }}>Two-factor verification</h2>
                <p className="text-sm mt-1" style={{ color: '#6B7A8D' }}>Enter the 6-digit code sent to your registered device</p>
                <p className="text-xs mt-2 font-mono" style={{ color: '#aab5c4' }}>Demo code: 123456</p>
              </div>
              {error && <ErrorBanner msg={error} />}
              {otpExpired && !error && (
                <div className="flex items-center gap-2 rounded-lg px-3 py-2.5 mb-4 text-sm" style={{ background: '#FEF2F2', border: '1px solid #FCA5A5', color: '#B91C1C' }}>
                  <XCircleIcon className="w-4 h-4 shrink-0" />
                  Verification code expired. Please request a new code.
                </div>
              )}
              {resendMsg && !error && <SuccessBanner msg={resendMsg} />}
              <div className="grid grid-cols-6 gap-1.5 mb-6">
                {otp.map((digit, i) => (
                  <input key={i}
                    ref={el => { otpRefs.current[i] = el }}
                    type="text" inputMode="numeric" maxLength={1} value={digit}
                    onChange={e => handleOtpChange(i, e.target.value)}
                    onKeyDown={e => handleOtpKeyDown(i, e)}
                    onPaste={e => handleOtpPaste(e, i)}
                    disabled={otpExpired}
                    className="w-full h-12 text-center text-lg font-bold border rounded-lg focus:outline-none transition-all disabled:opacity-40 disabled:cursor-not-allowed"
                    style={{
                      borderColor: digit ? '#003087' : '#DDE4EE',
                      color: '#003087',
                      background: digit ? '#EBF1FB' : '#FAFBFD',
                      boxShadow: digit ? '0 0 0 2px rgba(0,48,135,0.12)' : 'none',
                    }}
                  />
                ))}
              </div>
              <PrimaryBtn label={loading ? 'Verifying…' : 'Verify & Sign In'} onClick={handleVerifyMFA} loading={loading} disabled={!otpComplete || otpExpired} />
              <div className="w-full text-center mt-4 min-h-[20px]">
                {otpExpired
                  ? <button onClick={handleResend} disabled={loading} className="text-xs font-semibold transition-colors disabled:opacity-50" style={{ color: '#003087' }}
                      onMouseEnter={e => (e.currentTarget.style.color = '#F7941D')}
                      onMouseLeave={e => (e.currentTarget.style.color = '#003087')}
                    >Resend OTP</button>
                  : <span className="text-xs font-semibold tabular-nums" style={{ color: '#aab5c4' }}>
                      Resend OTP <span style={{ color: '#6B7A8D' }}>({fmt(countdown)})</span>
                    </span>
                }
              </div>
            </AuthCard>
          )}

          {/* ── Forgot password ── */}
          {state === 'forgot' && (
            <AuthCard>
              <BackButton onClick={() => { setState('credentials'); setError('') }} />
              <div className="mb-7">
                <h2 className="text-xl font-bold" style={{ color: '#003087' }}>Reset your password</h2>
                <p className="text-sm mt-1" style={{ color: '#6B7A8D' }}>Enter your email and we'll send a reset link</p>
              </div>
              {error && <ErrorBanner msg={error} />}
              <div className="space-y-4">
                <div>
                  <FieldLabel>Email Address</FieldLabel>
                  <InputField type="email" value={forgotEmail} onChange={v => { setForgotEmail(v); setError('') }}
                    onKeyDown={e => e.key === 'Enter' && handleForgot()}
                    placeholder="you@cibeg.com" icon={MailIcon} />
                </div>
                <PrimaryBtn label={loading ? 'Sending…' : 'Send Reset Link'} onClick={handleForgot} loading={loading} />
              </div>
            </AuthCard>
          )}

          {/* ── Sent — check email ── */}
          {state === 'sent' && (
            <AuthCard>
              <div className="text-center">
                <div className="w-14 h-14 rounded-2xl flex items-center justify-center mx-auto mb-5" style={{ background: '#ECFDF5' }}>
                  <CheckCircleIcon className="w-7 h-7" style={{ color: '#059669' }} />
                </div>
                <h2 className="text-xl font-bold mb-2" style={{ color: '#003087' }}>Check your email</h2>
                <p className="text-sm mb-6" style={{ color: '#6B7A8D' }}>
                  A reset link was sent to <strong className="text-gray-700">{forgotEmail}</strong>
                </p>
                <button
                  onClick={() => { setState('reset'); setError('') }}
                  className="w-full font-semibold py-2.5 rounded-lg text-sm transition-all mb-3"
                  style={{ background: '#EBF1FB', color: '#003087', border: '1px solid rgba(0,48,135,0.15)' }}
                  onMouseEnter={e => (e.currentTarget.style.background = '#D6E4F5')}
                  onMouseLeave={e => (e.currentTarget.style.background = '#EBF1FB')}
                >
                  Simulate clicking reset link →
                </button>
                <button onClick={() => setState('credentials')} className="text-sm font-semibold transition-colors" style={{ color: '#003087' }}>
                  Back to sign in
                </button>
              </div>
            </AuthCard>
          )}

          {/* ── Reset password form ── */}
          {state === 'reset' && (
            <AuthCard>
              <BackButton onClick={() => { setState('credentials'); setError('') }} />
              <div className="mb-6">
                <div className="w-12 h-12 rounded-xl flex items-center justify-center mb-4" style={{ background: '#EBF1FB' }}>
                  <LockIcon className="w-6 h-6" style={{ color: '#003087' }} />
                </div>
                <h2 className="text-xl font-bold" style={{ color: '#003087' }}>Set new password</h2>
                <p className="text-sm mt-1" style={{ color: '#6B7A8D' }}>Choose a new secure password for your account</p>
              </div>
              {error && <ErrorBanner msg={error} />}
              <div className="space-y-4">

                {/* New password */}
                <div>
                  <FieldLabel>New Password</FieldLabel>
                  <InputField
                    type={showNewPw ? 'text' : 'password'}
                    value={newPassword} onChange={v => { setNewPassword(v); setError('') }}
                    placeholder="New password" icon={LockIcon}
                    right={
                      <button type="button" onClick={() => setShowNewPw(!showNewPw)} style={{ color: '#aab5c4' }}
                        onMouseEnter={e => (e.currentTarget.style.color = '#6B7A8D')}
                        onMouseLeave={e => (e.currentTarget.style.color = '#aab5c4')}
                      ><EyeIcon className="w-4 h-4" /></button>
                    }
                  />
                  {newPassword && (
                    <div className="mt-2">
                      <div className="flex gap-1 mb-1">
                        {[1,2,3,4,5].map(i => (
                          <div key={i} className="flex-1 h-1.5 rounded-full transition-all"
                            style={{ background: i <= strength ? STRENGTH_COLOR[strength] : '#E5E7EB' }} />
                        ))}
                      </div>
                      <span className="text-[10px] font-semibold" style={{ color: STRENGTH_COLOR[strength] }}>
                        {STRENGTH_LABEL[strength]}
                      </span>
                    </div>
                  )}
                </div>

                {/* Confirm password */}
                <div>
                  <FieldLabel>Confirm New Password</FieldLabel>
                  <InputField
                    type={showConfPw ? 'text' : 'password'}
                    value={confirmPass} onChange={v => { setConfirmPass(v); setError('') }}
                    onKeyDown={e => e.key === 'Enter' && handleReset()}
                    placeholder="Confirm password" icon={LockIcon}
                    right={
                      <button type="button" onClick={() => setShowConfPw(!showConfPw)} style={{ color: '#aab5c4' }}
                        onMouseEnter={e => (e.currentTarget.style.color = '#6B7A8D')}
                        onMouseLeave={e => (e.currentTarget.style.color = '#aab5c4')}
                      ><EyeIcon className="w-4 h-4" /></button>
                    }
                  />
                  {confirmPass && newPassword !== confirmPass && (
                    <p className="text-[11px] mt-1" style={{ color: '#EF4444' }}>Passwords do not match</p>
                  )}
                  {confirmPass && newPassword === confirmPass && (
                    <p className="text-[11px] mt-1" style={{ color: '#059669' }}>Passwords match ✓</p>
                  )}
                </div>

                {/* Requirements checklist */}
                <div className="rounded-lg p-3" style={{ background: '#F8FAFD', border: '1px solid #E8EDF5' }}>
                  <p className="text-[10px] font-semibold uppercase tracking-wider mb-2" style={{ color: '#6B7A8D' }}>
                    Password requirements
                  </p>
                  <div className="space-y-1.5">
                    {PASS_REQS.map((req, idx) => {
                      const met = reqResults[idx]
                      return (
                        <div key={idx} className="flex items-center gap-2 text-xs" style={{ color: met ? '#059669' : '#9CA3AF' }}>
                          <span className="w-4 h-4 rounded-full flex items-center justify-center shrink-0"
                            style={{ background: met ? '#ECFDF5' : '#F3F4F6', border: `1px solid ${met ? '#A7F3D0' : '#E5E7EB'}` }}>
                            {met
                              ? <CheckIcon className="w-2.5 h-2.5" style={{ color: '#059669' }} />
                              : <span className="w-1.5 h-1.5 rounded-full" style={{ background: '#D1D5DB' }} />}
                          </span>
                          {req.label}
                        </div>
                      )
                    })}
                  </div>
                </div>

                <PrimaryBtn label={loading ? 'Updating password…' : 'Set New Password'} onClick={handleReset} loading={loading} disabled={!newPassword || !confirmPass} />
              </div>
            </AuthCard>
          )}

          {/* ── Reset success ── */}
          {state === 'reset-success' && (
            <AuthCard>
              <div className="text-center">
                <div className="w-14 h-14 rounded-2xl flex items-center justify-center mx-auto mb-5" style={{ background: '#ECFDF5' }}>
                  <CheckCircleIcon className="w-7 h-7" style={{ color: '#059669' }} />
                </div>
                <h2 className="text-xl font-bold mb-2" style={{ color: '#003087' }}>Password reset successfully</h2>
                <p className="text-sm mb-6" style={{ color: '#6B7A8D' }}>
                  Your password has been updated. You can now sign in with your new credentials.
                </p>
                <PrimaryBtn
                  label="Sign in with new password"
                  onClick={() => {
                    setState('credentials')
                    setNewPassword(''); setConfirmPass('')
                    setForgotEmail(''); setError('')
                  }}
                />
              </div>
            </AuthCard>
          )}

          <p className="text-center text-[11px] mt-6" style={{ color: '#aab5c4' }}>
            © {new Date().getFullYear()} Commercial International Bank Egypt S.A.E · Authorized Access Only
          </p>
        </div>
      </div>
    </div>
  )
}
