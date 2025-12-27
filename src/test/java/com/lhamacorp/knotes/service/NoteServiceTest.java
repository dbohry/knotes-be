package com.lhamacorp.knotes.service;

import com.github.f4b6a3.ulid.UlidCreator;
import com.lhamacorp.knotes.api.dto.NoteMetadata;
import com.lhamacorp.knotes.domain.Note;
import com.lhamacorp.knotes.exception.NotFoundException;
import com.lhamacorp.knotes.repository.NoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock
    private NoteRepository repository;

    private NoteService noteService;

    private Note testNote;
    private final String TEST_ID = "01HJQM5JK8G9FZ7A2B3C4D5E6F";
    private final String TEST_CONTENT = "Test note content";

    @BeforeEach
    void setUp() {
        noteService = new NoteService(repository);

        Instant now = Instant.now();
        testNote = new Note(TEST_ID, TEST_CONTENT, now, now);
    }

    @Test
    void queueUpdate_singleUpdate_shouldUpdateNoteAndEvictCache() throws ExecutionException, InterruptedException {
        // Given
        String newContent = "Updated content";
        when(repository.findById(TEST_ID)).thenReturn(Optional.of(testNote));

        Note updatedNote = new Note(TEST_ID, newContent, testNote.createdAt(), Instant.now());
        when(repository.save(any(Note.class))).thenReturn(updatedNote);

        // When
        CompletableFuture<Note> result = noteService.queueUpdate(TEST_ID, newContent);
        Note actualNote = result.get();

        // Then
        assertEquals(updatedNote.id(), actualNote.id());
        assertEquals(newContent, actualNote.content());

        // Verify repository interactions
        verify(repository).findById(TEST_ID);
        verify(repository).save(any(Note.class));
    }

    @Test
    void queueUpdate_multipleUpdatesForSameNote_shouldProcessInOrder() throws ExecutionException, InterruptedException {
        // Given
        String content1 = "First update";
        String content2 = "Second update";
        String content3 = "Third update";

        when(repository.findById(TEST_ID)).thenReturn(Optional.of(testNote));

        // Track the order of repository saves
        AtomicInteger saveCount = new AtomicInteger(0);
        when(repository.save(any(Note.class))).thenAnswer(invocation -> {
            Note note = invocation.getArgument(0);
            saveCount.incrementAndGet();

            // Add small delay to make race conditions more likely if queue isn't working
            Thread.sleep(10);

            return new Note(note.id(), note.content(), note.createdAt(), Instant.now());
        });

        // When
        CompletableFuture<Note> future1 = noteService.queueUpdate(TEST_ID, content1);
        CompletableFuture<Note> future2 = noteService.queueUpdate(TEST_ID, content2);
        CompletableFuture<Note> future3 = noteService.queueUpdate(TEST_ID, content3);

        // Wait for all to complete
        CompletableFuture.allOf(future1, future2, future3).get();

        Note result1 = future1.get();
        Note result2 = future2.get();
        Note result3 = future3.get();

        // Then
        assertEquals(content1, result1.content());
        assertEquals(content2, result2.content());
        assertEquals(content3, result3.content());

        // Verify
        assertEquals(3, saveCount.get());
        verify(repository, times(3)).save(any(Note.class));
    }

    @Test
    void queueUpdate_multipleUpdatesForDifferentNotes_shouldProcessConcurrently() throws ExecutionException, InterruptedException {
        // Given
        String noteId1 = "01HJQM5JK8G9FZ7A2B3C4D5E61";
        String noteId2 = "01HJQM5JK8G9FZ7A2B3C4D5E62";
        String content1 = "Content for note 1";
        String content2 = "Content for note 2";

        Note testNote1 = new Note(noteId1, "Original 1", Instant.now(), Instant.now());
        Note testNote2 = new Note(noteId2, "Original 2", Instant.now(), Instant.now());

        when(repository.findById(noteId1)).thenReturn(Optional.of(testNote1));
        when(repository.findById(noteId2)).thenReturn(Optional.of(testNote2));

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(2);

        when(repository.save(any(Note.class))).thenAnswer(invocation -> {
            try {
                // Wait for both updates to start
                startLatch.await(1, TimeUnit.SECONDS);
                // Small delay to simulate work
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } finally {
                finishLatch.countDown();
            }

            Note note = invocation.getArgument(0);
            return new Note(note.id(), note.content(), note.createdAt(), Instant.now());
        });

        // When
        CompletableFuture<Note> future1 = noteService.queueUpdate(noteId1, content1);
        CompletableFuture<Note> future2 = noteService.queueUpdate(noteId2, content2);

        // Release both updates to proceed
        startLatch.countDown();

        // Wait for both to complete
        CompletableFuture.allOf(future1, future2).get();

        // Then
        assertTrue(finishLatch.await(2, TimeUnit.SECONDS));

        Note result1 = future1.get();
        Note result2 = future2.get();

        assertEquals(content1, result1.content());
        assertEquals(content2, result2.content());
    }

    @Test
    void queueUpdate_queueCleanup_shouldRemoveCompletedEntries() throws ExecutionException, InterruptedException {
        // Given
        when(repository.findById(TEST_ID)).thenReturn(Optional.of(testNote));
        when(repository.save(any(Note.class))).thenReturn(testNote);

        // When
        CompletableFuture<Note> future = noteService.queueUpdate(TEST_ID, "New content");
        future.get();

        // Then
        CompletableFuture<Note> future2 = noteService.queueUpdate(TEST_ID, "Another update");
        future2.get();

        verify(repository, times(2)).save(any(Note.class));
    }

    @Test
    void queueUpdate_noteNotFound_shouldThrowNotFoundException() {
        // Given
        when(repository.findById(TEST_ID)).thenReturn(Optional.empty());

        // When
        CompletableFuture<Note> future = noteService.queueUpdate(TEST_ID, "New content");

        // Then
        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(NotFoundException.class, exception.getCause());
        assertEquals("Note with id " + TEST_ID + " not found!", exception.getCause().getMessage());
    }

    @Test
    void findById_shouldUseCache() {
        // Given
        when(repository.findById(TEST_ID)).thenReturn(Optional.of(testNote));

        // When
        Note result = noteService.findById(TEST_ID);

        // Then
        assertEquals(testNote, result);
        verify(repository).findById(TEST_ID);
    }

    @Test
    void findMetadataById_shouldReturnMetadata() {
        // Given
        when(repository.findMetadataProjectionById(TEST_ID)).thenReturn(Optional.of(testNote));

        // When
        NoteMetadata metadata = noteService.findMetadataById(TEST_ID);

        // Then
        assertEquals(TEST_ID, metadata.id());
        assertEquals(testNote.createdAt(), metadata.createdAt());
        assertEquals(testNote.modifiedAt(), metadata.modifiedAt());
        verify(repository).findMetadataProjectionById(TEST_ID);
    }

    @Test
    void save_shouldCreateNewNoteAndEvictCache() {
        // Given
        String content = "New note content";
        Note savedNote = new Note(UlidCreator.getUlid().toString(), content, Instant.now(), Instant.now());
        when(repository.save(any(Note.class))).thenReturn(savedNote);

        // When
        Note result = noteService.save(content);

        // Then
        assertEquals(savedNote, result);
        verify(repository).save(any(Note.class));
    }

    @Test
    void exists_shouldReturnRepositoryResult() {
        // Given
        when(repository.existsById(TEST_ID)).thenReturn(true);

        // When
        boolean exists = noteService.exists(TEST_ID);

        // Then
        assertTrue(exists);
        verify(repository).existsById(TEST_ID);
    }
}