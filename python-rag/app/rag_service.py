from pathlib import Path
import json
import os
import requests
import faiss
import numpy as np
import pandas as pd
from sentence_transformers import SentenceTransformer

from .config import settings

NO_RESULTS_MSG = (
    "I couldn't find a reliable answer to that question in the CIB Institution "
    "knowledge base. Try rephrasing your question or browse the FAQ categories below."
)

SYSTEM_PROMPT = """You are the CIB Institution Assistant for authorized School Portal users.

Your users are School Admin and School Finance users.
Answer ONLY from the supplied CIB knowledge-base context and conversation history.
Do not invent policies, fees, dates, permissions, endpoints, or procedures.
If the supplied context does not reliably answer the question, say that you could not find a reliable answer in the CIB Institution knowledge base.
Do not handle complaints, support tickets, issue submission, escalation, or Parent Portal requests.
Do not perform financial transactions.
Keep answers concise, clear, and useful.
"""

class RAGService:
    def __init__(self):
        self.df = pd.read_csv(settings.faq_csv_path)
        self.model = SentenceTransformer(settings.embedding_model)
        self.index = None
        self.documents = []
        self.load_or_build_index()

    def _texts(self):
        # Embed the question plus category/intent. The answer is retained as context,
        # but the retrieval representation focuses on the user's likely query terms.
        return [
            f"Category: {row.category}\nIntent: {row.intent}\nQuestion: {row.question}"
            for row in self.df.itertuples(index=False)
        ]

    def load_or_build_index(self):
        Path(settings.faiss_index_path).parent.mkdir(parents=True, exist_ok=True)
        if os.path.exists(settings.faiss_index_path):
            self.index = faiss.read_index(settings.faiss_index_path)
            if self.index.ntotal == len(self.df):
                return

        texts = self._texts()
        embeddings = self.model.encode(
            texts,
            convert_to_numpy=True,
            normalize_embeddings=True,
            show_progress_bar=True,
        ).astype("float32")
        self.index = faiss.IndexFlatIP(embeddings.shape[1])
        self.index.add(embeddings)
        faiss.write_index(self.index, settings.faiss_index_path)

    def retrieve(self, message: str, top_k: int | None = None):
        k = top_k or settings.top_k
        query = self.model.encode(
            [message],
            convert_to_numpy=True,
            normalize_embeddings=True,
        ).astype("float32")
        scores, indices = self.index.search(query, k)

        results = []
        for score, idx in zip(scores[0], indices[0]):
            if idx < 0:
                continue
            row = self.df.iloc[int(idx)]
            results.append({
                "id": str(row.id),
                "category": str(row.category),
                "intent": str(row.intent),
                "question": str(row.question),
                "answer": str(row.answer),
                "source": str(row.source),
                "score": float(score),
            })
        return results

    def _build_context(self, results):
        return "\n\n".join(
            f"FAQ ID: {r['id']}\nCategory: {r['category']}\n"
            f"Question: {r['question']}\nApproved Answer: {r['answer']}"
            for r in results
        )

    def _ollama_generate(self, message, history, context):
        messages = [{"role": "system", "content": SYSTEM_PROMPT}]
        for item in history[-10:]:
            messages.append({"role": item["role"], "content": item["content"]})
        messages.append({
            "role": "user",
            "content": (
                "Use the following approved CIB knowledge-base context to answer the question. "
                "Do not add facts that are not supported by it.\n\n"
                f"KNOWLEDGE BASE CONTEXT:\n{context}\n\n"
                f"CURRENT QUESTION:\n{message}"
            ),
        })

        response = requests.post(
            f"{settings.ollama_base_url}/api/chat",
            json={
                "model": settings.ollama_model,
                "messages": messages,
                "stream": False,
            },
            timeout=120,
        )
        response.raise_for_status()
        payload = response.json()
        return payload["message"]["content"].strip()

    def chat(self, message, user_role, history):
        results = self.retrieve(message)
        if not results or results[0]["score"] < settings.min_similarity:
            return {
                "answer": NO_RESULTS_MSG,
                "source": None,
                "confidence": None,
                "navPage": None,
                "navLabel": None,
            }

        # Only use results that pass the retrieval threshold as grounding context.
        grounded = [r for r in results if r["score"] >= settings.min_similarity]
        context = self._build_context(grounded)
        answer = self._ollama_generate(
            message,
            [item.model_dump() for item in history],
            context,
        )

        best = grounded[0]

        nav_page, nav_label = self._navigation_for(best)

        return {
            "answer": answer,
            "source": best["source"] or best["id"],
            "confidence": round(best["score"], 4),
            "navPage": nav_page,
            "navLabel": nav_label,
        }

    @staticmethod
    def _navigation_for(result):
        category = result["category"].lower()
        mapping = {
            "fee upload": ("fee-upload", "Go to Fee Upload"),
            "payments": ("payments", "Go to Payments"),
            "payment troubleshooting": ("payments", "Go to Payments"),
            "partial payments": ("payments", "Go to Payments"),
            "payment priority": ("payments", "Go to Payments"),
            "reconciliation": ("reconciliation", "Go to Reconciliation"),
            "students": ("students", "Go to Students"),
            "school users": ("users", "Go to School Users"),
            "reports": ("reports", "Go to Reports"),
            "notifications": ("notifications", "Go to Notifications"),
            "fee management": ("fee-management", "Go to Fee Management"),
        }
        return mapping.get(category, (None, None))
