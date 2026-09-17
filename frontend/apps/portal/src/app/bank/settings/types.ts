export interface FeeTypeSettingDto {
  id: string;
  name: string;
  code: string;
  taxable: boolean;
  active: boolean;
}

export interface CreateFeeTypeRequest {
  name: string;
  code: string;
  taxable?: boolean;
  active?: boolean;
}

export interface UpdateFeeTypeRequest {
  name?: string;
  code?: string;
  taxable?: boolean;
  active?: boolean;
}

export interface PaymentStatusInfo {
  status: string;
  description: string;
  terminal: boolean;
}

export interface EppSettingsDto {
  tenors: Record<string, boolean>;
  minAmountEGP: number;
  maxAmountEGP: number;
  interestRatePct: Record<string, number>;
  adminFeeRatePct: number;
  adminFeeCapEGP: number;
  requireApproval: boolean;
  maxPlansPerStudent: number;
}

export interface NotificationSettingsDto {
  events: {
    failedPayments: boolean;
    reconExceptions: boolean;
    schoolUploadErrors: boolean;
    newSchoolReg: boolean;
    systemAlerts: boolean;
    dailySummary: boolean;
  };
  channels: {
    inApp: boolean;
    email: boolean;
    sms: boolean;
    slack: boolean;
  };
}

export interface InstitutionSettingsDto {
  requireDualApproval: boolean;
  autoIntegrationAfterApproval: boolean;
  requireMOECertificate: boolean;
  maxStudentsPerUpload: number;
  postApprovalActivationDelayHours: number;
  allowedUploadFormats: {
    xlsx: boolean;
    csv: boolean;
    xml: boolean;
  };
}
