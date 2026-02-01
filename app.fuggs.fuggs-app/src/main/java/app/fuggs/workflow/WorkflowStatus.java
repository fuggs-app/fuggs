package app.fuggs.workflow;

/**
 * Status of a workflow instance.
 *
 * @deprecated This enum is kept for historical records only. New workflows use
 *             Temporal workflow engine.
 */
@Deprecated(since = "2.0.0", forRemoval = true)
public enum WorkflowStatus
{
	RUNNING, WAITING, COMPLETED, FAILED
}
