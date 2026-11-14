package br.com.agenteslocal.knowledge;

import br.com.agenteslocal.model.WorkspaceData.KnowledgeFile;
import br.com.agenteslocal.model.WorkspaceData.Project;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class KnowledgeService {
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "java", "kt", "js", "ts", "json", "xml", "yaml", "yml",
            "sql", "csv", "html", "css", "py", "properties", "log", "gradle", "sh", "ps1");
    private static final Set<String> STOPWORDS = Set.of(
            "para", "com", "como", "que", "uma", "uns", "das", "dos", "esse", "essa", "isto",
            "sao", "por", "sobre", "qual", "quais", "the", "and", "what", "from", "this", "that");
    private static final int MAX_SOURCE_BYTES = 25_000_000;
    private static final int MAX_TEXT_CHARS = 2_000_000;

    public record Hit(String name, String text, int score, int chunkNumber) { }

    public boolean canIndex(Path file) {
        String extension = extension(file);
        return TEXT_EXTENSIONS.contains(extension) || extension.equals("pdf") || extension.equals("docx");
    }

    public void index(KnowledgeFile file) throws IOException {
        Path source = Path.of(file.storedPath);
        if (Files.size(source) > MAX_SOURCE_BYTES) throw new IOException("O arquivo ultrapassa 25 MB.");
        String text = extract(source).trim();
        if (text.isBlank()) throw new IOException("Nenhum texto extraível. PDFs digitalizados precisam de OCR, ainda indisponível.");
        if (text.length() > MAX_TEXT_CHARS) text = text.substring(0, MAX_TEXT_CHARS);
        Path indexed = source.resolveSibling(source.getFileName() + ".index.txt");
        Files.writeString(indexed, text, StandardCharsets.UTF_8);
        file.indexedPath = indexed.toString();
        file.chunkCount = chunks(text).size();
        file.status = "Indexado";
        file.error = "";
    }

    public List<Hit> search(Project project, String query, String agentId) {
        if (agentId != null && !agentId.isBlank() && project.agent(agentId) != null
                && !project.agent(agentId).canUseKnowledge) return List.of();
        Set<String> terms = terms(query);
        if (terms.isEmpty()) return List.of();
        List<Hit> hits = new ArrayList<>();
        for (KnowledgeFile file : project.knowledge) {
            if (!"Indexado".equals(file.status)) continue;
            Path path = file.indexedPath == null || file.indexedPath.isBlank()
                    ? Path.of(file.storedPath) : Path.of(file.indexedPath);
            if (!Files.isRegularFile(path)) continue;
            try {
                String content = file.indexedPath == null || file.indexedPath.isBlank()
                        ? extract(path) : Files.readString(path, StandardCharsets.UTF_8);
                int index = 0;
                for (String chunk : chunks(content)) {
                    index++;
                    int score = score(chunk, terms);
                    if (score > 0) hits.add(new Hit(file.name, chunk.trim(), score, index));
                }
            } catch (IOException ignored) {
                // A origem pode ser reindexada na tela de conhecimento.
            }
        }
        hits.sort(Comparator.comparingInt(Hit::score).reversed());
        return hits.stream().limit(5).toList();
    }

    public String retrieve(Project project, String query) {
        return retrieve(project, query, "");
    }

    public String retrieve(Project project, String query, String agentId) {
        StringBuilder result = new StringBuilder();
        for (Hit hit : search(project, query, agentId)) {
            result.append("\n[Fonte: ").append(hit.name()).append(", trecho ")
                    .append(hit.chunkNumber()).append("]\n").append(hit.text()).append("\n");
        }
        return result.toString();
    }

    public String attachmentText(List<String> attachments) {
        StringBuilder result = new StringBuilder();
        for (String storedPath : attachments) {
            Path path = Path.of(storedPath);
            if (!Files.isRegularFile(path) || !canIndex(path)) continue;
            try {
                if (Files.size(path) > MAX_SOURCE_BYTES) continue;
                String text = extract(path);
                result.append("\n[Anexo: ").append(path.getFileName()).append("]\n")
                        .append(text, 0, Math.min(text.length(), 12_000)).append("\n");
            } catch (IOException ignored) {
                // O chat ainda mostra o nome do anexo quando o conteúdo não pode ser extraído.
            }
        }
        return result.toString();
    }

    private String extract(Path source) throws IOException {
        String extension = extension(source);
        if (extension.equals("pdf")) {
            try (PDDocument document = Loader.loadPDF(source.toFile())) {
                return new PDFTextStripper().getText(document);
            }
        }
        if (extension.equals("docx")) return extractDocx(source);
        if (!TEXT_EXTENSIONS.contains(extension)) throw new IOException("Formato não aceito: " + extension);
        String content = Files.readString(source, StandardCharsets.UTF_8);
        return content.length() > MAX_TEXT_CHARS ? content.substring(0, MAX_TEXT_CHARS) : content;
    }

    private String extractDocx(Path source) throws IOException {
        try (ZipFile zip = new ZipFile(source.toFile())) {
            ZipEntry document = zip.getEntry("word/document.xml");
            if (document == null) throw new IOException("Documento Word inválido.");
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
            StringBuilder text = new StringBuilder();
            try (InputStream input = zip.getInputStream(document)) {
                XMLStreamReader reader = factory.createXMLStreamReader(input);
                boolean inText = false;
                while (reader.hasNext() && text.length() < MAX_TEXT_CHARS) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT && reader.getLocalName().equals("t")) inText = true;
                    else if (event == XMLStreamConstants.END_ELEMENT) {
                        if (reader.getLocalName().equals("t")) inText = false;
                        if (reader.getLocalName().equals("p")) text.append('\n');
                    } else if (event == XMLStreamConstants.CHARACTERS && inText) text.append(reader.getText());
                }
                reader.close();
            } catch (javax.xml.stream.XMLStreamException e) {
                throw new IOException("Não foi possível ler o documento Word.", e);
            }
            return text.toString();
        }
    }

    private String extension(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int point = name.lastIndexOf('.');
        return point < 0 ? "" : name.substring(point + 1);
    }

    private Set<String> terms(String query) {
        Set<String> terms = new HashSet<>();
        for (String part : normalize(query).split("[^\\p{L}\\p{N}]+")) {
            if (part.length() >= 3 && !STOPWORDS.contains(part)) terms.add(part);
        }
        return terms;
    }

    private String normalize(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }

    private List<String> chunks(String text) {
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < text.length(); start += 1_100) {
            chunks.add(text.substring(start, Math.min(text.length(), start + 1_200)));
        }
        return chunks;
    }

    private int score(String chunk, Set<String> terms) {
        String lower = normalize(chunk);
        int score = 0;
        for (String term : terms) {
            int at = lower.indexOf(term);
            while (at >= 0) {
                score++;
                at = lower.indexOf(term, at + term.length());
            }
        }
        return score;
    }
}
