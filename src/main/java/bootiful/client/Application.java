package bootiful.client;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.mcp.security.client.sync.AuthenticationMcpTransportContextProvider;
import org.springaicommunity.mcp.security.client.sync.oauth2.http.client.OAuth2AuthorizationCodeSyncHttpRequestCustomizer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
//import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpRequest;

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
    OAuth2AuthorizationCodeSyncHttpRequestCustomizer auth2AuthorizationCodeSyncHttpRequestCustomizer(OAuth2AuthorizedClientManager authorizeRequest) {
        return new OAuth2AuthorizationCodeSyncHttpRequestCustomizer(authorizeRequest, "spring");
    }

//    @Bean
//    McpSyncHttpClientRequestCustomizer
//    mcpSyncHttpClientRequestCustomizer () {
//        return new McpSyncHttpClientRequestCustomizer() {
//            @Override
//            public void customize(HttpRequest.Builder builder, String method, URI endpoint, String body, McpTransportContext context) {
//
//            }
//        }
//    }

//    @Bean
//    McpSyncClientCustomizer mcpSyncClientCustomizer() {
//        return (s, syncSpec) -> syncSpec
//                .transportContextProvider(new AuthenticationMcpTransportContextProvider());
//    }

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
    String ask(@RequestParam String question) {
        return this.ai
                .prompt()
                .user(question)
                .call()
                .content();
    }
}