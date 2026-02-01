package app.fuggs.document.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

public class DocumentProcessingWorkflowImpl implements DocumentProcessingWorkflow
{
	private static final Logger LOG = Workflow.getLogger(DocumentProcessingWorkflowImpl.class);

	private boolean reviewCompleted = false;
	private ReviewInput reviewInput;
	private String currentStatus = "STARTED";

	@Override
	public DocumentProcessingResult processDocument(DocumentProcessingRequest request)
	{
		LOG.info("Starting document processing: documentId={}, organizationId={}",
			request.documentId(), request.organizationId());

		// Configure activity retry policy
		ActivityOptions activityOptions = ActivityOptions.newBuilder()
			.setStartToCloseTimeout(Duration.ofMinutes(5))
			.setRetryOptions(RetryOptions.newBuilder()
				.setInitialInterval(Duration.ofSeconds(2))
				.setMaximumInterval(Duration.ofMinutes(2))
				.setBackoffCoefficient(2.0)
				.setMaximumAttempts(5)
				.build())
			.build();

		DocumentProcessingActivities activities = Workflow.newActivityStub(DocumentProcessingActivities.class,
			activityOptions);

		// Step 1: Try ZugFerd extraction
		currentStatus = "ANALYZING_ZUGFERD";
		AnalysisResult zugferdResult = activities.analyzeWithZugFerd(request.documentId());

		// Step 2: Fallback to AI if ZugFerd failed
		if (!zugferdResult.success())
		{
			LOG.info("ZugFerd failed, falling back to AI: documentId={}", request.documentId());
			currentStatus = "ANALYZING_AI";
			activities.analyzeWithDocumentAi(request.documentId());
		}

		// Step 3: Wait for user review (signal)
		currentStatus = "WAITING_FOR_REVIEW";
		LOG.info("Waiting for user review: documentId={}", request.documentId());
		Workflow.await(() -> reviewCompleted);

		// Step 4: Process review result
		currentStatus = "PROCESSING_REVIEW";
		LOG.info("Processing review: documentId={}, confirmed={}, reanalyze={}",
			request.documentId(), reviewInput.confirmed(), reviewInput.reanalyze());

		activities.processReviewResult(request.documentId(), reviewInput);

		currentStatus = "COMPLETED";
		LOG.info("Document processing completed: documentId={}", request.documentId());

		return new DocumentProcessingResult(
			"COMPLETED",
			null,
			zugferdResult.source());
	}

	@Override
	public void reviewCompleted(ReviewInput input)
	{
		LOG.info("Review signal received: confirmed={}, reanalyze={}",
			input.confirmed(), input.reanalyze());
		this.reviewInput = input;
		this.reviewCompleted = true;
	}

	@Override
	public String getCurrentStatus()
	{
		return currentStatus;
	}
}
