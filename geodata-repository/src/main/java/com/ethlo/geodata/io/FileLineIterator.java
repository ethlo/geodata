package com.ethlo.geodata.io;

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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.util.CloseableIterator;

import com.google.common.collect.AbstractIterator;

public class FileLineIterator extends AbstractIterator<String> implements CloseableIterator<String>
{
    private final BufferedReader in;

    public FileLineIterator(File file) throws IOException
    {
        this.in = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
    }

    @Override
    protected String computeNext()
    {
        String line;
        try
        {
            line = in.readLine();
        }
        catch (IOException exc)
        {
            throw new DataAccessResourceFailureException(exc.getMessage(), exc);
        }
        return line != null ? line : endOfData();
    }

    @Override
    public void close()
    {
        try
        {
            in.close();
        }
        catch (IOException exc)
        {
            throw new DataAccessResourceFailureException(exc.getMessage(), exc);
        }
    }
}