export interface ChatResponse {
  answer: string;
  source?: string | null;
  confidence?: number | null;
  navPage?: string | null;
  navLabel?: string | null;
}

export interface ConversationMessage {
  role: 'user' | 'assistant';
  content: string;
}

export interface ChatRequest {
  message: string;
  conversationHistory: ConversationMessage[];
}

const API_URL =
  process.env.NEXT_PUBLIC_API_URL ||
  'http://localhost:8080/api/chat';

export async function sendChatMessage(
  message: string,
  conversationHistory: ConversationMessage[],
): Promise<ChatResponse> {
  const trimmedMessage = message.trim();

  if (!trimmedMessage) {
    throw new Error('Message cannot be empty.');
  }

  const requestBody: ChatRequest = {
    message: trimmedMessage,
    conversationHistory: conversationHistory.slice(-10),
  };

  const response = await fetch(API_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(requestBody),
  });

  if (!response.ok) {
    throw new Error(`Chat API request failed: ${response.status}`);
  }

  const data = (await response.json()) as ChatResponse;

  return {
    answer: data.answer,
    source: data.source ?? null,
    confidence: data.confidence ?? null,
    navPage: data.navPage ?? null,
    navLabel: data.navLabel ?? null,
  };
}
