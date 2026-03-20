package app.fuggs.document.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import app.fuggs.document.domain.AnalysisStatus;
import app.fuggs.document.domain.Document;
import app.fuggs.document.flow.DocumentFlowService;

@ExtendWith(MockitoExtension.class)
class DocumentAnalysisServiceTest
{
	@Mock
	DocumentFlowService flowService;

	@InjectMocks
	DocumentAnalysisService documentAnalysisService;

	private Document document;

	@BeforeEach
	void setUp()
	{
		document = new Document();
		document.id = 123L;
	}

	@Test
	void shouldTriggerAnalysisSuccessfully()
	{
		// Given
		String analyzedBy = "test-user";
		String flowId = "document-123";
		when(flowService.startDocumentProcessing(document.getId())).thenReturn(flowId);

		// When
		boolean result = documentAnalysisService.triggerAnalysis(document, analyzedBy);

		// Then
		assertTrue(result);
		assertEquals(flowId, document.getFlowId());
		assertEquals(analyzedBy, document.getAnalyzedBy());
		verify(flowService).startDocumentProcessing(document.getId());
	}

	@Test
	void shouldReturnFalseWhenAnalysisTriggerFails()
	{
		// Given
		String analyzedBy = "test-user";
		when(flowService.startDocumentProcessing(document.getId()))
			.thenThrow(new RuntimeException("Flow error"));

		// When
		boolean result = documentAnalysisService.triggerAnalysis(document, analyzedBy);

		// Then
		assertFalse(result);
		verify(flowService).startDocumentProcessing(document.getId());
	}

	@Test
	void shouldMarkAnalysisAsFailed()
	{
		// Given
		String errorMessage = "AI service unavailable";

		// When
		documentAnalysisService.markAnalysisFailed(document, errorMessage);

		// Then
		assertEquals(AnalysisStatus.FAILED, document.getAnalysisStatus());
		assertEquals(errorMessage, document.getAnalysisError());
	}

	@Test
	void shouldHandleNullErrorMessage()
	{
		// When
		documentAnalysisService.markAnalysisFailed(document, null);

		// Then
		assertEquals(AnalysisStatus.FAILED, document.getAnalysisStatus());
		assertNull(document.getAnalysisError());
	}

	@Test
	void shouldHandleBlankAnalyzedBy()
	{
		// Given
		String analyzedBy = "";
		when(flowService.startDocumentProcessing(document.getId())).thenReturn("document-123");

		// When
		boolean result = documentAnalysisService.triggerAnalysis(document, analyzedBy);

		// Then
		assertTrue(result);
		assertEquals("", document.getAnalyzedBy());
	}

	@Test
	void shouldHandleBlankErrorMessage()
	{
		// When
		documentAnalysisService.markAnalysisFailed(document, "");

		// Then
		assertEquals(AnalysisStatus.FAILED, document.getAnalysisStatus());
		assertEquals("", document.getAnalysisError());
	}

	@Test
	void shouldMarkAlreadyFailedDocumentAsFailedAgain()
	{
		// Given
		document.setAnalysisStatus(AnalysisStatus.FAILED);
		document.setAnalysisError("First error");

		// When
		documentAnalysisService.markAnalysisFailed(document, "Second error");

		// Then
		assertEquals(AnalysisStatus.FAILED, document.getAnalysisStatus());
		assertEquals("Second error", document.getAnalysisError());
	}
}
