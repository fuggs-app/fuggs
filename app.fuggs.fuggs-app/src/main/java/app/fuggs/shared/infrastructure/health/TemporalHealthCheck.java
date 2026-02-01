package app.fuggs.shared.infrastructure.health;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.temporal.client.WorkflowClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Liveness
public class TemporalHealthCheck implements HealthCheck
{
	private static final Logger LOG = LoggerFactory.getLogger(TemporalHealthCheck.class);

	@Inject
	WorkflowClient workflowClient;

	@Override
	public HealthCheckResponse call()
	{
		try
		{
			// Check connection to Temporal server
			workflowClient.getWorkflowServiceStubs().healthCheck();
			return HealthCheckResponse.up("temporal");
		}
		catch (Exception e)
		{
			LOG.error("Temporal health check failed", e);
			return HealthCheckResponse.down("temporal");
		}
	}
}
