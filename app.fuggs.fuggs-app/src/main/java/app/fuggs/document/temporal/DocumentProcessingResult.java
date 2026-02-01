package app.fuggs.document.temporal;

import app.fuggs.document.domain.ExtractionSource;

public record DocumentProcessingResult(
	String status,
	String error,
	ExtractionSource extractionSource)
{
}
