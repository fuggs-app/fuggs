package app.fuggs.document.flow;

import java.util.Map;

public record ReviewInput(
	Boolean confirmed,
	Boolean reanalyze,
	Map<String, Object> formData)
{
}
