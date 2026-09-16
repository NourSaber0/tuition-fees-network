import os
from dataclasses import dataclass
from dotenv import load_dotenv

load_dotenv()

@dataclass(frozen=True)
class Settings:
    faq_csv_path: str = os.getenv("FAQ_CSV_PATH", "data/cib_institution_chatbot_faq_4000.csv")
    faiss_index_path: str = os.getenv("FAISS_INDEX_PATH", "index/cib_faq.index")
    ollama_base_url: str = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
    ollama_model: str = os.getenv("OLLAMA_MODEL", "qwen3:0.6b")
    embedding_model: str = os.getenv("EMBEDDING_MODEL", "all-MiniLM-L6-v2")
    top_k: int = int(os.getenv("TOP_K", "5"))
    min_similarity: float = float(os.getenv("RAG_MIN_SIMILARITY", "0.45"))
    host: str = os.getenv("RAG_HOST", "0.0.0.0")
    port: int = int(os.getenv("RAG_PORT", "8000"))

settings = Settings()
