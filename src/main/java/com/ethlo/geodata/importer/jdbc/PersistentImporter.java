package com.ethlo.geodata.importer.jdbc;

import java.io.IOException;
import java.util.Date;

public interface PersistentImporter
{
    void purge() throws IOException;
    
    void importData() throws IOException;
    
    Date lastRemoteModified() throws IOException;
}
