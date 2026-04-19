package com.chatapp.controller;

import com.chatapp.BaseIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FriendshipTest extends BaseIntegrationTest {

    private record UserInfo(Long id, Cookie cookie) {}

    private UserInfo signUp(String email, String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email":"%s","username":"%s","password":"password123","displayName":"Test User"}
                        """.formatted(email, username)))
                .andExpect(status().isCreated())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("SESSION");
        Long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        return new UserInfo(id, cookie);
    }

    @Test
    void sendFriendRequest_success() throws Exception {
        UserInfo user1 = signUp("fr1a@test.com", "fr1a");
        UserInfo user2 = signUp("fr1b@test.com", "fr1b");

        mockMvc.perform(post("/api/friends/request")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user2.id)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.direction").value("OUTGOING"))
                .andExpect(jsonPath("$.friendUsername").value("fr1b"));
    }

    @Test
    void sendFriendRequest_toSelf_returns400() throws Exception {
        UserInfo user1 = signUp("fr2@test.com", "fr2");

        mockMvc.perform(post("/api/friends/request")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user1.id)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sendFriendRequest_duplicate_returnsConflict() throws Exception {
        UserInfo user1 = signUp("fr3a@test.com", "fr3a");
        UserInfo user2 = signUp("fr3b@test.com", "fr3b");

        mockMvc.perform(post("/api/friends/request")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user2.id)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/friends/request")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user2.id)))
                .andExpect(status().isConflict());
    }

    @Test
    void acceptFriendRequest_success() throws Exception {
        UserInfo user1 = signUp("fr4a@test.com", "fr4a");
        UserInfo user2 = signUp("fr4b@test.com", "fr4b");

        MvcResult result = mockMvc.perform(post("/api/friends/request")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user2.id)))
                .andExpect(status().isCreated())
                .andReturn();
        Long friendshipId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/friends/" + friendshipId + "/accept")
                        .cookie(user2.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // Both users should see each other in friends list
        mockMvc.perform(get("/api/friends").cookie(user1.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].friendUsername").value("fr4b"));

        mockMvc.perform(get("/api/friends").cookie(user2.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].friendUsername").value("fr4a"));
    }

    @Test
    void declineFriendRequest_success() throws Exception {
        UserInfo user1 = signUp("fr5a@test.com", "fr5a");
        UserInfo user2 = signUp("fr5b@test.com", "fr5b");

        MvcResult result = mockMvc.perform(post("/api/friends/request")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user2.id)))
                .andExpect(status().isCreated())
                .andReturn();
        Long friendshipId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/friends/" + friendshipId + "/decline")
                        .cookie(user2.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    void acceptFriendRequest_byNonAddressee_returnsConflict() throws Exception {
        UserInfo user1 = signUp("fr6a@test.com", "fr6a");
        UserInfo user2 = signUp("fr6b@test.com", "fr6b");

        MvcResult result = mockMvc.perform(post("/api/friends/request")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user2.id)))
                .andExpect(status().isCreated())
                .andReturn();
        Long friendshipId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        // User1 (requester) tries to accept their own request
        mockMvc.perform(post("/api/friends/" + friendshipId + "/accept")
                        .cookie(user1.cookie))
                .andExpect(status().isConflict());
    }

    @Test
    void removeFriend_success() throws Exception {
        UserInfo user1 = signUp("fr7a@test.com", "fr7a");
        UserInfo user2 = signUp("fr7b@test.com", "fr7b");

        MvcResult result = mockMvc.perform(post("/api/friends/request")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user2.id)))
                .andExpect(status().isCreated())
                .andReturn();
        Long friendshipId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/friends/" + friendshipId + "/accept").cookie(user2.cookie))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/friends/" + friendshipId).cookie(user1.cookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/friends").cookie(user1.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getPendingRequests_returnsIncomingAndOutgoing() throws Exception {
        UserInfo user1 = signUp("fr8a@test.com", "fr8a");
        UserInfo user2 = signUp("fr8b@test.com", "fr8b");

        mockMvc.perform(post("/api/friends/request")
                        .cookie(user1.cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"userId":%d}
                        """.formatted(user2.id)))
                .andExpect(status().isCreated());

        // User1 sees outgoing
        mockMvc.perform(get("/api/friends/requests").cookie(user1.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].direction").value("OUTGOING"));

        // User2 sees incoming
        mockMvc.perform(get("/api/friends/requests").cookie(user2.cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].direction").value("INCOMING"));
    }
}
