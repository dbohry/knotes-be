package com.lhamacorp.knotes.service;

import com.github.f4b6a3.ulid.Ulid;
import com.github.f4b6a3.ulid.UlidCreator;
import com.lhamacorp.knotes.api.dto.NoteMetadata;
import com.lhamacorp.knotes.domain.Note;
import com.lhamacorp.knotes.exception.NotFoundException;
import com.lhamacorp.knotes.repository.NoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.lhamacorp.knotes.api.dto.NoteMetadata.from;
import static java.time.Instant.now;

@Service
public class NoteService {

    private final NoteRepository repository;

    private final ConcurrentHashMap<String, CompletableFuture<Note>> updateQueues = new ConcurrentHashMap<>();

    private static final String ONCE_PER_DAY_AT_2AM = "0 0 2 * * *";

    Logger log = LoggerFactory.getLogger(NoteService.class);

    public NoteService(NoteRepository repository) {
        this.repository = repository;
    }

    public boolean exists(String id) {
        return repository.existsById(id);
    }

    @Cacheable(value = "contentCache", key = "#id")
    public Note findById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Note with id " + id + " not found!"));
    }

    @Cacheable(value = "metadataCache", key = "#id")
    public NoteMetadata findMetadataById(String id) {
        Note projection = repository.findMetadataProjectionById(id)
                .orElseThrow(() -> new NotFoundException("Note with id " + id + " not found!"));
        return from(projection);
    }

    @CacheEvict(value = {"contentCache", "metadataCache"}, key = "#result.id")
    public Note save(String content) {
        Ulid id = UlidCreator.getUlid();

        log.info("Saving note [{}]", id);

        Instant now = now();
        return repository.save(new Note(id.toString(), content, now, now));
    }

    @CacheEvict(value = {"contentCache", "metadataCache"}, key = "#id")
    public CompletableFuture<Note> queueUpdate(String id, String content) {
        return updateQueues.compute(id, (noteId, existingQueue) ->
                (existingQueue != null ? existingQueue : CompletableFuture.completedFuture(null))
                        .thenCompose(ignored -> CompletableFuture.supplyAsync(() -> {
                            try {
                                return performUpdate(noteId, content);
                            } finally {
                                updateQueues.remove(noteId, updateQueues.get(noteId));
                            }
                        }))
        );
    }

    private Note performUpdate(String id, String content) {
        Note note = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Note with id " + id + " not found!"));

        log.info("Updating note [{}]", id);

        Instant now = now();
        return repository.save(new Note(id, content, note.createdAt(), now));
    }

    @Scheduled(cron = ONCE_PER_DAY_AT_2AM)
    public void cleanup() {
        List<Note> emptyNotes = repository.findEmptyNotes();
        List<String> ids = emptyNotes.stream()
                .map(Note::id)
                .toList();

        if (!ids.isEmpty()) {
            log.info("Cleaning empty notes [{}]", ids);
            repository.deleteAllById(ids);
        }
    }

}
