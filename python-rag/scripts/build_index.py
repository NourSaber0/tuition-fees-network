from app.rag_service import RAGService

if __name__ == "__main__":
    service = RAGService()
    print(f"FAISS index ready with {service.index.ntotal} FAQ records.")
