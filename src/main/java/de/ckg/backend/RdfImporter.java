package de.ckg.backend;

import lombok.SneakyThrows;
import org.apache.jena.atlas.json.JsonArray;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class RdfImporter {

    private static final Logger logger = LogManager.getLogger(RdfImporter.class);

    private static JsonObject statementToJsonObj(Statement statement) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.put("subject", statement.getSubject().toString());
        jsonObject.put("predicate", statement.getPredicate().toString());
        jsonObject.put("object", statement.getObject().toString());
        return jsonObject;
    }

    @SneakyThrows
    public static JsonObject doImportFromInputStream(ModelController modelController, InputStream inputStream) {
        Model importModel = ModelFactory.createDefaultModel();
        importModel.read(inputStream, null, "TTL");
        StmtIterator iter = importModel.listStatements();
        JsonArray imported = new JsonArray();
        JsonArray alreadyPresent = new JsonArray();
        while (iter.hasNext()) {
            Statement stmt = iter.nextStatement();
            if (modelController.statementAlreadyPresent(stmt)) {
                alreadyPresent.add(statementToJsonObj(stmt));
                continue;
            }
            modelController.addStatement(stmt);
            imported.add(statementToJsonObj(stmt));
        }
        logger.info("Import of uploaded RDF/TTL file completed");
        JsonObject jsonResponse = new JsonObject();
        jsonResponse.put("imported", imported);
        jsonResponse.put("alreadyPresent", alreadyPresent);
        return jsonResponse;
    }
}
