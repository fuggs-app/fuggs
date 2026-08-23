package app.fuggs.document.service;

import app.fuggs.document.domain.AnalysisStatus;
import app.fuggs.document.domain.Document;
import app.fuggs.document.domain.DocumentStatus;
import app.fuggs.document.repository.DocumentRepository;
import app.fuggs.organization.domain.Organization;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Channel-agnostic document intake: creates a {@link Document} from raw file
 * bytes and triggers analysis, without depending on an authenticated request or
 * session. Used by both the web upload controller and the Telegram bot intake
 * endpoint.
 */
@ApplicationScoped
public class DocumentIntakeService
{
	private static final Logger LOG = LoggerFactory.getLogger(DocumentIntakeService.class);
	private static final String AI_UNAVAILABLE_MESSAGE = "KI-Dienst nicht verfügbar. Bitte füllen Sie die Felder manuell aus.";

	@Inject
	DocumentRepository documentRepository;

	@Inject
	DocumentFileService fileService;

	@Inject
	DocumentAnalysisService analysisService;

	/**
	 * Creates and persists a document for the given organization and uploader,
	 * stores the file, and triggers AI analysis.
	 *
	 * @param organization
	 *            the organization the document belongs to
	 * @param uploadedBy
	 *            the username of the uploader
	 * @param content
	 *            the file bytes
	 * @param fileName
	 *            the original filename
	 * @param contentType
	 *            the MIME type
	 * @return the persisted document
	 */
	@Transactional(Transactional.TxType.REQUIRES_NEW)
	public Document intake(Organization organization, String uploadedBy, byte[] content, String fileName,
		String contentType)
	{
		Document document = new Document();
		document.setTotal(BigDecimal.ZERO);
		document.setCurrencyCode("EUR");
		document.setAnalysisStatus(AnalysisStatus.PENDING);
		document.setDocumentStatus(DocumentStatus.UPLOADED);
		document.setUploadedBy(uploadedBy);
		document.setOrganization(organization);

		fileService.handleFileUpload(document, content, fileName, contentType);
		documentRepository.persist(document);

		boolean analysisStarted = analysisService.triggerAnalysis(document, uploadedBy);
		if (!analysisStarted)
		{
			analysisService.markAnalysisFailed(document, AI_UNAVAILABLE_MESSAGE);
		}

		LOG.info("Document intake completed: documentId={}, organization={}, uploadedBy={}",
			document.getId(), organization.getName(), uploadedBy);
		return document;
	}
}
