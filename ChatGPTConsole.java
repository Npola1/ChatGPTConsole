import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class ChatGPTConsole {
    private static final String API_URL = "https://api.openai.com/v1/responses";
    private static final String DEFAULT_MODEL = "gpt-5-mini";

    public static void main(String[] args) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Set the OPENAI_API_KEY environment variable first.");
            return;
        }

        String model = System.getenv("OPENAI_MODEL");
        if (model == null || model.isBlank()) {
            model = DEFAULT_MODEL;
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        String previousResponseId = null;
        System.out.println("ChatGPT console (model: " + model + ")");
        System.out.println("Enter a prompt, or type 'exit' to quit.\n");

        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            while (true) {
                System.out.print("You: ");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String prompt = scanner.nextLine();
                if (prompt.equalsIgnoreCase("exit") || prompt.equalsIgnoreCase("quit")) {
                    break;
                }
                if (prompt.isBlank()) {
                    continue;
                }

                String body = "{\"model\":" + jsonQuote(model)
                        + ",\"input\":" + jsonQuote(prompt)
                        + ",\"store\":true"
                        + (previousResponseId == null ? ""
                                : ",\"previous_response_id\":" + jsonQuote(previousResponseId))
                        + "}";

                HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
                        .timeout(Duration.ofSeconds(120))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

                try {
                    HttpResponse<String> response = client.send(
                            request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        System.err.println("API error (HTTP " + response.statusCode() + "):");
                        System.err.println(response.body() + "\n");
                        continue;
                    }

                    Map<?, ?> result = (Map<?, ?>) new JsonParser(response.body()).parse();
                    String answer = outputText(result);
                    if (answer.isBlank()) {
                        System.out.println("ChatGPT: No text returned.\n");
                        continue;
                    }

                    Object responseId = result.get("id");
                    if (responseId instanceof String) {
                        previousResponseId = (String) responseId;
                    }
                    System.out.println("ChatGPT: " + answer + "\n");
                } catch (IOException e) {
                    System.err.println("Network error: " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Request interrupted.");
                    break;
                } catch (RuntimeException e) {
                    System.err.println("Could not read the API response: " + e.getMessage());
                }
            }
        }
    }

    private static String outputText(Map<?, ?> response) {
        Object output = response.get("output");
        if (!(output instanceof List<?>)) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (Object item : (List<?>) output) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            Object content = ((Map<?, ?>) item).get("content");
            if (!(content instanceof List<?>)) {
                continue;
            }
            for (Object part : (List<?>) content) {
                if (part instanceof Map<?, ?>) {
                    Map<?, ?> obj = (Map<?, ?>) part;
                    if ("output_text".equals(obj.get("type")) && obj.get("text") instanceof String) {
                        text.append((String) obj.get("text"));
                    }
                }
            }
        }
        return text.toString();
    }

    private static String jsonQuote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': result.append("\\\""); break;
                case '\\': result.append("\\\\"); break;
                case '\n': result.append("\\n"); break;
                case '\r': result.append("\\r"); break;
                case '\t': result.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        result.append(String.format("\\u%04x", (int) c));
                    } else {
                        result.append(c);
                    }
            }
        }
        return result.append('"').toString();
    }

    // Minimal JSON parser to avoid requiring Maven or a JSON library.
    private static final class JsonParser {
        private final String json;
        private int pos;

        JsonParser(String json) {
            this.json = json;
        }

        Object parse() {
            Object parsed = value();
            whitespace();
            if (pos != json.length()) {
                throw new IllegalArgumentException("Unexpected text at position " + pos);
            }
            return parsed;
        }

        private Object value() {
            whitespace();
            if (pos >= json.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            char c = json.charAt(pos);
            if (c == '"') {
                return string();
            }
            if (c == '{') {
                pos++;
                Map<String, Object> object = new LinkedHashMap<>();
                whitespace();
                if (consume('}')) return object;
                do {
                    whitespace();
                    String key = string();
                    require(':');
                    object.put(key, value());
                    whitespace();
                    if (consume('}')) return object;
                    require(',');
                } while (true);
            }
            if (c == '[') {
                pos++;
                List<Object> array = new ArrayList<>();
                whitespace();
                if (consume(']')) return array;
                do {
                    array.add(value());
                    whitespace();
                    if (consume(']')) return array;
                    require(',');
                } while (true);
            }
            int start = pos;
            while (pos < json.length() && ",]} \n\r\t".indexOf(json.charAt(pos)) == -1) {
                pos++;
            }
            if (start == pos) throw new IllegalArgumentException("Invalid JSON value");
            String literal = json.substring(start, pos);
            if (literal.equals("null")) return null;
            if (literal.equals("true")) return Boolean.TRUE;
            if (literal.equals("false")) return Boolean.FALSE;
            return literal; // Numbers are not used by this program.
        }

        private String string() {
            require('"');
            StringBuilder result = new StringBuilder();
            while (pos < json.length()) {
                char c = json.charAt(pos++);
                if (c == '"') return result.toString();
                if (c != '\\') {
                    result.append(c);
                    continue;
                }
                if (pos >= json.length()) throw new IllegalArgumentException("Invalid escape");
                char escaped = json.charAt(pos++);
                switch (escaped) {
                    case '"': result.append('"'); break;
                    case '\\': result.append('\\'); break;
                    case '/': result.append('/'); break;
                    case 'b': result.append('\b'); break;
                    case 'f': result.append('\f'); break;
                    case 'n': result.append('\n'); break;
                    case 'r': result.append('\r'); break;
                    case 't': result.append('\t'); break;
                    case 'u':
                        if (pos + 4 > json.length()) throw new IllegalArgumentException("Invalid Unicode escape");
                        result.append((char) Integer.parseInt(json.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default: throw new IllegalArgumentException("Invalid escape character");
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private void whitespace() {
            while (pos < json.length() && " \t\r\n".indexOf(json.charAt(pos)) != -1) pos++;
        }

        private boolean consume(char c) {
            whitespace();
            if (pos < json.length() && json.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        private void require(char c) {
            if (!consume(c)) throw new IllegalArgumentException("Expected '" + c + "' at position " + pos);
        }
    }
}
