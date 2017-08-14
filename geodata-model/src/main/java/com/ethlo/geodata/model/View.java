package com.ethlo.geodata.model;

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