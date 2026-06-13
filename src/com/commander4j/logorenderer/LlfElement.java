package com.commander4j.logorenderer;

/**
 * Abstract base for all elements parsed from an LLF layout file.
 * Subclasses represent the element types: T (text), S (scalable text), L
 * (logo/image), G (graphic), C (barcode).
 */
public abstract class LlfElement
{

	public enum Type
	{
		TEXT, SCALABLE_TEXT, IMAGE, GRAPHIC, BARCODE
	}

	protected final int id;
	protected final Type type;
	protected final String options; // raw options string e.g. "QU", "DU",
									// "CLOQU"

	// Resolved data value (set just before rendering via FL links)
	protected String data = "";

	protected LlfElement(int id, Type type, String options)
	{
		this.id = id;
		this.type = type;
		this.options = options == null ? "" : options;
	}

	public int getId()
	{
		return id;
	}

	public Type getType()
	{
		return type;
	}

	public String getOptions()
	{
		return options;
	}

	public String getData()
	{
		return data;
	}

	public void setData(String data)
	{
		this.data = data == null ? "" : data;
	}

	/**
	 * True if the DISABLED (D) option flag is set — element should not be
	 * rendered.
	 */
	public boolean isDisabled()
	{
		return options.contains("D");
	}

	/** True if the UPSIDEDOWN (U) option flag is set. */
	public boolean isUpsideDown()
	{
		return options.contains("U");
	}

	/** True if the UNICODE_DATA (Q) option flag is set. */
	public boolean isUnicode()
	{
		return options.contains("Q");
	}

	/** True if the CENTER (C) option flag is set. */
	public boolean isCentered()
	{
		return options.contains("C");
	}

	/** True if the RIGHTADJUST (R) option flag is set. */
	public boolean isRightAdjust()
	{
		return options.contains("R");
	}

	/**
	 * True if the LOWER (L) option flag is set — anchor to bottom of bounding
	 * box.
	 */
	public boolean isLower()
	{
		return options.contains("L");
	}

	/**
	 * True if the MIDDLE (M) option flag is set — anchor to vertical midpoint
	 * of bounding box.
	 */
	public boolean isMiddle()
	{
		return options.contains("M");
	}

	/**
	 * True if the OUTDATA (O) option flag is set — this element's value feeds
	 * an FL link. Does NOT mean "draw human-readable text on the barcode"; that
	 * is always handled by an explicit T element in the template.
	 */
	public boolean isOutdata()
	{
		return options.contains("O");
	}


	@Override
	public String toString()
	{
		return type + "[" + id + "] data=\"" + data + "\" opts=" + options;
	}
}
