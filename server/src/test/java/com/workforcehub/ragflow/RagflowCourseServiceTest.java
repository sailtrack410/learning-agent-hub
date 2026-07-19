package com.workforcehub.ragflow;

import com.workforcehub.file.repository.AttachmentRefRepository;
import com.workforcehub.ragflow.repository.RagflowRepository;
import com.workforcehub.ragflow.repository.RagflowVectorRepository;
import com.workforcehub.ragflow.service.RagflowCourseService;
import com.workforcehub.ragflow.service.RagflowIngestService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executor;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RagflowCourseServiceTest {
    private final RagflowRepository repository = mock(RagflowRepository.class);
    private final RagflowVectorRepository vectorRepository = mock(RagflowVectorRepository.class);
    private final RagflowCourseService service = new RagflowCourseService(repository,
        mock(AttachmentRefRepository.class), mock(RagflowIngestService.class), vectorRepository, Runnable::run);

    @Test
    void shouldCleanDatabaseBeforeDeactivatingVectors() {
        service.deleteSpace(9001L, 8001L);

        var deletion = inOrder(repository, vectorRepository);
        deletion.verify(repository).softDeleteDocumentsBySpace(9001L, 8001L);
        deletion.verify(repository).deactivateSpaceChunks(9001L);
        deletion.verify(repository).softDeleteSpace(9001L, 8001L);
        deletion.verify(vectorRepository).deactivateSpaceVectors(9001L);
    }

    @Test
    void shouldDeactivateVectorsAfterTransactionCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.deleteSpace(9001L, 8001L);

            verify(vectorRepository, never()).deactivateSpaceVectors(9001L);
            for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(vectorRepository).deactivateSpaceVectors(9001L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
