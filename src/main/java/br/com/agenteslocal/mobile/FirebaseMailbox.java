package br.com.agenteslocal.mobile;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Stores only opaque encrypted envelopes in Firestore; larger payloads use small child documents. */
public final class FirebaseMailbox {
    private static final int CHUNK_CHARS = 400_000;
    private static final int INLINE_CHARS = 750_000;
    private static final int MAX_CHUNKS = 50;
    public record Envelope(String data, int partsCount) { }
    private final FirebaseRemoteClient client;
    private final String root;

    public FirebaseMailbox(FirebaseRemoteClient client) {
        this.client = client;
        this.root = "mailboxes/" + client.ownerUid();
    }

    public List<String> listIds(String box) throws IOException, InterruptedException {
        String collection = collection(box);
        List<String> result = new ArrayList<>();
        for (JsonObject document : client.listDocuments(collection)) {
            String name = document.get("name").getAsString();
            String id = name.substring(name.lastIndexOf('/') + 1);
            if (id.matches("[A-Za-z0-9_-]{1,80}")) result.add(id);
        }
        return result;
    }

    public void write(String box, String id, String envelope) throws IOException, InterruptedException {
        write(box, id, envelope, false);
    }

    public void write(String box, String id, String envelope, boolean preferInline)
            throws IOException, InterruptedException {
        String collection = collection(box);
        validateId(id);
        if (envelope == null || envelope.isEmpty() || envelope.length() > CHUNK_CHARS * MAX_CHUNKS)
            throw new IOException("Mensagem remota grande demais.");
        if (preferInline && envelope.length() <= INLINE_CHARS) {
            JsonObject fields = new JsonObject();
            fields.add("data", stringField(envelope));
            JsonObject body = new JsonObject(); body.add("fields", fields);
            client.createDocument(collection, id, body);
            return;
        }
        int count = (envelope.length() + CHUNK_CHARS - 1) / CHUNK_CHARS;
        String parts = collection + "/" + id + "/parts";
        int created = 0;
        try {
            for (int index = 0; index < count; index++) {
                String fragment = envelope.substring(index * CHUNK_CHARS,
                        Math.min(envelope.length(), (index + 1) * CHUNK_CHARS));
                JsonObject fields = new JsonObject();
                fields.add("data", stringField(fragment));
                JsonObject body = new JsonObject(); body.add("fields", fields);
                client.createDocument(parts, partId(index), body);
                created++;
            }
            JsonObject fields = new JsonObject();
            JsonObject size = new JsonObject(); size.addProperty("integerValue", Integer.toString(count));
            fields.add("partsCount", size);
            JsonObject body = new JsonObject(); body.add("fields", fields);
            client.createDocument(collection, id, body);
        } catch (IOException | InterruptedException error) {
            for (int index = 0; index < created; index++) {
                try { client.deleteDocument(parts + "/" + partId(index)); }
                catch (IOException | InterruptedException ignored) { }
            }
            throw error;
        }
    }

    public Envelope read(String box, String id) throws IOException, InterruptedException {
        String collection = collection(box);
        validateId(id);
        JsonObject manifest = client.getDocument(collection + "/" + id);
        if (manifest == null) return null;
        JsonObject fields = manifest.getAsJsonObject("fields");
        if (fields != null && fields.has("data"))
            return new Envelope(fields.getAsJsonObject("data").get("stringValue").getAsString(), 0);
        int count = count(manifest);
        StringBuilder envelope = new StringBuilder(Math.min(count * CHUNK_CHARS, 1_000_000));
        for (int index = 0; index < count; index++) {
            JsonObject part = client.getDocument(collection + "/" + id + "/parts/" + partId(index));
            if (part == null) throw new IOException("Parte da mensagem remota nao encontrada.");
            envelope.append(part.getAsJsonObject("fields").getAsJsonObject("data").get("stringValue").getAsString());
        }
        return new Envelope(envelope.toString(), count);
    }

    public boolean exists(String box, String id) throws IOException, InterruptedException {
        String collection = collection(box);
        validateId(id);
        return client.getDocument(collection + "/" + id) != null;
    }

    public void delete(String box, String id) throws IOException, InterruptedException {
        String collection = collection(box);
        validateId(id);
        JsonObject manifest = client.getDocument(collection + "/" + id);
        if (manifest == null) return;
        JsonObject fields = manifest.getAsJsonObject("fields");
        if (fields != null && fields.has("data")) {
            client.deleteDocument(collection + "/" + id);
            return;
        }
        int count = count(manifest);
        delete(box, id, count);
    }

    public void delete(String box, String id, int partsCount) throws IOException, InterruptedException {
        validateId(id);
        if (partsCount < 0 || partsCount > MAX_CHUNKS) throw new IOException("Manifesto remoto invalido.");
        String collection = collection(box);
        client.deleteDocument(collection + "/" + id);
        for (int index = 0; index < partsCount; index++)
            client.deleteDocument(collection + "/" + id + "/parts/" + partId(index));
    }

    private static JsonObject stringField(String value) {
        JsonObject field = new JsonObject(); field.addProperty("stringValue", value);
        return field;
    }

    private static int count(JsonObject manifest) throws IOException {
        try {
            int value = manifest.getAsJsonObject("fields").getAsJsonObject("partsCount")
                    .get("integerValue").getAsInt();
            if (value < 1 || value > MAX_CHUNKS) throw new NumberFormatException();
            return value;
        } catch (RuntimeException error) { throw new IOException("Manifesto remoto invalido.", error); }
    }

    private String collection(String box) {
        if (!"requests".equals(box) && !"responses".equals(box))
            throw new IllegalArgumentException("Caixa postal desconhecida.");
        return root + "/" + box;
    }

    private static String partId(int index) { return String.format("%03d", index); }

    private static void validateId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,80}"))
            throw new IllegalArgumentException("ID remoto invalido.");
    }
}
