package com.workforcehub.ragflow.service;

import com.workforcehub.file.repository.FileObjectRepository;
import com.workforcehub.file.repository.FileObjectRow;
import com.workforcehub.file.storage.StorageObjectResource;
import com.workforcehub.file.storage.StorageProvider;
import com.workforcehub.file.storage.StorageProviderRegistry;
import com.workforcehub.ragflow.repository.RagflowGlobalSettingRow;
import com.workforcehub.ragflow.repository.RagflowRepository;
import com.workforcehub.ragflow.repository.RagflowSpaceRow;
import com.workforcehub.ragflow.repository.RagflowVectorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagflowIngestServiceTest {

    @Test
    void shouldPersistEveryIngestStageBeforeCompleting() {
        RagflowRepository repository = mock(RagflowRepository.class);
        FileObjectRepository fileObjects = mock(FileObjectRepository.class);
        StorageProviderRegistry providers = mock(StorageProviderRegistry.class);
        StorageProvider provider = mock(StorageProvider.class);
        RagflowEmbeddingClient embeddingClient = mock(RagflowEmbeddingClient.class);
        RagflowConfigResolver configResolver = mock(RagflowConfigResolver.class);
        RagflowVectorRepository vectors = mock(RagflowVectorRepository.class);
        RagflowIngestService service = new RagflowIngestService(
            repository,
            mock(com.workforcehub.agent.repository.AgentRepository.class),
            fileObjects,
            providers,
            embeddingClient,
            configResolver,
            vectors,
            RestClient.builder()
        );

        when(repository.getGlobalSetting()).thenReturn(mock(RagflowGlobalSettingRow.class));
        when(repository.getSpaceOrThrow(9001L)).thenReturn(mock(RagflowSpaceRow.class));
        when(configResolver.resolve(any(), any())).thenReturn(
            new RagflowEffectiveConfig(7001L, 2, 800, 100, 5, new BigDecimal("0.50")));
        when(fileObjects.getFileObjectOrThrow(4001L)).thenReturn(
            new FileObjectRow(4001L, "LOCAL", "files", "course/lesson.md", 18L, "hash"));
        when(providers.require("LOCAL")).thenReturn(provider);
        when(provider.loadObject("files", "course/lesson.md")).thenReturn(
            new StorageObjectResource(new ByteArrayResource("Servlet 请求响应".getBytes(StandardCharsets.UTF_8)), 18L));
        when(embeddingClient.embed(any(), eq(7001L), eq(2))).thenReturn(List.of(List.of(0.1, 0.2)));
        when(vectors.replaceDocumentVectors(eq(9001L), eq(8001L), eq(6001L), any(), any()))
            .thenReturn(List.of(5001L));
        when(repository.completeIngestIfActive(eq(9001L), eq(8001L), eq(6001L), any(), any(), any()))
            .thenReturn(true);

        service.ingest(9001L, 8001L, 6001L, 4001L, "lesson.md", "text/markdown", "TEXT");

        var progress = inOrder(repository);
        progress.verify(repository).markIngestRunning(8001L, 6001L);
        progress.verify(repository).updateIngestProgress(6001L, 15, "PARSE_CONTENT");
        progress.verify(repository).updateIngestProgress(6001L, 35, "SPLIT_CHUNKS");
        progress.verify(repository).updateIngestProgress(6001L, 50, "VECTORIZE");
        progress.verify(repository).updateIngestProgress(6001L, 75, "WRITE_VECTOR");
        progress.verify(repository).updateIngestProgress(6001L, 90, "COMMIT_INDEX");
        progress.verify(repository).completeIngestIfActive(eq(9001L), eq(8001L), eq(6001L), any(), any(), any());
    }
}
