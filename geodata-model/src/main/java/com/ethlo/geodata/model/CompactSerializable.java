package com.ethlo.geodata.model;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface CompactSerializable
{
    void write(DataOutputStream out) throws IOException;

    void read(DataInputStream in) throws IOException;
}
