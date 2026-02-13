package app.fuggs.transaction.repository;

import java.util.List;
import java.util.Map;

import app.fuggs.shared.security.OrganizationContext;
import app.fuggs.shared.util.SortHelper;
import app.fuggs.transaction.domain.TransactionRecord;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TransactionRecordRepository implements PanacheRepository<TransactionRecord>
{
	@Inject
	OrganizationContext organizationContext;

	// Whitelist of allowed sort fields (field name -> column expression)
	private static final Map<String, String> ALLOWED_SORT_FIELDS = Map.of(
		"name", "t.name",
		"date", "t.transactionTime",
		"total", "t.total");

	private static final String DEFAULT_SORT_FIELD = "date";
	private static final String DEFAULT_SORT_DIRECTION = "DESC";

	/**
	 * Find all transaction records for the current organization, ordered by
	 * transaction time (newest first). Uses LEFT JOIN FETCH to prevent N+1
	 * queries for tags.
	 */
	public List<TransactionRecord> findAllOrderedByDate()
	{
		return findAllOrderedByDate(null, null);
	}

	/**
	 * Find all transaction records for the current organization with custom
	 * sorting.
	 *
	 * @param sortField
	 *            The field to sort by (validated against whitelist)
	 * @param sortDirection
	 *            The sort direction ("asc" or "desc")
	 * @return List of transaction records
	 */
	public List<TransactionRecord> findAllOrderedByDate(String sortField, String sortDirection)
	{
		Long orgId = organizationContext.getCurrentOrganizationId();
		if (orgId == null)
		{
			return List.of();
		}

		String orderBy = SortHelper.buildOrderByClause(
			ALLOWED_SORT_FIELDS, sortField, sortDirection,
			DEFAULT_SORT_FIELD, DEFAULT_SORT_DIRECTION);

		String query = "SELECT DISTINCT t FROM TransactionRecord t " +
			"LEFT JOIN FETCH t.transactionTags " +
			"WHERE t.organization.id = ?1 " +
			"ORDER BY " + orderBy;

		return find(query, orgId).list();
	}

	/**
	 * Find transaction records assigned to a specific Bommel within the current
	 * organization. Uses LEFT JOIN FETCH to prevent N+1 queries for tags.
	 */
	public List<TransactionRecord> findByBommel(Long bommelId)
	{
		return findByBommel(bommelId, null, null);
	}

	/**
	 * Find transaction records assigned to a specific Bommel within the current
	 * organization with custom sorting.
	 *
	 * @param bommelId
	 *            The bommel ID
	 * @param sortField
	 *            The field to sort by (validated against whitelist)
	 * @param sortDirection
	 *            The sort direction ("asc" or "desc")
	 * @return List of transaction records
	 */
	public List<TransactionRecord> findByBommel(Long bommelId, String sortField, String sortDirection)
	{
		Long orgId = organizationContext.getCurrentOrganizationId();
		if (orgId == null)
		{
			return List.of();
		}

		String orderBy = SortHelper.buildOrderByClause(
			ALLOWED_SORT_FIELDS, sortField, sortDirection,
			DEFAULT_SORT_FIELD, DEFAULT_SORT_DIRECTION);

		String query = "SELECT DISTINCT t FROM TransactionRecord t " +
			"LEFT JOIN FETCH t.transactionTags " +
			"WHERE t.bommel.id = ?1 AND t.organization.id = ?2 " +
			"ORDER BY " + orderBy;

		return find(query, bommelId, orgId).list();
	}

	/**
	 * Find transaction records not assigned to any Bommel within the current
	 * organization. Uses LEFT JOIN FETCH to prevent N+1 queries for tags.
	 */
	public List<TransactionRecord> findUnassigned()
	{
		Long orgId = organizationContext.getCurrentOrganizationId();
		if (orgId == null)
		{
			return List.of();
		}
		return find("SELECT DISTINCT t FROM TransactionRecord t " +
			"LEFT JOIN FETCH t.transactionTags " +
			"WHERE t.bommel IS NULL AND t.organization.id = ?1 " +
			"ORDER BY t.createdAt DESC",
			orgId)
				.list();
	}

	/**
	 * Find transaction records linked to a specific document within the current
	 * organization.
	 */
	public List<TransactionRecord> findByDocument(Long documentId)
	{
		return findByDocument(documentId, null, null);
	}

	/**
	 * Find transaction records linked to a specific document within the current
	 * organization with custom sorting.
	 *
	 * @param documentId
	 *            The document ID
	 * @param sortField
	 *            The field to sort by (validated against whitelist)
	 * @param sortDirection
	 *            The sort direction ("asc" or "desc")
	 * @return List of transaction records
	 */
	public List<TransactionRecord> findByDocument(Long documentId, String sortField, String sortDirection)
	{
		Long orgId = organizationContext.getCurrentOrganizationId();
		if (orgId == null)
		{
			return List.of();
		}

		String orderBy = SortHelper.buildOrderByClause(
			ALLOWED_SORT_FIELDS, sortField, sortDirection,
			DEFAULT_SORT_FIELD, DEFAULT_SORT_DIRECTION);

		String query = "SELECT DISTINCT t FROM TransactionRecord t " +
			"LEFT JOIN FETCH t.transactionTags " +
			"WHERE t.document.id = ?1 AND t.organization.id = ?2 " +
			"ORDER BY " + orderBy;

		return find(query, documentId, orgId).list();
	}

	/**
	 * Finds a transaction record by ID, scoped to the current organization.
	 * This prevents cross-organization access.
	 *
	 * @param id
	 *            The transaction record ID
	 * @return The transaction record, or null if not found or not in current
	 *         organization
	 */
	public TransactionRecord findByIdScoped(Long id)
	{
		Long orgId = organizationContext.getCurrentOrganizationId();
		if (orgId == null)
		{
			return null;
		}
		return find("id = ?1 and organization.id = ?2", id, orgId).firstResult();
	}
}
