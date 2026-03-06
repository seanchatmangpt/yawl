import java.time.Instant;
import java.util.UUID;
import org.yawlfoundation.yawl.engine.api.controller.AgentController;
import org.yawlfoundation.yawl.engine.agent.AgentEngineService;
import org.yawlfoundation.yawl.engine.api.dto.AgentDTO;

public class TestController {
    public static void main(String[] args) {
        // Test if AgentController can be instantiated and basic methods work
        try {
            // Create a mock service
            AgentEngineService service = new AgentEngineService() {
                @Override
                public boolean isReady() {
                    return true;
                }
            };

            // Create controller
            AgentController controller = new AgentController(service);

            // Test if we can create an AgentDTO (this tests the imports)
            AgentDTO dto = AgentDTO.create(
                UUID.randomUUID(),
                "RUNNING",
                "test-workflow",
                0L,
                60000L,
                0L,
                Instant.now(),
                Instant.now()
            );

            System.out.println("AgentController compilation test successful!");
            System.out.println("Created DTO: " + dto);
        } catch (Exception e) {
            System.err.println("Compilation or runtime error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}