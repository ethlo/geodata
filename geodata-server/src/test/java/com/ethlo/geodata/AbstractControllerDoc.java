package com.ethlo.geodata;

import java.io.IOException;
import java.sql.SQLException;

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


import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.restdocs.JUnitRestDocumentation;
import org.springframework.restdocs.cli.CliDocumentation;
import org.springframework.restdocs.http.HttpDocumentation;
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation;
import org.springframework.restdocs.operation.preprocess.Preprocessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.ethlo.geodata.restdocs.RequestSchemaSnippet;
import com.ethlo.geodata.restdocs.ResponseSchemaSnippet;
import com.fasterxml.jackson.databind.ObjectMapper;

import capital.scalable.restdocs.AutoDocumentation;
import capital.scalable.restdocs.SnippetRegistry;
import capital.scalable.restdocs.jackson.JacksonResultHandlers;
import capital.scalable.restdocs.response.ResponseModifyingPreprocessors;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = RestGeodataApplication.class)
@WebAppConfiguration
@ActiveProfiles("test")
@TestPropertySource(locations="classpath:test-application.properties")
@TestExecutionListeners(inheritListeners=false, listeners={DependencyInjectionTestExecutionListener.class})
public abstract class AbstractControllerDoc
{
    @Rule
    public final JUnitRestDocumentation restDocumentation = new JUnitRestDocumentation("target/generated-snippets");

    @Autowired
    private WebApplicationContext context;
    
    @Autowired
    private GeoMetaService geoMetaService;

    protected MockMvc mockMvc;

    private static boolean initialized = false;
    private static boolean loaded = false;
    
    @Before
    public void contextLoads() throws IOException, SQLException
    {
        if (! initialized)
        {
            geoMetaService.update();
            initialized = true;
        }

        final GeodataServiceImpl impl = context.getBean(GeodataServiceImpl.class);
        if (! loaded)
        {
            impl.load();
            loaded = true;
        }
    }
    
    @Before
    public void setUp() throws Exception
    {
        final ObjectMapper objectMapper = new ObjectMapper();
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .alwaysDo(JacksonResultHandlers.prepareJackson(objectMapper))
                .alwaysDo(MockMvcRestDocumentation.document("{class-name}/{method-name}",
                        Preprocessors.preprocessRequest(),
                        Preprocessors.preprocessResponse(
                                ResponseModifyingPreprocessors.replaceBinaryContent(),
                                ResponseModifyingPreprocessors.limitJsonArrayLength(objectMapper),
                                Preprocessors.prettyPrint())))
                .apply(MockMvcRestDocumentation.documentationConfiguration(restDocumentation)
                        .uris()
                        .withScheme("http")
                        .withHost("localhost")
                        .withPort(8080)
                        .and().snippets()
                        .withDefaults(
                            CliDocumentation.curlRequest(),
                            new RequestSchemaSnippet(),
                            new ResponseSchemaSnippet(),
                            HttpDocumentation.httpRequest(),
                            HttpDocumentation.httpResponse(),
                            AutoDocumentation.pathParameters(),
                            AutoDocumentation.requestParameters(),
                            AutoDocumentation.description(),
                            AutoDocumentation.methodAndPath(),
                            AutoDocumentation.sectionBuilder()
                            .snippetNames(
                                SnippetRegistry.CURL_REQUEST,
                                SnippetRegistry.PATH_PARAMETERS,
                                SnippetRegistry.REQUEST_PARAMETERS,
                                SnippetRegistry.REQUEST_FIELDS,
                                "request-schema",
                                SnippetRegistry.RESPONSE_FIELDS,
                                "response-schema",
                                SnippetRegistry.HTTP_RESPONSE)
                            .skipEmpty(true) 
                            .build()))
                .build();
    }
}