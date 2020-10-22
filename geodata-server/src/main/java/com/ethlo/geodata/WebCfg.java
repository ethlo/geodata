package com.ethlo.geodata;

/*-
 * #%L
 * geodata-server
 * %%
 * Copyright (C) 2017 - 2020 Morten Haraldsen (ethlo)
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
