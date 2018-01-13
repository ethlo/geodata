package com.ethlo.geodata;

/*-
 * #%L
 * geodata-server
 * %%
 * Copyright (C) 2017 Morten Haraldsen (ethlo)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import java.util.List;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;

import com.fasterxml.jackson.module.afterburner.AfterburnerModule;

import cz.jirutka.spring.exhandler.RestHandlerExceptionResolver;
import cz.jirutka.spring.exhandler.messages.ErrorMessage;
import cz.jirutka.spring.exhandler.support.HttpMessageConverterUtils;

@Configuration
public class GeodataCfg extends WebMvcConfigurerAdapter 
{
    @Override
    public void configureHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers)
    {
        resolvers.add(exceptionHandlerExceptionResolver());
        resolvers.add(restExceptionResolver());
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer()
    {
        return b->b.modulesToInstall(new AfterburnerModule());
    }
    
    @Bean
    public RestHandlerExceptionResolver restExceptionResolver()
    {
        return RestHandlerExceptionResolver.builder()
                .defaultContentType(MediaType.APPLICATION_JSON)
                .addErrorMessageHandler(InvalidIpException.class, HttpStatus.BAD_REQUEST)
                .addHandler(EmptyResultDataAccessException.class, (e,r)->
                	{ 
                		final ErrorMessage tmpl = new ErrorMessage();
                        tmpl.setDetail(e.getMessage());
                        tmpl.setStatus(HttpStatus.NOT_FOUND);
                        tmpl.setTitle("Not Found");
                        return new ResponseEntity<>(tmpl, HttpStatus.NOT_FOUND);
                    })
                .addHandler(BindException.class, (e,r)->
            	{ 
            		final ErrorMessage tmpl = new ErrorMessage();
                    tmpl.setDetail(StringUtils.collectionToCommaDelimitedString(e.getFieldErrors()));
                    tmpl.setStatus(HttpStatus.BAD_REQUEST);
                    tmpl.setTitle("Bad request");
                    return new ResponseEntity<>(tmpl, HttpStatus.BAD_REQUEST);
                })
                .addHandler(HttpMediaTypeNotAcceptableException.class, (e,r)->
                { 
                    final ErrorMessage tmpl = new ErrorMessage();
                    tmpl.setStatus(HttpStatus.NOT_ACCEPTABLE);
                    tmpl.setTitle("No acceptable representation");
                    return new ResponseEntity<>(tmpl, HttpStatus.NOT_ACCEPTABLE);
                })
                .build();
    }

    @Bean
    public ExceptionHandlerExceptionResolver exceptionHandlerExceptionResolver()
    {
        final ExceptionHandlerExceptionResolver resolver = new ExceptionHandlerExceptionResolver();
        resolver.setMessageConverters(HttpMessageConverterUtils.getDefaultHttpMessageConverters());
        return resolver;
    }
}
