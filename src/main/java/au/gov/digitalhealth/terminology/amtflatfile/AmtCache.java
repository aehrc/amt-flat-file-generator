package au.gov.digitalhealth.terminology.amtflatfile;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jgrapht.alg.TransitiveClosure;
import org.jgrapht.graph.SimpleDirectedGraph;
import au.gov.digitalhealth.terminology.amtflatfile.Junit.JUnitFailure;
import au.gov.digitalhealth.terminology.amtflatfile.Junit.JUnitTestCase;
import au.gov.digitalhealth.terminology.amtflatfile.Junit.JUnitTestSuite;

public class AmtCache {

    private static final String AU_METADATA_MODULE = "161771000036108";

    private static final String INTERNATIONAL_METADATA_MODULE = "900000000000012004";

    private static final String PREFERRED = "900000000000548007";

    private static final String FSN = "900000000000003001";

    private static final String AMT_MODULE_ID = "900062011000036108";

    private static final Logger logger = Logger.getLogger(AmtCache.class.getCanonicalName());
    private static final String INTERNATIONAL_MODULE = "900000000000207008";
    private static final String AU_MODULE = "32506021000036107";

    private static final List<String> HISTORICAL_ASSOCAITION_IDS = List.of("900000000000523009",
            "900000000000530003",
            "900000000000525002",
            "900000000000524003",
            "1186924009",
            "900000000000523009",
            "1186921001",
            "900000000000531004",
            "900000000000526001",
            "900000000000527005",
            "900000000000529008",
            "900000000000528000");

    private SimpleDirectedGraph<Long, Edge> graph = new SimpleDirectedGraph<>(Edge.class);

    private Map<Long, Concept> conceptCache = new HashMap<>();

    public Map<Long, Concept> getConceptCache() {
        return conceptCache;
    }

    private Set<Long> preferredDescriptionIdCache = new HashSet<>();

    private Set<Replacement> replacements = new HashSet<>();

    private boolean exitOnError;

    private JUnitTestSuite testSuite;
    private JUnitTestCase graphCase;

    private Map<Long, Concept> ctppsFromRefset = new HashMap<>();
    private Map<Long, Concept> tppsFromRefset = new HashMap<>();
    private Map<Long, Concept> tpuusFromRefset = new HashMap<>();
    private Map<Long, Concept> tpsFromRefset = new HashMap<>();
    private Map<Long, Concept> mppsFromRefset = new HashMap<>();
    private Map<Long, Concept> mpuusFromRefset = new HashMap<>();
    private Map<Long, Concept> mpsFromRefset = new HashMap<>();
    private Map<String,  Map<Long, Concept>> amtRefsets = Map.ofEntries(
        Map.entry(AmtRefset.CTPP.getIdString(), ctppsFromRefset),
        Map.entry(AmtRefset.TPP.getIdString(), tppsFromRefset),
        Map.entry(AmtRefset.TPUU.getIdString(), tpuusFromRefset),
        Map.entry(AmtRefset.TP.getIdString(), tpsFromRefset),
        Map.entry(AmtRefset.MPP.getIdString(), mppsFromRefset),
        Map.entry(AmtRefset.MPUU.getIdString(), mpuusFromRefset),
        Map.entry(AmtRefset.MP.getIdString(), mpsFromRefset)
    );

    private static final Map<String, AmtConcept> amtRefsetToV3Concept = Map.ofEntries(
        Map.entry(AmtRefset.CTPP.getIdString(), AmtConcept.CTPP),
        Map.entry(AmtRefset.TPP.getIdString(), AmtConcept.TPP),
        Map.entry(AmtRefset.TPUU.getIdString(), AmtConcept.TPUU),
        Map.entry(AmtRefset.TP.getIdString(), AmtConcept.TP),
        Map.entry(AmtRefset.MPP.getIdString(), AmtConcept.MPP),
        Map.entry(AmtRefset.MPUU.getIdString(), AmtConcept.MPUU),
        Map.entry(AmtRefset.MP.getIdString(), AmtConcept.MP)
    );

    private Map<AmtRefset, Map<Long, Concept>> conceptMaps = new HashMap<>();

    private boolean amtV3 = false;

    public boolean isAmtV3() {
        return amtV3;
    }


    public Map<String, Map<Long, Concept>> getAmtRefsets() {
        return amtRefsets;
    }


    public AmtCache(FileSystem amtZip, boolean exitOnError, JUnitTestSuite testSuite) throws IOException {
        this.testSuite = testSuite;
        this.exitOnError = exitOnError;
        processAmtFiles(amtZip);
    }

    private void processAmtFiles(FileSystem amtZip) throws IOException {

        graphCase = new JUnitTestCase().setName("Graph errors");

        TerminologyFileVisitor visitor = new TerminologyFileVisitor();

        Files.walkFileTree(amtZip.getPath("/"), visitor);
        
        visitor.ensureAllFilesExist();

        readFile(visitor.getConceptFile(), s -> handleConceptRow(s), true, "\t");
        readFile(visitor.getRelationshipFile(), s -> handleRelationshipRow(s), true, "\t");
        readFile(visitor.getLanguageRefsetFile(), s -> handleLanguageRefsetRow(s), true, "\t");
        readFile(visitor.getDescriptionFile(), s -> handleDescriptionRow(s), true, "\t");
        readFile(visitor.getArtgIdRefsetFile(), s -> handleArtgIdRefsetRow(s), true, "\t");
        int refsetFileCount = 0;
        for (Path amtRefsetFile : visitor.getAMTRefsetFiles()) {
            readFile(amtRefsetFile, s -> handleAMTRefsetRow(s), true, "\t");
            refsetFileCount++;
        }
        if (refsetFileCount > 1) {
            this.amtV3 = true;
        }
        for (Path historicalFile : visitor.getHistoricalAssociationRefsetFiles()) {
            readFile(historicalFile, s -> handleHistoricalAssociationRefsetRow(s), true, "\t");
        }

        if (this.isAmtV3()) {
            logger.info("######### Working with AMT V3 Release #########");
        } else {
            logger.info("######### Working with AMT V4 Release #########");
        }

        try {
            calculateTransitiveClosure();
        } catch (Exception e) {
            String message = "Could not close graph. Elements missing";
            JUnitFailure fail = new JUnitFailure();
            fail.setMessage(message);
            graphCase.addFailure(fail);
            if (exitOnError) {
                throw new RuntimeException(message);
            }
        }

        Iterator<Entry<Long, Concept>> it = ctppsFromRefset.entrySet().iterator();
        while (it.hasNext()) {
            Entry<Long, Concept> entry = it.next();
            if (!entry.getValue().isActive()) {
                String message = "Found inactive CTPP! " + entry.getValue();
                logger.warning(message);
                testSuite.addTestCase("Inactive CTPP found", entry.getValue().toString(), this.getClass().getName(), "Inactive_CTPP", "ERROR");
                if (exitOnError) {
                    throw new RuntimeException(message);
                }
                it.remove();
            }
        }

        conceptCache.values().stream().forEach(c -> {
            c.addAncestors(
                graph.outgoingEdgesOf(c.getId())
                    .stream()
                    .map(e -> e.getTarget())
                    .collect(Collectors.<Long, Long, Concept> toMap(id -> id, id -> conceptCache.get(id))));
        });

        validateConceptCache();

        logger.info("Loaded " + ctppsFromRefset.size() + " CTPPs " + conceptCache.size() + " concepts ");

        validateUnits();

        logger.info("Validated cached concepts ");
    }

    private void validateConceptCache() {
        // inactive concepts shouldn't have references to other things
        assertConceptCache(c -> !c.isActive() && !c.getParents().isEmpty(), "Inactive concepts with parents", "Inactive_with_parents",
            c -> c.getParents().clear());
        assertConceptCache(c -> !c.isActive() && !c.getTps().isEmpty(), "Inactive concepts with TPs", "Inactive_with_TPs",
            c -> c.getTps().clear());
        assertConceptCache(c -> !c.isActive() && !c.getUnits().isEmpty(), "Inactive concepts with Units", "Inactive_with_Units",
            c -> c.getUnits().clear());
        assertConceptCache(c -> !c.isActive() && !c.getArtgIds().isEmpty(), "Inactive concepts with ARTGIDs", "Inactive_with_ARTGIDs",
            c -> c.getArtgIds().clear());

        // all concepts should have PTs and FSNs
        assertConceptCache(c -> c.getFullSpecifiedName() == null || c.getFullSpecifiedName().isEmpty(), "Concepts with null or empty FSN",
            "Null_or_empty_FSN", c -> c.setFullSpecifiedName("Concept " + c.getId() + " has not FSN!!!")); // fix is a no-op
        assertConceptCache(c -> c.getPreferredTerm() == null || c.getPreferredTerm().isEmpty(), "Concepts with null or empty PT",
            "Null_or_empty_PT", c -> c.setPreferredTerm("Concept " + c.getId() + " has not Preferred Term!!!"));

        // active concepts should only reference active things
        assertConceptCache(c -> c.isActive() && c.getUnits().stream().anyMatch(u -> !u.isActive()),
            "Active concept with inactive linked unit/s", "Active_concept_inactive_units",
            c -> c.getUnits().removeAll(c.getUnits().stream().filter(u -> !u.isActive()).collect(Collectors.toSet())));
        assertConceptCache(c -> c.isActive() && c.getTps().stream().anyMatch(u -> !u.isActive()),
            "Active concept with inactive linked TP/s", "Active_concept_inactive_TP",
            c -> c.getTps().remove(c.getTps()
                .stream()
                .filter(t -> !t.isActive())
                .collect(Collectors.toSet())));
        assertConceptCache(c -> c.isActive() && c.getParents().values().stream().anyMatch(u -> !u.isActive()),
            "Active concept with inactive linked parent/s", "Active_concept_inactive_parents",
            c -> c.getParents().remove(c.getParents()
                .entrySet()
                .stream()
                .filter(e -> !e.getValue().isActive())
                .map(e -> e.getValue())
                .collect(Collectors.toSet())));
    }

    private void assertConceptCache(Predicate<Concept> predicate, String message, String testCaseName, Consumer<Concept> fix) {
        Set<Concept> errors =
                conceptCache.values().stream().filter(predicate).collect(Collectors.toSet());

        if (!errors.isEmpty()) {
            logger.warning(message + " " + errors);
            testSuite.addTestCase(message, errors.toString(), this.getClass().getName(), testCaseName, "ERROR");

            if (exitOnError || fix == null) {
                throw new RuntimeException(message + " " + errors);
            } else {
                logger.warning(
                    "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                logger.warning(
                    "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                logger.warning(
                    "FIX APPLIED FOR ERRONEOUS INPUT DATA - continuing as requested, however RESULTS MAY BE UNRELIABLE AS A RESULT!!!!");
                logger.warning(
                    "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                logger.warning(
                    "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                errors.stream().forEach(fix);
            }
        }
    }

    private void validateUnits() {
        Set<Concept> packConceptsWithNoUnits = graph.incomingEdgesOf(AmtConcept.MPP.getId())
            .stream()
            .map(e -> e.getSource())
            .filter(id -> !AmtConcept.isEnumValue(Long.toString(id)))
            .map(id -> conceptCache.get(id))
            .filter(concept -> concept.getUnits() == null || concept.getUnits().size() == 0)
            .collect(Collectors.toSet());

        Set<Concept> mppsWithTpuus = graph.incomingEdgesOf(AmtConcept.MPP.getId())
            .stream()
            .map(e -> e.getSource())
            .filter(id -> !AmtConcept.isEnumValue(Long.toString(id)))
            .map(id -> conceptCache.get(id))
            .filter(concept -> !concept.hasAtLeastOneMatchingAncestor(AmtConcept.TPP))
            .filter(concept -> concept.getUnits()
                .stream()
                .anyMatch(unit -> unit.hasAtLeastOneMatchingAncestor(AmtConcept.TPUU)))
            .collect(Collectors.toSet());

        Set<Concept> tppsWithMpuus = graph.incomingEdgesOf(AmtConcept.TPP.getId())
            .stream()
            .map(e -> e.getSource())
            .filter(id -> !AmtConcept.isEnumValue(Long.toString(id)))
            .map(id -> conceptCache.get(id))
            .filter(concept -> concept.getUnits()
                .stream()
                .anyMatch(unit -> !unit.hasAtLeastOneMatchingAncestor(AmtConcept.TPUU)))
            .collect(Collectors.toSet());

        if (!packConceptsWithNoUnits.isEmpty() || !mppsWithTpuus.isEmpty() || !tppsWithMpuus.isEmpty()) {

            String detail = "Detected pack concepts with no units "
                    + packConceptsWithNoUnits.stream().map(c -> c.getId() + " |" + c.getPreferredTerm() + "|\n").collect(Collectors.toSet())
                    + " and/or MPPs with TPUU units "
                    + mppsWithTpuus.stream()
                        .map(c -> c.getId() + " |" + c.getPreferredTerm() + "|\n")
                        .collect(Collectors.toSet())
                    + " and/or TPP/CTPPs with MPUU units "
                    + tppsWithMpuus.stream()
                        .map(c -> c.getId() + " |" + c.getPreferredTerm() + "|\n")
                        .collect(Collectors.toSet());

            testSuite.addTestCase("Detected pack concepts with no units and/or MPPs with TPUU units and/or TPP/CTPPs with MPUU units",
                detail, this.getClass().getName(), "heirarchy_error", "ERROR");

            if (exitOnError) {
                throw new RuntimeException(detail);
            }
        }
    }

    public Map<Long, Concept> getCtppsFromRefset() {
        return ctppsFromRefset;
    }

    public Map<Long, Concept> getTppsFromRefset() {
        return tppsFromRefset;
    }

    public Map<Long, Concept> getTpuusFromRefset() {
        return tpuusFromRefset;
    }

    public Map<Long, Concept> getTpsFromRefset() {
        return tpsFromRefset;
    }

    public Map<Long, Concept> getMppsFromRefset() {
        return mppsFromRefset;
    }

    public Map<Long, Concept> getMpuusFromRefset() {
        return mpuusFromRefset;
    }

    public Map<Long, Concept> getMpsFromRefset() {
        return mpsFromRefset;
    }

    private void handleConceptRow(String[] row) {
        try {
            if (isAuModule(row) || isAmtOrMetadataModule(row) || isIntModule(row)) {
                long conceptId = Long.parseLong(row[0]);
                graph.addVertex(conceptId);
                conceptCache.put(conceptId, new Concept(conceptId, isActive(row)));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed processing row: " + String.join(", ",row) + " of Concepts file", e);
        }
    }

    private void handleRelationshipRow(String[] row) {

        try {
            long source = Long.parseLong(row[4]);
            long destination = Long.parseLong(row[5]);
            String type = row[7];

            if (isActive(row) && (isAmtModule(row) || isAuModule(row) || isIntModule(row)) && AttributeType.isEnumValue(type) && graph.containsVertex(source)
                    && graph.containsVertex(destination)) {
                Concept sourceConcept = conceptCache.get(source);

                switch (AttributeType.fromIdString(type)) {
                    case IS_A:
                        graph.addEdge(source, destination);
                        sourceConcept.addParent(conceptCache.get(destination));
                        break;

                    case HAS_INTENDED_ACTIVE_INGREDIENT:
                    case HAS_ACTIVE_INGREDIENT:
                    case HAS_PRECISE_ACTIVE_INGREDIENT:
                        sourceConcept.addIngredient(conceptCache.get(destination));
                        break;

                    case HAS_MPUU:
                    case HAS_TPUU:
                    case CONTAINS_CLINICAL_DRUG:
                    case CONTAINS_DEVICE:
                        sourceConcept.addUnit(conceptCache.get(destination));
                        break;

                    case CONTAINS_PACKAGED_CLINICAL_DRUG:
                        sourceConcept.addSubpack(conceptCache.get(destination));
                        break;

                    case HAS_TP:
                    case HAS_PRODUCT_NAME:
                        sourceConcept.addTp(conceptCache.get(destination));

                    default:
                        break;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed processing row: " + String.join(", ",row) + " of Relationships file", e);
        }

    }

    private void handleDescriptionRow(String[] row) {

        try {

            Long conceptId = Long.parseLong(row[4]);

            if (isActive(row) && (isAmtOrMetadataModule(row) || isIntModule(row) || isAuModule(row)) && conceptCache.containsKey(conceptId)) {
                String descriptionId = row[0];
                String term = row[7];
                Concept concept = conceptCache.get(conceptId);
                if (row[6].equals(FSN)) {
                    concept.setFullSpecifiedName(term);
                } else if (preferredDescriptionIdCache.contains(Long.parseLong(descriptionId))) {
                    concept.setPreferredTerm(term);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed processing row: " + String.join(", ",row) + " of Descriptions file", e);
        }
    }

    private void handleLanguageRefsetRow(String[] row) {

        try {
            if (isActive(row) && (isAmtOrMetadataModule(row) || isAuModule(row)) && row[6].equals(PREFERRED)) {
                preferredDescriptionIdCache.add(Long.parseLong(row[5]));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed processing row: " + String.join(", ",row) + " of Language file", e);
        }

    }

    private void handleArtgIdRefsetRow(String[] row) {
        try {
            long conceptId = Long.parseLong(row[5]);
            if (isActive(row) && (isAmtModule(row) || isAuModule(row))) {
                conceptCache.get(conceptId).addArtgIds(row[6]);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed processing row: " + String.join(", ",row) + " of ARTG file", e);
        }
    }

    private void handleAMTRefsetRow(String[] row) {
        try {
            long conceptId = Long.parseLong(row[5]);
            if (isActive(row) && (isAmtModule(row) || isAuModule(row))) {
                String refsetId = row[4];
                AmtConcept v3Concept = amtRefsetToV3Concept.get(refsetId);
                if (v3Concept != null) {
                    Concept concept = conceptCache.get(conceptId);
                    concept.setType(v3Concept);
                    amtRefsets.get(refsetId).put(conceptId, concept);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed processing row: " + String.join(", ",row) + " of ARTG file", e);
        }
    }

    private void handleHistoricalAssociationRefsetRow(String[] row) {
        try {
            if (isActive(row) && (isAmtModule(row) || isAuModule(row)) && !isDescriptionId(row[5]) && HISTORICAL_ASSOCAITION_IDS.contains(row[4])) {
                Concept replacementType = conceptCache.get(Long.parseLong(row[4]));
                Concept inactiveConcept = conceptCache.get(Long.parseLong(row[5]));
                Concept replacementConcept = conceptCache.get(Long.parseLong(row[6]));

                if (replacementType == null || inactiveConcept == null || replacementConcept == null) {
                    throw new RuntimeException("Failed processing row: " + String.join("\t", row) + " of History file.\nOne of the concepts is null. "
                            + "\nreplacementType: " + replacementType + " replacement id is " + Long.parseLong(row[4]) + "\ninactiveConcept: " + inactiveConcept + "\nreplacementConcept: "
                            + replacementConcept);
                }

                replacements.add(new Replacement(inactiveConcept, replacementType, replacementConcept, row[1]));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed processing row: " + String.join(", ",row) + " of History file", e);
        }
    }

    private boolean isDescriptionId(String id) {
        return id.substring(id.length() - 2, id.length() - 1).equals("1");
    }

    private void calculateTransitiveClosure() {
        logger.info("Calculating transitive closure");
        TransitiveClosure.INSTANCE.closeSimpleDirectedGraph(graph);
        logger.info("Calculated transitive closure");
    }

    private boolean isActive(String[] row) {
        return row[2].equals("1");
    }

    private boolean isAmtModule(String[] row) {
        return row[3].equals(AMT_MODULE_ID);
    }

    private boolean isAmtOrMetadataModule(String[] row) {
        return row[3].equals(AMT_MODULE_ID) || row[3].equals(INTERNATIONAL_METADATA_MODULE) || row[3].equals(AU_METADATA_MODULE);
    }
    private boolean isIntModule(String[] row) {
        return row[3].equals(INTERNATIONAL_MODULE);
    }
    private boolean isAuModule(String[] row) {
        return row[3].equals(AU_MODULE);
    }
    public Set<Replacement> getReplacementConcepts() {
        return replacements;
    }

    @SuppressWarnings("resource")
    public static void readFile(Path path, Consumer<String[]> consumer, boolean hasHeader, String delimiter)
            throws IOException {
        Stream<String> stream = Files.lines(path);
        if (hasHeader) {
            stream = stream.skip(1);
        }
        stream.map(s -> s.split(delimiter, -1)).forEach(consumer);
        stream.close();
        logger.info("Processed " + path);
    }
}
