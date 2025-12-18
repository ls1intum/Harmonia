package de.tum.cit.aet.analysis.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/analysis")
@Slf4j
public class AnalysisResource {

    @PostMapping("/{course}/{exercise}/recompute")
    public ResponseEntity<String> recompute(@PathVariable String course, @PathVariable String exercise) {
        log.info("Recompute requested for course: {}, exercise: {}", course, exercise);
        return ResponseEntity.ok("Recompute triggered");
    }
}
