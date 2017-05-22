package com.ethlo.geodata.model;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class Node
{
    private Long id;
    
    private List<Node> children;
    
    private Node parent;

    public Node(Long id)
    {
        this.id = id;
        children = new LinkedList<>();
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

    public List<Node> getChildren()
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
        return "Node [" + (id != null ? "id=" + id + ", " : "") + (children != null ? "children=" + StringUtils.collectionToCommaDelimitedString(children.stream().map(c->c.getId()).collect(Collectors.toList())) : "") + (parent != null ? "parent=" + parent.getId() : "") + "]";
    }
}