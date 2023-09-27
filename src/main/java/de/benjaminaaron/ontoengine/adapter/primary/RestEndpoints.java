package de.benjaminaaron.ontoengine.adapter.primary;

import de.benjaminaaron.ontoengine.domain.ModelController;
import de.benjaminaaron.ontoengine.domain.importer.RdfImporter;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;


@RestController
@RequestMapping("api/v1/ckg")
public class RestEndpoints {

    private final Logger logger = LogManager.getLogger(RestEndpoints.class);

    @Autowired
    protected ModelController modelController;

    @PutMapping(value = "/query", consumes = "text/plain")
    public ResponseEntity<String> query(@RequestBody String query) {
        return ResponseEntity.ok(modelController.runCkgSelectQuery(query).toString());
    }

    @PostMapping(value = "/importTurtle")
    public ResponseEntity<String> handleTurtleImport(@RequestBody String turtleData) {
        JsonObject report = RdfImporter.doImportFromInputStream(modelController,
            new ByteArrayInputStream(turtleData.getBytes(StandardCharsets.UTF_8)));
        return ResponseEntity.ok().body(report.toString());
    }

    @PutMapping(value = "/formWorkflow")
    public ResponseEntity<String> handleFormWorkflowTurtleFile(@RequestBody String turtleData) {
        JsonObject report = modelController.handleFormWorkflowTurtleFile(
            new ByteArrayInputStream(turtleData.getBytes(StandardCharsets.UTF_8)));
        return ResponseEntity.ok().body(report.toString());
    }

    @PostMapping(value = "/addNewStatement")
    public ResponseEntity<String> addNewStatement(@RequestBody List<String> statementParts) {
        boolean wasAdded = modelController.addNewStatement(
            statementParts.get(0), statementParts.get(1), statementParts.get(2));
        return ResponseEntity.ok().body("Was added: " + wasAdded);
    }

    @GetMapping(value = "/getAllTriples")
    public ResponseEntity<String> getAllTriples() {
        return ResponseEntity.ok().body(modelController.getAllTriples().toString());
    }
}
