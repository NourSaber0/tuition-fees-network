from typing import Literal
from pydantic import BaseModel, Field

class ConversationMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=4000)

class RagChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=2000)
    userRole: str | None = None
    conversationHistory: list[ConversationMessage] = Field(default_factory=list, max_length=10)

class RagChatResponse(BaseModel):
    answer: str
    source: str | None = None
    confidence: float | None = None
    navPage: str | None = None
    navLabel: str | None = None
