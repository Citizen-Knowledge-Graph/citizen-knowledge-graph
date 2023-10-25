package de.ckg.backend;

import lombok.Getter;
import org.apache.jena.atlas.json.JsonArray;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.graph.Graph;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.shacl.ShaclValidator;
import org.apache.jena.shacl.Shapes;
import org.apache.jena.shacl.ValidationReport;
import org.apache.jena.shacl.lib.ShLib;
import org.apache.jena.tdb.TDBFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ModelController {

    private final Logger logger = LogManager.getLogger(ModelController.class);

    @Getter
    private final Model mainModel;

    public ModelController() {
        Dataset dataset = TDBFactory.createDataset("jena-tdb");
        mainModel = dataset.getDefaultModel();
        mainModel.setNsPrefix("ckg", Utils.DEFAULT_URI_NAMESPACE);
        printStatements();
    }

    @PreDestroy
    private void close() {
        mainModel.close();
    }

    public boolean statementAlreadyPresent(Statement statement) {
        return mainModel.contains(statement);
    }

    public void addStatement(Statement statement) {
        mainModel.add(statement);
        logger.info("Statement added: " + statement.getSubject() + ", " + statement.getPredicate() + ", " + statement.getObject());
    }

    public void printStatements() {
        mainModel.listStatements().toList().forEach(System.out::println);
    }

    public JsonObject runCkgSelectQuery(String query) {
        // regex via ChatGPT and cleaned up via IntelliJ suggestions
        // would be nicer to extract it via query.getValuesVariables(), but didn't get that to work, was always null
        Matcher matcher = Pattern.compile("VALUES\\s+\\?\\S+\\s+\\{([^}]+)}").matcher(query);
        if (!matcher.find()) throw new RuntimeException("No VALUES clause found in the query: " + query);
        Map<String, String> prefixes = new HashMap<>(); // extract instead of entering values here
        prefixes.put("ckg", Utils.DEFAULT_URI_NAMESPACE);
        prefixes.put("vcard", "http://www.w3.org/2006/vcard/ns#");
        prefixes.put("foaf", "http://xmlns.com/foaf/0.1#");
        Set<String> valuesInQuery = Arrays.stream(matcher.group(1).trim().split(" "))
            .map(val -> prefixes.get(val.split(":")[0]) + val.split(":")[1])
            .collect(Collectors.toSet());
        return sortIntoValuesFoundAndNotFound(query, valuesInQuery, new JsonObject());
    }

    public JsonObject sortIntoValuesFoundAndNotFound(String query, Set<String> valuesInQuery, JsonObject report) {
        try (QueryExecution queryExecution = QueryExecutionFactory.create(query, mainModel)) {
            ResultSet resultSet = queryExecution.execSelect();
            JsonObject valuesFound = new JsonObject();
            while(resultSet.hasNext()) {
                QuerySolution qs = resultSet.next();
                String pred = qs.getResource("p").toString();
                if (valuesInQuery.contains(pred)) {
                    valuesFound.put(pred, qs.get("o").toString());
                    valuesInQuery.remove(pred);
                }
            }
            JsonArray valuesNotFound = new JsonArray();
            valuesInQuery.forEach(valuesNotFound::add);
            report.put("valuesFound", valuesFound);
            report.put("valuesNotFound", valuesNotFound);
            return report;
        }
    }

    public JsonObject handleFormWorkflowTurtleFile(InputStream inputStream) {
        Model importModel = ModelFactory.createDefaultModel();
        importModel.read(inputStream, null, "TTL");
        String query = "SELECT * WHERE { ?s ?p ?o . }";
        JsonObject fields = new JsonObject();
        Map<String, String> prefixes = new HashMap<>(); // extract instead of entering values here
        prefixes.put("http://ckg.de/default#", "ckg");
        prefixes.put("http://www.w3.org/2006/vcard/ns#", "vcard");
        prefixes.put("http://xmlns.com/foaf/0.1#", "foaf");
        try (QueryExecution queryExecution = QueryExecutionFactory.create(query, importModel)) {
            ResultSet resultSet = queryExecution.execSelect();
            Set<String> valuesInQuery = new HashSet<>();
            StringBuilder valuesQueryPart = new StringBuilder();
            valuesQueryPart.append("  VALUES ?p { ");
            while (resultSet.hasNext()) {
                QuerySolution qs = resultSet.next();
                String subLocalName = qs.getResource("s").getLocalName();
                if (!subLocalName.startsWith("field")) continue;
                if (!fields.hasKey(subLocalName)) fields.put(subLocalName, new JsonObject());
                String predLocalName = qs.getResource("p").getLocalName();
                String obj = qs.get("o").isLiteral() ? qs.getLiteral("o").getString() : qs.getResource("o").toString();
                fields.getObj(subLocalName).put(predLocalName, obj);
                if (predLocalName.equals("hasPredicate")) {
                    valuesInQuery.add(obj);
                    valuesQueryPart
                        .append(prefixes.get(obj.split("#")[0] + "#"))
                        .append(":")
                        .append(obj.split("#")[1])
                        .append(" ");
                }
            }
            query = "PREFIX ckg: <http://ckg.de/default#> "
                + "PREFIX vcard: <http://www.w3.org/2006/vcard/ns#> "
                + "PREFIX foaf: <http://xmlns.com/foaf/0.1#> "
                + "SELECT ?s ?p ?o WHERE { "
                + valuesQueryPart.append("} ")
                + "  ?s ?p ?o ."
                + "}";
            JsonObject report = new JsonObject();
            report.put("fields", fields);
            return sortIntoValuesFoundAndNotFound(query, valuesInQuery, report);
        }
    }

    public boolean addNewStatement(String sub, String pred, String obj) {
        RDFNode object;
        if (obj.startsWith("http")) {
            object = mainModel.createResource(obj);
        } else {
            try {
                object = mainModel.createTypedLiteral(Integer.parseInt(obj));
            } catch (NumberFormatException e) {
                object = mainModel.createLiteral(obj);
            }
        }
        Statement statement = mainModel.createStatement(
            mainModel.createResource(Utils.ensureUri(sub)),
            mainModel.createProperty(Utils.ensureUri(pred)),
            object
        );
        if (statementAlreadyPresent(statement)) {
            return false;
        }
        addStatement(statement);
        return true;
    }

    public JsonObject getAllTriples() {
        JsonArray triples = new JsonArray();
        mainModel.listStatements().forEachRemaining(statement -> {
            JsonObject triple = new JsonObject();
            triple.put("subject", statement.getSubject().getURI());
            triple.put("predicate", statement.getPredicate().getURI());
            triple.put("object", statement.getObject().toString());
            triples.add(triple);
        });
        JsonObject result = new JsonObject();
        result.put("triples", triples);
        return result;
    }

    @Value("classpath:shapes.ttl")
    private Path SHAPES_FILE;

    public void dev() {
        /*
        "mainPerson", "owns", "http://ckg.de/default#House1"
        "House1", "http://www.w3.org/1999/02/22-rdf-syntax-ns#type", "http://ckg.de/default#House"
        "House1", "roofArea", "110"
        "House1", "houseAge", "35"
        */

        Graph shapesGraph = RDFDataMgr.loadGraph(SHAPES_FILE.toString());

        /*Model model = ModelFactory.createDefaultModel();

        String ckg = "http://ckg.de/default#";
        String sh = "http://www.w3.org/ns/shacl#";
        String xsd = "http://www.w3.org/2001/XMLSchema#";
        String rdf = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

        model.setNsPrefix("ckg", ckg);
        model.setNsPrefix("sh", sh);
        model.setNsPrefix("xsd", xsd);

        Resource eligibleHouseShape = ResourceFactory.createResource(ckg + "EligibleHouseShape");
        Resource house = ResourceFactory.createResource(ckg + "House");
        Property roofArea = ResourceFactory.createProperty(ckg + "roofArea");
        Property houseAge = ResourceFactory.createProperty(ckg + "houseAge");
        Property shProperty = ResourceFactory.createProperty(sh + "property");
        Property shDatatype = ResourceFactory.createProperty(sh + "datatype");
        Property shMinInclusive = ResourceFactory.createProperty(sh + "minInclusive");
        Property shMessage = ResourceFactory.createProperty(sh + "message");
        Property shPath = ResourceFactory.createProperty(sh + "path");
        Property rdfType = ResourceFactory.createProperty(rdf + "type");
        Property shTargetClass = ResourceFactory.createProperty(sh + "targetClass");

        model.add(eligibleHouseShape, rdfType, ResourceFactory.createResource(sh + "NodeShape"));
        model.add(eligibleHouseShape, shTargetClass, house);

        Resource roofAreaShape = model.createResource()
                .addProperty(shPath, roofArea)
                .addProperty(shDatatype, ResourceFactory.createResource(xsd + "integer"))
                .addProperty(shMinInclusive, model.createTypedLiteral("100", XSDDatatype.XSDinteger))
                .addProperty(shMessage, "Roof area is below the minimum required");
        model.add(eligibleHouseShape, shProperty, roofAreaShape);

        Resource houseAgeShape = model.createResource()
                .addProperty(shPath, houseAge)
                .addProperty(shDatatype, ResourceFactory.createResource(xsd + "integer"))
                .addProperty(shMinInclusive, model.createTypedLiteral("30", XSDDatatype.XSDinteger))
                .addProperty(shMessage, "House age is below the minimum required");
        model.add(eligibleHouseShape, shProperty, houseAgeShape);

        // model.write(System.out, "TTL");
*/
        Shapes shapes = Shapes.parse(shapesGraph);
        //Shapes shapes = Shapes.parse(model);
        ValidationReport report = ShaclValidator.get().validate(shapes, mainModel.getGraph());
        ShLib.printReport(report);
        // RDFDataMgr.write(System.out, report.getModel(), Lang.TTL);
    }
}
