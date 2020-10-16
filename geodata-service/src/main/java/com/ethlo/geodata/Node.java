package com.ethlo.geodata;

/*-
 * #%L
 * geodata
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

import java.util.Arrays;
import java.util.Objects;

public class Node implements Comparable<Node>
{
    private static final int[] EMPTY = new int[0];

    private final int id;
    private int[] children;
    private Integer parent;

    public Node(int id)
    {
        this.id = id;
    }

    public void addChild(int child)
    {
        if (children == null)
        {
            this.children = new int[]{child};
        }
        else
        {
            this.children = Arrays.copyOf(children, children.length + 1);
            this.children[this.children.length - 1] = child;
        }
    }

    public Integer getId()
    {
        return id;
    }

    public int[] getChildren()
    {
        return children != null ? children : EMPTY;
    }

    public Integer getParent()
    {
        return parent;
    }

    public void setParent(Integer parent)
    {
        this.parent = parent;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof Node)
        {
            return Objects.equals(id, ((Node) obj).getId());
        }
        return false;
    }

    @Override
    public String toString()
    {
        return "Node [id=" + id + ", " +
                "children=" + Arrays.toString(children) + ", " +
                "parent=" + parent + "]";
    }

    @Override
    public int compareTo(final Node node)
    {
        return Integer.compare(id, node.id);
    }
}
