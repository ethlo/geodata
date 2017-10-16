package com.ethlo.geodata.boundaries;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.springframework.dao.DataAccessResourceFailureException;

public class WkbDataWriter implements AutoCloseable
{
    private BufferedOutputStream out;
    private DataOutputStream indexOut;
    private long offset = 0;

    public WkbDataWriter(File file) throws FileNotFoundException
    {
        final File indexFile = new File(file.getAbsolutePath() + ".index");
        this.out = new BufferedOutputStream(new FileOutputStream(file)); 
        this.indexOut = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(indexFile)));
    }
    
    public void write(long id, byte[] wkb)
    {
        try
        {
            indexOut.writeLong(id);
            indexOut.writeLong(offset);
            indexOut.writeInt(wkb.length);
            
            out.write(wkb);
            offset += wkb.length;
        }
        catch (IOException exc)
        {
            throw new DataAccessResourceFailureException(exc.getMessage(), exc);
        }
    }

    @Override
    public void close() throws IOException
    {
        if (this.out != null)
        {
            this.out.close();
        }
        
        if (this.indexOut != null)
        {
            this.indexOut.close();
        }
    }
}