package com.ethlo.geodata.importer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import com.ethlo.geodata.DataType;

@SpringBootApplication
@ComponentScan(basePackageClasses = DataType.class)
public class Main
{
    public static void main(String[] args)
    {
        SpringApplication.run(Main.class, args);
    }
}
