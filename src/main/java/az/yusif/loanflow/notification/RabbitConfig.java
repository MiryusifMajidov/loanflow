package az.yusif.loanflow.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "loanflow.notifications";
    static final String ARCHIVE_QUEUE = "loanflow.notifications.archive";

    @Bean
    TopicExchange notificationsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue notificationsArchive() {
        return QueueBuilder.durable(ARCHIVE_QUEUE).build();
    }

    @Bean
    Binding archiveBinding(Queue notificationsArchive, TopicExchange notificationsExchange) {
        return BindingBuilder.bind(notificationsArchive).to(notificationsExchange).with("#");
    }

    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
