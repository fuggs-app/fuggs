package app.fuggs.document.flow;

import java.io.InputStream;
import java.util.function.BiFunction;

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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class DocumentAnalysisActivitiesService
{
	private static final Logger LOG = LoggerFactory.getLogger(DocumentAnalysisActivitiesService.class);

	@Inject
	DocumentRepository documentRepository;

	@Inject
	BommelRepository bommelRepository;

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

	@Transactional
	public AnalysisResult analyzeWithZugFerd(Long documentId)
	{
		LOG.info("Starting ZugFerd analysis: documentId={}", documentId);
		Document document = requireDocument(documentId);
		logAuditEvent(document, "AnalyzeDocumentZugFerd", "Started ZugFerd analysis");

		if (!document.hasFile())
		{
			LOG.info("Document has no file, skipping ZugFerd: documentId={}", documentId);
			return new AnalysisResult(false, null, "No file attached");
		}

		if (!document.isPdf())
		{
			LOG.info("Document is not PDF, skipping ZugFerd: documentId={}, contentType={}",
				documentId, document.getFileContentType());
			return new AnalysisResult(false, null, "Not a PDF file");
		}

		document.setAnalysisStatus(AnalysisStatus.ANALYZING);
		document.setDocumentStatus(DocumentStatus.ANALYZING);

		try
		{
			DocumentData data = performScan(document, zugFerdClient::scanDocument);
			completeAnalysis(document, data, ExtractionSource.ZUGFERD);
			logAuditEvent(document, "AnalyzeDocumentZugFerd", "ZugFerd analysis completed successfully");
			LOG.info("ZugFerd analysis completed: documentId={}", documentId);
			return new AnalysisResult(true, ExtractionSource.ZUGFERD, null);
		}
		catch (Exception e)
		{
			LOG.warn("ZugFerd extraction failed: documentId={}, error={}", documentId, e.getMessage());
			// Reset for AI fallback — not a terminal failure
			document.setAnalysisStatus(AnalysisStatus.PENDING);
			document.setDocumentStatus(DocumentStatus.UPLOADED);
			logAuditEvent(document, "AnalyzeDocumentZugFerd", "ZugFerd analysis failed: " + e.getMessage());
			return new AnalysisResult(false, null, e.getMessage());
		}
	}

	@Transactional
	public void analyzeWithDocumentAi(Long documentId)
	{
		LOG.info("Starting AI analysis: documentId={}", documentId);
		Document document = requireDocument(documentId);
		logAuditEvent(document, "AnalyzeDocumentAi", "Started AI analysis");

		if (!document.hasFile())
		{
			LOG.warn("Document has no file: documentId={}", documentId);
			markAnalysisFailed(document, "Kein Dokument vorhanden");
			return;
		}

		document.setAnalysisStatus(AnalysisStatus.ANALYZING);
		document.setDocumentStatus(DocumentStatus.ANALYZING);

		try
		{
			DocumentData data = performScan(document, documentAiClient::scanDocument);
			completeAnalysis(document, data, ExtractionSource.AI);
			logAuditEvent(document, "AnalyzeDocumentAi", "AI analysis completed successfully");
			LOG.info("AI analysis completed: documentId={}", documentId);
		}
		catch (Exception e)
		{
			LOG.error("AI analysis failed: documentId={}, error={}", documentId, e.getMessage(), e);
			markAnalysisFailed(document, "KI-Analyse fehlgeschlagen: " + e.getMessage());
			logAuditEvent(document, "AnalyzeDocumentAi", "AI analysis failed: " + e.getMessage());
		}
	}

	@Transactional
	public void processReviewResult(Long documentId, ReviewInput reviewInput)
	{
		LOG.info("Processing review result: documentId={}, confirmed={}, reanalyze={}",
			documentId, reviewInput.confirmed(), reviewInput.reanalyze());

		Document document = requireDocument(documentId);

		if (reviewInput.confirmed())
		{
			documentDataService.applyFormData(document, reviewInput.formData());

			Long bommelId = (Long)reviewInput.formData().get("bommelId");
			Bommel bommel = (bommelId != null && bommelId > 0) ? bommelRepository.findByIdScoped(bommelId) : null;
			document.setBommel(bommel);

			documentDataService.updateTags(document, (String)reviewInput.formData().get("tags"));

			document.setDocumentStatus(DocumentStatus.CONFIRMED);
			logAuditEvent(document, "ReviewDocument", "Document confirmed by user");
			LOG.info("Document confirmed: documentId={}", documentId);
		}
		else
		{
			document.setDocumentStatus(DocumentStatus.UPLOADED);
			document.setFlowId(null);
			document.setAnalysisError(null);

			if (reviewInput.reanalyze())
			{
				logAuditEvent(document, "ReviewDocument", "Re-analysis requested by user");
				LOG.info("Re-analysis requested: documentId={}", documentId);
			}
			else
			{
				logAuditEvent(document, "ReviewDocument", "Manual entry selected by user");
				LOG.info("Manual entry selected: documentId={}", documentId);
			}
		}
	}

	private Document requireDocument(Long documentId)
	{
		Document document = documentRepository.findById(documentId);
		if (document == null)
		{
			LOG.error("Document not found: id={}", documentId);
			throw new IllegalStateException("Document not found: " + documentId);
		}
		return document;
	}

	private DocumentData performScan(Document document,
		BiFunction<InputStream, Long, DocumentData> scanner) throws Exception
	{
		try (InputStream fileStream = storageService.downloadFile(document.getFileKey()))
		{
			LOG.debug("Downloaded file from S3: key={}", document.getFileKey());
			DocumentData data = scanner.apply(fileStream, document.getId());
			if (data == null)
			{
				throw new RuntimeException("Scanner returned no data");
			}
			return data;
		}
	}

	private void completeAnalysis(Document document, DocumentData data, ExtractionSource source)
	{
		documentDataApplier.applyDocumentData(document, data, TagSource.AI);
		document.setAnalysisStatus(AnalysisStatus.COMPLETED);
		document.setDocumentStatus(DocumentStatus.ANALYZED);
		document.setExtractionSource(source);
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
