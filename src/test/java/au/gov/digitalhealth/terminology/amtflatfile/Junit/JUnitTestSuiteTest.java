package au.gov.digitalhealth.terminology.amtflatfile.Junit;

import au.gov.digitalhealth.JUnitFileParser;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * AmtCache.handleRowError files every bad row under one test case, "DataLoadingError". These tests
 * write the suite to XML and read it back, because that file is what the build reports.
 */
public class JUnitTestSuiteTest {

  private static final String CLASS_NAME = "au.gov.digitalhealth.terminology.amtflatfile.AmtCache";

  @Test(description = "Every bad row is reported once, even when they share a test case")
  public void reportsEachFailureUnderASharedTestCase() throws IOException {
    JUnitTestSuite suite = new JUnitTestSuite();

    suite.addTestCase("Failed processing row: [row-1]", "concept is null", CLASS_NAME, "DataLoadingError", "ERROR");
    suite.addTestCase("Failed processing row: [row-2]", "concept is null", CLASS_NAME, "DataLoadingError", "ERROR");
    suite.addTestCase("Failed processing row: [row-3]", "concept is null", CLASS_NAME, "DataLoadingError", "ERROR");

    // Any order: only that each row appears exactly once matters.
    Assert.assertEqualsNoOrder(writtenFailureMessages(suite).toArray(), new Object[] {
        "Failed processing row: [row-1]",
        "Failed processing row: [row-2]",
        "Failed processing row: [row-3]"});
  }

  @Test(description = "The same failure added twice is reported once")
  public void reportsARepeatedFailureOnce() throws IOException {
    JUnitTestSuite suite = new JUnitTestSuite();

    suite.addTestCase("Failed processing row: [row-1]", "concept is null", CLASS_NAME, "DataLoadingError", "ERROR");
    suite.addTestCase("Failed processing row: [row-1]", "concept is null", CLASS_NAME, "DataLoadingError", "ERROR");

    Assert.assertEquals(writtenFailureMessages(suite), List.of("Failed processing row: [row-1]"));
  }

  private static List<String> writtenFailureMessages(JUnitTestSuite suite) throws IOException {
    File xml = File.createTempFile("junit-suite", ".xml");
    xml.deleteOnExit();
    try (BufferedWriter out = new BufferedWriter(new FileWriter(xml))) {
      suite.writeToFile(out);
    }
    return JUnitFileParser.parse(xml).stream()
        .flatMap(testCase -> testCase.getFailures().stream())
        .map(JUnitFailure::getMessage)
        .collect(Collectors.toList());
  }
}
