package app.fuggs.document.temporal;

public record DocumentProcessingRequest(
	Long documentId,
	Long organizationId)
{
}
