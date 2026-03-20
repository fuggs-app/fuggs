package app.fuggs.document.flow;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DocumentFlowService
{
	private static final Logger LOG = LoggerFactory.getLogger(DocumentFlowService.class);

	@Inject
	ManagedExecutor managedExecutor;

	@Inject
	DocumentAnalysisActivitiesService activities;

	public String startDocumentProcessing(Long documentId)
	{
		String flowId = "document-" + documentId;
		LOG.info("Starting document analysis flow: documentId={}, flowId={}", documentId, flowId);

		managedExecutor.runAsync(() -> {
			try
			{
				AnalysisResult zugferdResult = activities.analyzeWithZugFerd(documentId);
				if (!zugferdResult.success())
				{
					activities.analyzeWithDocumentAi(documentId);
				}
			}
			catch (Exception e)
			{
				LOG.error("Document analysis flow failed: documentId={}, error={}", documentId, e.getMessage(), e);
			}
		});

		return flowId;
	}
}
