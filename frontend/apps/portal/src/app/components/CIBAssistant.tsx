"use client";

import { useEffect, useRef, useState } from 'react';
import type { Page } from '../types';
import {
  sendChatMessage,
  type ConversationMessage,
} from '../services/chatApi';

interface Props {
  currentUserRole: 'School Admin' | 'School Finance';
  navigate: (page: Page) => void;
}

type View =
  | 'welcome'
  | 'chat'
  | 'faq'
  | 'faq-category'
  | 'loading'
  | 'no-results'
  | 'error';

type LoadingPhase = 'searching' | 'thinking';

interface Message {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  navPage?: Page;
  navLabel?: string;
}

const NO_RESULTS_MSG =
  "I couldn't find a reliable answer to that question in the CIB Institution knowledge base. Try rephrasing your question or browse the FAQ categories below.";

const API_ERROR_MSG =
  "I'm having trouble connecting to the CIB Assistant right now. Please try again.";

interface FAQ {
  q: string;
  a: string;
  navPage?: Page;
  navLabel?: string;
}

interface FAQSection {
  label: string;
  icon: string;
  faqs: FAQ[];
  adminOnly?: boolean;
}

/*
 * FAQ browsing is UI functionality.
 * AI free-text answers are handled by Spring Boot -> Python RAG -> Ollama.
 */
const FAQ_DATA: FAQSection[] = [
  {
    label: 'Payments',
    icon: '💳',
    faqs: [
      {
        q: 'How do I view payment history?',
        a: 'Open the Payments section. Use the filters to search by reference, student, status, method, or date range.',
        navPage: 'payments' as Page,
        navLabel: 'Go to Payments',
      },
      {
        q: 'What do the payment statuses mean?',
        a: 'Successful = confirmed, Pending = awaiting confirmation, Failed = declined, Refunded = amount returned, Reversed = transaction voided. These are distinct from fee statuses.',
      },
      {
        q: 'What is a partial payment?',
        a: "A partial payment covers only part of a fee. The remaining outstanding balance stays on the student's record.",
      },
      {
        q: 'How are payments allocated across fees?',
        a: 'Payments are allocated by priority: Tuition first, then Books, Activity, and Bus. Overdue fees take precedence within each category.',
        navPage: 'payments' as Page,
        navLabel: 'Go to Payments',
      },
      {
        q: 'What is the difference between fee status and payment status?',
        a: "Payment Status reflects the transaction result. Fee Status reflects the fee's state. A payment can be Successful while the fee remains Overdue if only a partial amount was paid.",
      },
    ],
  },
  {
    label: 'Fee Upload',
    icon: '📤',
    faqs: [
      {
        q: 'How do I upload fees?',
        a: 'Go to Fee Upload, download the template, fill in all required fields including due dates, then upload the file. Review the validation results afterwards.',
        navPage: 'fee-upload' as Page,
        navLabel: 'Go to Fee Upload',
      },
      {
        q: 'Where do I get the upload template?',
        a: 'Download it from the Fee Upload screen using the "Download Template" button.',
        navPage: 'fee-upload' as Page,
        navLabel: 'Go to Fee Upload',
      },
      {
        q: 'Why were some rows rejected?',
        a: 'Rejected rows failed validation. Common causes are missing required fields, invalid student IDs, or incorrect formats. Open Upload Results to see the specific reason for each rejected row.',
      },
      {
        q: 'How do I fix and resubmit rejected rows?',
        a: 'Download the Error Report, correct the flagged issues, and resubmit only the corrected rows. Do not re-upload rows that were already accepted.',
      },
      {
        q: 'Where can I see my upload history?',
        a: 'Your upload history is available in the Fee Upload section. Each row shows the file name, date, reference, and accepted/rejected counts.',
        navPage: 'fee-upload' as Page,
        navLabel: 'Go to Fee Upload',
      },
    ],
  },
  {
    label: 'Fee Management',
    icon: '💰',
    faqs: [
      {
        q: 'What fee types are available?',
        a: 'The available fee categories are Tuition, Books, Activity, Bus, and system-generated Penalty fees. Penalty fees are linked to overdue Tuition.',
        navPage: 'fee-management' as Page,
        navLabel: 'Go to Fee Management',
      },
      {
        q: 'How do I add a fee?',
        a: 'Go to Fee Management and select Add Fee. Required information includes fee name, category, amount, term, and due date.',
        navPage: 'fee-management' as Page,
        navLabel: 'Go to Fee Management',
      },
      {
        q: 'How do I edit a fee?',
        a: 'Go to Fee Management, find the fee, and select Edit. Update the permitted fields and save the changes.',
        navPage: 'fee-management' as Page,
        navLabel: 'Go to Fee Management',
      },
      {
        q: 'What determines whether a fee is overdue?',
        a: 'A fee is Overdue when its due date has passed and it still has an outstanding balance.',
      },
      {
        q: 'When is the Tuition penalty applied?',
        a: 'A 5% penalty applies only to Tuition when it is 7 full days overdue and still has an outstanding balance. It is not applied to Books, Activity, or Bus.',
      },
    ],
  },
  {
    label: 'Reconciliation',
    icon: '🔄',
    faqs: [
      {
        q: 'How do I check reconciliation?',
        a: 'Open Reconciliation to view the reconciliation status of your school’s payment records.',
        navPage: 'reconciliation' as Page,
        navLabel: 'Go to Reconciliation',
      },
      {
        q: 'What does Reconciled mean?',
        a: 'Reconciled means the payment has been successfully matched by the bank.',
      },
      {
        q: 'What does Pending mean?',
        a: 'Pending means reconciliation has not yet been completed.',
      },
      {
        q: 'What does Unreconciled mean?',
        a: 'Unreconciled means the payment has not been successfully reconciled. The bank is reviewing the transaction.',
      },
    ],
  },
  {
    label: 'Students',
    icon: '👨‍🎓',
    faqs: [
      {
        q: 'How do I view students?',
        a: 'Open the Students section to view students belonging to your school.',
        navPage: 'students' as Page,
        navLabel: 'Go to Students',
      },
      {
        q: 'How do I search for a student?',
        a: 'Use the search and filter controls in the Students section.',
        navPage: 'students' as Page,
        navLabel: 'Go to Students',
      },
      {
        q: 'Can I see a student’s outstanding amount?',
        a: 'Yes. The student record can show fee status and outstanding amount for the student.',
      },
    ],
  },
  {
    label: 'School Users',
    icon: '👥',
    adminOnly: true,
    faqs: [
      {
        q: 'How do I add a school user?',
        a: 'Open School Users and select Add School User. Assign the appropriate School Admin or School Finance role.',
        navPage: 'users' as Page,
        navLabel: 'Go to School Users',
      },
      {
        q: 'How do I edit a school user?',
        a: 'Open School Users, select the user, choose Edit, update the permitted information, and save the changes.',
        navPage: 'users' as Page,
        navLabel: 'Go to School Users',
      },
      {
        q: 'How do I deactivate a school user?',
        a: 'Open School Users, find the user, and select Deactivate. The user will no longer be able to log in.',
        navPage: 'users' as Page,
        navLabel: 'Go to School Users',
      },
      {
        q: 'What can School Finance users access?',
        a: 'School Finance users can access fee uploads, fee management, payments, reconciliation, reports, and notifications.',
      },
    ],
  },
  {
    label: 'Reports',
    icon: '📊',
    faqs: [
      {
        q: 'What reports are available?',
        a: 'Reports cover collection information, payment information, outstanding fees, partial payments, and payment history.',
        navPage: 'reports' as Page,
        navLabel: 'Go to Reports',
      },
      {
        q: 'Can I export reports?',
        a: 'Yes. Reports can be exported from the Reports section.',
        navPage: 'reports' as Page,
        navLabel: 'Go to Reports',
      },
    ],
  },
  {
    label: 'Notifications',
    icon: '🔔',
    faqs: [
      {
        q: 'What types of notifications are there?',
        a: 'The portal can show payment confirmations, fee upload results, one-week payment reminders, and penalty alerts.',
        navPage: 'notifications' as Page,
        navLabel: 'Go to Notifications',
      },
      {
        q: 'When is a payment reminder sent?',
        a: "Exactly 7 days before a fee's due date if the fee is still outstanding. The same reminder is not sent more than once for the same fee.",
      },
      {
        q: 'What do notification statuses mean?',
        a: 'Scheduled means queued, Sent means delivered, and Failed means delivery was unsuccessful.',
      },
    ],
  },
  {
    label: 'Account & Access',
    icon: '🔐',
    faqs: [
      {
        q: 'How do I reset my password?',
        a: "Click Forgot Password on the login screen, enter your registered email, and follow the reset instructions.",
      },
      {
        q: 'What is MFA?',
        a: 'Multi-factor authentication adds a second verification step after login using a one-time verification code.',
      },
      {
        q: 'What access do I have?',
        a: 'Access depends on your authenticated School Portal role. School Admin has broader administrative access than School Finance.',
      },
    ],
  },
];

const CATEGORIES = FAQ_DATA.map(section => ({
  label: section.label,
  icon: section.icon,
  faqKey: section.label,
  adminOnly: section.adminOnly,
})).concat([{ label: 'FAQs', icon: '❓', faqKey: null as string | null }]);

const QUICK_ACTIONS = [
  { label: 'View FAQs', action: 'faq' as const },
  { label: 'Fee Upload Help', action: 'chat' as const, msg: 'How do I upload fees?' },
  { label: 'Payment Help', action: 'chat' as const, msg: 'How do I view payment history?' },
  { label: 'Check Reconciliation', action: 'chat' as const, msg: 'How do I check reconciliation?' },
  { label: 'Fee Management Help', action: 'chat' as const, msg: 'How do I manage fees?' },
  {
    label: 'School User Help',
    action: 'chat' as const,
    msg: 'How do I manage school users?',
    adminOnly: true,
  },
];

function ChatInput({
  value,
  onChange,
  onSend,
  disabled = false,
  placeholder = 'Ask me anything about the School Portal...',
  inputRef,
}: {
  value: string;
  onChange: (value: string) => void;
  onSend: () => void;
  disabled?: boolean;
  placeholder?: string;
  inputRef?: React.RefObject<HTMLInputElement | null>;
}) {
  return (
    <div className="px-3 pb-3 pt-2 flex-shrink-0 border-t border-gray-100 bg-white">
      <div className="flex items-center gap-2 border border-gray-200 rounded-xl px-3 py-2 bg-white focus-within:ring-2 focus-within:ring-[#1B3A7A]/25 focus-within:border-[#1B3A7A] transition-all">
        <input
          ref={inputRef}
          type="text"
          value={value}
          onChange={event => onChange(event.target.value)}
          onKeyDown={event => {
            if (event.key === 'Enter' && !event.shiftKey && !disabled) {
              event.preventDefault();
              onSend();
            }
          }}
          placeholder={placeholder}
          disabled={disabled}
          className="flex-1 text-sm text-gray-700 placeholder-gray-400 outline-none bg-transparent disabled:opacity-50"
        />
        <button
          onClick={onSend}
          disabled={disabled || !value.trim()}
          className="w-7 h-7 bg-[#1B3A7A] disabled:bg-gray-200 text-white rounded-lg flex items-center justify-center transition-colors flex-shrink-0"
        >
          <svg width="13" height="13" fill="none" viewBox="0 0 24 24">
            <path
              d="M22 2L11 13M22 2L15 22l-4-9-9-4 20-7z"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </button>
      </div>
    </div>
  );
}

function UserBubble({ text }: { text: string }) {
  return (
    <div className="flex justify-end">
      <div className="bg-[#1B3A7A] text-white rounded-2xl rounded-tr-sm px-4 py-2.5 max-w-[80%] text-sm leading-relaxed whitespace-pre-line">
        {text}
      </div>
    </div>
  );
}

function AssistantBubble({
  text,
  navPage,
  navLabel,
  onNav,
}: {
  text: string;
  navPage?: Page;
  navLabel?: string;
  onNav?: (page: Page) => void;
}) {
  return (
    <div className="flex items-start gap-2.5">
      <div className="w-7 h-7 rounded-full bg-[#1B3A7A] flex items-center justify-center flex-shrink-0 mt-0.5">
        <svg width="12" height="12" fill="none" viewBox="0 0 24 24">
          <path
            d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"
            stroke="white"
            strokeWidth="2"
          />
        </svg>
      </div>

      <div className="max-w-[85%] space-y-2">
        <div className="bg-gray-100 text-gray-800 rounded-2xl rounded-tl-sm px-4 py-3 text-sm leading-relaxed whitespace-pre-line">
          {text}
        </div>

        {navPage && navLabel && onNav && (
          <button
            onClick={() => onNav(navPage)}
            className="flex items-center gap-1.5 text-xs font-semibold text-[#1B3A7A] bg-[#1B3A7A]/10 hover:bg-[#1B3A7A]/20 px-3 py-1.5 rounded-lg transition-colors"
          >
            {navLabel}
            <svg width="12" height="12" fill="none" viewBox="0 0 24 24">
              <path
                d="M5 12h14M12 5l7 7-7 7"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </button>
        )}
      </div>
    </div>
  );
}

function FAQCard({
  faq,
  onNav,
}: {
  faq: FAQ;
  onNav: (page: Page) => void;
}) {
  const [open, setOpen] = useState(false);

  return (
    <div className="border border-gray-100 rounded-xl overflow-hidden bg-white shadow-sm">
      <button
        onClick={() => setOpen(value => !value)}
        className="w-full flex items-start justify-between gap-3 px-4 py-3 text-left hover:bg-gray-50 transition-colors"
      >
        <span className="text-sm font-medium text-gray-800 leading-snug">
          {faq.q}
        </span>

        <svg
          width="14"
          height="14"
          fill="none"
          viewBox="0 0 24 24"
          className={`flex-shrink-0 mt-0.5 text-gray-400 transition-transform ${
            open ? 'rotate-180' : ''
          }`}
        >
          <path
            d="M6 9l6 6 6-6"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </button>

      {open && (
        <div className="px-4 pb-3 text-sm text-gray-600 leading-relaxed whitespace-pre-line">
          {faq.a}

          {faq.navPage && faq.navLabel && (
            <button
              onClick={() => onNav(faq.navPage!)}
              className="mt-3 text-xs font-semibold text-[#1B3A7A] hover:underline"
            >
              {faq.navLabel} →
            </button>
          )}
        </div>
      )}
    </div>
  );
}

export default function CIBAssistant({
  currentUserRole,
  navigate: navToPage,
}: Props) {
  const [isOpen, setIsOpen] = useState(false);
  const [isMinimized, setIsMinimized] = useState(false);
  const [showMenu, setShowMenu] = useState(false);
  const [view, setView] = useState<View>('welcome');
  const [loadingPhase, setLoadingPhase] =
    useState<LoadingPhase>('searching');
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [activeFaqCategory, setActiveFaqCategory] = useState('');
  const [faqSearch, setFaqSearch] = useState('');

  const bottomRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, view]);

  useEffect(() => {
    if (view === 'chat' || view === 'no-results') {
      const timer = setTimeout(() => inputRef.current?.focus(), 80);
      return () => clearTimeout(timer);
    }
  }, [view]);

  const openWithView = (nextView: View) => {
    setIsOpen(true);
    setIsMinimized(false);
    setShowMenu(false);
    setView(nextView);
  };

  const goHome = () => {
    setView('welcome');
    setShowMenu(false);
    setMessages([]);
    setInputValue('');
    setFaqSearch('');
  };

  const sendMessage = async (text: string) => {
    const trimmedText = text.trim();

    if (!trimmedText) return;

    const userMessage: Message = {
      id: Date.now().toString(),
      role: 'user',
      text: trimmedText,
    };

    // Capture previous messages before adding the current user message.
    const conversationHistory: ConversationMessage[] =
      messages.slice(-10).map(message => ({
        role: message.role,
        content: message.text,
      }));

    setMessages(previous => [...previous, userMessage]);
    setInputValue('');
    setLoadingPhase('searching');
    setView('loading');

    let thinkingTimer: ReturnType<typeof setTimeout> | undefined;

    try {
      thinkingTimer = setTimeout(() => {
        setLoadingPhase('thinking');
      }, 800);

      const response = await sendChatMessage(
        trimmedText,
        conversationHistory,
      );

      if (thinkingTimer) clearTimeout(thinkingTimer);

      const assistantMessage: Message = {
        id: `${Date.now()}-assistant`,
        role: 'assistant',
        text: response.answer,
        navPage: response.navPage
          ? (response.navPage as Page)
          : undefined,
        navLabel: response.navLabel ?? undefined,
      };

      setMessages(previous => [...previous, assistantMessage]);

      if (
        !response.answer ||
        response.answer.trim() === NO_RESULTS_MSG
      ) {
        setView('no-results');
      } else {
        setView('chat');
      }
    } catch (error) {
      if (thinkingTimer) clearTimeout(thinkingTimer);

      console.error('CIB Assistant API error:', error);

      const errorMessage: Message = {
        id: `${Date.now()}-error`,
        role: 'assistant',
        text: API_ERROR_MSG,
      };

      setMessages(previous => [...previous, errorMessage]);
      setView('chat');
    } finally {
      setLoadingPhase('thinking');
    }
  };

  const handleQuickAction = (
    quickAction: (typeof QUICK_ACTIONS)[number],
  ) => {
    setShowMenu(false);

    if (quickAction.action === 'faq') {
      setView('faq');
      return;
    }

    if (quickAction.action === 'chat' && quickAction.msg) {
      setMessages([]);
      sendMessage(quickAction.msg);
    }
  };

  const handleCategoryClick = (
    category: (typeof CATEGORIES)[number],
  ) => {
    setShowMenu(false);

    if (category.faqKey === null) {
      setView('faq');
      return;
    }

    setActiveFaqCategory(category.faqKey);
    setView('faq-category');
  };

  const activeFaqs = FAQ_DATA.find(
    section => section.label === activeFaqCategory,
  );

  const filteredFaqs = faqSearch.trim()
    ? FAQ_DATA.flatMap(section => {
        if (
          section.adminOnly &&
          currentUserRole !== 'School Admin'
        ) {
          return [];
        }

        return section.faqs
          .filter(
            faq =>
              faq.q
                .toLowerCase()
                .includes(faqSearch.toLowerCase()) ||
              faq.a
                .toLowerCase()
                .includes(faqSearch.toLowerCase()),
          )
          .map(faq => ({
            ...faq,
            section: section.label,
          }));
      })
    : [];

  const visibleCategories = CATEGORIES.filter(
    category =>
      !category.adminOnly ||
      currentUserRole === 'School Admin',
  );

  const visibleQuickActions = QUICK_ACTIONS.filter(
    action =>
      !action.adminOnly ||
      currentUserRole === 'School Admin',
  );

  const BackBar = ({
    onBack,
    title,
  }: {
    onBack: () => void;
    title: string;
  }) => (
    <div className="px-4 py-2.5 border-b border-gray-100 flex items-center gap-2 flex-shrink-0 bg-white">
      <button
        onClick={onBack}
        className="text-gray-400 hover:text-gray-600 transition-colors"
      >
        <svg width="16" height="16" fill="none" viewBox="0 0 24 24">
          <path
            d="M15 18l-6-6 6-6"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </button>
      <span className="text-sm font-semibold text-gray-700">
        {title}
      </span>
    </div>
  );

  return (
    <>
      {!isOpen && (
        <button
          onClick={() => openWithView('welcome')}
          className="fixed bottom-6 right-6 z-50 w-14 h-14 bg-[#1B3A7A] hover:bg-[#152e63] text-white rounded-full shadow-2xl flex items-center justify-center transition-all duration-200 hover:scale-105"
          title="CIB Institution Assistant"
        >
          <svg width="24" height="24" fill="none" viewBox="0 0 24 24">
            <path
              d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              strokeLinejoin="round"
              fill="currentColor"
              fillOpacity="0.15"
            />
            <circle cx="9" cy="10" r="1" fill="currentColor" />
            <circle cx="12" cy="10" r="1" fill="currentColor" />
            <circle cx="15" cy="10" r="1" fill="currentColor" />
          </svg>
          <span className="absolute -top-1 -right-1 w-4 h-4 bg-[#E8871A] rounded-full border-2 border-white" />
        </button>
      )}

      {isOpen && (
        <div
          className={`fixed bottom-6 right-6 z-50 w-[390px] bg-white rounded-2xl shadow-2xl flex flex-col overflow-hidden border border-gray-100 transition-all duration-200 ${
            isMinimized ? 'h-auto' : 'h-[600px]'
          }`}
          style={{ maxHeight: 'calc(100vh - 48px)' }}
        >
          <div className="bg-[#1B3A7A] px-4 py-3.5 flex items-center gap-3 flex-shrink-0">
            <div className="w-8 h-8 rounded-full bg-white/20 flex items-center justify-center flex-shrink-0">
              <svg width="16" height="16" fill="none" viewBox="0 0 24 24">
                <path
                  d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"
                  stroke="white"
                  strokeWidth="1.8"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  fill="white"
                  fillOpacity="0.2"
                />
              </svg>
            </div>

            <div className="flex-1 min-w-0">
              <div className="text-white font-semibold text-sm leading-tight">
                CIB Institution Assistant
              </div>
              <div className="text-white/60 text-xs">
                Your AI assistant for the School Portal
              </div>
            </div>

            <div className="flex items-center gap-1">
              <button
                onClick={() => setShowMenu(value => !value)}
                className="text-white/70 hover:text-white w-7 h-7 flex items-center justify-center rounded-lg hover:bg-white/10 transition-colors"
                title="Menu"
              >
                <svg width="14" height="14" fill="none" viewBox="0 0 24 24">
                  <path
                    d="M4 6h16M4 12h16M4 18h16"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                  />
                </svg>
              </button>

              <button
                onClick={() => setIsMinimized(value => !value)}
                className="text-white/70 hover:text-white w-7 h-7 flex items-center justify-center rounded-lg hover:bg-white/10 transition-colors"
                title={isMinimized ? 'Expand' : 'Minimize'}
              >
                <svg width="14" height="14" fill="none" viewBox="0 0 24 24">
                  <path
                    d={
                      isMinimized
                        ? 'M5 15l7-7 7 7'
                        : 'M19 9l-7 7-7-7'
                    }
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              </button>

              <button
                onClick={() => {
                  setIsOpen(false);
                  goHome();
                }}
                className="text-white/70 hover:text-white w-7 h-7 flex items-center justify-center rounded-lg hover:bg-white/10 transition-colors"
                title="Close"
              >
                <svg width="14" height="14" fill="none" viewBox="0 0 24 24">
                  <path
                    d="M18 6L6 18M6 6l12 12"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                  />
                </svg>
              </button>
            </div>
          </div>

          {!isMinimized && (
            <div className="flex-1 flex flex-col min-h-0 relative">
              {showMenu && (
                <div className="absolute inset-0 bg-white z-20 flex flex-col overflow-y-auto">
                  <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100">
                    <span className="text-sm font-semibold text-gray-800">
                      Menu
                    </span>
                    <button
                      onClick={() => setShowMenu(false)}
                      className="text-gray-400 hover:text-gray-600"
                    >
                      <svg width="16" height="16" fill="none" viewBox="0 0 24 24">
                        <path
                          d="M18 6L6 18M6 6l12 12"
                          stroke="currentColor"
                          strokeWidth="2"
                          strokeLinecap="round"
                        />
                      </svg>
                    </button>
                  </div>

                  <div className="p-3 space-y-1">
                    {visibleCategories.map(category => (
                      <button
                        key={category.label}
                        onClick={() => handleCategoryClick(category)}
                        className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm text-gray-700 hover:bg-gray-50 text-left transition-colors"
                      >
                        <span className="text-base">
                          {category.icon}
                        </span>
                        <span className="font-medium flex-1">
                          {category.label}
                        </span>
                        <svg
                          width="14"
                          height="14"
                          fill="none"
                          viewBox="0 0 24 24"
                          className="text-gray-300"
                        >
                          <path
                            d="M9 18l6-6-6-6"
                            stroke="currentColor"
                            strokeWidth="2"
                            strokeLinecap="round"
                            strokeLinejoin="round"
                          />
                        </svg>
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {view === 'welcome' && (
                <div className="flex-1 flex flex-col min-h-0">
                  <div className="flex-1 overflow-y-auto p-4 space-y-4">
                    <div className="bg-gradient-to-br from-[#1B3A7A]/5 to-[#E8871A]/5 rounded-xl p-4">
                      <div className="text-2xl mb-2">👋</div>
                      <h3 className="font-bold text-gray-900 text-base">
                        Hello! How can I help you today?
                      </h3>
                      <p className="text-sm text-gray-500 mt-1">
                        What do you need help with?
                      </p>
                    </div>

                    <div>
                      <div className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
                        Quick Actions
                      </div>

                      <div className="grid grid-cols-2 gap-2">
                        {visibleQuickActions.map(action => (
                          <button
                            key={action.label}
                            onClick={() => handleQuickAction(action)}
                            className="flex items-center gap-2 px-3 py-2.5 rounded-xl border border-gray-100 bg-white hover:border-[#1B3A7A]/25 hover:bg-blue-50/30 text-left transition-all duration-150 shadow-sm"
                          >
                            <span className="text-xs font-medium text-gray-700 leading-tight">
                              {action.label}
                            </span>
                            <svg
                              width="12"
                              height="12"
                              fill="none"
                              viewBox="0 0 24 24"
                              className="ml-auto flex-shrink-0 text-gray-300"
                            >
                              <path
                                d="M5 12h14M12 5l7 7-7 7"
                                stroke="currentColor"
                                strokeWidth="2"
                                strokeLinecap="round"
                                strokeLinejoin="round"
                              />
                            </svg>
                          </button>
                        ))}
                      </div>
                    </div>

                    <div>
                      <div className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">
                        Browse by Category
                      </div>

                      <div className="grid grid-cols-2 gap-1.5">
                        {visibleCategories.map(category => (
                          <button
                            key={category.label}
                            onClick={() => handleCategoryClick(category)}
                            className="flex items-center gap-2 px-3 py-2 rounded-lg hover:bg-gray-50 text-left text-sm text-gray-600 hover:text-gray-900 transition-colors"
                          >
                            <span className="text-sm">
                              {category.icon}
                            </span>
                            <span className="text-xs font-medium">
                              {category.label}
                            </span>
                          </button>
                        ))}
                      </div>
                    </div>

                    <div className="flex items-center gap-2">
                      <div className="flex-1 h-px bg-gray-100" />
                      <span className="text-xs text-gray-400 font-medium">
                        or ask me a question
                      </span>
                      <div className="flex-1 h-px bg-gray-100" />
                    </div>
                  </div>

                  <ChatInput
                    value={inputValue}
                    onChange={setInputValue}
                    onSend={() => sendMessage(inputValue)}
                  />
                </div>
              )}

              {view === 'faq' && (
                <div className="flex-1 flex flex-col min-h-0">
                  <BackBar
                    onBack={goHome}
                    title="Frequently Asked Questions"
                  />

                  <div className="flex-1 overflow-y-auto p-3">
                    <div className="relative mb-3">
                      <svg
                        width="14"
                        height="14"
                        viewBox="0 0 24 24"
                        fill="none"
                        className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400"
                      >
                        <circle
                          cx="11"
                          cy="11"
                          r="8"
                          stroke="currentColor"
                          strokeWidth="1.8"
                        />
                        <path
                          d="M21 21l-4.35-4.35"
                          stroke="currentColor"
                          strokeWidth="1.8"
                          strokeLinecap="round"
                        />
                      </svg>

                      <input
                        type="text"
                        value={faqSearch}
                        onChange={event =>
                          setFaqSearch(event.target.value)
                        }
                        placeholder="Search your question..."
                        className="w-full border border-gray-200 rounded-lg pl-8 pr-3 py-2 text-sm placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-[#1B3A7A]/30 focus:border-[#1B3A7A]"
                      />
                    </div>

                    {faqSearch.trim() ? (
                      filteredFaqs.length > 0 ? (
                        <div className="space-y-2">
                          {filteredFaqs.map((faq, index) => (
                            <FAQCard
                              key={`${faq.section}-${index}`}
                              faq={faq}
                              onNav={navToPage}
                            />
                          ))}
                        </div>
                      ) : (
                        <div className="text-center py-8 text-gray-500 text-sm">
                          No results found for "{faqSearch}"
                        </div>
                      )
                    ) : (
                      <div className="space-y-1.5">
                        {FAQ_DATA.filter(
                          section =>
                            !section.adminOnly ||
                            currentUserRole === 'School Admin',
                        ).map(section => (
                          <button
                            key={section.label}
                            onClick={() => {
                              setActiveFaqCategory(section.label);
                              setView('faq-category');
                            }}
                            className="w-full flex items-center gap-3 px-3.5 py-3 rounded-xl border border-gray-100 hover:border-[#1B3A7A]/20 hover:bg-blue-50/20 text-left transition-colors"
                          >
                            <span className="text-base">
                              {section.icon}
                            </span>
                            <span className="text-sm font-medium text-gray-700 flex-1">
                              {section.label}
                            </span>
                            <span className="text-xs text-gray-400">
                              {section.faqs.length}
                            </span>
                            <svg
                              width="12"
                              height="12"
                              fill="none"
                              viewBox="0 0 24 24"
                            >
                              <path
                                d="M9 18l6-6-6-6"
                                stroke="#9CA3AF"
                                strokeWidth="2"
                                strokeLinecap="round"
                                strokeLinejoin="round"
                              />
                            </svg>
                          </button>
                        ))}
                      </div>
                    )}
                  </div>

                  <ChatInput
                    value={inputValue}
                    onChange={setInputValue}
                    onSend={() => sendMessage(inputValue)}
                  />
                </div>
              )}

              {view === 'faq-category' && activeFaqs && (
                <div className="flex-1 flex flex-col min-h-0">
                  <div className="px-4 py-2.5 border-b border-gray-100 flex items-center gap-2 flex-shrink-0 bg-white">
                    <button
                      onClick={() => setView('faq')}
                      className="text-gray-400 hover:text-gray-600 transition-colors"
                    >
                      <svg width="16" height="16" fill="none" viewBox="0 0 24 24">
                        <path
                          d="M15 18l-6-6 6-6"
                          stroke="currentColor"
                          strokeWidth="2"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                        />
                      </svg>
                    </button>
                    <span className="text-base">
                      {activeFaqs.icon}
                    </span>
                    <span className="font-semibold text-gray-800 text-sm">
                      {activeFaqs.label}
                    </span>
                  </div>

                  <div className="flex-1 overflow-y-auto p-3 space-y-2">
                    {activeFaqs.faqs.map((faq, index) => (
                      <FAQCard
                        key={index}
                        faq={faq}
                        onNav={navToPage}
                      />
                    ))}
                  </div>

                  <ChatInput
                    value={inputValue}
                    onChange={setInputValue}
                    onSend={() => sendMessage(inputValue)}
                  />
                </div>
              )}

              {view === 'chat' && (
                <div className="flex-1 flex flex-col min-h-0">
                  <BackBar onBack={goHome} title="Chat" />

                  <div className="flex-1 overflow-y-auto p-4 space-y-3">
                    <AssistantBubble
                      text="Hello! Ask me anything about the School Portal — fees, payments, uploads, or users."
                    />

                    {messages.map(message =>
                      message.role === 'user' ? (
                        <UserBubble
                          key={message.id}
                          text={message.text}
                        />
                      ) : (
                        <AssistantBubble
                          key={message.id}
                          text={message.text}
                          navPage={message.navPage}
                          navLabel={message.navLabel}
                          onNav={page => {
                            navToPage(page);
                            setIsOpen(false);
                          }}
                        />
                      ),
                    )}

                    <div ref={bottomRef} />
                  </div>

                  <ChatInput
                    inputRef={inputRef}
                    value={inputValue}
                    onChange={setInputValue}
                    onSend={() => sendMessage(inputValue)}
                  />
                </div>
              )}

              {view === 'loading' && (
                <div className="flex-1 flex flex-col min-h-0">
                  <BackBar onBack={goHome} title="Chat" />

                  <div className="flex-1 overflow-y-auto p-4 space-y-3">
                    <AssistantBubble
                      text="Hello! Ask me anything about the School Portal — fees, payments, uploads, or users."
                    />

                    {messages.map(message =>
                      message.role === 'user' ? (
                        <UserBubble
                          key={message.id}
                          text={message.text}
                        />
                      ) : (
                        <AssistantBubble
                          key={message.id}
                          text={message.text}
                        />
                      ),
                    )}

                    <div className="flex items-start gap-2.5">
                      <div className="w-7 h-7 rounded-full bg-[#1B3A7A] flex items-center justify-center flex-shrink-0 mt-0.5">
                        <svg width="12" height="12" fill="none" viewBox="0 0 24 24">
                          <path
                            d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z"
                            stroke="white"
                            strokeWidth="2"
                          />
                        </svg>
                      </div>

                      <div className="bg-gray-100 rounded-2xl rounded-tl-sm px-4 py-3">
                        <div className="flex items-center gap-2">
                          {loadingPhase === 'searching' ? (
                            <>
                              <svg
                                width="12"
                                height="12"
                                fill="none"
                                viewBox="0 0 24 24"
                                className="text-[#1B3A7A] animate-spin flex-shrink-0"
                              >
                                <circle
                                  cx="12"
                                  cy="12"
                                  r="10"
                                  stroke="currentColor"
                                  strokeWidth="2"
                                  strokeDasharray="40 20"
                                />
                              </svg>
                              <span className="text-xs text-[#1B3A7A] font-medium">
                                Searching the CIB knowledge base...
                              </span>
                            </>
                          ) : (
                            <>
                              <div className="flex gap-1">
                                {[0, 1, 2].map(index => (
                                  <div
                                    key={index}
                                    className="w-1.5 h-1.5 bg-[#1B3A7A]/60 rounded-full animate-bounce"
                                    style={{
                                      animationDelay: `${index * 150}ms`,
                                    }}
                                  />
                                ))}
                              </div>
                              <span className="text-xs text-gray-500 font-medium">
                                CIB Assistant is thinking...
                              </span>
                            </>
                          )}
                        </div>
                      </div>
                    </div>

                    <div ref={bottomRef} />
                  </div>

                  <ChatInput
                    value=""
                    onChange={() => undefined}
                    onSend={() => undefined}
                    disabled
                  />
                </div>
              )}

              {view === 'no-results' && (
                <div className="flex-1 flex flex-col min-h-0">
                  <BackBar onBack={goHome} title="Chat" />

                  <div className="flex-1 overflow-y-auto p-4 space-y-3">
                    <AssistantBubble
                      text="Hello! Ask me anything about the School Portal — fees, payments, uploads, or users."
                    />

                    {messages.map(message =>
                      message.role === 'user' ? (
                        <UserBubble
                          key={message.id}
                          text={message.text}
                        />
                      ) : null,
                    )}

                    <AssistantBubble text={NO_RESULTS_MSG} />

                    <div className="flex gap-2 flex-wrap">
                      <button
                        onClick={() => {
                          setMessages([]);
                          setView('faq');
                        }}
                        className="text-xs bg-[#1B3A7A] text-white px-3.5 py-2 rounded-lg font-semibold hover:bg-[#152e63] transition-colors"
                      >
                        Browse FAQs
                      </button>

                      <button
                        onClick={goHome}
                        className="text-xs bg-white border border-gray-200 text-gray-700 px-3.5 py-2 rounded-lg font-semibold hover:bg-gray-50 transition-colors"
                      >
                        Back to Menu
                      </button>
                    </div>

                    <div ref={bottomRef} />
                  </div>

                  <ChatInput
                    inputRef={inputRef}
                    value={inputValue}
                    onChange={setInputValue}
                    onSend={() => sendMessage(inputValue)}
                    placeholder="Try rephrasing your question..."
                  />
                </div>
              )}

              {view === 'error' && (
                <div className="flex-1 flex flex-col items-center justify-center p-6 text-center">
                  <div className="w-12 h-12 rounded-full bg-red-50 flex items-center justify-center mb-4">
                    <svg width="22" height="22" fill="none" viewBox="0 0 24 24">
                      <circle
                        cx="12"
                        cy="12"
                        r="10"
                        stroke="#DC2626"
                        strokeWidth="1.8"
                      />
                      <path
                        d="M12 8v4M12 16h.01"
                        stroke="#DC2626"
                        strokeWidth="1.8"
                        strokeLinecap="round"
                      />
                    </svg>
                  </div>

                  <h4 className="font-semibold text-gray-900 mb-1">
                    Something went wrong
                  </h4>

                  <p className="text-sm text-gray-500 mb-5 leading-relaxed">
                    {API_ERROR_MSG}
                  </p>

                  <div className="flex gap-2">
                    <button
                      onClick={() => {
                        setMessages([]);
                        setView('chat');
                      }}
                      className="text-sm bg-[#1B3A7A] text-white px-4 py-2 rounded-lg font-semibold hover:bg-[#152e63] transition-colors"
                    >
                      Try Again
                    </button>

                    <button
                      onClick={goHome}
                      className="text-sm bg-white border border-gray-200 text-gray-700 px-4 py-2 rounded-lg font-semibold hover:bg-gray-50 transition-colors"
                    >
                      Back to Menu
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </>
  );
}
