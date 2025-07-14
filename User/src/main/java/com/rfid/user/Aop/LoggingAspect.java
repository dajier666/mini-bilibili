package com.rfid.user.Aop;


import com.rfid.user.entity.LogMessage;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggingAspect {

    private final KafkaTemplate<String, LogMessage> kafkaTemplate;

    @Value("${logging.kafka.topic:spring-boot-logs}")
    private String topic;

    public LoggingAspect(KafkaTemplate<String, LogMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @After("execution(* org.slf4j.Logger.*(..))")
    public void logToKafka(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0 && args[0] instanceof String message) {
            String level = joinPoint.getSignature().getName();

            kafkaTemplate.send(topic, new LogMessage(
                    level.toUpperCase(),
                    joinPoint.getTarget().getClass().getName(),
                    message,
                    Thread.currentThread().getName()
            ));
        }
    }
}
