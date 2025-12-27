package com.lhamacorp.knotes.api;

import com.lhamacorp.knotes.api.dto.NoteMetadata;
import com.lhamacorp.knotes.api.dto.NoteRequest;
import com.lhamacorp.knotes.api.dto.NoteResponse;
import com.lhamacorp.knotes.api.dto.NoteUpdateRequest;
import com.lhamacorp.knotes.domain.Note;
import com.lhamacorp.knotes.service.NoteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

import static com.lhamacorp.knotes.api.dto.NoteResponse.from;
import static org.springframework.http.ResponseEntity.ok;

@RestController
@RequestMapping("api/notes")
@CrossOrigin(origins = "*")
public class NoteController {

    private final NoteService service;

    public NoteController(NoteService service) {
        this.service = service;
    }

    @GetMapping("{id}")
    public ResponseEntity<NoteResponse> find(@PathVariable String id) {
        Note note = service.findById(id);
        return ok().body(from(note));
    }

    @GetMapping("{id}/metadata")
    public ResponseEntity<NoteMetadata> getMetadata(@PathVariable String id) {
        NoteMetadata metadata = service.findMetadataById(id);
        return ok().body(metadata);
    }

    @PostMapping
    public ResponseEntity<NoteResponse> save(@RequestBody NoteRequest request) {
        Note savedNote = service.save(request.note());
        return ok().body(from(savedNote));
    }

    @PutMapping
    public CompletableFuture<ResponseEntity<NoteResponse>> update(@RequestBody NoteUpdateRequest request) {
        return service.queueUpdate(request.id(), request.content())
                .thenApply(update -> ok().body(from(update)));
    }

}
