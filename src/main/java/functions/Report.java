package functions;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class Report implements HttpFunction {
  private static final String MS_FEEDBACK_BASE_URL_ENV = "MS_FEEDBACK_BASE_URL";
  private static final String NOTIFICATION_BASE_URL_ENV = "NOTIFICATION_BASE_URL";
  private static final String REPORT_EMAIL_ENV = "REPORT_EMAIL";
  
  private static final String DEFAULT_MS_FEEDBACK_BASE_URL = "http://localhost:9084";
  private static final String DEFAULT_NOTIFICATION_BASE_URL = "https://notification-780675609467.europe-west1.run.app";
  
  private final HttpClient httpClient;
  private final Gson gson;

  public Report() {
    this.httpClient = HttpClient.newHttpClient();
    this.gson = new Gson();
  }

  @Override
  public void service(HttpRequest request, HttpResponse response)
      throws IOException {
    BufferedWriter writer = response.getWriter();
    
    try {
      // Read environment variables
      String msFeedbackBaseUrl = getEnvVar(MS_FEEDBACK_BASE_URL_ENV, DEFAULT_MS_FEEDBACK_BASE_URL);
      String notificationBaseUrl = getEnvVar(NOTIFICATION_BASE_URL_ENV, DEFAULT_NOTIFICATION_BASE_URL);
      String reportEmail = getEnvVar(REPORT_EMAIL_ENV, null);
      
      if (reportEmail == null || reportEmail.isEmpty()) {
        response.setStatusCode(500);
        writer.write("{\"error\": \"REPORT_EMAIL environment variable is not set\"}");
        return;
      }

      // Extract Authorization header from incoming request
      Optional<String> authHeader = request.getFirstHeader("Authorization");
      if (authHeader.isEmpty()) {
        response.setStatusCode(401);
        writer.write("{\"error\": \"Authorization header is missing\"}");
        return;
      }

      // Get current date in ISO 8601 format
      String currentDate = Instant.now().atZone(java.time.ZoneId.of("UTC"))
          .format(DateTimeFormatter.ISO_INSTANT);

      // Make HTTP POST request to ms-feedback service
      String feedbackUrl = msFeedbackBaseUrl + "/ms-feedback/v1/feedback/report";
      JsonObject requestBody = new JsonObject();
      requestBody.addProperty("date", currentDate);
      
      java.net.http.HttpRequest feedbackRequest = java.net.http.HttpRequest.newBuilder()
          .uri(URI.create(feedbackUrl))
          .header("accept", "application/json")
          .header("Content-Type", "application/json")
          .header("Authorization", authHeader.get())
          .POST(BodyPublishers.ofString(gson.toJson(requestBody)))
          .build();

      java.net.http.HttpResponse<String> feedbackResponse = httpClient.send(
          feedbackRequest, BodyHandlers.ofString());

      if (feedbackResponse.statusCode() != 200) {
        response.setStatusCode(feedbackResponse.statusCode());
        writer.write("{\"error\": \"Failed to fetch feedback report: " + 
            feedbackResponse.statusCode() + "\"}");
        return;
      }

      // Parse JSON response
      JsonObject feedbackData = JsonParser.parseString(feedbackResponse.body()).getAsJsonObject();
      
      // Format response into simple text
      String formattedReport = formatReport(feedbackData);

      // Make HTTP POST request to notification service
      JsonObject notificationBody = new JsonObject();
      notificationBody.addProperty("to", reportEmail);
      notificationBody.addProperty("subject", "Feedback Report");
      notificationBody.addProperty("body", formattedReport);

      java.net.http.HttpRequest notificationRequest = java.net.http.HttpRequest.newBuilder()
          .uri(URI.create(notificationBaseUrl))
          .header("Content-Type", "application/json")
          .POST(BodyPublishers.ofString(gson.toJson(notificationBody)))
          .build();

      java.net.http.HttpResponse<String> notificationResponse = httpClient.send(
          notificationRequest, BodyHandlers.ofString());

      if (notificationResponse.statusCode() < 200 || notificationResponse.statusCode() >= 300) {
        response.setStatusCode(notificationResponse.statusCode());
        writer.write("{\"error\": \"Failed to send notification: " + 
            notificationResponse.statusCode() + "\"}");
        return;
      }

      // Return success response
      response.setStatusCode(200);
      response.setContentType("application/json");
      writer.write("{\"message\": \"Report generated and sent successfully\"}");

    } catch (Exception e) {
      response.setStatusCode(500);
      writer.write("{\"error\": \"" + e.getMessage() + "\"}");
    }
  }

  private String formatReport(JsonObject feedbackData) {
    StringBuilder report = new StringBuilder();
    report.append("Feedback Report\n");
    report.append("===============\n\n");

    // Format evaluations per day
    if (feedbackData.has("evaluationsPerDay")) {
      JsonArray evaluationsPerDay = feedbackData.getAsJsonArray("evaluationsPerDay");
      report.append("Evaluations per day:\n");
      for (int i = 0; i < evaluationsPerDay.size(); i++) {
        JsonObject dayData = evaluationsPerDay.get(i).getAsJsonObject();
        String day = dayData.get("day").getAsString();
        int quantity = dayData.get("quantity").getAsInt();
        report.append("  ").append(day).append(": ").append(quantity).append("\n");
      }
      report.append("\n");
    }

    // Format evaluations per urgency
    if (feedbackData.has("evaluationsPerUrgency")) {
      JsonArray evaluationsPerUrgency = feedbackData.getAsJsonArray("evaluationsPerUrgency");
      report.append("Evaluations per urgency:\n");
      for (int i = 0; i < evaluationsPerUrgency.size(); i++) {
        JsonObject urgencyData = evaluationsPerUrgency.get(i).getAsJsonObject();
        String urgency = urgencyData.get("urgency").getAsString();
        int quantity = urgencyData.get("quantity").getAsInt();
        report.append("  ").append(urgency).append(": ").append(quantity).append("\n");
      }
    }

    return report.toString();
  }

  private String getEnvVar(String name, String defaultValue) {
    String value = System.getenv(name);
    return value != null && !value.isEmpty() ? value : defaultValue;
  }
}

