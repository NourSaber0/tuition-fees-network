# Tuition & Services Fees Collection Network - ER Diagram

This document contains the Entity-Relationship (ER) diagram for the MVP database schema.

```mermaid
erDiagram
    %% =========================================
    %% KEY / LEGEND
    %% PK (Primary Key): The unique identifier for a specific record within its own table.
    %% FK (Foreign Key): A reference to a Primary Key in another table, used to link records.
    %% UK (Unique Key): A constraint ensuring the data in this column is strictly unique.
    %% =========================================

    _LEGEND_ {
        string pk_indicator PK "Primary Key"
        string fk_indicator FK "Foreign Key"
        string uk_indicator "Unique Key"
    }

    GUARDIAN {
        uuid id PK
        string national_id_hash "HMAC (For Search)"
        string national_id_encrypted "AES-256-GCM (For Rest)"
        string name
        string email
        string phone
        string password_hash
        boolean cib_account_linked
    }
    
    INSTITUTION_ADMIN {
        uuid id PK
        uuid institution_id FK
        string name
        string email
        string password_hash
        string role "e.g., Finance, IT, SuperAdmin"
    }

    BANK_EMPLOYEE {
        uuid id PK
        string name
        string email "UK"
        string employee_id "UK"
        string password_hash
        string department "e.g., Operations, Support"
    }
    
    STUDENT {
        uuid id PK
        string national_id_hash "HMAC (For Search)"
        string national_id_encrypted "AES-256-GCM (For Rest)"
        string full_name
        datetime date_of_birth
    }
    
    GUARDIAN_STUDENT {
        uuid guardian_id FK
        uuid student_id FK
        string relationship_type
    }
    
    INSTITUTION {
        uuid id PK
        string name
        string code "UK"
        string fee_absorption_policy
    }
    
    FEE_LINE {
        uuid id PK
        uuid institution_id FK
        uuid student_id FK
        string fee_type "Tuition, Bus, Books, Activities"
        decimal total_amount "Original Due (>0)"
        decimal running_balance "Current Due"
        string currency "EGP"
        string collection_period "e.g., Term 2 - 2026"
        datetime due_date
        string status "Open, Partial, Paid"
        int version "Optimistic Locking"
        string row_idempotency_key "UK - Hash of Student+Inst+Type+Period"
    }
    
    PAYMENT {
        uuid id PK
        uuid guardian_id FK
        decimal total_amount
        string payment_method "CreditCard, DirectDebit"
        string payment_type "Full, Partial, EPP"
        string status "Pending, Auth, Captured, Failed"
        string idempotency_key "UK"
        string transaction_reference "Bank Gateway Ref"
        string auth_code
        datetime created_at
    }
    
    PAYMENT_STATE_LOG {
        uuid id PK
        uuid payment_id FK
        string status_from
        string status_to
        string gateway_response_code
        string gateway_message
        datetime transitioned_at
    }
    
    PAYMENT_ALLOCATION {
        uuid id PK
        uuid payment_id FK
        uuid fee_line_id FK
        decimal amount_applied
    }
    
    EPP_PLAN {
        uuid id PK
        uuid payment_id FK
        int tenor_months "3, 6, 12, 18"
        decimal principal
        decimal interest
        decimal admin_fee
        decimal total_payable
        decimal monthly_instalment
    }
    
    EPP_INSTALLMENT {
        uuid id PK
        uuid epp_plan_id FK
        int installment_number
        decimal amount
        datetime due_date
        string status "Read-Only from Bank / Display purposes"
    }
    
    RECEIPT {
        uuid id PK
        uuid payment_id FK
        string crypto_signature
        string file_url
        datetime issued_at
    }
    
    CSV_UPLOAD {
        uuid id PK
        uuid institution_id FK
        string file_name
        int total_rows
        int failed_rows
        datetime uploaded_at
    }

    UPLOAD_ERROR {
        uuid id PK
        uuid csv_upload_id FK
        int row_number
        string error_message
        string raw_row_data
    }
    
    NOTIFICATION {
        uuid id PK
        uuid guardian_id FK
        string type "Email, SMS, Portal"
        string message
        string status "Unread, Read, Sent, Failed"
        datetime created_at
    }

    AUDIT_LOG {
        uuid audit_id PK
        uuid actor_id "Guardian or Admin ID"
        string actor_type "Guardian, InstitutionAdmin, System"
        string action "e.g., Search_ID, Payment_Initiated"
        string target_resource "e.g., Hashed National ID"
        datetime timestamp
    }

    %% Relationships
    GUARDIAN ||--o{ GUARDIAN_STUDENT : "manages"
    STUDENT ||--o{ GUARDIAN_STUDENT : "is_dependent_of"
    
    INSTITUTION ||--o{ INSTITUTION_ADMIN : "employs"
    
    INSTITUTION ||--o{ FEE_LINE : "issues"
    STUDENT ||--o{ FEE_LINE : "owes"
    
    INSTITUTION ||--o{ CSV_UPLOAD : "uploads"
    CSV_UPLOAD ||--o{ UPLOAD_ERROR : "generates"
    
    GUARDIAN ||--o{ PAYMENT : "initiates"
    PAYMENT ||--o{ PAYMENT_STATE_LOG : "audits"
    PAYMENT ||--o{ PAYMENT_ALLOCATION : "splits_into"
    FEE_LINE ||--o{ PAYMENT_ALLOCATION : "is_paid_by"
    
    PAYMENT ||--o| EPP_PLAN : "converts_to (Optional)"
    EPP_PLAN ||--o{ EPP_INSTALLMENT : "schedules"
    
    PAYMENT ||--|| RECEIPT : "generates"
    
    GUARDIAN ||--o{ NOTIFICATION : "receives"