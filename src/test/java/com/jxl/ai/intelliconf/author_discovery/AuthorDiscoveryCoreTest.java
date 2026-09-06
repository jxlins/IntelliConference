package com.jxl.ai.intelliconf.author_discovery;

import com.jxl.ai.intelliconf.author_discovery.dto.AuthorCandidate;
import com.jxl.ai.intelliconf.author_discovery.dto.EmailEvidence;
import com.jxl.ai.intelliconf.author_discovery.dto.OpenAlexAuthor;
import com.jxl.ai.intelliconf.author_discovery.dto.OpenAlexPaper;
import com.jxl.ai.intelliconf.author_discovery.entity.AuthorDiscoveryJobDO;
import com.jxl.ai.intelliconf.author_discovery.entity.PotentialAuthorDO;
import com.jxl.ai.intelliconf.author_discovery.enums.ReviewStatus;
import com.jxl.ai.intelliconf.author_discovery.repository.AuthorDiscoveryJobMapper;
import com.jxl.ai.intelliconf.author_discovery.repository.AuthorEmailSourceMapper;
import com.jxl.ai.intelliconf.author_discovery.repository.EmailSuppressionMapper;
import com.jxl.ai.intelliconf.author_discovery.repository.PotentialAuthorMapper;
import com.jxl.ai.intelliconf.author_discovery.repository.PotentialAuthorRepository;
import com.jxl.ai.intelliconf.author_discovery.service.AuthorCandidateBuilder;
import com.jxl.ai.intelliconf.author_discovery.service.AuthorDiscoveryService;
import com.jxl.ai.intelliconf.author_discovery.service.CandidateScoringService;
import com.jxl.ai.intelliconf.author_discovery.service.OpenAlexClient;
import com.jxl.ai.intelliconf.author_discovery.service.PublicEmailExtractor;
import com.jxl.ai.intelliconf.author_discovery.config.AuthorDiscoveryProperties;
import com.jxl.ai.intelliconf.author_discovery.util.RobotsTxtChecker;
import com.sun.net.httpserver.HttpServer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorDiscoveryCoreTest {

    @Test
    void openAlexClientParsesWorksFromKeywordSearch() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/works", exchange -> {
            String response = """
                    {"results":[{"id":"https://openalex.org/W1","display_name":"AI in Education","publication_year":2025,
                    "publication_date":"2025-01-01","doi":"https://doi.org/10.1000/test","cited_by_count":7,
                    "primary_location":{"landing_page_url":"https://publisher.example/paper"},
                    "best_oa_location":{"landing_page_url":"https://oa.example/paper","pdf_url":"https://oa.example/paper.pdf"},
                    "open_access":{"is_oa":true},
                    "topics":[{"display_name":"Artificial Intelligence"}],
                    "keywords":[{"keyword":"learning analytics"}],
                    "authorships":[{"is_corresponding":true,"author":{"id":"A1","display_name":"Jane Smith"},
                    "institutions":[{"display_name":"Example University","country_code":"US"}]}]}]}
                    """;
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            AuthorDiscoveryProperties properties = new AuthorDiscoveryProperties();
            properties.setOpenalexBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            OpenAlexClient client = new OpenAlexClient(properties);

            List<OpenAlexPaper> papers = client.searchWorks(List.of("AI Education"), 2021, 2026, 10);

            assertEquals(1, papers.size());
            assertEquals("AI in Education", papers.get(0).getTitle());
            assertEquals("10.1000/test", papers.get(0).getDoi());
            assertEquals("Jane Smith", papers.get(0).getAuthors().get(0).getDisplayName());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void openAlexClientKeepsPartialResultsWhenOneKeywordFails() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/works", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null && query.contains("bad")) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
                return;
            }
            String response = """
                    {"results":[{"id":"https://openalex.org/W2","display_name":"Learning Analytics","publication_year":2025,
                    "doi":"https://doi.org/10.1000/ok","cited_by_count":3,"authorships":[]}]}
                    """;
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            AuthorDiscoveryProperties properties = new AuthorDiscoveryProperties();
            properties.setOpenalexBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            properties.setMaxRetry(0);
            OpenAlexClient client = new OpenAlexClient(properties);

            List<OpenAlexPaper> papers = client.searchWorks(List.of("bad keyword", "good keyword"), 2021, 2026, 10);

            assertEquals(1, papers.size());
            assertEquals("Learning Analytics", papers.get(0).getTitle());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void candidateBuilderMergesDuplicateAuthorByNameAndInstitution() {
        OpenAlexAuthor author = OpenAlexAuthor.builder()
                .displayName("Jane Smith")
                .institution("Example University")
                .countryCode("US")
                .corresponding(true)
                .build();
        OpenAlexPaper first = paper("AI in Education", 2025, author);
        OpenAlexPaper second = paper("Learning Analytics", 2024, author);

        List<AuthorCandidate> candidates = new AuthorCandidateBuilder().buildCandidates(List.of(first, second), 10);

        assertEquals(1, candidates.size());
        assertEquals(2, candidates.get(0).getPaperCount());
        assertTrue(candidates.get(0).isCorresponding());
    }

    @Test
    void emailExtractorExtractsValidEmailsFromHtml() {
        PublicEmailExtractor extractor = new PublicEmailExtractor(new AuthorDiscoveryProperties(), mock(RobotsTxtChecker.class));

        List<EmailEvidence> evidences = extractor.extractFromHtmlText(
                "<html><script>bad@example.com</script><body>Contact Jane Smith jane.smith@university.edu</body></html>",
                "https://example.org/paper",
                "OPEN_ACCESS_PAGE");

        assertEquals(1, evidences.size());
        assertEquals("jane.smith@university.edu", evidences.get(0).getEmail());
        assertFalse(evidences.get(0).getEvidenceText().contains("<body>"));
        assertTrue(evidences.get(0).getPageText().contains("Jane Smith"));
    }

    @Test
    void emailExtractorExtractsMailtoAndObfuscatedEmails() {
        PublicEmailExtractor extractor = new PublicEmailExtractor(new AuthorDiscoveryProperties(), mock(RobotsTxtChecker.class));

        List<EmailEvidence> evidences = extractor.extractFromHtmlText(
                """
                <html><body>
                  <a href="mailto:jane.smith%40university.edu">Email Jane Smith</a>
                  Prof. John Doe, Example University, john.doe [at] example.edu
                </body></html>
                """,
                "https://example.org/author",
                "AUTHOR_PAGE");

        assertEquals(2, evidences.size());
        assertTrue(evidences.stream().anyMatch(item -> "jane.smith@university.edu".equals(item.getEmail())));
        assertTrue(evidences.stream().anyMatch(item -> "john.doe@example.edu".equals(item.getEmail())));
        assertTrue(evidences.stream()
                .filter(item -> "jane.smith@university.edu".equals(item.getEmail()))
                .findFirst()
                .orElseThrow()
                .getEvidenceText()
                .contains("Jane Smith"));
    }

    @Test
    void robotsTxtReturns404AllowsAccess() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/robots.txt", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            AuthorDiscoveryProperties properties = new AuthorDiscoveryProperties();
            RobotsTxtChecker checker = new RobotsTxtChecker(properties);
            assertTrue(checker.isAllowed("http://127.0.0.1:" + server.getAddress().getPort() + "/paper"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void emailExtractorDiscoversPdfLinksFromHtml() {
        PublicEmailExtractor extractor = new PublicEmailExtractor(new AuthorDiscoveryProperties(), mock(RobotsTxtChecker.class));

        List<String> pdfLinks = extractor.discoverPdfLinks(
                "<html><body><a href=\"/article.pdf\">Download PDF</a></body></html>",
                "https://publisher.example/paper");

        assertEquals(List.of("https://publisher.example/article.pdf"), pdfLinks);
    }

    @Test
    void emailExtractorParsesPdfFirstPagesWithPdfBox() throws IOException {
        byte[] pdf = buildPdf("Corresponding author email: jane.smith@university.edu");
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/paper.pdf", exchange -> {
            exchange.getResponseHeaders().add("content-type", "application/pdf");
            exchange.sendResponseHeaders(200, pdf.length);
            exchange.getResponseBody().write(pdf);
            exchange.close();
        });
        server.start();
        try {
            RobotsTxtChecker robots = mock(RobotsTxtChecker.class);
            when(robots.isAllowed(any())).thenReturn(true);
            PublicEmailExtractor extractor = new PublicEmailExtractor(new AuthorDiscoveryProperties(), robots);

            List<EmailEvidence> evidences = extractor.extractFromUrls(
                    List.of("http://127.0.0.1:" + server.getAddress().getPort() + "/paper.pdf"));

            assertEquals(1, evidences.size());
            assertEquals("jane.smith@university.edu", evidences.get(0).getEmail());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void scoringReturnsValuesBetweenZeroAndOne() {
        AuthorCandidate candidate = AuthorCandidate.builder()
                .authorName("Jane Smith")
                .normalizedName("jane smith")
                .paperCount(2)
                .recentPaperCount(2)
                .totalCitations(40)
                .papers(List.of(paper("Artificial Intelligence in Education", 2025, null)))
                .emailEvidences(List.of(EmailEvidence.builder().email("jane@university.edu").confidence(0.85).build()))
                .build();

        new CandidateScoringService().score(candidate, List.of("Artificial Intelligence in Education"));

        assertTrue(candidate.getTopicSimilarity() >= 0 && candidate.getTopicSimilarity() <= 1);
        assertTrue(candidate.getEmailConfidence() >= 0 && candidate.getEmailConfidence() <= 1);
        assertTrue(candidate.getOverallScore() >= 0 && candidate.getOverallScore() <= 1);
    }

    @Test
    void repositoryDoesNotDuplicateExistingCandidate() {
        PotentialAuthorMapper authorMapper = mock(PotentialAuthorMapper.class);
        AuthorEmailSourceMapper sourceMapper = mock(AuthorEmailSourceMapper.class);
        EmailSuppressionMapper suppressionMapper = mock(EmailSuppressionMapper.class);
        PotentialAuthorRepository repository = new PotentialAuthorRepository(authorMapper, sourceMapper, suppressionMapper);
        PotentialAuthorDO existing = PotentialAuthorDO.builder().id(9L).conferenceId(1L).email("jane@university.edu").build();
        when(authorMapper.selectOne(any())).thenReturn(existing);

        repository.saveOrUpdateCandidate(PotentialAuthorDO.builder()
                .conferenceId(1L)
                .normalizedName("jane smith")
                .email("jane@university.edu")
                .overallScore(BigDecimal.ONE)
                .build(), List.of());

        verify(authorMapper, never()).insert(any());
        verify(authorMapper).updateById(any());
    }

    @Test
    void approvedCandidateIsNotOverwrittenAsPending() {
        PotentialAuthorMapper authorMapper = mock(PotentialAuthorMapper.class);
        AuthorEmailSourceMapper sourceMapper = mock(AuthorEmailSourceMapper.class);
        EmailSuppressionMapper suppressionMapper = mock(EmailSuppressionMapper.class);
        PotentialAuthorRepository repository = new PotentialAuthorRepository(authorMapper, sourceMapper, suppressionMapper);
        PotentialAuthorDO existing = PotentialAuthorDO.builder()
                .id(9L)
                .conferenceId(1L)
                .email("jane@university.edu")
                .reviewStatus(ReviewStatus.APPROVED.name())
                .emailConfidence(BigDecimal.valueOf(0.9))
                .build();
        when(authorMapper.selectOne(any())).thenReturn(existing);

        repository.saveOrUpdateCandidate(PotentialAuthorDO.builder()
                .conferenceId(1L)
                .normalizedName("jane smith")
                .email("jane@university.edu")
                .reviewStatus(ReviewStatus.PENDING.name())
                .emailConfidence(BigDecimal.valueOf(0.8))
                .build(), List.of());

        verify(authorMapper).updateById(org.mockito.ArgumentMatchers.argThat(item ->
                ReviewStatus.APPROVED.name().equals(item.getReviewStatus())
                        && BigDecimal.valueOf(0.9).compareTo(item.getEmailConfidence()) == 0));
    }

    @Test
    void suppressedEmailIsNotSavedAsContactableEmail() {
        PotentialAuthorMapper authorMapper = mock(PotentialAuthorMapper.class);
        AuthorEmailSourceMapper sourceMapper = mock(AuthorEmailSourceMapper.class);
        EmailSuppressionMapper suppressionMapper = mock(EmailSuppressionMapper.class);
        when(suppressionMapper.selectCount(any())).thenReturn(1L);
        PotentialAuthorRepository repository = new PotentialAuthorRepository(authorMapper, sourceMapper, suppressionMapper);
        PotentialAuthorDO candidate = PotentialAuthorDO.builder()
                .conferenceId(1L)
                .normalizedName("jane smith")
                .email("jane@university.edu")
                .build();

        repository.saveOrUpdateCandidate(candidate, List.of());

        assertEquals(null, candidate.getEmail());
        assertEquals("BLOCKED", candidate.getContactStatus());
    }

    @Test
    void taskFailureCanBeMarkedFailed() {
        AuthorDiscoveryJobMapper jobMapper = mock(AuthorDiscoveryJobMapper.class);
        AuthorDiscoveryService service = new AuthorDiscoveryService(
                jobMapper, null, null, null, null, null, null, null, null, null, null, null);

        service.markFailed(1L, "OpenAlex unavailable");

        verify(jobMapper).updateById(any(AuthorDiscoveryJobDO.class));
    }

    private OpenAlexPaper paper(String title, int year, OpenAlexAuthor author) {
        return OpenAlexPaper.builder()
                .title(title)
                .publicationYear(year)
                .citedByCount(10)
                .topics(List.of("Artificial Intelligence in Education"))
                .keywords(List.of("Learning Analytics"))
                .authors(author == null ? List.of() : List.of(author))
                .build();
    }

    private byte[] buildPdf(String text) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
