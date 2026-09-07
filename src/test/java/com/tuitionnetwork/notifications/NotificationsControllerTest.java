package com.tuitionnetwork.notifications;

import com.example.demo.DemoApplication;
import com.tuitionnetwork.notifications.domain.NotifSeverity;
import com.tuitionnetwork.notifications.domain.NotifType;
import com.tuitionnetwork.notifications.dto.BackOfficeNotificationDto;
import com.tuitionnetwork.notifications.dto.NotificationListResponse;
import com.tuitionnetwork.notifications.service.NotificationsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DemoApplication.class)
class NotificationsControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private NotificationsService notificationsService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private NotificationListResponse oneItem() {
        BackOfficeNotificationDto dto = new BackOfficeNotificationDto(
                UUID.randomUUID(), NotifType.RECON_EXCEPTION, NotifSeverity.HIGH,
                "Reconciliation Exception Detected", "EGP 9,400 discrepancy",
                "Exception: EXC-001", false, LocalDateTime.now(),
                new BackOfficeNotificationDto.Action("Investigate Exception", "reconciliation", "EXC-001"));
        return new NotificationListResponse(List.of(dto), 0, 25, 1, 1, 5);
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void list_returns200_withUnreadCount() throws Exception {
        when(notificationsService.list(any(), anyBoolean(), anyInt(), anyInt())).thenReturn(oneItem());

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(5))
                .andExpect(jsonPath("$.data[0].type").value("RECON_EXCEPTION"))
                .andExpect(jsonPath("$.data[0].action.screen").value("reconciliation"));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void list_filterByTypeAndUnread_passesParams() throws Exception {
        when(notificationsService.list(eq(NotifType.FAILED_PAYMENT), eq(true), anyInt(), anyInt()))
                .thenReturn(new NotificationListResponse(List.of(), 0, 25, 0, 0, 0));

        mockMvc.perform(get("/api/v1/notifications")
                        .param("type", "FAILED_PAYMENT").param("unread", "true"))
                .andExpect(status().isOk());

        verify(notificationsService).list(eq(NotifType.FAILED_PAYMENT), eq(true), anyInt(), anyInt());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void unreadCount_returns200() throws Exception {
        when(notificationsService.unreadCount()).thenReturn(5L);

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void markRead_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/notifications/{id}/read", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        verify(notificationsService).markRead(id);
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void markRead_unknown_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found: " + id))
                .when(notificationsService).markRead(id);

        mockMvc.perform(post("/api/v1/notifications/{id}/read", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void readAll_returns200_withUpdatedCount() throws Exception {
        when(notificationsService.markAllRead()).thenReturn(3);

        mockMvc.perform(post("/api/v1/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(3));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void dismiss_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/notifications/{id}", id))
                .andExpect(status().isNoContent());
        verify(notificationsService).dismiss(id);
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void dismiss_unknown_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found: " + id))
                .when(notificationsService).dismiss(id);

        mockMvc.perform(delete("/api/v1/notifications/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "parent@example.com", roles = {"GUARDIAN"})
    void guardian_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }
}
