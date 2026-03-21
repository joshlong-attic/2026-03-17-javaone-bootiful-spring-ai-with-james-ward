package bootiful.client;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.mcp.security.client.sync.AuthenticationMcpTransportContextProvider;
import org.springaicommunity.mcp.security.client.sync.oauth2.http.client.OAuth2AuthorizationCodeSyncHttpRequestCustomizer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.mcp.client.common.autoconfigure.configurer.McpSyncClientConfigurer;
import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.sql.DataSource;
import java.security.Principal;
import java.util.List;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore store) {
        return QuestionAnswerAdvisor
                .builder(store)
                .build();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2Login(login -> login
                        .loginPage("/oauth2/authorization/spring"))
                .oauth2Client(Customizer.withDefaults())
                .build();
    }

    @Bean
    McpClientCustomizer<HttpClientStreamableHttpTransport.Builder> mcpTransportCustomizer(OAuth2AuthorizedClientManager authorizedClientManager) {
        var requestCustomizer = new OAuth2AuthorizationCodeSyncHttpRequestCustomizer(authorizedClientManager, "spring");
        return (name, builder) -> builder.httpRequestCustomizer(requestCustomizer);
    }

    @Bean
    McpSyncClientConfigurer mcpSyncClientConfigurer() {
        McpClientCustomizer<McpClient.SyncSpec> c =
                (name, spec) -> spec.transportContextProvider(new AuthenticationMcpTransportContextProvider());
        return new McpSyncClientConfigurer(List.of(c));
    }

    @Bean
    PromptChatMemoryAdvisor promptChatMemoryAdvisor(DataSource dataSource) {
        var jdbc = JdbcChatMemoryRepository
                .builder()
                .dataSource(dataSource)
                .build();
        var memory = MessageWindowChatMemory
                .builder()
                .chatMemoryRepository(jdbc)
                .build();
        return PromptChatMemoryAdvisor
                .builder(memory)
                .build();
    }

}


@Controller
@ResponseBody
class PoochPalaceController {

    private final ChatClient ai;

    PoochPalaceController(
            ToolCallbackProvider toolCallbackProvider,
            QuestionAnswerAdvisor questionAnswerAdvisor,
            PromptChatMemoryAdvisor promptChatMemoryAdvisor,
            ChatClient.Builder ai) {

        var system = """
                
                You are an AI powered assistant to help people adopt a dog from the adoptions agency named Pooch Palace 
                with locations in Antwerp, Seoul, Tokyo, Singapore, Paris, Mumbai, New Delhi, Barcelona, San Francisco, 
                and London. Information about the dogs availables will be presented below. If there is no information, 
                then return a polite response suggesting wes don't have any dogs available.
                
                If somebody asks for a time to pick up the dog, don't ask other questions: simply provide a time by 
                consulting the tools you have available.
                
                """;
        var skills = SkillsTool
                .builder()
                .addSkillsResource(new ClassPathResource("/META-INF/skills"))
                .build();
        this.ai = ai
                .defaultSystem(system)
                .defaultAdvisors(questionAnswerAdvisor, promptChatMemoryAdvisor)
                .defaultToolCallbacks(skills)
                .defaultToolCallbacks(toolCallbackProvider)
                .build();
    }

    @GetMapping("/ask")
    String ask(Principal principal) {
        IO.println(principal);

        return this.ai
                .prompt()
                .user("schedule time to pick prancer, id 47 from pooch palace")
                .call()
                .content();
    }
}
