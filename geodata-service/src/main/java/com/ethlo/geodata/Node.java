package com.ethlo.geodata;

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
        if (obj instanceof Node)
        {
            Node other = (Node) obj;
            return other.id == id;
        }
        return false;
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
