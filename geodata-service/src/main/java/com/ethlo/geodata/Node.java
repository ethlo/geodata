package com.ethlo.geodata;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class Node implements Comparable<Node>
{
    private Long id;
    
    private Set<Node> children;
    
    private Node parent;

    public Node(Long id)
    {
        this.id = id;
        children = new TreeSet<>();
    }

    public void addChild(Node child)
    {
        this.children.add(child);
    }

    public void setParent(Node parent)
    {
        this.parent = parent;
    }

    public Long getId()
    {
        return id;
    }

    public Set<Node> getChildren()
    {
        return children;
    }

    public Node getParent()
    {
        return parent;
    }
    
    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((children == null) ? 0 : children.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((parent == null) ? 0 : parent.id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Node other = (Node) obj;
        if (children == null)
        {
            if (other.children != null)
                return false;
        }
        else if (!children.equals(other.children))
            return false;
        if (id == null)
        {
            if (other.id != null)
                return false;
        }
        else if (!id.equals(other.id))
            return false;
        if (parent == null)
        {
            if (other.parent != null)
                return false;
        }
        else if (!parent.equals(other.parent))
            return false;
        return true;
    }

    @Override
    public String toString()
    {
        return "Node [" + (id != null ? "id=" + id + ", " : "") + 
            (children != null ? "children=" + children.stream().map(c->c.getId().toString()).collect(Collectors.joining(",")) : "") + 
            (parent != null ? "parent=" + parent.getId() : "") + "]";
    }

    @Override
    public int compareTo(Node o)
    {
        return id.compareTo(o.id);
    }
}
