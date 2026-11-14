package br.com.agenteslocal.mobile;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Firebase Auth and Firestore REST client using a normal user ID token, so Security Rules apply. */
public final class FirebaseRemoteClient {
    public record Config(String apiKey, String projectId, String ownerUid) {
        public Config {
            if (apiKey == null || !apiKey.matches("[A-Za-z0-9_-]{20,}"))
                throw new IllegalArgumentException("Chave publica do Firebase invalida.");
            if (projectId == null || !projectId.matches("[a-z][a-z0-9-]{4,62}"))
                throw new IllegalArgumentException("ID do projeto Firebase invalido.");
            if (ownerUid == null || !ownerUid.matches("[A-Za-z0-9]{10,128}"))
                throw new IllegalArgumentException("UID do usuario Firebase invalido.");
        }
    }

    private static final String AUTH_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=";
    private static final String REFRESH_URL = "https://securetoken.googleapis.com/v1/token?key=";
    private static final String FIRESTORE_URL = "https://firestore.googleapis.com/v1/projects/";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Gson gson = new Gson();
    private final Config config;
    private String idToken = "";
    private String refreshToken = "";
    private Instant expiresAt = Instant.EPOCH;

    public FirebaseRemoteClient(Config config) { this.config = config; }

    public String ownerUid() { return config.ownerUid(); }

    public synchronized void signIn(String email, String password) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        body.addProperty("returnSecureToken", true);
        JsonObject result = request("POST", AUTH_URL + encode(config.apiKey()), gson.toJson(body), null, false);
        if (!config.ownerUid().equals(string(result, "localId")))
            throw new IOException("Esta conta nao e o usuario autorizado do Maestro.");
        idToken = string(result, "idToken");
        refreshToken = string(result, "refreshToken");
        expiresAt = Instant.now().plusSeconds(Math.max(1, number(result, "expiresIn") - 90));
    }

    public synchronized void restore(String savedRefreshToken) throws IOException, InterruptedException {
        refreshToken = savedRefreshToken;
        idToken = "";
        expiresAt = Instant.EPOCH;
        refresh();
    }

    public synchronized String refreshToken() { return refreshToken; }

    public JsonObject getDocument(String relativePath) throws IOException, InterruptedException {
        return request("GET", firestoreUrl(relativePath), null, bearer(), true);
    }

    public List<JsonObject> listDocuments(String collectionPath) throws IOException, InterruptedException {
        List<JsonObject> result = new ArrayList<>();
        String page = "";
        do {
            String url = firestoreUrl(collectionPath) + "?pageSize=100&mask.fieldPaths=partsCount"
                    + (page.isEmpty() ? "" : "&pageToken=" + encode(page));
            JsonObject batch = request("GET", url, null, bearer(), true);
            if (batch == null) break;
            JsonArray documents = batch.has("documents") ? batch.getAsJsonArray("documents") : new JsonArray();
            documents.forEach(item -> result.add(item.getAsJsonObject()));
            page = string(batch, "nextPageToken");
            if (result.size() > 1000) throw new IOException("Fila remota grande demais para processar.");
        } while (!page.isEmpty());
        return result;
    }

    public JsonObject createDocument(String collectionPath, String documentId, JsonObject body)
            throws IOException, InterruptedException {
        String url = firestoreUrl(collectionPath) + "?documentId=" + encode(documentId)
                + "&mask.fieldPaths=partsCount";
        return request("POST", url, gson.toJson(body), bearer(), false);
    }

    public void deleteDocument(String relativePath) throws IOException, InterruptedException {
        request("DELETE", firestoreUrl(relativePath), null, bearer(), true);
    }

    private synchronized String bearer() throws IOException, InterruptedException {
        if (refreshToken.isBlank()) throw new IOException("Entre novamente na conta Firebase.");
        if (idToken.isBlank() || Instant.now().isAfter(expiresAt)) refresh();
        return "Bearer " + idToken;
    }

    private void refresh() throws IOException, InterruptedException {
        String body = "grant_type=refresh_token&refresh_token=" + encode(refreshToken);
        JsonObject result = request("POST", REFRESH_URL + encode(config.apiKey()), body, null, false);
        if (!config.ownerUid().equals(string(result, "user_id")))
            throw new IOException("A sessao Firebase pertence a outro usuario.");
        idToken = string(result, "id_token");
        refreshToken = string(result, "refresh_token");
        expiresAt = Instant.now().plusSeconds(Math.max(1, number(result, "expires_in") - 90));
    }

    private String firestoreUrl(String relativePath) {
        if (!relativePath.matches("[A-Za-z0-9_/-]+") || relativePath.contains(".."))
            throw new IllegalArgumentException("Caminho Firebase invalido.");
        return FIRESTORE_URL + encode(config.projectId()) + "/databases/(default)/documents/" + relativePath;
    }

    private JsonObject request(String method, String url, String body, String authorization, boolean allowNotFound)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(25));
        if (authorization != null) builder.header("Authorization", authorization);
        if (body != null) builder.header("Content-Type", url.startsWith(REFRESH_URL)
                ? "application/x-www-form-urlencoded" : "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (allowNotFound && response.statusCode() == 404) return null;
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("Firebase respondeu com HTTP " + response.statusCode() + ".");
        return response.body().isBlank() ? new JsonObject() : JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static String string(JsonObject object, String field) {
        return object.has(field) ? object.get(field).getAsString() : "";
    }

    private static long number(JsonObject object, String field) {
        try { return Long.parseLong(string(object, field)); }
        catch (NumberFormatException error) { return 0; }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
