"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth, isBankRole, isSchoolRole } from "@tuition/api-client";
import {
  LockIcon,
  MailIcon,
  EyeIcon,
  XCircleIcon,
  CheckCircleIcon,
  KeyIcon,
  ChevronLeftIcon,
  CheckIcon,
  BankIcon,
} from "@tuition/ui";

type LoginState = "credentials" | "mfa" | "forgot" | "sent" | "reset" | "reset-success";

const PASS_REQS = [
  { label: "At least 8 characters", test: (p: string) => p.length >= 8 },
  { label: "Uppercase letter (A-Z)", test: (p: string) => /[A-Z]/.test(p) },
  { label: "Lowercase letter (a-z)", test: (p: string) => /[a-z]/.test(p) },
  { label: "Number (0-9)", test: (p: string) => /[0-9]/.test(p) },
  { label: "Special character", test: (p: string) => /[^A-Za-z0-9]/.test(p) },
];
const STRENGTH_LABEL = ["", "Weak", "Weak", "Fair", "Good", "Strong"];
const STRENGTH_COLOR = ["", "#EF4444", "#EF4444", "#F59E0B", "#22C55E", "#003087"];

function InputField({
  type = "text",
  value,
  onChange,
  onKeyDown,
  placeholder,
  icon: Icon,
  right,
}: {
  type?: string;
  value: string;
  onChange: (v: string) => void;
  onKeyDown?: (e: React.KeyboardEvent) => void;
  placeholder: string;
  icon?: React.ComponentType<React.SVGProps<SVGSVGElement>>;
  right?: React.ReactNode;
}) {
  return (
    <div className="relative">
      {Icon && <Icon className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-300 pointer-events-none" />}
      <input
        type={type}
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={onKeyDown}
        className={`w-full border rounded-lg text-sm text-gray-700 placeholder-gray-300 focus:outline-none focus:border-[var(--cib-blue)] focus:ring-2 focus:ring-[var(--cib-blue)]/10 transition-colors py-2.5 ${Icon ? "pl-9" : "px-3"} ${right ? "pr-10" : "pr-4"}`}
        style={{ borderColor: "var(--cib-border)", background: "#FAFBFD" }}
      />
      {right && <div className="absolute right-3 top-1/2 -translate-y-1/2">{right}</div>}
    </div>
  );
}

function PrimaryBtn({
  label,
  onClick,
  loading,
  disabled,
  type = "button",
}: {
  label: string;
  onClick?: () => void;
  loading?: boolean;
  disabled?: boolean;
  type?: "button" | "submit";
}) {
  const off = loading || !!disabled;
  return (
    <button
      type={type}
      onClick={onClick}
      disabled={off}
      className="w-full font-semibold py-2.5 rounded-lg text-sm text-white transition-all flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
      style={{ background: "var(--cib-blue)" }}
    >
      {loading ? (
        <>
          <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
          <span>{label}</span>
        </>
      ) : (
        label
      )}
    </button>
  );
}

function ErrorBanner({ msg }: { msg: string }) {
  return (
    <div
      className="flex items-center gap-2 rounded-lg px-3 py-2.5 mb-4 text-sm"
      style={{ background: "#FEF2F2", border: "1px solid #FCA5A5", color: "#B91C1C" }}
    >
      <XCircleIcon className="w-4 h-4 shrink-0" />
      {msg}
    </div>
  );
}

function SuccessBanner({ msg }: { msg: string }) {
  return (
    <div
      className="flex items-center gap-2 rounded-lg px-3 py-2.5 mb-4 text-sm"
      style={{ background: "#ECFDF5", border: "1px solid #6EE7B7", color: "#065F46" }}
    >
      <CheckCircleIcon className="w-4 h-4 shrink-0" />
      {msg}
    </div>
  );
}

function AuthCard({ children }: { children: React.ReactNode }) {
  return (
    <div
      className="rounded-2xl p-8"
      style={{ background: "#FFFFFF", border: "1px solid var(--cib-border)", boxShadow: "0 4px 24px rgba(0,48,135,0.07)" }}
    >
      {children}
    </div>
  );
}

function FieldLabel({ children }: { children: string }) {
  return (
    <label className="block text-[11px] font-semibold uppercase tracking-wider mb-1.5" style={{ color: "var(--cib-text-muted)" }}>
      {children}
    </label>
  );
}

function BackButton({ onClick, label = "Back to sign in" }: { onClick: () => void; label?: string }) {
  return (
    <button onClick={onClick} className="flex items-center gap-1 text-xs mb-6 transition-colors" style={{ color: "#aab5c4" }}>
      <ChevronLeftIcon className="w-3.5 h-3.5" />
      {label}
    </button>
  );
}

export default function LoginPage() {
  const router = useRouter();
  const { login, verifyMfa, resendMfa, trustDevice, forgotPassword, resetPassword } = useAuth();

  const [state, setState] = useState<LoginState>("credentials");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [rememberDevice, setRememberDevice] = useState(false);

  const [forgotEmail, setForgotEmail] = useState("");
  const [resetToken, setResetToken] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPass, setConfirmPass] = useState("");
  const [showNewPw, setShowNewPw] = useState(false);
  const [showConfPw, setShowConfPw] = useState(false);

  const [otp, setOtp] = useState(["", "", "", "", "", ""]);
  const [mfaToken, setMfaToken] = useState("");
  const [otpHint, setOtpHint] = useState("");
  const [countdown, setCountdown] = useState(60);
  const [otpExpired, setOtpExpired] = useState(false);
  const [resendMsg, setResendMsg] = useState("");

  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const otpRefs = useRef<(HTMLInputElement | null)[]>([]);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const startCountdown = () => {
    if (timerRef.current) clearInterval(timerRef.current);
    setCountdown(60);
    setOtpExpired(false);
    timerRef.current = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          clearInterval(timerRef.current!);
          setOtpExpired(true);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
  };

  useEffect(() => {
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, []);

  const fmt = (s: number) => `${String(Math.floor(s / 60)).padStart(2, "0")}:${String(s % 60).padStart(2, "0")}`;

  const reqResults = PASS_REQS.map((r) => r.test(newPassword));
  const strength = reqResults.filter(Boolean).length;
  const otpComplete = otp.every((d) => d !== "");

  async function handleLogin() {
    if (!username || !password) {
      setError("Please enter your username and password.");
      return;
    }
    setError("");
    setLoading(true);
    try {
      const res = await login(username, password);
      setMfaToken(res.mfaToken);
      setOtpHint(res.otpDestinationHint);
      setResendMsg("");
      startCountdown();
      setState("mfa");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Incorrect username or password. Please try again.");
    } finally {
      setLoading(false);
    }
  }

  function handleOtpChange(i: number, v: string) {
    if (!/^\d*$/.test(v)) return;
    const next = [...otp];
    next[i] = v.slice(-1);
    setOtp(next);
    setError("");
    if (v && i < 5) otpRefs.current[i + 1]?.focus();
  }
  function handleOtpKeyDown(i: number, e: React.KeyboardEvent) {
    if (e.key === "Backspace" && !otp[i] && i > 0) otpRefs.current[i - 1]?.focus();
  }
  function handleOtpPaste(e: React.ClipboardEvent, startIdx: number) {
    e.preventDefault();
    const digits = e.clipboardData.getData("text").replace(/\D/g, "").slice(0, 6);
    if (!digits) return;
    const next = [...otp];
    for (let j = 0; j < digits.length; j++) {
      if (startIdx + j < 6) next[startIdx + j] = digits[j];
    }
    setOtp(next);
    otpRefs.current[Math.min(startIdx + digits.length, 5)]?.focus();
    setError("");
  }

  async function handleVerifyMfa() {
    const code = otp.join("");
    if (code.length < 6) {
      setError("Please enter the complete 6-digit code.");
      return;
    }
    setError("");
    setLoading(true);
    try {
      const user = await verifyMfa(mfaToken, code);
      if (rememberDevice) {
        await trustDevice(mfaToken).catch(() => {});
      }
      if (isBankRole(user.role)) {
        router.replace("/bank/dashboard");
      } else if (isSchoolRole(user.role)) {
        router.replace("/school/dashboard");
      } else {
        router.replace("/");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Invalid verification code. Please try again.");
      setOtp(["", "", "", "", "", ""]);
      otpRefs.current[0]?.focus();
    } finally {
      setLoading(false);
    }
  }

  async function handleResend() {
    setOtp(["", "", "", "", "", ""]);
    setError("");
    setResendMsg("");
    setLoading(true);
    try {
      await resendMfa(mfaToken);
      setResendMsg("A new verification code has been sent to your registered device.");
      startCountdown();
      otpRefs.current[0]?.focus();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not resend the code.");
    } finally {
      setLoading(false);
    }
  }

  async function handleForgot() {
    if (!forgotEmail) {
      setError("Please enter your email address.");
      return;
    }
    setError("");
    setLoading(true);
    try {
      await forgotPassword(forgotEmail);
      setState("sent");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not send the reset link.");
    } finally {
      setLoading(false);
    }
  }

  async function handleReset() {
    if (!resetToken) {
      setError("Enter the reset token from the email link.");
      return;
    }
    if (!newPassword) {
      setError("Please enter a new password.");
      return;
    }
    if (strength < 5) {
      setError("Password must satisfy every requirement below.");
      return;
    }
    if (newPassword !== confirmPass) {
      setError("Passwords do not match. Please re-enter.");
      return;
    }
    setError("");
    setLoading(true);
    try {
      await resetPassword(resetToken, newPassword);
      setState("reset-success");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not reset the password.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="min-h-full flex flex-1" style={{ background: "var(--cib-bg)" }}>
      <div className="hidden lg:flex w-[44%] flex-col relative overflow-hidden" style={{ background: "var(--cib-blue)" }}>
        <div className="relative z-10 flex flex-col h-full p-12">
          <div className="mb-auto flex items-center gap-2">
            <BankIcon className="w-9 h-9 text-white" />
            <span className="text-white font-bold text-lg">CIB Tuition Network</span>
          </div>
          <div className="mb-auto mt-12">
            <h1 className="text-[2.1rem] font-light text-white leading-tight mb-4">
              One sign-in.
              <br />
              <span className="font-bold" style={{ color: "var(--cib-orange)" }}>Two portals.</span>
            </h1>
            <p className="text-sm leading-relaxed max-w-xs" style={{ color: "rgba(255,255,255,0.52)" }}>
              Bank Back-Office staff and School Portal admins sign in here - your account decides where you land, not a
              screen you pick.
            </p>
          </div>
        </div>
      </div>

      <div className="flex-1 flex items-center justify-center p-8">
        <div className="w-full max-w-sm">
          <div className="flex lg:hidden items-center gap-2 justify-center mb-8">
            <BankIcon className="w-7 h-7" style={{ color: "var(--cib-blue)" }} />
            <span className="font-bold text-base" style={{ color: "var(--cib-blue)" }}>
              CIB Tuition Network
            </span>
          </div>

          {state === "credentials" && (
            <AuthCard>
              <div className="mb-7">
                <h2 className="text-xl font-bold" style={{ color: "var(--cib-blue)" }}>
                  Sign in to your account
                </h2>
                <p className="text-sm mt-1" style={{ color: "var(--cib-text-muted)" }}>
                  Authorized personnel only
                </p>
              </div>
              {error && <ErrorBanner msg={error} />}
              <form
                className="space-y-4"
                onSubmit={(e) => {
                  e.preventDefault();
                  handleLogin();
                }}
              >
                <div>
                  <FieldLabel>Username or Email</FieldLabel>
                  <InputField
                    type="text"
                    value={username}
                    onChange={(v) => {
                      setUsername(v);
                      setError("");
                    }}
                    placeholder="you@example.com"
                    icon={MailIcon}
                  />
                </div>
                <div>
                  <FieldLabel>Password</FieldLabel>
                  <InputField
                    type={showPassword ? "text" : "password"}
                    value={password}
                    onChange={(v) => {
                      setPassword(v);
                      setError("");
                    }}
                    placeholder="********"
                    icon={LockIcon}
                    right={
                      <button type="button" onClick={() => setShowPassword(!showPassword)} style={{ color: "#aab5c4" }}>
                        <EyeIcon className="w-4 h-4" />
                      </button>
                    }
                  />
                </div>
                <div className="flex items-center justify-between">
                  <span />
                  <button
                    type="button"
                    onClick={() => {
                      setState("forgot");
                      setError("");
                    }}
                    className="text-xs font-semibold transition-colors"
                    style={{ color: "var(--cib-blue)" }}
                  >
                    Forgot password?
                  </button>
                </div>
                <PrimaryBtn type="submit" label={loading ? "Signing in..." : "Sign In"} loading={loading} />
              </form>
            </AuthCard>
          )}

          {state === "mfa" && (
            <AuthCard>
              <BackButton
                onClick={() => {
                  setState("credentials");
                  setOtp(["", "", "", "", "", ""]);
                  setError("");
                  setResendMsg("");
                }}
                label="Back"
              />
              <div className="mb-7">
                <div className="w-12 h-12 rounded-xl flex items-center justify-center mb-4" style={{ background: "var(--cib-blue-light)" }}>
                  <KeyIcon className="w-6 h-6" style={{ color: "var(--cib-blue)" }} />
                </div>
                <h2 className="text-xl font-bold" style={{ color: "var(--cib-blue)" }}>
                  Two-factor verification
                </h2>
                <p className="text-sm mt-1" style={{ color: "var(--cib-text-muted)" }}>
                  Enter the 6-digit code sent to {otpHint || "your registered device"}.
                </p>
              </div>
              {error && <ErrorBanner msg={error} />}
              {otpExpired && !error && <ErrorBanner msg="Verification code expired. Please request a new code." />}
              {resendMsg && !error && <SuccessBanner msg={resendMsg} />}
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  handleVerifyMfa();
                }}
              >
                <div className="grid grid-cols-6 gap-1.5 mb-4">
                  {otp.map((digit, i) => (
                    <input
                      key={i}
                      ref={(el) => {
                        otpRefs.current[i] = el;
                      }}
                      type="text"
                      inputMode="numeric"
                      maxLength={1}
                      value={digit}
                      onChange={(e) => handleOtpChange(i, e.target.value)}
                      onKeyDown={(e) => handleOtpKeyDown(i, e)}
                      onPaste={(e) => handleOtpPaste(e, i)}
                      disabled={otpExpired}
                      className="w-full h-12 text-center text-lg font-bold border rounded-lg focus:outline-none transition-all disabled:opacity-40 disabled:cursor-not-allowed"
                      style={{
                        borderColor: digit ? "var(--cib-blue)" : "var(--cib-border)",
                        color: "var(--cib-blue)",
                        background: digit ? "var(--cib-blue-light)" : "#FAFBFD",
                      }}
                    />
                  ))}
                </div>
                <label className="flex items-center gap-2 cursor-pointer mb-4">
                  <input
                    type="checkbox"
                    className="w-3.5 h-3.5"
                    checked={rememberDevice}
                    onChange={(e) => setRememberDevice(e.target.checked)}
                  />
                  <span className="text-xs" style={{ color: "var(--cib-text-muted)" }}>
                    Remember this device for 30 days
                  </span>
                </label>
                <PrimaryBtn
                  type="submit"
                  label={loading ? "Verifying..." : "Verify & Sign In"}
                  loading={loading}
                  disabled={!otpComplete || otpExpired}
                />
              </form>
              <div className="w-full text-center mt-4 min-h-[20px]">
                {otpExpired ? (
                  <button
                    onClick={handleResend}
                    disabled={loading}
                    className="text-xs font-semibold transition-colors disabled:opacity-50"
                    style={{ color: "var(--cib-blue)" }}
                  >
                    Resend code
                  </button>
                ) : (
                  <span className="text-xs font-semibold tabular-nums" style={{ color: "#aab5c4" }}>
                    Resend code <span style={{ color: "var(--cib-text-muted)" }}>({fmt(countdown)})</span>
                  </span>
                )}
              </div>
            </AuthCard>
          )}

          {state === "forgot" && (
            <AuthCard>
              <BackButton
                onClick={() => {
                  setState("credentials");
                  setError("");
                }}
              />
              <div className="mb-7">
                <h2 className="text-xl font-bold" style={{ color: "var(--cib-blue)" }}>
                  Reset your password
                </h2>
                <p className="text-sm mt-1" style={{ color: "var(--cib-text-muted)" }}>
                  Enter your email and we&apos;ll send a reset link
                </p>
              </div>
              {error && <ErrorBanner msg={error} />}
              <form
                className="space-y-4"
                onSubmit={(e) => {
                  e.preventDefault();
                  handleForgot();
                }}
              >
                <div>
                  <FieldLabel>Email Address</FieldLabel>
                  <InputField
                    type="email"
                    value={forgotEmail}
                    onChange={(v) => {
                      setForgotEmail(v);
                      setError("");
                    }}
                    placeholder="you@example.com"
                    icon={MailIcon}
                  />
                </div>
                <PrimaryBtn type="submit" label={loading ? "Sending..." : "Send Reset Link"} loading={loading} />
              </form>
            </AuthCard>
          )}

          {state === "sent" && (
            <AuthCard>
              <div className="text-center">
                <div className="w-14 h-14 rounded-2xl flex items-center justify-center mx-auto mb-5" style={{ background: "#ECFDF5" }}>
                  <CheckCircleIcon className="w-7 h-7" style={{ color: "#059669" }} />
                </div>
                <h2 className="text-xl font-bold mb-2" style={{ color: "var(--cib-blue)" }}>
                  Check your email
                </h2>
                <p className="text-sm mb-6" style={{ color: "var(--cib-text-muted)" }}>
                  If an account exists for <strong>{forgotEmail}</strong>, a reset link was sent to it. This demo
                  environment doesn&apos;t deliver real email - the reset token is written to the backend&apos;s server
                  log instead.
                </p>
                <button
                  onClick={() => {
                    setState("reset");
                    setError("");
                  }}
                  className="w-full font-semibold py-2.5 rounded-lg text-sm transition-all mb-3"
                  style={{ background: "var(--cib-blue-light)", color: "var(--cib-blue)", border: "1px solid rgba(0,48,135,0.15)" }}
                >
                  I have the reset token
                </button>
                <button onClick={() => setState("credentials")} className="text-sm font-semibold transition-colors" style={{ color: "var(--cib-blue)" }}>
                  Back to sign in
                </button>
              </div>
            </AuthCard>
          )}

          {state === "reset" && (
            <AuthCard>
              <BackButton
                onClick={() => {
                  setState("credentials");
                  setError("");
                }}
              />
              <div className="mb-6">
                <div className="w-12 h-12 rounded-xl flex items-center justify-center mb-4" style={{ background: "var(--cib-blue-light)" }}>
                  <LockIcon className="w-6 h-6" style={{ color: "var(--cib-blue)" }} />
                </div>
                <h2 className="text-xl font-bold" style={{ color: "var(--cib-blue)" }}>
                  Set new password
                </h2>
                <p className="text-sm mt-1" style={{ color: "var(--cib-text-muted)" }}>
                  Paste the reset token from the email link, then choose a new password
                </p>
              </div>
              {error && <ErrorBanner msg={error} />}
              <form
                className="space-y-4"
                onSubmit={(e) => {
                  e.preventDefault();
                  handleReset();
                }}
              >
                <div>
                  <FieldLabel>Reset Token</FieldLabel>
                  <InputField type="text" value={resetToken} onChange={setResetToken} placeholder="prt_..." icon={KeyIcon} />
                </div>

                <div>
                  <FieldLabel>New Password</FieldLabel>
                  <InputField
                    type={showNewPw ? "text" : "password"}
                    value={newPassword}
                    onChange={(v) => {
                      setNewPassword(v);
                      setError("");
                    }}
                    placeholder="New password"
                    icon={LockIcon}
                    right={
                      <button type="button" onClick={() => setShowNewPw(!showNewPw)} style={{ color: "#aab5c4" }}>
                        <EyeIcon className="w-4 h-4" />
                      </button>
                    }
                  />
                  {newPassword && (
                    <div className="mt-2">
                      <div className="flex gap-1 mb-1">
                        {[1, 2, 3, 4, 5].map((i) => (
                          <div
                            key={i}
                            className="flex-1 h-1.5 rounded-full transition-all"
                            style={{ background: i <= strength ? STRENGTH_COLOR[strength] : "#E5E7EB" }}
                          />
                        ))}
                      </div>
                      <span className="text-[10px] font-semibold" style={{ color: STRENGTH_COLOR[strength] }}>
                        {STRENGTH_LABEL[strength]}
                      </span>
                    </div>
                  )}
                </div>

                <div>
                  <FieldLabel>Confirm New Password</FieldLabel>
                  <InputField
                    type={showConfPw ? "text" : "password"}
                    value={confirmPass}
                    onChange={(v) => {
                      setConfirmPass(v);
                      setError("");
                    }}
                    placeholder="Confirm password"
                    icon={LockIcon}
                    right={
                      <button type="button" onClick={() => setShowConfPw(!showConfPw)} style={{ color: "#aab5c4" }}>
                        <EyeIcon className="w-4 h-4" />
                      </button>
                    }
                  />
                  {confirmPass && newPassword !== confirmPass && (
                    <p className="text-[11px] mt-1" style={{ color: "#EF4444" }}>
                      Passwords do not match
                    </p>
                  )}
                </div>

                <div className="rounded-lg p-3" style={{ background: "#F8FAFD", border: "1px solid #E8EDF5" }}>
                  <p className="text-[10px] font-semibold uppercase tracking-wider mb-2" style={{ color: "var(--cib-text-muted)" }}>
                    Password requirements
                  </p>
                  <div className="space-y-1.5">
                    {PASS_REQS.map((req, idx) => {
                      const met = reqResults[idx];
                      return (
                        <div key={idx} className="flex items-center gap-2 text-xs" style={{ color: met ? "#059669" : "#9CA3AF" }}>
                          <span
                            className="w-4 h-4 rounded-full flex items-center justify-center shrink-0"
                            style={{ background: met ? "#ECFDF5" : "#F3F4F6", border: `1px solid ${met ? "#A7F3D0" : "#E5E7EB"}` }}
                          >
                            {met ? <CheckIcon className="w-2.5 h-2.5" style={{ color: "#059669" }} /> : <span className="w-1.5 h-1.5 rounded-full" style={{ background: "#D1D5DB" }} />}
                          </span>
                          {req.label}
                        </div>
                      );
                    })}
                  </div>
                </div>

                <PrimaryBtn type="submit" label={loading ? "Updating password..." : "Set New Password"} loading={loading} disabled={!resetToken || !newPassword || !confirmPass} />
              </form>
            </AuthCard>
          )}

          {state === "reset-success" && (
            <AuthCard>
              <div className="text-center">
                <div className="w-14 h-14 rounded-2xl flex items-center justify-center mx-auto mb-5" style={{ background: "#ECFDF5" }}>
                  <CheckCircleIcon className="w-7 h-7" style={{ color: "#059669" }} />
                </div>
                <h2 className="text-xl font-bold mb-2" style={{ color: "var(--cib-blue)" }}>
                  Password reset successfully
                </h2>
                <p className="text-sm mb-6" style={{ color: "var(--cib-text-muted)" }}>
                  Your password has been updated. You can now sign in with your new credentials.
                </p>
                <PrimaryBtn
                  label="Sign in with new password"
                  onClick={() => {
                    setState("credentials");
                    setNewPassword("");
                    setConfirmPass("");
                    setResetToken("");
                    setForgotEmail("");
                    setError("");
                  }}
                />
              </div>
            </AuthCard>
          )}

          <p className="text-center text-[11px] mt-6" style={{ color: "#aab5c4" }}>
            &copy; {new Date().getFullYear()} Commercial International Bank Egypt S.A.E &middot; Authorized Access Only
          </p>
        </div>
      </div>
    </div>
  );
}
