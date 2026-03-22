package app.fuggs.document.flow;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DocumentFlowService
{
	private static final Logger LOG = LoggerFactory.getLogger(DocumentFlowService.class);

	@Inject
	DocumentAnalysisFlow documentAnalysisFlow;

	public String startDocumentProcessing(Long documentId)
	{
		String flowId = "document-" + documentId;
		LOG.info("Starting document analysis flow: documentId={}, flowId={}", documentId, flowId);

		documentAnalysisFlow.startInstance(Map.of("documentId", documentId))
			.subscribe().with(
				instance -> LOG.info("Analysis flow completed: documentId={}", documentId),
				failure -> LOG.error("Analysis flow failed: documentId={}, error={}", documentId,
					failure.getMessage(), failure));

		return flowId;
	}
}
