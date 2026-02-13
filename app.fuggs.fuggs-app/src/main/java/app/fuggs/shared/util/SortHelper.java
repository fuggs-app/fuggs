package app.fuggs.shared.util;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for building ORDER BY clauses with SQL injection protection.
 * Uses whitelist-based validation to ensure only allowed fields and directions
 * are used.
 */
public class SortHelper
{
	private static final Logger LOG = LoggerFactory.getLogger(SortHelper.class);

	private SortHelper()
	{
		// Utility class
	}

	/**
	 * Builds a safe ORDER BY clause for JPQL queries with SQL injection
	 * protection.
	 *
	 * @param allowedFields
	 *            Map of allowed field names to their column expressions (e.g.,
	 *            "name" -> "d.name")
	 * @param sortField
	 *            The requested sort field (validated against allowedFields)
	 * @param sortDirection
	 *            The requested sort direction ("asc" or "desc", validated)
	 * @param defaultField
	 *            The default field to sort by if sortField is invalid
	 * @param defaultDirection
	 *            The default direction if sortDirection is invalid
	 * @return A safe ORDER BY clause (e.g., "d.name ASC, d.id ASC")
	 */
	public static String buildOrderByClause(Map<String, String> allowedFields, String sortField,
		String sortDirection, String defaultField, String defaultDirection)
	{
		// Validate field against whitelist
		String columnExpression;
		if (sortField == null || sortField.isBlank() || !allowedFields.containsKey(sortField))
		{
			if (sortField != null && !sortField.isBlank())
			{
				LOG.debug("Invalid sort field '{}', falling back to default '{}'", sortField, defaultField);
			}
			columnExpression = allowedFields.get(defaultField);
			sortField = defaultField;
		}
		else
		{
			columnExpression = allowedFields.get(sortField);
		}

		// Validate direction (only "asc" or "desc" allowed)
		String direction;
		if (sortDirection == null || sortDirection.isBlank()
			|| (!sortDirection.equalsIgnoreCase("asc") && !sortDirection.equalsIgnoreCase("desc")))
		{
			if (sortDirection != null && !sortDirection.isBlank())
			{
				LOG.debug("Invalid sort direction '{}', falling back to default '{}'", sortDirection,
					defaultDirection);
			}
			direction = defaultDirection.toUpperCase();
		}
		else
		{
			direction = sortDirection.toUpperCase();
		}

		// Extract alias from columnExpression (e.g., "d.name" -> "d.")
		// This is needed for the tie-breaker on id to avoid ambiguous
		// references
		String alias = "";
		if (columnExpression.contains("."))
		{
			alias = columnExpression.substring(0, columnExpression.lastIndexOf('.') + 1);
		}

		// Build ORDER BY clause with tie-breaker on id for consistent sorting
		return columnExpression + " " + direction + ", " + alias + "id " + direction;
	}
}
