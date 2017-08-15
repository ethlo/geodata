package com.ethlo.geodata.model;

/*-
 * #%L
 * geodata-model
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

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class View
{
	protected View()
	{
		
	}
	
    @NotNull
    @Min(-180)
    @Max(180)
	private Double minLng;
    
    @NotNull
    @Min(-180)
    @Max(180)
	private Double maxLng;
	
    @NotNull
    @Min(-90)
    @Max(90)
    private Double minLat;

    @NotNull
    @Min(-90)
    @Max(90)
    private Double maxLat;
	
    @NotNull
    @Min(32)
	private Integer height;
    
    @NotNull
    @Min(32)
	private Integer width;
	
	public View(double minLng, double maxLng, double minLat, double maxLat, int width, int height)
	{
		this.minLng = minLng;
		this.maxLng = maxLng;
		this.minLat = minLat;
		this.maxLat = maxLat;
		this.width = width;
		this.height = height;
	}
	
	public void setMinLng(Double minLng)
	{
		this.minLng = minLng;
	}

	public void setMaxLng(Double maxLng)
	{
		this.maxLng = maxLng;
	}

	public void setMinLat(Double minLat)
	{
		this.minLat = minLat;
	}

	public void setMaxLat(Double maxLat)
	{
		this.maxLat = maxLat;
	}

	public void setHeight(Integer height)
	{
		this.height = height;
	}

	public void setWidth(Integer width)
	{
		this.width = width;
	}

	public Double getMinLng()
	{
		return minLng;
	}

	public Double getMaxLng()
	{
		return maxLng;
	}

	public Double getMinLat()
	{
		return minLat;
	}

	public Double getMaxLat()
	{
		return maxLat;
	}

	public Integer getHeight()
	{
		return height;
	}

	public Integer getWidth()
	{
		return width;
	}
}
