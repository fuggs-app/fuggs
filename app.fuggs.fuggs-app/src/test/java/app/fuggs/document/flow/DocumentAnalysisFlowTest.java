package app.fuggs.document.flow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import app.fuggs.document.domain.ExtractionSource;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

@QuarkusTest
class DocumentAnalysisFlowTest
{
	@Inject
	DocumentAnalysisFlow flow;

	@InjectMock
	DocumentAnalysisActivitiesService activities;

	@Test
	void shouldSkipAiWhenZugFerdSucceeds()
	{
		when(activities.analyzeWithZugFerd(1L))
			.thenReturn(new AnalysisResult(1L, true, ExtractionSource.ZUGFERD, null));

		flow.startInstance(Map.of("documentId", 1L))
			.await().atMost(Duration.ofSeconds(5));

		verify(activities).analyzeWithZugFerd(1L);
		verify(activities, never()).analyzeWithDocumentAi(any());
	}

	@Test
	void shouldCallAiWhenZugFerdFails()
	{
		when(activities.analyzeWithZugFerd(2L))
			.thenReturn(new AnalysisResult(2L, false, null, "Connection refused"));

		flow.startInstance(Map.of("documentId", 2L))
			.await().atMost(Duration.ofSeconds(5));

		verify(activities).analyzeWithZugFerd(2L);
		verify(activities).analyzeWithDocumentAi(2L);
	}

	@Test
	void shouldCallAiWhenDocumentIsNotPdf()
	{
		when(activities.analyzeWithZugFerd(3L))
			.thenReturn(new AnalysisResult(3L, false, null, "Not a PDF file"));

		flow.startInstance(Map.of("documentId", 3L))
			.await().atMost(Duration.ofSeconds(5));

		verify(activities).analyzeWithZugFerd(3L);
		verify(activities).analyzeWithDocumentAi(3L);
	}
}
