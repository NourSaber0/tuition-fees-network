"use client";

import { useCallback, useEffect, useState } from "react";
import { useApiClient, useAuth } from "@tuition/api-client";
import {
  Badge,
  Button,
  LoadingSpinner,
  SchoolIcon,
  BellIcon,
  LockIcon,
  CheckCircleIcon,
  AlertIcon,
  InfoIcon,
} from "@tuition/ui";

import type {
  SchoolProfileDto,
  SchoolNotificationSettingsDto,
  ChangePasswordRequest,
} from "./types";


function ProfileField({ label, value }: { label: string; value?: string | null }) {
  return (
    <div className="space-y-0.5">
      <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">{label}</p>
      <p className="text-sm font-medium text-gray-900">{value || "—"}</p>
    </div>
  );
}

export default function SchoolSettingsPage() {
  const apiClient = useApiClient();
  const { user } = useAuth();

  // Profile State
  const [profile, setProfile] = useState<SchoolProfileDto | null>(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [profileError, setProfileError] = useState<string | null>(null);

  // Notifications Settings State
  const [inAppEnabled, setInAppEnabled] = useState(true);
  const [emailEnabled, setEmailEnabled] = useState(true);
  const [notifLoading, setNotifLoading] = useState(true);
  const [notifSaving, setNotifSaving] = useState(false);
  const [notifSuccess, setNotifSuccess] = useState(false);
  const [notifError, setNotifError] = useState<string | null>(null);

  // Change Password State
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [pwdLoading, setPwdLoading] = useState(false);
  const [pwdSuccess, setPwdSuccess] = useState(false);
  const [pwdError, setPwdError] = useState<string | null>(null);

  // -------------------------------------------------------------------------
  // Fetch Profile
  // -------------------------------------------------------------------------
  const fetchProfile = useCallback(() => {
    setProfileLoading(true);
    setProfileError(null);
    apiClient
      .get<SchoolProfileDto>("/settings/profile")
      .then((res) => {
        setProfile(res);
      })
      .catch((err) => {
        setProfileError(err instanceof Error ? err.message : "Failed to load school profile.");
      })
      .finally(() => setProfileLoading(false));
  }, [apiClient]);

  // -------------------------------------------------------------------------
  // Fetch Notification Settings
  // -------------------------------------------------------------------------
  const fetchNotificationSettings = useCallback(() => {
    setNotifLoading(true);
    apiClient
      .get<SchoolNotificationSettingsDto>("/settings/notifications")
      .then((res) => {
        if (res && res.channels) {
          setInAppEnabled(res.channels.inApp ?? true);
          setEmailEnabled(res.channels.email ?? true);
        }
      })
      .catch(() => {})
      .finally(() => setNotifLoading(false));
  }, [apiClient]);

  useEffect(() => {
    fetchProfile();
    fetchNotificationSettings();
  }, [fetchProfile, fetchNotificationSettings]);

  // -------------------------------------------------------------------------
  // Save Notification Settings
  // -------------------------------------------------------------------------
  const handleSaveNotifications = async (e: React.FormEvent) => {
    e.preventDefault();
    setNotifSaving(true);
    setNotifError(null);
    setNotifSuccess(false);

    try {
      await apiClient.put("/settings/notifications", {
        channels: {
          inApp: inAppEnabled,
          email: emailEnabled,
        },
      });
      setNotifSuccess(true);
      setTimeout(() => setNotifSuccess(false), 4000);
    } catch (err) {
      setNotifError(
        err instanceof Error ? err.message : "Failed to update notification preferences."
      );
    } finally {
      setNotifSaving(false);
    }
  };

  // -------------------------------------------------------------------------
  // Handle Change Password
  // -------------------------------------------------------------------------
  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setPwdSuccess(false);
    setPwdError(null);

    if (!currentPassword) {
      setPwdError("Please enter your current password.");
      return;
    }
    if (!newPassword || newPassword.length < 6) {
      setPwdError("New password must be at least 6 characters long.");
      return;
    }
    if (newPassword !== confirmPassword) {
      setPwdError("New password and confirm password do not match.");
      return;
    }

    setPwdLoading(true);

    try {
      const payload: ChangePasswordRequest = { currentPassword, newPassword };
      await apiClient.post("/settings/change-password", payload);
      setPwdSuccess(true);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      setTimeout(() => setPwdSuccess(false), 5000);
    } catch (err) {
      setPwdError(err instanceof Error ? err.message : "Failed to change password.");
    } finally {
      setPwdLoading(false);
    }
  };

  // -------------------------------------------------------------------------
  // Render Main Layout
  // -------------------------------------------------------------------------
  return (
    <div className="space-y-8 pb-16 max-w-5xl">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Settings</h1>
        <p className="mt-1 text-sm text-gray-500">
          View school metadata, notification delivery channels, and manage your account security.
        </p>
      </div>

      {/* ================================================================== */}
      {/* SECTION 1: Read-Only School Profile Card                            */}
      {/* ================================================================== */}
      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden shadow-sm">
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between bg-gray-50/50">
          <div className="flex items-center gap-2">
            <SchoolIcon className="w-5 h-5 text-[#003087]" />
            <h2 className="text-base font-bold text-gray-900">School Profile</h2>
          </div>
          <Badge tone="info">Read-Only</Badge>
        </div>

        <div className="p-6 space-y-6">
          {/* Read-Only Banner */}
          <div className="flex items-start gap-2.5 p-3.5 bg-blue-50/70 border border-blue-100 rounded-lg text-xs text-blue-900">
            <InfoIcon className="w-4 h-4 text-[#003087] shrink-0 mt-0.5" />
            <p>
              Institution profile details, bank accounts, and integration status are managed by{" "}
              <span className="font-semibold">CIB Bank Back-Office Administrators</span>. To request updates to your institution details, please contact support.
            </p>
          </div>

          {profileLoading ? (
            <div className="flex justify-center py-10">
              <LoadingSpinner size={28} />
            </div>
          ) : profileError ? (
            <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-xs text-red-700 flex items-center justify-between">
              <span>{profileError}</span>
              <Button variant="secondary" size="sm" onClick={fetchProfile}>
                Retry
              </Button>
            </div>
          ) : profile ? (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              <ProfileField label="School Name" value={profile.name} />
              <ProfileField label="Institution Code" value={profile.code} />
              <ProfileField
                label="Institution Type"
                value={`${profile.institutionType ?? "School"} ${
                  profile.subType ? `(${profile.subType})` : ""
                }`}
              />

              <ProfileField label="City" value={profile.city} />
              <ProfileField label="Principal / Admin" value={profile.principalName} />
              <ProfileField label="Contact Phone" value={profile.phone} />

              <ProfileField label="Official Email" value={profile.email} />
              <ProfileField label="Registration No." value={profile.registrationNumber} />
              <ProfileField label="Tax Registration No." value={profile.taxRegistrationNumber} />

              <ProfileField label="Bank Account Number" value={profile.bankAccountNumber} />
              <ProfileField label="IBAN" value={profile.iban} />
              <ProfileField label="Fee Absorption Policy" value={profile.feeAbsorptionPolicy} />

              <div className="space-y-1">
                <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">
                  Account Status
                </p>
                <Badge
                  tone={
                    (profile.accountStatus ?? "").toLowerCase() === "active" ? "success" : "warning"
                  }
                >
                  {profile.accountStatus ?? "Active"}
                </Badge>
              </div>

              <div className="space-y-1">
                <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">
                  Integration Status
                </p>
                <Badge
                  tone={
                    (profile.integrationStatus ?? "").toLowerCase().includes("connect") ||
                    (profile.integrationStatus ?? "").toLowerCase().includes("live")
                      ? "success"
                      : "info"
                  }
                >
                  {profile.integrationStatus ?? "Connected"}
                </Badge>
              </div>

              <ProfileField label="Registered Date" value={profile.registeredAt} />
            </div>
          ) : null}
        </div>
      </div>

      {/* ================================================================== */}
      {/* SECTION 2: Notification Delivery Settings Toggles                  */}
      {/* ================================================================== */}
      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden shadow-sm">
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between bg-gray-50/50">
          <div className="flex items-center gap-2">
            <BellIcon className="w-5 h-5 text-[#003087]" />
            <h2 className="text-base font-bold text-gray-900">Notification Delivery Toggles</h2>
          </div>
        </div>

        <form onSubmit={handleSaveNotifications} className="p-6 space-y-6">
          <p className="text-xs text-gray-500">
            Configure how staff members receive delivery notifications for payment updates, fee uploads, and automated reminders.
          </p>

          {notifSuccess && (
            <div className="flex items-center gap-2 bg-green-50 border border-green-200 rounded-lg p-3 text-xs text-green-800">
              <CheckCircleIcon className="w-4 h-4 text-green-600 shrink-0" />
              Notification preferences saved successfully.
            </div>
          )}

          {notifError && (
            <div className="flex items-center gap-2 bg-red-50 border border-red-200 rounded-lg p-3 text-xs text-red-700">
              <AlertIcon className="w-4 h-4 text-red-600 shrink-0" />
              {notifError}
            </div>
          )}

          {notifLoading ? (
            <div className="flex justify-center py-6">
              <LoadingSpinner size={24} />
            </div>
          ) : (
            <div className="space-y-4">
              {/* In-App Toggle */}
              <div className="flex items-center justify-between p-4 rounded-xl border border-gray-100 bg-gray-50/50">
                <div>
                  <h3 className="text-sm font-semibold text-gray-900">In-App Notifications</h3>
                  <p className="text-xs text-gray-500 mt-0.5">
                    Show real-time notification cards and top-bar unread badge inside the portal.
                  </p>
                </div>
                <label className="relative inline-flex items-center cursor-pointer">
                  <input
                    type="checkbox"
                    checked={inAppEnabled}
                    onChange={(e) => setInAppEnabled(e.target.checked)}
                    className="sr-only peer"
                  />
                  <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#003087]"></div>
                </label>
              </div>

              {/* Email Toggle */}
              <div className="flex items-center justify-between p-4 rounded-xl border border-gray-100 bg-gray-50/50">
                <div>
                  <h3 className="text-sm font-semibold text-gray-900">Email Notifications</h3>
                  <p className="text-xs text-gray-500 mt-0.5">
                    Send email alert digests for fee upload validations and automated reminders.
                  </p>
                </div>
                <label className="relative inline-flex items-center cursor-pointer">
                  <input
                    type="checkbox"
                    checked={emailEnabled}
                    onChange={(e) => setEmailEnabled(e.target.checked)}
                    className="sr-only peer"
                  />
                  <div className="w-11 h-6 bg-gray-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-[#003087]"></div>
                </label>
              </div>
            </div>
          )}

          <div className="flex justify-end pt-2">
            <Button variant="primary" type="submit" disabled={notifSaving || notifLoading}>
              {notifSaving ? "Saving…" : "Save Preferences"}
            </Button>
          </div>
        </form>
      </div>

      {/* ================================================================== */}
      {/* SECTION 3: Self-Service Change Password                             */}
      {/* ================================================================== */}
      <div className="bg-white rounded-xl border border-gray-200 overflow-hidden shadow-sm">
        <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between bg-gray-50/50">
          <div className="flex items-center gap-2">
            <LockIcon className="w-5 h-5 text-[#003087]" />
            <h2 className="text-base font-bold text-gray-900">Change Password</h2>
          </div>
        </div>

        <form onSubmit={handleChangePassword} className="p-6 space-y-4 max-w-xl">
          <p className="text-xs text-gray-500">
            Rotate your account password. Ensure your new password is at least 6 characters long.
          </p>

          {pwdSuccess && (
            <div className="flex items-center gap-2 bg-green-50 border border-green-200 rounded-lg p-3 text-xs text-green-800">
              <CheckCircleIcon className="w-4 h-4 text-green-600 shrink-0" />
              Your password has been changed successfully.
            </div>
          )}

          {pwdError && (
            <div className="flex items-center gap-2 bg-red-50 border border-red-200 rounded-lg p-3 text-xs text-red-700">
              <AlertIcon className="w-4 h-4 text-red-600 shrink-0" />
              {pwdError}
            </div>
          )}

          <div>
            <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wide mb-1">
              Current Password *
            </label>
            <input
              type="password"
              required
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              placeholder="Enter current password"
              className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#003087]"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wide mb-1">
              New Password *
            </label>
            <input
              type="password"
              required
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              placeholder="Min. 6 characters"
              className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#003087]"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wide mb-1">
              Confirm New Password *
            </label>
            <input
              type="password"
              required
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder="Re-enter new password"
              className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#003087]"
            />
          </div>

          <div className="pt-2">
            <Button variant="primary" type="submit" disabled={pwdLoading}>
              {pwdLoading ? "Updating…" : "Update Password"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

