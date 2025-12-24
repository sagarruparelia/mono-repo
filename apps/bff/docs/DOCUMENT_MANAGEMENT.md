# Document Management Feature Specification

> **Status**: Design Phase
> **Last Updated**: 2024-01-XX
> **Owner**: Engineering Team

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Business Requirements](#2-business-requirements)
3. [Technical Architecture](#3-technical-architecture)
4. [Security Deep Dive](#4-security-deep-dive)
5. [API Specification](#5-api-specification)
6. [Data Models](#6-data-models)
7. [Infrastructure](#7-infrastructure)
8. [Implementation Phases](#8-implementation-phases)
9. [Cost Analysis](#9-cost-analysis)
10. [Risk Assessment](#10-risk-assessment)
11. [Open Decisions](#11-open-decisions)
12. [Appendix](#12-appendix)

---

## 1. Executive Summary

### 1.1 Overview

Document Management enables users to upload, store, and manage personal documents (medical records, prescriptions, insurance cards, etc.) with role-based access control. The system uses AWS S3 for file storage and MongoDB Atlas for metadata, with future AI capabilities for document understanding.

### 1.2 Key Features

| Feature | Description | Phase |
|---------|-------------|-------|
| Upload | Presigned URL upload up to 25MB, 4 concurrent files | 1 |
| Download | Presigned URL download with access validation | 1 |
| Categories | Business-managed document categories | 1 |
| Access Control | Persona-based access (SELF, DELEGATE, AGENT, CASE_WORKER) | 1 |
| Malware Scan | AWS GuardDuty malware protection | 2 |
| Encryption | SSE-KMS with dedicated key | 2 |
| Audit Trail | Comprehensive action logging | 3 |
| AI Extraction | OCR, metadata extraction, embeddings | 4 |

### 1.3 User Experience Flow

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           USER UPLOAD FLOW                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  1. SELECT FILE              2. UPLOADING              3. FINALIZE      │
│  ┌─────────────┐            ┌─────────────┐          ┌─────────────┐    │
│  │ Choose File │───────────▶│ Progress... │─────────▶│ Category ▼  │    │
│  │ (25MB max)  │            │ ████████░░  │          │ [Prescription]│   │
│  └─────────────┘            │    80%      │          │             │    │
│        │                    └─────────────┘          │ [Upload ✓]  │    │
│        │                          │                  └─────────────┘    │
│        │                          │                         │           │
│        ▼                          ▼                         ▼           │
│   API: /initiate            Direct S3 PUT           API: /finalize      │
│   ← presigned URL           (background)            → PROCESSING        │
│                                                     → READY (after scan)│
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Business Requirements

### 2.1 Functional Requirements

#### 2.1.1 Document Upload
- **FR-1**: User can upload files up to 25MB
- **FR-2**: User can upload up to 4 files concurrently
- **FR-3**: Upload starts immediately when file is selected (background)
- **FR-4**: User must select a category before finalizing upload
- **FR-5**: Temporary files are cleaned up after 4 hours if not finalized

#### 2.1.2 Document Management
- **FR-6**: User can list their documents with pagination
- **FR-7**: User can filter documents by category
- **FR-8**: User can download documents they have access to
- **FR-9**: User can delete (soft-delete) their documents
- **FR-10**: Documents are retained indefinitely until manually deleted

#### 2.1.3 Access Control
- **FR-11**: SELF persona has full access to their own documents
- **FR-12**: DELEGATE persona requires ROI + DAA + RPR permissions
- **FR-13**: AGENT and CASE_WORKER can access any member's documents
- **FR-14**: CONFIG_SPECIALIST has no document access

#### 2.1.4 Categories
- **FR-15**: Categories are managed by business team (not code changes)
- **FR-16**: Only approved categories can be assigned to documents

### 2.2 Non-Functional Requirements

| Requirement | Target | Metric |
|-------------|--------|--------|
| Upload Latency | < 500ms | Time to get presigned URL |
| Download Latency | < 300ms | Time to get presigned URL |
| Availability | 99.9% | Monthly uptime |
| Durability | 99.999999999% | S3 standard |
| Max File Size | 25MB | Hard limit |
| Concurrent Uploads | 4 per user | Rate limit |

### 2.3 Document Categories

| Category ID | Display Name | Description |
|-------------|--------------|-------------|
| MARKSHEET | Marksheet/Academic Records | Educational documents |
| PRESCRIPTION | Medicine Prescription | Doctor prescriptions |
| PATHOLOGY_REPORT | Pathology/Lab Report | Test results |
| MEDICAL_RECORD | Medical Record | General medical documents |
| INSURANCE_CARD | Insurance Card | Health insurance cards |
| DISCHARGE_SUMMARY | Discharge Summary | Hospital discharge papers |
| IMMUNIZATION_RECORD | Immunization Record | Vaccination records |
| IDENTIFICATION | ID Document | Identity documents |
| OTHER | Other | Uncategorized documents |

---

## 3. Technical Architecture

### 3.1 System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              DOCUMENT MANAGEMENT ARCHITECTURE                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────┐     ┌─────────────────────────────────────────────────────┐   │
│  │  Client  │     │                    BFF Application                   │   │
│  │ (Browser/│     │  ┌─────────────────────────────────────────────────┐ │   │
│  │  Partner)│     │  │              DocumentController                  │ │   │
│  └────┬─────┘     │  │  /upload/initiate │ /upload/finalize │ /list   │ │   │
│       │           │  │  /download        │ /delete          │ /detail │ │   │
│       │           │  └─────────────────────────────────────────────────┘ │   │
│       │           │                         │                            │   │
│       │           │  ┌─────────────────────────────────────────────────┐ │   │
│       │           │  │              DocumentService                     │ │   │
│       │           │  │  • Access validation (persona checks)           │ │   │
│       │           │  │  • Upload orchestration                         │ │   │
│       │           │  │  • Metadata management                          │ │   │
│       │           │  └───────────────┬─────────────────────────────────┘ │   │
│       │           │                  │                                   │   │
│       │           │      ┌───────────┴───────────┐                       │   │
│       │           │      ▼                       ▼                       │   │
│       │           │  ┌───────────────┐   ┌────────────────────┐         │   │
│       │           │  │S3StorageClient│   │DocumentRepository  │         │   │
│       │           │  │• Presigned URL│   │• Metadata CRUD     │         │   │
│       │           │  │• Move objects │   │• Category service  │         │   │
│       │           │  └───────┬───────┘   └─────────┬──────────┘         │   │
│       │           │          │                     │                     │   │
│       │           └──────────┼─────────────────────┼─────────────────────┘   │
│       │                      │                     │                         │
│       │    ┌─────────────────┼─────────────────────┼───────────────────┐    │
│       │    │     AWS         │                     │      MongoDB      │    │
│       │    │                 ▼                     │         ▼         │    │
│       │    │  ┌────────────────────────────┐      │  ┌─────────────┐  │    │
│       │    │  │           S3 Bucket        │      │  │  documents  │  │    │
│       │    │  │  ┌──────┐    ┌──────────┐  │      │  │ temp_uploads│  │    │
│       │    │  │  │ temp/│    │  docs/   │  │      │  │ categories  │  │    │
│       │    │  │  └──────┘    └──────────┘  │      │  └─────────────┘  │    │
│       │    │  └────────────────────────────┘      │                   │    │
│       │    │                                      │                   │    │
│       └────┼───── Direct S3 Upload (presigned) ──▶│                   │    │
│            │                                      │                   │    │
│            │  ┌────────────────────────────┐      │                   │    │
│            │  │       GuardDuty            │      │                   │    │
│            │  │  (Malware Protection)      │──────┼──▶ Update status  │    │
│            │  └────────────────────────────┘      │                   │    │
│            │                                      │                   │    │
│            │  ┌────────────────────────────┐      │                   │    │
│            │  │          KMS               │      │                   │    │
│            │  │  (Encryption Keys)         │      │                   │    │
│            │  └────────────────────────────┘      │                   │    │
│            └──────────────────────────────────────┴───────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Upload Flow Sequence

```
┌────────┐     ┌─────────┐     ┌──────────┐     ┌────┐     ┌─────────┐
│ Client │     │   BFF   │     │ MongoDB  │     │ S3 │     │GuardDuty│
└───┬────┘     └────┬────┘     └────┬─────┘     └──┬─┘     └────┬────┘
    │               │               │              │             │
    │ 1. Select file│               │              │             │
    │──────────────▶│               │              │             │
    │               │               │              │             │
    │ 2. POST /upload/initiate      │              │             │
    │──────────────▶│               │              │             │
    │               │               │              │             │
    │               │ 3. Create TempUpload         │             │
    │               │──────────────▶│              │             │
    │               │               │              │             │
    │               │ 4. Generate presigned URL    │             │
    │               │─────────────────────────────▶│             │
    │               │               │              │             │
    │ 5. Return presigned URL       │              │             │
    │◀──────────────│               │              │             │
    │               │               │              │             │
    │ 6. PUT file directly to S3 (background)      │             │
    │─────────────────────────────────────────────▶│             │
    │               │               │              │             │
    │ 7. User selects category, clicks Upload      │             │
    │               │               │              │             │
    │ 8. POST /upload/finalize      │              │             │
    │──────────────▶│               │              │             │
    │               │               │              │             │
    │               │ 9. Verify TempUpload exists  │             │
    │               │──────────────▶│              │             │
    │               │               │              │             │
    │               │ 10. Head object (verify upload)            │
    │               │─────────────────────────────▶│             │
    │               │               │              │             │
    │               │ 11. Copy temp → permanent    │             │
    │               │─────────────────────────────▶│             │
    │               │               │              │             │
    │               │ 12. Delete temp object       │             │
    │               │─────────────────────────────▶│             │
    │               │               │              │             │
    │               │ 13. Create DocumentMetadata  │             │
    │               │──────────────▶│              │             │
    │               │               │              │             │
    │ 14. Return document ID (PENDING_SCAN)        │             │
    │◀──────────────│               │              │             │
    │               │               │              │             │
    │               │               │              │ 15. Scan    │
    │               │               │              │◀───────────▶│
    │               │               │              │             │
    │               │ 16. EventBridge: Scan result │             │
    │               │◀────────────────────────────────────────────
    │               │               │              │             │
    │               │ 17. Update scan status       │             │
    │               │──────────────▶│              │             │
    │               │               │              │             │
└───┴────┘     └────┴────┘     └────┴─────┘     └──┴─┘     └────┴────┘
```

### 3.3 S3 Bucket Structure

```
bff-documents-{env}/
│
├── temp/                                    # Temporary uploads (TTL: 4 hours → 24 hours S3 lifecycle)
│   └── {uploaderId}/                        # Grouped by uploader for easy cleanup
│       └── {uploadId}/                      # Unique upload session
│           └── {sanitized-filename}         # User's file
│
└── docs/                                    # Permanent documents
    └── {ownerEnterpriseId}/                 # Grouped by document owner
        └── {documentId}/                    # Unique document ID
            └── {sanitized-filename}         # User's file
```

**Key Naming Examples**:
```
temp/HSID-user-abc123/550e8400-e29b-41d4-a716-446655440000/insurance_card.pdf
docs/ENT123456789/660e8400-e29b-41d4-a716-446655440001/insurance_card.pdf
```

### 3.4 Component Responsibilities

| Component | Responsibility |
|-----------|----------------|
| DocumentController | API endpoints, request validation, annotations |
| DocumentService | Business logic, orchestration, access validation |
| DocumentAccessValidator | Persona-based access control logic |
| S3StorageClient | Presigned URLs, S3 operations |
| DocumentMetadataRepository | MongoDB CRUD for documents |
| TempUploadRepository | MongoDB CRUD for temp uploads |
| DocumentCategoryService | Category management with caching |

---

## 4. Security Deep Dive

### 4.1 Encryption Options Analysis

#### Option A: SSE-S3 (Server-Side Encryption with S3-Managed Keys)

```
┌─────────────────────────────────────────────────────────────────┐
│                        SSE-S3 Encryption                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────┐         ┌─────────────────────┐                    │
│  │ Client  │────────▶│        S3           │                    │
│  └─────────┘         │  ┌───────────────┐  │                    │
│                      │  │ AWS Managed   │  │                    │
│                      │  │ Master Key    │  │                    │
│                      │  └───────┬───────┘  │                    │
│                      │          │          │                    │
│                      │  ┌───────▼───────┐  │                    │
│                      │  │ Data Key      │  │                    │
│                      │  │ (per object)  │  │                    │
│                      │  └───────┬───────┘  │                    │
│                      │          │          │                    │
│                      │  ┌───────▼───────┐  │                    │
│                      │  │ Encrypted     │  │                    │
│                      │  │ Object        │  │                    │
│                      │  └───────────────┘  │                    │
│                      └─────────────────────┘                    │
│                                                                  │
│  Pros:                      Cons:                                │
│  ✓ Zero configuration      ✗ AWS controls keys                  │
│  ✓ No cost                 ✗ No key rotation control            │
│  ✓ Automatic               ✗ Admin with S3 access can read      │
│                             ✗ No audit trail for key usage       │
└─────────────────────────────────────────────────────────────────┘
```

**Verdict**: Suitable for MVP, upgrade to SSE-KMS in Phase 2.

#### Option B: SSE-KMS (Server-Side Encryption with KMS-Managed Keys)

```
┌─────────────────────────────────────────────────────────────────┐
│                       SSE-KMS Encryption                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────┐    ┌───────────┐    ┌─────────────────────┐        │
│  │ Client  │───▶│    S3     │───▶│        KMS          │        │
│  └─────────┘    └───────────┘    │  ┌───────────────┐  │        │
│                                  │  │ Customer      │  │        │
│                                  │  │ Master Key    │  │        │
│                                  │  │ (CMK)         │  │        │
│                                  │  └───────┬───────┘  │        │
│                                  │          │          │        │
│                                  │  ┌───────▼───────┐  │        │
│                                  │  │ Data Key      │  │        │
│                                  │  └───────────────┘  │        │
│                                  └─────────────────────┘        │
│                                                                  │
│  Key Policy Example:                                             │
│  {                                                               │
│    "Effect": "Allow",                                            │
│    "Principal": {"AWS": "arn:aws:iam::ACCOUNT:role/BFFServiceRole"},
│    "Action": ["kms:Encrypt", "kms:Decrypt", "kms:GenerateDataKey"],
│    "Resource": "*"                                               │
│  }                                                               │
│                                                                  │
│  Pros:                      Cons:                                │
│  ✓ Customer-controlled key  ✗ $1/month per key                  │
│  ✓ CloudTrail audit         ✗ API request costs                 │
│  ✓ Key rotation control     ✗ Slight latency increase           │
│  ✓ IAM-based access control                                     │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Verdict**: Recommended for Phase 2. Provides audit trail and access control.

#### Option C: SSE-KMS Per-Member Keys

```
┌─────────────────────────────────────────────────────────────────┐
│                   SSE-KMS Per-Member Keys                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐         │
│  │  Member A    │   │  Member B    │   │  Member C    │         │
│  │  Documents   │   │  Documents   │   │  Documents   │         │
│  └──────┬───────┘   └──────┬───────┘   └──────┬───────┘         │
│         │                  │                  │                  │
│         ▼                  ▼                  ▼                  │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐         │
│  │ CMK: alias/  │   │ CMK: alias/  │   │ CMK: alias/  │         │
│  │ doc-ENT001   │   │ doc-ENT002   │   │ doc-ENT003   │         │
│  └──────────────┘   └──────────────┘   └──────────────┘         │
│                                                                  │
│  Implementation:                                                 │
│  1. On first upload for member, create CMK with alias            │
│  2. Use member-specific CMK for all their documents              │
│  3. Key deletion = all documents become unreadable               │
│                                                                  │
│  Pros:                      Cons:                                │
│  ✓ Complete isolation       ✗ $1/month PER member               │
│  ✓ Key per member          ✗ Complex key management             │
│  ✓ Granular access control ✗ Key creation on first upload       │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Verdict**: Consider for Phase 5 if regulatory requirements demand it.

#### Option D: Client-Side Encryption (Zero-Knowledge)

```
┌─────────────────────────────────────────────────────────────────┐
│                   Client-Side Encryption                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                       CLIENT SIDE                            ││
│  │  ┌─────────┐   ┌─────────────┐   ┌─────────────────────┐    ││
│  │  │ Plain   │──▶│ Generate    │──▶│ Encrypt with DEK    │    ││
│  │  │ File    │   │ Data Key    │   │ (AES-256)           │    ││
│  │  └─────────┘   │ (DEK)       │   └──────────┬──────────┘    ││
│  │                └──────┬──────┘              │               ││
│  │                       │                     │               ││
│  │                ┌──────▼──────┐              │               ││
│  │                │ Encrypt DEK │              │               ││
│  │                │ with KEK    │              │               ││
│  │                │ (from KMS)  │              │               ││
│  │                └──────┬──────┘              │               ││
│  │                       │                     │               ││
│  └───────────────────────┼─────────────────────┼───────────────┘│
│                          │                     │                 │
│                          ▼                     ▼                 │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                       S3                                     ││
│  │  ┌─────────────────────────────────────────────────────────┐││
│  │  │ Encrypted File + Encrypted DEK (metadata)               │││
│  │  │ (S3 cannot decrypt - no access to KEK)                  │││
│  │  └─────────────────────────────────────────────────────────┘││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  CRITICAL TRADE-OFFS:                                            │
│  ✗ Server-side operations impossible:                            │
│    - No virus scanning (file encrypted)                          │
│    - No AI/OCR extraction                                        │
│    - No thumbnail generation                                     │
│    - No content-based search                                     │
│  ✗ Key recovery is complex (user loses access → data lost)      │
│  ✗ Higher client-side compute requirements                       │
│                                                                  │
│  When to use:                                                    │
│  - Regulatory requirement for zero-knowledge                     │
│  - User-controlled access is paramount                           │
│  - Server-side features not needed                               │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Verdict**: Only if zero-knowledge is a hard regulatory requirement. Breaks AI features.

### 4.2 Encryption Recommendation

```
┌─────────────────────────────────────────────────────────────────┐
│              RECOMMENDED ENCRYPTION ROADMAP                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  PHASE 1 (MVP)                    PHASE 2 (Security)            │
│  ┌───────────────────────┐       ┌───────────────────────┐      │
│  │       SSE-S3          │──────▶│      SSE-KMS          │      │
│  │  • Zero config        │       │  • Single CMK         │      │
│  │  • Quick start        │       │  • CloudTrail audit   │      │
│  │  • Acceptable for MVP │       │  • IAM access control │      │
│  └───────────────────────┘       └───────────────────────┘      │
│                                           │                      │
│                                           ▼                      │
│  PHASE 5+ (If Required)                                         │
│  ┌───────────────────────┐                                      │
│  │   Per-Member Keys     │                                      │
│  │  • Maximum isolation  │                                      │
│  │  • Higher cost        │                                      │
│  │  • Complex management │                                      │
│  └───────────────────────┘                                      │
│                                                                  │
│  NOTE: Client-side encryption NOT recommended due to:           │
│  - Breaks malware scanning                                       │
│  - Breaks AI metadata extraction                                 │
│  - Complex key recovery                                          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 4.3 Malware Scanning Options

#### Option 1: AWS GuardDuty Malware Protection (Recommended)

```
┌─────────────────────────────────────────────────────────────────┐
│                 GuardDuty Malware Protection                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌────────┐   ┌────────┐   ┌───────────┐   ┌───────────────────┐│
│  │   S3   │──▶│GuardDuty│──▶│EventBridge│──▶│    Lambda         ││
│  │ Upload │   │  Scan   │   │   Rule    │   │ Update MongoDB    ││
│  └────────┘   └────────┘   └───────────┘   └───────────────────┘│
│                                                                  │
│  Pricing (as of 2024):                                          │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ Volume (GB/month)    │ Price per GB                        │ │
│  ├────────────────────────────────────────────────────────────┤ │
│  │ First 500 GB         │ $1.04                               │ │
│  │ 500 GB - 5 TB        │ $0.52                               │ │
│  │ 5 TB+                │ $0.26                               │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  Example Cost (10K docs/month @ 2MB avg = 20GB):                │
│  20 GB × $1.04 = $20.80/month                                   │
│                                                                  │
│  Implementation:                                                 │
│  1. Enable GuardDuty in AWS Console                             │
│  2. Enable Malware Protection for S3                            │
│  3. Add EventBridge rule for scan results                       │
│  4. Create Lambda to update MongoDB status                      │
│                                                                  │
│  Pros:                           Cons:                          │
│  ✓ AWS native                    ✗ Async (not blocking)        │
│  ✓ No infrastructure             ✗ 1-3 min scan time           │
│  ✓ Auto threat updates           ✗ Cost per GB                 │
│  ✓ EventBridge integration                                      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

#### Option 2: ClamAV Lambda (Self-Managed)

```
┌─────────────────────────────────────────────────────────────────┐
│                     ClamAV Lambda                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌────────┐   ┌────────────────┐   ┌───────────────────────────┐│
│  │   S3   │──▶│ S3 Event       │──▶│ Lambda (ClamAV)           ││
│  │ Upload │   │ Notification   │   │ • Download file           ││
│  └────────┘   └────────────────┘   │ • Scan with ClamAV        ││
│                                    │ • Update MongoDB          ││
│                                    │ • Delete if infected      ││
│                                    └───────────────────────────┘│
│                                                                  │
│  Challenges:                                                     │
│  • Lambda /tmp limited to 512MB (10GB with EFS)                 │
│  • ClamAV definitions ~300MB (Lambda layer)                     │
│  • 15 min timeout for large files                               │
│  • Must update ClamAV definitions regularly                     │
│                                                                  │
│  Cost: ~$20-50/month for Lambda compute                         │
│                                                                  │
│  Pros:                           Cons:                          │
│  ✓ Lower cost at scale           ✗ Maintain ClamAV updates     │
│  ✓ Full control                  ✗ Lambda size limits          │
│  ✓ Can be synchronous            ✗ More complex setup          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 4.4 Malware Scanning Recommendation

**Use GuardDuty Malware Protection** because:
1. Native AWS integration
2. No maintenance required
3. Automatic threat intelligence updates
4. Reasonable cost (~$20-40/month at expected scale)

**Important**: Block downloads until scan completes:

```java
// In DocumentService.getDownloadUrl()
if (doc.getScanStatus() == ScanStatus.NOT_SCANNED ||
    doc.getScanStatus() == ScanStatus.SCANNING) {
    return Mono.error(new DocumentNotReadyException(
        "Document is being scanned. Please try again in a few minutes."));
}
if (doc.getScanStatus() == ScanStatus.INFECTED) {
    return Mono.error(new DocumentQuarantinedException(
        "Document failed security scan and cannot be downloaded."));
}
```

### 4.5 Access Control Matrix

```
┌────────────────────────────────────────────────────────────────────────────┐
│                         ACCESS CONTROL MATRIX                               │
├────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Persona             │ Own Docs │ Managed Member │ Any Member │ Delegates  │
│  ────────────────────┼──────────┼────────────────┼────────────┼────────────│
│  SELF                │    ✓     │       -        │     -      │     -      │
│  DELEGATE            │    -     │    ✓ (ROI)     │     -      │ ROI+DAA+RPR│
│  AGENT               │    -     │       -        │     ✓      │     -      │
│  CASE_WORKER         │    -     │       -        │     ✓      │     -      │
│  CONFIG_SPECIALIST   │    -     │       -        │     -      │   BLOCKED  │
│                                                                             │
│  Legend:                                                                    │
│  ✓       = Full access (upload, download, delete)                          │
│  ✓ (ROI) = Access requires ROI permission                                  │
│  -       = No access                                                        │
│  BLOCKED = Endpoint returns 403                                             │
│                                                                             │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. API Specification

### 5.1 Endpoints Overview

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/v1/documents/upload/initiate` | Get presigned upload URL | Required |
| POST | `/api/v1/documents/upload/finalize` | Complete upload | Required |
| POST | `/api/v1/documents/list` | List documents | Required |
| POST | `/api/v1/documents/download` | Get presigned download URL | Required |
| POST | `/api/v1/documents/delete` | Soft-delete document | Required |
| POST | `/api/v1/documents/detail` | Get document metadata | Required |
| GET | `/api/v1/documents/categories` | Get active categories | Public |

All endpoints except `/categories` have `@MfeEnabled` for partner access.

### 5.2 Initiate Upload

**Request**:
```http
POST /api/v1/documents/upload/initiate
Content-Type: application/json
Cookie: BFF_SESSION=...

{
  "enterpriseId": "ENT123456",      // Required for DELEGATE/AGENT/CASE_WORKER
  "filename": "insurance_card.pdf",
  "contentType": "application/pdf",
  "fileSizeBytes": 1048576
}
```

**Response** (200 OK):
```json
{
  "uploadId": "550e8400-e29b-41d4-a716-446655440000",
  "presignedUrl": "https://bff-documents-prod.s3.amazonaws.com/temp/...?X-Amz-...",
  "presignedUrlExpiresAt": "2024-01-15T10:30:00Z",
  "uploadExpiresAt": "2024-01-15T14:15:00Z",
  "maxFileSizeBytes": 26214400,
  "requiredContentType": "application/pdf"
}
```

**Validation**:
- `filename`: Required, max 255 chars
- `contentType`: Must be in allowed list
- `fileSizeBytes`: 1 byte to 25MB

**Error Responses**:
- `400`: Validation error
- `401`: Not authenticated
- `403`: Not authorized (persona check)
- `429`: Too many concurrent uploads (max 4)

### 5.3 Finalize Upload

**Request**:
```http
POST /api/v1/documents/upload/finalize
Content-Type: application/json

{
  "enterpriseId": "ENT123456",
  "uploadId": "550e8400-e29b-41d4-a716-446655440000",
  "category": "INSURANCE_CARD",
  "title": "Blue Cross Card 2024",
  "description": "Front and back scan"
}
```

**Response** (200 OK):
```json
{
  "documentId": "660e8400-e29b-41d4-a716-446655440001",
  "filename": "insurance_card.pdf",
  "category": "INSURANCE_CARD",
  "status": "PENDING_SCAN",
  "createdAt": "2024-01-15T10:16:00Z"
}
```

**Error Responses**:
- `400`: Upload not found, expired, or already finalized
- `400`: File not uploaded to S3
- `400`: Invalid category
- `403`: Upload belongs to different user

### 5.4 List Documents

**Request**:
```http
POST /api/v1/documents/list
Content-Type: application/json

{
  "enterpriseId": "ENT123456",
  "page": 0,
  "size": 20,
  "category": "INSURANCE_CARD"  // Optional filter
}
```

**Response** (200 OK):
```json
{
  "documents": [
    {
      "documentId": "660e8400-e29b-41d4-a716-446655440001",
      "filename": "insurance_card.pdf",
      "category": "INSURANCE_CARD",
      "title": "Blue Cross Card 2024",
      "fileSizeBytes": 1048576,
      "contentType": "application/pdf",
      "status": "ACTIVE",
      "scanStatus": "CLEAN",
      "createdAt": "2024-01-15T10:16:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalRecords": 45,
  "totalPages": 3,
  "hasNext": true,
  "hasPrevious": false
}
```

### 5.5 Download Document

**Request**:
```http
POST /api/v1/documents/download
Content-Type: application/json

{
  "enterpriseId": "ENT123456",
  "documentId": "660e8400-e29b-41d4-a716-446655440001"
}
```

**Response** (200 OK):
```json
{
  "presignedUrl": "https://bff-documents-prod.s3.amazonaws.com/docs/...?X-Amz-...",
  "expiresAt": "2024-01-15T10:21:00Z",
  "filename": "insurance_card.pdf",
  "contentType": "application/pdf",
  "fileSizeBytes": 1048576
}
```

**Error Responses**:
- `400`: Document not found
- `403`: Access denied
- `409`: Document pending scan (retry later)
- `410`: Document has been deleted
- `422`: Document failed security scan

### 5.6 Delete Document

**Request**:
```http
POST /api/v1/documents/delete
Content-Type: application/json

{
  "enterpriseId": "ENT123456",
  "documentId": "660e8400-e29b-41d4-a716-446655440001"
}
```

**Response** (200 OK):
```json
{
  "success": true,
  "documentId": "660e8400-e29b-41d4-a716-446655440001",
  "deletedAt": "2024-01-15T11:00:00Z"
}
```

### 5.7 Get Categories

**Request**:
```http
GET /api/v1/documents/categories
```

**Response** (200 OK):
```json
{
  "categories": [
    {
      "id": "MARKSHEET",
      "displayName": "Marksheet/Academic Records",
      "description": "Educational documents and transcripts"
    },
    {
      "id": "PRESCRIPTION",
      "displayName": "Medicine Prescription",
      "description": "Doctor prescriptions and medication records"
    }
    // ... more categories
  ]
}
```

---

## 6. Data Models

### 6.1 DocumentMetadataDoc (MongoDB)

```java
@Document(collection = "documents")
@CompoundIndexes({
    @CompoundIndex(name = "owner_status_idx", def = "{'ownerEnterpriseId': 1, 'status': 1}"),
    @CompoundIndex(name = "owner_category_idx", def = "{'ownerEnterpriseId': 1, 'category': 1}")
})
public class DocumentMetadataDoc {

    @Id
    private String id;                      // UUID

    // Ownership
    @Indexed
    private String ownerEnterpriseId;       // Document owner (always the member)

    // Uploader (may differ from owner)
    private String uploaderId;              // ID of who uploaded
    private MemberIdType uploaderIdType;    // HSID, MSID, OHID
    private Persona uploaderPersona;        // SELF, DELEGATE, AGENT, CASE_WORKER

    // S3 location
    private String s3Bucket;
    private String s3Key;

    // File metadata
    private String originalFilename;
    private String contentType;
    private Long fileSizeBytes;

    // Classification
    private String category;                // Category ID from document_categories
    private String title;                   // User-provided (optional)
    private String description;             // User-provided (optional)
    private List<String> tags;              // For search (future)

    // Status
    private DocumentStatus status;          // ACTIVE, DELETED, PENDING_SCAN, QUARANTINED
    private ScanStatus scanStatus;          // NOT_SCANNED, SCANNING, CLEAN, INFECTED

    // Timestamps
    @CreatedDate
    @Indexed
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    // AI/ML fields (Phase 4)
    private String aiProcessingStatus;      // PENDING, PROCESSING, COMPLETE, FAILED
    private Map<String, Object> extractedMetadata;
    private List<Double> vectorEmbedding;   // 1536 dimensions for ada-002
    private String ocrText;
    private Instant aiProcessedAt;
}
```

### 6.2 TempUploadDoc (MongoDB)

```java
@Document(collection = "temp_uploads")
@CompoundIndex(name = "uploader_status_idx", def = "{'uploaderId': 1, 'uploadStatus': 1}")
public class TempUploadDoc {

    @Id
    private String id;                      // uploadId (UUID)

    // S3 location
    private String s3Key;

    // Uploader info
    private String uploaderId;
    private MemberIdType uploaderIdType;
    private Persona uploaderPersona;

    // Target owner
    private String targetOwnerEid;

    // File info
    private String originalFilename;
    private String contentType;
    private Long expectedFileSizeBytes;

    // Status
    private UploadStatus uploadStatus;      // PENDING, UPLOADED, FINALIZED, EXPIRED

    // Timing
    @CreatedDate
    private Instant createdAt;

    private Instant presignedUrlExpiresAt;

    @Indexed(expireAfter = "4h")            // TTL index
    private Instant expiresAt;
}
```

### 6.3 DocumentCategoryDoc (MongoDB)

```java
@Document(collection = "document_categories")
public class DocumentCategoryDoc {

    @Id
    private String id;                      // e.g., "PRESCRIPTION"

    private String displayName;             // e.g., "Medicine Prescription"
    private String description;
    private boolean active;
    private int sortOrder;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
```

### 6.4 Enums

```java
public enum DocumentStatus {
    ACTIVE,         // Document available
    DELETED,        // Soft-deleted by user
    PENDING_SCAN,   // Awaiting malware scan
    QUARANTINED     // Failed malware scan
}

public enum ScanStatus {
    NOT_SCANNED,    // Scan not started
    SCANNING,       // Scan in progress
    CLEAN,          // Passed scan
    INFECTED        // Failed scan (malware detected)
}

public enum UploadStatus {
    PENDING,        // Presigned URL generated, waiting for upload
    UPLOADED,       // File uploaded to S3
    FINALIZED,      // Moved to permanent storage
    EXPIRED,        // Temp upload expired
    FAILED          // Upload or move failed
}
```

---

## 7. Infrastructure

### 7.1 AWS Resources

#### S3 Bucket

```yaml
Resource: S3Bucket
Properties:
  BucketName: bff-documents-${env}

  # Encryption
  BucketEncryption:
    ServerSideEncryptionConfiguration:
      - ServerSideEncryptionByDefault:
          SSEAlgorithm: aws:kms              # Phase 2: Use KMS
          KMSMasterKeyID: !Ref DocumentsKMSKey

  # Block public access
  PublicAccessBlockConfiguration:
    BlockPublicAcls: true
    BlockPublicPolicy: true
    IgnorePublicAcls: true
    RestrictPublicBuckets: true

  # Lifecycle rules
  LifecycleConfiguration:
    Rules:
      - Id: TempCleanup
        Prefix: temp/
        Status: Enabled
        ExpirationInDays: 1                   # Safety net (Phase 2: 4h via Lambda)
        AbortIncompleteMultipartUpload:
          DaysAfterInitiation: 1

      - Id: IntelligentTiering
        Prefix: docs/
        Status: Enabled
        Transitions:
          - TransitionInDays: 90
            StorageClass: INTELLIGENT_TIERING

  # Versioning (for recovery)
  VersioningConfiguration:
    Status: Enabled

  # Access logging
  LoggingConfiguration:
    DestinationBucketName: !Ref LogsBucket
    LogFilePrefix: s3-access-logs/
```

#### KMS Key (Phase 2)

```yaml
Resource: DocumentsKMSKey
Properties:
  Description: Encryption key for document storage
  EnableKeyRotation: true
  KeyPolicy:
    Version: '2012-10-17'
    Statement:
      - Sid: AllowBFFService
        Effect: Allow
        Principal:
          AWS: !GetAtt BFFServiceRole.Arn
        Action:
          - kms:Encrypt
          - kms:Decrypt
          - kms:GenerateDataKey
          - kms:DescribeKey
        Resource: '*'

      - Sid: DenyAdminDecrypt
        Effect: Deny
        Principal:
          AWS: !Sub 'arn:aws:iam::${AWS::AccountId}:role/AdminRole'
        Action:
          - kms:Decrypt
        Resource: '*'
        Condition:
          StringNotLike:
            kms:ViaService: 's3.*.amazonaws.com'
```

#### GuardDuty (Phase 2)

```yaml
# Enable via AWS Console or CLI:
# aws guardduty create-detector --enable
# aws guardduty update-malware-scan-settings --detector-id <id> --scan-resource-criteria ...

# EventBridge Rule
Resource: GuardDutyScanResultRule
Properties:
  EventPattern:
    source:
      - aws.guardduty
    detail-type:
      - GuardDuty Malware Protection Object Scan Result
    detail:
      s3ObjectDetails:
        bucketName:
          - !Ref DocumentsBucket
  Targets:
    - Id: UpdateDocumentStatus
      Arn: !GetAtt ScanStatusUpdaterLambda.Arn
```

### 7.2 MongoDB Atlas

```javascript
// Collections to create:

// 1. documents
db.createCollection("documents")
db.documents.createIndex({ "ownerEnterpriseId": 1, "status": 1 })
db.documents.createIndex({ "ownerEnterpriseId": 1, "category": 1 })
db.documents.createIndex({ "createdAt": 1 })

// 2. temp_uploads
db.createCollection("temp_uploads")
db.temp_uploads.createIndex({ "uploaderId": 1, "uploadStatus": 1 })
db.temp_uploads.createIndex({ "expiresAt": 1 }, { expireAfterSeconds: 0 })  // TTL

// 3. document_categories
db.createCollection("document_categories")

// 4. document_audit (Phase 3)
db.createCollection("document_audit")
db.document_audit.createIndex({ "documentId": 1 })
db.document_audit.createIndex({ "timestamp": 1 })

// Vector Search Index (Phase 4)
// Create via Atlas UI or API
{
  "mappings": {
    "dynamic": false,
    "fields": {
      "vectorEmbedding": {
        "type": "knnVector",
        "dimensions": 1536,
        "similarity": "cosine"
      },
      "ownerEnterpriseId": {
        "type": "token"
      }
    }
  }
}
```

---

## 8. Implementation Phases

### Phase 1: MVP with Full Security

**Duration**: 3-4 sprints
**Goal**: Working document management with production-ready security

#### Scope

| Component | Description |
|-----------|-------------|
| Endpoints | initiate, finalize, list, download, delete, categories |
| Encryption | **SSE-KMS** with dedicated CMK |
| Scanning | **GuardDuty Malware Protection** |
| Download | **Block until scan=CLEAN** |
| Metadata | MongoDB collections (documents, temp_uploads, document_categories) |
| Access | Persona-based authorization (SELF, DELEGATE, AGENT, CASE_WORKER) |
| Cleanup | S3 lifecycle (24h for temp files) |

#### Deliverables
1. `DocumentController` with 6 endpoints
2. `DocumentService` with business logic
3. `S3StorageClient` for presigned URLs (with KMS)
4. KMS key: `alias/bff-documents-key`
5. MongoDB repositories and documents
6. GuardDuty Malware Protection enabled
7. EventBridge rule for scan results
8. Lambda to update MongoDB with scan status
9. Download blocking until scan=CLEAN
10. Unit tests (80%+ coverage)
11. Integration tests

#### Risk Level: LOW
- Full encryption from day 1
- Malware scanning included
- No infected file downloads possible

---

### Phase 2: Compliance & Polish

**Duration**: 1-2 sprints
**Goal**: Audit trail and production polish

#### Scope

| Component | Description |
|-----------|-------------|
| Audit | Full audit trail (document_audit collection) |
| Validation | Content-type and file size verification on finalize |
| Logging | Enhanced structured logging |
| Performance | Optimization and monitoring |

#### Deliverables
1. `document_audit` MongoDB collection
2. Audit service and API
3. Enhanced validation in finalize flow
4. Performance testing and optimization

---

### Phase 3: AI Integration

**Duration**: 2-3 sprints
**Goal**: Intelligent document understanding

#### Scope

| Component | Description |
|-----------|-------------|
| OCR | AWS Textract for text extraction |
| Metadata | LLM-based metadata extraction |
| Embeddings | Vector embeddings for semantic search |
| Search | MongoDB Atlas Vector Search |

#### Deliverables
1. AI processing pipeline (async)
2. Textract integration
3. Claude/GPT integration for metadata
4. Embedding generation
5. Semantic search API
6. AI status tracking in documents

---

### Phase 4: Advanced Features

**Duration**: 2+ sprints
**Goal**: Enhanced functionality

#### Scope

| Component | Description |
|-----------|-------------|
| Versioning | Document version history |
| Thumbnails | Image thumbnail generation |
| Privacy | Per-member KMS keys (if required) |
| Dedup | Duplicate detection warning |
| Cleanup | Precise 4h cleanup (Lambda) |

---

## 9. Cost Analysis

### 9.1 Monthly Cost Estimate

**Assumptions**:
- 1,000 active users
- 10 documents/user/month = 10,000 uploads
- Average file size: 2MB
- 5 downloads/document/month = 50,000 downloads

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          MONTHLY COST BREAKDOWN                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Component              │ Calculation                     │ Cost/Month     │
│  ───────────────────────┼─────────────────────────────────┼───────────────│
│  S3 Storage             │ 240GB × $0.023/GB               │ $5.52          │
│  S3 PUT Requests        │ 10K × $0.005/1K                 │ $0.05          │
│  S3 GET Requests        │ 50K × $0.0004/1K                │ $0.02          │
│  S3 Data Transfer       │ 100GB × $0.09/GB                │ $9.00          │
│  ───────────────────────┼─────────────────────────────────┼───────────────│
│  S3 Subtotal            │                                 │ $14.59         │
│  ───────────────────────┼─────────────────────────────────┼───────────────│
│                                                                              │
│  KMS Key                │ 1 key × $1/month                │ $1.00          │
│  KMS Requests           │ 60K × $0.03/10K                 │ $0.18          │
│  ───────────────────────┼─────────────────────────────────┼───────────────│
│  KMS Subtotal           │                                 │ $1.18          │
│  ───────────────────────┼─────────────────────────────────┼───────────────│
│                                                                              │
│  GuardDuty Malware      │ 20GB × $1.04/GB                 │ $20.80         │
│  ───────────────────────┼─────────────────────────────────┼───────────────│
│                                                                              │
│  Lambda (cleanup)       │ 10K invocations, 100ms          │ $0.50          │
│  ───────────────────────┼─────────────────────────────────┼───────────────│
│                                                                              │
│  MongoDB Atlas          │ Existing cluster (no extra)     │ $0.00          │
│  ───────────────────────┼─────────────────────────────────┼───────────────│
│                                                                              │
│  PHASE 1 TOTAL          │ S3 + Lambda                     │ ~$15/month     │
│  PHASE 2 TOTAL          │ + KMS + GuardDuty               │ ~$37/month     │
│                                                                              │
│  At Scale (100K users)  │ Linear scaling                  │ ~$400/month    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 9.2 Cost Optimization

| Strategy | Savings | When to Apply |
|----------|---------|---------------|
| S3 Intelligent-Tiering | 40% on old docs | Documents > 90 days |
| Reserved MongoDB | 30-50% | Committed usage |
| Regional S3 endpoints | Reduced transfer | Production |
| Compression | 30-50% storage | Future enhancement |

---

## 10. Risk Assessment

### 10.1 Risk Matrix

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Malware upload (Phase 1) | Medium | High | Expedite Phase 2, UI warning |
| S3 presigned URL leak | Low | High | Short TTL (5-15 min), HTTPS only |
| Unauthorized access | Low | Critical | Persona validation, audit logs |
| Data loss | Very Low | Critical | S3 versioning, backups |
| Cost overrun | Low | Medium | Monitoring, alerts, lifecycle rules |
| Performance degradation | Low | Medium | Presigned URLs bypass BFF |

### 10.2 Security Considerations

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          SECURITY CHECKLIST                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Phase 1:                                                                    │
│  [x] Persona-based access control                                           │
│  [x] Presigned URL with short TTL                                           │
│  [x] HTTPS only                                                              │
│  [x] Input validation (size, type)                                          │
│  [x] Soft delete (data recovery)                                            │
│  [ ] Malware scanning (Phase 2)                                             │
│                                                                              │
│  Phase 2:                                                                    │
│  [ ] KMS encryption                                                          │
│  [ ] GuardDuty malware scanning                                              │
│  [ ] Block download until scan complete                                      │
│  [ ] Content-type verification on finalize                                   │
│  [ ] File size verification on finalize                                      │
│                                                                              │
│  Phase 3:                                                                    │
│  [ ] Comprehensive audit logging                                             │
│  [ ] Audit log retention policy                                              │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 11. Decisions (FINALIZED)

| ID | Decision | Final Choice | Status |
|----|----------|--------------|--------|
| D1 | Encryption strategy | **SSE-KMS single key** (Phase 1) | **DECIDED** |
| D2 | Malware scanning | **GuardDuty** (Phase 1) | **DECIDED** |
| D3 | Download behavior | **Block until scan=CLEAN** | **DECIDED** |
| D4 | Temp cleanup timing | **24h S3 lifecycle** | **DECIDED** |
| D5 | Category management | **MongoDB collection** | **DECIDED** |
| D6 | AI integration scope | Full (Phase 3) | **DECIDED** |

---

## 12. Appendix

### 12.1 File Structure

```
src/main/java/com/chanakya/bff/document/
├── controller/
│   └── DocumentController.java
├── service/
│   ├── DocumentService.java
│   ├── DocumentAccessValidator.java
│   ├── DocumentCategoryService.java
│   └── TempUploadCleanupService.java
├── client/
│   └── S3StorageClient.java
├── repository/
│   ├── DocumentMetadataRepository.java
│   ├── TempUploadRepository.java
│   └── DocumentCategoryRepository.java
├── document/
│   ├── DocumentMetadataDoc.java
│   ├── TempUploadDoc.java
│   └── DocumentCategoryDoc.java
├── model/
│   ├── DocumentStatus.java
│   ├── ScanStatus.java
│   ├── UploadStatus.java
│   └── request/
│   │   ├── InitiateUploadRequest.java
│   │   ├── FinalizeUploadRequest.java
│   │   ├── DocumentListRequest.java
│   │   ├── DocumentDownloadRequest.java
│   │   └── DocumentDeleteRequest.java
│   └── response/
│       ├── InitiateUploadResponse.java
│       ├── FinalizeUploadResponse.java
│       ├── DocumentListResponse.java
│       ├── DocumentDownloadResponse.java
│       └── DocumentDeleteResponse.java
└── config/
    └── AwsConfig.java
```

### 12.2 Configuration Properties

```yaml
# application.yml additions
bff:
  document:
    s3-bucket: ${DOCUMENT_S3_BUCKET}
    s3-region: ${AWS_REGION:us-east-1}
    presigned-upload-ttl-minutes: ${DOCUMENT_UPLOAD_TTL:15}
    presigned-download-ttl-minutes: ${DOCUMENT_DOWNLOAD_TTL:5}
    max-file-size-mb: ${DOCUMENT_MAX_SIZE_MB:25}
    max-concurrent-uploads: ${DOCUMENT_MAX_CONCURRENT:4}
    temp-expiry-hours: ${DOCUMENT_TEMP_EXPIRY_HOURS:4}
    kms-key-id: ${DOCUMENT_KMS_KEY_ID:}
    allowed-content-types:
      - application/pdf
      - image/jpeg
      - image/png
      - image/tiff
      - application/vnd.openxmlformats-officedocument.wordprocessingml.document
      - application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
```

### 12.3 Maven Dependencies

```xml
<!-- AWS SDK v2 for S3 -->
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
</dependency>
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>netty-nio-client</artifactId>
</dependency>

<!-- AWS SDK BOM -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bom</artifactId>
            <version>2.40.14</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

*Document Version: 1.0*
*Created: 2024-01-XX*
*Last Updated: 2024-01-XX*
