package app.fuggs.document.temporal;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface DocumentProcessingWorkflow
{
	@WorkflowMethod
	DocumentProcessingResult processDocument(DocumentProcessingRequest request);

	@SignalMethod
	void reviewCompleted(ReviewInput reviewInput);

	@QueryMethod
	String getCurrentStatus();
}
