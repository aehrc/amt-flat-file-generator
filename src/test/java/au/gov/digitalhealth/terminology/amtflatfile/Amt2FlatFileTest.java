package au.gov.digitalhealth.terminology.amtflatfile;

import static org.junit.Assert.fail;

import au.gov.digitalhealth.JUnitFileParser;
import au.gov.digitalhealth.terminology.amtflatfile.Junit.JUnitFailure;
import au.gov.digitalhealth.terminology.amtflatfile.Junit.JUnitTestCase;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.xml.stream.XMLStreamException;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

public class Amt2FlatFileTest {

  private final String testResDirectory = "src/test/resources/";
  private final String outFilePath = "target/test-out/out";

  //Clean up files between tests
  @AfterMethod()
  public void clearOutputFile() {
    clearFile(new File(FileFormat.CSV.getFilePath(outFilePath)));
    clearFile(new File(FileFormat.TSV.getFilePath(outFilePath)));
  }

  private void clearFile(File fileToClear) {
    if (fileToClear.exists()) {
      fileToClear.delete();
    }
  }

  @Test(groups = "files", priority = 1, description = "Tests that the JUnit file gets generated")
  public void JUnitGenerated() throws MojoExecutionException, MojoFailureException, IOException {
    Amt2FlatFile amt2FlatFile = new Amt2FlatFile();
    amt2FlatFile.setInputZipFilePath("target/test-classes/rf2-fails-flat-file-generation-1.0.zip");
    amt2FlatFile.setOutputFilePath(outFilePath);
    amt2FlatFile.execute();
    File validXml = new File("target/ValidationErrors.xml");
    Assert.assertTrue(validXml.exists());
  }

  @Test(groups = "files", priority = 1, description = "Tests that the the correct errors are reported by the JUnit xml")
  public void JUnitContainsCorrectErrors()
      throws MojoExecutionException, MojoFailureException, IOException, XMLStreamException {
    Amt2FlatFile amt2FlatFile = new Amt2FlatFile();
    amt2FlatFile.setInputZipFilePath("target/test-classes/rf2-fails-flat-file-generation-1.0.zip");
    amt2FlatFile.setOutputFilePath(outFilePath);
    amt2FlatFile.setJunitFilePath("target/JUnitContainsCorrectErrors.xml");
    amt2FlatFile.execute();
    List<JUnitTestCase> testCases = JUnitFileParser.parse(
        new File("target/JUnitContainsCorrectErrors.xml"));
    List<String> fails = new ArrayList<>();
    testCases.forEach(testCase -> {
      List<JUnitFailure> failures = testCase.getFailures();
      fails.addAll(
          failures.stream()
              .map(fail -> fail.getValue())
              .collect(Collectors.toList()));
    });
    Assert.assertTrue(fails.stream().anyMatch(fail -> fail.contains("1212261000168108")));
    Assert.assertTrue(fails.stream().anyMatch(fail -> fail.contains("1209811000168100")));
    Assert.assertEquals(fails.size(), 10);
  }


  @Test(groups = "files", priority = 1, description = "An exception is thrown when the provided input zip file doesn't exist", expectedExceptions = IllegalArgumentException.class)
  public void fileDoesNotExistThrowException()
      throws MojoExecutionException, MojoFailureException, IOException {

    Amt2FlatFile amt2FlatFile = new Amt2FlatFile();
    amt2FlatFile.setInputZipFilePath(testResDirectory + "nonExistent.zip");
    amt2FlatFile.setOutputFilePath(outFilePath);
    amt2FlatFile.execute();

  }

  @Test(groups = "files", priority = 1, description = "An exception is thrown when the provided input zip file is missing critical files", expectedExceptions = RuntimeException.class)
  public void fileMissingFromReleaseExceptionThrown()
      throws MojoExecutionException, MojoFailureException, IOException {

    Amt2FlatFile amt2FlatFile = new Amt2FlatFile();
    amt2FlatFile.setInputZipFilePath(testResDirectory + "incomplete.zip");
    amt2FlatFile.setOutputFilePath(outFilePath);
    amt2FlatFile.execute();
  }

  @Test(groups = "parse", priority = 2, description = "The output file should match the expected result. Test line by line, regardless fo order")
  public void outputMatchesExpectedImproved()
      throws MojoExecutionException, MojoFailureException, IOException {

    Amt2FlatFile amt2FlatFile = new Amt2FlatFile();
    String inFile =
        testResDirectory + "NCTS_SCT_RF2_DISTRIBUTION_32506021000036107-20180430-SNAPSHOT.zip";
    amt2FlatFile.setInputZipFilePath(inFile);
    amt2FlatFile.setOutputFilePath(outFilePath);
    String replacementFilePath = "target/test-out/replacement";
    amt2FlatFile.setReplacementsFilePath(replacementFilePath);
    amt2FlatFile.execute();

    checkFile(outFilePath, "expected");
    checkFile(replacementFilePath, "expectedReplacement");
  }

  private void checkFile(String outFilePath, String expected) throws IOException {
    assertDosLineTermination(FileFormat.CSV.getFilePath(outFilePath));
    assertDosLineTermination(FileFormat.TSV.getFilePath(outFilePath));

    List<String> outFileLinesCSV =
        FileUtils.readLines(new File(FileFormat.CSV.getFilePath(outFilePath)), "UTF-8");
    List<String> outFileLinesTSV =
        FileUtils.readLines(new File(FileFormat.TSV.getFilePath(outFilePath)), "UTF-8");

    String expectedFilePath = testResDirectory + expected;
    List<String> expectedLinesCSV = FileUtils
        .readLines(new File(FileFormat.CSV.getFilePath(expectedFilePath)), "UTF-8");
    List<String> expectedLinesTSV = FileUtils
        .readLines(new File(FileFormat.TSV.getFilePath(expectedFilePath)), "UTF-8");
    Collections.sort(outFileLinesCSV);
    Collections.sort(outFileLinesTSV);
    Collections.sort(expectedLinesCSV);
    Collections.sort(expectedLinesTSV);

    checkFiles(outFileLinesCSV, expectedLinesCSV, FileFormat.CSV);
    checkFiles(outFileLinesTSV, expectedLinesTSV, FileFormat.TSV);
  }

  private void checkFiles(List<String> outFileLines, List<String> expectedLines,
      FileFormat format) {
    // Assert and explain TSV differences
    if (!outFileLines.equals(expectedLines)) {
      for (int i = 0; i < Math.min(outFileLines.size(), expectedLines.size()); i++) {
        if (!outFileLines.get(i).equals(expectedLines.get(i))) {
          fail("Difference found in " + format.toString() + " file at line " + (i + 1)
              + ": expected '" + expectedLines.get(i) + "' but was '"
              + outFileLines.get(i) + "'");
        }
      }
      if (outFileLines.size() != expectedLines.size()) {
        fail(format.toString() + " files differ in length: expected " + expectedLines.size()
            + " lines, but was " + outFileLines.size() + " lines.");
      }
    }
  }

  public static void assertDosLineTermination(String filePath) throws IOException {
    String content = new String(Files.readAllBytes(new File(filePath).toPath()), "UTF-8");
    Pattern pattern = Pattern.compile(".*(\r\n|\r|\n)");
    Matcher matcher = pattern.matcher(content);

    int count = 0;
    while (matcher.find()) {
      count++;
      String line = matcher.group();
      org.junit.Assert.assertTrue("Line " + (count) + " of file " + filePath
          + " does not end with DOS line termination: " + line, line.endsWith("\r\n"));
    }
  }
}
