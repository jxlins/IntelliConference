import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import com.jxl.ai.intelliconf.author_discovery.config.AuthorDiscoveryProperties;
import com.jxl.ai.intelliconf.author_discovery.service.*;
import com.jxl.ai.intelliconf.author_discovery.util.*;
import com.jxl.ai.intelliconf.author_discovery.repository.*;
import com.jxl.ai.intelliconf.author_discovery.dto.*;
import com.jxl.ai.intelliconf.dao.mapper.*;
import com.jxl.ai.intelliconf.mail.infrastructure.PasswordEncryptor;
import com.jxl.ai.intelliconf.mail.provider.*;

/** Explicit local operator diagnostic. Never sends email; discovery writes pending candidates only. */
public class VerifyWorkflow {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !Set.of("auth", "discovery", "probe").contains(args[0]))
            throw new IllegalArgumentException("Usage: VerifyWorkflow auth|discovery conferenceId");
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger("ROOT")).setLevel(ch.qos.logback.classic.Level.ERROR);
        Map<String,Object> config;
        try (var input = Files.newInputStream(Path.of("src/main/resources/application.yml"))) {
            config = new Yaml().load(input);
        }
        Map<String,Object> spring = (Map<String,Object>)config.get("spring");
        Map<String,Object> db = (Map<String,Object>)spring.get("datasource");
        var source = new DriverManagerDataSource((String)db.get("url"), (String)db.get("username"), (String)db.get("password"));
        var jdbc = new JdbcTemplate(source);
        Long conf = Long.valueOf(args[1]);
        if ("probe".equals(args[0])) {
            for (String url : List.of(
                    "https://api.openalex.org/works?search=intelligent%20tutoring%20systems&per-page=1&filter=is_oa:true&select=id,display_name",
                    "https://api.semanticscholar.org/graph/v1/paper/search?query=intelligent%20tutoring%20systems&limit=1&fields=title,openAccessPdf")) {
                try {
                    var client = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(12)).build();
                    var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).timeout(java.time.Duration.ofSeconds(12)).build();
                    var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                    System.out.println(java.net.URI.create(url).getHost() + " HTTP=" + response.statusCode() + " BODY=" + response.body().substring(0, Math.min(400, response.body().length())));
                } catch (Exception e) { System.out.println(java.net.URI.create(url).getHost() + " ERROR=" + e); }
            }
            return;
        }
        if ("auth".equals(args[0])) {
            var row = jdbc.queryForMap("SELECT * FROM conference_mail_account WHERE conference_id=?", conf);
            String password = new PasswordEncryptor().decrypt((String)row.get("password_cipher"));
            var account = MailAccountConfig.builder().conferenceId(conf)
                    .providerType(MailProviderType.valueOf((String)row.get("provider_type")))
                    .smtpHost((String)row.get("smtp_host")).smtpPort(((Number)row.get("smtp_port")).intValue())
                    .username((String)row.get("username")).fromEmail((String)row.get("from_email"))
                    .password(password).sslEnabled(Boolean.parseBoolean(row.get("ssl_enabled").toString()) || "1".equals(row.get("ssl_enabled").toString()))
                    .starttlsEnabled(Boolean.parseBoolean(row.get("starttls_enabled").toString()) || "1".equals(row.get("starttls_enabled").toString())).build();
            MailProvider provider = account.getProviderType() == MailProviderType.TENCENT_EXMAIL ? new TencentExmailProvider() : new SmtpMailProvider();
            var result = provider.checkConnection(account);
            System.out.println("SMTP_AUTH=" + result.isSuccess() + "; ERROR=" + result.getErrorCode() + "; DETAIL=" + result.getErrorMessage());
            return;
        }
        var mybatis = new MybatisConfiguration();
        mybatis.setMapUnderscoreToCamelCase(true);
        mybatis.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        mybatis.addMappers("com.jxl.ai.intelliconf.dao.mapper");
        mybatis.addMappers("com.jxl.ai.intelliconf.author_discovery.repository");
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(mybatis);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));
        var session = new SqlSessionTemplate(factory.getObject());
        var settings = new AuthorDiscoveryProperties();
        settings.setMaxTotalPapers(1200);
        settings.setMaxPapersFactor(30);
        settings.setMaxRetry(1);
        settings.setRequestTimeoutSeconds(12);
        settings.setJobDeadlineMinutes(10);
        settings.setPdfParseMaxPages(3);
        settings.setApiKey(System.getenv().getOrDefault("OPENALEX_API_KEY", ""));
        settings.setSemanticScholarApiKey(System.getenv().getOrDefault("SEMANTIC_SCHOLAR_API_KEY", ""));
        var repository = new PotentialAuthorRepository(session.getMapper(PotentialAuthorMapper.class),
                session.getMapper(AuthorEmailSourceMapper.class), session.getMapper(EmailSuppressionMapper.class));
        var service = new AuthorDiscoveryService(session.getMapper(AuthorDiscoveryJobMapper.class),
                session.getMapper(PotentialAuthorMapper.class), session.getMapper(ConferenceMapper.class),
                session.getMapper(ConfContactPoolMapper.class), session.getMapper(ConfMemberMapper.class),
                new OpenAlexClient(settings), new SemanticScholarClient(settings), new CrossrefClient(settings),
                new AuthorCandidateBuilder(), new PublicEmailExtractor(settings, new RobotsTxtChecker(settings)),
                new CandidateScoringService(), new AuthorIdentityMatcher(), repository, settings);
        var job = service.createJob(AuthorDiscoveryCommand.builder().conferenceId(conf).maxAuthors(200)
                .yearFrom(2021).yearTo(2026).topicKeywords(List.of("artificial intelligence in education", "intelligent tutoring systems", "learning analytics")).build());
        System.out.println("DISCOVERY_JOB_ID=" + job.getId());
        var monitor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(() -> {
            try { System.out.println("PROGRESS=" + com.alibaba.fastjson2.JSON.toJSONString(service.getJob(job.getId()))); }
            catch (Exception e) { System.out.println("PROGRESS_UNAVAILABLE"); }
        }, 10, 30, java.util.concurrent.TimeUnit.SECONDS);
        try { System.out.println("RESULT=" + com.alibaba.fastjson2.JSON.toJSONString(service.runJob(job.getId(), true, true))); }
        finally { monitor.shutdownNow(); }
    }
}
