package com.smartdesk.ticket.client;

import com.smartdesk.ticket.dto.client.NotificationEvent;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service", url = "${app.clients.notification-service.url:http://localhost:8083}")
public interface NotificationClient {

    @PostMapping("/api/v1/notifications/send")
    void send(@RequestBody NotificationEvent event);
}
