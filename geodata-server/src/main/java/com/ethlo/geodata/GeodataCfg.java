package com.ethlo.geodata;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;

@Configuration
public class GeodataCfg
{
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer()
    {
        return b->b.modulesToInstall(new AfterburnerModule(), new Jdk8Module());
    }    
}
