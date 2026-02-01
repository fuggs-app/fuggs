package app.fuggs.document.temporal;

import app.fuggs.document.domain.ExtractionSource;

public record AnalysisResult(
	boolean success,
	ExtractionSource source,
	String errorMessage)
{
}
