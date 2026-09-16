from fastapi import FastAPI, HTTPException
from .config import settings
from .models import RagChatRequest, RagChatResponse
from .rag_service import RAGService

app = FastAPI(
    title="CIB Institution Assistant RAG Service",
    version="1.0.0",
)

rag = RAGService()

@app.get("/health")
def health():
    return {
        "status": "ok",
        "service": "cib-rag",
        "index_loaded": rag.index is not None,
        "faq_count": len(rag.df),
        "ollama_model": settings.ollama_model,
    }

@app.post("/rag/chat", response_model=RagChatResponse)
def chat(request: RagChatRequest):
    try:
        return rag.chat(
            request.message,
            request.userRole,
            request.conversationHistory,
        )
    except Exception as exc:
        raise HTTPException(status_code=503, detail="RAG service temporarily unavailable") from exc
