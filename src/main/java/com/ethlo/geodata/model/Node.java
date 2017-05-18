package com.ethlo.geodata.model;

import org.apache.commons.lang3.ArrayUtils;

public class Node
{
    private final Node parent;
    private final long self;
    private Node[] children = new Node[0];
    
    public Node(Node parent, long self)
    {
        this.parent = parent;
        if (parent != null)
        {
            parent.children = ArrayUtils.add(children, this);
        }
        this.self = self;
    }

    public Node getParent()
    {
        return parent;
    }
    
    public Node[] getChildren()
    {
        return children;
    }

    public long getSelf()
    {
        return self;
    }

    public void addChild(Long child)
    {
        parent.children = ArrayUtils.add(children, this);        
    }
}
