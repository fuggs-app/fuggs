package app.fuggs.document.service;

import app.fuggs.document.domain.AnalysisStatus;
import app.fuggs.document.domain.Document;
import app.fuggs.document.domain.DocumentStatus;
import app.fuggs.document.repository.DocumentRepository;
import app.fuggs.organization.domain.Organization;
import app.fuggs.shared.BaseOrganizationTest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the channel-agnostic intake path that both the web upload controller
 * and the Telegram bot endpoint delegate into.
 */
@QuarkusTest
class DocumentIntakeServiceTest extends BaseOrganizationTest
{
	@Inject
	DocumentIntakeService intakeService;

	@Inject
	DocumentRepository documentRepository;

	@Test
	void shouldCreateDocumentFromRawBytesAndStoreTheFile()
	{
		Organization org = getOrCreateTestOrganization();
		byte[] content = "%PDF-1.4 fake receipt bytes".getBytes(StandardCharsets.UTF_8);

		Document document = intakeService.intake(org, "hugo.mueller", content, "Kaufland.pdf", "application/pdf");

		assertNotNull(document.getId());
		assertEquals(BigDecimal.ZERO, document.getTotal());
		assertEquals("EUR", document.getCurrencyCode());
		assertEquals("hugo.mueller", document.getUploadedBy());
		assertEquals(org.id, document.getOrganization().id);
		assertTrue(document.hasFile());
		assertEquals("Kaufland.pdf", document.getFileName());
		assertEquals("application/pdf", document.getFileContentType());

		Document reloaded = documentRepository.findById(document.getId());
		assertNotNull(reloaded);
		assertNotNull(reloaded.getAnalysisStatus());
		assertNotNull(reloaded.getDocumentStatus());
		// The analysis flow runs synchronously in this test environment and
		// the ZugFerd/AI microservices aren't reachable, so by the time
		// intake() returns the document may already show UPLOADED (analysis
		// still pending), ANALYZING, or FAILED - either way, intake itself
		// must not throw.
		assertTrue(reloaded.getDocumentStatus() == DocumentStatus.UPLOADED
			|| reloaded.getDocumentStatus() == DocumentStatus.ANALYZING
			|| reloaded.getDocumentStatus() == DocumentStatus.FAILED);
		assertTrue(reloaded.getAnalysisStatus() == AnalysisStatus.PENDING
			|| reloaded.getAnalysisStatus() == AnalysisStatus.ANALYZING
			|| reloaded.getAnalysisStatus() == AnalysisStatus.FAILED);
	}
}
