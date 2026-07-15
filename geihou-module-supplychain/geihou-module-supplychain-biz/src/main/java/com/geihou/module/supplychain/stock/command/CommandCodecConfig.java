package com.geihou.module.supplychain.stock.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@link CommandCodec} bean backed by {@link CommandCodecImpl}.
 *
 * <p>{@code CommandCodecImpl} intentionally has no {@code @Component}; this
 * config class is the single registration point so the strict Jackson
 * {@link ObjectMapper} configuration stays explicit and testable.
 *
 * <p>The Spring Boot auto-configured {@link ObjectMapper} is injected and
 * {@link ObjectMapper#copy() copied} inside the impl, so the shared bean is
 * never mutated.
 */
@Configuration
public class CommandCodecConfig {

    @Bean
    public CommandCodec commandCodec(ObjectMapper springBootMapper, CommandClock clock) {
        return new CommandCodecImpl(springBootMapper, clock);
    }
}
