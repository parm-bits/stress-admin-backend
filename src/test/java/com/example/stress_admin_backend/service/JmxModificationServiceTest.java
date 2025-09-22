package com.example.stress_admin_backend.service;

import com.example.stress_admin_backend.model.UseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JmxModificationServiceTest {

    private JmxModificationService jmxModificationService;

    @BeforeEach
    void setUp() {
        jmxModificationService = new JmxModificationService();
    }

    @Test
    void testCsvPathReplacement() throws IOException {
        // Create a test JMX content with Windows CSV path
        String testJmxContent = """
            <?xml version="1.0" encoding="UTF-8"?>
            <jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
              <hashTree>
                <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Test Plan">
                </TestPlan>
                <hashTree>
                  <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="MsgByDid">
                    <intProp name="ThreadGroup.num_threads">1</intProp>
                    <intProp name="ThreadGroup.ramp_time">1</intProp>
                    <longProp name="ThreadGroup.duration">900</longProp>
                  </ThreadGroup>
                  <hashTree>
                    <CSVDataSet guiclass="TestBeanGUI" testclass="CSVDataSet" testname="CSV Data Set Config">
                      <stringProp name="delimiter">,</stringProp>
                      <stringProp name="fileEncoding">UTF-8</stringProp>
                      <stringProp name="filename">C:/Users/ParmjeetYadav/Desktop/stress-admin/SendMsgDid/userst2.csv</stringProp>
                      <boolProp name="ignoreFirstLine">false</boolProp>
                      <boolProp name="quotedData">false</boolProp>
                      <boolProp name="recycle">true</boolProp>
                      <stringProp name="shareMode">shareMode.all</stringProp>
                      <boolProp name="stopThread">false</boolProp>
                      <stringProp name="variableNames">username,password</stringProp>
                    </CSVDataSet>
                  </hashTree>
                </hashTree>
              </hashTree>
            </jmeterTestPlan>
            """;

        // Create a test UseCase with CSV path
        UseCase testUseCase = UseCase.builder()
                .name("Test Use Case")
                .csvPath("/home/ubuntu/stress-admin-storage/csv/test-uuid_userst2.csv")
                .build();

        // Create a temporary JMX file
        Path tempJmxFile = Files.createTempFile("test", ".jmx");
        Files.write(tempJmxFile, testJmxContent.getBytes());

        try {
            // Test the modification
            String modifiedContent = jmxModificationService.modifyJmxWithConfiguration(
                    tempJmxFile.toString(), testUseCase, 300);

            // Verify that the CSV path was updated
            assertTrue(modifiedContent.contains("/home/ubuntu/stress-admin-storage/csv/test-uuid_userst2.csv"),
                    "CSV path should be updated to server path");
            
            assertFalse(modifiedContent.contains("C:/Users/ParmjeetYadav/Desktop/stress-admin/SendMsgDid/userst2.csv"),
                    "Original Windows CSV path should be replaced");

            // Verify that the duration was NOT updated (should remain original JMX duration)
            assertTrue(modifiedContent.contains("<longProp name=\"ThreadGroup.duration\">900</longProp>"),
                    "Duration should remain original JMX duration (900 seconds) - not overridden");
            
            // Verify that duration was NOT changed to 300
            assertFalse(modifiedContent.contains("<longProp name=\"ThreadGroup.duration\">300</longProp>"),
                    "Duration should NOT be updated to 300 seconds");

            System.out.println("Test passed: CSV path replacement working correctly");
            System.out.println("Modified content snippet:");
            System.out.println(modifiedContent.substring(modifiedContent.indexOf("CSVDataSet"), 
                    modifiedContent.indexOf("CSVDataSet") + 500));

        } finally {
            // Clean up
            Files.deleteIfExists(tempJmxFile);
        }
    }

    @Test
    void testCsvPathReplacementWithDifferentPatterns() throws IOException {
        // Test different CSV path patterns - only Windows paths should be updated
        String[] windowsPaths = {
            "C:/Users/ParmjeetYadav/Desktop/stress-admin/SendMsgDid/userst2.csv",
            "C:\\Users\\ParmjeetYadav\\Desktop\\stress-admin\\SendMsgDid\\userst2.csv"
        };

        String[] serverPaths = {
            "/home/user/test.csv",
            "relative/path/test.csv"
        };

        String expectedServerPath = "/home/ubuntu/stress-admin-storage/csv/test-uuid_userst2.csv";

        // Test Windows paths - these should be updated
        for (String testPath : windowsPaths) {
            String testJmxContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2">
                  <hashTree>
                    <TestPlan testname="Test Plan"></TestPlan>
                    <hashTree>
                      <ThreadGroup testname="Thread Group">
                        <longProp name="ThreadGroup.duration">900</longProp>
                      </ThreadGroup>
                      <hashTree>
                        <CSVDataSet testname="CSV Data Set Config">
                          <stringProp name="filename">%s</stringProp>
                        </CSVDataSet>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """.formatted(testPath);

            UseCase testUseCase = UseCase.builder()
                    .name("Test Use Case")
                    .csvPath("/home/ubuntu/stress-admin-storage/csv/test-uuid_userst2.csv")
                    .build();

            Path tempJmxFile = Files.createTempFile("test", ".jmx");
            Files.write(tempJmxFile, testJmxContent.getBytes());

            try {
                String modifiedContent = jmxModificationService.modifyJmxWithConfiguration(
                        tempJmxFile.toString(), testUseCase, 300);

                assertTrue(modifiedContent.contains(expectedServerPath),
                        "CSV path should be updated for Windows pattern: " + testPath);
                
                assertFalse(modifiedContent.contains(testPath),
                        "Original Windows CSV path should be replaced for pattern: " + testPath);

            } finally {
                Files.deleteIfExists(tempJmxFile);
            }
        }

        // Test server paths - these should be updated to the correct server path
        for (String testPath : serverPaths) {
            String testJmxContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <jmeterTestPlan version="1.2">
                  <hashTree>
                    <TestPlan testname="Test Plan"></TestPlan>
                    <hashTree>
                      <ThreadGroup testname="Thread Group">
                        <longProp name="ThreadGroup.duration">900</longProp>
                      </ThreadGroup>
                      <hashTree>
                        <CSVDataSet testname="CSV Data Set Config">
                          <stringProp name="filename">%s</stringProp>
                        </CSVDataSet>
                      </hashTree>
                    </hashTree>
                  </hashTree>
                </jmeterTestPlan>
                """.formatted(testPath);

            UseCase testUseCase = UseCase.builder()
                    .name("Test Use Case")
                    .csvPath("/home/ubuntu/stress-admin-storage/csv/test-uuid_userst2.csv")
                    .build();

            Path tempJmxFile = Files.createTempFile("test", ".jmx");
            Files.write(tempJmxFile, testJmxContent.getBytes());

            try {
                String modifiedContent = jmxModificationService.modifyJmxWithConfiguration(
                        tempJmxFile.toString(), testUseCase, 300);

                // All CSV paths should be updated to the expected server path
                assertTrue(modifiedContent.contains(expectedServerPath),
                        "CSV path should be updated for pattern: " + testPath);

            } finally {
                Files.deleteIfExists(tempJmxFile);
            }
        }
    }
}
