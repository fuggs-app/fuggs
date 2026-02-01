package app.fuggs.document.temporal;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.fuggs.organization.domain.Organization;
import app.fuggs.shared.security.OrganizationContext;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TemporalDocumentService
{
	private static final Logger LOG = LoggerFactory.getLogger(TemporalDocumentService.class);

	@Inject
	WorkflowClient workflowClient;

	@Inject
	OrganizationContext organizationContext;

	public String startDocumentProcessing(Long documentId)
	{
		Organization org = organizationContext.getCurrentOrganization();

		String workflowId = "document-" + documentId;

		LOG.info("Starting document processing workflow: documentId={}, workflowId={}, organizationId={}",
			documentId, workflowId, org.id);

		// Configure workflow options with search attributes for multi-tenancy
		Map<String, Object> searchAttributes = new HashMap<>();
		searchAttributes.put("OrganizationId", org.id.toString());
		searchAttributes.put("DocumentId", documentId.toString());

		WorkflowOptions options = WorkflowOptions.newBuilder()
			.setTaskQueue("DocumentProcessingQueue")
			.setWorkflowId(workflowId)
			.setSearchAttributes(searchAttributes)
			.build();

		// Create workflow stub
		DocumentProcessingWorkflow workflow = workflowClient.newWorkflowStub(DocumentProcessingWorkflow.class,
			options);

		// Start workflow asynchronously
		DocumentProcessingRequest request = new DocumentProcessingRequest(documentId, org.id);

		WorkflowClient.start(workflow::processDocument, request);

		LOG.info("Document processing workflow started: workflowId={}", workflowId);
		return workflowId;
	}

	public void completeReview(String workflowId, ReviewInput reviewInput)
	{
		LOG.info("Sending review completion signal: workflowId={}", workflowId);

		DocumentProcessingWorkflow workflow = workflowClient.newWorkflowStub(
			DocumentProcessingWorkflow.class,
			workflowId);

		workflow.reviewCompleted(reviewInput);

		LOG.info("Review signal sent: workflowId={}", workflowId);
	}

	public String getWorkflowStatus(String workflowId)
	{
		DocumentProcessingWorkflow workflow = workflowClient.newWorkflowStub(
			DocumentProcessingWorkflow.class,
			workflowId);

		return workflow.getCurrentStatus();
	}
}
