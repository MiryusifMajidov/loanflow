package az.yusif.loanflow.notification;

import java.time.Instant;
import java.util.Map;

public record NotificationMessage(String recipientId,
                                  String type,
                                  String text,
                                  Map<String, Object> data,
                                  Instant createdAt) {
}
