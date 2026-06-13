package com.commander4j.logorenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Holds the parsed contents of an LLF layout file.
 *
 * Fields (FD/UD), UVAR formulas, FL links and elements are all stored here.
 * Field values may be overridden at print time via
 * {@link #setField(int, String)}.
 */
public class LlfLayout
{

	// Canvas dimensions in dots (from NOTE,RESOLUTION)
	private int canvasWidth;
	private int canvasHeight;

	// Layout body in dots (from LAY command)
	private int layoutX;
	private int layoutY;
	private int layoutWidth;
	private int layoutHeight;

	// Image path declarations: logical name -> remote path (from IMG lines)
	private final Map<String, String> imagePaths = new LinkedHashMap<>();

	// Font declarations: label font name -> SPD alias (physical font file name)
	// The FONT line declares the logical name; the following SPD line gives the alias.
	private final Map<String, String> fontAliases = new LinkedHashMap<>();

	// Field data: id -> current value (populated from FD/UD; overridable at
	// print time)
	private final Map<Integer, String> fields = new LinkedHashMap<>();

	// UVAR formulas: id -> formula string
	private final Map<Integer, String> uvars = new LinkedHashMap<>();

	// QUE operator prompts: field id -> prompt text (from QUE lines)
	private final Map<Integer, String> quePrompts = new LinkedHashMap<>();

	// FL links: data-source-id -> element-id
	// FL,500,100 means element 100 gets its data from field/uvar 500
	private final Map<Integer, Integer> fieldLinks = new LinkedHashMap<>();

	// All elements in parse order
	private final List<LlfElement> elements = new ArrayList<>();

	// --- canvas / layout ---

	public void setCanvasSize(int width, int height)
	{
		this.canvasWidth = width;
		this.canvasHeight = height;
	}

	public void setLayoutBounds(int x, int y, int width, int height)
	{
		this.layoutX = x;
		this.layoutY = y;
		this.layoutWidth = width;
		this.layoutHeight = height;
	}

	public int getCanvasWidth()
	{
		return canvasWidth;
	}

	public int getCanvasHeight()
	{
		return canvasHeight;
	}

	public int getLayoutX()
	{
		return layoutX;
	}

	public int getLayoutY()
	{
		return layoutY;
	}

	public int getLayoutWidth()
	{
		return layoutWidth;
	}

	public int getLayoutHeight()
	{
		return layoutHeight;
	}

	// --- image paths ---

	public void addImagePath(String logicalName, String remotePath)
	{
		imagePaths.put(logicalName, remotePath);
	}

	/** Returns the remote path for the named image, or null if not declared. */
	public String getImagePath(String logicalName)
	{
		return imagePaths.get(logicalName);
	}

	public Map<String, String> getImagePaths()
	{
		return Collections.unmodifiableMap(imagePaths);
	}

	// --- font aliases ---

	public void addFontAlias(String fontName, String spdAlias)
	{
		fontAliases.put(fontName, spdAlias);
	}

	public Map<String, String> getFontAliases()
	{
		return Collections.unmodifiableMap(fontAliases);
	}

	// --- fields ---

	/** Set a field value (FD or UD from the LLF, or runtime override). */
	public void setField(int id, String value)
	{
		fields.put(id, value == null ? "" : value);
	}

	/**
	 * Register an element ID as a known field without disturbing any value
	 * already set. Called from {@link LlfParser} when a T/S/C/L element is
	 * parsed so runtime FD/UD commands can target the element even when the
	 * LQF gave it no initial value. Real PowerLeap III labellers accept FD
	 * against any defined element; this brings the simulator into line.
	 */
	public void registerField(int id)
	{
		fields.putIfAbsent(id, "");
	}

	/** Get the current value of a field, or empty string if not set. */
	public String getField(int id)
	{
		return fields.getOrDefault(id, "");
	}

	public Map<Integer, String> getFields()
	{
		return Collections.unmodifiableMap(fields);
	}

	// --- uvars ---

	public void addUvar(int id, String formula)
	{
		uvars.put(id, formula);
	}

	public String getUvarFormula(int id)
	{
		return uvars.get(id);
	}

	public Map<Integer, String> getUvars()
	{
		return Collections.unmodifiableMap(uvars);
	}

	// --- QUE operator prompts ---

	/** Record a QUE,id,promptText line. */
	public void addQuePrompt(int id, String promptText)
	{
		quePrompts.put(id, promptText == null ? "" : promptText);
	}

	public String getQuePrompt(int id)
	{
		return quePrompts.get(id);
	}

	public Map<Integer, String> getQuePrompts()
	{
		return Collections.unmodifiableMap(quePrompts);
	}

	// --- field links ---

	/**
	 * Record FL,sourceId,elementId — element elementId draws data from
	 * sourceId.
	 */
	public void addFieldLink(int sourceId, int elementId)
	{
		fieldLinks.put(elementId, sourceId);
	}

	/**
	 * Returns the data-source id for the given element id, or -1 if not linked.
	 */
	public int getSourceForElement(int elementId)
	{
		return fieldLinks.getOrDefault(elementId, -1);
	}

	public Map<Integer, Integer> getFieldLinks()
	{
		return Collections.unmodifiableMap(fieldLinks);
	}

	// --- elements ---

	public void addElement(LlfElement element)
	{
		elements.add(element);
	}

	public List<LlfElement> getElements()
	{
		return Collections.unmodifiableList(elements);
	}

	/** Returns all unique font names referenced by T and S elements in this layout. */
	public java.util.Set<String> getUsedFontNames()
	{
		java.util.Set<String> names = new TreeSet<>();
		for (LlfElement el : elements)
		{
			if (el instanceof TextElement)
				names.add(((TextElement) el).getFontName());
			else if (el instanceof ScalableTextElement)
				names.add(((ScalableTextElement) el).getFontName());
		}
		names.remove("");
		return names;
	}

	/** Look up an element by id, or null if not found. */
	public LlfElement getElementById(int id)
	{
		for (LlfElement e : elements)
		{
			if (e.getId() == id)
				return e;
		}
		return null;
	}
}
