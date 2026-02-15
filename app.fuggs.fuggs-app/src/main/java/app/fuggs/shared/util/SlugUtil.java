package app.fuggs.shared.util;

import app.fuggs.organization.repository.OrganizationRepository;

import java.text.Normalizer;

/**
 * Utility class for generating URL-safe slugs from organization names.
 */
public class SlugUtil
{
	private SlugUtil()
	{
		// Utility class
	}

	/**
	 * Generates a URL-safe slug from a name, ensuring uniqueness.
	 *
	 * @param name
	 *            The name to slugify
	 * @param organizationRepository
	 *            Repository to check for existing slugs
	 * @return A unique slug
	 */
	public static String generateUniqueSlug(String name, OrganizationRepository organizationRepository)
	{
		String baseSlug = slugify(name);
		String slug = baseSlug;
		int counter = 2;

		// Ensure uniqueness by appending numbers if needed
		while (organizationRepository.findBySlug(slug) != null)
		{
			slug = baseSlug + "-" + counter++;
		}

		return slug;
	}

	/**
	 * Converts a string to a URL-safe slug.
	 *
	 * @param text
	 *            The text to slugify
	 * @return A slug (lowercase, hyphens, no special chars)
	 */
	private static String slugify(String text)
	{
		if (text == null || text.isBlank())
		{
			return "org";
		}

		// Normalize to decompose accented characters
		String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);

		// Remove diacritics and convert to lowercase
		String slug = normalized.replaceAll("\\p{M}", "") // Remove diacritical
															// marks
			.toLowerCase().replaceAll("[^a-z0-9\\s-]", "") // Keep only
															// alphanumeric,
															// spaces, hyphens
			.replaceAll("\\s+", "-") // Replace spaces with hyphens
			.replaceAll("-+", "-") // Replace multiple hyphens with single
			.replaceAll("^-|-$", ""); // Trim hyphens from start/end

		return slug.isEmpty() ? "org" : slug;
	}
}
