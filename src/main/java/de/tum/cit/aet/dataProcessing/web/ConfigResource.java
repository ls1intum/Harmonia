package de.tum.cit.aet.dataProcessing.web;

import de.tum.cit.aet.core.config.HarmoniaProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/config")
public class ConfigResource {

    private final HarmoniaProperties harmoniaProperties;

    public ConfigResource(HarmoniaProperties harmoniaProperties) {
        this.harmoniaProperties = harmoniaProperties;
    }

    @GetMapping("/projects")
    public ResponseEntity<List<HarmoniaProperties.Project>> getProjects() {
        return ResponseEntity.ok(harmoniaProperties.getProjects());
    }
}
