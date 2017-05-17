package com.ethlo.geodata.importer;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

public interface DataImporter
{
    void processFile(Consumer<Map<String, String>> sink) throws IOException;
}
