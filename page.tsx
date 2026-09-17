"use client";

import {
    useEffect,
    useMemo,
    useState,
    type CSSProperties,
    type Dispatch,
    type ReactNode,
    type SetStateAction,
} from "react";

import { useApiClient } from "../../../../../../packages/api-client";

import {
    EmptyState,
    LoadingSpinner,
} from "../../../../../../packages/ui";

import type {
    ApplyToMode,
    CreateFeeRequest,
    Fee,
    FeeCategory,
    FeeCategoryOption,
    FeeDetail,
    FeeFormStatus,
    FeeStatus,
    PenaltyInfo,
    StudentSearchResult,
    UpdateFeeRequest,
} from "./types";

/* -------------------------------------------------------------------------- */
/* Constants                                                                  */
/* -------------------------------------------------------------------------- */

const PAGE_SIZE = 25;

const BASE_CATEGORIES: FeeCategoryOption[] = [
    {
        code: "Tuition",
        displayName: "Tuition",
        priority: 1,
    },
    {
        code: "Books",
        displayName: "Books",
        priority: 2,
    },
    {
        code: "Activity",
        displayName: "Activity",
        priority: 3,
    },
    {
        code: "Bus",
        displayName: "Bus",
        priority: 4,
    },
];

const PENALTY_CATEGORY: FeeCategoryOption = {
    code: "Penalty",
    displayName: "Penalty",
    priority: 5,
};

const STATUS_OPTIONS: FeeStatus[] = [
    "Active",
    "Paid",
    "Partial",
    "Outstanding",
    "Overdue",
    "Draft",
];

/* -------------------------------------------------------------------------- */
/* Helpers                                                                    */
/* -------------------------------------------------------------------------- */

function formatEGP(
    value: number | null | undefined
) {
    const amount = Number(value ?? 0);

    return `${amount.toLocaleString("en-US", {
        maximumFractionDigits: 2,
    })} EGP`;
}

function formatDate(
    value?: string | null
) {
    if (!value) {
        return "—";
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return value;
    }

    return date.toLocaleDateString("en-GB", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
    });
}

/*
 * Protects against the thousand-scaled demo values that appeared
 * earlier in the Fee Management screen.
 */
function normalizeFeeAmounts(
    fee: Fee
): Fee {
    const original = Number(
        fee.originalAmountEGP ?? 0
    );

    const paid = Number(
        fee.paidEGP ?? 0
    );

    const remaining = Number(
        fee.remainingEGP ?? 0
    );

    const looksScaled =
        original > 0 &&
        paid >= 0 &&
        remaining >= 0 &&
        Math.abs(
            paid +
            remaining -
            original * 1000
        ) <
        Math.max(
            original * 1000 * 0.01,
            1
        );

    if (!looksScaled) {
        return fee;
    }

    return {
        ...fee,
        paidEGP: paid / 1000,
        remainingEGP:
            remaining / 1000,
        totalDueEGP:
            fee.totalDueEGP !==
            undefined
                ? fee.totalDueEGP / 1000
                : fee.totalDueEGP,
    };
}

function getStatusColors(
    status: string
) {
    const normalized =
        status.toLowerCase();

    if (
        normalized ===
        "overdue"
    ) {
        return {
            background: "#fff1f2",
            color: "#dc2626",
            border: "#fecdd3",
        };
    }

    if (
        normalized ===
        "paid"
    ) {
        return {
            background: "#ecfdf5",
            color: "#059669",
            border: "#bbf7d0",
        };
    }

    if (
        normalized ===
        "partial"
    ) {
        return {
            background: "#eff6ff",
            color: "#2563eb",
            border: "#bfdbfe",
        };
    }

    if (
        normalized ===
        "outstanding"
    ) {
        return {
            background: "#fff7ed",
            color: "#ea580c",
            border: "#fed7aa",
        };
    }

    if (
        normalized ===
        "draft"
    ) {
        return {
            background: "#f8fafc",
            color: "#64748b",
            border: "#cbd5e1",
        };
    }

    return {
        background: "#ecfdf5",
        color: "#059669",
        border: "#bbf7d0",
    };
}

function getCategoryColors(
    category: string
) {
    const normalized =
        category.toLowerCase();

    if (
        normalized ===
        "tuition"
    ) {
        return {
            background: "#eef2ff",
            color: "#29498a",
            border: "#c7d2fe",
        };
    }

    if (
        normalized ===
        "activity"
    ) {
        return {
            background: "#ecfeff",
            color: "#0891b2",
            border: "#a5f3fc",
        };
    }

    if (
        normalized ===
        "bus"
    ) {
        return {
            background: "#faf5ff",
            color: "#9333ea",
            border: "#e9d5ff",
        };
    }

    if (
        normalized ===
        "books"
    ) {
        return {
            background: "#fffbeb",
            color: "#d97706",
            border: "#fde68a",
        };
    }

    if (
        normalized ===
        "penalty"
    ) {
        return {
            background: "#fff1f2",
            color: "#ef4444",
            border: "#fecdd3",
        };
    }

    return {
        background: "#f8fafc",
        color: "#475569",
        border: "#cbd5e1",
    };
}

function getProgress(
    fee: Fee
) {
    const original = Number(
        fee.originalAmountEGP ?? 0
    );

    const paid = Number(
        fee.paidEGP ?? 0
    );

    if (original <= 0) {
        return 0;
    }

    return Math.min(
        100,
        Math.max(
            0,
            (paid / original) *
            100
        )
    );
}

function hasPenalty(
    fee: Fee
) {
    const category =
        String(
            fee.category
        ).toLowerCase();

    const original = Number(
        fee.originalAmountEGP ?? 0
    );

    const totalDue = Number(
        fee.totalDueEGP ?? 0
    );

    return (
        category ===
        "penalty" ||
        fee.penaltyApplied ===
        true ||
        (totalDue > 0 &&
            totalDue >
            original)
    );
}

/* -------------------------------------------------------------------------- */
/* Main page                                                                  */
/* -------------------------------------------------------------------------- */

export default function FeeManagementPage() {
    const api = useApiClient();

    const [
        fees,
        setFees,
    ] = useState<Fee[]>([]);

    const [
        categories,
        setCategories,
    ] = useState<
        FeeCategoryOption[]
    >([
        ...BASE_CATEGORIES,
        PENALTY_CATEGORY,
    ]);

    const [
        loading,
        setLoading,
    ] = useState(true);

    const [
        detailLoading,
        setDetailLoading,
    ] = useState(false);

    const [
        saving,
        setSaving,
    ] = useState(false);

    const [
        error,
        setError,
    ] = useState("");

    const [
        selectedFee,
        setSelectedFee,
    ] = useState<FeeDetail | null>(
        null
    );

    const [
        penaltyInfo,
        setPenaltyInfo,
    ] = useState<PenaltyInfo | null>(
        null
    );

    const [
        showForm,
        setShowForm,
    ] = useState(false);

    const [
        editingFee,
        setEditingFee,
    ] = useState<Fee | null>(
        null
    );

    /* ------------------------------------------------------------------------ */
    /* Filters                                                                  */
    /* ------------------------------------------------------------------------ */

    const [
        search,
        setSearch,
    ] = useState("");

    const [
        studentId,
        setStudentId,
    ] = useState("");

    const [
        category,
        setCategory,
    ] = useState("");

    const [
        status,
        setStatus,
    ] = useState("");

    const [
        dueDateFrom,
        setDueDateFrom,
    ] = useState("");

    const [
        dueDateTo,
        setDueDateTo,
    ] = useState("");

    const [
        page,
        setPage,
    ] = useState(0);

    const [
        totalPages,
        setTotalPages,
    ] = useState(1);

    const [
        totalRecords,
        setTotalRecords,
    ] = useState(0);

    /* ------------------------------------------------------------------------ */
    /* Add/Edit form                                                            */
    /* ------------------------------------------------------------------------ */

    const [
        applyTo,
        setApplyTo,
    ] = useState<ApplyToMode>(
        "ALL_ACTIVE"
    );

    const [
        formStudentId,
        setFormStudentId,
    ] = useState("");

    const [
        formStudentName,
        setFormStudentName,
    ] = useState("");

    const [
        formName,
        setFormName,
    ] = useState("");

    const [
        formCategory,
        setFormCategory,
    ] = useState<FeeCategory>(
        "Tuition"
    );

    const [
        formAmount,
        setFormAmount,
    ] = useState("");

    const [
        formDueDate,
        setFormDueDate,
    ] = useState("");

    const [
        formTerm,
        setFormTerm,
    ] = useState("");

    const [
        formStatus,
        setFormStatus,
    ] = useState<FeeFormStatus>(
        "Draft"
    );

    const [
        formError,
        setFormError,
    ] = useState("");

    /* ------------------------------------------------------------------------ */
    /* Student search                                                           */
    /* ------------------------------------------------------------------------ */

    const [
        studentSearch,
        setStudentSearch,
    ] = useState("");

    const [
        studentResults,
        setStudentResults,
    ] = useState<
        StudentSearchResult[]
    >([]);

    const [
        studentSearchLoading,
        setStudentSearchLoading,
    ] = useState(false);

    /* ------------------------------------------------------------------------ */
    /* Load categories                                                          */
    /* ------------------------------------------------------------------------ */

    const loadCategories =
        async () => {
            try {
                const response: any =
                    await api.get(
                        "/fee-categories"
                    );

                let loaded: FeeCategoryOption[] =
                    [];

                if (
                    Array.isArray(
                        response
                    )
                ) {
                    loaded =
                        response;
                } else if (
                    Array.isArray(
                        response?.data
                    )
                ) {
                    loaded =
                        response.data;
                }

                const filtered =
                    loaded.filter(
                        (item) =>
                            item.code !==
                            "Penalty"
                    );

                setCategories([
                    ...filtered,
                    PENALTY_CATEGORY,
                ]);
            } catch {
                setCategories([
                    ...BASE_CATEGORIES,
                    PENALTY_CATEGORY,
                ]);
            }
        };

    /* ------------------------------------------------------------------------ */
    /* Load fees                                                                */
    /* ------------------------------------------------------------------------ */

    const loadFees =
        async () => {
            setLoading(true);
            setError("");

            try {
                const params =
                    new URLSearchParams();

                if (
                    search.trim()
                ) {
                    params.set(
                        "search",
                        search.trim()
                    );
                }

                if (
                    studentId.trim()
                ) {
                    params.set(
                        "studentId",
                        studentId.trim()
                    );
                }

                if (category) {
                    params.set(
                        "category",
                        category
                    );
                }

                if (status) {
                    params.set(
                        "status",
                        status
                    );
                }

                if (dueDateFrom) {
                    params.set(
                        "dueDateFrom",
                        dueDateFrom
                    );
                }

                if (dueDateTo) {
                    params.set(
                        "dueDateTo",
                        dueDateTo
                    );
                }

                params.set(
                    "page",
                    String(page)
                );

                params.set(
                    "pageSize",
                    String(PAGE_SIZE)
                );

                const response: any =
                    await api.get(
                        `/fees?${params.toString()}`
                    );

                const rawFees: Fee[] =
                    Array.isArray(
                        response
                    )
                        ? response
                        : Array.isArray(
                            response?.data
                        )
                            ? response.data
                            : [];

                const normalized =
                    rawFees.map(
                        normalizeFeeAmounts
                    );

                setFees(normalized);

                const total =
                    Number(
                        response?.total ??
                        normalized.length
                    );

                const pages =
                    Number(
                        response?.totalPages ??
                        Math.max(
                            1,
                            Math.ceil(
                                total /
                                PAGE_SIZE
                            )
                        )
                    );

                setTotalRecords(
                    total
                );

                setTotalPages(
                    Math.max(
                        1,
                        pages
                    )
                );
            } catch (err: any) {
                setError(
                    err?.message ||
                    "Failed to load fees."
                );

                setFees([]);
                setTotalRecords(0);
                setTotalPages(1);
            } finally {
                setLoading(false);
            }
        };

    useEffect(() => {
        loadCategories();
    }, []);

    useEffect(() => {
        loadFees();
    }, [
        page,
        search,
        studentId,
        category,
        status,
        dueDateFrom,
        dueDateTo,
    ]);

    /* ------------------------------------------------------------------------ */
    /* Student search                                                           */
    /* ------------------------------------------------------------------------ */

    useEffect(() => {
        if (
            applyTo !==
            "ONE_STUDENT"
        ) {
            setStudentResults([]);
            return;
        }

        const value =
            studentSearch.trim();

        if (!value) {
            setStudentResults([]);
            return;
        }

        const timer =
            setTimeout(
                async () => {
                    setStudentSearchLoading(
                        true
                    );

                    try {
                        const response: any =
                            await api.get(
                                `/students/search?activeOnly=true&q=${encodeURIComponent(
                                    value
                                )}`
                            );

                        const results: StudentSearchResult[] =
                            Array.isArray(
                                response
                            )
                                ? response
                                : Array.isArray(
                                    response?.data
                                )
                                    ? response.data
                                    : [];

                        setStudentResults(
                            results
                        );
                    } catch {
                        setStudentResults(
                            []
                        );
                    } finally {
                        setStudentSearchLoading(
                            false
                        );
                    }
                },
                300
            );

        return () =>
            clearTimeout(timer);
    }, [
        studentSearch,
        applyTo,
    ]);

    /* ------------------------------------------------------------------------ */
    /* Statistics                                                               */
    /* ------------------------------------------------------------------------ */

    const statistics =
        useMemo(() => {
            return {
                total:
                totalRecords,

                active:
                fees.filter(
                    (fee) =>
                        String(
                            fee.status
                        ).toLowerCase() ===
                        "active"
                ).length,

                overdue:
                fees.filter(
                    (fee) =>
                        String(
                            fee.status
                        ).toLowerCase() ===
                        "overdue"
                ).length,

                penalties:
                fees.filter(
                    hasPenalty
                ).length,
            };
        }, [
            fees,
            totalRecords,
        ]);

    /* ------------------------------------------------------------------------ */
    /* Form reset                                                               */
    /* ------------------------------------------------------------------------ */

    const resetForm =
        () => {
            setApplyTo(
                "ALL_ACTIVE"
            );

            setFormStudentId("");
            setFormStudentName("");
            setFormName("");
            setFormCategory(
                "Tuition"
            );
            setFormAmount("");
            setFormDueDate("");
            setFormTerm("");
            setFormStatus(
                "Draft"
            );
            setFormError("");

            setStudentSearch("");
            setStudentResults([]);
        };

    /* ------------------------------------------------------------------------ */
    /* Add form                                                                 */
    /* ------------------------------------------------------------------------ */

    const openAddForm =
        () => {
            setEditingFee(null);
            resetForm();
            setShowForm(true);
        };

    /* ------------------------------------------------------------------------ */
    /* Edit form                                                                */
    /* ------------------------------------------------------------------------ */

    const openEditForm =
        async (
            fee: Fee
        ) => {
            setEditingFee(
                fee
            );

            setApplyTo(
                "ONE_STUDENT"
            );

            setFormStudentId(
                fee.studentId
            );

            setFormStudentName(
                fee.studentName ||
                ""
            );

            setFormName(
                fee.name
            );

            setFormCategory(
                (fee.category as FeeCategory) ||
                "Tuition"
            );

            setFormAmount(
                String(
                    fee.originalAmountEGP ??
                    ""
                )
            );

            setFormDueDate(
                fee.dueDate ||
                ""
            );

            setFormTerm(
                fee.term ||
                ""
            );

            setFormStatus(
                fee.status ===
                "Draft"
                    ? "Draft"
                    : "Active"
            );

            setFormError("");

            try {
                const detail: any =
                    await api.get(
                        `/fees/${fee.id}`
                    );

                if (
                    detail?.term
                ) {
                    setFormTerm(
                        detail.term
                    );
                }

                if (
                    detail?.studentName
                ) {
                    setFormStudentName(
                        detail.studentName
                    );
                }

                if (
                    detail?.studentId
                ) {
                    setFormStudentId(
                        detail.studentId
                    );
                }
            } catch {
                // Keep list data if detail cannot be loaded.
            }

            setShowForm(
                true
            );
        };

    const closeForm =
        () => {
            if (saving) {
                return;
            }

            setShowForm(
                false
            );

            setEditingFee(
                null
            );

            resetForm();
        };

    /* ------------------------------------------------------------------------ */
    /* Validate                                                                 */
    /* ------------------------------------------------------------------------ */

    const validateForm =
        () => {
            if (
                !editingFee &&
                applyTo ===
                "ONE_STUDENT" &&
                !formStudentId
            ) {
                return "Please select a student.";
            }

            if (!formName.trim()) {
                return "Fee name is required.";
            }

            if (!formCategory) {
                return "Fee category is required.";
            }

            if (
                formCategory ===
                "Penalty"
            ) {
                return "Penalty fees are generated by the system and cannot be manually added.";
            }

            const amount =
                Number(
                    formAmount
                );

            if (
                !Number.isFinite(
                    amount
                ) ||
                amount <= 0
            ) {
                return "Amount must be greater than 0.";
            }

            if (!formDueDate) {
                return "Due date is required.";
            }

            if (!formTerm.trim()) {
                return "Term / period is required.";
            }

            if (
                editingFee
            ) {
                const paid =
                    Number(
                        editingFee.paidEGP ??
                        0
                    );

                if (
                    amount < paid
                ) {
                    return "New amount cannot be less than the amount already paid.";
                }
            }

            /*
       * Draft is displayed in the requested UI,
       * but the documented backend POST does not accept a status field.
       */
            if (
                formStatus ===
                "Draft"
            ) {
                return "Please select Active before saving the fee.";
            }

            return "";
        };

    /* ------------------------------------------------------------------------ */
    /* Save fee                                                                 */
    /* ------------------------------------------------------------------------ */

    const saveFee =
        async () => {
            const validationError =
                validateForm();

            if (
                validationError
            ) {
                setFormError(
                    validationError
                );
                return;
            }

            setSaving(true);
            setFormError("");

            try {
                const amount =
                    Number(
                        formAmount
                    );

                /* Edit */
                if (
                    editingFee
                ) {
                    const body: UpdateFeeRequest =
                        {
                            name:
                                formName.trim(),
                            category:
                            formCategory,
                            amountEGP:
                            amount,
                            term:
                                formTerm.trim(),
                            dueDate:
                            formDueDate,
                        };

                    await api.patch(
                        `/fees/${editingFee.id}`,
                        body
                    );

                    closeForm();
                    await loadFees();

                    return;
                }

                /* One student */
                if (
                    applyTo ===
                    "ONE_STUDENT"
                ) {
                    const body: CreateFeeRequest =
                        {
                            studentId:
                            formStudentId,
                            name:
                                formName.trim(),
                            category:
                            formCategory,
                            amountEGP:
                            amount,
                            term:
                                formTerm.trim(),
                            dueDate:
                            formDueDate,
                        };

                    await api.post(
                        "/fees",
                        body
                    );

                    closeForm();
                    await loadFees();

                    return;
                }

                /* All active students */
                const studentsResponse: any =
                    await api.get(
                        "/students?page=0&pageSize=1000"
                    );

                const activeStudents: StudentSearchResult[] =
                    Array.isArray(
                        studentsResponse
                    )
                        ? studentsResponse
                        : Array.isArray(
                            studentsResponse?.data
                        )
                            ? studentsResponse.data
                            : [];

                if (
                    activeStudents.length ===
                    0
                ) {
                    throw new Error(
                        "No active students were found."
                    );
                }

                let successCount =
                    0;

                let lastError =
                    "";

                for (const student of activeStudents) {
                    try {
                        const body: CreateFeeRequest =
                            {
                                studentId:
                                student.id,
                                name:
                                    formName.trim(),
                                category:
                                formCategory,
                                amountEGP:
                                amount,
                                term:
                                    formTerm.trim(),
                                dueDate:
                                formDueDate,
                            };

                        await api.post(
                            "/fees",
                            body
                        );

                        successCount++;
                    } catch (
                        studentError: any
                        ) {
                        lastError =
                            studentError?.message ||
                            "One student could not be processed.";
                    }
                }

                if (
                    successCount ===
                    0
                ) {
                    throw new Error(
                        lastError ||
                        "No fees were created."
                    );
                }

                if (
                    successCount <
                    activeStudents.length
                ) {
                    setFormError(
                        `${successCount} of ${activeStudents.length} active students received the fee.`
                    );
                } else {
                    closeForm();
                }

                await loadFees();
            } catch (err: any) {
                setFormError(
                    err?.message ||
                    "The fee could not be saved. Please check the entered data."
                );
            } finally {
                setSaving(false);
            }
        };

    /* ------------------------------------------------------------------------ */
    /* Details                                                                  */
    /* ------------------------------------------------------------------------ */

    const openDetails =
        async (
            fee: Fee
        ) => {
            setSelectedFee(
                null
            );

            setPenaltyInfo(
                null
            );

            setDetailLoading(
                true
            );

            try {
                const detail: any =
                    await api.get(
                        `/fees/${fee.id}`
                    );

                const normalizedDetail =
                    normalizeFeeAmounts(
                        detail as Fee
                    ) as FeeDetail;

                setSelectedFee(
                    normalizedDetail
                );

                if (
                    normalizedDetail.category ===
                    "Tuition"
                ) {
                    try {
                        const penalty: any =
                            await api.get(
                                `/fees/${fee.id}/penalty-info`
                            );

                        setPenaltyInfo(
                            penalty as PenaltyInfo
                        );
                    } catch {
                        setPenaltyInfo(
                            null
                        );
                    }
                }
            } catch (err: any) {
                setError(
                    err?.message ||
                    "Failed to load fee details."
                );
            } finally {
                setDetailLoading(
                    false
                );
            }
        };

    const closeDetails =
        () => {
            setSelectedFee(
                null
            );
            setPenaltyInfo(
                null
            );
        };

    /* ------------------------------------------------------------------------ */
    /* Filters                                                                  */
    /* ------------------------------------------------------------------------ */

    const updateFilter =
        (
            setter: Dispatch<
                SetStateAction<string>
            >,
            value: string
        ) => {
            setter(value);
            setPage(0);
        };

    const clearFilters =
        () => {
            setSearch("");
            setStudentId("");
            setCategory("");
            setStatus("");
            setDueDateFrom("");
            setDueDateTo("");
            setPage(0);
        };

    /* ------------------------------------------------------------------------ */
    /* Render                                                                   */
    /* ------------------------------------------------------------------------ */

    return (
        <div
            style={{
                minHeight:
                    "100%",
                background:
                    "#f5f7fb",
                padding:
                    "28px 36px 50px",
                color:
                    "#1f2937",
            }}
        >
            {/* Header */}
            <div
                style={{
                    display:
                        "flex",
                    justifyContent:
                        "space-between",
                    alignItems:
                        "center",
                    gap: 16,
                    flexWrap:
                        "wrap",
                    marginBottom:
                        24,
                }}
            >
                <div>
                    <h1
                        style={{
                            margin: 0,
                            color:
                                "#ea8a17",
                            fontSize:
                                30,
                            fontWeight:
                                700,
                        }}
                    >
                        Fee Management
                    </h1>

                    <p
                        style={{
                            margin:
                                "7px 0 0",
                            color:
                                "#7b8498",
                            fontSize:
                                14,
                        }}
                    >
                        Manage school fees,
                        due dates, payments
                        and penalties
                    </p>
                </div>

                <button
                    onClick={
                        openAddForm
                    }
                    style={{
                        border: 0,
                        borderRadius:
                            10,
                        background:
                            "#234487",
                        color:
                            "#fff",
                        padding:
                            "12px 20px",
                        fontSize:
                            16,
                        fontWeight:
                            700,
                        cursor:
                            "pointer",
                    }}
                >
                    + Add Fee
                </button>
            </div>

            {/* KPI cards */}
            <div
                style={{
                    display:
                        "grid",
                    gridTemplateColumns:
                        "repeat(4, minmax(0, 1fr))",
                    gap: 16,
                    marginBottom:
                        22,
                }}
            >
                <StatCard
                    label="Total Fees"
                    value={
                        statistics.total
                    }
                    valueColor="#234487"
                />

                <StatCard
                    label="Active"
                    value={
                        statistics.active
                    }
                    valueColor="#059669"
                />

                <StatCard
                    label="Overdue"
                    value={
                        statistics.overdue
                    }
                    valueColor="#dc2626"
                />

                <StatCard
                    label="Penalties"
                    value={
                        statistics.penalties
                    }
                    valueColor="#ef4444"
                />
            </div>

            {/* Filters */}
            <div
                style={{
                    background:
                        "#fff",
                    border:
                        "1px solid #e2e7ef",
                    borderRadius:
                        14,
                    padding: 20,
                    marginBottom:
                        20,
                }}
            >
                <div
                    style={{
                        display:
                            "grid",
                        gridTemplateColumns:
                            "minmax(220px, 2fr) minmax(160px, 1fr) minmax(170px, 1fr) minmax(170px, 1fr)",
                        gap: 12,
                    }}
                >
                    <input
                        value={search}
                        onChange={(e) =>
                            updateFilter(
                                setSearch,
                                e.target
                                    .value
                            )
                        }
                        placeholder="Search fees..."
                        style={
                            inputStyle
                        }
                    />

                    <input
                        value={studentId}
                        onChange={(e) =>
                            updateFilter(
                                setStudentId,
                                e.target
                                    .value
                            )
                        }
                        placeholder="Student ID"
                        style={
                            inputStyle
                        }
                    />

                    <select
                        value={
                            category
                        }
                        onChange={(e) =>
                            updateFilter(
                                setCategory,
                                e.target
                                    .value
                            )
                        }
                        style={
                            inputStyle
                        }
                    >
                        <option value="">
                            All Categories
                        </option>

                        {categories.map(
                            (
                                item
                            ) => (
                                <option
                                    key={
                                        item.code
                                    }
                                    value={
                                        item.code
                                    }
                                >
                                    {
                                        item.displayName
                                    }
                                </option>
                            )
                        )}
                    </select>

                    <select
                        value={status}
                        onChange={(e) =>
                            updateFilter(
                                setStatus,
                                e.target
                                    .value
                            )
                        }
                        style={
                            inputStyle
                        }
                    >
                        <option value="">
                            All Statuses
                        </option>

                        {STATUS_OPTIONS.map(
                            (
                                item
                            ) => (
                                <option
                                    key={
                                        item
                                    }
                                    value={
                                        item
                                    }
                                >
                                    {item}
                                </option>
                            )
                        )}
                    </select>
                </div>

                {/* Due dates */}
                <div
                    style={{
                        display:
                            "grid",
                        gridTemplateColumns:
                            "minmax(180px, 1fr) minmax(180px, 1fr) auto",
                        gap: 12,
                        marginTop:
                            14,
                        alignItems:
                            "end",
                    }}
                >
                    <div>
                        <label
                            style={
                                filterLabelStyle
                            }
                        >
                            Due Date From
                        </label>

                        <input
                            type="date"
                            value={
                                dueDateFrom
                            }
                            onChange={(e) =>
                                updateFilter(
                                    setDueDateFrom,
                                    e.target
                                        .value
                                )
                            }
                            style={
                                inputStyle
                            }
                        />
                    </div>

                    <div>
                        <label
                            style={
                                filterLabelStyle
                            }
                        >
                            Due Date To
                        </label>

                        <input
                            type="date"
                            value={
                                dueDateTo
                            }
                            onChange={(e) =>
                                updateFilter(
                                    setDueDateTo,
                                    e.target
                                        .value
                                )
                            }
                            style={
                                inputStyle
                            }
                        />
                    </div>

                    <button
                        onClick={
                            clearFilters
                        }
                        style={{
                            height: 46,
                            border:
                                "1px solid #d8deea",
                            borderRadius:
                                9,
                            padding:
                                "0 18px",
                            background:
                                "#fff",
                            color:
                                "#526078",
                            cursor:
                                "pointer",
                            fontWeight:
                                600,
                            whiteSpace:
                                "nowrap",
                        }}
                    >
                        Clear Filters
                    </button>
                </div>
            </div>

            {/* Count */}
            <div
                style={{
                    display:
                        "flex",
                    justifyContent:
                        "space-between",
                    alignItems:
                        "center",
                    marginBottom:
                        12,
                }}
            >
                <div
                    style={{
                        color:
                            "#69758b",
                        fontSize:
                            14,
                    }}
                >
                    {totalRecords} fee record
                    {totalRecords ===
                    1
                        ? ""
                        : "s"}
                </div>

                {!loading &&
                    fees.length >
                    0 && (
                        <div
                            style={{
                                color:
                                    "#8a94a6",
                                fontSize:
                                    13,
                            }}
                        >
                            Showing{" "}
                            {page *
                                PAGE_SIZE +
                                1}
                            –
                            {Math.min(
                                page *
                                PAGE_SIZE +
                                fees.length,
                                totalRecords
                            )}{" "}
                            of{" "}
                            {totalRecords}
                        </div>
                    )}
            </div>

            {/* Error */}
            {error && (
                <div
                    style={{
                        background:
                            "#fff1f2",
                        border:
                            "1px solid #fecdd3",
                        color:
                            "#be123c",
                        borderRadius:
                            10,
                        padding: 14,
                        marginBottom:
                            16,
                    }}
                >
                    {error}
                </div>
            )}

            {/* Table */}
            <div
                style={{
                    background:
                        "#fff",
                    border:
                        "1px solid #e5e9f1",
                    borderRadius:
                        16,
                    overflow:
                        "hidden",
                }}
            >
                {loading ? (
                    <div
                        style={{
                            padding:
                                50,
                            display:
                                "flex",
                            justifyContent:
                                "center",
                        }}
                    >
                        <LoadingSpinner />
                    </div>
                ) : fees.length ===
                0 ? (
                    <div
                        style={{
                            padding:
                                30,
                        }}
                    >
                        <EmptyState
                            title="No fees found"
                            description="Try changing your filters or add a new fee."
                        />
                    </div>
                ) : (
                    <div
                        style={{
                            overflowX:
                                "auto",
                        }}
                    >
                        <table
                            style={{
                                width:
                                    "100%",
                                minWidth:
                                    1450,
                                borderCollapse:
                                    "collapse",
                            }}
                        >
                            <thead>
                            <tr
                                style={{
                                    background:
                                        "#fafbfd",
                                    borderBottom:
                                        "1px solid #e9edf4",
                                }}
                            >
                                <TableHead>
                                    NO.
                                </TableHead>

                                <TableHead>
                                    FEE ID
                                </TableHead>

                                <TableHead>
                                    NAME
                                </TableHead>

                                <TableHead>
                                    STUDENT
                                </TableHead>

                                <TableHead>
                                    CATEGORY
                                </TableHead>

                                <TableHead>
                                    AMOUNT
                                </TableHead>

                                <TableHead>
                                    TOTAL PAID
                                </TableHead>

                                <TableHead>
                                    REMAINING
                                </TableHead>

                                <TableHead>
                                    DUE DATE
                                </TableHead>

                                <TableHead>
                                    TERM
                                </TableHead>

                                <TableHead>
                                    STATUS
                                </TableHead>

                                <TableHead>
                                    PENALTY
                                </TableHead>

                                <TableHead>
                                    ACTIONS
                                </TableHead>
                            </tr>
                            </thead>

                            <tbody>
                            {fees.map(
                                (
                                    fee,
                                    index
                                ) => {
                                    const rowNumber =
                                        page *
                                        PAGE_SIZE +
                                        index +
                                        1;

                                    const progress =
                                        getProgress(
                                            fee
                                        );

                                    const statusColors =
                                        getStatusColors(
                                            String(
                                                fee.status
                                            )
                                        );

                                    const categoryColors =
                                        getCategoryColors(
                                            String(
                                                fee.category
                                            )
                                        );

                                    const penalty =
                                        hasPenalty(
                                            fee
                                        );

                                    const penaltyAmount =
                                        Math.max(
                                            0,
                                            Number(
                                                fee.totalDueEGP ??
                                                0
                                            ) -
                                            Number(
                                                fee.originalAmountEGP ??
                                                0
                                            )
                                        );

                                    return (
                                        <tr
                                            key={
                                                fee.id ||
                                                rowNumber
                                            }
                                            style={{
                                                borderBottom:
                                                    "1px solid #eef1f5",
                                            }}
                                        >
                                            {/* NO */}
                                            <td
                                                style={{
                                                    ...cellStyle,
                                                    color:
                                                        "#8a94a6",
                                                    fontWeight:
                                                        700,
                                                    width:
                                                        55,
                                                }}
                                            >
                                                {String(
                                                    rowNumber
                                                ).padStart(
                                                    2,
                                                    "0"
                                                )}
                                            </td>

                                            {/* ID */}
                                            <td
                                                style={
                                                    cellStyle
                                                }
                                            >
                                                <button
                                                    onClick={() =>
                                                        openDetails(
                                                            fee
                                                        )
                                                    }
                                                    style={{
                                                        border:
                                                            0,
                                                        background:
                                                            "transparent",
                                                        color:
                                                            "#234487",
                                                        cursor:
                                                            "pointer",
                                                        padding:
                                                            0,
                                                        fontWeight:
                                                            700,
                                                    }}
                                                >
                                                    {
                                                        fee.id
                                                    }
                                                </button>
                                            </td>

                                            {/* NAME */}
                                            <td
                                                style={{
                                                    ...cellStyle,
                                                    minWidth:
                                                        210,
                                                }}
                                            >
                                                <div
                                                    style={{
                                                        fontWeight:
                                                            600,
                                                        color:
                                                            "#28344a",
                                                    }}
                                                >
                                                    {
                                                        fee.name
                                                    }
                                                </div>

                                                <div
                                                    style={{
                                                        display:
                                                            "flex",
                                                        alignItems:
                                                            "center",
                                                        gap: 7,
                                                        marginTop:
                                                            8,
                                                    }}
                                                >
                                                    <div
                                                        style={{
                                                            width:
                                                                120,
                                                            height:
                                                                5,
                                                            background:
                                                                "#edf0f5",
                                                            borderRadius:
                                                                99,
                                                            overflow:
                                                                "hidden",
                                                        }}
                                                    >
                                                        <div
                                                            style={{
                                                                width: `${progress}%`,
                                                                height:
                                                                    "100%",
                                                                background:
                                                                    progress >=
                                                                    100
                                                                        ? "#16a34a"
                                                                        : progress ===
                                                                        0
                                                                            ? "#e5e7eb"
                                                                            : "#234487",
                                                                borderRadius:
                                                                    99,
                                                            }}
                                                        />
                                                    </div>

                                                    <span
                                                        style={{
                                                            fontSize:
                                                                12,
                                                            color:
                                                                "#8b95a8",
                                                        }}
                                                    >
                              {Math.round(
                                  progress
                              )}
                                                        %
                            </span>
                                                </div>
                                            </td>

                                            {/* STUDENT */}
                                            <td
                                                style={
                                                    cellStyle
                                                }
                                            >
                                                <div
                                                    style={{
                                                        fontWeight:
                                                            600,
                                                        color:
                                                            "#4b5563",
                                                    }}
                                                >
                                                    {fee.studentName ||
                                                        fee.studentId}
                                                </div>

                                                <div
                                                    style={{
                                                        marginTop:
                                                            3,
                                                        fontSize:
                                                            12,
                                                        color:
                                                            "#9aa3b2",
                                                    }}
                                                >
                                                    {
                                                        fee.studentId
                                                    }
                                                </div>
                                            </td>

                                            {/* CATEGORY */}
                                            <td
                                                style={
                                                    cellStyle
                                                }
                                            >
                          <span
                              style={{
                                  display:
                                      "inline-flex",
                                  alignItems:
                                      "center",
                                  padding:
                                      "7px 13px",
                                  borderRadius:
                                      99,
                                  border:
                                      `1px solid ${categoryColors.border}`,
                                  background:
                                  categoryColors.background,
                                  color:
                                  categoryColors.color,
                                  fontWeight:
                                      600,
                                  fontSize:
                                      13,
                              }}
                          >
                            {
                                fee.category
                            }
                          </span>
                                            </td>

                                            {/* AMOUNT */}
                                            <td
                                                style={
                                                    moneyCell
                                                }
                                            >
                                                {formatEGP(
                                                    fee.originalAmountEGP
                                                )}
                                            </td>

                                            {/* PAID */}
                                            <td
                                                style={{
                                                    ...moneyCell,
                                                    color:
                                                        "#00a34a",
                                                }}
                                            >
                                                {formatEGP(
                                                    fee.paidEGP
                                                )}
                                            </td>

                                            {/* REMAINING */}
                                            <td
                                                style={{
                                                    ...moneyCell,
                                                    color:
                                                        "#ea8a17",
                                                }}
                                            >
                                                {formatEGP(
                                                    fee.remainingEGP
                                                )}
                                            </td>

                                            {/* DUE DATE */}
                                            <td
                                                style={
                                                    cellStyle
                                                }
                                            >
                                                {formatDate(
                                                    fee.dueDate
                                                )}
                                            </td>

                                            {/* TERM */}
                                            <td
                                                style={
                                                    cellStyle
                                                }
                                            >
                                                {fee.term ||
                                                    "—"}
                                            </td>

                                            {/* STATUS */}
                                            <td
                                                style={
                                                    cellStyle
                                                }
                                            >
                          <span
                              style={{
                                  display:
                                      "inline-flex",
                                  alignItems:
                                      "center",
                                  padding:
                                      "7px 13px",
                                  borderRadius:
                                      99,
                                  border:
                                      `1px solid ${statusColors.border}`,
                                  background:
                                  statusColors.background,
                                  color:
                                  statusColors.color,
                                  fontWeight:
                                      600,
                                  fontSize:
                                      13,
                              }}
                          >
                            {
                                fee.status
                            }
                          </span>
                                            </td>

                                            {/* PENALTY */}
                                            <td
                                                style={
                                                    cellStyle
                                                }
                                            >
                                                {penalty ? (
                                                    <div>
                              <span
                                  style={{
                                      display:
                                          "inline-flex",
                                      padding:
                                          "6px 10px",
                                      borderRadius:
                                          99,
                                      background:
                                          "#fff1f2",
                                      color:
                                          "#ef4444",
                                      border:
                                          "1px solid #fecdd3",
                                      fontSize:
                                          12,
                                      fontWeight:
                                          700,
                                  }}
                              >
                                Applied
                              </span>

                                                        {penaltyAmount >
                                                            0 && (
                                                                <div
                                                                    style={{
                                                                        marginTop:
                                                                            4,
                                                                        fontSize:
                                                                            12,
                                                                        color:
                                                                            "#ef4444",
                                                                    }}
                                                                >
                                                                    {formatEGP(
                                                                        penaltyAmount
                                                                    )}
                                                                </div>
                                                            )}
                                                    </div>
                                                ) : (
                                                    <span
                                                        style={{
                                                            color:
                                                                "#9aa3b2",
                                                            fontSize:
                                                                13,
                                                        }}
                                                    >
                              —
                            </span>
                                                )}
                                            </td>

                                            {/* ACTIONS */}
                                            <td
                                                style={
                                                    cellStyle
                                                }
                                            >
                                                <div
                                                    style={{
                                                        display:
                                                            "flex",
                                                        gap: 8,
                                                    }}
                                                >
                                                    <button
                                                        onClick={() =>
                                                            openDetails(
                                                                fee
                                                            )
                                                        }
                                                        style={actionButtonStyle(
                                                            "#059669"
                                                        )}
                                                    >
                                                        View
                                                    </button>

                                                    <button
                                                        onClick={() =>
                                                            openEditForm(
                                                                fee
                                                            )
                                                        }
                                                        style={actionButtonStyle(
                                                            "#ea8a17"
                                                        )}
                                                    >
                                                        Edit
                                                    </button>
                                                </div>
                                            </td>
                                        </tr>
                                    );
                                }
                            )}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>

            {/* Pagination */}
            {!loading &&
                fees.length >
                0 && (
                    <div
                        style={{
                            marginTop:
                                18,
                            display:
                                "flex",
                            justifyContent:
                                "space-between",
                            alignItems:
                                "center",
                        }}
                    >
                        <div
                            style={{
                                fontSize:
                                    13,
                                color:
                                    "#7b8498",
                            }}
                        >
                            Page{" "}
                            {page +
                                1}{" "}
                            of{" "}
                            {totalPages}
                        </div>

                        <div
                            style={{
                                display:
                                    "flex",
                                gap: 8,
                            }}
                        >
                            <button
                                disabled={
                                    page ===
                                    0
                                }
                                onClick={() =>
                                    setPage(
                                        (
                                            current
                                        ) =>
                                            current -
                                            1
                                    )
                                }
                                style={{
                                    ...paginationButton,
                                    opacity:
                                        page ===
                                        0
                                            ? 0.5
                                            : 1,
                                    cursor:
                                        page ===
                                        0
                                            ? "not-allowed"
                                            : "pointer",
                                }}
                            >
                                Previous
                            </button>

                            <button
                                disabled={
                                    page >=
                                    totalPages -
                                    1
                                }
                                onClick={() =>
                                    setPage(
                                        (
                                            current
                                        ) =>
                                            current +
                                            1
                                    )
                                }
                                style={{
                                    ...paginationButton,
                                    opacity:
                                        page >=
                                        totalPages -
                                        1
                                            ? 0.5
                                            : 1,
                                    cursor:
                                        page >=
                                        totalPages -
                                        1
                                            ? "not-allowed"
                                            : "pointer",
                                }}
                            >
                                Next
                            </button>
                        </div>
                    </div>
                )}

            {/* ================================================================== */}
            {/* DETAILS MODAL                                                     */}
            {/* ================================================================== */}

            {(selectedFee ||
                detailLoading) && (
                <Modal
                    onClose={
                        closeDetails
                    }
                >
                    {detailLoading ? (
                        <div
                            style={{
                                minHeight:
                                    240,
                                display:
                                    "flex",
                                justifyContent:
                                    "center",
                                alignItems:
                                    "center",
                            }}
                        >
                            <LoadingSpinner />
                        </div>
                    ) : selectedFee ? (
                        <div>
                            <ModalHeader
                                title={
                                    selectedFee.name
                                }
                                subtitle={
                                    selectedFee.id
                                }
                                onClose={
                                    closeDetails
                                }
                            />

                            {/* Amounts */}
                            <div
                                style={{
                                    display:
                                        "grid",
                                    gridTemplateColumns:
                                        "repeat(3, minmax(0, 1fr))",
                                    gap: 12,
                                    marginBottom:
                                        20,
                                }}
                            >
                                <InfoCard
                                    label="Original Amount"
                                    value={formatEGP(
                                        selectedFee.originalAmountEGP
                                    )}
                                />

                                <InfoCard
                                    label="Total Paid"
                                    value={formatEGP(
                                        selectedFee.paidEGP
                                    )}
                                    valueColor="#059669"
                                />

                                <InfoCard
                                    label="Remaining"
                                    value={formatEGP(
                                        selectedFee.remainingEGP
                                    )}
                                    valueColor="#ea8a17"
                                />
                            </div>

                            {/* Details */}
                            <div
                                style={{
                                    display:
                                        "grid",
                                    gridTemplateColumns:
                                        "repeat(2, minmax(0, 1fr))",
                                    gap: 14,
                                    marginBottom:
                                        22,
                                }}
                            >
                                <InfoBox
                                    label="Student"
                                    value={
                                        selectedFee.studentName ||
                                        selectedFee.studentId
                                    }
                                />

                                <InfoBox
                                    label="Student ID"
                                    value={
                                        selectedFee.studentId
                                    }
                                />

                                <InfoBox
                                    label="Category"
                                    value={String(
                                        selectedFee.category
                                    )}
                                />

                                <InfoBox
                                    label="Due Date"
                                    value={formatDate(
                                        selectedFee.dueDate
                                    )}
                                />

                                <InfoBox
                                    label="Term / Period"
                                    value={
                                        selectedFee.term ||
                                        "—"
                                    }
                                />

                                <InfoBox
                                    label="Status"
                                    value={String(
                                        selectedFee.status
                                    )}
                                />

                                <InfoBox
                                    label="Overdue"
                                    value={
                                        selectedFee.overdue
                                            ?.isOverdue
                                            ? `${selectedFee.overdue.daysOverdue} days`
                                            : "No"
                                    }
                                />

                                <InfoBox
                                    label="Penalty Applied"
                                    value={
                                        selectedFee.penalty
                                            ?.applied ||
                                        Number(
                                            penaltyInfo?.penaltyAmountEGP ??
                                            0
                                        ) > 0
                                            ? "Yes"
                                            : "No"
                                    }
                                />
                            </div>

                            {/* Tuition penalty */}
                            {selectedFee.category ===
                                "Tuition" && (
                                    <div
                                        style={{
                                            border:
                                                "1px solid #fed7aa",
                                            background:
                                                "#fffaf3",
                                            borderRadius:
                                                12,
                                            padding:
                                                18,
                                            marginBottom:
                                                22,
                                        }}
                                    >
                                        <div
                                            style={{
                                                display:
                                                    "flex",
                                                justifyContent:
                                                    "space-between",
                                                alignItems:
                                                    "center",
                                                gap: 12,
                                                marginBottom:
                                                    15,
                                            }}
                                        >
                                            <div>
                                                <h3
                                                    style={{
                                                        margin:
                                                            0,
                                                        fontSize:
                                                            18,
                                                        color:
                                                            "#c2410c",
                                                    }}
                                                >
                                                    Tuition Penalty
                                                </h3>

                                                <div
                                                    style={{
                                                        marginTop:
                                                            4,
                                                        fontSize:
                                                            12,
                                                        color:
                                                            "#9a7357",
                                                    }}
                                                >
                                                    Penalty details
                                                </div>
                                            </div>

                                            {Number(
                                                penaltyInfo?.penaltyAmountEGP ??
                                                selectedFee.penalty
                                                    ?.penaltyAmountEGP ??
                                                0
                                            ) > 0 && (
                                                <span
                                                    style={{
                                                        padding:
                                                            "6px 11px",
                                                        borderRadius:
                                                            99,
                                                        background:
                                                            "#fff1f2",
                                                        border:
                                                            "1px solid #fecdd3",
                                                        color:
                                                            "#ef4444",
                                                        fontWeight:
                                                            700,
                                                        fontSize:
                                                            12,
                                                    }}
                                                >
                        PENALTY APPLIED
                      </span>
                                            )}
                                        </div>

                                        <div
                                            style={{
                                                display:
                                                    "grid",
                                                gridTemplateColumns:
                                                    "repeat(3, minmax(0, 1fr))",
                                                gap: 12,
                                            }}
                                        >
                                            <InfoBox
                                                label="Penalty Amount"
                                                value={formatEGP(
                                                    penaltyInfo?.penaltyAmountEGP ??
                                                    selectedFee
                                                        .penalty
                                                        ?.penaltyAmountEGP ??
                                                    0
                                                )}
                                            />

                                            <InfoBox
                                                label="Grace Ended"
                                                value={
                                                    (
                                                        penaltyInfo?.graceEnded ??
                                                        selectedFee
                                                            .penalty
                                                            ?.graceEnded ??
                                                        false
                                                    )
                                                        ? "Yes"
                                                        : "No"
                                                }
                                            />

                                            <InfoBox
                                                label="Total Due"
                                                value={formatEGP(
                                                    penaltyInfo?.totalDueEGP ??
                                                    selectedFee
                                                        .penalty
                                                        ?.totalDueEGP ??
                                                    selectedFee.totalDueEGP ??
                                                    selectedFee.remainingEGP
                                                )}
                                            />

                                            <InfoBox
                                                label="Priority"
                                                value={
                                                    penaltyInfo?.priority ||
                                                    "—"
                                                }
                                            />

                                            <InfoBox
                                                label="Days To Due"
                                                value={
                                                    penaltyInfo
                                                        ? String(
                                                            penaltyInfo.daysToDue
                                                        )
                                                        : "—"
                                                }
                                            />

                                            <InfoBox
                                                label="Penalty Applied At"
                                                value={
                                                    penaltyInfo?.penaltyAppliedAt
                                                        ? formatDate(
                                                            penaltyInfo.penaltyAppliedAt
                                                        )
                                                        : selectedFee
                                                            .penalty
                                                            ?.penaltyAppliedAt
                                                            ? formatDate(
                                                                selectedFee
                                                                    .penalty
                                                                    .penaltyAppliedAt
                                                            )
                                                            : "—"
                                                }
                                            />
                                        </div>
                                    </div>
                                )}

                            {/* Payment history */}
                            <div>
                                <h3
                                    style={{
                                        margin:
                                            "0 0 12px",
                                        fontSize:
                                            18,
                                        color:
                                            "#28344a",
                                    }}
                                >
                                    Payment History
                                </h3>

                                {selectedFee
                                    .paymentHistory
                                    ?.length ? (
                                    <div
                                        style={{
                                            border:
                                                "1px solid #e6eaf0",
                                            borderRadius:
                                                10,
                                            overflow:
                                                "hidden",
                                        }}
                                    >
                                        <table
                                            style={{
                                                width:
                                                    "100%",
                                                borderCollapse:
                                                    "collapse",
                                            }}
                                        >
                                            <thead>
                                            <tr
                                                style={{
                                                    background:
                                                        "#fafbfd",
                                                }}
                                            >
                                                <TableHead>
                                                    PAYMENT ID
                                                </TableHead>

                                                <TableHead>
                                                    DATE
                                                </TableHead>

                                                <TableHead>
                                                    AMOUNT
                                                </TableHead>

                                                <TableHead>
                                                    STATUS
                                                </TableHead>
                                            </tr>
                                            </thead>

                                            <tbody>
                                            {selectedFee.paymentHistory.map(
                                                (
                                                    payment
                                                ) => (
                                                    <tr
                                                        key={
                                                            payment.paymentId
                                                        }
                                                        style={{
                                                            borderTop:
                                                                "1px solid #edf0f4",
                                                        }}
                                                    >
                                                        <td
                                                            style={
                                                                cellStyle
                                                            }
                                                        >
                                                            {
                                                                payment.paymentId
                                                            }
                                                        </td>

                                                        <td
                                                            style={
                                                                cellStyle
                                                            }
                                                        >
                                                            {formatDate(
                                                                payment.dateEGP
                                                            )}
                                                        </td>

                                                        <td
                                                            style={
                                                                moneyCell
                                                            }
                                                        >
                                                            {formatEGP(
                                                                payment.amountEGP
                                                            )}
                                                        </td>

                                                        <td
                                                            style={
                                                                cellStyle
                                                            }
                                                        >
                                                            {
                                                                payment.status
                                                            }
                                                        </td>
                                                    </tr>
                                                )
                                            )}
                                            </tbody>
                                        </table>
                                    </div>
                                ) : (
                                    <div
                                        style={{
                                            background:
                                                "#f8fafc",
                                            borderRadius:
                                                10,
                                            padding:
                                                16,
                                            color:
                                                "#718096",
                                        }}
                                    >
                                        No payment history
                                        found.
                                    </div>
                                )}
                            </div>
                        </div>
                    ) : null}
                </Modal>
            )}

            {/* ================================================================== */}
            {/* ADD / EDIT MODAL                                                   */}
            {/* ================================================================== */}

            {showForm && (
                <Modal
                    onClose={
                        closeForm
                    }
                >
                    <ModalHeader
                        title={
                            editingFee
                                ? "Edit Fee"
                                : "Add Fee"
                        }
                        subtitle={
                            editingFee
                                ? `Editing ${editingFee.id}`
                                : "Create a new student fee"
                        }
                        onClose={
                            closeForm
                        }
                    />

                    {formError && (
                        <div
                            style={{
                                background:
                                    "#fff1f2",
                                border:
                                    "1px solid #fecdd3",
                                color:
                                    "#be123c",
                                padding:
                                    12,
                                borderRadius:
                                    9,
                                marginBottom:
                                    16,
                            }}
                        >
                            {formError}
                        </div>
                    )}

                    {/* APPLY TO */}
                    {!editingFee && (
                        <div
                            style={{
                                marginBottom:
                                    20,
                            }}
                        >
                            <label
                                style={
                                    labelStyle
                                }
                            >
                                APPLY TO
                            </label>

                            <div
                                style={{
                                    display:
                                        "grid",
                                    gridTemplateColumns:
                                        "1fr 1fr",
                                    gap: 10,
                                }}
                            >
                                <button
                                    type="button"
                                    onClick={() => {
                                        setApplyTo(
                                            "ALL_ACTIVE"
                                        );

                                        setFormStudentId(
                                            ""
                                        );

                                        setFormStudentName(
                                            ""
                                        );

                                        setStudentSearch(
                                            ""
                                        );

                                        setStudentResults(
                                            []
                                        );
                                    }}
                                    style={{
                                        minHeight:
                                            54,
                                        border:
                                            applyTo ===
                                            "ALL_ACTIVE"
                                                ? "1px solid #234487"
                                                : "1px solid #dce4f0",
                                        borderRadius:
                                            10,
                                        background:
                                            applyTo ===
                                            "ALL_ACTIVE"
                                                ? "#f8fafc"
                                                : "#fff",
                                        color:
                                            "#234487",
                                        fontWeight:
                                            applyTo ===
                                            "ALL_ACTIVE"
                                                ? 700
                                                : 500,
                                        cursor:
                                            "pointer",
                                        fontSize:
                                            15,
                                    }}
                                >
                                    All Active Students
                                </button>

                                <button
                                    type="button"
                                    onClick={() => {
                                        setApplyTo(
                                            "ONE_STUDENT"
                                        );
                                    }}
                                    style={{
                                        minHeight:
                                            54,
                                        border:
                                            applyTo ===
                                            "ONE_STUDENT"
                                                ? "1px solid #234487"
                                                : "1px solid #dce4f0",
                                        borderRadius:
                                            10,
                                        background:
                                            applyTo ===
                                            "ONE_STUDENT"
                                                ? "#f8fafc"
                                                : "#fff",
                                        color:
                                            "#234487",
                                        fontWeight:
                                            applyTo ===
                                            "ONE_STUDENT"
                                                ? 700
                                                : 500,
                                        cursor:
                                            "pointer",
                                        fontSize:
                                            15,
                                    }}
                                >
                                    Select Student
                                </button>
                            </div>
                        </div>
                    )}

                    {/* STUDENT PICKER */}
                    {!editingFee &&
                        applyTo ===
                        "ONE_STUDENT" && (
                            <div
                                style={{
                                    marginBottom:
                                        20,
                                    position:
                                        "relative",
                                }}
                            >
                                <label
                                    style={{
                                        ...labelStyle,
                                        fontSize:
                                            12,
                                    }}
                                >
                                    STUDENT
                                </label>

                                <input
                                    value={
                                        formStudentName ||
                                        studentSearch
                                    }
                                    onChange={(e) => {
                                        setFormStudentName(
                                            ""
                                        );

                                        setFormStudentId(
                                            ""
                                        );

                                        setStudentSearch(
                                            e.target
                                                .value
                                        );
                                    }}
                                    placeholder="Type student name or ID..."
                                    style={{
                                        ...inputStyle,
                                        height:
                                            52,
                                    }}
                                />

                                {studentSearchLoading && (
                                    <div
                                        style={{
                                            marginTop:
                                                6,
                                            color:
                                                "#7b8498",
                                            fontSize:
                                                13,
                                        }}
                                    >
                                        Searching...
                                    </div>
                                )}

                                {!formStudentId &&
                                    studentResults.length >
                                    0 && (
                                        <div
                                            style={{
                                                position:
                                                    "absolute",
                                                top:
                                                    "100%",
                                                left: 0,
                                                right: 0,
                                                zIndex:
                                                    30,
                                                marginTop:
                                                    5,
                                                background:
                                                    "#fff",
                                                border:
                                                    "1px solid #dde3ed",
                                                borderRadius:
                                                    10,
                                                boxShadow:
                                                    "0 12px 30px rgba(20,35,70,.12)",
                                                overflow:
                                                    "hidden",
                                            }}
                                        >
                                            {studentResults.map(
                                                (
                                                    student
                                                ) => (
                                                    <button
                                                        type="button"
                                                        key={
                                                            student.id
                                                        }
                                                        onClick={() => {
                                                            setFormStudentId(
                                                                student.id
                                                            );

                                                            setFormStudentName(
                                                                student.name
                                                            );

                                                            setStudentSearch(
                                                                ""
                                                            );

                                                            setStudentResults(
                                                                []
                                                            );
                                                        }}
                                                        style={{
                                                            width:
                                                                "100%",
                                                            textAlign:
                                                                "left",
                                                            border:
                                                                0,
                                                            borderBottom:
                                                                "1px solid #edf0f4",
                                                            background:
                                                                "#fff",
                                                            padding:
                                                                "11px 13px",
                                                            cursor:
                                                                "pointer",
                                                        }}
                                                    >
                                                        <div
                                                            style={{
                                                                fontWeight:
                                                                    600,
                                                                color:
                                                                    "#28344a",
                                                            }}
                                                        >
                                                            {
                                                                student.name
                                                            }
                                                        </div>

                                                        <div
                                                            style={{
                                                                marginTop:
                                                                    2,
                                                                fontSize:
                                                                    12,
                                                                color:
                                                                    "#8690a2",
                                                            }}
                                                        >
                                                            {student.studentRef ||
                                                                student.id}

                                                            {student.grade
                                                                ? ` • ${student.grade}`
                                                                : ""}
                                                        </div>
                                                    </button>
                                                )
                                            )}
                                        </div>
                                    )}

                                {formStudentId && (
                                    <div
                                        style={{
                                            marginTop:
                                                7,
                                            padding:
                                                "9px 11px",
                                            background:
                                                "#ecfdf5",
                                            border:
                                                "1px solid #bbf7d0",
                                            color:
                                                "#059669",
                                            borderRadius:
                                                8,
                                            fontSize:
                                                13,
                                            fontWeight:
                                                600,
                                        }}
                                    >
                                        Selected:{" "}
                                        {
                                            formStudentName
                                        }
                                    </div>
                                )}
                            </div>
                        )}

                    {/* ALL ACTIVE STUDENTS */}
                    {!editingFee &&
                        applyTo ===
                        "ALL_ACTIVE" && (
                            <div
                                style={{
                                    marginBottom:
                                        20,
                                    padding:
                                        "11px 13px",
                                    background:
                                        "#f8fafc",
                                    border:
                                        "1px solid #e4e9f1",
                                    borderRadius:
                                        9,
                                    color:
                                        "#64748b",
                                    fontSize:
                                        13,
                                }}
                            >
                                This fee will be created
                                for every active student.
                            </div>
                        )}

                    {/* Existing student on edit */}
                    {editingFee && (
                        <div
                            style={{
                                marginBottom:
                                    18,
                                padding:
                                    "12px 14px",
                                background:
                                    "#f8fafc",
                                border:
                                    "1px solid #e6ebf2",
                                borderRadius:
                                    9,
                                color:
                                    "#59657b",
                                fontSize:
                                    14,
                            }}
                        >
                            Student:{" "}
                            <strong
                                style={{
                                    color:
                                        "#28344a",
                                }}
                            >
                                {formStudentName ||
                                    editingFee.studentId}
                            </strong>
                        </div>
                    )}

                    {/* FEE NAME */}
                    <div
                        style={{
                            marginBottom:
                                18,
                        }}
                    >
                        <label
                            style={
                                labelStyle
                            }
                        >
                            FEE NAME
                        </label>

                        <input
                            value={
                                formName
                            }
                            onChange={(e) =>
                                setFormName(
                                    e.target.value
                                )
                            }
                            placeholder="e.g. Tuition Q3 2026"
                            style={{
                                ...inputStyle,
                                height:
                                    52,
                            }}
                        />
                    </div>

                    {/* CATEGORY + AMOUNT */}
                    <div
                        style={{
                            display:
                                "grid",
                            gridTemplateColumns:
                                "1fr 1fr",
                            gap: 14,
                        }}
                    >
                        <div>
                            <label
                                style={
                                    labelStyle
                                }
                            >
                                CATEGORY
                            </label>

                            <select
                                value={
                                    formCategory
                                }
                                onChange={(e) =>
                                    setFormCategory(
                                        e.target
                                            .value as FeeCategory
                                    )
                                }
                                style={{
                                    ...inputStyle,
                                    height:
                                        52,
                                }}
                            >
                                {categories.map(
                                    (
                                        item
                                    ) => (
                                        <option
                                            key={
                                                item.code
                                            }
                                            value={
                                                item.code
                                            }
                                        >
                                            {
                                                item.displayName
                                            }
                                        </option>
                                    )
                                )}
                            </select>
                        </div>

                        <div>
                            <label
                                style={
                                    labelStyle
                                }
                            >
                                AMOUNT (EGP)
                            </label>

                            <input
                                type="number"
                                min="0"
                                step="0.01"
                                value={
                                    formAmount
                                }
                                onChange={(e) =>
                                    setFormAmount(
                                        e.target.value
                                    )
                                }
                                placeholder="18000"
                                style={{
                                    ...inputStyle,
                                    height:
                                        52,
                                }}
                            />
                        </div>
                    </div>

                    {/* DUE DATE + TERM */}
                    <div
                        style={{
                            display:
                                "grid",
                            gridTemplateColumns:
                                "1fr 1fr",
                            gap: 14,
                            marginTop:
                                17,
                        }}
                    >
                        <div>
                            <label
                                style={
                                    labelStyle
                                }
                            >
                                DUE DATE *
                            </label>

                            <input
                                type="date"
                                value={
                                    formDueDate
                                }
                                onChange={(e) =>
                                    setFormDueDate(
                                        e.target.value
                                    )
                                }
                                style={{
                                    ...inputStyle,
                                    height:
                                        52,
                                }}
                            />
                        </div>

                        <div>
                            <label
                                style={
                                    labelStyle
                                }
                            >
                                TERM / PERIOD
                            </label>

                            <input
                                value={
                                    formTerm
                                }
                                onChange={(e) =>
                                    setFormTerm(
                                        e.target.value
                                    )
                                }
                                placeholder="e.g. Q3 2026"
                                style={{
                                    ...inputStyle,
                                    height:
                                        52,
                                }}
                            />
                        </div>
                    </div>

                    {/* STATUS */}
                    <div
                        style={{
                            marginTop:
                                18,
                        }}
                    >
                        <label
                            style={
                                labelStyle
                            }
                        >
                            STATUS
                        </label>

                        <select
                            value={
                                formStatus
                            }
                            onChange={(e) =>
                                setFormStatus(
                                    e.target
                                        .value as FeeFormStatus
                                )
                            }
                            style={{
                                ...inputStyle,
                                height:
                                    52,
                            }}
                        >
                            <option value="Draft">
                                Draft — not yet active
                            </option>

                            <option value="Active">
                                Active — immediately available
                            </option>
                        </select>
                    </div>

                    {/* FOOTER */}
                    <div
                        style={{
                            display:
                                "flex",
                            justifyContent:
                                "flex-end",
                            gap: 10,
                            marginTop:
                                24,
                            paddingTop:
                                18,
                            borderTop:
                                "1px solid #edf0f4",
                        }}
                    >
                        <button
                            onClick={
                                closeForm
                            }
                            disabled={
                                saving
                            }
                            style={{
                                border:
                                    "1px solid #d7ddea",
                                background:
                                    "#fff",
                                color:
                                    "#59657b",
                                padding:
                                    "11px 20px",
                                borderRadius:
                                    9,
                                fontWeight:
                                    600,
                                cursor:
                                    "pointer",
                            }}
                        >
                            Cancel
                        </button>

                        <button
                            onClick={
                                saveFee
                            }
                            disabled={
                                saving
                            }
                            style={{
                                border:
                                    0,
                                background:
                                    "#234487",
                                color:
                                    "#fff",
                                padding:
                                    "11px 22px",
                                borderRadius:
                                    9,
                                fontWeight:
                                    700,
                                cursor:
                                    "pointer",
                            }}
                        >
                            {saving
                                ? "Saving..."
                                : editingFee
                                    ? "Save Changes"
                                    : "Save Fee"}
                        </button>
                    </div>
                </Modal>
            )}
        </div>
    );
}

/* -------------------------------------------------------------------------- */
/* UI Components                                                              */
/* -------------------------------------------------------------------------- */

function StatCard({
                      label,
                      value,
                      valueColor,
                  }: {
    label: string;
    value: number;
    valueColor: string;
}) {
    return (
        <div
            style={{
                background:
                    "#fff",
                border:
                    "1px solid #e7ebf2",
                borderRadius:
                    14,
                padding:
                    "18px 20px",
                boxShadow:
                    "0 2px 7px rgba(20,35,70,.04)",
            }}
        >
            <div
                style={{
                    fontSize:
                        14,
                    color:
                        "#727d90",
                    marginBottom:
                        6,
                }}
            >
                {label}
            </div>

            <div
                style={{
                    fontSize:
                        30,
                    fontWeight:
                        700,
                    color:
                    valueColor,
                }}
            >
                {value}
            </div>
        </div>
    );
}

function TableHead({
                       children,
                   }: {
    children: ReactNode;
}) {
    return (
        <th
            style={{
                padding:
                    "15px 16px",
                textAlign:
                    "left",
                fontSize:
                    12,
                color:
                    "#6f7b8f",
                fontWeight:
                    700,
                letterSpacing:
                    ".04em",
                whiteSpace:
                    "nowrap",
            }}
        >
            {children}
        </th>
    );
}

function InfoCard({
                      label,
                      value,
                      valueColor = "#28344a",
                  }: {
    label: string;
    value: string;
    valueColor?: string;
}) {
    return (
        <div
            style={{
                background:
                    "#f8fafc",
                borderRadius:
                    10,
                padding:
                    14,
            }}
        >
            <div
                style={{
                    fontSize:
                        12,
                    color:
                        "#7b8498",
                    marginBottom:
                        4,
                }}
            >
                {label}
            </div>

            <div
                style={{
                    fontWeight:
                        700,
                    color:
                    valueColor,
                    fontSize:
                        17,
                }}
            >
                {value}
            </div>
        </div>
    );
}

function InfoBox({
                     label,
                     value,
                 }: {
    label: string;
    value: string;
}) {
    return (
        <div
            style={{
                border:
                    "1px solid #e8ecf2",
                borderRadius:
                    10,
                padding:
                    13,
            }}
        >
            <div
                style={{
                    color:
                        "#7a8598",
                    fontSize:
                        12,
                    marginBottom:
                        4,
                }}
            >
                {label}
            </div>

            <div
                style={{
                    color:
                        "#293449",
                    fontWeight:
                        600,
                    fontSize:
                        14,
                }}
            >
                {value}
            </div>
        </div>
    );
}

function Modal({
                   children,
                   onClose,
               }: {
    children: ReactNode;
    onClose: () => void;
}) {
    return (
        <div
            onMouseDown={
                onClose
            }
            style={{
                position:
                    "fixed",
                inset: 0,
                zIndex:
                    1000,
                background:
                    "rgba(16,28,53,.42)",
                display:
                    "flex",
                alignItems:
                    "center",
                justifyContent:
                    "center",
                padding: 20,
            }}
        >
            <div
                onMouseDown={(e) =>
                    e.stopPropagation()
                }
                style={{
                    width:
                        "min(920px, 100%)",
                    maxHeight:
                        "90vh",
                    overflowY:
                        "auto",
                    background:
                        "#fff",
                    borderRadius:
                        18,
                    boxShadow:
                        "0 25px 70px rgba(14,31,66,.25)",
                    padding:
                        24,
                }}
            >
                {children}
            </div>
        </div>
    );
}

function ModalHeader({
                         title,
                         subtitle,
                         onClose,
                     }: {
    title: string;
    subtitle: string;
    onClose: () => void;
}) {
    return (
        <div
            style={{
                display:
                    "flex",
                justifyContent:
                    "space-between",
                alignItems:
                    "flex-start",
                gap: 12,
                marginBottom:
                    22,
            }}
        >
            <div>
                <h2
                    style={{
                        margin: 0,
                        color:
                            "#28344a",
                        fontSize:
                            23,
                    }}
                >
                    {title}
                </h2>

                <div
                    style={{
                        marginTop:
                            4,
                        color:
                            "#8290a6",
                        fontSize:
                            13,
                    }}
                >
                    {subtitle}
                </div>
            </div>

            <button
                onClick={
                    onClose
                }
                style={{
                    width: 36,
                    height: 36,
                    borderRadius:
                        9,
                    border:
                        "1px solid #e0e5ec",
                    background:
                        "#fff",
                    color:
                        "#69758a",
                    cursor:
                        "pointer",
                    fontSize:
                        20,
                }}
            >
                ×
            </button>
        </div>
    );
}

/* -------------------------------------------------------------------------- */
/* Styles                                                                     */
/* -------------------------------------------------------------------------- */

const inputStyle: CSSProperties = {
    width: "100%",
    height: 46,
    boxSizing: "border-box",
    border: "1px solid #dfe4ec",
    borderRadius: 9,
    background: "#fff",
    padding: "0 13px",
    color: "#344054",
    fontSize: 14,
    outline: "none",
};

const labelStyle: CSSProperties = {
    display: "block",
    fontSize: 13,
    fontWeight: 700,
    color: "#65738a",
    marginBottom: 8,
    letterSpacing: ".02em",
};

const filterLabelStyle: CSSProperties = {
    display: "block",
    marginBottom: 7,
    fontSize: 13,
    fontWeight: 600,
    color: "#59657b",
};

const cellStyle: CSSProperties = {
    padding: "18px 16px",
    fontSize: 14,
    color: "#657187",
    whiteSpace: "nowrap",
    verticalAlign: "middle",
};

const moneyCell: CSSProperties = {
    ...cellStyle,
    fontWeight: 600,
    color: "#28344a",
};

const paginationButton: CSSProperties = {
    border: "1px solid #d9dfeb",
    background: "#fff",
    color: "#445067",
    borderRadius: 8,
    padding: "9px 14px",
    fontWeight: 600,
};

function actionButtonStyle(
    color: string
): CSSProperties {
    return {
        border: `1px solid ${color}`,
        background: "#fff",
        color,
        borderRadius: 8,
        padding: "7px 11px",
        fontSize: 12,
        fontWeight: 700,
        cursor: "pointer",
    };
}