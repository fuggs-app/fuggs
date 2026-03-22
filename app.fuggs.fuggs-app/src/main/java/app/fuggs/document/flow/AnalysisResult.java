package app.fuggs.document.flow;

import app.fuggs.document.domain.ExtractionSource;

public record AnalysisResult(
	long documentId,
	boolean success,
	ExtractionSource source,
	String errorMessage)
{
}
