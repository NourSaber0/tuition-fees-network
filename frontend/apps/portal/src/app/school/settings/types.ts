
export interface SchoolProfileDto {
  id: string;
  name: string;
  code: string;
  institutionType?: string | null;
  subType?: string | null;
  city?: string | null;
  principalName?: string | null;
  phone?: string | null;
  email?: string | null;
  registrationNumber?: string | null;
  taxRegistrationNumber?: string | null;
  commercialRegNumber?: string | null;
  bankAccountNumber?: string | null;
  iban?: string | null;
  feeAbsorptionPolicy?: string | null;
  accountStatus?: string | null;
  integrationStatus?: string | null;
  registeredAt?: string | null;
}

export interface SchoolNotificationSettingsDto {
  channels: Record<string, boolean>;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface MessageResponse {
  message?: string;
}

