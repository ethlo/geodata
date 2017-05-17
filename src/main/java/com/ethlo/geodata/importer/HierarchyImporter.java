package com.ethlo.geodata.importer;

import static org.apache.commons.lang3.StringUtils.stripToNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import org.springframework.util.StringUtils;

public class HierarchyImporter implements DataImporter
{
    private final File hierarchyFile;
    
    public HierarchyImporter(File hierarchyFile)
    {
        this.hierarchyFile = hierarchyFile;
    }
    
    @Override
    public void processFile(Consumer<Map<String, String>> sink) throws IOException
    {
        try (final BufferedReader reader = new BufferedReader(new FileReader(hierarchyFile)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                final String[] entry = StringUtils.delimitedListToStringArray(line, "\t");
                final Map<String, String> paramMap = new TreeMap<>();
                paramMap.put("parent_id", stripToNull(entry[0]));
                paramMap.put("child_id", stripToNull(entry[1]));
                sink.accept(paramMap);
            }
        }        
    }
}
