package app.fuggs.document.flow;

import java.util.Map;

public record ReviewInput(
	boolean confirmed,
	boolean reanalyze,
	Map<String, Object> formData)
{
}
