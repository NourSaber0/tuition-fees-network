# CIB Institution Assistant — Python RAG Service

This service contains the AI-specific layer of the chatbot.

## Responsibilities

- Load the approved 4,000-row CIB FAQ CSV
- Generate embeddings with SentenceTransformer `all-MiniLM-L6-v2`
- Store/search embeddings with FAISS
- Retrieve the most relevant CIB FAQ records
- Apply a configurable similarity threshold
- Send grounded context to Ollama
- Use Qwen3 8B to generate the final answer
- Return answer, source, retrieval confidence, and controlled navigation metadata

## Setup

From this directory:

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

On Windows:

```powershell
.venv\\Scripts\\activate
pip install -r requirements.txt
```

## Ollama

Install Ollama separately and make sure the Qwen3 8B model is available:

```bash
ollama pull qwen3:8b
```

Then run Ollama normally.

## Build the FAISS index

```bash
python scripts/build_index.py
```

The first run downloads/loads the embedding model and creates the index under `index/`.

## Start the RAG API

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Health:

```text
GET http://localhost:8000/health
```

Chat:

```text
POST http://localhost:8000/rag/chat
```

## Request example

```json
{
  "message": "How do I upload fees?",
  "userRole": "SCHOOL_ADMIN",
  "conversationHistory": []
}
```

## Important

The RAG service should be reachable by Spring Boot, not directly by the browser.

The LLM is instructed to answer only from retrieved CIB knowledge-base context. This is a grounded RAG implementation, not a general web chatbot.
