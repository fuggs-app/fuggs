package app.fuggs.document.temporal;

public interface DocumentProcessingActivities
{
	AnalysisResult analyzeWithZugFerd(Long documentId);

	void analyzeWithDocumentAi(Long documentId);

	void processReviewResult(Long documentId, ReviewInput reviewInput);
}
