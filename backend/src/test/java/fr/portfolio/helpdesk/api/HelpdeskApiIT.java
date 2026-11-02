package fr.portfolio.helpdesk.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'intégration de bout en bout : vraie base PostgreSQL (Testcontainers), migrations Flyway,
 * sécurité, contrôleurs et services. Données de démonstration activées.
 */
@SpringBootTest(properties = {"app.seed=true", "spring.mail.host=localhost", "spring.mail.port=3025", "app.upload-dir=./target/uploads-it"})
@AutoConfigureMockMvc
@Testcontainers
class HelpdeskApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String token(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asText();
    }

    @Test
    @DisplayName("sans jeton : 401 ; mauvais mot de passe : 401")
    void unauthenticated() throws Exception {
        mvc.perform(get("/api/tickets")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@helpdesk.local\",\"password\":\"faux\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("parcours complet : création par l'utilisateur, traitement par le technicien")
    void fullTicketLifecycle() throws Exception {
        String user = token("user@helpdesk.local", "User123!");
        String tech = token("tech@helpdesk.local", "Tech123!");

        String created = mvc.perform(post("/api/tickets").header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Écran noir au démarrage\",\"description\":\"Le PC démarre mais l'écran reste noir.\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.sla").value("OK"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();

        // L'utilisateur ne peut pas changer le statut
        mvc.perform(patch("/api/tickets/" + id).header("Authorization", user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CLOSED\"}")).andExpect(status().isForbidden());

        // Le technicien le prend en charge
        mvc.perform(patch("/api/tickets/" + id).header("Authorization", tech).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstResponseAt").isNotEmpty());

        // Note interne invisible pour le demandeur
        mvc.perform(post("/api/tickets/" + id + "/comments").header("Authorization", tech).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Vérifier le câble HDMI\",\"internal\":true}")).andExpect(status().isCreated());
        JsonNode seenByUser = json.readTree(mvc.perform(get("/api/tickets/" + id).header("Authorization", user))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(seenByUser.get("comments")).isEmpty();
        assertThat(seenByUser.get("events").size()).isGreaterThanOrEqualTo(2);

        // Transition interdite
        mvc.perform(patch("/api/tickets/" + id).header("Authorization", tech).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Transition interdite : IN_PROGRESS → OPEN"));
    }

    @Test
    @DisplayName("cloisonnement : un utilisateur ne voit que ses tickets")
    void isolation() throws Exception {
        String admin = token("admin@helpdesk.local", "Admin123!");
        mvc.perform(post("/api/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"autre@helpdesk.local\",\"name\":\"Autre\",\"role\":\"USER\",\"password\":\"Autre1234\"}"))
                .andExpect(status().isCreated());
        String other = token("autre@helpdesk.local", "Autre1234");

        mvc.perform(get("/api/tickets").header("Authorization", other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/tickets/1").header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(get("/api/stats/overview").header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(get("/api/users").header("Authorization", other)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("tableau de bord pour les techniciens")
    void stats() throws Exception {
        mvc.perform(get("/api/stats/overview").header("Authorization", token("tech@helpdesk.local", "Tech123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perDay.length()").value(31))
                .andExpect(jsonPath("$.activeTickets").isNumber());
    }
}
