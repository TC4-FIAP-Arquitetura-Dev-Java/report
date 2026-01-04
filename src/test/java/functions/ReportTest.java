package functions;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class ReportTest {

  @Mock private HttpRequest request;
  @Mock private HttpResponse response;
  @Mock private BufferedWriter writer;
  @Mock private HttpClient httpClient;
  
  private Report report;
  private StringWriter stringWriter;

  @BeforeEach
  void setUp() throws IOException {
    stringWriter = new StringWriter();
    lenient().when(response.getWriter()).thenReturn(writer);
    report = new Report(httpClient);
  }

  @Test
  void testMissingMsFeedbackBaseUrl() throws IOException {
    report.service(request, response);
  }

  @Test
  void testMissingNotificationBaseUrl() throws IOException {
    report.service(request, response);
  }

  @Test
  void testMissingReportEmail() throws IOException {
    report.service(request, response);
  }

  @Test
  void testSuccessfulFlow() throws Exception {
    TestableReport testReport = new TestableReport(httpClient);
    testReport.setEnvVar("MS_FEEDBACK_BASE_URL", "http://localhost:9084");
    testReport.setEnvVar("NOTIFICATION_BASE_URL", "http://localhost:8080");
    testReport.setEnvVar("REPORT_EMAIL", "test@example.com");

    JsonObject feedbackData = new JsonObject();
    JsonArray evaluationsPerDay = new JsonArray();
    JsonObject day1 = new JsonObject();
    day1.addProperty("day", "2026-01-03");
    day1.addProperty("quantity", 6);
    evaluationsPerDay.add(day1);
    feedbackData.add("evaluationsPerDay", evaluationsPerDay);

    JsonArray evaluationsPerUrgency = new JsonArray();
    JsonObject urgency1 = new JsonObject();
    urgency1.addProperty("urgency", "LOW");
    urgency1.addProperty("quantity", 4);
    evaluationsPerUrgency.add(urgency1);
    feedbackData.add("evaluationsPerUrgency", evaluationsPerUrgency);

    @SuppressWarnings("unchecked")
    java.net.http.HttpResponse<String> feedbackResponse = mock(java.net.http.HttpResponse.class);
    lenient().when(feedbackResponse.statusCode()).thenReturn(200);
    lenient().when(feedbackResponse.body()).thenReturn(feedbackData.toString());

    @SuppressWarnings("unchecked")
    java.net.http.HttpResponse<String> notificationResponse = mock(java.net.http.HttpResponse.class);
    lenient().when(notificationResponse.statusCode()).thenReturn(200);
    lenient().when(notificationResponse.body()).thenReturn("{\"success\": true}");

    AtomicInteger callCount = new AtomicInteger(0);
    lenient().when(httpClient.send(any(), any())).thenAnswer((Answer<java.net.http.HttpResponse<String>>) invocation -> {
      int count = callCount.getAndIncrement();
      return count == 0 ? feedbackResponse : notificationResponse;
    });

    testReport.service(request, response);
  }

  private static class TestableReport extends Report {
    private java.util.Map<String, String> envVars = new java.util.HashMap<>();

    TestableReport(HttpClient httpClient) {
      super(httpClient);
    }

    void setEnvVar(String name, String value) {
      envVars.put(name, value);
    }

    @Override
    String getEnvVar(String name) {
      return envVars.getOrDefault(name, System.getenv(name));
    }
  }

  @Test
  void testFeedbackServiceError() throws Exception {
    TestableReport testReport = new TestableReport(httpClient);
    testReport.setEnvVar("MS_FEEDBACK_BASE_URL", "http://localhost:9084");
    testReport.setEnvVar("NOTIFICATION_BASE_URL", "http://localhost:8080");
    testReport.setEnvVar("REPORT_EMAIL", "test@example.com");

    @SuppressWarnings("unchecked")
    java.net.http.HttpResponse<String> feedbackResponse = mock(java.net.http.HttpResponse.class);
    lenient().when(feedbackResponse.statusCode()).thenReturn(500);
    lenient().when(feedbackResponse.body()).thenReturn("Internal Server Error");

    lenient().when(httpClient.send(any(), any())).thenAnswer((Answer<java.net.http.HttpResponse<String>>) invocation -> feedbackResponse);

    testReport.service(request, response);
  }

  @Test
  void testNotificationServiceError() throws Exception {
    TestableReport testReport = new TestableReport(httpClient);
    testReport.setEnvVar("MS_FEEDBACK_BASE_URL", "http://localhost:9084");
    testReport.setEnvVar("NOTIFICATION_BASE_URL", "http://localhost:8080");
    testReport.setEnvVar("REPORT_EMAIL", "test@example.com");

    JsonObject feedbackData = new JsonObject();
    JsonArray evaluationsPerDay = new JsonArray();
    feedbackData.add("evaluationsPerDay", evaluationsPerDay);

    @SuppressWarnings("unchecked")
    java.net.http.HttpResponse<String> feedbackResponse = mock(java.net.http.HttpResponse.class);
    when(feedbackResponse.statusCode()).thenReturn(200);
    when(feedbackResponse.body()).thenReturn(feedbackData.toString());

    @SuppressWarnings("unchecked")
    java.net.http.HttpResponse<String> notificationResponse = mock(java.net.http.HttpResponse.class);
    when(notificationResponse.statusCode()).thenReturn(500);

    AtomicInteger callCount = new AtomicInteger(0);
    when(httpClient.send(any(), any())).thenAnswer((Answer<java.net.http.HttpResponse<String>>) invocation -> {
      int count = callCount.getAndIncrement();
      return count == 0 ? feedbackResponse : notificationResponse;
    });

    testReport.service(request, response);
  }

  @Test
  void testHttpClientException() throws Exception {
    TestableReport testReport = new TestableReport(httpClient);
    testReport.setEnvVar("MS_FEEDBACK_BASE_URL", "http://localhost:9084");
    testReport.setEnvVar("NOTIFICATION_BASE_URL", "http://localhost:8080");
    testReport.setEnvVar("REPORT_EMAIL", "test@example.com");

    when(httpClient.send(any(), any())).thenThrow(new IOException("Network error"));

    testReport.service(request, response);
  }

  @Test
  void testMissingMsFeedbackBaseUrlWithTestableReport() throws Exception {
    TestableReport testReport = new TestableReport(httpClient);
    testReport.setEnvVar("MS_FEEDBACK_BASE_URL", "");
    testReport.setEnvVar("NOTIFICATION_BASE_URL", "http://localhost:8080");
    testReport.setEnvVar("REPORT_EMAIL", "test@example.com");

    testReport.service(request, response);
  }

  @Test
  void testMissingNotificationBaseUrlWithTestableReport() throws Exception {
    TestableReport testReport = new TestableReport(httpClient);
    testReport.setEnvVar("MS_FEEDBACK_BASE_URL", "http://localhost:9084");
    testReport.setEnvVar("NOTIFICATION_BASE_URL", "");
    testReport.setEnvVar("REPORT_EMAIL", "test@example.com");

    testReport.service(request, response);
  }

  @Test
  void testMissingReportEmailWithTestableReport() throws Exception {
    TestableReport testReport = new TestableReport(httpClient);
    testReport.setEnvVar("MS_FEEDBACK_BASE_URL", "http://localhost:9084");
    testReport.setEnvVar("NOTIFICATION_BASE_URL", "http://localhost:8080");
    testReport.setEnvVar("REPORT_EMAIL", "");

    testReport.service(request, response);
  }

  @Test
  void testFormatReportWithBothArrays() {
    JsonObject feedbackData = new JsonObject();
    
    JsonArray evaluationsPerDay = new JsonArray();
    JsonObject day1 = new JsonObject();
    day1.addProperty("day", "2026-01-03");
    day1.addProperty("quantity", 6);
    JsonObject day2 = new JsonObject();
    day2.addProperty("day", "2026-01-02");
    day2.addProperty("quantity", 2);
    evaluationsPerDay.add(day1);
    evaluationsPerDay.add(day2);
    feedbackData.add("evaluationsPerDay", evaluationsPerDay);

    JsonArray evaluationsPerUrgency = new JsonArray();
    JsonObject urgency1 = new JsonObject();
    urgency1.addProperty("urgency", "LOW");
    urgency1.addProperty("quantity", 4);
    JsonObject urgency2 = new JsonObject();
    urgency2.addProperty("urgency", "URGENT");
    urgency2.addProperty("quantity", 5);
    evaluationsPerUrgency.add(urgency1);
    evaluationsPerUrgency.add(urgency2);
    feedbackData.add("evaluationsPerUrgency", evaluationsPerUrgency);

    String result = report.formatReport(feedbackData);

    assertThat(result).contains("Feedback Report");
    assertThat(result).contains("Evaluations per day:");
    assertThat(result).contains("2026-01-03: 6");
    assertThat(result).contains("2026-01-02: 2");
    assertThat(result).contains("Evaluations per urgency:");
    assertThat(result).contains("LOW: 4");
    assertThat(result).contains("URGENT: 5");
  }

  @Test
  void testFormatReportWithOnlyEvaluationsPerDay() {
    JsonObject feedbackData = new JsonObject();
    
    JsonArray evaluationsPerDay = new JsonArray();
    JsonObject day1 = new JsonObject();
    day1.addProperty("day", "2026-01-01");
    day1.addProperty("quantity", 1);
    evaluationsPerDay.add(day1);
    feedbackData.add("evaluationsPerDay", evaluationsPerDay);

    String result = report.formatReport(feedbackData);

    assertThat(result).contains("Feedback Report");
    assertThat(result).contains("Evaluations per day:");
    assertThat(result).contains("2026-01-01: 1");
    assertThat(result).doesNotContain("Evaluations per urgency:");
  }

  @Test
  void testFormatReportWithOnlyEvaluationsPerUrgency() {
    JsonObject feedbackData = new JsonObject();
    
    JsonArray evaluationsPerUrgency = new JsonArray();
    JsonObject urgency1 = new JsonObject();
    urgency1.addProperty("urgency", "MEDIUM");
    urgency1.addProperty("quantity", 3);
    evaluationsPerUrgency.add(urgency1);
    feedbackData.add("evaluationsPerUrgency", evaluationsPerUrgency);

    String result = report.formatReport(feedbackData);

    assertThat(result).contains("Feedback Report");
    assertThat(result).contains("Evaluations per urgency:");
    assertThat(result).contains("MEDIUM: 3");
    assertThat(result).doesNotContain("Evaluations per day:");
  }

  @Test
  void testFormatReportWithEmptyData() {
    JsonObject feedbackData = new JsonObject();

    String result = report.formatReport(feedbackData);

    assertThat(result).contains("Feedback Report");
    assertThat(result).doesNotContain("Evaluations per day:");
    assertThat(result).doesNotContain("Evaluations per urgency:");
  }

  @Test
  void testGetEnvVar() {
    String testVar = "TEST_VAR_" + System.currentTimeMillis();
    String result = report.getEnvVar(testVar);
    assertThat(result).isNull();
  }
}
