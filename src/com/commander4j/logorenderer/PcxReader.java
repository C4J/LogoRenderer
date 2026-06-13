package com.commander4j.logorenderer;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Minimal PCX image reader.
 *
 * Supports common PCX variants: - 1-bit (monochrome) — version 2.5
 * / 3.0 - 8-bit palette (256-colour) — version 5 - 24-bit RGB — version 5
 *
 * PCX uses RLE compression (PackBits style). All multi-byte values in the
 * header are little-endian.
 */
public class PcxReader
{

	@SuppressWarnings("unused")
	public static BufferedImage read(File file) throws IOException
	{
		try (FileInputStream fis = new FileInputStream(file); DataInputStream in = new DataInputStream(fis))
		{

			// --- PCX header (128 bytes) ---
			int manufacturer = in.read(); // must be 0x0A
			if (manufacturer != 0x0A)
			{
				throw new IOException("Not a PCX file: " + file.getName());
			}

			int version = in.read(); // 0,2,3,4,5
			int encoding = in.read(); // 1 = RLE
			int bitsPerPlane = in.read(); // bits per pixel per plane

			int xMin = readLE16(in);
			int yMin = readLE16(in);
			int xMax = readLE16(in);
			int yMax = readLE16(in);


			int hDpi = readLE16(in);

			int vDpi = readLE16(in);

			// 48-byte EGA palette (header palette, used for 16-colour images)
			byte[] headerPalette = new byte[48];
			in.readFully(headerPalette);

			in.read(); // reserved
			int planes = in.read(); // number of colour planes
			int bytesPerLine = readLE16(in);// bytes per scanline per plane
											// (always even)
			int paletteType = readLE16(in);// 1=colour/BW, 2=greyscale
			in.skipBytes(58); // filler to complete 128-byte header

			int width = xMax - xMin + 1;
			int height = yMax - yMin + 1;

			// Read the RLE-compressed pixel data
			int dataSize = bytesPerLine * planes * height;
			byte[] raw = new byte[dataSize];
			int pos = 0;
			while (pos < dataSize)
			{
				int b = in.read();
				if (b < 0)
					break;
				if ((b & 0xC0) == 0xC0)
				{
					int count = b & 0x3F;
					int value = in.read();
					for (int i = 0; i < count && pos < dataSize; i++)
					{
						raw[pos++] = (byte) value;
					}
				}
				else
				{
					raw[pos++] = (byte) b;
				}
			}

			// --- Build BufferedImage ---
			int totalBitsPerPixel = bitsPerPlane * planes;

			if (totalBitsPerPixel == 1 && planes == 1)
			{
				return decode1Bit(raw, width, height, bytesPerLine);
			}
			else if (totalBitsPerPixel <= 8 && planes == 1)
			{
				// 8-bit indexed — read 256-colour palette from end of file
				byte[] palette = read256Palette(file, raw);
				return decode8Bit(raw, width, height, bytesPerLine, palette);
			}
			else if (planes == 3 && bitsPerPlane == 8)
			{
				return decode24Bit(raw, width, height, bytesPerLine);
			}
			else
			{
				throw new IOException("Unsupported PCX format: " + bitsPerPlane + " bits/plane, " + planes + " planes");
			}
		}
	}

	// -------------------------------------------------------------------------
	// Decoders
	// -------------------------------------------------------------------------

	private static BufferedImage decode1Bit(byte[] raw, int width, int height, int bytesPerLine)
	{
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++)
		{
			int rowStart = y * bytesPerLine;
			for (int x = 0; x < width; x++)
			{
				int byteIdx = rowStart + (x / 8);
				int bitIdx = 7 - (x % 8);
				int bit = (raw[byteIdx] >> bitIdx) & 1;
				// 0 = black, 1 = white (standard PCX 1-bit)
				int rgb = (bit == 0) ? 0x000000 : 0xFFFFFF;
				img.setRGB(x, y, rgb);
			}
		}
		return img;
	}

	private static BufferedImage decode8Bit(byte[] raw, int width, int height, int bytesPerLine, byte[] palette)
	{
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++)
		{
			int rowStart = y * bytesPerLine;
			for (int x = 0; x < width; x++)
			{
				int idx = raw[rowStart + x] & 0xFF;
				int r = palette[idx * 3] & 0xFF;
				int g = palette[idx * 3 + 1] & 0xFF;
				int b = palette[idx * 3 + 2] & 0xFF;
				img.setRGB(x, y, (r << 16) | (g << 8) | b);
			}
		}
		return img;
	}

	private static BufferedImage decode24Bit(byte[] raw, int width, int height, int bytesPerLine)
	{
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < height; y++)
		{
			int rOffset = y * bytesPerLine * 3;
			int gOffset = rOffset + bytesPerLine;
			int bOffset = gOffset + bytesPerLine;
			for (int x = 0; x < width; x++)
			{
				int r = raw[rOffset + x] & 0xFF;
				int g = raw[gOffset + x] & 0xFF;
				int b = raw[bOffset + x] & 0xFF;
				img.setRGB(x, y, (r << 16) | (g << 8) | b);
			}
		}
		return img;
	}

	// -------------------------------------------------------------------------
	// 256-colour palette: stored as last 769 bytes of file (marker 0x0C + 768
	// bytes)
	// -------------------------------------------------------------------------

	private static byte[] read256Palette(File file, byte[] raw) throws IOException
	{
		try (FileInputStream fis = new FileInputStream(file))
		{
			long fileLen = file.length();
			if (fileLen < 769)
				return defaultGreyscalePalette();

			long skip = fileLen - 769;
			fis.skip(skip);
			int marker = fis.read();
			if (marker != 0x0C)
				return defaultGreyscalePalette();

			byte[] palette = new byte[768];
			int read = fis.read(palette);
			if (read < 768)
				return defaultGreyscalePalette();
			return palette;
		}
	}

	private static byte[] defaultGreyscalePalette()
	{
		byte[] pal = new byte[768];
		for (int i = 0; i < 256; i++)
		{
			pal[i * 3] = (byte) i;
			pal[i * 3 + 1] = (byte) i;
			pal[i * 3 + 2] = (byte) i;
		}
		return pal;
	}

	// -------------------------------------------------------------------------
	// Little-endian 16-bit read
	// -------------------------------------------------------------------------

	private static int readLE16(DataInputStream in) throws IOException
	{
		int lo = in.read();
		int hi = in.read();
		return (hi << 8) | lo;
	}
}
