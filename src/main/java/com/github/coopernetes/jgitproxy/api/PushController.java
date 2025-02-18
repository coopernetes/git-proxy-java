package com.github.coopernetes.jgitproxy.api;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/push", produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class PushController {

    @GetMapping()
    public ResponseEntity<Object> getPushes(@RequestParam Map<String, String> queryParams) {
        // TODO: Implement this method
        log.debug("Received request to get pushes with query params: {}", queryParams);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> getPush(@PathVariable String id) {
        // TODO: Implement this method
        log.debug("Received request to get push with id: {}", id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Object> rejectPush(@PathVariable String id, @RequestBody Map<String, Object> body) {
        // TODO: Implement this method
        log.debug("Received request to reject push with id: {}", id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/authorise")
    public ResponseEntity<Object> authorisePush(@PathVariable String id, @RequestBody Map<String, Object> body) {
        // TODO: Implement this method
        log.debug("Received request to authorise push with id: {}", id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Object> cancelPush(@PathVariable String id) {
        // TODO: Implement this method
        log.debug("Received request to cancel push with id: {}", id);
        return ResponseEntity.ok().build();
    }
}
