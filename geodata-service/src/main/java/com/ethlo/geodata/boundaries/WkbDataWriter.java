package com.ethlo.geodata.boundaries;

/*-
 * #%L
 * Geodata service
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
