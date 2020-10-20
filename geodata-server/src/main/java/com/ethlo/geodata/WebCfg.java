package com.ethlo.geodata;

import java.io.IOException;
import java.lang.reflect.Type;

import javax.annotation.Nonnull;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import com.jsoniter.output.JsonStream;

//@Configuration
public class WebCfg
{
    @Bean
    public HttpMessageConverter<Object> jsonConverter()
    {
        return new MappingJackson2HttpMessageConverter()
        {
            @Override
            protected void writeInternal(@Nonnull final Object o, final Type type, @Nonnull final HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException
            {
                JsonStream.serialize(type, o, outputMessage.getBody());
            }
        };
    }
}
