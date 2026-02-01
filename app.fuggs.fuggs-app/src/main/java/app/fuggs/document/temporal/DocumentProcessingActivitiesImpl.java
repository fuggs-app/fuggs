package app.fuggs.document.temporal;

import java.io.InputStream;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import app.fuggs.audit.domain.AuditLogEntry;
import app.fuggs.audit.repository.AuditLogRepository;
import app.fuggs.bommel.domain.Bommel;
import app.fuggs.bommel.repository.BommelRepository;
import app.fuggs.document.client.DocumentAiClient;
import app.fuggs.document.client.DocumentData;
import app.fuggs.document.client.ZugFerdClient;
import app.fuggs.document.domain.AnalysisStatus;
import app.fuggs.document.domain.Document;
import app.fuggs.document.domain.DocumentStatus;
import app.fuggs.document.domain.ExtractionSource;
import app.fuggs.document.domain.TagSource;
import app.fuggs.document.repository.DocumentRepository;
import app.fuggs.document.service.DocumentDataApplier;
import app.fuggs.document.service.DocumentDataService;
import app.fuggs.document.service.StorageService;
import app.fuggs.shared.repository.TagRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class DocumentProcessingActivitiesImpl implements DocumentProcessingActivities
{
	private static final Logger LOG = LoggerFactory.getLogger(DocumentProcessingActivitiesImpl.class);

	@Inject
	DocumentRepository documentRepository;

	@Inject
	BommelRepository bommelRepository;

	@Inject
	TagRepository tagRepository;

	@Inject
	StorageService storageService;

	@Inject
	DocumentDataApplier documentDataApplier;

	@Inject
	DocumentDataService documentDataService;

	@Inject
	AuditLogRepository auditLogRepository;

	@RestClient
	ZugFerdClient zugFerdClient;

	@RestClient
	DocumentAiClient documentAiClient;

	@Override
	@Transactional
	public AnalysisResult analyzeWithZugFerd(Long documentId)
	{
		LOG.info("Starting ZugFerd analysis: documentId={}", documentId);

		Document document = documentRepository.findById(documentId);
		if (document == null)
		{
			LOG.error("Document not found: id={}", documentId);
			throw new IllegalStateException("Document not found: " + documentId);
		}

		// Log audit event
		logAuditEvent(document, "AnalyzeDocumentZugFerd", "Started ZugFerd analysis");

		// Skip if no file
		if (!document.hasFile())
		{
			LOG.info("Document has no file, skipping ZugFerd: documentId={}", documentId);
			return new AnalysisResult(false, null, "No file attached");
		}

		// Skip if not PDF
		if (!document.isPdf())
		{
			LOG.info("Document is not PDF, skipping ZugFerd: documentId={}, contentType={}",
				documentId, document.getFileContentType());
			return new AnalysisResult(false, null, "Not a PDF file");
		}

		// Mark as analyzing
		document.setAnalysisStatus(AnalysisStatus.ANALYZING);
		document.setDocumentStatus(DocumentStatus.ANALYZING);

		try (InputStream fileStream = storageService.downloadFile(document.getFileKey()))
		{
			LOG.debug("Downloaded file from S3: key={}", document.getFileKey());

			// Call ZugFerd microservice
			DocumentData data = zugFerdClient.scanDocument(fileStream, documentId);

			if (data == null)
			{
				LOG.warn("ZugFerd service returned no data: documentId={}", documentId);
				throw new RuntimeException("ZugFerd extraction returned no data");
			}

			// Apply extracted data
			documentDataApplier.applyDocumentData(document, data, TagSource.AI);

			// Mark as completed
			document.setAnalysisStatus(AnalysisStatus.COMPLETED);
			document.setDocumentStatus(DocumentStatus.ANALYZED);
			document.setExtractionSource(ExtractionSource.ZUGFERD);

			logAuditEvent(document, "AnalyzeDocumentZugFerd", "ZugFerd analysis completed successfully");
			LOG.info("ZugFerd analysis completed: documentId={}", documentId);

			return new AnalysisResult(true, ExtractionSource.ZUGFERD, null);

		}
		catch (Exception e)
		{
			LOG.warn("ZugFerd extraction failed: documentId={}, error={}", documentId, e.getMessage());
			// Reset status for AI fallback
			document.setAnalysisStatus(AnalysisStatus.PENDING);

			logAuditEvent(document, "AnalyzeDocumentZugFerd",
				"ZugFerd analysis failed: " + e.getMessage());

			return new AnalysisResult(false, null, e.getMessage());
		}
	}

	@Override
	@Transactional
	public void analyzeWithDocumentAi(Long documentId)
	{
		LOG.info("Starting AI analysis: documentId={}", documentId);

		Document document = documentRepository.findById(documentId);
		if (document == null)
		{
			throw new IllegalStateException("Document not found: " + documentId);
		}

		logAuditEvent(document, "AnalyzeDocumentAi", "Started AI analysis");

		if (!document.hasFile())
		{
			LOG.warn("Document has no file: documentId={}", documentId);
			markAnalysisFailed(document, "Kein Dokument vorhanden");
			return;
		}

		document.setAnalysisStatus(AnalysisStatus.ANALYZING);
		document.setDocumentStatus(DocumentStatus.ANALYZING);

		try (InputStream fileStream = storageService.downloadFile(document.getFileKey()))
		{
			LOG.debug("Downloaded file for AI analysis: key={}", document.getFileKey());

			// Call Document AI microservice
			DocumentData data = documentAiClient.scanDocument(fileStream, documentId);

			if (data == null)
			{
				throw new RuntimeException("Document AI returned no data");
			}

			// Apply extracted data
			documentDataApplier.applyDocumentData(document, data, TagSource.AI);

			// Mark as completed
			document.setAnalysisStatus(AnalysisStatus.COMPLETED);
			document.setDocumentStatus(DocumentStatus.ANALYZED);
			document.setExtractionSource(ExtractionSource.AI);

			logAuditEvent(document, "AnalyzeDocumentAi", "AI analysis completed successfully");
			LOG.info("AI analysis completed: documentId={}", documentId);

		}
		catch (Exception e)
		{
			LOG.error("AI analysis failed: documentId={}, error={}", documentId, e.getMessage(), e);
			markAnalysisFailed(document, "KI-Analyse fehlgeschlagen: " + e.getMessage());

			logAuditEvent(document, "AnalyzeDocumentAi",
				"AI analysis failed: " + e.getMessage());
		}
	}

	@Override
	@Transactional
	public void processReviewResult(Long documentId, ReviewInput reviewInput)
	{
		LOG.info("Processing review result: documentId={}, confirmed={}, reanalyze={}",
			documentId, reviewInput.confirmed(), reviewInput.reanalyze());

		Document document = documentRepository.findById(documentId);
		if (document == null)
		{
			throw new IllegalStateException("Document not found: " + documentId);
		}

		if (Boolean.TRUE.equals(reviewInput.confirmed()))
		{
			// User confirmed - apply form data and mark as confirmed
			documentDataService.applyFormData(document, reviewInput.formData());

			// Handle bommel assignment
			Long bommelId = (Long)reviewInput.formData().get("bommelId");
			if (bommelId != null && bommelId > 0)
			{
				Bommel bommel = bommelRepository.findById(bommelId);
				document.setBommel(bommel);
			}
			else
			{
				document.setBommel(null);
			}

			// Update tags
			String tagsInput = (String)reviewInput.formData().get("tags");
			documentDataService.updateTags(document, tagsInput);

			document.setDocumentStatus(DocumentStatus.CONFIRMED);

			logAuditEvent(document, "ReviewDocument", "Document confirmed by user");
			LOG.info("Document confirmed: documentId={}", documentId);

		}
		else if (Boolean.TRUE.equals(reviewInput.reanalyze()))
		{
			// User rejected and wants re-analysis
			document.setDocumentStatus(DocumentStatus.UPLOADED);
			document.setAnalysisError(null);
			document.setTemporalWorkflowId(null); // Clear workflow link

			logAuditEvent(document, "ReviewDocument", "Re-analysis requested by user");
			LOG.info("Re-analysis requested: documentId={}", documentId);

		}
		else
		{
			// User rejected and wants manual entry
			document.setDocumentStatus(DocumentStatus.UPLOADED);
			document.setTemporalWorkflowId(null); // Clear workflow link

			logAuditEvent(document, "ReviewDocument", "Manual entry selected by user");
			LOG.info("Manual entry selected: documentId={}", documentId);
		}
	}

	private void markAnalysisFailed(Document document, String errorMessage)
	{
		document.setAnalysisStatus(AnalysisStatus.FAILED);
		document.setDocumentStatus(DocumentStatus.FAILED);
		document.setAnalysisError(errorMessage);
	}

	private void logAuditEvent(Document document, String taskName, String details)
	{
		AuditLogEntry entry = new AuditLogEntry();
		entry.setEntityName("Document");
		entry.setEntityId(document.getId().toString());
		entry.setTaskName(taskName);
		entry.setDetails(details);
		entry.setUsername("system");
		entry.setOrganization(document.getOrganization());
		auditLogRepository.persist(entry);
	}
}
