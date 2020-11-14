package com.ethlo.geodata;

import org.springframework.boot.SpringApplication;

import com.ethlo.geodata.importer.GeodataImporter;

public class Runner
{
    public static void main(String[] args)
    {
        if (args.length == 1 && "-import".equals(args[0]))
        {
            SpringApplication.run(GeodataImporter.class, args);
        }
        else
        {
            SpringApplication.run(UndertowServer.class, args);
        }
    }
}
