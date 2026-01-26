package com.juvis.juvis.notification;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;

import lombok.extern.slf4j.Slf4j;
import com.google.firebase.messaging.BatchResponse;

@Slf4j
@Service
public class FcmPushService {

    public void sendToTokens(List<String> tokens, String title, String body, Map<String,String> data) {
        if (tokens == null || tokens.isEmpty()) {
            log.info("📭 FCM skip: tokens empty");
            return;
        }

        var builder = MulticastMessage.builder()
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .addAllTokens(tokens);

        if (data != null && !data.isEmpty()) builder.putAllData(data);

        try {
            BatchResponse res = FirebaseMessaging.getInstance().sendEachForMulticast(builder.build());

            log.info("✅ FCM sent: success={} failure={} total={}",
                    res.getSuccessCount(), res.getFailureCount(), tokens.size());

            if (res.getFailureCount() > 0) {
                for (int i = 0; i < res.getResponses().size(); i++) {
                    var r = res.getResponses().get(i);
                    if (!r.isSuccessful()) {
                        log.warn("❌ FCM fail token[{}]={} error={}",
                                i, tokens.get(i), r.getException().getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ FCM send exception", e);
        }
    }
}


