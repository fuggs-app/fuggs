package app.fuggs.document.flow;

import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.consume;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.function;
import static io.serverlessworkflow.fluent.func.dsl.FuncDSL.switchWhenOrElse;

import io.quarkiverse.flow.Flow;
import io.serverlessworkflow.api.types.FlowDirectiveEnum;
import io.serverlessworkflow.api.types.Workflow;
import io.serverlessworkflow.fluent.func.FuncWorkflowBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class DocumentAnalysisFlow extends Flow
{
	@Inject
	DocumentAnalysisActivitiesService activities;

	@Override
	public Workflow descriptor()
	{
		return FuncWorkflowBuilder.workflow("document-analysis")
			.tasks(
				// Step 1: Try ZugFerd extraction first
				function("analyzeZugFerd", activities::analyzeWithZugFerd, Long.class)
					.inputFrom(".documentId")
					.outputAs("{ zugferdSuccess: .success }"),

				// Step 2: If ZugFerd failed, run AI fallback; otherwise end
				switchWhenOrElse(".zugferdSuccess | not", "analyzeAi", FlowDirectiveEnum.END),

				// Step 3: AI fallback (only reached when ZugFerd failed)
				consume("analyzeAi", activities::analyzeWithDocumentAi, Long.class)
					.inputFrom(".documentId"))
			.build();
	}
}
