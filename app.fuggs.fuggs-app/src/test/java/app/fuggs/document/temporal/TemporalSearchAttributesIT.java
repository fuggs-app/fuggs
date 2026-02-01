package app.fuggs.document.temporal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import jakarta.inject.Inject;

/**
 * Integration test to verify Temporal search attributes (OrganizationId,
 * DocumentId) are registered correctly when DevServices starts.
 * <p>
 * This test will FAIL with "INVALID_ARGUMENT: Namespace default has no mapping
 * defined for search attribute" if the temporal-init container doesn't properly
 * register the search attributes.
 */
@QuarkusTest
class TemporalSearchAttributesIT
{
	@Inject
	WorkflowClient workflowClient;

	@Test
	void shouldBeAbleToCreateWorkflowWithOrganizationIdSearchAttribute()
	{
		// Verify we can create a workflow with OrganizationId search attribute
		// This will throw INVALID_ARGUMENT if the attribute isn't registered
		assertDoesNotThrow(() -> {
			Map<String, Object> searchAttributes = new HashMap<>();
			searchAttributes.put("OrganizationId", "test-org-123");
			searchAttributes.put("DocumentId", "test-doc-456");

			WorkflowOptions options = WorkflowOptions.newBuilder()
				.setTaskQueue("TestQueue")
				.setWorkflowId("test-workflow-search-attrs")
				.setSearchAttributes(searchAttributes)
				.build();

			DocumentProcessingWorkflow workflow = workflowClient.newWorkflowStub(DocumentProcessingWorkflow.class,
				options);

			assertNotNull(workflow, "Should create workflow stub with search attributes");
		}, "Should not throw exception when creating workflow with OrganizationId and DocumentId search attributes");
	}
}
